package com.benny.nfctools

import android.media.AudioManager
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import com.benny.nfctools.system.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class SystemActionDeviceTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(app.activity)[NfcViewModel::class.java]
    private val audio get() = app.activity.getSystemService(AudioManager::class.java)
    private var originalVolume = 0
    @Before fun setup() {
        originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        app.runOnIdle { vm.clearDraft(); vm.selectKind(RecordKind.SYSTEM); vm.clearEntry() }
    }
    @After fun cleanup() {
        app.runOnIdle { audio.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0); vm.clearDraft() }
    }

    @Test fun systemFunctionFormSurvivesRotationAndKeepsOneOperation() {
        app.onNodeWithText("选择系统功能").performClick()
        app.onNodeWithText("自动旋转").performClick()
        app.onNodeWithText("关闭").performScrollTo().performClick()
        app.runOnIdle { assertEquals("off", vm.state.value.activeEntry.secondary) }
        app.activityRule.scenario.recreate()
        app.onNodeWithText("自动旋转").assertExists()
        app.runOnIdle {
            assertEquals(SystemAction(SystemFunction.AUTO_ROTATE, "off"), SystemAction.fromMessage(NdefCodec.parse(requireNotNull(vm.messageBytes()))))
            assertEquals(1, NdefCodec.parse(requireNotNull(vm.messageBytes())).records.size)
        }
    }
    @Test fun trialActuallyAppliesMediaVolume() {
        app.runOnIdle { vm.quickEntry(SystemAction(SystemFunction.MEDIA_VOLUME, "25").draft()) }
        app.onNodeWithText("在本机试运行").performScrollTo().performClick()
        app.waitUntil(5000) { audio.getStreamVolume(AudioManager.STREAM_MUSIC) == (audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.25f).roundToInt() }
    }
    @Test fun missingSpecialAccessShowsContextualPermissionAndCancelDoesNothing() {
        if (Settings.System.canWrite(app.activity)) return
        val before = Settings.System.getInt(app.activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        app.runOnIdle { vm.quickEntry(SystemAction(SystemFunction.BRIGHTNESS, "25").draft()) }
        app.onNodeWithText("在本机试运行").performScrollTo().performClick()
        app.onNodeWithText("需要修改系统设置").assertExists()
        app.onNodeWithText("取消").performClick()
        assertEquals(before, Settings.System.getInt(app.activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
    }
    @Test fun openingLocalSystemCardDoesNotExecuteUntilButtonIsPressed() {
        val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val action = SystemAction(SystemFunction.MEDIA_VOLUME, "25")
        val snapshot = TagSnapshot("LOCAL-SYSTEM", listOf("Ndef"), "Type 2", 492, true, true, false, emptyMap(), NdefCodec.base64(NdefCodec.encode(listOf(action.draft())).toByteArray()))
        app.runOnIdle { vm.openHistory(snapshot) }
        app.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("执行此操作"))
        assertEquals(before, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        app.onNodeWithText("执行此操作").performClick()
        app.waitUntil(5000) { audio.getStreamVolume(AudioManager.STREAM_MUSIC) == (audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.25f).roundToInt() }
    }
    @Test fun panelChoiceDoesNotPretendToBeADirectSwitch() {
        app.onNodeWithText("打开设置", substring = false).performClick()
        app.onNodeWithText("Wi-Fi 设置").assertExists()
        app.onNodeWithText("打开系统 Wi-Fi 面板，由你确认开关").assertExists()
        app.runOnIdle {
            assertEquals(SystemAction(SystemFunction.WIFI_SETTINGS), SystemAction.fromMessage(NdefCodec.parse(requireNotNull(vm.messageBytes()))))
        }
    }
}
