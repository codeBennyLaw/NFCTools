package com.benny.nfctools

import android.os.Looper
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import com.benny.nfctools.tasks.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class TaskRunnerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requests = mutableListOf<TaskRequest>()
    private val runner = TaskRunner(scope)
    @Before fun collectRequests() { scope.launch { runner.requests.collect { requests += it } }; idle() }
    @After fun clear() { runner.stop(); scope.cancel(); idle() }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun plan(vararg records: DraftRecord) { assertTrue(runner.prepare(NdefCodec.encode(records.toList(), true))); runner.start(); idle() }
    private fun answer(result: TaskResult) { runner.dispatched(requests.last(), result); idle() }

    @Test fun externalActivityBlocksTheNextStepUntilTheUserContinues() {
        plan(DraftRecord(value = "first"), DraftRecord(kind = RecordKind.APPLICATION, value = "com.android.settings"), DraftRecord(value = "last"))
        assertEquals("first", requests.single().record.value)
        answer(TaskResult.Done)
        assertEquals("com.android.settings", requests.last().record.value)
        answer(TaskResult.External("Return and continue"))
        runner.pause(); runner.resume(); idle()
        assertEquals(2, requests.size)
        assertEquals(TaskStatus.WAITING_RETURN, runner.state.value?.status)
        assertEquals(1, runner.state.value?.index)
        runner.continueAfterExternal(); idle()
        assertEquals("last", requests.last().record.value)
        answer(TaskResult.Done)
        assertEquals(TaskStatus.COMPLETED, runner.state.value?.status)
    }
    @Test fun failedStepsCanRetryAndOldRepliesCannotCompleteTheRetry() {
        plan(DraftRecord(value = "Retry"))
        val old = requests.single()
        answer(TaskResult.Failure("Try again"))
        assertEquals(0, runner.state.value?.index)
        assertEquals(TaskStatus.FAILED, runner.state.value?.status)
        runner.start(); idle()
        assertNotEquals(old.id, requests.last().id)
        runner.dispatched(old, TaskResult.Done); idle()
        assertEquals(TaskStatus.RUNNING, runner.state.value?.status)
        answer(TaskResult.Done)
        assertEquals(TaskStatus.COMPLETED, runner.state.value?.status)
    }
    @Test fun delayPausesWithItsRemainingTimeAndResumeDoesNotRunByItself() {
        plan(DraftRecord(kind = RecordKind.DELAY, value = "2"), DraftRecord(value = "after"))
        shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
        runner.pause()
        val remaining = requireNotNull(runner.state.value?.remainingDelayMs)
        assertTrue(remaining in 1..1999)
        shadowOf(Looper.getMainLooper()).idleFor(3, TimeUnit.SECONDS)
        runner.resume(); idle()
        assertTrue(requests.isEmpty())
        assertEquals(TaskStatus.PAUSED, runner.state.value?.status)
        runner.start(); idle()
        shadowOf(Looper.getMainLooper()).idleFor(remaining + 10, TimeUnit.MILLISECONDS)
        assertEquals("after", requests.single().record.value)
    }
    @Test fun stoppingInvalidatesQueuedRequestsAndNewPlansStayIndependent() {
        plan(DraftRecord(value = "Old"))
        val stale = requests.single()
        runner.stop()
        plan(DraftRecord(value = "New"))
        assertFalse(runner.accepts(stale))
        runner.dispatched(stale, TaskResult.Done); idle()
        assertEquals(TaskStatus.RUNNING, runner.state.value?.status)
        answer(TaskResult.Done)
        assertEquals(TaskStatus.COMPLETED, runner.state.value?.status)
    }
    @Test fun pausingBeforeDispatchPreventsStaleActionsFromLaunchingAfterResume() {
        plan(DraftRecord(value = "A"))
        val stale = requests.single()
        runner.pause(); runner.resume(); runner.start(); idle()
        assertFalse(runner.accepts(stale))
        assertEquals(2, requests.size)
        answer(TaskResult.Done)
        assertEquals(TaskStatus.COMPLETED, runner.state.value?.status)
    }
    @Test fun unsupportedRecordsAreShownAndCanBeSkippedWithoutLosingTheNextStep() {
        val raw = android.nfc.NdefRecord.createExternal("example.com", "unknown", byteArrayOf(1))
        val message = android.nfc.NdefMessage(arrayOf(raw, NdefCodec.encodeRecord(DraftRecord(value = "Keep me"))))
        assertTrue(runner.prepare(message))
        runner.start(); idle()
        assertEquals(TaskStatus.FAILED, runner.state.value?.status)
        assertTrue(requests.isEmpty())
        assertFalse(runner.prepare(NdefCodec.encode(listOf(DraftRecord(value = "Cannot replace running plan")))))
        runner.skip(); idle()
        assertEquals("Keep me", requests.single().record.value)
    }
}
