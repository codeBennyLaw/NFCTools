package com.benny.nfctools

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickEntryTest {
    @Test fun wifiQrTextPreservesEscapedSeparators() {
        val entry = QuickEntry.parse("""WIFI:T:WPA;S:Cafe\;Guest;P:pa:ss\;word;;""")
        assertEquals(RecordKind.WIFI, entry.kind)
        assertEquals("Cafe;Guest", entry.value)
        assertEquals("pa:ss;word", entry.secondary)
        assertEquals(entry.value, NdefCodec.editable(NdefCodec.encode(listOf(entry))).single().value)
    }
    @Test fun commonClipboardValuesAreRecognizedWithoutRetyping() {
        val values = mapOf("example.com" to RecordKind.URI, "+86 13800138000" to RecordKind.PHONE,
            "hello@example.com" to RecordKind.EMAIL, "geo:-33.9,151.2" to RecordKind.GEO, "一段普通文字" to RecordKind.TEXT)
        values.forEach { (input, kind) -> assertEquals(input, kind, QuickEntry.parse(input).kind) }
        assertEquals("https://example.com", QuickEntry.parse("example.com").value)
    }
    @Test fun urlCanBeEnteredWithoutScheme() {
        assertEquals("https://example.com/test", NdefCodec.encodeRecord(DraftRecord(kind = RecordKind.URI, value = "example.com/test")).toUri().toString())
    }
    @Test fun applicationRecordCanBeReadAndEdited() {
        val entry = DraftRecord(kind = RecordKind.APPLICATION, value = "com.android.settings")
        val message = NdefCodec.encode(listOf(entry))
        assertEquals("打开应用", NdefCodec.decode(message).single().title)
        val restored = NdefCodec.editable(message).single()
        assertEquals(entry.kind, restored.kind)
        assertEquals(entry.value, restored.value)
        assertArrayEquals(message.toByteArray(), NdefCodec.encode(listOf(restored)).toByteArray())
    }
    @Test fun mixedRecordsBecomeEditableWithoutDroppingFilePayload() {
        val originals = listOf(DraftRecord(value = "你好"), DraftRecord(kind = RecordKind.URI, value = "https://example.com"),
            DraftRecord(kind = RecordKind.WIFI, value = "Home", secondary = "password123"),
            DraftRecord(kind = RecordKind.FILE, value = "raw.bin", base64 = NdefCodec.base64(byteArrayOf(0, 127, -1))))
        val message = NdefMessage(originals.map(NdefCodec::encodeRecord).toTypedArray())
        val edited = NdefCodec.editable(message)
        assertArrayEquals(message.toByteArray(), NdefMessage(edited.map(NdefCodec::encodeRecord).toTypedArray()).toByteArray())
        assertThrows(IllegalArgumentException::class.java) { NdefCodec.encode(edited) }
    }
    @Test fun uriQueryFieldsAreDecodedForEditing() {
        val entry = DraftRecord(kind = RecordKind.SMS, value = "+8613800138000", secondary = "你好 & NFC")
        val restored = NdefCodec.editable(NdefCodec.encode(listOf(entry))).single()
        assertEquals(entry.secondary, restored.secondary)
        assertEquals(entry.value, restored.value)
    }
    @Test fun rawHexRequiresCompleteBytes() {
        assertArrayEquals(byteArrayOf(0, 16, -1), NdefCodec.hexBytes("00 10\nFF"))
        assertThrows(IllegalArgumentException::class.java) { NdefCodec.hexBytes("123") }
        assertThrows(IllegalArgumentException::class.java) { NdefCodec.hexBytes("GG") }
    }
    @Test fun unknownRecordsKeepTheOriginalCopyPathInsteadOfLosingData() {
        val record = NdefRecord.createExternal("example.com", "custom", byteArrayOf(1, 2, 3))
        assertThrows(IllegalStateException::class.java) { NdefCodec.editable(NdefMessage(arrayOf(record))) }
    }
    @Test fun mctDumpRoundTripPreservesUnreadableBlocks() {
        val dump = MemoryDump("MIFARE Classic", 16, listOf("00112233445566778899AABBCCDDEEFF", null, "FF".repeat(16), "00".repeat(16)))
        val restored = MemoryDumpCodec.parse(MemoryDumpCodec.export(dump), "test.mct")
        assertEquals(dump.blocks, restored.blocks)
        assertEquals(dump.blockSize, restored.blockSize)
    }
    @Test fun binaryDumpSizesAreChecked() {
        assertEquals(64, MemoryDumpCodec.parse(ByteArray(1024), "test.bin").blocks.size)
        assertThrows(IllegalArgumentException::class.java) { MemoryDumpCodec.parse(ByteArray(111), "bad.bin") }
    }
    @Test fun comparisonFindsLengthAndContentDifferences() {
        assertEquals(listOf(1, 2), CardComparison.differentOffsets(byteArrayOf(1, 2), byteArrayOf(1, 3, 4)))
        val a = TagSnapshot("01", listOf("NfcA"), "Type 2", 144, true, true, false, emptyMap(), null)
        val b = a.copy(uid = "02")
        assertEquals(listOf("UID"), CardComparison.compare(a, b).filterNot { it.equal }.map { it.label })
    }
}
