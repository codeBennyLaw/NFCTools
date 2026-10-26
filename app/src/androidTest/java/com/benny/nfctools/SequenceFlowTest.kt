package com.benny.nfctools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import com.benny.nfctools.tasks.TaskStatus
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class SequenceFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(app.activity)[NfcViewModel::class.java]
    private lateinit var originalDraft: List<DraftRecord>
    private lateinit var originalEntry: DraftRecord
    private var originalImported: String? = null
    private var originalMode = false
    private lateinit var originalIntent: Intent
    private val store get() = LocalStore(app.activity)
    @Before fun setUp() {
        app.runOnIdle {
            originalIntent = Intent(app.activity.intent)
            originalDraft = store.draft(); originalImported = store.imported(); originalEntry = store.quickEntry(); originalMode = store.sequenceMode()
            vm.tasks.stop(); vm.clearDraft(); vm.selectKind(RecordKind.TEXT); vm.clearEntry()
        }
    }
    @After fun restore() {
        app.runOnIdle {
            app.activity.intent = originalIntent
            vm.tasks.stop(); vm.clearDraft(); vm.clearEntry()
            store.saveDraft(originalDraft, originalImported, originalMode); store.saveQuickEntry(originalEntry)
        }
    }
    @Test fun movingStepsUpdatesTheSavedOrderAndSurvivesRotation() {
        app.runOnIdle {
            vm.add(DraftRecord(value = "First")); vm.add(DraftRecord(value = "Second")); vm.add(DraftRecord(kind = RecordKind.DELAY, value = "1"))
            vm.navigate(Destination.WRITE)
        }
        app.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("上移第 2 项"))
        app.onNodeWithContentDescription("上移第 2 项").performClick()
        app.runOnIdle { assertEquals(listOf("Second", "First", "1"), store.draft().map { it.value }); assertTrue(store.sequenceMode()) }
        app.activityRule.scenario.recreate()
        app.runOnIdle { assertEquals(listOf("Second", "First", "1"), vm.state.value.draft.map { it.value }); assertTrue(vm.state.value.sequenceMode) }
        capture("sequence-editor")
    }
    @Test fun textAndDelayStepsRunInOrderAndShowCompletion() {
        prepare(listOf(DraftRecord(value = "Before"), DraftRecord(kind = RecordKind.DELAY, value = "1"), DraftRecord(value = "After")))
        app.onNodeWithText("顺序任务 · 3 步").assertExists()
        capture("sequence-preview")
        app.onNodeWithText("开始执行").assertIsDisplayed().performClick()
        app.waitUntil(6000) { vm.tasks.state.value?.status == TaskStatus.COMPLETED }
        app.onNodeWithText("任务已处理完毕").assertExists()
        app.runOnIdle { assertEquals("After", clipboardText()) }
    }
    @Test fun externalAppWaitsForExplicitContinuationAfterReturning() {
        prepare(listOf(DraftRecord(kind = RecordKind.APPLICATION, value = "com.android.settings"), DraftRecord(value = "After returning")))
        app.runOnIdle { clipboard().setPrimaryClip(ClipData.newPlainText("before", "Before returning")) }
        app.onNodeWithText("开始执行").performClick()
        waitFor { vm.tasks.state.value?.status == TaskStatus.WAITING_RETURN }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("am start -n com.benny.nfctools/.MainActivity")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() }
        app.onNodeWithText("已完成，继续下一步").assertExists()
        app.runOnIdle { assertEquals(0, vm.tasks.state.value?.index); assertEquals("Before returning", clipboardText()) }
        app.onNodeWithText("已完成，继续下一步").performClick()
        app.waitUntil(5000) { vm.tasks.state.value?.status == TaskStatus.COMPLETED }
        app.runOnIdle { assertEquals("After returning", clipboardText()) }
    }
    @Test fun rotationPausesDelayUntilTheUserResumes() {
        prepare(listOf(DraftRecord(kind = RecordKind.DELAY, value = "3"), DraftRecord(value = "Continued")))
        app.onNodeWithText("开始执行").performClick()
        app.waitUntil(5000) { vm.tasks.state.value?.status == TaskStatus.RUNNING }
        app.activityRule.scenario.recreate()
        app.onNodeWithText("继续执行").assertExists()
        app.runOnIdle { assertEquals(TaskStatus.PAUSED, vm.tasks.state.value?.status) }
        app.onNodeWithText("继续执行").performClick()
        app.waitUntil(6000) { vm.tasks.state.value?.status == TaskStatus.COMPLETED }
    }
    private fun prepare(steps: List<DraftRecord>) {
        val encoded = NdefCodec.base64(NdefCodec.encode(steps, true).toByteArray())
        app.runOnIdle { vm.prepareTasks(encoded) }
    }
    private fun clipboard() = app.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private fun clipboardText() = clipboard().primaryClip?.getItemAt(0)?.text.toString()
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("Task step did not change state", condition())
    }
    private fun capture(name: String) {
        app.waitForIdle()
        // Let the system clipboard overlay and dialog window animation finish.
        Thread.sleep(7000)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return
        try { File(app.activity.getExternalFilesDir(null), "$name-0.2.0.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { screenshot.recycle() }
    }
}
