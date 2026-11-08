package com.tongzhouweiji.baichuan.ui.screen.mcp

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tongzhouweiji.baichuan.BaichuanApplication
import com.tongzhouweiji.baichuan.core.db.entity.McpServerEntity
import com.tongzhouweiji.baichuan.core.db.entity.McpToolEntity
import com.tongzhouweiji.baichuan.core.mcp.McpClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BaichuanApplication
    val scope = rememberCoroutineScope()
    val servers by app.container.mcpDao.observeServers().collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MCP 连接") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加服务器")
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
            if (servers.isEmpty()) {
                Text(
                    text = "还没有 MCP 服务器。第一版只提供连接配置与状态登记，工具调用将在 Agent 阶段接入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(servers, key = { it.id }) { server ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(server.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "${server.transport} · ${server.endpoint}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = when (server.lastStatus) {
                                        "connected" -> "已连接"
                                        "failed" -> "连接失败"
                                        else -> "未连接"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (server.lastStatus == "connected") MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                                    onClick = {
                                        scope.launch {
                                            status = "连接中…"
                                            val now = System.currentTimeMillis()
                                            runCatching {
                                                val info = McpClient.initialize(server.endpoint)
                                                val tools = McpClient.listTools(server.endpoint)
                                                app.container.mcpDao.clearTools(server.id)
                                                if (tools.isNotEmpty()) {
                                                    app.container.mcpDao.insertTools(
                                                        tools.map { tool ->
                                                            McpToolEntity(
                                                                serverId = server.id,
                                                                name = tool.name,
                                                                description = tool.description,
                                                                schemaJson = tool.schemaJson,
                                                            )
                                                        },
                                                    )
                                                }
                                                app.container.mcpDao.updateServer(
                                                    server.copy(
                                                        lastStatus = "connected",
                                                        lastConnectedAt = now,
                                                    ),
                                                )
                                                "已连接「${info.name.ifBlank { server.name }}」，${tools.size} 个工具：${tools.joinToString { it.name }.take(80)}"
                                            }.onFailure { error ->
                                                app.container.mcpDao.updateServer(
                                                    server.copy(lastStatus = "failed", lastConnectedAt = now),
                                                )
                                                status = "连接失败：${error.message}"
                                            }.getOrNull()?.let { status = it }
                                        }
                                    },
                                ) { Text("连接并读取工具") }
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    app.container.mcpDao.deleteServer(server)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (status != null) {
                Text(
                    text = status.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加 MCP 服务器") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("地址（http / sse）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && endpoint.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val transport = if (endpoint.startsWith("http", ignoreCase = true)) "http" else "stdio"
                            app.container.mcpDao.insertServer(
                                McpServerEntity(
                                    name = name.trim(),
                                    transport = transport,
                                    endpoint = endpoint.trim(),
                                    createdAt = System.currentTimeMillis(),
                                ),
                            )
                            name = ""
                            endpoint = ""
                            showAdd = false
                        }
                    },
                ) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
        )
    }
}
