package com.benny.nfctools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.benny.nfctools.tasks.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskSheet(run: TaskRun, runner: TaskRunner) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = runner::stop, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("顺序任务 · ${run.steps.size} 步", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(when (run.status) {
                TaskStatus.READY -> "按列表顺序执行，外部应用返回后点击继续。"
                TaskStatus.COMPLETED -> "任务已处理完毕"
                TaskStatus.RUNNING -> "正在处理第 ${run.index + 1} 步"
                else -> run.message ?: "任务已暂停"
            }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(run.steps) { index, step ->
                    val label = when {
                        index < run.index -> "已处理"
                        index > run.index -> "待执行"
                        run.status == TaskStatus.FAILED -> "执行失败"
                        run.status == TaskStatus.WAITING_RETURN -> "等待继续"
                        run.status == TaskStatus.RUNNING -> "执行中"
                        else -> "待执行"
                    }
                    Surface(color = if (index == run.index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${index + 1}. ${step.title} · $label", fontWeight = FontWeight.Medium)
                            Text(step.summary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (run.status) {
                    TaskStatus.READY -> Button(onClick = runner::start) { Text("开始执行") }
                    TaskStatus.PAUSED -> Button(onClick = runner::start) { Text("继续执行") }
                    TaskStatus.FAILED -> Button(onClick = runner::start) { Text("重试本步") }
                    TaskStatus.WAITING_RETURN -> Button(onClick = runner::continueAfterExternal) { Text("已完成，继续下一步") }
                    TaskStatus.RUNNING -> CircularProgressIndicator(Modifier.size(32.dp))
                    TaskStatus.COMPLETED -> Button(onClick = runner::stop) { Text("完成") }
                }
                if (run.status in listOf(TaskStatus.FAILED, TaskStatus.PAUSED)) OutlinedButton(onClick = runner::skip) { Text("跳过本步") }
                if (run.status != TaskStatus.COMPLETED) TextButton(onClick = runner::stop) { Text("停止任务") }
            }
        }
    }
}
