package com.benny.nfctools.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.benny.nfctools.NfcViewModel
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.CardComparison
import com.benny.nfctools.nfc.NdefCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardsScreen(state: AppState, vm: NfcViewModel, onCompare: () -> Unit) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var managingId by rememberSaveable { mutableStateOf<String?>(null) }
    val cards = state.cards.filter { (category == null || it.category.name == category) && (it.name.contains(query, true) || it.tag.uid.contains(query, true)) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("我的卡包 · ${state.cards.size}", "比较卡片", onCompare) }
        item { OutlinedTextField(query, { query = it }, label = { Text("搜索名称或卡号") }, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth()) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("全部") }, modifier = Modifier.heightIn(min = 48.dp))
                CardCategory.entries.forEach { c -> FilterChip(selected = category == c.name, onClick = { category = c.name }, label = { Text(c.label) }, modifier = Modifier.heightIn(min = 48.dp)) }
            }
        }
        if (cards.isEmpty()) item { EmptyState(Icons.Outlined.Wallet, if (state.cards.isEmpty()) "把常用卡片收进这里" else "没有匹配的卡片", "读卡后点“保存到卡包”，可以改名、分类和比较。") }
        items(cards, key = { it.id }) { card ->
            Surface(onClick = { vm.openCard(card.id) }, enabled = !state.busy, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(if (card.category == CardCategory.ACCESS) Icons.Outlined.DoorFront else Icons.Outlined.CreditCard)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(card.name, fontWeight = FontWeight.SemiBold)
                        Text("${card.category.label} · ${card.tag.type}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(card.tag.uid, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text(remember(card.tag.messageBase64) { NdefCodec.summary(card.tag.messageBase64) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { managingId = card.id }) { Icon(Icons.Outlined.MoreVert, "管理${card.name}") }
                }
            }
        }
        item { Text("点击卡片查看本机最新记录；读写成功后自动更新已收藏卡片。门禁模拟需系统钱包支持。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    state.cards.firstOrNull { it.id == managingId }?.let { card ->
        CardManageDialog(card, onDismiss = { managingId = null }, onSave = { vm.updateCard(it); managingId = null }, onDelete = { vm.deleteCard(card.id); managingId = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardManageDialog(card: SavedCard, onDismiss: () -> Unit, onSave: (SavedCard) -> Unit, onDelete: () -> Unit) {
    var name by rememberSaveable(card.id) { mutableStateOf(card.name) }
    var category by rememberSaveable(card.id) { mutableStateOf(card.category.name) }
    var deleting by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (deleting) "删除卡包记录？" else "管理卡片") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (deleting) Text("只删除本机卡包记录，不会修改实体卡片。") else {
                OutlinedTextField(name, { name = it }, label = { Text("卡片名称") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CardCategory.entries.forEach { c -> FilterChip(selected = category == c.name, onClick = { category = c.name }, label = { Text(c.label) }, modifier = Modifier.heightIn(min = 48.dp)) } }
                TextButton(onClick = { deleting = true }) { Text("从卡包删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }, confirmButton = { TextButton(onClick = { if (deleting) onDelete() else onSave(card.copy(name = name.trim(), category = CardCategory.valueOf(category))) }, enabled = deleting || name.isNotBlank()) { Text(if (deleting) "删除" else "保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonSheet(state: AppState, onDismiss: () -> Unit) {
    val candidates = remember(state.current, state.cards, state.history) {
        (listOfNotNull(state.current?.let { "当前：${it.type}" to it }) + state.cards.map { it.name to it.tag } + state.history.take(10).map { it.uid to it }).distinctBy { it.second.id }
    }
    var firstId by rememberSaveable { mutableStateOf(candidates.firstOrNull()?.second?.id) }
    var secondId by rememberSaveable { mutableStateOf(candidates.getOrNull(1)?.second?.id) }
    var selecting by remember { mutableStateOf<Int?>(null) }
    val a = candidates.firstOrNull { it.second.id == firstId }
    val b = candidates.firstOrNull { it.second.id == secondId }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("卡片比较", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            item { OutlinedButton(onClick = { selecting = 0 }, modifier = Modifier.fillMaxWidth()) { Text("A · ${a?.first ?: "选择第一张卡"}") } }
            item { OutlinedButton(onClick = { selecting = 1 }, modifier = Modifier.fillMaxWidth()) { Text("B · ${b?.first ?: "选择第二张卡"}") } }
            if (a == null || b == null) item { InfoNote("先读取并保存两张卡片，也可选择最近的读取记录或导入的 Dump。") }
            else {
                val lines = CardComparison.compare(a.second, b.second)
                item { Text("${lines.count { !it.equal }} 项不同 / ${lines.size} 项", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
                items(lines) { line ->
                    AppCard {
                        Text("${if (line.equal) "相同" else "不同"} · ${line.label}", fontWeight = FontWeight.SemiBold)
                        Text("A：${line.first.take(180)}", style = MaterialTheme.typography.bodyMedium)
                        Text("B：${line.second.take(180)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    val first = a.second.messageBase64?.let(NdefCodec::fromBase64) ?: byteArrayOf()
                    val second = b.second.messageBase64?.let(NdefCodec::fromBase64) ?: byteArrayOf()
                    val offsets = CardComparison.differentOffsets(first, second)
                    InfoNote("NDEF 有 ${offsets.size} 个字节位置不同${if (offsets.isEmpty()) "" else "，前 32 个偏移：${offsets.take(32).joinToString { "0x" + it.toString(16).uppercase() }}"}。UID 不同不代表内容不同。")
                }
            }
        }
    }
    if (selecting != null) AlertDialog(onDismissRequest = { selecting = null }, title = { Text("选择卡片") }, text = {
        LazyColumn(Modifier.heightIn(max = 400.dp)) {
            items(candidates, key = { it.second.id }) { candidate -> TextButton(onClick = { if (selecting == 0) firstId = candidate.second.id else secondId = candidate.second.id; selecting = null }, modifier = Modifier.fillMaxWidth()) { Text("${candidate.first}\n${candidate.second.uid}") } }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = { selecting = null }) { Text("取消") } })
}

@Composable
fun AdvancedToolsScreen(state: AppState, onOperation: (OperationKind) -> Unit, onImport: () -> Unit, onImportDump: () -> Unit, onCompare: () -> Unit, onSample: () -> Unit, onNfcSettings: () -> Unit, onWallet: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("卡片与数据") }
        item { ActionRow(Icons.AutoMirrored.Outlined.CompareArrows, "卡片比较", "比较 UID、协议、NDEF 与内存数据", onClick = onCompare) }
        item { ActionRow(Icons.Outlined.Memory, "读取扇区 / 页", "MIFARE Classic 密钥读取、Ultralight 页") { onOperation(OperationKind.READ_MEMORY) } }
        item { ActionRow(Icons.Outlined.FileOpen, "导入 Dump", "查看 .mct / .bin / HEX 文本镜像", onClick = onImportDump) }
        item { ActionRow(Icons.Outlined.Restore, "导入 NDEF 备份", "恢复内容，可直接复制或转为编辑", onClick = onImport) }
        item { SectionTitle("标签管理") }
        item { ActionRow(Icons.Outlined.CleaningServices, "擦除内容", "清空下一张标签的 NDEF 记录", true) { onOperation(OperationKind.ERASE) } }
        item { ActionRow(Icons.Outlined.RestartAlt, "格式化为 NDEF", "初始化支持格式化的标签") { onOperation(OperationKind.FORMAT) } }
        item { ActionRow(Icons.Outlined.Lock, "设为永久只读", "永久禁止再写入，无法撤销", true) { onOperation(OperationKind.LOCK) } }
        item { SectionTitle("设备与兼容性") }
        item { ActionRow(Icons.Outlined.Nfc, "NFC 设置", when (state.device) { DeviceStatus.READY -> "已开启，可直接贴卡"; DeviceStatus.DISABLED -> "未开启，点击进入系统设置"; else -> "当前设备不支持 NFC" }, onClick = onNfcSettings) }
        item { ActionRow(Icons.Outlined.Wallet, "打开系统钱包", "荣耀门禁添加与模拟以系统钱包支持为准", onClick = onWallet) }
        item { InfoNote("${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\n已针对 Android 17 的 NFC 分发与前台读卡配置适配；MagicOS 11 实际表现需荣耀设备验证。") }
        item { Text("扇区读取只使用你提供的密钥，不进行密码字典尝试。Dump 导入用于查看与比较。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { TextButton(onClick = onSample, modifier = Modifier.fillMaxWidth()) { Text("查看示例标签") } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryReadDialog(onDismiss: () -> Unit, onRead: (String, Boolean, Int) -> Unit) {
    var key by rememberSaveable { mutableStateOf("FFFFFFFFFFFF") }
    var useB by rememberSaveable { mutableStateOf(false) }
    var pages by rememberSaveable { mutableIntStateOf(16) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("读取原始内存") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Classic 使用一个已知密钥逐扇区读取；Ultralight 读取指定页数。")
            OutlinedTextField(key, { key = it.uppercase().filterNot(Char::isWhitespace) }, label = { Text("Classic 密钥（12 位 HEX）") }, singleLine = true, supportingText = { Text("FFFFFFFFFFFF 是常见出厂密钥，可替换为你的密钥") }, isError = !key.matches(Regex("[A-F0-9]{12}")))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !useB, onClick = { useB = false }, label = { Text("Key A") }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = useB, onClick = { useB = true }, label = { Text("Key B") }, modifier = Modifier.heightIn(min = 48.dp))
            }
            Text("Ultralight / Type 2 读取页数")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(16, 48, 144, 240).forEach { count -> FilterChip(selected = pages == count, onClick = { pages = count }, label = { Text("$count 页") }, modifier = Modifier.heightIn(min = 48.dp)) } }
        }
    }, confirmButton = { TextButton(onClick = { onRead(key, useB, pages) }, enabled = key.matches(Regex("[A-F0-9]{12}"))) { Text("准备读取") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private data class LaunchableApp(val label: String, val packageName: String)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationPicker(onDismiss: () -> Unit, onSelect: (String, String) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                val pm = context.packageManager; val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolved = if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0)) else pm.queryIntentActivities(intent, 0)
                resolved.map { LaunchableApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }.distinctBy { it.packageName }.sortedBy { it.label }
            }
        }.onSuccess { apps = it }.onFailure { failure = it.message ?: "无法获取应用列表" }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("选择要打开的 App", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("搜索应用名称或包名") }, singleLine = true)
            if (apps == null && failure == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failure != null) Text(failure!!, color = MaterialTheme.colorScheme.error)
            val filtered = apps.orEmpty().filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
            if (apps != null && filtered.isEmpty()) Text("没有匹配应用，也可以返回手动输入包名。")
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    TextButton(onClick = { onSelect(app.packageName, app.label) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Apps, null); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) { Text(app.label); Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
