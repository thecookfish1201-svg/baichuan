package com.tongzhouweiji.baichuan.ui.screen.plugin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.tongzhouweiji.baichuan.core.db.entity.PluginEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BaichuanApplication
    val scope = rememberCoroutineScope()
    val plugins by app.container.pluginDao.observeAll().collectAsState(initial = emptyList())
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PluginEntity?>(null) }
    var pendingGrant by remember { mutableStateOf<PluginEntity?>(null) }
    var pendingPermissions by remember { mutableStateOf<List<String>>(emptyList()) }

    val installer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = runCatching { app.container.pluginManager.install(uri) }.fold(
                    onSuccess = { "已安装：${it.name} ${it.version}" },
                    onFailure = { "安装失败：${it.message}" },
                )
                busy = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("未济插件") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { installer.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                        Icon(Icons.Default.Add, contentDescription = "安装插件")
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
            if (plugins.isEmpty()) {
                Text(
                    text = "还没有插件。点右上角安装 ZIP 包，或先装一个内部测试插件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(plugins, key = { it.id }) { plugin ->
                        PluginRow(
                            plugin = plugin,
                            onToggle = { checked ->
                                if (!checked) {
                                    scope.launch {
                                        runCatching { app.container.pluginManager.setEnabled(plugin.pluginId, false) }
                                            .onFailure { status = it.message }
                                        app.container.refreshTheme()
                                    }
                                } else {
                                    scope.launch {
                                        val declared = runCatching {
                                            kotlinx.serialization.json.Json.parseToJsonElement(plugin.permissionsJson)
                                                .let { element ->
                                                    if (element is kotlinx.serialization.json.JsonArray) {
                                                        element.mapNotNull { it.toString().trim('"') }
                                                    } else {
                                                        emptyList()
                                                    }
                                                }
                                        }.getOrDefault(emptyList())
                                        val records = app.container.pluginPermissionDao.listByPlugin(plugin.pluginId)
                                        val ungranted = declared.filter { permission ->
                                            records.none { it.permission == permission && it.granted }
                                        }
                                        if (ungranted.isEmpty()) {
                                            runCatching { app.container.pluginManager.setEnabled(plugin.pluginId, true) }
                                                .onFailure { status = it.message }
                                            app.container.refreshTheme()
                                        } else {
                                            pendingPermissions = ungranted
                                            pendingGrant = plugin
                                        }
                                    }
                                }
                            },
                            onDelete = { pendingDelete = plugin },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        status = runCatching {
                            app.container.pluginManager.installBundled("plugins/test-plugin.zip")
                        }.fold(
                            onSuccess = { "已安装内部测试插件，去启用它试试主题变化" },
                            onFailure = { "安装失败：${it.message}" },
                        )
                        busy = false
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text("安装内部测试插件（主题）") }
            if (busy) {
                Text(
                    text = "处理中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
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

    pendingGrant?.let { plugin ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingGrant = null },
            title = { Text("授权插件") },
            text = {
                Column {
                    Text("「${plugin.name}」请求以下权限，确认后才会启用：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    pendingPermissions.forEach { permission ->
                        Text(
                            text = "· " + com.tongzhouweiji.baichuan.core.plugin.PluginPermissions.describe(permission),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching {
                            pendingPermissions.forEach { permission ->
                                val existing = app.container.pluginPermissionDao.find(plugin.pluginId, permission)
                                if (existing == null) {
                                    app.container.pluginPermissionDao.insertAll(
                                        listOf(
                                            com.tongzhouweiji.baichuan.core.db.entity.PluginPermissionEntity(
                                                pluginId = plugin.pluginId,
                                                permission = permission,
                                                granted = true,
                                                grantedAt = System.currentTimeMillis(),
                                            ),
                                        ),
                                    )
                                }
                            }
                            app.container.pluginManager.setEnabled(plugin.pluginId, true)
                        }.onFailure { status = it.message }
                        app.container.refreshTheme()
                        pendingGrant = null
                    }
                }) { Text("授权并启用") }
            },
            dismissButton = {
                TextButton(onClick = { pendingGrant = null }) { Text("拒绝") }
            },
        )
    }

    pendingDelete?.let { plugin ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("卸载插件") },
            text = { Text("卸载「${plugin.name}」并删除其数据？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { app.container.pluginManager.uninstall(plugin.pluginId, deleteData = true) }
                        app.container.refreshTheme()
                        pendingDelete = null
                    }
                }) { Text("卸载", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun PluginRow(
    plugin: PluginEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(plugin.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "v${plugin.version} · 未济接口 ${plugin.apiVersion} · ${if (plugin.type == "config") "配置型" else "代码型"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = when (plugin.state) {
                    "enabled" -> "已启用"
                    "disabled" -> "已停用"
                    "failed" -> "加载失败"
                    else -> "已安装"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (plugin.state == "enabled") MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = plugin.state == "enabled",
            onCheckedChange = onToggle,
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "卸载",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
