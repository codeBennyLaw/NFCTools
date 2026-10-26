package com.benny.nfctools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.benny.nfctools.BuildConfig
import com.benny.nfctools.data.DraftRecord
import com.benny.nfctools.data.RecordKind
import com.benny.nfctools.nfc.NdefCodec

@Composable
fun RecordEditor(record: DraftRecord, onDismiss: () -> Unit, onSave: (DraftRecord) -> Unit) {
    var value by rememberSaveable(record.id) { mutableStateOf(record.value) }
    var secondary by rememberSaveable(record.id) { mutableStateOf(record.secondary) }
    var tertiary by rememberSaveable(record.id) { mutableStateOf(record.tertiary) }
    var error by rememberSaveable(record.id) { mutableStateOf<String?>(null) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val kind = record.kind
    val firstLabel = when (kind) {
        RecordKind.TEXT -> "文本内容"; RecordKind.URI -> "完整网址"; RecordKind.WIFI -> "网络名称（SSID）";
        RecordKind.CONTACT -> "联系人姓名"; RecordKind.PHONE, RecordKind.SMS -> "电话号码";
        RecordKind.EMAIL -> "收件人邮箱"; RecordKind.GEO -> "纬度"; RecordKind.FILE -> "文件名称";
        RecordKind.APPLICATION -> "应用包名"; RecordKind.CUSTOM_URI -> "应用链接 / URI"; RecordKind.HEX -> "十六进制数据"; RecordKind.DELAY -> "等待秒数（1–600）"
    }
    val secondLabel = when (kind) { RecordKind.WIFI -> "密码（开放网络留空）"; RecordKind.CONTACT -> "电话号码（可选）"; RecordKind.EMAIL -> "邮件主题（可选）"; RecordKind.SMS -> "短信内容（可选）"; RecordKind.GEO -> "经度"; RecordKind.HEX -> "MIME 类型（默认 application/octet-stream）"; else -> null }
    val keyboard = when (kind) { RecordKind.PHONE, RecordKind.SMS -> KeyboardType.Phone; RecordKind.EMAIL -> KeyboardType.Email; RecordKind.URI -> KeyboardType.Uri; RecordKind.GEO -> KeyboardType.Text; else -> KeyboardType.Text }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(kind.icon(), null) },
        title = { Text("${if (record.value.isEmpty()) "添加" else "编辑"}${kind.label}") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(kind.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = value, onValueChange = { value = it; error = null }, label = { Text(firstLabel) },
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    singleLine = kind != RecordKind.TEXT, minLines = if (kind == RecordKind.TEXT) 4 else 1,
                    isError = error != null,
                    supportingText = when (kind) { RecordKind.URI -> ({ Text("例如：https://example.com") }); RecordKind.GEO -> ({ Text("范围 -90 至 90，支持负数") }); else -> null })
                if (secondLabel != null) OutlinedTextField(value = secondary, onValueChange = { secondary = it; error = null }, label = { Text(secondLabel) },
                    modifier = Modifier.fillMaxWidth(), singleLine = kind != RecordKind.SMS,
                    keyboardOptions = KeyboardOptions(keyboardType = when (kind) { RecordKind.WIFI -> KeyboardType.Password; RecordKind.CONTACT -> KeyboardType.Phone; else -> KeyboardType.Text }),
                    visualTransformation = if (kind == RecordKind.WIFI && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    trailingIcon = if (kind == RecordKind.WIFI) ({ IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (passwordVisible) "隐藏密码" else "显示密码") } }) else null,
                    supportingText = if (kind == RecordKind.GEO) ({ Text("范围 -180 至 180，支持负数") }) else null)
                if (kind == RecordKind.CONTACT) OutlinedTextField(value = tertiary, onValueChange = { tertiary = it; error = null }, label = { Text("邮箱（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                if (kind == RecordKind.WIFI) Text("有密码的网络按 WPA2-Personal / AES 编码。手机是否自动连接取决于系统支持。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = {
            val draft = record.copy(value = value, secondary = secondary, tertiary = tertiary)
            runCatching { NdefCodec.encode(listOf(draft)) }.onSuccess { onSave(draft) }.onFailure { error = it.message ?: "请检查填写内容" }
        }) { Text("保存记录") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(mode: String, onDismiss: () -> Unit, onTheme: (String) -> Unit, onNfcSettings: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("按你的习惯使用", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("外观", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (key, label) ->
                    FilterChip(selected = mode == key, onClick = { onTheme(key) }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            OutlinedButton(onClick = onNfcSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.Settings, null); Spacer(Modifier.width(8.dp)); Text("系统 NFC 设置") }
            Text("一些使用提示", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            InfoNote("读卡时保持 App 在前台、手机已解锁。贴近手机背面的 NFC 感应区，并等待操作完成。厚手机壳和金属附件可能影响感应。")
            InfoNote("NDEF 是标签内容格式，卡片能否读写取决于芯片与手机。银行卡、交通卡和加密门禁卡通常只能显示公开的技术信息。")
            InfoNote("擦除只会清空 NDEF 内容；设为永久只读无法撤销。退出 App 或打开其他应用会取消尚未执行的贴卡操作。")
            InfoNote("草稿、文件内容和最近 50 条读卡记录仅保存在本机。Wi-Fi 密码、联系人等内容也可能包含在本地记录和导出文件中，可在记录页清空。", Icons.Outlined.Shield)
            Text("NFC 工具箱  ${BuildConfig.VERSION_NAME}\n轻触之间，连接日常。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
