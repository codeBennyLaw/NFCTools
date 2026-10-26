package com.benny.nfctools

import android.app.Application
import android.net.Uri
import android.nfc.Tag
import android.nfc.TagLostException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import com.benny.nfctools.nfc.NfcEngine
import com.benny.nfctools.nfc.NfcOperations
import com.benny.nfctools.nfc.QuickEntry
import com.benny.nfctools.nfc.MemoryDumpCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

class NfcViewModel internal constructor(application: Application, private val engine: NfcOperations) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, NfcEngine())

    private val store = LocalStore(application)
    private val historyMutex = Mutex()
    private val cardsMutex = Mutex()
    private var foreground = true
    private var lastWrittenUid: String? = null
    private val initialEntry = store.quickEntry()
    private val mutable = MutableStateFlow(AppState(draft = store.draft(), importedMessage = store.imported(), darkMode = store.theme(), activeEntry = initialEntry, composerKind = initialEntry.kind))
    val state = mutable.asStateFlow()
    private val notices = Channel<String>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()

    init {
        viewModelScope.launch {
            cardsMutex.withLock {
                try { val cards = withContext(Dispatchers.IO) { store.loadCards() }; mutable.update { it.copy(cards = cards) } }
                catch (_: Exception) { notify("卡包读取失败，请保留本机数据以便恢复") }
            }
        }
        viewModelScope.launch {
            historyMutex.withLock {
                try {
                    val history = withContext(Dispatchers.IO) { store.loadHistory() }
                    mutable.update { it.copy(history = history, historyLoaded = true) }
                } catch (_: Exception) {
                    mutable.update { it.copy(historyLoaded = true) }
                    notify("历史记录读取失败，原文件已保留；新扫描仍可使用")
                }
            }
        }
    }
    fun notify(message: String) { notices.trySend(message) }
    fun setDevice(status: DeviceStatus) { mutable.update { it.copy(device = status, pending = if (status == DeviceStatus.READY) it.pending else null) } }
    fun navigate(destination: Destination) { if (!state.value.busy) mutable.update { it.copy(destination = destination, pending = null) } }
    fun theme(mode: String) { store.saveTheme(mode); mutable.update { it.copy(darkMode = mode) } }
    fun cancel() { if (!state.value.busy) mutable.update { it.copy(pending = null) } }
    fun pause() { foreground = false; mutable.update { it.copy(pending = null) } }
    fun resume() { foreground = true }
    fun dismissError() { mutable.update { it.copy(error = null) } }
    fun selectKind(kind: RecordKind) {
        mutable.update { it.copy(composerKind = kind, activeEntry = it.activeEntry.copy(kind = kind), destination = Destination.WRITE, pending = null) }
        store.saveQuickEntry(state.value.activeEntry)
    }
    fun quickEntry(record: DraftRecord) {
        mutable.update { it.copy(activeEntry = record, composerKind = record.kind, destination = Destination.WRITE, pending = null) }
        store.saveQuickEntry(record)
    }
    fun updateEntry(value: String? = null, secondary: String? = null, tertiary: String? = null) {
        mutable.update { it.copy(activeEntry = it.activeEntry.copy(value = value ?: it.activeEntry.value, secondary = secondary ?: it.activeEntry.secondary, tertiary = tertiary ?: it.activeEntry.tertiary)) }
        store.saveQuickEntry(state.value.activeEntry)
    }
    fun clearEntry() {
        mutable.update { it.copy(activeEntry = DraftRecord(kind = it.composerKind)) }
        store.saveQuickEntry(state.value.activeEntry)
    }
    fun paste(text: String) { runCatching { QuickEntry.parse(text) }.onSuccess(::quickEntry).onFailure { notify(it.message ?: "无法识别文字") } }
    fun batch(enabled: Boolean) { mutable.update { it.copy(batchWrite = enabled, pending = null, writtenCount = 0) }; lastWrittenUid = null }

    fun add(record: DraftRecord): Boolean {
        val old = runCatching { state.value.importedMessage?.let { NdefCodec.editable(requireNotNull(NdefCodec.message(it))) } ?: state.value.draft }
            .getOrElse { notify("备份包含不可编辑记录，请先清空或直接原样写入"); return false }
        val next = if (old.any { it.id == record.id }) old.map { if (it.id == record.id) record else it } else old + record
        return runCatching { NdefCodec.encode(next) }.onSuccess {
            mutable.update { s -> s.copy(draft = next, importedMessage = null) }; saveDraft()
        }.onFailure { notify(it.message ?: "记录无效") }.isSuccess
    }
    fun remove(id: String) { mutable.update { s -> s.copy(draft = s.draft.filterNot { it.id == id }) }; saveDraft() }
    fun clearDraft() { mutable.update { it.copy(draft = emptyList(), importedMessage = null) }; saveDraft() }
    private fun saveDraft() { store.saveDraft(state.value.draft, state.value.importedMessage) }
    fun messageBytes(): ByteArray? = runCatching {
        state.value.importedMessage?.let { NdefCodec.fromBase64(it) } ?: NdefCodec.encode(state.value.draft).toByteArray()
    }.getOrNull()

    fun arm(kind: OperationKind) {
        if (state.value.busy || state.value.device != DeviceStatus.READY) { notify("请先开启手机 NFC，并保持 App 在前台"); return }
        val bytes = if (kind == OperationKind.WRITE) messageBytes() else null
        if (kind == OperationKind.WRITE && bytes == null) { notify("请先添加有效记录"); return }
        lastWrittenUid = null
        mutable.update { it.copy(pending = PendingOperation(kind, bytes), writtenCount = 0, error = null) }
    }
    fun armMemory(key: String, useKeyB: Boolean, pageCount: Int = 16) {
        if (state.value.device != DeviceStatus.READY || state.value.busy) { notify("请先开启 NFC"); return }
        if (!key.matches(Regex("[A-Fa-f0-9]{12}"))) { notify("MIFARE Classic 密钥应为 12 位 HEX"); return }
        mutable.update { it.copy(pending = PendingOperation(OperationKind.READ_MEMORY, keyHex = key, keyB = useKeyB, pageCount = pageCount.coerceIn(4, 240)), error = null) }
    }

    fun onTag(tag: Tag) {
        viewModelScope.launch {
            if (state.value.busy) return@launch
            val started = state.value
            val operation = started.pending
            val singleWrite = operation?.kind == OperationKind.WRITE && !started.batchWrite
            if (operation?.kind == OperationKind.WRITE && state.value.batchWrite && tag.id.joinToString("") { "%02X".format(it) } == lastWrittenUid) return@launch
            mutable.update { it.copy(busy = true, pending = null, error = null) }
            try {
                val snapshot = withContext(Dispatchers.IO) { if (operation == null) engine.read(tag) else engine.execute(tag, operation) }
                if (operation?.kind == OperationKind.WRITE) lastWrittenUid = tag.id.joinToString("") { "%02X".format(it) }
                // Clear only after the engine has verified the write. Keep any new
                // content received via a share/import while NFC I/O was running.
                val clearWrittenDraft = singleWrite && state.value.draft == started.draft && state.value.importedMessage == started.importedMessage
                val clearWrittenEntry = singleWrite && state.value.activeEntry == started.activeEntry
                mutable.update { it.copy(current = snapshot,
                    draft = if (clearWrittenDraft) emptyList() else it.draft,
                    importedMessage = if (clearWrittenDraft) null else it.importedMessage,
                    activeEntry = if (clearWrittenEntry) DraftRecord(kind = it.composerKind) else it.activeEntry,
                    writtenCount = if (operation?.kind == OperationKind.WRITE) it.writtenCount + 1 else it.writtenCount,
                    destination = if (operation?.kind == OperationKind.WRITE && it.batchWrite) Destination.WRITE else Destination.READ,
                    pending = if (operation?.kind == OperationKind.WRITE && it.batchWrite && foreground) operation else null) }
                if (clearWrittenDraft) saveDraft()
                if (clearWrittenEntry) store.saveQuickEntry(state.value.activeEntry)
                notify(if (operation == null) "已读取标签" else if (clearWrittenDraft && clearWrittenEntry) "写入完成，已清空待写入内容" else if (operation.kind == OperationKind.FORMAT) "格式化完成，请移开后重新扫描" else "${operation.kind.label}完成")
                appendHistory(snapshot)
            } catch (e: Exception) {
                val error = when (e) {
                    is TagLostException -> "卡片已移开，请贴近手机重新扫描；若刚执行写入，请检查内容"
                    is IOException -> "NFC 通信失败，请重新贴卡检查内容：${e.message.orEmpty()}"
                    is SecurityException -> "NFC 标签已失效，请移开后重新贴卡"
                    else -> e.message ?: "操作失败，请重新扫描"
                }
                mutable.update { it.copy(error = error) }
                notify(error)
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }

    private suspend fun appendHistory(snapshot: TagSnapshot) = historyMutex.withLock {
        val history = (listOf(snapshot) + state.value.history).take(50)
        mutable.update { it.copy(history = history) }
        try { withContext(Dispatchers.IO) { store.saveHistory(history) } }
        catch (_: Exception) { notify("操作成功，但历史记录保存失败") }
    }
    fun clearHistory() {
        viewModelScope.launch {
            historyMutex.withLock {
                try { withContext(Dispatchers.IO) { store.saveHistory(emptyList()) }; mutable.update { it.copy(history = emptyList()) } }
                catch (_: Exception) { notify("清空记录失败，请重试") }
            }
        }
    }
    fun openHistory(snapshot: TagSnapshot) { mutable.update { it.copy(current = snapshot, destination = Destination.READ, pending = null) } }
    fun reuse(snapshot: TagSnapshot) {
        val message = snapshot.messageBase64 ?: return
        mutable.update { it.copy(importedMessage = message, draft = emptyList(), destination = Destination.WRITE, pending = null) }
        saveDraft()
        notify("NDEF 内容已加入待写入区；UID 和加密区不会被复制")
    }
    fun copyToNext(snapshot: TagSnapshot) {
        if (snapshot.messageBase64 == null) { notify("此卡没有可复制的 NDEF 内容"); return }
        mutable.update { it.copy(batchWrite = false) }
        reuse(snapshot)
        if (state.value.device == DeviceStatus.READY) arm(OperationKind.WRITE)
    }
    fun editContents(snapshot: TagSnapshot) {
        runCatching {
            val message = NdefCodec.message(snapshot.messageBase64) ?: error("此卡没有 NDEF 内容")
            val records = NdefCodec.editable(message)
            NdefCodec.encode(records)
            records
        }.onSuccess { records -> mutable.update { it.copy(draft = records, importedMessage = null, destination = Destination.WRITE, pending = null) }; saveDraft() }
            .onFailure { notify(it.message ?: "无法直接编辑，可使用原样复制") }
    }
    fun editImported() {
        val encoded = state.value.importedMessage ?: return
        runCatching { NdefCodec.editable(requireNotNull(NdefCodec.message(encoded))).also { NdefCodec.encode(it) } }
            .onSuccess { records -> mutable.update { it.copy(draft = records, importedMessage = null) }; saveDraft() }
            .onFailure { notify(it.message ?: "无法转为可编辑记录") }
    }
    fun saveCard(tag: TagSnapshot, name: String = "", category: CardCategory = CardCategory.TAG) {
        if (tag.sample) { notify("示例标签不会存入卡包，请先读取真实卡片"); return }
        viewModelScope.launch {
            cardsMutex.withLock {
                val existing = state.value.cards.firstOrNull { it.tag.uid == tag.uid }
                val card = SavedCard(existing?.id ?: java.util.UUID.randomUUID().toString(), name.ifBlank { existing?.name ?: "卡片 ${tag.uid.takeLast(8)}" }, existing?.category ?: category, tag)
                val cards = listOf(card) + state.value.cards.filterNot { it.id == card.id }
                try { withContext(Dispatchers.IO) { store.saveCards(cards) }; mutable.update { it.copy(cards = cards) }; notify("已保存到卡包，可在卡包中改名和分类") }
                catch (e: Exception) { notify("保存失败：${e.message}") }
            }
        }
    }
    fun updateCard(card: SavedCard) = mutateCards { it.map { old -> if (old.id == card.id) card else old } }
    fun deleteCard(id: String) = mutateCards { it.filterNot { card -> card.id == id } }
    private fun mutateCards(transform: (List<SavedCard>) -> List<SavedCard>) {
        viewModelScope.launch { cardsMutex.withLock {
            val cards = transform(state.value.cards)
            try { withContext(Dispatchers.IO) { store.saveCards(cards) }; mutable.update { it.copy(cards = cards) } }
            catch (e: Exception) { notify("卡包更新失败：${e.message}") }
        } }
    }
    fun importDump(uri: Uri, name: String) {
        viewModelScope.launch {
            try {
                val dump = withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytesBounded(NdefCodec.MAX_FILE_BYTES) } ?: error("无法打开文件")
                    MemoryDumpCodec.parse(bytes, name)
                }
                val tag = TagSnapshot("文件 ${java.util.UUID.randomUUID().toString().take(8)}", emptyList(), dump.format, null, null, false, false, mapOf("来源文件" to name), null, action = "导入 Dump", memory = dump)
                mutable.update { it.copy(current = tag, destination = Destination.READ) }
            } catch (e: Exception) { notify("导入 Dump 失败：${e.message}") }
        }
    }
    fun showSample() {
        val message = NdefCodec.encode(listOf(DraftRecord(value = "你好，NFC。\n让每一次轻触，都有新的可能。"), DraftRecord(kind = RecordKind.URI, value = "https://developer.android.com")))
        mutable.update { it.copy(destination = Destination.READ, current = TagSnapshot(
            "04 A2 8B 6C 71 2D 80", listOf("NfcA", "MifareUltralight", "Ndef"), "NFC Forum Type 2", 492,
            true, true, false, mapOf("ATQA" to "44 00", "SAK" to "00", "数据来源" to "界面演示，非真实读卡"),
            NdefCodec.base64(message.toByteArray()), sample = true)) }
    }

    fun importFile(uri: Uri, name: String, mime: String, asNdef: Boolean) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        it.readBytesBounded(NdefCodec.MAX_FILE_BYTES)
                    } ?: error("无法打开此文件")
                }
                if (asNdef) {
                    NdefCodec.parse(bytes)
                    mutable.update { it.copy(importedMessage = NdefCodec.base64(bytes), draft = emptyList(), destination = Destination.WRITE) }
                    saveDraft()
                    notify("已导入 NDEF，请确认内容后写入")
                } else {
                    add(DraftRecord(kind = RecordKind.FILE, value = name, mime = mime, base64 = NdefCodec.base64(bytes)))
                }
            } catch (e: Exception) { notify("导入失败：${e.message ?: "文件格式不受支持"}") }
        }
    }
    fun exportFile(uri: Uri, bytes: ByteArray) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("无法创建文件") }
                notify("文件已导出")
            } catch (e: Exception) { notify("导出失败：${e.message.orEmpty()}") }
        }
    }
}

fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        require(output.size() + n <= limit) { "文件超过 ${limit / 1024} KB，请使用更小的文件或写入下载链接" }
        output.write(buffer, 0, n)
    }
    return output.toByteArray()
}
