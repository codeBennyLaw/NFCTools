package com.benny.nfctools.nfc

import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.util.Base64
import androidx.core.net.toUri
import com.benny.nfctools.data.DecodedRecord
import com.benny.nfctools.data.DraftRecord
import com.benny.nfctools.data.RecordKind
import java.io.ByteArrayOutputStream

object NdefCodec {
    const val MAX_FILE_BYTES = 48 * 1024
    const val SEQUENCE_TYPE = "com.benny.nfctools:sequence"
    const val DELAY_TYPE = "com.benny.nfctools:delay"
    private const val PACKAGE = "com.benny.nfctools"
    fun isSequence(message: NdefMessage?): Boolean {
        val records = message?.records ?: return false
        return records.size >= 3 && records[0].tnf == NdefRecord.TNF_EXTERNAL_TYPE &&
            records[0].type.toString(Charsets.US_ASCII) == SEQUENCE_TYPE && records[0].id.isEmpty() && records[0].payload.contentEquals(byteArrayOf(1)) &&
            records[1].tnf == NdefRecord.TNF_EXTERNAL_TYPE && records[1].type.toString(Charsets.US_ASCII) == "android.com:pkg" &&
            records[1].id.isEmpty() && records[1].payload.toString(Charsets.UTF_8) == PACKAGE
    }
    fun visibleRecords(message: NdefMessage?): List<NdefRecord> =
        message?.records?.drop(if (isSequence(message)) 2 else 0) ?: emptyList()

    fun encode(records: List<DraftRecord>, sequence: Boolean = false): NdefMessage {
        require(records.isNotEmpty()) { "请先添加至少一条记录" }
        require(records.size <= 32) { "最多添加 32 条记录" }
        val routing = if (sequence) listOf(NdefRecord.createExternal(PACKAGE, "sequence", byteArrayOf(1)), NdefRecord.createApplicationRecord(PACKAGE)) else emptyList()
        return NdefMessage((routing + records.map(::encodeRecord)).toTypedArray()).also {
            require(it.toByteArray().size <= MAX_FILE_BYTES) { "总数据不能超过 48 KB，请缩小文件或减少记录" }
        }
    }

    fun encodeRecord(d: DraftRecord): NdefRecord = when (d.kind) {
        RecordKind.DELAY -> {
            val seconds = d.value.toIntOrNull()
            require(seconds != null && seconds in 1..600) { "等待时间应为 1–600 秒的整数" }
            NdefRecord.createExternal(PACKAGE, "delay", seconds.toString().toByteArray(Charsets.US_ASCII))
        }
        RecordKind.TEXT -> {
            require(d.value.isNotBlank()) { "请输入文本内容" }
            NdefRecord.createTextRecord("zh", d.value)
        }
        RecordKind.URI -> {
            val raw = d.value.trim()
            val normalized = if (!raw.contains("://") && raw.matches(Regex("(?i)(?:www\\.)?[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}(?:/\\S*)?"))) "https://$raw" else raw
            val uri = normalized.toUri()
            require(uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.host!!.none(Char::isWhitespace)) { "请输入有效网址，例如 example.com 或 https://example.com" }
            NdefRecord.createUri(uri)
        }
        RecordKind.APPLICATION -> {
            require(d.value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+"))) { "请选择应用，或输入有效包名" }
            NdefRecord.createApplicationRecord(d.value)
        }
        RecordKind.CUSTOM_URI -> {
            require(d.value.trim().matches(Regex("[A-Za-z][A-Za-z0-9+.-]*:[^\\s]+"))) { "请输入包含协议的链接，例如 myapp://home" }
            NdefRecord.createUri(d.value.trim())
        }
        RecordKind.HEX -> {
            val bytes = hexBytes(d.value)
            require(bytes.isNotEmpty()) { "请输入十六进制内容" }
            NdefRecord.createMime(d.secondary.ifBlank { d.mime }, bytes)
        }
        RecordKind.PHONE -> NdefRecord.createUri("tel:${phone(d.value)}")
        RecordKind.SMS -> NdefRecord.createUri("sms:${phone(d.value)}?body=${Uri.encode(d.secondary)}")
        RecordKind.EMAIL -> {
            require(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(d.value.trim())) { "请输入有效的邮箱地址" }
            NdefRecord.createUri("mailto:${d.value.trim()}?subject=${Uri.encode(d.secondary)}")
        }
        RecordKind.GEO -> {
            val lat = d.value.toDoubleOrNull()
            val lon = d.secondary.toDoubleOrNull()
            require(lat != null && lat.isFinite() && lat in -90.0..90.0) { "纬度应在 -90 到 90 之间" }
            require(lon != null && lon.isFinite() && lon in -180.0..180.0) { "经度应在 -180 到 180 之间" }
            NdefRecord.createUri("geo:$lat,$lon")
        }
        RecordKind.CONTACT -> {
            require(d.value.isNotBlank()) { "请输入联系人姓名" }
            val card = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:${vcard(d.value)}\r\nTEL:${vcard(d.secondary)}\r\nEMAIL:${vcard(d.tertiary)}\r\nEND:VCARD\r\n"
            NdefRecord.createMime("text/vcard", card.toByteArray(Charsets.UTF_8))
        }
        RecordKind.WIFI -> wifi(d.value, d.secondary)
        RecordKind.FILE -> {
            val bytes = fromBase64(d.base64)
            require(bytes.isNotEmpty()) { "文件为空，请重新选择" }
            require(bytes.size <= MAX_FILE_BYTES) { "文件不能超过 48 KB" }
            require(d.mime.matches(Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+"))) { "文件 MIME 类型无效" }
            NdefRecord.createMime(d.mime, bytes)
        }
    }

    private fun phone(text: String): String {
        val value = text.trim().replace(" ", "").replace("-", "")
        require(value.matches(Regex("\\+?[0-9]{3,20}"))) { "请输入有效电话号码（3–20 位数字，可带 +）" }
        return value
    }

    private fun vcard(value: String) = value.replace("\\", "\\\\").replace("\r\n", "\n").replace("\r", "\n")
        .replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,")

    // NFC Forum Wi-Fi Simple Configuration credential, WPA2-Personal/AES or open.
    private fun wifi(ssid: String, password: String): NdefRecord {
        val name = ssid.toByteArray(Charsets.UTF_8)
        require(name.size in 1..32) { "Wi-Fi 名称需为 1–32 字节" }
        require(password.isEmpty() || (password.length in 8..63 && password.all { it.code in 32..126 })) { "WPA2 密码需为 8–63 个 ASCII 字符；开放网络留空" }
        val credential = ByteArrayOutputStream()
        fun field(type: Int, bytes: ByteArray) {
            credential.write(byteArrayOf((type shr 8).toByte(), type.toByte(), (bytes.size shr 8).toByte(), bytes.size.toByte()))
            credential.write(bytes)
        }
        field(0x1026, byteArrayOf(1))
        field(0x1045, name)
        field(0x1003, byteArrayOf(0, if (password.isEmpty()) 1 else 0x20))
        field(0x100f, byteArrayOf(0, if (password.isEmpty()) 1 else 8))
        field(0x1027, password.toByteArray(Charsets.UTF_8))
        field(0x1020, ByteArray(6) { 0xff.toByte() })
        val value = credential.toByteArray()
        val payload = byteArrayOf(0x10, 0x0e, (value.size shr 8).toByte(), value.size.toByte()) + value
        return NdefRecord.createMime("application/vnd.wfa.wsc", payload)
    }

    fun summary(encoded: String?): String = runCatching {
        val message = message(encoded) ?: return "暂无 NDEF 内容"
        val records = visibleRecords(message)
        if (records.all { it.tnf == NdefRecord.TNF_EMPTY }) return "内容已清空"
        val types = decode(message).map { it.title }.distinct().take(3).joinToString(" · ")
        "$types · ${records.size} ${if (isSequence(message)) "步" else "条"}"
    }.getOrDefault("NDEF 内容无法解析")

    fun decode(message: NdefMessage?): List<DecodedRecord> = visibleRecords(message).map { r ->
        var title = "原始数据"
        var content = r.payload.hex()
        val type = r.type.toString(Charsets.US_ASCII)
        try {
            when {
                r.tnf == NdefRecord.TNF_EMPTY -> { title = "空记录"; content = "此标签的 NDEF 内容已清空" }
                r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                    title = "文本"
                    val status = r.payload.first().toInt() and 0xff
                    val start = 1 + (status and 0x3f)
                    require(start <= r.payload.size)
                    content = r.payload.copyOfRange(start, r.payload.size).toString(if (status and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8)
                }
                r.tnf == NdefRecord.TNF_EXTERNAL_TYPE && type == "android.com:pkg" -> { title = "打开应用"; content = r.payload.toString(Charsets.UTF_8) }
                r.tnf == NdefRecord.TNF_EXTERNAL_TYPE && type == DELAY_TYPE -> { title = "等待"; content = "${r.payload.toString(Charsets.US_ASCII)} 秒" }
                r.toUri() != null -> { title = "链接 / URI"; content = r.toUri().toString() }
                r.tnf == NdefRecord.TNF_MIME_MEDIA -> {
                    title = when (type) { "text/vcard", "text/x-vcard" -> "联系人"; "application/vnd.wfa.wsc" -> "Wi-Fi 配置"; else -> "文件 / MIME" }
                    content = when {
                        type == "application/vnd.wfa.wsc" -> describeWifi(r.payload)
                        type.startsWith("text/") || type == "application/json" -> r.payload.toString(Charsets.UTF_8)
                        else -> "二进制内容 · ${r.payload.size} 字节\n${r.payload.take(64).toByteArray().hex()}${if (r.payload.size > 64) " …" else ""}"
                    }
                }
            }
        } catch (_: RuntimeException) { title = "无法解析的记录" }
        val raw = NdefMessage(arrayOf(r)).toByteArray()
        DecodedRecord(title, content, "TNF ${r.tnf} · ${type.ifEmpty { "无类型" }}", raw.size, raw.hex())
    }

    fun parse(bytes: ByteArray): NdefMessage {
        require(bytes.isNotEmpty() && bytes.size <= MAX_FILE_BYTES) { "NDEF 数据大小需为 1 字节至 48 KB" }
        return NdefMessage(bytes).also { require(it.records.size <= 128) { "此文件包含过多记录，最多支持 128 条" } }
    }
    private fun describeWifi(payload: ByteArray): String {
        fun fields(bytes: ByteArray): Map<Int, ByteArray> {
            var i = 0
            val result = mutableMapOf<Int, ByteArray>()
            while (i + 4 <= bytes.size) {
                val type = ((bytes[i].toInt() and 255) shl 8) or (bytes[i + 1].toInt() and 255)
                val length = ((bytes[i + 2].toInt() and 255) shl 8) or (bytes[i + 3].toInt() and 255)
                i += 4
                require(length <= bytes.size - i)
                result[type] = bytes.copyOfRange(i, i + length)
                i += length
            }
            return result
        }
        val credential = fields(payload)[0x100e] ?: return "Wi-Fi WSC 配置 · ${payload.size} B"
        val values = fields(credential)
        val ssid = values[0x1045]?.toString(Charsets.UTF_8) ?: "未知网络"
        val protected = values[0x1027]?.isNotEmpty() == true
        return "网络名称：$ssid\n${if (protected) "包含网络凭据，可通过 HEX 或导出查看" else "开放网络"}"
    }
    fun message(base64: String?): NdefMessage? = base64?.let { parse(fromBase64(it)) }
    fun editable(message: NdefMessage): List<DraftRecord> = visibleRecords(message).filter { it.tnf != NdefRecord.TNF_EMPTY }.map { r ->
        require(r.id.isEmpty()) { "此记录带有自定义 ID，请使用原样复制以保留数据" }
        val type = r.type.toString(Charsets.US_ASCII)
        when {
            r.tnf == NdefRecord.TNF_EXTERNAL_TYPE && type == DELAY_TYPE -> DraftRecord(kind = RecordKind.DELAY, value = r.payload.toString(Charsets.US_ASCII)).also { encodeRecord(it) }
            r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_TEXT) -> DraftRecord(value = decode(NdefMessage(arrayOf(r))).single().content)
            r.tnf == NdefRecord.TNF_EXTERNAL_TYPE && type == "android.com:pkg" -> DraftRecord(kind = RecordKind.APPLICATION, value = r.payload.toString(Charsets.UTF_8))
            type == "application/vnd.wfa.wsc" -> {
                val credential = tlv(r.payload)[0x100e] ?: error("此 Wi-Fi 配置不能直接编辑，可使用原样复制")
                val values = tlv(credential)
                val auth = values[0x1003]
                require(auth?.contentEquals(byteArrayOf(0, 1)) == true || auth?.contentEquals(byteArrayOf(0, 0x20)) == true) { "此 Wi-Fi 安全类型不能直接编辑，请使用原样复制" }
                DraftRecord(kind = RecordKind.WIFI, value = values[0x1045]?.toString(Charsets.UTF_8).orEmpty(), secondary = values[0x1027]?.toString(Charsets.UTF_8).orEmpty())
            }
            type == "text/vcard" || type == "text/x-vcard" -> QuickEntry.parse(r.payload.toString(Charsets.UTF_8))
            r.tnf == NdefRecord.TNF_MIME_MEDIA -> DraftRecord(kind = RecordKind.FILE, value = type, mime = type, base64 = base64(r.payload))
            r.toUri() != null && r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_URI) -> {
                val value = r.toUri().toString()
                QuickEntry.parse(value).let { if (it.kind == RecordKind.TEXT) it.copy(kind = RecordKind.CUSTOM_URI) else it }
            }
            else -> error("此内容包含不可编辑的记录，请使用原样复制以保留全部数据")
        }
    }
    private fun tlv(bytes: ByteArray): Map<Int, ByteArray> {
        val values = mutableMapOf<Int, ByteArray>(); var i = 0
        while (i < bytes.size) {
            require(i + 4 <= bytes.size) { "Wi-Fi 数据不完整" }
            val type = ((bytes[i].toInt() and 255) shl 8) or (bytes[i + 1].toInt() and 255)
            val size = ((bytes[i + 2].toInt() and 255) shl 8) or (bytes[i + 3].toInt() and 255)
            i += 4; require(i + size <= bytes.size) { "Wi-Fi 数据不完整" }
            values[type] = bytes.copyOfRange(i, i + size); i += size
        }
        return values
    }
    fun hexBytes(value: String): ByteArray {
        val hex = value.replace(Regex("\\s+"), "")
        require(hex.length % 2 == 0 && hex.matches(Regex("[a-fA-F0-9]*"))) { "HEX 需为偶数位十六进制字符，可包含空格和换行" }
        require(hex.length / 2 <= MAX_FILE_BYTES) { "HEX 数据超过 48 KB" }
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun fromBase64(value: String): ByteArray = Base64.decode(value, Base64.DEFAULT)
    fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
}
