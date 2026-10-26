package com.benny.nfctools

import android.app.Application
import android.graphics.Bitmap
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.os.Bundle
import android.os.Parcel
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import com.benny.nfctools.nfc.NfcOperations
import com.benny.nfctools.ui.NfcApp
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

class CardFlowTest {
    @get:Rule val app = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.applicationContext as Application
    private val store get() = LocalStore(context)
    private val viewModels = ViewModelStore()
    private lateinit var vm: NfcViewModel
    private lateinit var originalCards: List<SavedCard>
    private lateinit var originalHistory: List<TagSnapshot>
    private lateinit var originalDraft: List<DraftRecord>
    private lateinit var originalEntry: DraftRecord
    private var originalImported: String? = null
    private var originalMode = false
    private val old = snapshot("Old note", System.currentTimeMillis() - 60_000)
    private val card = SavedCard("card-flow-test", "床头卡", CardCategory.OTHER, old)
    private val engine = FakeNfc()

    @Before fun preserveAndSeed() {
        originalCards = store.loadCards()
        originalHistory = store.loadHistory()
        originalDraft = store.draft()
        originalImported = store.imported()
        originalMode = store.sequenceMode()
        originalEntry = store.quickEntry()
        store.saveCards(listOf(card))
        store.saveHistory(emptyList())
        store.saveDraft(emptyList(), null)
        store.saveQuickEntry(DraftRecord())
    }

    @After fun restore() {
        instrumentation.runOnMainSync { viewModels.clear() }
        store.saveCards(originalCards)
        store.saveHistory(originalHistory)
        store.saveDraft(originalDraft, originalImported, originalMode)
        store.saveQuickEntry(originalEntry)
    }

    private fun launch(history: List<TagSnapshot> = emptyList()) {
        store.saveHistory(history)
        instrumentation.runOnMainSync {
            vm = NfcViewModel(context, engine)
            viewModels.put("card-flow", vm)
            vm.setDevice(DeviceStatus.READY)
        }
        app.setContent { NfcApp(vm, {}) }
        app.waitUntil(5000) { vm.state.value.historyLoaded && vm.state.value.cards.isNotEmpty() }
    }

    @Test fun successfulWriteUpdatesCardAndClickShowsAllLatestRecords() {
        launch()
        app.runOnIdle {
            vm.add(DraftRecord(kind = RecordKind.APPLICATION, value = "com.android.settings"))
            vm.add(DraftRecord(kind = RecordKind.URI, value = "https://example.com/latest"))
            vm.arm(OperationKind.WRITE)
            vm.onTag(mockTag())
        }
        app.waitUntil(5000) { vm.state.value.writtenCount == 1 && !vm.state.value.busy }
        val latest = vm.state.value.current!!
        assertEquals(latest, store.loadCards().single().tag)
        assertEquals(card.name, vm.state.value.cards.single().name)
        assertEquals(card.category, vm.state.value.cards.single().category)
        app.runOnIdle { vm.navigate(Destination.CARDS) }
        app.onNodeWithText("打开应用 · 链接 / URI · 2 条").assertExists()
        capture("cards")
        app.onNodeWithText(card.name).performClick()
        app.onNodeWithText("卡包 · ${card.name}").assertExists()
        app.onNodeWithText("本机最新记录", substring = true).assertExists()
        capture("card-detail")
        app.onNodeWithText("标签内容 · 2 条").assertExists()
        app.onNodeWithText("com.android.settings").assertExists()
        app.onNodeWithText("https://example.com/latest").performScrollTo().assertIsDisplayed()
        app.onNodeWithText("Old note").assertDoesNotExist()
        app.runOnIdle { assertEquals(ReadSource.CARD, vm.state.value.currentSource); vm.clearHistory() }
        app.waitUntil(5000) { vm.state.value.history.isEmpty() }
        assertEquals(latest, LocalStore(context).loadCards().single().tag)
    }

    @Test fun oldCardPicksUpNewerHistoryAndRenameCannotRollbackTheContent() {
        val latest = snapshot("Latest from 0.1.1", old.timestamp + 10_000)
        launch(listOf(latest))
        app.waitUntil(5000) { vm.state.value.cards.single().tag == latest }
        app.runOnIdle { vm.updateCard(card.copy(name = "新卡名", category = CardCategory.TAG)) }
        app.waitUntil(5000) { vm.state.value.cards.single().name == "新卡名" }
        assertEquals(latest, store.loadCards().single().tag)
        app.runOnIdle { vm.navigate(Destination.CARDS) }
        app.onNodeWithText("新卡名").performClick()
        app.onNodeWithText("卡包 · 新卡名").assertExists()
        app.onNodeWithText("Latest from 0.1.1").assertExists()
        app.onNodeWithText("Old note").assertDoesNotExist()
    }

    @Test fun failedWriteNeverUpdatesTheCardOrDropsTheDraft() {
        launch()
        engine.failure = IOException("Verification failed")
        app.runOnIdle {
            vm.add(DraftRecord(value = "Failed write"))
            vm.arm(OperationKind.WRITE)
            vm.onTag(mockTag())
        }
        app.waitUntil(5000) { vm.state.value.error != null && !vm.state.value.busy }
        assertEquals(card, store.loadCards().single())
        assertEquals("Failed write", store.draft().single().value)
        app.runOnIdle { vm.openCard(card.id) }
        app.onNodeWithText("Old note").assertExists()
    }

    @Test fun laterReadAndEraseRefreshTheSavedCardWithoutChangingItsIdentity() {
        launch()
        val latest = snapshot("Read another time", old.timestamp + 10_000)
        engine.next = latest
        app.runOnIdle { vm.onTag(mockTag()) }
        app.waitUntil(5000) { !vm.state.value.busy && vm.state.value.cards.single().tag == latest }
        assertEquals(latest, store.loadCards().single().tag)
        val empty = NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, byteArrayOf(), byteArrayOf(), byteArrayOf())))
        engine.next = latest.copy(timestamp = latest.timestamp + 10_000, messageBase64 = NdefCodec.base64(empty.toByteArray()))
        app.runOnIdle { vm.arm(OperationKind.ERASE); vm.onTag(mockTag()) }
        app.waitUntil(5000) { !vm.state.value.busy && vm.state.value.cards.single().tag.action == OperationKind.ERASE.label }
        val erased = store.loadCards().single()
        assertEquals(card.id, erased.id)
        assertEquals(card.name, erased.name)
        assertEquals(card.category, erased.category)
        assertEquals("内容已清空", NdefCodec.summary(erased.tag.messageBase64))
    }

    // A service-free Tag parcel exercises ViewModel handling without NFC RF I/O.
    private fun mockTag(): Tag = Parcel.obtain().let { parcel ->
        try {
            val uid = byteArrayOf(4, 0xa1.toByte(), 0xb2.toByte(), 0xc3.toByte())
            parcel.writeInt(uid.size)
            parcel.writeByteArray(uid)
            parcel.writeInt(0)
            parcel.writeIntArray(intArrayOf())
            parcel.writeTypedArray(emptyArray<Bundle>(), 0)
            parcel.writeInt(0)
            parcel.writeLong(0)
            parcel.writeInt(1)
            parcel.setDataPosition(0)
            Tag.CREATOR.createFromParcel(parcel)
        } finally { parcel.recycle() }
    }

    private fun snapshot(value: String, timestamp: Long) = TagSnapshot("04 A1 B2 C3", listOf("Ndef"), "Type 2", 492,
        true, true, false, emptyMap(), NdefCodec.base64(NdefCodec.encode(listOf(DraftRecord(value = value))).toByteArray()), timestamp = timestamp)

    private fun capture(name: String) {
        app.waitForIdle()
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return
        try { File(context.getExternalFilesDir(null), "$name-0.2.0.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { screenshot.recycle() }
    }

    private inner class FakeNfc : NfcOperations {
        var next = old.copy(timestamp = old.timestamp + 10_000)
        var failure: Exception? = null
        override fun read(tag: Tag) = next
        override fun execute(tag: Tag, op: PendingOperation): TagSnapshot {
            failure?.let { throw it }
            return next.copy(action = op.kind.label, messageBase64 = if (op.kind == OperationKind.WRITE) NdefCodec.base64(requireNotNull(op.bytes)) else next.messageBase64)
        }
    }
}
