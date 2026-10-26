package com.benny.nfctools

import android.content.Context
import com.benny.nfctools.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreTest {
    private lateinit var store: LocalStore
    @Before fun reset() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "history.json").delete()
        File(context.filesDir, "cards.json").delete()
        store = LocalStore(context)
    }
    @Test fun historyRoundTripPreservesNullCapabilities() {
        val s = TagSnapshot("04 AA", listOf("IsoDep", "NfcA"), "ISO-DEP", null, null, false, false, mapOf("SAK" to "20"), null, timestamp = 42)
        store.saveHistory(listOf(s))
        assertEquals(s, store.loadHistory().single())
    }
    // Replacing an existing AtomicFile is exercised on Android in LocalStoreDeviceTest.
    // Android's rename semantics are different from the Windows JVM filesystem.
    @Test fun draftAndImportedBackupPersistAcrossStoreInstances() {
        val d = DraftRecord(kind = RecordKind.WIFI, value = "Home", secondary = "password123")
        store.saveDraft(listOf(d), null)
        assertEquals(listOf(d), LocalStore(RuntimeEnvironment.getApplication()).draft())
        store.saveDraft(emptyList(), "0AA=")
        assertEquals("0AA=", store.imported())
        assertTrue(store.draft().isEmpty())
    }
    @Test fun corruptedDraftDoesNotCrashStartup() {
        RuntimeEnvironment.getApplication().getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().putString("draft", "not-json").commit()
        assertTrue(store.draft().isEmpty())
    }
    @Test fun unfinishedQuickEntrySurvivesAStoreRestart() {
        val entry = DraftRecord(kind = RecordKind.APPLICATION, value = "com.android.settings", secondary = "设置")
        store.saveQuickEntry(entry)
        assertEquals(entry, LocalStore(RuntimeEnvironment.getApplication()).quickEntry())
    }
    @Test fun savedCardPreservesCategoryAndMemoryDump() {
        val tag = TagSnapshot("01 02", listOf("MifareClassic"), "MIFARE Classic", null, null, false, false, emptyMap(), null,
            memory = MemoryDump("MIFARE Classic", 16, listOf("00".repeat(16), null)))
        val card = SavedCard(name = "家门", category = CardCategory.ACCESS, tag = tag)
        store.saveCards(listOf(card))
        assertEquals(card, store.loadCards().single())
    }
}
