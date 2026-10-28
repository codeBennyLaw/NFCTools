package com.benny.nfctools.data

import java.util.UUID

enum class RecordKind(val label: String, val description: String) {
    TEXT("文本", "便签、说明或任意文字"),
    URI("网址", "轻触即可打开的链接"),
    WIFI("Wi-Fi", "分享网络名称和密码"),
    CONTACT("联系人", "姓名、电话与邮箱"),
    PHONE("电话", "快速拨号"),
    EMAIL("邮件", "收件人和邮件主题"),
    SMS("短信", "号码与预设内容"),
    GEO("位置", "经纬度坐标"),
    FILE("文件", "将小文件作为 MIME 数据写入"),
    APPLICATION("打开应用", "选择手机里的 App，轻触打开"),
    SYSTEM("系统功能", "音量、亮度、手电筒、勿扰与系统设置"),
    CUSTOM_URI("自定义链接", "应用深层链接或其他 URI"),
    HEX("HEX 数据", "自定义 MIME 类型与十六进制内容"),
}

data class DraftRecord(
    val id: String = UUID.randomUUID().toString(),
    val kind: RecordKind = RecordKind.TEXT,
    val value: String = "",
    val secondary: String = "",
    val tertiary: String = "",
    val mime: String = "application/octet-stream",
    val base64: String = "",
) {
    val summary: String get() = when (kind) {
        RecordKind.WIFI -> "$value · Wi-Fi 网络"
        RecordKind.FILE -> value
        else -> value
    }
}

data class DecodedRecord(
    val title: String,
    val content: String,
    val type: String,
    val size: Int,
    val hex: String,
)

data class TagSnapshot(
    val uid: String,
    val technologies: List<String>,
    val type: String,
    val capacity: Int?,
    val writable: Boolean?,
    val canLock: Boolean,
    val formatable: Boolean,
    val details: Map<String, String>,
    val messageBase64: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String = "读取",
    val sample: Boolean = false,
    val memory: MemoryDump? = null,
) {
    val id: String get() = "$timestamp-$uid"
}

enum class DeviceStatus { READY, DISABLED, UNSUPPORTED }
enum class ReadSource { NFC, CARD, HISTORY, FILE }
enum class Destination(val label: String) { READ("读取"), WRITE("写入"), CARDS("卡包"), TOOLS("工具"), HISTORY("记录") }
enum class OperationKind(val label: String) { WRITE("写入内容"), ERASE("擦除 NDEF"), FORMAT("格式化 NDEF"), LOCK("永久只读"), READ_MEMORY("读取内存") }
data class PendingOperation(val kind: OperationKind, val bytes: ByteArray? = null, val keyHex: String = "FFFFFFFFFFFF", val keyB: Boolean = false, val pageCount: Int = 16)
data class MemoryDump(val format: String, val blockSize: Int, val blocks: List<String?>, val notes: List<String> = emptyList())
enum class CardCategory(val label: String) { TAG("普通标签"), ACCESS("门禁"), TRANSIT("交通"), CAMPUS("校园"), OTHER("其他") }
data class SavedCard(val id: String = UUID.randomUUID().toString(), val name: String, val category: CardCategory = CardCategory.TAG, val tag: TagSnapshot)

data class AppState(
    val device: DeviceStatus = DeviceStatus.UNSUPPORTED,
    val destination: Destination = Destination.READ,
    val current: TagSnapshot? = null,
    val currentSource: ReadSource = ReadSource.NFC,
    val currentCardName: String? = null,
    val draft: List<DraftRecord> = emptyList(),
    val importedMessage: String? = null,
    val history: List<TagSnapshot> = emptyList(),
    val pending: PendingOperation? = null,
    val busy: Boolean = false,
    val historyLoaded: Boolean = false,
    val darkMode: String = "system",
    val cards: List<SavedCard> = emptyList(),
    val composerKind: RecordKind = RecordKind.TEXT,
    val activeEntry: DraftRecord = DraftRecord(),
    val batchWrite: Boolean = false,
    val writtenCount: Int = 0,
    val error: String? = null,
)
