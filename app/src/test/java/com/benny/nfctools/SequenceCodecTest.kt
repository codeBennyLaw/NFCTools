package com.benny.nfctools

import android.nfc.NdefMessage
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SequenceCodecTest {
    private val steps = listOf(DraftRecord(kind = RecordKind.APPLICATION, value = "com.android.settings"),
        DraftRecord(kind = RecordKind.DELAY, value = "2"), DraftRecord(value = "Copy this"),
        DraftRecord(kind = RecordKind.URI, value = "https://example.com"))

    @Test fun sequenceRoundTripPreservesOrderAndHidesRoutingFromContent() {
        val message = NdefCodec.encode(steps, true)
        assertEquals(NdefCodec.SEQUENCE_TYPE, message.records[0].type.toString(Charsets.US_ASCII))
        assertEquals("com.benny.nfctools", message.records[1].payload.toString(Charsets.UTF_8))
        val restored = NdefCodec.parse(message.toByteArray())
        assertTrue(NdefCodec.isSequence(restored))
        assertEquals(steps.map { it.kind }, NdefCodec.editable(restored).map { it.kind })
        assertEquals(steps.map { it.value }, NdefCodec.editable(restored).map { it.value })
        assertEquals(4, NdefCodec.decode(restored).size)
        assertTrue(NdefCodec.summary(NdefCodec.base64(message.toByteArray())).endsWith("4 步"))
        assertArrayEquals(message.toByteArray(), NdefCodec.encode(NdefCodec.editable(restored), true).toByteArray())
    }
    @Test fun ordinaryRecordsKeepTheirStandardEncoding() {
        val ordinary = NdefCodec.encode(steps)
        assertFalse(NdefCodec.isSequence(ordinary))
        assertEquals(4, ordinary.records.size)
        assertEquals("android.com:pkg", ordinary.records[0].type.toString(Charsets.US_ASCII))
    }
    @Test fun anotherMetadataVersionIsNeverSilentlyRemoved() {
        val message = NdefCodec.encode(steps, true)
        val records = message.records.clone()
        records[0] = android.nfc.NdefRecord.createExternal("com.benny.nfctools", "sequence", byteArrayOf(2))
        val unsupported = NdefMessage(records)
        assertFalse(NdefCodec.isSequence(unsupported))
        assertEquals(6, NdefCodec.visibleRecords(unsupported).size)
        assertTrue(runCatching { NdefCodec.editable(unsupported) }.isFailure)
    }
    @Test fun delayRequiresAnIntegerBetweenOneAndSixHundredSeconds() {
        listOf("0", "-1", "601", "1.5", "not-a-time").forEach {
            assertTrue(runCatching { NdefCodec.encodeRecord(DraftRecord(kind = RecordKind.DELAY, value = it)) }.isFailure)
        }
        listOf("1", "600").forEach {
            val message = NdefCodec.encode(listOf(DraftRecord(kind = RecordKind.DELAY, value = it)), true)
            assertEquals(it, NdefCodec.editable(message).single().value)
        }
    }
    @Test fun stepLimitExcludesRoutingButStillLimitsTheUserPlan() {
        assertEquals(34, NdefCodec.encode(List(32) { DraftRecord(value = "Step $it") }, true).records.size)
        assertTrue(runCatching { NdefCodec.encode(List(33) { DraftRecord(value = "Too many") }, true) }.isFailure)
    }
    @Test fun routingOverheadCountsTowardsTheTotalCapacityLimit() {
        val large = listOf(DraftRecord(value = "a".repeat(NdefCodec.MAX_FILE_BYTES - 16)))
        assertTrue(NdefCodec.encode(large).toByteArray().size <= NdefCodec.MAX_FILE_BYTES)
        assertTrue(runCatching { NdefCodec.encode(large, true) }.isFailure)
    }
}
