package com.tongzhouweiji.baichuan.ui.screen.scheduled

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tongzhouweiji.baichuan.BaichuanApplication
import com.tongzhouweiji.baichuan.core.db.entity.ScheduledMessageEntity
import com.tongzhouweiji.baichuan.core.work.Scheduler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessageScreen(agentId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BaichuanApplication
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("generated") }
    var content by remember { mutableStateOf("") }
    var windowStart by remember { mutableStateOf("08:00") }
    var windowEnd by remember { mutableStateOf("23:00") }
    var intervalMin by remember { mutableStateOf("60") }
    var intervalMax by remember { mutableStateOf("180") }
    var loaded by remember { mutableStateOf(false) }
    var existingId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(agentId) {
        val existing = app.container.scheduledMessageDao.listByAgent(agentId).firstOrNull()
        if (existing != null) {
            existingId = existing.id
            enabled = existing.enabled
            mode = existing.mode
            content = existing.content
            windowStart = existing.windowStart
            windowEnd = existing.windowEnd
            intervalMin = existing.intervalMin.toString()
            intervalMax = existing.intervalMax.toString()
        }
        loaded = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("主动消息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        enabled = loaded,
                        onClick = {
                            scope.launch {
                                val min = intervalMin.toIntOrNull()?.coerceAtLeast(1) ?: 60
                                val max = intervalMax.toIntOrNull()?.coerceAtLeast(min) ?: 180
                                val entity = ScheduledMessageEntity(
                                    id = existingId ?: 0,
                                    agentId = agentId,
                                    mode = mode,
                                    content = content,
                                    windowStart = windowStart.ifBlank { "08:00" },
                                    windowEnd = windowEnd.ifBlank { "23:00" },
                                    intervalMin = min,
                                    intervalMax = max,
                                    enabled = enabled,
                                )
                                val id = if (existingId == null) {
                                    app.container.scheduledMessageDao.insert(entity)
                                } else {
                                    app.container.scheduledMessageDao.update(entity)
                                    entity.id
                                }
                                val saved = entity.copy(id = id)
                                if (saved.enabled) {
                                    Scheduler.schedule(context, saved, System.currentTimeMillis() + 60_000L)
                                    android.widget.Toast.makeText(
                                        context,
                                        "已保存并调度：约 1 分钟后首次检查",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    Scheduler.cancel(context, id)
                                    android.widget.Toast.makeText(context, "已关闭主动消息", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                onBack()
                            }
                        },
                    ) { Text("保存") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "启用主动消息",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Spacer(Modifier.height(12.dp))
            Text("消息类型", style = MaterialTheme.typography.bodyMedium)
            Row {
                OutlinedButton(onClick = { mode = "generated" }) {
                    Text(if (mode == "generated") "· 生成新消息" else "生成新消息")
                }
                Spacer(Modifier.padding(horizontal = 4.dp))
                OutlinedButton(onClick = { mode = "static" }) {
                    Text(if (mode == "static") "· 发送预设内容" else "发送预设内容")
                }
            }
            if (mode == "static") {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("预设内容") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row {
                OutlinedTextField(
                    value = windowStart,
                    onValueChange = { windowStart = it },
                    label = { Text("开始时间") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                OutlinedTextField(
                    value = windowEnd,
                    onValueChange = { windowEnd = it },
                    label = { Text("结束时间") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row {
                OutlinedTextField(
                    value = intervalMin,
                    onValueChange = { intervalMin = it },
                    label = { Text("间隔最小（分钟）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                OutlinedTextField(
                    value = intervalMax,
                    onValueChange = { intervalMax = it },
                    label = { Text("间隔最大（分钟）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "受 Android 后台调度限制，实际发送时间会有延迟，不承诺精准。智能体停用或拉黑后不会发送。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
