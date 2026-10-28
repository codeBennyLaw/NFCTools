package com.benny.nfctools

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardSnapshotsTest {
    private fun tag(time: Long, uid: String = "04 A1 B2 C3", message: String? = null) =
        TagSnapshot(uid, listOf("Ndef"), "Type 2", 144, true, true, false, emptyMap(), message, timestamp = time)

    @Test fun latestUsesTimestampAndMatchesFormattedUid() {
        val saved = tag(10)
        val newest = tag(30, "04:a1:b2:c3")
        assertEquals(newest, CardSnapshots.latest(saved, listOf(tag(20), newest, tag(15))))
        assertTrue(CardSnapshots.sameCard(saved, tag(40, "04-A1-B2-C3")))
    }

    @Test fun anotherCardAndDemoNeverReplaceTheSavedCard() {
        val saved = tag(10)
        assertEquals(saved, CardSnapshots.latest(saved, listOf(tag(30, "04 FF FF FF"), tag(40).copy(sample = true))))
    }

    @Test fun aPartialReadDoesNotEraseTheLastKnownContent() {
        val saved = tag(10, message = "known")
        val unreadable = tag(20).copy(details = mapOf("NDEF 读取" to "无法读取内容"))
        assertEquals(saved, CardSnapshots.latest(saved, listOf(unreadable), unreadable))
    }

    @Test fun successfulEraseReplacesOldContentEvenWhenTheMessageIsEmpty() {
        val saved = tag(10, message = "old")
        val erased = tag(20)
        assertEquals(erased, CardSnapshots.latest(saved, listOf(erased)))
    }

    @Test fun currentResultWinsTimestampTiesAndAnOlderHistoryCannotRollbackIt() {
        val saved = tag(20, message = "old")
        val current = tag(20, message = "new")
        assertEquals(current, CardSnapshots.latest(saved, listOf(tag(10)), current))
    }

    @Test fun missingUidAndImportedFileNamesDoNotMatchAsPhysicalCards() {
        assertFalse(CardSnapshots.sameCard(tag(1, "无 UID"), tag(2, "无 UID")))
        assertFalse(CardSnapshots.sameCard(tag(1, "文件 abcd"), tag(2, "文件 abcd")))
        assertFalse(CardSnapshots.sameCard(tag(1, ""), tag(2, "")))
    }

    @Test fun multiRecordSummaryListsFeaturesWithoutExposingTheirValues() {
        val message = NdefMessage(listOf(
            DraftRecord(kind = RecordKind.APPLICATION, value = "com.android.settings"),
            DraftRecord(kind = RecordKind.WIFI, value = "Home", secondary = "secret123"),
            DraftRecord(kind = RecordKind.URI, value = "https://example.com")).map(NdefCodec::encodeRecord).toTypedArray())
        val summary = NdefCodec.summary(NdefCodec.base64(message.toByteArray()))
        assertTrue(summary.contains("打开应用"))
        assertTrue(summary.contains("Wi-Fi 配置"))
        assertTrue(summary.contains("3 条"))
        assertFalse(summary.contains("secret123"))
        assertFalse(summary.contains("com.android.settings"))
    }

    @Test fun summaryDistinguishesErasedMissingAndInvalidMessages() {
        val empty = NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, byteArrayOf(), byteArrayOf(), byteArrayOf())))
        assertEquals("内容已清空", NdefCodec.summary(NdefCodec.base64(empty.toByteArray())))
        assertEquals("暂无 NDEF 内容", NdefCodec.summary(null))
        assertEquals("NDEF 内容无法解析", NdefCodec.summary("bad-data"))
    }
}
