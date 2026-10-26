package com.benny.nfctools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benny.nfctools.NfcViewModel
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import com.benny.nfctools.nfc.MemoryDumpCodec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PagePadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp)
private fun date(time: Long) = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(time))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadScreen(state: AppState, vm: NfcViewModel, openSettings: () -> Unit, onQuick: (RecordKind, String?) -> Unit, onExportPayload: (ByteArray, String) -> Unit, onExport: (TagSnapshot, Boolean) -> Unit) {
    LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (state.current == null || state.currentSource == ReadSource.NFC) item { ReaderStatus(state, openSettings) }
        if (state.current != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(if (state.current.sample) "示例标签 · 界面演示" else when (state.currentSource) {
                        ReadSource.CARD -> "卡包 · ${state.currentCardName.orEmpty()}"
                        ReadSource.HISTORY -> "历史记录"
                        ReadSource.FILE -> "导入的内存文件"
                        ReadSource.NFC -> "最近读取的标签"
                    })
                    if (state.currentSource == ReadSource.CARD) Text("本机最新记录 · ${date(state.current.timestamp)}，贴卡可更新", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { TagDetails(state.current, vm, onExport, onExportPayload, !state.busy, state.currentSource == ReadSource.CARD) }
        } else {
            item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(Icons.Outlined.Bolt to "即贴即读", Icons.Outlined.Shield to "本地处理", Icons.Outlined.DataObject to "标准 NDEF").forEach { (icon, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { SectionTitle("快捷操作", "全部类型") { vm.navigate(Destination.WRITE) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(RecordKind.TEXT, RecordKind.APPLICATION, RecordKind.FILE).forEach { kind ->
                    Surface(onClick = { onQuick(kind, null) }, enabled = !state.busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(kind.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                            Text(if (kind == RecordKind.APPLICATION) "打开 App" else "写入${kind.label}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Lightbulb, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("第一次使用 NFC？", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("解锁手机，将标签贴近背面的 NFC 感应区，保持片刻即可读取。", fontSize = 13.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        if (state.current == null) item {
            TextButton(onClick = vm::showSample, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("还没有标签？查看示例", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun TagDetails(tag: TagSnapshot, vm: NfcViewModel, onExport: (TagSnapshot, Boolean) -> Unit, onExportPayload: (ByteArray, String) -> Unit, enabled: Boolean, contentFirst: Boolean) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val message = remember(tag.messageBase64) { runCatching { NdefCodec.message(tag.messageBase64) }.getOrNull() }
    val records = remember(message) { NdefCodec.decode(message) }
    val size = message?.toByteArray()?.size ?: 0
    var raw by rememberSaveable(tag.id) { mutableStateOf(false) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Icons.Outlined.CreditCard)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tag.type, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(if (tag.sample) "演示数据 · 非真实卡片" else "${tag.action} · ${date(tag.timestamp)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        tag.details["NDEF 读取"]?.let { InfoNote(it) }
        if (!contentFirst) TagActions(tag, vm, enabled)
        if (records.isNotEmpty()) {
            if (records.size > 1 || NdefCodec.isSequence(message) || records.any { it.title == "等待" }) FilledTonalButton(onClick = { vm.prepareTasks(tag.messageBase64) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("按顺序执行 · ${records.size} 步")
            }
            SectionTitle("标签内容 · ${records.size} 条", if (raw) "收起 HEX" else "查看 HEX") { raw = !raw }
            if (records.size > 1) Text("可逐项查看，或使用工具箱按顺序执行；打开外部应用后，返回并确认继续下一步。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            records.forEachIndexed { index, record ->
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}. ${record.title}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    SelectionContainer { Text(record.content.take(4096), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (record.content.length > 4096) Text("内容较长，导出 NDEF 可查看完整数据", style = MaterialTheme.typography.bodySmall)
                    Text("${record.type} · ${record.size} B", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (raw) SelectionContainer { Text(record.hex.take(4096), fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    val original = NdefCodec.visibleRecords(message)[index]
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(record.content)); vm.notify("内容已复制") }) { Text("复制内容") }
                        if (record.title != "等待" && (original.toUri() != null || record.title == "打开应用")) TextButton(onClick = {
                            runCatching {
                                val intent = if (record.title == "打开应用") context.packageManager.getLaunchIntentForPackage(record.content) ?: error("手机未安装此应用")
                                else android.content.Intent(android.content.Intent.ACTION_VIEW, original.toUri())
                                context.startActivity(intent)
                            }.onFailure { vm.notify(it.message ?: "没有可处理此内容的应用") }
                        }, enabled = enabled) { Text(if (record.title == "打开应用") "打开 App" else "打开内容") }
                    }
                    if (original.tnf == android.nfc.NdefRecord.TNF_MIME_MEDIA) TextButton(onClick = {
                        val mime = original.toMimeType().orEmpty()
                        val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
                        onExportPayload(original.payload, "nfc-record-${index + 1}.$extension")
                    }, enabled = enabled) { Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("保存此文件") }
                }
            }
        } else InfoNote(if (tag.formatable) "尚未发现 NDEF 内容，可在工具页将此标签格式化。" else "此卡没有可读取的 NDEF 内容。已显示可获取的公开信息；加密区需专用协议和授权。")
        if (contentFirst) TagActions(tag, vm, enabled)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("UID / 序列号", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer { Text(tag.uid, fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(tag.uid)); vm.notify("UID 已复制") }) { Icon(Icons.Outlined.ContentCopy, "复制 UID", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
            }
            DetailLine("技术协议", tag.technologies.joinToString(" · "))
            DetailLine("写入状态", when (tag.writable) { true -> "可写入"; false -> "只读"; null -> if (tag.formatable) "可格式化为 NDEF" else "不支持 NDEF 写入" })
            if (tag.writable == true) DetailLine("设为只读", if (tag.canLock) "支持（永久生效）" else "不支持")
            if (tag.capacity != null) {
                DetailLine("NDEF 容量", "$size / ${tag.capacity} B")
                LinearProgressIndicator(progress = { if (tag.capacity > 0) (size.toFloat() / tag.capacity).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(5.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
            }
        }
        var expanded by rememberSaveable(tag.id) { mutableStateOf(false) }
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(if (expanded) "收起技术信息" else "展开技术信息"); Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
        if (expanded) tag.details.forEach { (label, value) -> DetailLine(label, value) }
        tag.memory?.let { dump ->
            var fullMemory by rememberSaveable(tag.id) { mutableStateOf(false) }
            Text("${dump.format} · ${dump.blocks.count { it != null }} / ${dump.blocks.size} 块可读", fontWeight = FontWeight.SemiBold)
            val shown = if (fullMemory) dump.blocks else dump.blocks.take(8)
            SelectionContainer {
                Text(shown.mapIndexed { index, block -> "${index.toString().padStart(3, '0')}: ${block ?: "?".repeat(dump.blockSize * 2)}" }.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            if (dump.blocks.size > 8) TextButton(onClick = { fullMemory = !fullMemory }) { Text(if (fullMemory) "收起内存" else "显示全部 ${dump.blocks.size} 块") }
            dump.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick = { onExportPayload(MemoryDumpCodec.export(dump), "nfc-memory-${tag.timestamp}.${if (dump.blockSize == 16) "mct" else "txt"}") }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("导出内存 Dump") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onExport(tag, false) }, enabled = enabled, modifier = Modifier.weight(1f)) { Text("导出报告", fontSize = 13.sp) }
            OutlinedButton(onClick = { onExport(tag, true) }, enabled = enabled && tag.messageBase64 != null, modifier = Modifier.weight(1f)) { Text("备份 NDEF", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun TagActions(tag: TagSnapshot, vm: NfcViewModel, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { vm.saveCard(tag) }, enabled = enabled && !tag.sample, modifier = Modifier.weight(1f)) { Text("保存到卡包", fontSize = 13.sp) }
        OutlinedButton(onClick = { vm.copyToNext(tag) }, enabled = enabled && tag.messageBase64 != null, modifier = Modifier.weight(1f)) { Text("复制到新卡", fontSize = 13.sp) }
    }
    if (tag.messageBase64 != null) OutlinedButton(onClick = { vm.editContents(tag) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.EditNote, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("编辑读到的内容")
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.widthIn(min = 78.dp).weight(.37f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer(Modifier.weight(.63f)) { Text(value, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun HistoryScreen(state: AppState, onOpen: (TagSnapshot) -> Unit, onClear: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(state.history, query) { state.history.filter { it.uid.contains(query, true) || it.type.contains(query, true) || it.action.contains(query, true) } }
    LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("每次轻触，都有迹可循。最近 50 次保存在本机。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        item { OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("搜索 UID、卡片类型或操作") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp)) }
        item { SectionTitle("${filtered.size} 条记录", if (state.history.isNotEmpty()) "清空记录" else null, onClear) }
        if (!state.historyLoaded) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (filtered.isEmpty()) item { EmptyState(Icons.Outlined.History, if (query.isEmpty()) "还没有轻触记录" else "没有找到匹配记录", if (query.isEmpty()) "读取一张 NFC 标签后，记录会出现在这里。" else "试试其他 UID 或卡片类型。") }
        items(filtered, key = { it.id }) { tag ->
            Surface(onClick = { onOpen(tag) }, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.CreditCard)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(tag.type, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(tag.uid, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${tag.action} · ${date(tag.timestamp)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
