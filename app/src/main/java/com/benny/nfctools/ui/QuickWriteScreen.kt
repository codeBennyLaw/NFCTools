package com.benny.nfctools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.benny.nfctools.NfcViewModel
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickWriteScreen(state: AppState, vm: NfcViewModel, onFile: () -> Unit, onContact: () -> Unit, onApplication: () -> Unit,
                     onEdit: (DraftRecord) -> Unit, onImport: () -> Unit, onExport: (ByteArray) -> Unit) {
    val value = state.activeEntry.value
    val secondary = state.activeEntry.secondary
    val tertiary = state.activeEntry.tertiary
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var clear by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val kind = state.composerKind
    val bytes = remember(state.draft, state.importedMessage) { vm.messageBytes() }
    val available = !state.busy && state.pending == null
    fun add(start: Boolean) {
        if (value.isNotBlank() && kind != RecordKind.FILE) {
            val record = state.activeEntry
            val problem = runCatching { NdefCodec.encode(listOf(record)) }.exceptionOrNull()
            if (problem != null) { error = problem.message; return }
            if (!vm.add(record)) return
            vm.clearEntry(); error = null
        } else if (vm.messageBytes() == null) { error = if (kind == RecordKind.FILE) "请先选择文件" else "请先填写内容"; return }
        if (start) vm.arm(OperationKind.WRITE)
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                AppCard {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                            Text("写点什么？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Row {
                        TextButton(onClick = { clipboard.getText()?.text?.let(vm::paste) ?: vm.notify("剪贴板没有文字") }, enabled = available) {
                            Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("粘贴识别")
                        }
                        TextButton(onClick = { more = !more }) { Text(if (more) "收起" else "更多") }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val kinds = if (more) RecordKind.entries.toList() else listOf(RecordKind.TEXT, RecordKind.URI, RecordKind.WIFI, RecordKind.APPLICATION, RecordKind.CONTACT, RecordKind.FILE)
                        kinds.forEach { type ->
                            FilterChip(selected = kind == type, onClick = { vm.selectKind(type); error = null; when (type) { RecordKind.APPLICATION -> onApplication(); RecordKind.CONTACT -> onContact(); RecordKind.FILE -> onFile(); else -> Unit } }, enabled = available,
                                label = { Text(type.label) }, leadingIcon = { Icon(type.icon(), null, Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                        }
                    }
                    if (kind == RecordKind.FILE) {
                        Text("直接选择小文件，类型会自动识别。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onFile, enabled = available, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("选择文件") }
                    } else {
                        if (kind == RecordKind.APPLICATION) OutlinedButton(onClick = onApplication, enabled = available, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Apps, null); Spacer(Modifier.width(8.dp)); Text("选择已安装应用") }
                        if (kind == RecordKind.CONTACT) OutlinedButton(onClick = onContact, enabled = available, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.PersonSearch, null); Spacer(Modifier.width(8.dp)); Text("从通讯录选择") }
                        QuickFields(kind, value, secondary, tertiary, { vm.updateEntry(value = it); error = null }, { vm.updateEntry(secondary = it); error = null }, { vm.updateEntry(tertiary = it); error = null }, available)
                        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { add(false) }, enabled = available && value.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("添加到列表，继续录入") }
                    }
                }
            }
            if (state.draft.isNotEmpty() || state.importedMessage != null) item {
                SectionTitle("待写入 · ${bytes?.size ?: 0} B", "清空") { clear = true }
            }
            items(state.draft, key = { it.id }) { record ->
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(record.kind.icon(), null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(if (record.kind == RecordKind.APPLICATION) record.secondary.ifBlank { record.kind.label } else record.kind.label, fontWeight = FontWeight.SemiBold)
                            Text(record.summary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (record.kind != RecordKind.FILE) IconButton(onClick = { onEdit(record) }, enabled = available) { Icon(Icons.Outlined.Edit, "编辑${record.kind.label}", Modifier.size(20.dp)) }
                        IconButton(onClick = { vm.remove(record.id) }, enabled = available) { Icon(Icons.Outlined.Close, "移除${record.kind.label}", Modifier.size(20.dp)) }
                    }
                }
            }
            if (state.importedMessage != null) item {
                AppCard {
                    Text("已导入 NDEF · ${bytes?.size ?: 0} B", fontWeight = FontWeight.SemiBold)
                    val records = remember(state.importedMessage) { runCatching { NdefCodec.decode(NdefCodec.message(state.importedMessage)) }.getOrDefault(emptyList()) }
                    records.take(8).forEach { Text("${it.title}：${it.content.take(100)}", style = MaterialTheme.typography.bodyMedium) }
                    TextButton(onClick = { vm.editImported() }, enabled = available) { Text("转为可编辑记录") }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("连续写多张卡", fontWeight = FontWeight.Medium)
                        Text(if (state.batchWrite) "已完成 ${state.writtenCount} 张，写完后继续等待下一张" else "开启后，逐张贴卡写入相同内容", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.batchWrite, onCheckedChange = vm::batch, enabled = available)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImport, enabled = available, modifier = Modifier.weight(1f)) { Text("导入备份") }
                    OutlinedButton(onClick = { bytes?.let(onExport) }, enabled = available && bytes != null, modifier = Modifier.weight(1f)) { Text("导出备份") }
                }
                Text("会覆盖目标标签原有 NDEF 内容。文件受卡片容量限制。", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.device != DeviceStatus.READY) Text(if (state.device == DeviceStatus.DISABLED) "NFC 未开启，可先录入" else "无 NFC，可先录入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { if (state.pending != null) vm.cancel() else add(true) }, enabled = !state.busy && (state.pending != null || state.device == DeviceStatus.READY),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(if (state.pending != null) Icons.Outlined.Close else Icons.Outlined.Nfc, null); Spacer(Modifier.width(8.dp))
                    Text(if (state.pending != null) "停止等待贴卡" else "贴卡写入", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("清空待写入内容？") }, text = { Text("卡片上的内容不受影响。") }, confirmButton = { TextButton(onClick = { vm.clearDraft(); vm.clearEntry(); clear = false }) { Text("清空") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("取消") } })
}

@Composable
fun QuickFields(kind: RecordKind, value: String, secondary: String, tertiary: String, onValue: (String) -> Unit, onSecondary: (String) -> Unit, onTertiary: (String) -> Unit, enabled: Boolean = true) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val label = when (kind) {
        RecordKind.TEXT -> "文本内容"; RecordKind.URI -> "网址"; RecordKind.WIFI -> "Wi-Fi 名称"; RecordKind.CONTACT -> "姓名"
        RecordKind.APPLICATION -> "应用包名"; RecordKind.PHONE, RecordKind.SMS -> "电话号码"; RecordKind.EMAIL -> "收件人邮箱"
        RecordKind.GEO -> "纬度"; RecordKind.CUSTOM_URI -> "应用链接 / URI"; RecordKind.HEX -> "十六进制数据"; RecordKind.FILE -> "文件"
    }
    OutlinedTextField(value, onValue, modifier = Modifier.fillMaxWidth(), enabled = enabled, label = { Text(label) },
        singleLine = kind !in listOf(RecordKind.TEXT, RecordKind.HEX), minLines = if (kind in listOf(RecordKind.TEXT, RecordKind.HEX)) 3 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = when (kind) { RecordKind.URI, RecordKind.CUSTOM_URI -> KeyboardType.Uri; RecordKind.PHONE, RecordKind.SMS -> KeyboardType.Phone; RecordKind.EMAIL -> KeyboardType.Email; else -> KeyboardType.Text }),
        supportingText = when (kind) { RecordKind.URI -> ({ Text("example.com 即可，自动补全 https://") }); RecordKind.APPLICATION -> ({ Text("碰标签打开应用；接收手机需安装该 App") }); RecordKind.WIFI -> ({ Text("也可粘贴 WIFI: 格式的二维码文字自动填入") }); RecordKind.GEO -> ({ Text("-90 至 90，支持负数") }); else -> null })
    val secondLabel = when (kind) { RecordKind.WIFI -> "密码（开放网络留空）"; RecordKind.CONTACT -> "电话（可选）"; RecordKind.SMS -> "短信内容"; RecordKind.EMAIL -> "主题（可选）"; RecordKind.GEO -> "经度"; RecordKind.HEX -> "MIME 类型（默认 application/octet-stream）"; else -> null }
    if (secondLabel != null) OutlinedTextField(secondary, onSecondary, modifier = Modifier.fillMaxWidth(), enabled = enabled, label = { Text(secondLabel) }, singleLine = kind != RecordKind.SMS,
        keyboardOptions = KeyboardOptions(keyboardType = when (kind) { RecordKind.WIFI -> KeyboardType.Password; RecordKind.CONTACT -> KeyboardType.Phone; else -> KeyboardType.Text }),
        visualTransformation = if (kind == RecordKind.WIFI && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (kind == RecordKind.WIFI) ({ IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "隐藏密码" else "显示密码") } }) else null)
    if (kind == RecordKind.CONTACT) OutlinedTextField(tertiary, onTertiary, modifier = Modifier.fillMaxWidth(), enabled = enabled, label = { Text("邮箱（可选）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
}
