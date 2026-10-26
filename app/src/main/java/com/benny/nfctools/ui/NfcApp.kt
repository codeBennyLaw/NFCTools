package com.benny.nfctools.ui

import android.provider.OpenableColumns
import android.provider.ContactsContract
import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.benny.nfctools.NfcViewModel
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import com.benny.nfctools.tasks.TaskDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcApp(vm: NfcViewModel, openSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val taskRun by vm.tasks.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycleOwner, context) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.tasks.resume()
            try {
                vm.tasks.requests.collect { request ->
                    if (vm.tasks.accepts(request)) vm.tasks.dispatched(request, TaskDispatcher.dispatch(context, request.record))
                }
            } finally { vm.tasks.pause() }
        }
    }
    val scope = rememberCoroutineScope()
    var settings by rememberSaveable { mutableStateOf(false) }
    var editorKind by rememberSaveable { mutableStateOf<String?>(null) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var fileIsNdef by rememberSaveable { mutableStateOf(false) }
    var fileIsDump by rememberSaveable { mutableStateOf(false) }
    var applicationPicker by rememberSaveable { mutableStateOf(false) }
    var comparing by rememberSaveable { mutableStateOf(false) }
    var readingMemory by rememberSaveable { mutableStateOf(false) }
    var exportBytes by rememberSaveable { mutableStateOf<ByteArray?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val meta = withContext(Dispatchers.IO) {
                    var name = "file.bin"
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
                    }
                    name to (context.contentResolver.getType(uri) ?: "application/octet-stream")
                }
                if (fileIsDump) vm.importDump(uri, meta.first) else vm.importFile(uri, meta.first, meta.second, fileIsNdef)
            } catch (e: Exception) { vm.notify("无法打开文件：${e.message.orEmpty()}") }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = exportBytes
        if (uri != null && bytes != null) vm.exportFile(uri, bytes)
        exportBytes = null
    }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { uri -> scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use {
                        require(it.moveToFirst()) { "联系人为空" }
                        DraftRecord(kind = RecordKind.CONTACT, value = it.getString(0).orEmpty(), secondary = it.getString(1).orEmpty())
                    } ?: error("无法读取联系人")
                }
            }.onSuccess(vm::quickEntry).onFailure { vm.notify("读取联系人失败，可手动填写：${it.message}") }
        } }
    }
    fun chooseContact() { runCatching { contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }.onFailure { vm.notify("此设备没有联系人选择器，可手动填写") } }
    fun chooseFile(ndef: Boolean, dump: Boolean = false) { fileIsNdef = ndef; fileIsDump = dump; importer.launch(arrayOf("*/*")) }
    fun export(bytes: ByteArray, name: String) { exportBytes = bytes; exporter.launch(name) }
    fun edit(kind: RecordKind, id: String? = null) {
        if (id == null) vm.selectKind(kind) else vm.navigate(Destination.WRITE)
        if (kind == RecordKind.FILE) chooseFile(false)
        else if (kind == RecordKind.APPLICATION && id == null) applicationPicker = true
        else if (id != null) { editorKind = kind.name; editorId = id }
    }
    fun openWallet() {
        val intent = listOf("com.hihonor.wallet", "com.huawei.wallet").firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
        if (intent == null) vm.notify("未找到可启动的系统钱包，请从桌面打开荣耀钱包添加门禁卡")
        else runCatching { context.startActivity(intent) }.onFailure { vm.notify("无法打开系统钱包，请从桌面启动") }
    }
    val view = LocalView.current
    DisposableEffect(state.pending != null || state.busy) {
        val window = (view.context as? Activity)?.window
        if (state.pending != null || state.busy) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(vm) { vm.messages.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it, withDismissAction = true) } }
    BackHandler(enabled = state.pending != null && !state.busy) { vm.cancel() }
    BackHandler(enabled = state.destination != Destination.READ && state.pending == null && !state.busy && !settings && editorKind == null && confirmation == null) { vm.navigate(Destination.READ) }

    NfcTheme(state.darkMode) {
        taskRun?.let { TaskSheet(it, vm.tasks) }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            modifier = Modifier.imePadding(),
            bottomBar = { if (!keyboardVisible) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Destination.entries.forEach { d ->
                        val icon = when (d) { Destination.READ -> Icons.Outlined.Nfc; Destination.WRITE -> Icons.Outlined.EditNote; Destination.CARDS -> Icons.Outlined.Wallet; Destination.TOOLS -> Icons.Outlined.Widgets; Destination.HISTORY -> Icons.Outlined.History }
                        NavigationBarItem(selected = state.destination == d, onClick = { vm.navigate(d) }, enabled = !state.busy,
                            icon = { Icon(icon, null, Modifier.size(24.dp)) }, label = { Text(d.label) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer, selectedTextColor = MaterialTheme.colorScheme.primary))
                    }
                }
            } }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(when (state.destination) { Destination.READ -> "NFC 工具箱"; Destination.WRITE -> "快捷写入"; Destination.CARDS -> "我的卡包"; Destination.TOOLS -> "标签工具"; Destination.HISTORY -> "读取记录" },
                                fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalIconButton(onClick = { settings = true }, modifier = Modifier.size(48.dp), shape = androidx.compose.foundation.shape.CircleShape) {
                            Icon(Icons.Outlined.Tune, "设置与使用帮助")
                        }
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
                    if (state.error != null) Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Row(Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(state.error!!, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = vm::dismissError) { Icon(Icons.Outlined.Close, "关闭错误提示") }
                        }
                    }
                    if (state.pending != null) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                                    Text("等待贴卡 · ${state.pending!!.kind.label}", fontWeight = FontWeight.Bold)
                                    Text(if (state.batchWrite && state.pending!!.kind == OperationKind.WRITE) "连续写卡 · 已完成 ${state.writtenCount} 张，请更换标签" else "仅对下一张靠近的卡片执行一次", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = vm::cancel) { Text("取消") }
                            }
                        }
                    }
                    when (state.destination) {
                        Destination.READ -> ReadScreen(state, vm, openSettings, ::edit, onExportPayload = ::export,
                            onExport = { snapshot, raw ->
                                if (raw) snapshot.messageBase64?.let { export(NdefCodec.fromBase64(it), "nfc-${snapshot.timestamp}.ndef") }
                                else export(LocalStore.json(snapshot).toString(2).toByteArray(), "nfc-${snapshot.timestamp}.json")
                            })
                        Destination.WRITE -> QuickWriteScreen(state, vm, onFile = { chooseFile(false) }, onContact = ::chooseContact, onApplication = { applicationPicker = true }, onEdit = { edit(it.kind, it.id) },
                            onImport = { chooseFile(true) }, onExport = { bytes -> export(bytes, "nfc-draft.ndef") })
                        Destination.CARDS -> CardsScreen(state, vm, onCompare = { comparing = true })
                        Destination.TOOLS -> AdvancedToolsScreen(state, onOperation = { if (it == OperationKind.READ_MEMORY) readingMemory = true else confirmation = it.name }, onImport = { chooseFile(true) }, onImportDump = { chooseFile(false, true) }, onCompare = { comparing = true }, onSample = vm::showSample, onNfcSettings = openSettings, onWallet = ::openWallet)
                        Destination.HISTORY -> HistoryScreen(state, onOpen = vm::openHistory, onClear = { confirmation = "HISTORY" })
                    }
                }
            }
        }

        if (editorKind != null) {
            val kind = RecordKind.valueOf(editorKind!!)
            val record = state.draft.firstOrNull { it.id == editorId } ?: remember(editorKind, editorId) { DraftRecord(id = editorId!!, kind = kind) }
            RecordEditor(record, onDismiss = { editorKind = null }, onSave = { if (vm.add(it)) editorKind = null })
        }
        if (settings) SettingsSheet(state.darkMode, onDismiss = { settings = false }, onTheme = vm::theme, onNfcSettings = openSettings)
        if (applicationPicker) ApplicationPicker(onDismiss = { applicationPicker = false }, onSelect = { pkg, label -> vm.quickEntry(DraftRecord(kind = RecordKind.APPLICATION, value = pkg, secondary = label)); applicationPicker = false })
        if (comparing) ComparisonSheet(state, onDismiss = { comparing = false })
        if (readingMemory) MemoryReadDialog(onDismiss = { readingMemory = false }, onRead = { key, useB, pages -> readingMemory = false; vm.armMemory(key, useB, pages) })
        if (confirmation != null) {
            val key = confirmation!!
            val text = when (key) {
                "WRITE" -> "将用待写入区的内容覆盖下一张卡片的全部 NDEF 记录。写入后会回读校验。请确认目标卡片可写，操作期间保持贴近。"
                "ERASE" -> "将下一张卡片的 NDEF 内容替换为空记录。此操作不清除 UID、加密区或整张卡的物理存储。"
                "FORMAT" -> "将下一张支持 NdefFormatable 的标签初始化为 NDEF，并写入空记录。原有数据可能丢失，完成后请重新扫描。"
                "LOCK" -> "下一张卡片将被永久设为只读。此操作无法撤销，之后无法再次写入或擦除。请先读卡确认内容，并确保只靠近目标标签。"
                "DRAFT" -> "清空当前待写入内容，已经写入的卡片不会受到影响。"
                else -> "清空本机所有读卡记录，卡片上的内容不会受到影响。"
            }
            val title = when (key) { "WRITE" -> "确认写入"; "ERASE" -> "擦除 NDEF 内容？"; "FORMAT" -> "格式化标签？"; "LOCK" -> "永久锁定为只读？"; "DRAFT" -> "清空待写入区？"; else -> "清空历史记录？" }
            AlertDialog(onDismissRequest = { confirmation = null }, icon = { Icon(if (key == "LOCK") Icons.Outlined.Lock else Icons.Outlined.Info, null) },
                title = { Text(title) }, text = { Text(text) },
                confirmButton = { TextButton(onClick = {
                    confirmation = null
                    when (key) { "HISTORY" -> vm.clearHistory(); "DRAFT" -> vm.clearDraft(); else -> vm.arm(OperationKind.valueOf(key)) }
                }) { Text(if (key == "LOCK") "我理解，永久设为只读" else "确认", color = if (key == "WRITE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } })
        }
    }
}
