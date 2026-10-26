package com.benny.nfctools

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.provider.Settings
import com.benny.nfctools.data.*
import com.benny.nfctools.nfc.NdefCodec
import com.benny.nfctools.tasks.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TaskDispatcherTest {
    private val controller = Robolectric.buildActivity(Activity::class.java)
    private lateinit var activity: Activity
    @Before fun create() { activity = controller.setup().get() }
    @After fun close() { controller.pause().stop().destroy() }
    private fun dispatch(record: DraftRecord) = runBlocking { TaskDispatcher.dispatch(activity, record) }

    @Test fun textIsCopiedLocallyWithoutStartingAnotherActivity() {
        assertEquals(TaskResult.Done, dispatch(DraftRecord(value = "Ordered clipboard")))
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("Ordered clipboard", clipboard.primaryClip?.getItemAt(0)?.text.toString())
        assertNull(shadowOf(activity).nextStartedActivity)
    }
    @Test fun phoneAndSmsOpenUserControlledEditors() {
        assertTrue(dispatch(DraftRecord(kind = RecordKind.PHONE, value = "12345678")) is TaskResult.External)
        assertEquals(Intent.ACTION_DIAL, shadowOf(activity).nextStartedActivity.action)
        assertTrue(dispatch(DraftRecord(kind = RecordKind.SMS, value = "12345678", secondary = "Draft only")) is TaskResult.External)
        val sms = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_SENDTO, sms.action)
        assertTrue(sms.data.toString().contains("Draft%20only"))
    }
    @Test fun contactUsesAnInsertScreenAndKeepsTheEnteredFields() {
        dispatch(DraftRecord(kind = RecordKind.CONTACT, value = "Alice", secondary = "12345678", tertiary = "alice@example.com"))
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals("Alice", intent.getStringExtra(ContactsContract.Intents.Insert.NAME))
        assertEquals("12345678", intent.getStringExtra(ContactsContract.Intents.Insert.PHONE))
    }
    // FileProvider root matching assumes Android path separators. The real
    // provider and URI grants are covered by FileTransferDeviceTest on Android.
    @Test fun wifiRequestsSystemConfirmationAndMissingAppsAreReportedAsFailure() {
        assertTrue(dispatch(DraftRecord(kind = RecordKind.WIFI, value = "Home", secondary = "password123")) is TaskResult.External)
        assertEquals(Settings.ACTION_WIFI_ADD_NETWORKS, shadowOf(activity).nextStartedActivity.action)
        assertTrue(dispatch(DraftRecord(kind = RecordKind.APPLICATION, value = "missing.example.app")) is TaskResult.Failure)
    }
}
