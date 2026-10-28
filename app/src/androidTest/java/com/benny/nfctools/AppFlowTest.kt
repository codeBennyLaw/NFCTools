package com.benny.nfctools

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import androidx.lifecycle.ViewModelProvider
import com.benny.nfctools.data.RecordKind

class AppFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    @Before fun resetDraft() {
        app.runOnIdle {
            ViewModelProvider(app.activity)[NfcViewModel::class.java].apply { clearDraft(); selectKind(RecordKind.TEXT); clearEntry(); navigate(com.benny.nfctools.data.Destination.READ) }
        }
    }

    @Test fun singleOperationCanBeEditedAndClearedWithoutAList() {
        app.onNodeWithText("写入", useUnmergedTree = true).performClick()
        app.onNodeWithText("文本内容").performTextInput("NFC flow test")
        app.onNode(hasSetTextAction() and hasText("NFC flow test")).performTextReplacement("Updated NFC text")
        app.onNodeWithText("Updated NFC text").assertIsDisplayed()
        app.onNodeWithText("清空当前操作").performScrollTo().performClick()
        app.onNodeWithText("Updated NFC text").assertDoesNotExist()
    }
    @Test fun invalidUrlKeepsEditorOpenAndShowsError() {
        app.onNodeWithText("写入", useUnmergedTree = true).performClick()
        app.onNodeWithText("网址", useUnmergedTree = true).performClick()
        app.onNode(hasSetTextAction() and hasText("网址")).performTextInput("not-a-url")
        app.runOnIdle { ViewModelProvider(app.activity)[NfcViewModel::class.java].setDevice(com.benny.nfctools.data.DeviceStatus.READY) }
        app.onNodeWithText("贴卡写入").performClick()
        app.onNodeWithText("请输入有效网址，例如 example.com 或 https://example.com").assertExists()
    }
    @Test fun destructiveToolRequiresConfirmationAndCanBeCancelled() {
        app.onNodeWithText("工具", useUnmergedTree = true).performClick()
        scrollTo("设为永久只读")
        app.onNodeWithText("设为永久只读").performClick()
        app.onNodeWithText("永久锁定为只读？").assertExists()
        app.onNodeWithText("取消").performClick()
        app.onNodeWithText("永久锁定为只读？").assertDoesNotExist()
    }
    @Test fun sampleIsLabelledAndDoesNotCreateHistory() {
        app.onNodeWithText("工具", useUnmergedTree = true).performClick()
        scrollTo("查看示例标签")
        app.onNodeWithText("查看示例标签").performClick()
        scrollTo("示例标签 · 界面演示")
        app.onNodeWithText("示例标签 · 界面演示").assertIsDisplayed()
        app.onNodeWithText("演示数据 · 非真实卡片").assertExists()
        app.onNodeWithText("记录", useUnmergedTree = true).performClick()
        app.onNodeWithText("还没有轻触记录").assertExists()
    }
    private fun scrollTo(text: String) {
        app.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }
}
