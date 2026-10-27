package com.benny.nfctools.system

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.benny.nfctools.data.DraftRecord
import com.benny.nfctools.data.RecordKind
import org.json.JSONObject

enum class SystemFunction(val label: String, val options: List<Pair<String, String>>, val help: String) {
    MEDIA_VOLUME("媒体音量", emptyList(), "设置音乐、视频的音量百分比"),
    RING_VOLUME("铃声音量", emptyList(), "设置来电铃声音量；可能影响响铃模式，需要勿扰权限"),
    ALARM_VOLUME("闹钟音量", emptyList(), "设置闹钟音量百分比，设为零可能听不到闹钟"),
    NOTIFICATION_VOLUME("通知音量", emptyList(), "设置通知音量；部分手机与铃声音量同步，需要勿扰权限"),
    RINGER("响铃模式", listOf("normal" to "响铃", "vibrate" to "振动", "silent" to "静音"), "需要勿扰权限；以系统实际允许的模式为准"),
    BRIGHTNESS("屏幕亮度", emptyList(), "需要修改系统设置权限；同时关闭自动亮度"),
    AUTO_BRIGHTNESS("自动亮度", switches(), "需要修改系统设置权限及光线传感器支持"),
    AUTO_ROTATE("自动旋转", switches(), "需要修改系统设置权限；关闭后使用系统固定方向"),
    SCREEN_TIMEOUT("自动息屏", listOf("15000" to "15 秒", "30000" to "30 秒", "60000" to "1 分钟", "120000" to "2 分钟", "300000" to "5 分钟", "600000" to "10 分钟"), "需要修改系统设置权限；可能受省电或设备策略限制"),
    TORCH("手电筒", switches(), "需要相机权限和闪光灯；相机被占用时可能无法使用"),
    DND("工具箱勿扰", switches(), "需要勿扰权限；Android 15 及以上仅控制本 App 的勿扰规则，关闭不会解除其他勿扰规则"),
    WIFI_SETTINGS("Wi-Fi 设置", emptyList(), "打开系统 Wi-Fi 面板，由你确认开关"),
    BLUETOOTH_SETTINGS("蓝牙设置", emptyList(), "打开系统蓝牙设置，由你确认开关"),
    INTERNET_SETTINGS("网络与移动数据", emptyList(), "打开系统网络面板，由你确认开关"),
    NFC_SETTINGS("NFC 设置", emptyList(), "打开 NFC 设置；关闭后需手动重新开启才能读卡"),
    LOCATION_SETTINGS("定位设置", emptyList(), "打开系统定位设置，由你确认开关"),
    AIRPLANE_SETTINGS("飞行模式设置", emptyList(), "打开系统飞行模式设置，由你确认开关"),
    HOTSPOT_SETTINGS("热点设置", emptyList(), "打开系统网络共享设置，由你确认开关"),
    BATTERY_SETTINGS("省电模式设置", emptyList(), "打开系统省电设置，由你确认开关"),
    CAST_SETTINGS("投屏设置", emptyList(), "打开系统投屏设置，选择接收设备"),
    VPN_SETTINGS("VPN 设置", emptyList(), "打开系统 VPN 设置，选择已有连接");

    val isPanel get() = ordinal >= WIFI_SETTINGS.ordinal
    val isPercent get() = this in listOf(MEDIA_VOLUME, RING_VOLUME, ALARM_VOLUME, NOTIFICATION_VOLUME, BRIGHTNESS)
    val defaultValue get() = if (isPercent) "50" else options.firstOrNull()?.first.orEmpty()
}

private fun switches() = listOf("on" to "开启", "off" to "关闭")

data class SystemAction(val function: SystemFunction, val argument: String = function.defaultValue) {
    init {
        require(when {
            function.isPercent -> argument.toIntOrNull()?.let { it in (if (function == SystemFunction.BRIGHTNESS) 1 else 0)..100 } == true
            function.isPanel -> argument.isEmpty()
            else -> function.options.any { it.first == argument }
        }) { "系统操作参数无效" }
    }
    val description get() = function.label + when {
        function.isPercent -> " · $argument%"
        function.isPanel -> " · 打开设置"
        else -> " · ${function.options.first { it.first == argument }.second}"
    }
    fun draft() = DraftRecord(kind = RecordKind.SYSTEM, value = function.name, secondary = argument)
    fun json(): String = JSONObject().put("version", 1).put("function", function.name).put("argument", argument).toString()
    fun record(): NdefRecord = NdefRecord.createMime(MIME, json().toByteArray(Charsets.UTF_8))

    companion object {
        const val MIME = "application/vnd.com.benny.nfctools.system-action+json"
        fun fromDraft(draft: DraftRecord): SystemAction {
            val function = SystemFunction.entries.firstOrNull { it.name == draft.value } ?: error("请选择系统功能")
            return SystemAction(function, draft.secondary)
        }
        fun parse(json: String): SystemAction {
            require(json.toByteArray(Charsets.UTF_8).size <= 1024) { "系统操作数据过大" }
            val data = JSONObject(json)
            require(data.get("version") == 1) { "不支持的系统操作版本" }
            return SystemAction(SystemFunction.valueOf(data.getString("function")), data.getString("argument"))
        }
        fun isRecord(record: NdefRecord) = record.tnf == NdefRecord.TNF_MIME_MEDIA && record.toMimeType() == MIME
        fun fromRecord(record: NdefRecord): SystemAction? = if (isRecord(record)) parse(record.payload.toString(Charsets.UTF_8)) else null
        // Never execute one item out of a legacy multi-operation card.
        fun fromMessage(message: NdefMessage?): SystemAction? = message?.records?.singleOrNull()?.let(::fromRecord)
    }
}
