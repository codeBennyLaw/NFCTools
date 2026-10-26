package com.benny.nfctools

import android.app.Application
import android.content.Context
import android.nfc.Tag
import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import com.benny.nfctools.nfc.NfcOperations
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class WriteCompletionTest {
    private lateinit var application: Application
    private lateinit var vm: NfcViewModel
    private lateinit var store: LocalStore
    private lateinit var engine: FakeNfc
    private val viewModels = ViewModelStore()
    private val releaseWrite = CountDownLatch(1)

    @Before fun setUp() {
        application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit()
        File(application.filesDir, "history.json").delete()
        File(application.filesDir, "cards.json").delete()
        store = LocalStore(application)
        engine = FakeNfc()
        vm = NfcViewModel(application, engine)
        viewModels.put("test", vm)
        vm.setDevice(DeviceStatus.READY)
        waitFor { vm.state.value.historyLoaded }
    }

    @After fun tearDown() {
        releaseWrite.countDown()
        viewModels.clear()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun successfulSingleWriteClearsListAndFieldsFromPersistentStorage() {
        val records = listOf(
            DraftRecord(value = "A note"),
            DraftRecord(kind = RecordKind.URI, value = "https://example.com"),
            DraftRecord(kind = RecordKind.WIFI, value = "Network", secondary = "password123"),
            DraftRecord(kind = RecordKind.APPLICATION, value = "com.android.settings"),
            DraftRecord(kind = RecordKind.FILE, value = "small.bin", base64 = NdefCodec.base64(byteArrayOf(1, 2, 3))),
        )
        records.forEach { assertTrue(vm.add(it)) }
        vm.quickEntry(DraftRecord(kind = RecordKind.WIFI, value = "Network", secondary = "password123"))
        val written = requireNotNull(vm.messageBytes())
        write()

        assertTrue(vm.state.value.draft.isEmpty())
        assertNull(vm.state.value.importedMessage)
        assertNull(vm.messageBytes())
        assertTrue(store.draft().isEmpty())
        assertNull(store.imported())
        assertEquals("", store.quickEntry().value)
        assertEquals("", store.quickEntry().secondary)
        assertEquals(RecordKind.WIFI, store.quickEntry().kind)
        assertArrayEquals(written, NdefCodec.fromBase64(requireNotNull(vm.state.value.current?.messageBase64)))
        assertEquals(1, vm.state.value.history.size)
        assertEquals(Destination.READ, vm.state.value.destination)
    }

    @Test fun successfulBackupWriteClearsImportedMessageToo() {
        val message = NdefCodec.base64(NdefCodec.encode(listOf(DraftRecord(value = "From backup"))).toByteArray())
        vm.reuse(snapshot(message))
        assertEquals(message, store.imported())
        write()
        assertNull(vm.state.value.importedMessage)
        assertNull(store.imported())
        assertNull(vm.messageBytes())
    }

    @Test fun failedWritePreservesContentForRetryAndOnlySuccessClearsIt() {
        val record = DraftRecord(value = "Retry me")
        vm.add(record)
        vm.quickEntry(record)
        engine.failure = IOException("Read-back verification failed")
        vm.arm(OperationKind.WRITE)
        vm.onTag(tag(1))
        waitFor { vm.state.value.error != null && !vm.state.value.busy }
        assertEquals(listOf(record), vm.state.value.draft)
        assertEquals(listOf(record), store.draft())
        assertEquals(record, store.quickEntry())
        engine.failure = null
        write()
        assertTrue(store.draft().isEmpty())
        assertEquals("", store.quickEntry().value)
    }

    @Test fun cancellingOrBackgroundingBeforeWriteKeepsTheDraft() {
        val record = DraftRecord(value = "Not written yet")
        vm.add(record)
        vm.arm(OperationKind.WRITE)
        vm.cancel()
        assertNull(vm.state.value.pending)
        assertEquals(listOf(record), store.draft())
        vm.arm(OperationKind.WRITE)
        vm.pause()
        vm.resume()
        assertNull(vm.state.value.pending)
        assertEquals(listOf(record), store.draft())
    }

    @Test fun batchWriteKeepsContentAndContinuesWithTheNextCard() {
        val record = DraftRecord(value = "Same for each card")
        vm.add(record)
        vm.batch(true)
        vm.arm(OperationKind.WRITE)
        val bytes = requireNotNull(vm.state.value.pending?.bytes)
        vm.onTag(tag(1))
        waitFor { vm.state.value.writtenCount == 1 && !vm.state.value.busy }
        assertEquals(listOf(record), store.draft())
        assertArrayEquals(bytes, vm.state.value.pending?.bytes)
        vm.onTag(tag(2))
        waitFor { vm.state.value.writtenCount == 2 && !vm.state.value.busy }
        assertEquals(listOf(record), vm.state.value.draft)
        assertEquals(Destination.WRITE, vm.state.value.destination)
        vm.cancel()
        assertEquals(listOf(record), store.draft())
    }

    @Test fun readingAndErasingDoNotClearUnrelatedWriteDrafts() {
        val record = DraftRecord(value = "Keep this for later")
        vm.add(record)
        vm.onTag(tag(1))
        waitFor { vm.state.value.current != null && !vm.state.value.busy }
        assertEquals(listOf(record), store.draft())
        vm.arm(OperationKind.ERASE)
        vm.onTag(tag(2))
        waitFor { vm.state.value.current?.action == OperationKind.ERASE.label && !vm.state.value.busy }
        assertEquals(listOf(record), store.draft())
    }

    @Test fun newlySharedContentDuringWriteIsKeptForTheNextWrite() {
        vm.add(DraftRecord(value = "Old content"))
        vm.quickEntry(DraftRecord(value = "Old entry"))
        val writing = CountDownLatch(1)
        engine.beforeReturn = {
            writing.countDown()
            check(releaseWrite.await(10, TimeUnit.SECONDS))
        }
        vm.arm(OperationKind.WRITE)
        vm.onTag(tag(1))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(writing.await(5, TimeUnit.SECONDS))
        val next = DraftRecord(value = "New shared content")
        vm.clearDraft()
        vm.add(next)
        vm.quickEntry(next)
        releaseWrite.countDown()
        waitFor { vm.state.value.writtenCount == 1 && !vm.state.value.busy }
        assertEquals(listOf(next), store.draft())
        assertEquals(next, store.quickEntry())
    }

    private fun write() {
        vm.arm(OperationKind.WRITE)
        vm.onTag(tag(1))
        waitFor { vm.state.value.writtenCount == 1 && !vm.state.value.busy }
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("NFC operation did not complete", condition())
    }

    private fun tag(uid: Byte): Tag = ReflectionHelpers.callStaticMethod(Tag::class.java, "createMockTag",
        ClassParameter.from(ByteArray::class.java, byteArrayOf(uid)),
        ClassParameter.from(IntArray::class.java, intArrayOf()),
        ClassParameter.from(Array<Bundle>::class.java, emptyArray<Bundle>()),
        ClassParameter.from(Long::class.javaPrimitiveType, 0L))

    private fun snapshot(message: String? = null, action: String = "读取") = TagSnapshot(
        "TEST", listOf("Ndef"), "Type 2", 1024, true, true, false, emptyMap(), message, action = action)

    private inner class FakeNfc : NfcOperations {
        var failure: Exception? = null
        var beforeReturn: (() -> Unit)? = null
        override fun read(tag: Tag) = snapshot()
        override fun execute(tag: Tag, op: PendingOperation): TagSnapshot {
            beforeReturn?.invoke()
            failure?.let { throw it }
            return snapshot(op.bytes?.let(NdefCodec::base64), op.kind.label)
        }
    }
}
