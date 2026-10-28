package com.benny.nfctools

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.benny.nfctools.data.DraftRecord
import com.benny.nfctools.data.RecordKind
import com.benny.nfctools.nfc.NdefCodec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NdefCodecTest {
    @Test fun chineseTextAndUriSurviveBinaryRoundTrip() {
        val drafts = listOf(DraftRecord(value = "你好，世界\n第二行 NFC"), DraftRecord(kind = RecordKind.URI, value = "https://example.com/a?b=1"))
        val original = NdefMessage(drafts.map(NdefCodec::encodeRecord).toTypedArray())
        val restored = NdefCodec.parse(NdefCodec.fromBase64(NdefCodec.base64(original.toByteArray())))
        val records = NdefCodec.decode(restored)
        assertEquals(drafts[0].value, records[0].content)
        assertEquals(drafts[1].value, records[1].content)
        assertArrayEquals(original.toByteArray(), restored.toByteArray())
    }
    @Test fun utf16TextAndLanguagePrefixAreDecoded() {
        val payload = byteArrayOf(0x82.toByte()) + "en".toByteArray() + "你好 NFC".toByteArray(Charsets.UTF_16)
        val record = NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, byteArrayOf(), payload)
        assertEquals("你好 NFC", NdefCodec.decode(NdefMessage(arrayOf(record))).single().content)
    }
    @Test fun malformedTextCannotCrashReader() {
        val record = NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, byteArrayOf(), byteArrayOf(63, 1))
        assertEquals("无法解析的记录", NdefCodec.decode(NdefMessage(arrayOf(record))).single().title)
    }
    @Test fun binaryFilePayloadIsPreserved() {
        val bytes = byteArrayOf(0, 1, 2, 0xff.toByte(), 64)
        val d = DraftRecord(kind = RecordKind.FILE, value = "test.bin", base64 = NdefCodec.base64(bytes))
        val message = NdefCodec.encode(listOf(d))
        assertArrayEquals(bytes, message.records.single().payload)
        assertEquals("application/octet-stream", message.records.single().toMimeType())
    }
    @Test fun mailAndSmsCannotInjectUriParameters() {
        val sms = NdefCodec.encodeRecord(DraftRecord(kind = RecordKind.SMS, value = "+86 13800138000", secondary = "a&body=bad #你好"))
        assertTrue(sms.toUri().toString().contains("a%26body%3Dbad%20%23"))
        assertThrows(IllegalArgumentException::class.java) { NdefCodec.encodeRecord(DraftRecord(kind = RecordKind.PHONE, value = "123?body=bad")) }
    }
    @Test fun vcardEscapesUntrustedFieldNewlines() {
        val r = NdefCodec.encodeRecord(DraftRecord(kind = RecordKind.CONTACT, value = "张三\r\nTEL:evil;test,yes"))
        val text = r.payload.toString(Charsets.UTF_8)
        assertTrue(text.contains("FN:张三\\nTEL:evil\\;test\\,yes"))
        assertFalse(text.contains("\r\nTEL:evil"))
    }
    @Test fun wifiCredentialContainsStandardWscFields() {
        val record = NdefCodec.encodeRecord(DraftRecord(kind = RecordKind.WIFI, value = "Home 中文", secondary = "password123"))
        assertEquals("application/vnd.wfa.wsc", record.toMimeType())
        val outer = tlv(record.payload)
        val fields = tlv(outer.getValue(0x100e))
        assertEquals("Home 中文", fields.getValue(0x1045).toString(Charsets.UTF_8))
        assertArrayEquals(byteArrayOf(0, 0x20), fields.getValue(0x1003))
        assertArrayEquals(byteArrayOf(0, 8), fields.getValue(0x100f))
        assertEquals("password123", fields.getValue(0x1027).toString(Charsets.UTF_8))
        val open = tlv(tlv(NdefCodec.encodeRecord(DraftRecord(kind = RecordKind.WIFI, value = "Guest")).payload).getValue(0x100e))
        assertArrayEquals(byteArrayOf(0, 1), open.getValue(0x1003))
        assertTrue(open.getValue(0x1027).isEmpty())
    }
    @Test fun invalidCoordinatesUrlsAndPasswordsAreRejected() {
        val invalid = listOf(
            DraftRecord(kind = RecordKind.GEO, value = "NaN", secondary = "1"),
            DraftRecord(kind = RecordKind.GEO, value = "91", secondary = "1"),
            DraftRecord(kind = RecordKind.GEO, value = "1", secondary = "181"),
            DraftRecord(kind = RecordKind.URI, value = "javascript:alert(1)"),
            DraftRecord(kind = RecordKind.URI, value = "https://"),
            DraftRecord(kind = RecordKind.WIFI, value = "家".repeat(11)),
            DraftRecord(kind = RecordKind.WIFI, value = "Home", secondary = "123"),
            DraftRecord(kind = RecordKind.EMAIL, value = "a@"),
        )
        invalid.forEach { d -> assertThrows(d.kind.name, IllegalArgumentException::class.java) { NdefCodec.encodeRecord(d) } }
    }
    @Test fun validNegativeCoordinatesAreAccepted() {
        assertEquals("geo:-33.9,151.2", NdefCodec.encodeRecord(DraftRecord(kind = RecordKind.GEO, value = "-33.9", secondary = "151.2")).toUri().toString())
    }
    @Test fun messageBoundIncludesRecordOverhead() {
        val d = DraftRecord(kind = RecordKind.FILE, value = "full.bin", base64 = NdefCodec.base64(ByteArray(NdefCodec.MAX_FILE_BYTES)))
        assertThrows(IllegalArgumentException::class.java) { NdefCodec.encode(listOf(d)) }
        assertThrows(IllegalArgumentException::class.java) { NdefCodec.encode(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { NdefCodec.encode(List(33) { DraftRecord(value = "x") }) }
    }
    @Test fun boundedImportNeverReadsAnOversizeFileIntoMemory() {
        assertThrows(IllegalArgumentException::class.java) { ByteArrayInputStream(ByteArray(4097)).readBytesBounded(4096) }
        assertEquals(4096, ByteArrayInputStream(ByteArray(4096)).readBytesBounded(4096).size)
    }
    @Test fun corruptedNdefIsRejectedAndEmptyNdefIsRecognized() {
        assertThrows(Exception::class.java) { NdefCodec.parse(byteArrayOf(1, 2, 3)) }
        val empty = NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, byteArrayOf(), byteArrayOf(), byteArrayOf())))
        assertEquals("空记录", NdefCodec.decode(NdefCodec.parse(empty.toByteArray())).single().title)
    }
    private fun tlv(bytes: ByteArray): Map<Int, ByteArray> {
        val result = mutableMapOf<Int, ByteArray>()
        var i = 0
        while (i < bytes.size) {
            val type = ((bytes[i].toInt() and 255) shl 8) or (bytes[i + 1].toInt() and 255)
            val length = ((bytes[i + 2].toInt() and 255) shl 8) or (bytes[i + 3].toInt() and 255)
            i += 4
            result[type] = bytes.copyOfRange(i, i + length)
            i += length
        }
        return result
    }
}
