package com.benny.nfctools.nfc

import android.net.Uri
import androidx.core.net.toUri
import com.benny.nfctools.data.DraftRecord
import com.benny.nfctools.data.RecordKind

object QuickEntry {
    fun parse(text: String): DraftRecord {
        val value = text.trim()
        require(value.isNotEmpty()) { "剪贴板没有文字" }
        if (value.startsWith("WIFI:", true)) {
            val fields = splitEscaped(value.substring(5)).mapNotNull {
                val i = it.indexOf(':'); if (i > 0) it.substring(0, i).uppercase() to it.substring(i + 1) else null
            }.toMap()
            require(fields["T"].orEmpty().uppercase() !in listOf("WPA-EAP", "WPA3", "SAE")) { "暂不支持此 Wi-Fi 安全类型" }
            return DraftRecord(kind = RecordKind.WIFI, value = fields["S"].orEmpty(), secondary = fields["P"].orEmpty())
        }
        if (value.startsWith("BEGIN:VCARD", true)) {
            fun field(name: String) = value.lineSequence().firstOrNull { it.substringBefore(':').substringBefore(';').equals(name, true) }
                ?.substringAfter(':')?.replace("\\n", "\n")?.replace("\\,", ",")?.replace("\\;", ";")?.replace("\\\\", "\\").orEmpty()
            return DraftRecord(kind = RecordKind.CONTACT, value = field("FN").ifEmpty { field("N").replace(';', ' ') }, secondary = field("TEL"), tertiary = field("EMAIL"))
        }
        val uri = value.toUri()
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> DraftRecord(kind = RecordKind.URI, value = value)
            "tel" -> DraftRecord(kind = RecordKind.PHONE, value = uri.schemeSpecificPart)
            "mailto" -> DraftRecord(kind = RecordKind.EMAIL, value = uri.schemeSpecificPart.substringBefore('?'), secondary = query(value, "subject"))
            "sms", "smsto" -> DraftRecord(kind = RecordKind.SMS, value = uri.schemeSpecificPart.substringBefore('?'), secondary = query(value, "body"))
            "geo" -> {
                val parts = uri.schemeSpecificPart.substringBefore('?').split(',')
                DraftRecord(kind = RecordKind.GEO, value = parts.firstOrNull().orEmpty(), secondary = parts.getOrNull(1).orEmpty())
            }
            else -> when {
                value.matches(Regex("[+()0-9 -]{5,24}")) -> DraftRecord(kind = RecordKind.PHONE, value = value.replace("(", "").replace(")", ""))
                value.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) -> DraftRecord(kind = RecordKind.EMAIL, value = value)
                value.matches(Regex("(?i)(?:www\\.)?[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}(?:/\\S*)?")) -> DraftRecord(kind = RecordKind.URI, value = "https://$value")
                else -> DraftRecord(value = text)
            }
        }
    }
    private fun query(value: String, key: String): String = value.substringAfter('?', "").split('&')
        .firstOrNull { it.substringBefore('=') == key }?.substringAfter('=')?.let(Uri::decode).orEmpty()
    private fun splitEscaped(value: String): List<String> {
        val fields = mutableListOf<String>(); val current = StringBuilder(); var escaped = false
        value.forEach { c -> when { escaped -> { current.append(c); escaped = false }; c == '\\' -> escaped = true; c == ';' -> { fields += current.toString(); current.clear() }; else -> current.append(c) } }
        fields += current.toString()
        return fields
    }
}
