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
