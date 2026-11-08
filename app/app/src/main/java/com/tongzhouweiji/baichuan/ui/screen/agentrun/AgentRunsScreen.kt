package com.tongzhouweiji.baichuan.ui.screen.agentrun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tongzhouweiji.baichuan.BaichuanApplication
import com.tongzhouweiji.baichuan.core.db.entity.AgentRunEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Agent 运行记录：只读查看，执行轨迹可追溯。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentRunsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BaichuanApplication
    val runs by app.container.agentRunDao.observeRuns().collectAsState(initial = emptyList())
    var expandedId by remember { mutableStateOf<Long?>(null) }
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Agent 运行记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (runs.isEmpty()) {
                Text(
                    text = "还没有运行记录。授权智能体在聊天页使用 Agent 后，这里会显示任务与工具调用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(runs, key = { it.id }) { run ->
                        RunRow(
                            run = run,
                            expanded = expandedId == run.id,
                            formatter = formatter,
                            onToggle = { expandedId = if (expandedId == run.id) null else run.id },
                            actions = { app.container.agentRunDao },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunRow(
    run: AgentRunEntity,
    expanded: Boolean,
    formatter: SimpleDateFormat,
    onToggle: () -> Unit,
    actions: () -> com.tongzhouweiji.baichuan.core.db.dao.AgentRunDao,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatter.format(Date(run.startedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            Text(
                text = when (run.state) {
                    "running" -> "运行中"
                    "done" -> "已完成"
                    "cancelled" -> "已取消"
                    else -> "失败"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (run.state == "running") MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            Text(
                text = "等级 ${run.permissionLevel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = run.goal,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (expanded) {
            val list by actions().observeActions(run.id).collectAsState(initial = emptyList())
            if (list.isEmpty()) {
                Text(
                    text = "没有工具调用记录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                list.forEach { action ->
                    Text(
                        text = "· ${action.toolName} → ${action.resultSummary ?: "无结果"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
