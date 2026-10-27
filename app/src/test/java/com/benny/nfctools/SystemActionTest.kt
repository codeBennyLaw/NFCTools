package com.benny.nfctools

import android.app.Application
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.os.Looper
import android.os.Bundle
import androidx.lifecycle.ViewModelStore
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import com.benny.nfctools.nfc.NfcOperations
import com.benny.nfctools.system.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
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
import org.robolectric.util.ReflectionHelpers.ClassParameter
import android.app.AppOpsManager
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemActionTest {
    private val application: Application get() = RuntimeEnvironment.getApplication()
    private val models = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val received = mutableListOf<SystemAction>()
    private var message = NdefMessage(arrayOf(SystemAction(SystemFunction.MEDIA_VOLUME, "25").record()))
    private var fail = false
    private lateinit var vm: NfcViewModel

    @Before fun setup() {
        application.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit()
        vm = NfcViewModel(application, object : NfcOperations {
            override fun read(tag: Tag): TagSnapshot {
                if (fail) error("Read failed")
                return snapshot()
            }
            override fun execute(tag: Tag, op: PendingOperation) = snapshot()
        }).also { models.put("system", it) }
        scope.launch { vm.systemRequests.collect { received += it } }
        vm.setDevice(DeviceStatus.READY)
        waitFor { vm.state.value.historyLoaded }
    }
    @After fun close() { scope.cancel(); models.clear(); shadowOf(Looper.getMainLooper()).idle() }
    private fun snapshot() = TagSnapshot("04 AA", listOf("Ndef"), "Type 2", 492, true, true, false, emptyMap(), NdefCodec.base64(message.toByteArray()))
    private fun scan() {
        val tag: Tag = ReflectionHelpers.callStaticMethod(Tag::class.java, "createMockTag",
            ClassParameter.from(ByteArray::class.java, byteArrayOf(1)), ClassParameter.from(IntArray::class.java, intArrayOf()),
            ClassParameter.from(Array<Bundle>::class.java, emptyArray<Bundle>()), ClassParameter.from(Long::class.javaPrimitiveType, 0L))
        vm.onTag(tag)
        waitFor { vm.state.value.current != null && !vm.state.value.busy }
    }
    private fun waitFor(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(condition())
    }

    @Test fun everyFunctionRoundTripsAsOneEditableRecord() {
        SystemFunction.entries.forEach { function ->
            val action = SystemAction(function)
            val encoded = NdefCodec.encode(listOf(action.draft()))
            assertEquals(1, encoded.records.size)
            assertEquals(action, SystemAction.fromMessage(NdefCodec.parse(encoded.toByteArray())))
            assertEquals(action.description, NdefCodec.decode(encoded).single().content)
            assertEquals(action, SystemAction.fromDraft(NdefCodec.editable(encoded).single()))
        }
    }
    @Test fun arbitraryActionsInvalidParametersAndVersionsAreRejected() {
        listOf("{\"version\":2,\"function\":\"MEDIA_VOLUME\",\"argument\":\"10\"}",
            "{\"version\":1,\"function\":\"SHELL\",\"argument\":\"reboot\"}",
            "{\"version\":1,\"function\":\"MEDIA_VOLUME\",\"argument\":\"101\"}",
            "{\"version\":1,\"function\":\"WIFI_SETTINGS\",\"argument\":\"on\"}",
            "{\"version\":1,\"function\":\"TORCH\",\"argument\":\"toggle\"}",
            "{\"version\":1,\"function\":\"BRIGHTNESS\",\"argument\":\"0\"}", "{}").forEach { json ->
            assertTrue(json, runCatching { SystemAction.parse(json) }.isFailure)
        }
    }
    @Test fun legacyMultiRecordCardsNeverAutoExecute() {
        message = NdefMessage(arrayOf(message.records.single(), NdefRecord.createTextRecord("en", "legacy")))
        assertNull(SystemAction.fromMessage(message))
        scan()
        assertTrue(received.isEmpty())
    }
    @Test fun actualReadEmitsExactlyOneRequest() {
        scan()
        assertEquals(listOf(SystemAction(SystemFunction.MEDIA_VOLUME, "25")), received)
    }
    @Test fun writingDoesNotExecuteTheWrittenAction() {
        vm.add(SystemAction(SystemFunction.MEDIA_VOLUME, "25").draft())
        vm.arm(OperationKind.WRITE)
        scan()
        assertTrue(received.isEmpty())
        assertNull(vm.messageBytes())
    }
    @Test fun viewingHistoryCardOrBackupDoesNotExecute() {
        vm.openHistory(snapshot())
        vm.reuse(snapshot())
        vm.editImported()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(received.isEmpty())
    }
    @Test fun backgroundReadDoesNotExecute() {
        vm.pause()
        scan()
        assertTrue(received.isEmpty())
    }
    @Test fun failedReadDoesNotExecute() {
        fail = true
        vm.onTag(ReflectionHelpers.newInstance(Tag::class.java))
        waitFor { vm.state.value.error != null && !vm.state.value.busy }
        assertTrue(received.isEmpty())
    }
    @Test fun brightnessCannotBeChangedWithoutSpecialAccess() {
        shadowOf(application.getSystemService(AppOpsManager::class.java)).setMode(AppOpsManager.OPSTR_WRITE_SETTINGS, android.os.Process.myUid(), application.packageName, AppOpsManager.MODE_ERRORED)
        val result = SystemActionRunner(application).execute(SystemAction(SystemFunction.BRIGHTNESS, "25"))
        assertEquals(ActionResult.Permission(SystemPermission.WRITE_SETTINGS), result)
    }
}
