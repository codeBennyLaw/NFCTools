package com.benny.nfctools.tasks

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.benny.nfctools.data.DraftRecord
import com.benny.nfctools.data.RecordKind
import com.benny.nfctools.nfc.NdefCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object TaskDispatcher {
    suspend fun dispatch(context: Context, record: DraftRecord): TaskResult = try {
        NdefCodec.encodeRecord(record) // Revalidate data from imported or untrusted tags.
        when (record.kind) {
            RecordKind.TEXT -> {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("NFC 任务", record.value))
                TaskResult.Done
            }
            RecordKind.DELAY -> error("等待步骤由任务执行器处理")
            else -> {
                val intent = when (record.kind) {
                    RecordKind.APPLICATION -> context.packageManager.getLaunchIntentForPackage(record.value) ?: error("手机未安装或无法打开此应用")
                    RecordKind.WIFI -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val network = WifiNetworkSuggestion.Builder().setSsid(record.value).apply {
                            if (record.secondary.isNotEmpty()) setWpa2Passphrase(record.secondary)
                        }.build()
                        Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(network))
                    } else Intent(Settings.ACTION_WIFI_SETTINGS)
                    RecordKind.CONTACT -> Intent(Intent.ACTION_INSERT).setType(ContactsContract.Contacts.CONTENT_TYPE)
                        .putExtra(ContactsContract.Intents.Insert.NAME, record.value)
                        .putExtra(ContactsContract.Intents.Insert.PHONE, record.secondary)
                        .putExtra(ContactsContract.Intents.Insert.EMAIL, record.tertiary)
                    RecordKind.FILE, RecordKind.HEX -> {
                        val original = NdefCodec.encodeRecord(record)
                        val mime = requireNotNull(original.toMimeType())
                        val uri = withContext(Dispatchers.IO) {
                            val folder = File(context.cacheDir, "sequence").apply { mkdirs() }
                            val digest = MessageDigest.getInstance("SHA-256").digest(original.payload).joinToString("") { "%02x".format(it) }
                            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
                            val file = File(folder, "$digest.$extension")
                            file.writeBytes(original.payload)
                            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        }
                        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    RecordKind.PHONE -> Intent(Intent.ACTION_DIAL, NdefCodec.encodeRecord(record).toUri())
                    RecordKind.SMS, RecordKind.EMAIL -> Intent(Intent.ACTION_SENDTO, NdefCodec.encodeRecord(record).toUri())
                    else -> Intent(Intent.ACTION_VIEW, NdefCodec.encodeRecord(record).toUri())
                }
                context.startActivity(intent)
                TaskResult.External(when (record.kind) {
                    RecordKind.WIFI -> if (Build.VERSION.SDK_INT >= 30) "请在系统界面确认网络；连接情况以系统显示为准，返回后继续。" else "请在 Wi-Fi 设置中手动连接 ${record.value}，返回后继续。"
                    RecordKind.PHONE -> "已打开拨号界面；是否拨出由你确认，返回后继续。"
                    RecordKind.SMS, RecordKind.EMAIL, RecordKind.CONTACT -> "请在打开的界面中确认或取消操作，返回工具箱后继续。"
                    else -> "已打开外部应用，完成后返回工具箱，点击继续下一步。"
                })
            }
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: ActivityNotFoundException) { TaskResult.Failure("没有可处理此内容的应用，可跳过本步") }
    catch (error: Exception) { TaskResult.Failure(error.message ?: "执行失败，可重试或跳过") }
}
