package com.benny.nfctools

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SequenceDraftTest {
    private lateinit var application: Application
    private lateinit var vm: NfcViewModel
    private val models = ViewModelStore()
    @Before fun reset() {
        application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit()
        vm = NfcViewModel(application); models.put("vm", vm)
    }
    @After fun clear() { models.clear(); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun reorderingAndSequenceModePersistAndMatchTheWrittenOrder() {
        val a = DraftRecord(value = "First")
        val b = DraftRecord(kind = RecordKind.DELAY, value = "1")
        vm.add(a); vm.add(b); vm.moveRecord(b.id, -1)
        assertEquals(listOf(b, a), LocalStore(application).draft())
        assertTrue(LocalStore(application).sequenceMode())
        val restored = NfcViewModel(application); models.put("restored", restored)
        assertTrue(restored.state.value.sequenceMode)
        assertEquals(listOf("1", "First"), NdefCodec.editable(NdefCodec.parse(requireNotNull(restored.messageBytes()))).map { it.value })
    }
    @Test fun armedPayloadCannotBeReorderedUntilCancelled() {
        val a = DraftRecord(value = "First"); val b = DraftRecord(value = "Second")
        vm.add(a); vm.add(b); vm.sequence(true); vm.setDevice(DeviceStatus.READY); vm.arm(OperationKind.WRITE)
        val frozen = requireNotNull(vm.state.value.pending?.bytes)
        vm.moveRecord(b.id, -1); vm.sequence(false)
        assertEquals(listOf(a, b), vm.state.value.draft)
        assertArrayEquals(frozen, vm.messageBytes())
        vm.cancel(); vm.moveRecord(b.id, -1)
        assertEquals(listOf(b, a), vm.state.value.draft)
    }
    @Test fun backupEditingKeepsTheTaskHeaderAndDelayAndClearingRemovesBoth() {
        val message = NdefCodec.encode(listOf(DraftRecord(kind = RecordKind.DELAY, value = "2"), DraftRecord(value = "After")), true)
        val tag = TagSnapshot("04 AA", listOf("Ndef"), "Type 2", 492, true, true, false, emptyMap(), NdefCodec.base64(message.toByteArray()))
        vm.reuse(tag); vm.editImported()
        assertTrue(vm.state.value.sequenceMode)
        assertEquals(2, vm.state.value.draft.size)
        assertArrayEquals(message.toByteArray(), vm.messageBytes())
        vm.clearDraft()
        assertFalse(LocalStore(application).sequenceMode())
        assertNull(vm.messageBytes())
    }
}
