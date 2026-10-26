package com.benny.nfctools.tasks

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.os.SystemClock
import com.benny.nfctools.data.DraftRecord
import com.benny.nfctools.data.RecordKind
import com.benny.nfctools.nfc.NdefCodec
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID

enum class TaskStatus { READY, RUNNING, WAITING_RETURN, PAUSED, FAILED, COMPLETED }
data class TaskStep(val title: String, val summary: String, val record: DraftRecord?)
data class TaskRun(val steps: List<TaskStep>, val index: Int = 0, val status: TaskStatus = TaskStatus.READY,
                   val message: String? = null, val remainingDelayMs: Long? = null)
data class TaskRequest(val id: String, val record: DraftRecord)
sealed interface TaskResult {
    data object Done : TaskResult
    data class External(val instruction: String) : TaskResult
    data class Failure(val reason: String) : TaskResult
}

/** Runs local work in order, and waits for explicit continuation after external UI. */
class TaskRunner(private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow<TaskRun?>(null)
    val state = mutable.asStateFlow()
    private val channel = Channel<TaskRequest>(Channel.BUFFERED)
    val requests = channel.receiveAsFlow()
    private var foreground = true
    private var job: Job? = null
    private var reply: Pair<String, CompletableDeferred<TaskResult>>? = null
    private var delayStartedAt: Long? = null

    fun prepare(message: NdefMessage): Boolean {
        if (state.value?.status in listOf(TaskStatus.RUNNING, TaskStatus.WAITING_RETURN, TaskStatus.PAUSED, TaskStatus.FAILED)) return false
        val steps = NdefCodec.visibleRecords(message).filter { it.tnf != NdefRecord.TNF_EMPTY }.map { raw ->
            val one = NdefMessage(arrayOf(raw))
            val record = runCatching { NdefCodec.editable(one).single().also { NdefCodec.encodeRecord(it) } }.getOrNull()
            val decoded = NdefCodec.decode(one).single()
            TaskStep(when (record?.kind) {
                RecordKind.TEXT -> "复制文本"
                RecordKind.CONTACT -> "添加联系人"
                RecordKind.PHONE -> "拨号"
                RecordKind.SMS -> "撰写短信"
                RecordKind.EMAIL -> "撰写邮件"
                RecordKind.WIFI -> "连接 Wi-Fi"
                RecordKind.FILE, RecordKind.HEX -> "打开文件"
                else -> decoded.title
            }, when (record?.kind) {
                RecordKind.DELAY -> "${record.value} 秒"
                RecordKind.WIFI -> record.value
                RecordKind.FILE -> "${raw.payload.size} 字节 · ${record.mime}"
                else -> (record?.value ?: "此记录不支持执行，可跳过").take(160)
            }, record)
        }
        if (steps.isEmpty()) return false
        stop()
        mutable.value = TaskRun(steps)
        return true
    }

    fun start() {
        val current = state.value ?: return
        if (!foreground || current.status !in listOf(TaskStatus.READY, TaskStatus.PAUSED, TaskStatus.FAILED)) return
        mutable.value = current.copy(status = TaskStatus.RUNNING, message = null)
        job = scope.launch {
            try {
                while (isActive) {
                    val run = state.value ?: return@launch
                    if (run.index >= run.steps.size) { mutable.value = run.copy(status = TaskStatus.COMPLETED); return@launch }
                    val record = run.steps[run.index].record
                    if (record == null) { mutable.value = run.copy(status = TaskStatus.FAILED, message = "此记录不支持执行，请跳过本步"); return@launch }
                    if (record.kind == RecordKind.DELAY) {
                        val duration = run.remainingDelayMs ?: (record.value.toLong() * 1000)
                        mutable.value = run.copy(remainingDelayMs = duration)
                        delayStartedAt = SystemClock.elapsedRealtime()
                        delay(duration)
                        delayStartedAt = null
                    } else {
                        val request = TaskRequest(UUID.randomUUID().toString(), record)
                        val result = CompletableDeferred<TaskResult>()
                        reply = request.id to result
                        channel.send(request)
                        val outcome = result.await()
                        reply = null
                        when (outcome) {
                            is TaskResult.External -> { mutable.value = run.copy(status = TaskStatus.WAITING_RETURN, message = outcome.instruction); return@launch }
                            is TaskResult.Failure -> { mutable.value = run.copy(status = TaskStatus.FAILED, message = outcome.reason); return@launch }
                            TaskResult.Done -> Unit
                        }
                    }
                    mutable.value = run.copy(index = run.index + 1, status = TaskStatus.RUNNING, remainingDelayMs = null)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it?.copy(status = TaskStatus.FAILED, message = error.message ?: "执行失败") } }
        }
    }

    fun accepts(request: TaskRequest): Boolean = foreground && state.value?.status == TaskStatus.RUNNING && reply?.first == request.id
    fun dispatched(request: TaskRequest, result: TaskResult) {
        if (!accepts(request)) return
        // Mark external work before Activity.onPause can pause this dispatch.
        if (result is TaskResult.External) mutable.update { it?.copy(status = TaskStatus.WAITING_RETURN, message = result.instruction) }
        reply?.second?.complete(result)
    }
    fun continueAfterExternal() {
        val run = state.value ?: return
        if (!foreground || run.status != TaskStatus.WAITING_RETURN) return
        mutable.value = run.copy(index = run.index + 1, status = TaskStatus.PAUSED, message = null)
        start()
    }
    fun skip() {
        val run = state.value ?: return
        if (!foreground || run.status !in listOf(TaskStatus.FAILED, TaskStatus.PAUSED, TaskStatus.WAITING_RETURN)) return
        mutable.value = run.copy(index = run.index + 1, status = TaskStatus.PAUSED, message = null, remainingDelayMs = null)
        start()
    }
    fun pause() {
        foreground = false
        val run = state.value ?: return
        if (run.status == TaskStatus.RUNNING) {
            val remaining = delayStartedAt?.let { started -> ((run.remainingDelayMs ?: 0) - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(0) }
            job?.cancel(); reply?.second?.cancel(); reply = null; delayStartedAt = null
            mutable.value = run.copy(status = TaskStatus.PAUSED, remainingDelayMs = remaining ?: run.remainingDelayMs, message = "已暂停，返回后点击继续执行")
        }
    }
    fun resume() { foreground = true }
    fun stop() {
        job?.cancel(); job = null
        reply?.second?.cancel(); reply = null; delayStartedAt = null
        mutable.value = null
    }
}
