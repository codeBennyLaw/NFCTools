package com.benny.nfctools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickWriteScreen(state: AppState, vm: NfcViewModel, onFile: () -> Unit, onContact: () -> Unit, onApplication: () -> Unit,
                     onImport: () -> Unit, onExport: (ByteArray) -> Unit) {
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var more by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val kind = state.composerKind
    val bytes = remember(state.draft, state.importedMessage, state.activeEntry) { vm.messageBytes() }
    val imported = remember(state.importedMessage) { runCatching { NdefCodec.message(state.importedMessage) }.getOrNull() }
    val records = remember(imported) { NdefCodec.decode(imported) }
    val file = state.draft.singleOrNull()?.takeIf { it.kind == RecordKind.FILE }
    val available = !state.busy && state.pending == null
    fun write() {
        if (state.importedMessage == null && kind != RecordKind.FILE) {
            val record = state.activeEntry
            val problem = runCatching { NdefCodec.encode(listOf(record)) }.exceptionOrNull()
            if (problem != null) { error = problem.message; return }
            if (!vm.add(record)) return
        }
        if (vm.messageBytes() == null) { error = if (kind == RecordKind.FILE) "请先选择文件" else "请先选择一个有效操作"; return }
        error = null
        vm.arm(OperationKind.WRITE)
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                AppCard {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) { Text("录入一个操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        Row {
                            TextButton(onClick = { clipboard.getText()?.text?.let(vm::paste) ?: vm.notify("剪贴板没有文字") }, enabled = available) { Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("粘贴识别") }
                            TextButton(onClick = { more = !more }) { Text(if (more) "收起" else "更多") }
                        }
                    }
                    Text("每张卡只录入一个操作，写入会替换卡片上的原内容。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val kinds = if (more) RecordKind.entries.toList() else listOf(RecordKind.TEXT, RecordKind.URI, RecordKind.WIFI, RecordKind.APPLICATION, RecordKind.CONTACT, RecordKind.FILE)
                        kinds.forEach { type ->
                            FilterChip(selected = kind == type && state.importedMessage == null, onClick = { vm.selectKind(type); error = null; when (type) { RecordKind.APPLICATION -> onApplication(); RecordKind.CONTACT -> onContact(); RecordKind.FILE -> onFile(); else -> Unit } }, enabled = available,
                                label = { Text(type.label) }, leadingIcon = { Icon(type.icon(), null, Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                        }
                    }
                    when {
                        state.importedMessage != null -> {
                            Text(if (NdefCodec.isSingleOperation(imported)) "已导入：${records.firstOrNull()?.title ?: "NDEF"}" else "旧备份包含多条内容，需先选择其中一个操作", fontWeight = FontWeight.Medium)
                            records.take(3).forEach { Text(it.content.take(200), style = MaterialTheme.typography.bodySmall) }
                            OutlinedButton(onClick = vm::editImported, enabled = available, modifier = Modifier.fillMaxWidth()) { Text(if (NdefCodec.isSingleOperation(imported)) "编辑当前操作" else "查看并选择一个操作") }
                        }
                        kind == RecordKind.FILE -> {
                            Text(file?.let { "已选择：${it.value}" } ?: "选择要写入的小文件", style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = { error = null; onFile() }, enabled = available, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(if (file == null) "选择文件" else "更换文件") }
                        }
                        else -> {
                            if (kind == RecordKind.APPLICATION) OutlinedButton(onClick = onApplication, enabled = available, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Apps, null); Spacer(Modifier.width(8.dp)); Text("选择已安装应用") }
                            if (kind == RecordKind.CONTACT) OutlinedButton(onClick = onContact, enabled = available, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.PersonSearch, null); Spacer(Modifier.width(8.dp)); Text("从通讯录选择") }
                            QuickFields(kind, state.activeEntry.value, state.activeEntry.secondary, state.activeEntry.tertiary, { vm.updateEntry(value = it); error = null }, { vm.updateEntry(secondary = it); error = null }, { vm.updateEntry(tertiary = it); error = null }, available)
                        }
                    }
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    if (bytes != null) Text("当前操作 · ${bytes.size} B", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.draft.isNotEmpty() || state.importedMessage != null || listOf(state.activeEntry.value, state.activeEntry.secondary, state.activeEntry.tertiary).any { it.isNotBlank() })
                        TextButton(onClick = { vm.clearDraft(); error = null }, enabled = available) { Text("清空当前操作") }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("连续写多张卡", fontWeight = FontWeight.Medium)
                        Text(if (state.batchWrite) "已完成 ${state.writtenCount} 张，继续写入同一个操作" else "将当前这一个操作写入多张卡", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.batchWrite, onCheckedChange = vm::batch, enabled = available)
                }
            }
            item {
                val backup = imported?.toByteArray() ?: bytes
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImport, enabled = available, modifier = Modifier.weight(1f)) { Text("导入备份") }
                    OutlinedButton(onClick = { backup?.let(onExport) }, enabled = available && backup != null, modifier = Modifier.weight(1f)) { Text("导出备份") }
                }
                Text("文件大小受标签容量限制。", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.device != DeviceStatus.READY) Text(if (state.device == DeviceStatus.DISABLED) "NFC 未开启，可先录入" else "无 NFC，可先录入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { if (state.pending != null) vm.cancel() else write() }, enabled = !state.busy && (state.pending != null || state.device == DeviceStatus.READY && (state.importedMessage == null || NdefCodec.isSingleOperation(imported))), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(if (state.pending != null) Icons.Outlined.Close else Icons.Outlined.Nfc, null); Spacer(Modifier.width(8.dp)); Text(if (state.pending != null) "停止等待贴卡" else "贴卡写入", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
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
