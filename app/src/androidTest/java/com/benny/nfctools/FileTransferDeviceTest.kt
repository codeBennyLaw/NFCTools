package com.benny.nfctools

import android.app.Application
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.benny.nfctools.data.RecordKind
import com.benny.nfctools.data.DraftRecord
import com.benny.nfctools.data.DeviceStatus
import com.benny.nfctools.data.OperationKind
import com.benny.nfctools.nfc.NdefCodec
import com.benny.nfctools.tasks.TaskDispatcher
import com.benny.nfctools.tasks.TaskResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FileTransferDeviceTest {
    @Test fun sequenceFileUsesAContentUriAndTemporaryReadPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var captured: Intent? = null
        val target = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { captured = intent }
        }
        val payload = byteArrayOf(0, 1, 2, 3, 0xff.toByte())
        val result = runBlocking { TaskDispatcher.dispatch(target, DraftRecord(kind = RecordKind.FILE, value = "test.bin", base64 = NdefCodec.base64(payload))) }
        assertTrue(result.toString(), result is TaskResult.External)
        val intent = requireNotNull(captured)
        assertEquals("content", intent.data?.scheme)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertArrayEquals(payload, context.contentResolver.openInputStream(requireNotNull(intent.data))!!.use { it.readBytes() })
    }
    @Test fun mimeFileAndNdefBackupUseRealContentResolver() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext as Application
        lateinit var vm: NfcViewModel
        instrumentation.runOnMainSync { vm = NfcViewModel(context); vm.clearDraft() }
        val input = File(context.cacheDir, "test-payload.bin")
        val output = File(context.cacheDir, "test-backup.ndef")
        output.delete()
        val payload = byteArrayOf(0, 1, 2, 3, 0xff.toByte())
        input.writeBytes(payload)
        try {
            instrumentation.runOnMainSync { vm.importFile(Uri.fromFile(input), input.name, "application/octet-stream", false) }
            waitFor { vm.state.value.draft.size == 1 }
            assertEquals(RecordKind.FILE, vm.state.value.draft.single().kind)
            val bytes = requireNotNull(vm.messageBytes())
            assertArrayEquals(payload, NdefCodec.parse(bytes).records.single().payload)
            instrumentation.runOnMainSync { vm.exportFile(Uri.fromFile(output), bytes) }
            waitFor { output.exists() && output.readBytes().contentEquals(bytes) }
            instrumentation.runOnMainSync { vm.clearDraft(); vm.importFile(Uri.fromFile(output), output.name, "application/octet-stream", true) }
            waitFor { vm.state.value.importedMessage != null }
            assertArrayEquals(bytes, vm.messageBytes())
        } finally {
            instrumentation.runOnMainSync { vm.clearDraft() }
            input.delete(); output.delete()
        }
    }
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(30)
        assertTrue("File operation did not complete within 5 seconds", condition())
    }
    @Test fun batchUsesAFrozenMessageAndBackgroundCancelsIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext as Application
        lateinit var vm: NfcViewModel
        instrumentation.runOnMainSync {
            vm = NfcViewModel(context)
            vm.clearDraft()
            vm.setDevice(DeviceStatus.READY)
            vm.add(DraftRecord(value = "Batch message"))
            vm.batch(true)
            vm.arm(OperationKind.WRITE)
            val pending = requireNotNull(vm.state.value.pending)
            vm.add(DraftRecord(value = "Changed after arming"))
            assertEquals(1, NdefCodec.parse(requireNotNull(pending.bytes)).records.size)
            assertEquals(2, NdefCodec.parse(requireNotNull(vm.messageBytes())).records.size)
            vm.pause()
            assertNull(vm.state.value.pending)
            vm.resume()
            assertNull(vm.state.value.pending)
            vm.clearDraft()
        }
    }
}
