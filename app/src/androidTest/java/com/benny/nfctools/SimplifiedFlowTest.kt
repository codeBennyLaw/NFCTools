package com.benny.nfctools

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.benny.nfctools.data.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class SimplifiedFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(app.activity)[NfcViewModel::class.java]
    private lateinit var scenarioIntent: Intent
    @Before fun reset() {
        app.runOnIdle { scenarioIntent = Intent(app.activity.intent); vm.clearDraft(); vm.selectKind(RecordKind.TEXT); vm.clearEntry(); vm.navigate(Destination.READ) }
    }
    @After fun restoreScenarioIntent() {
        // ActivityScenario matches lifecycle callbacks against its launch intent.
        // Production onNewIntent correctly replaces getIntent() after a share.
        app.runOnIdle { app.activity.intent = scenarioIntent; vm.clearEntry(); vm.clearDraft() }
    }
    @Test fun unfinishedEntrySurvivesRotationAndTabChanges() {
        app.onNodeWithText("写入", useUnmergedTree = true).performClick()
        app.onNodeWithText("文本内容").performTextInput("Keep this unfinished draft")
        app.activityRule.scenario.recreate()
        app.waitForIdle()
        app.onNode(hasSetTextAction() and hasText("Keep this unfinished draft")).assertExists()
        app.onNodeWithText("工具", useUnmergedTree = true).performClick()
        app.onNodeWithText("写入", useUnmergedTree = true).performClick()
        app.onNode(hasSetTextAction() and hasText("Keep this unfinished draft")).assertExists()
    }
    @Test fun systemShareOpensAnEditableUrlWithoutAddingDialogs() {
        app.runOnIdle {
            app.activity.startActivity(Intent(app.activity, MainActivity::class.java).setAction(Intent.ACTION_SEND)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://example.com/shared").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        app.waitUntil(5000) { vm.state.value.activeEntry.value == "https://example.com/shared" }
        app.onNodeWithText("快捷写入").assertExists()
        app.onNode(hasSetTextAction() and hasText("https://example.com/shared")).assertExists()
        assertEquals(RecordKind.URI, vm.state.value.composerKind)
    }
    @Test fun installedApplicationCanBeSelectedWithoutTypingPackageName() {
        app.onNodeWithText("写入", useUnmergedTree = true).performClick()
        app.onNodeWithText("打开应用", useUnmergedTree = true).performClick()
        app.onNodeWithText("搜索应用名称或包名").performTextInput("settings")
        app.waitUntil(5000) { app.onAllNodesWithText("com.android.settings").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithText("com.android.settings").performClick()
        app.onNode(hasSetTextAction() and hasText("com.android.settings")).assertExists()
        assertEquals(RecordKind.APPLICATION, vm.state.value.activeEntry.kind)
    }
    @Test fun editingReadContentsCreatesEditableRecords() {
        val message = com.benny.nfctools.nfc.NdefCodec.encode(listOf(DraftRecord(value = "Edit me")))
        val tag = TagSnapshot("TEST-EDIT", listOf("Ndef"), "Type 2", 144, true, true, false, emptyMap(), com.benny.nfctools.nfc.NdefCodec.base64(message.toByteArray()))
        app.runOnIdle { vm.openHistory(tag) }
        app.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("编辑读到的内容"))
        app.onNodeWithText("编辑读到的内容").performScrollTo().performClick()
        app.runOnIdle { assertEquals(Destination.WRITE, vm.state.value.destination) }
        app.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Edit me"))
        app.onNodeWithContentDescription("编辑文本").performClick()
        app.onNode(hasText("文本内容") and hasAnyAncestor(isDialog())).performTextReplacement("Changed")
        app.onNodeWithText("保存记录").performClick()
        app.runOnIdle { assertEquals("Changed", vm.state.value.draft.single().value) }
    }
}
