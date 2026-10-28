package com.benny.nfctools.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.benny.nfctools.NfcViewModel
import com.benny.nfctools.system.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SystemActionFields(value: String, argument: String, enabled: Boolean, onSelect: (SystemAction) -> Unit) {
    val function = SystemFunction.entries.firstOrNull { it.name == value }
    var expanded by remember { mutableStateOf(false) }
    var panels by rememberSaveable { mutableStateOf(function?.isPanel ?: false) }
    LaunchedEffect(function?.isPanel) { if (function != null) panels = function.isPanel }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !panels, onClick = { panels = false; if (function?.isPanel != false) onSelect(SystemAction(SystemFunction.MEDIA_VOLUME)) }, enabled = enabled, label = { Text("直接控制") }, modifier = Modifier.heightIn(min = 48.dp))
        FilterChip(selected = panels, onClick = { panels = true; if (function?.isPanel != true) onSelect(SystemAction(SystemFunction.WIFI_SETTINGS)) }, enabled = enabled, label = { Text("打开设置") }, modifier = Modifier.heightIn(min = 48.dp))
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = !expanded }) {
        OutlinedTextField(value = function?.label.orEmpty(), onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text("选择系统功能") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SystemFunction.entries.filter { it.isPanel == panels }.forEach { item ->
                DropdownMenuItem(text = { Text(item.label) }, onClick = { onSelect(SystemAction(item)); expanded = false })
            }
        }
    }
    if (function != null) {
        Text(function.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (function.isPercent) {
            Text("目标值：$argument%")
            Slider(value = argument.toFloatOrNull() ?: 50f, onValueChange = { onSelect(SystemAction(function, it.toInt().toString())) },
                valueRange = (if (function == SystemFunction.BRIGHTNESS) 1f else 0f)..100f, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "${function.label}目标值"; stateDescription = "$argument%" })
        } else if (!function.isPanel) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            function.options.forEach { (key, label) ->
                FilterChip(selected = argument == key, onClick = { onSelect(SystemAction(function, key)) }, enabled = enabled, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
    Text("接收手机需安装 NFC 工具箱并解锁。写入和查看本机记录不会自动执行；实际读卡时触发。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Permission prompts are shown only when an operation is actually requested. */
@Composable
fun SystemActionHost(vm: NfcViewModel) {
    val context = LocalContext.current
    val runner = remember(context) { SystemActionRunner(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preferences = remember(context) { context.getSharedPreferences("system_permissions", Context.MODE_PRIVATE) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var requested by rememberSaveable { mutableStateOf(false) }
    fun run(action: SystemAction) {
        when (val result = runner.execute(action)) {
            is ActionResult.Done -> { pending = null; vm.notify(result.message) }
            is ActionResult.Failed -> { pending = null; vm.notify(result.message) }
            is ActionResult.Permission -> { pending = action.json(); requested = false }
        }
    }
    fun returned() {
        val action = pending?.let { runCatching { SystemAction.parse(it) }.getOrNull() }
        pending = null
        requested = false
        if (action != null) {
            if (runner.permission(action) == null) run(action)
            else vm.notify("未授予所需权限，操作未执行；可稍后重新贴卡")
        }
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { returned() }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { returned() }
    LaunchedEffect(vm, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.systemRequests.collect { if (pending == null) run(it) }
        }
    }
    val action = pending?.let { runCatching { SystemAction.parse(it) }.getOrNull() }
    val permission = action?.let(runner::permission)
    val cameraSettings = permission == SystemPermission.CAMERA && preferences.getBoolean("camera_requested", false) &&
        (context as? Activity)?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } == true
    if (action != null && permission != null && !requested) AlertDialog(
        onDismissRequest = { pending = null },
        title = { Text("需要${permission.label}") },
        text = { Text("${action.description}\n\n${permission.explanation}。授权返回后执行本次操作；未授权则不执行。") },
        confirmButton = { TextButton(onClick = {
            requested = true
            try {
                if (permission == SystemPermission.CAMERA && !cameraSettings) {
                    preferences.edit().putBoolean("camera_requested", true).apply()
                    camera.launch(Manifest.permission.CAMERA)
                }
                else settings.launch(runner.permissionIntent(permission))
            } catch (_: Exception) { pending = null; requested = false; vm.notify("无法打开授权页面，请到系统设置中授权后重新贴卡") }
        }) { Text(if (cameraSettings) "到设置授权" else "去授权") } },
        dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } })
}
