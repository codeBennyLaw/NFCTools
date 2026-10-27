package com.benny.nfctools

import android.app.Application
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import com.benny.nfctools.nfc.NfcEngine
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SingleOperationTest {
    private lateinit var application: Application
    private val models = ViewModelStore()
    private lateinit var vm: NfcViewModel
    @Before fun reset() {
        application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit()
        vm = model("original")
    }
    @After fun close() { models.clear(); shadowOf(Looper.getMainLooper()).idle() }
    private fun model(key: String) = NfcViewModel(application).also { models.put(key, it) }
    private fun legacy() = NdefMessage(arrayOf(NdefRecord.createTextRecord("zh", "Old text"), NdefRecord.createUri("https://example.com/latest")))
    private fun snapshot(message: NdefMessage) = TagSnapshot("04 AA", listOf("Ndef"), "Type 2", 492, true, true, false, emptyMap(), NdefCodec.base64(message.toByteArray()))

    @Test fun newOperationReplacesThePreviousOneInStateAndStorage() {
        vm.add(DraftRecord(value = "Previous"))
        val latest = DraftRecord(kind = RecordKind.APPLICATION, value = "com.android.settings")
        vm.add(latest)
        assertEquals(listOf(latest), vm.state.value.draft)
        assertEquals(listOf(latest), LocalStore(application).draft())
        assertEquals("com.android.settings", NdefCodec.decode(NdefCodec.parse(requireNotNull(vm.messageBytes()))).single().content)
    }
    @Test fun changingOrClearingTheFormCannotReviveAnOldFile() {
        vm.add(DraftRecord(kind = RecordKind.FILE, value = "old.bin", base64 = NdefCodec.base64(byteArrayOf(1, 2, 3))))
        vm.quickEntry(DraftRecord(value = "New note"))
        assertEquals("New note", NdefCodec.decode(NdefCodec.parse(requireNotNull(vm.messageBytes()))).single().content)
        vm.updateEntry(value = "")
        assertNull(vm.messageBytes())
        assertTrue(LocalStore(application).draft().isEmpty())
    }
    @Test fun selectingAnotherTypeStartsAnEmptyOperation() {
        vm.add(DraftRecord(value = "Not an app package"))
        vm.selectKind(RecordKind.APPLICATION)
        assertEquals("", vm.state.value.activeEntry.value)
        assertNull(vm.messageBytes())
        assertTrue(vm.state.value.draft.isEmpty())
    }
    @Test fun codecStoreAndEngineRejectMultipleOperations() {
        val drafts = listOf(DraftRecord(value = "A"), DraftRecord(value = "B"))
        assertThrows(IllegalArgumentException::class.java) { NdefCodec.encode(drafts) }
        assertThrows(IllegalArgumentException::class.java) { LocalStore(application).saveDraft(drafts, null) }
        val tag = ReflectionHelpers.newInstance(Tag::class.java)
        assertThrows(IllegalArgumentException::class.java) { NfcEngine().execute(tag, PendingOperation(OperationKind.WRITE, legacy().toByteArray())) }
    }
    @Test fun copyingAMultiOperationCardDoesNotReplaceTheCurrentOperationOrArmAWrite() {
        vm.add(DraftRecord(value = "Keep this"))
        vm.setDevice(DeviceStatus.READY)
        vm.copyToNext(snapshot(legacy()))
        assertNull(vm.state.value.pending)
        assertEquals("Keep this", vm.state.value.activeEntry.value)
        assertEquals("Keep this", NdefCodec.decode(NdefCodec.parse(requireNotNull(vm.messageBytes()))).single().content)
    }
    @Test fun oldMultiOperationBackupIsReadOnlyUntilAnItemIsSelected() {
        LocalStore(application).saveDraft(emptyList(), NdefCodec.base64(legacy().toByteArray()))
        val restored = model("backup")
        restored.setDevice(DeviceStatus.READY)
        restored.arm(OperationKind.WRITE)
        assertNull(restored.messageBytes())
        assertNull(restored.state.value.pending)
        restored.editImported()
        assertEquals(ReadSource.FILE, restored.state.value.currentSource)
        restored.selectRecord(requireNotNull(restored.state.value.current), 1)
        assertEquals(Destination.WRITE, restored.state.value.destination)
        assertEquals(1, NdefCodec.parse(requireNotNull(restored.messageBytes())).records.size)
        assertEquals("https://example.com/latest", restored.state.value.activeEntry.value)
    }
    @Test fun oldDraftMigratesToTheLatestSupportedItemAndIgnoresRemovedDelaySteps() {
        application.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().putString("draft",
            """[{"id":"a","kind":"TEXT","value":"Old"},{"id":"b","kind":"URI","value":"https://example.com/latest"},{"id":"c","kind":"DELAY","value":"2"}]""").commit()
        val restored = model("migrated")
        assertEquals(1, restored.state.value.draft.size)
        assertEquals("https://example.com/latest", restored.state.value.activeEntry.value)
        assertEquals(1, NdefCodec.parse(requireNotNull(restored.messageBytes())).records.size)
    }
    @Test fun legacyTaskMetadataIsReadOnlyAndSelectingAnActionDropsTheRouting() {
        val marker = NdefRecord.createExternal("com.benny.nfctools", "sequence", byteArrayOf(1))
        val route = NdefRecord.createApplicationRecord("com.benny.nfctools")
        val action = NdefRecord.createApplicationRecord("com.android.settings")
        val delay = NdefRecord.createExternal("com.benny.nfctools", "delay", "2".toByteArray())
        val old = snapshot(NdefMessage(arrayOf(marker, route, action, delay)))
        assertEquals(2, NdefCodec.decode(NdefCodec.message(old.messageBase64)).size)
        vm.selectRecord(old, 1)
        assertNull(vm.messageBytes())
        vm.selectRecord(old, 0)
        val written = NdefCodec.parse(requireNotNull(vm.messageBytes()))
        assertEquals(1, written.records.size)
        assertEquals("com.android.settings", written.records.single().payload.toString(Charsets.UTF_8))
    }
}
