package com.benny.nfctools.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.*
import com.benny.nfctools.data.OperationKind
import com.benny.nfctools.data.PendingOperation
import com.benny.nfctools.data.TagSnapshot
import com.benny.nfctools.data.MemoryDump
import com.benny.nfctools.nfc.NdefCodec.hex
import java.io.IOException

interface NfcOperations {
    fun read(tag: Tag): TagSnapshot
    fun execute(tag: Tag, op: PendingOperation): TagSnapshot
}

class NfcEngine : NfcOperations {
    override fun read(tag: Tag): TagSnapshot = read(tag, "读取")

    fun read(tag: Tag, action: String, strict: Boolean = false): TagSnapshot {
        val ndef = Ndef.get(tag)
        var bytes: ByteArray? = null
        var capacity: Int? = null
        var writable: Boolean? = null
        var canLock = false
        val details = linkedMapOf<String, String>()
        if (ndef != null) {
            capacity = ndef.maxSize
            writable = ndef.isWritable
            canLock = ndef.canMakeReadOnly()
            try {
                ndef.use { it.connect(); bytes = it.ndefMessage?.toByteArray() }
                if ((bytes?.size ?: 0) > NdefCodec.MAX_FILE_BYTES) {
                    if (strict) error("NDEF 数据超过 48 KB")
                    bytes = null; details["NDEF 读取"] = "数据超过 48 KB，已保留公开卡片信息"
                }
            } catch (e: android.nfc.TagLostException) { throw e }
            catch (e: Exception) {
                if (strict) throw e
                details["NDEF 读取"] = "无法读取内容，仍可查看 UID 与协议：${e.message.orEmpty()}"
            }
        }
        if (tag.id.isNotEmpty()) {
            details["UID 正序十进制"] = java.math.BigInteger(1, tag.id).toString()
            details["UID 反序十进制"] = java.math.BigInteger(1, tag.id.reversedArray()).toString()
        }
        NfcA.get(tag)?.let { details["ATQA"] = it.atqa.hex(); details["SAK"] = "%02X".format(it.sak.toInt() and 0xff) }
        NfcB.get(tag)?.let { details["应用数据"] = it.applicationData.hex(); details["协议信息"] = it.protocolInfo.hex() }
        NfcF.get(tag)?.let { details["系统代码"] = it.systemCode.hex(); details["制造商数据"] = it.manufacturer.hex() }
        NfcV.get(tag)?.let { details["DSF ID"] = "%02X".format(it.dsfId); details["响应标记"] = "%02X".format(it.responseFlags) }
        IsoDep.get(tag)?.let {
            it.historicalBytes?.let { b -> details["历史字节"] = b.hex() }
            it.hiLayerResponse?.let { b -> details["高层响应"] = b.hex() }
            details["最大收发长度"] = "${it.maxTransceiveLength} B"
        }
        MifareClassic.get(tag)?.let { details["存储大小"] = "${it.size} B"; details["扇区 / 块"] = "${it.sectorCount} / ${it.blockCount}" }
        MifareUltralight.get(tag)?.let { details["Ultralight 类型"] = when (it.type) { 1 -> "Ultralight"; 2 -> "Ultralight C"; else -> "未知" } }
        val type = ndef?.type?.let {
            when (it) {
                Ndef.NFC_FORUM_TYPE_1 -> "NFC Forum Type 1"
                Ndef.NFC_FORUM_TYPE_2 -> "NFC Forum Type 2"
                Ndef.NFC_FORUM_TYPE_3 -> "NFC Forum Type 3"
                Ndef.NFC_FORUM_TYPE_4 -> "NFC Forum Type 4"
                Ndef.MIFARE_CLASSIC -> "MIFARE Classic / NDEF"
                else -> it.substringAfterLast('.')
            }
        } ?: when {
            MifareClassic.get(tag) != null -> "MIFARE Classic"
            IsoDep.get(tag) != null -> "ISO 14443-4 / ISO-DEP"
            NfcV.get(tag) != null -> "ISO 15693"
            NfcF.get(tag) != null -> "FeliCa / NFC-F"
            else -> "非 NDEF 标签"
        }
        return TagSnapshot(tag.id.hex().ifBlank { "无 UID" }, tag.techList.map { it.substringAfterLast('.') }, type,
            capacity, writable, canLock, NdefFormatable.get(tag) != null, details,
            bytes?.let(NdefCodec::base64), action = action)
    }

    override fun execute(tag: Tag, op: PendingOperation): TagSnapshot {
        when (op.kind) {
            OperationKind.READ_MEMORY -> {
                val snapshot = read(tag, "读取内存")
                return snapshot.copy(memory = memory(tag, op))
            }
            OperationKind.WRITE -> write(tag, NdefCodec.requireSingleOperation(NdefCodec.parse(requireNotNull(op.bytes))))
            OperationKind.ERASE -> write(tag, emptyMessage())
            OperationKind.FORMAT -> {
                require(Ndef.get(tag) == null) { "此标签已经是 NDEF 格式，可直接写入或擦除" }
                val formatable = NdefFormatable.get(tag) ?: error("此卡不支持 Android NDEF 格式化")
                formatable.use { it.connect(); it.format(emptyMessage()) }
                // Some tag implementations only expose Ndef after a new discovery.
                return read(tag, "格式化").copy(details = mapOf("格式化结果" to "已完成，请移开卡片后重新扫描确认"))
            }
            OperationKind.LOCK -> {
                val ndef = Ndef.get(tag) ?: error("此卡不是 NDEF 标签，无法设置只读")
                ndef.use {
                    it.connect()
                    require(it.isWritable) { "此标签已经是只读状态" }
                    require(it.canMakeReadOnly()) { "此标签不支持设置永久只读" }
                    check(it.makeReadOnly()) { "标签拒绝设置只读，请重新扫描检查状态" }
                }
            }
        }
        val snapshot = read(tag, op.kind.label, strict = true)
        // Android caches Ndef metadata on the Tag until the next discovery.
        return if (op.kind == OperationKind.LOCK) snapshot.copy(writable = false, canLock = false) else snapshot
    }

    private fun write(tag: Tag, message: NdefMessage) {
        val ndef = Ndef.get(tag) ?: error("此卡不支持 NDEF 写入；若可格式化，请先使用格式化工具")
        ndef.use {
            it.connect()
            require(it.isWritable) { "此标签是只读的，无法写入或擦除" }
            require(message.toByteArray().size <= it.maxSize) { "容量不足：需要 ${message.toByteArray().size} B，标签只有 ${it.maxSize} B" }
            it.writeNdefMessage(message)
            val verified = it.ndefMessage ?: throw IOException("已发送写入，但未读到校验数据；请重新扫描确认")
            check(verified.toByteArray().contentEquals(message.toByteArray())) { "写入后校验不一致，请重新扫描确认内容" }
        }
    }
    private fun emptyMessage() = NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, byteArrayOf(), byteArrayOf(), byteArrayOf())))

    private fun memory(tag: Tag, request: PendingOperation): MemoryDump {
        MifareClassic.get(tag)?.let { classic ->
            val blocks = MutableList<String?>(classic.blockCount) { null }
            var failed = 0
            classic.use {
                it.connect()
                val key = NdefCodec.hexBytes(request.keyHex)
                for (sector in 0 until it.sectorCount) {
                    val authenticated = if (request.keyB) it.authenticateSectorWithKeyB(sector, key) else it.authenticateSectorWithKeyA(sector, key)
                    if (!authenticated) { failed++; continue }
                    val first = it.sectorToBlock(sector)
                    for (block in first until first + it.getBlockCountInSector(sector)) blocks[block] = it.readBlock(block).hex().replace(" ", "")
                }
            }
            return MemoryDump("MIFARE Classic", 16, blocks, listOf("使用用户提供的 Key ${if (request.keyB) "B" else "A"} 读取；$failed 个扇区未通过认证", "? 表示未读取的块；密钥和访问位以芯片实际返回值为准"))
        }
        MifareUltralight.get(tag)?.let { ultra ->
            val blocks = mutableListOf<String?>()
            val notes = mutableListOf<String>()
            ultra.use {
                it.connect()
                val header = it.readPages(0)
                val declaredPages = if (header.size >= 16 && header[12].toInt() and 255 == 0xe1) 4 + (header[14].toInt() and 255) * 2 else 16
                val limit = minOf(request.pageCount, declaredPages.coerceIn(4, 240))
                if (limit < request.pageCount) notes += "按公开容量限制读取前 $limit 页，避免越界后循环返回重复数据"
                for (page in 0 until limit step 4) {
                    try {
                        val bytes = if (page == 0) header else it.readPages(page)
                        require(bytes.size >= 16) { "标签返回页数据不完整" }
                        repeat(minOf(4, limit - page)) { n -> blocks += bytes.copyOfRange(n * 4, n * 4 + 4).hex().replace(" ", "") }
                    } catch (e: android.nfc.TagLostException) { throw e }
                    catch (_: IOException) { notes += "从第 $page 页起不可读取：可能超出容量或受密码保护"; break }
                }
            }
            require(blocks.isNotEmpty()) { "未读到可访问的页数据" }
            return MemoryDump("Type 2 pages", 4, blocks, notes)
        }
        error("此手机或卡片不支持 MIFARE Classic / Ultralight 原始内存读取；仍可查看公开协议信息")
    }
}
