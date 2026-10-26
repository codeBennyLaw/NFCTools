package com.benny.nfctools

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import android.provider.OpenableColumns
import android.net.Uri
import android.nfc.Tag
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.benny.nfctools.data.DeviceStatus
import com.benny.nfctools.ui.NfcApp

class MainActivity : ComponentActivity() {
    private val vm: NfcViewModel by viewModels()
    private val adapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private var resumed = false
    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED && resumed) refreshReader()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NfcApp(vm, ::openNfcSettings) }
        if (savedInstanceState == null) handleIntent(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    private fun handleIntent(incoming: Intent) {
        IntentCompat.getParcelableExtra(incoming, NfcAdapter.EXTRA_TAG, Tag::class.java)?.let { vm.onTag(it); return }
        if (incoming.action == Intent.ACTION_SEND) {
            val uri = IntentCompat.getParcelableExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java)
            if (uri != null) importShared(uri)
            else incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let(vm::paste)
        } else if (incoming.action == Intent.ACTION_VIEW && incoming.data?.scheme == "content") incoming.data?.let(::importShared)
    }
    private fun importShared(uri: Uri) {
        lifecycleScope.launch {
            try {
                val (name, mime) = withContext(Dispatchers.IO) {
                    var name = "shared.bin"
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
                    name to (contentResolver.getType(uri) ?: "application/octet-stream")
                }
                if (name.endsWith(".mct", true)) vm.importDump(uri, name)
                else {
                    vm.navigate(com.benny.nfctools.data.Destination.WRITE)
                    vm.importFile(uri, name, mime, name.endsWith(".ndef", true))
                }
            } catch (e: Exception) { vm.notify("无法导入分享内容：${e.message}") }
        }
    }
    override fun onResume() {
        super.onResume()
        resumed = true
        vm.resume()
        refreshReader()
    }
    override fun onStart() {
        super.onStart()
        // NFC broadcasts may originate from the separate privileged NFC service UID.
        // We trust no extras: refreshReader queries the actual adapter state.
        ContextCompat.registerReceiver(this, nfcStateReceiver, IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    }
    override fun onStop() {
        unregisterReceiver(nfcStateReceiver)
        super.onStop()
    }
    private fun refreshReader() {
        val nfc = adapter
        vm.setDevice(when { nfc == null -> DeviceStatus.UNSUPPORTED; !nfc.isEnabled -> DeviceStatus.DISABLED; else -> DeviceStatus.READY })
        if (nfc?.isEnabled == true) {
            try {
                nfc.enableReaderMode(this, { tag -> vm.onTag(tag) },
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NFC_BARCODE,
                    Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) })
            } catch (_: Exception) { vm.setDevice(DeviceStatus.DISABLED); vm.notify("无法启动读卡，请检查 NFC 设置") }
        }
    }
    override fun onPause() {
        resumed = false
        vm.pause()
        runCatching { adapter?.disableReaderMode(this) }
        super.onPause()
    }
    private fun openNfcSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }.onFailure { vm.notify("无法打开系统 NFC 设置") } }
    }
}
