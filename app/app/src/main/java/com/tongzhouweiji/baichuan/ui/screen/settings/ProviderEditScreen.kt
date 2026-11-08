package com.tongzhouweiji.baichuan.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tongzhouweiji.baichuan.BaichuanApplication
import com.tongzhouweiji.baichuan.core.db.entity.ModelEntity
import com.tongzhouweiji.baichuan.core.db.entity.ProviderEntity
import com.tongzhouweiji.baichuan.core.security.SecretStore
import com.tongzhouweiji.baichuan.model.ModelAdapterRegistry
import com.tongzhouweiji.baichuan.model.ModelException
import com.tongzhouweiji.baichuan.model.ModelInfo
import com.tongzhouweiji.baichuan.model.ProviderConfig
import com.tongzhouweiji.baichuan.model.ProviderProtocol
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(
    providerId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as BaichuanApplication
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://api.deepseek.com") }
    var apiKey by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var fetched by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(providerId) {
        if (providerId != 0L) {
            app.container.providerDao.findById(providerId)?.let { provider ->
                name = provider.name
                baseUrl = provider.baseUrl
                apiKey = SecretStore.decrypt(provider.keyRef.orEmpty())
            }
        }
    }

    fun config(): ProviderConfig = ProviderConfig(
        id = providerId,
        name = name.trim(),
        baseUrl = baseUrl.trim(),
        protocol = ProviderProtocol.OPENAI_COMPATIBLE,
        apiKey = apiKey.trim(),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (providerId == 0L) "添加服务商" else "编辑服务商") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (providerId != 0L) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除服务商",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    TextButton(
                        enabled = name.isNotBlank() && baseUrl.isNotBlank() && !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val encrypted = SecretStore.encrypt(apiKey.trim())
                                val now = System.currentTimeMillis()
                                val id = if (providerId == 0L) {
                                    app.container.providerDao.insert(
                                        ProviderEntity(
                                            name = name.trim(),
                                            baseUrl = baseUrl.trim(),
                                            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                                            keyRef = encrypted,
                                            createdAt = now,
                                        ),
                                    )
                                } else {
                                    val existing = app.container.providerDao.findById(providerId)
                                    if (existing != null) {
                                        app.container.providerDao.update(
                                            existing.copy(
                                                name = name.trim(),
                                                baseUrl = baseUrl.trim(),
                                                protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                                                keyRef = encrypted,
                                            ),
                                        )
                                    }
                                    providerId
                                }
                                if (fetched.isNotEmpty()) {
                                    app.container.providerDao.clearModels(id)
                                    app.container.providerDao.insertModels(
                                        fetched.mapIndexed { index, model ->
                                            ModelEntity(
                                                providerId = id,
                                                modelName = model.id,
                                                displayName = model.displayName.ifBlank { model.id },
                                                isDefault = index == 0,
                                            )
                                        },
                                    )
                                }
                                busy = false
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("服务商名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API 地址") },
                placeholder = { Text("https://api.deepseek.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row {
                OutlinedButton(
                    enabled = !busy && apiKey.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true
                            status = null
                            val ok = runCatching {
                                ModelAdapterRegistry.adapterFor(config().protocol).testConnection(config())
                            }.getOrDefault(false)
                            status = if (ok) "连接成功" else "连接失败，请检查地址与 Key"
                            busy = false
                        }
                    },
                ) { Text("测试连接") }
                Spacer(Modifier.height(0.dp))
                TextButton(
                    enabled = !busy && apiKey.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true
                            status = null
                            try {
                                fetched = ModelAdapterRegistry.adapterFor(config().protocol).listModels(config())
                                status = "已获取 ${fetched.size} 个模型"
                            } catch (e: ModelException) {
                                status = e.message
                            } catch (e: Exception) {
                                status = "获取模型失败"
                            }
                            busy = false
                        }
                    },
                ) { Text("获取模型列表") }
            }
            if (status != null) {
                Text(
                    text = status.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (fetched.isNotEmpty()) {
                Text(
                    text = fetched.joinToString("\n") { "· ${it.displayName}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "API Key 使用 Android Keystore 加密后保存，不会明文写入数据库、日志或普通备份。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除服务商") },
            text = { Text("删除「${name.trim()}」会同时删除它下面的模型列表。使用该服务商模型的智能体会提示未配置模型。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val existing = app.container.providerDao.findById(providerId)
                        if (existing != null) {
                            app.container.providerDao.delete(existing)
                        }
                        showDeleteConfirm = false
                        onBack()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}
