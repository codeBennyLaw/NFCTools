package com.benny.nfctools.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benny.nfctools.data.*

fun RecordKind.icon(): ImageVector = when (this) {
    RecordKind.TEXT -> Icons.AutoMirrored.Outlined.Notes
    RecordKind.URI -> Icons.Outlined.Link
    RecordKind.WIFI -> Icons.Outlined.Wifi
    RecordKind.CONTACT -> Icons.Outlined.PersonOutline
    RecordKind.PHONE -> Icons.Outlined.Call
    RecordKind.EMAIL -> Icons.Outlined.MailOutline
    RecordKind.SMS -> Icons.Outlined.ChatBubbleOutline
    RecordKind.GEO -> Icons.Outlined.LocationOn
    RecordKind.FILE -> Icons.AutoMirrored.Outlined.InsertDriveFile
    RecordKind.APPLICATION -> Icons.Outlined.Apps
    RecordKind.SYSTEM -> Icons.Outlined.SettingsSuggest
    RecordKind.CUSTOM_URI -> Icons.AutoMirrored.Outlined.OpenInNew
    RecordKind.HEX -> Icons.Outlined.DataObject
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, danger: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(48.dp).background(if (danger) colors.errorContainer else colors.primaryContainer, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (danger) colors.onErrorContainer else colors.onPrimaryContainer, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun InfoNote(text: String, icon: ImageVector = Icons.Outlined.Info) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        Text(text, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, content: (@Composable () -> Unit)? = null) {
    AppCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(icon, Modifier.size(56.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content?.invoke()
        }
    }
}

@Composable
fun ScannerArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 180f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color.White.copy(alpha = .055f), 86 * unit, center)
        drawCircle(Color.White.copy(alpha = .09f), 65 * unit, center, style = Stroke(1.2f * unit))
        rotate(-12f, center) {
            val left = center.x - 36 * unit
            val top = center.y - 47 * unit
            drawRoundRect(Color.White.copy(alpha = .12f), Offset(left + 9 * unit, top - 7 * unit), Size(72 * unit, 94 * unit), CornerRadius(13 * unit))
            drawRoundRect(Color.White, Offset(left, top), Size(72 * unit, 94 * unit), CornerRadius(13 * unit))
            val blue = NfcBrand.blue
            drawLine(blue, Offset(left + 22 * unit, top + 57 * unit), Offset(left + 22 * unit, top + 35 * unit), 4 * unit, StrokeCap.Round)
            drawLine(blue, Offset(left + 22 * unit, top + 35 * unit), Offset(left + 42 * unit, top + 57 * unit), 4 * unit, StrokeCap.Round)
            drawLine(blue, Offset(left + 42 * unit, top + 57 * unit), Offset(left + 42 * unit, top + 35 * unit), 4 * unit, StrokeCap.Round)
            drawArc(blue, -50f, 100f, false, Offset(left + 34 * unit, top + 27 * unit), Size(23 * unit, 38 * unit), style = Stroke(3 * unit, cap = StrokeCap.Round))
            drawLine(blue.copy(alpha = .2f), Offset(left + 20 * unit, top + 76 * unit), Offset(left + 52 * unit, top + 76 * unit), 3 * unit, StrokeCap.Round)
        }
    }
}

@Composable
fun ActionRow(icon: ImageVector, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, danger = danger)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ReaderStatus(state: AppState, onSettings: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Outlined.Nfc, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(when { state.busy -> "正在读取，请保持贴近"; state.pending != null -> "请贴近目标卡片"; state.device == DeviceStatus.READY -> "贴近手机，即可读取"; state.device == DeviceStatus.DISABLED -> "NFC 未开启"; else -> "当前设备没有 NFC" }, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(when (state.device) { DeviceStatus.READY -> "NFC 已开启 · 无需手动开始"; DeviceStatus.DISABLED -> "打开系统 NFC 后返回即可"; DeviceStatus.UNSUPPORTED -> "仍可录入、管理备份与查看示例" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (state.device == DeviceStatus.DISABLED) TextButton(onClick = onSettings) { Text("开启") }
        }
    }
}
