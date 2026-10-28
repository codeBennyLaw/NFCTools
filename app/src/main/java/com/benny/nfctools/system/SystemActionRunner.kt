package com.benny.nfctools.system

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlin.math.roundToInt

enum class SystemPermission(val label: String, val explanation: String) {
    WRITE_SETTINGS("修改系统设置", "用于调整手机亮度、自动旋转和息屏时间"),
    DND("勿扰权限", "用于调整响铃模式、铃声音量或工具箱的勿扰规则"),
    CAMERA("相机权限", "仅用于控制闪光灯手电筒，不拍照或录像")
}

sealed interface ActionResult {
    data class Done(val message: String) : ActionResult
    data class Permission(val permission: SystemPermission) : ActionResult
    data class Failed(val message: String) : ActionResult
}

class SystemActionRunner(private val context: Context) {
    private val audio get() = context.getSystemService(AudioManager::class.java)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    fun permission(action: SystemAction): SystemPermission? = when {
        action.function in listOf(SystemFunction.BRIGHTNESS, SystemFunction.AUTO_BRIGHTNESS, SystemFunction.AUTO_ROTATE, SystemFunction.SCREEN_TIMEOUT) && !Settings.System.canWrite(context) -> SystemPermission.WRITE_SETTINGS
        action.function in listOf(SystemFunction.DND, SystemFunction.RINGER, SystemFunction.RING_VOLUME, SystemFunction.NOTIFICATION_VOLUME) && !notifications.isNotificationPolicyAccessGranted -> SystemPermission.DND
        action.function == SystemFunction.TORCH && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED -> SystemPermission.CAMERA
        else -> null
    }

    fun execute(action: SystemAction): ActionResult {
        permission(action)?.let { return ActionResult.Permission(it) }
        return try {
            val on = action.argument == "on"
            when (action.function) {
                SystemFunction.MEDIA_VOLUME, SystemFunction.RING_VOLUME, SystemFunction.ALARM_VOLUME, SystemFunction.NOTIFICATION_VOLUME -> {
                    check(!audio.isVolumeFixed) { "此设备不允许调整音量" }
                    val stream = when (action.function) {
                        SystemFunction.RING_VOLUME -> AudioManager.STREAM_RING
                        SystemFunction.ALARM_VOLUME -> AudioManager.STREAM_ALARM
                        SystemFunction.NOTIFICATION_VOLUME -> AudioManager.STREAM_NOTIFICATION
                        else -> AudioManager.STREAM_MUSIC
                    }
                    val min = if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(stream) else 0
                    val target = (audio.getStreamMaxVolume(stream) * action.argument.toInt() / 100f).roundToInt().coerceAtLeast(min)
                    audio.setStreamVolume(stream, target, 0)
                    check(audio.getStreamVolume(stream) == target) { "系统未应用目标音量，请检查音量限制" }
                }
                SystemFunction.RINGER -> {
                    val mode = when (action.argument) { "silent" -> AudioManager.RINGER_MODE_SILENT; "vibrate" -> AudioManager.RINGER_MODE_VIBRATE; else -> AudioManager.RINGER_MODE_NORMAL }
                    audio.ringerMode = mode
                    check(audio.ringerMode == mode) { "此设备未应用所选响铃模式" }
                }
                SystemFunction.BRIGHTNESS -> {
                    put(Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    put(Settings.System.SCREEN_BRIGHTNESS, (action.argument.toInt() * 255 / 100f).roundToInt().coerceIn(1, 255))
                }
                SystemFunction.AUTO_BRIGHTNESS -> {
                    if (on) check(context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_LIGHT) != null) { "此设备没有光线传感器" }
                    put(Settings.System.SCREEN_BRIGHTNESS_MODE, if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                }
                SystemFunction.AUTO_ROTATE -> put(Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
                SystemFunction.SCREEN_TIMEOUT -> put(Settings.System.SCREEN_OFF_TIMEOUT, action.argument.toInt())
                SystemFunction.TORCH -> {
                    val cameras = context.getSystemService(CameraManager::class.java)
                    val id = cameras.cameraIdList.firstOrNull { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                        ?: error("此设备没有可用的闪光灯")
                    cameras.setTorchMode(id, on)
                }
                SystemFunction.DND -> notifications.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
                else -> {
                    openPanel(action.function)
                    return ActionResult.Done("已打开${action.function.label}，请在系统中操作")
                }
            }
            ActionResult.Done("已执行：${action.description}")
        } catch (e: Exception) {
            ActionResult.Failed(if (e is SecurityException) "系统拒绝了此操作，请检查权限或系统限制" else e.message ?: "执行失败，请检查系统设置")
        }
    }

    private fun put(key: String, value: Int) {
        check(Settings.System.putInt(context.contentResolver, key, value)) { "系统未接受此设置" }
        check(Settings.System.getInt(context.contentResolver, key) == value) { "系统未应用此设置，可能受设备策略限制" }
    }

    fun permissionIntent(permission: SystemPermission): Intent = when (permission) {
        SystemPermission.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())
        SystemPermission.DND -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        SystemPermission.CAMERA -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    }

    private fun openPanel(function: SystemFunction) {
        val action = when (function) {
            SystemFunction.WIFI_SETTINGS -> if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
            SystemFunction.BLUETOOTH_SETTINGS -> Settings.ACTION_BLUETOOTH_SETTINGS
            SystemFunction.INTERNET_SETTINGS -> if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_DATA_ROAMING_SETTINGS
            SystemFunction.NFC_SETTINGS -> Settings.ACTION_NFC_SETTINGS
            SystemFunction.LOCATION_SETTINGS -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            SystemFunction.AIRPLANE_SETTINGS -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            SystemFunction.HOTSPOT_SETTINGS -> "android.settings.TETHER_SETTINGS"
            SystemFunction.BATTERY_SETTINGS -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            SystemFunction.CAST_SETTINGS -> Settings.ACTION_CAST_SETTINGS
            SystemFunction.VPN_SETTINGS -> Settings.ACTION_VPN_SETTINGS
            else -> error("不是系统设置入口")
        }
        try { context.startActivity(Intent(action)) }
        catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(Intent(if (function in listOf(SystemFunction.WIFI_SETTINGS, SystemFunction.INTERNET_SETTINGS, SystemFunction.HOTSPOT_SETTINGS, SystemFunction.NFC_SETTINGS)) Settings.ACTION_WIRELESS_SETTINGS else Settings.ACTION_SETTINGS))
        }
    }
}
