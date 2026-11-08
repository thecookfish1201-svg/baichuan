package com.tongzhouweiji.baichuan.ui.screen.agent

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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tongzhouweiji.baichuan.core.db.entity.AgentEntity
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val reasoningOptions = com.tongzhouweiji.baichuan.model.ReasoningEffort.entries
    .map { it to it.label }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentEditScreen(
    agentId: Long,
    onBack: () -> Unit,
    onOpenMemory: (Long) -> Unit = {},
    onOpenScheduled: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as BaichuanApplication
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf<Long?>(null) }
    var temperature by remember { mutableStateOf(0.7f) }
    var stream by remember { mutableStateOf(true) }
    var reasoning by remember { mutableStateOf<com.tongzhouweiji.baichuan.model.ReasoningEffort>(com.tongzhouweiji.baichuan.model.ReasoningEffort.AUTO) }
    var loaded by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var reasoningMenu by remember { mutableStateOf(false) }
    var presetMenu by remember { mutableStateOf(false) }
    var presets by remember { mutableStateOf<List<com.tongzhouweiji.baichuan.core.plugin.PluginManager.PresetWithOwner>>(emptyList()) }
    var authorizedLevel by remember { mutableStateOf<String?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showRevokeConfirm by remember { mutableStateOf(false) }
    var disclaimer by remember { mutableStateOf("") }

    val models by app.container.providerDao.observeAllModels().collectAsState(initial = emptyList())
    val json = remember { Json { ignoreUnknownKeys = true } }

    LaunchedEffect(agentId) {
        if (agentId != 0L) {
            val agent = app.container.agentDao.findById(agentId)
            if (agent != null) {
                name = agent.name
                persona = agent.persona
                systemPrompt = agent.systemPrompt
                modelId = agent.modelConfigId
                runCatching {
                    val obj = json.parseToJsonElement(agent.generationJson).jsonObject
                    temperature = obj["temperature"]?.jsonPrimitive?.floatOrNull ?: 0.7f
                    stream = obj["stream"]?.jsonPrimitive?.booleanOrNull ?: true
                    reasoning = com.tongzhouweiji.baichuan.model.ReasoningEffort.fromKey(obj["reasoning"]?.jsonPrimitive?.contentOrNull)
                }
            }
        }
        loaded = true
    }

    LaunchedEffect(Unit) {
        disclaimer = runCatching {
            context.assets.open("agent_disclaimer.txt").bufferedReader().readText()
        }.getOrDefault("免责说明加载失败")
    }

    LaunchedEffect(agentId) {
        if (agentId != 0L) {
            authorizedLevel = app.container.agentManager.levelOf(agentId)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (agentId == 0L) "新建智能体" else "编辑智能体") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        enabled = loaded && name.isNotBlank(),
                        onClick = {
                            scope.launch {
                                val now = System.currentTimeMillis()
                                val generation = buildJsonObject {
                                    put("temperature", temperature)
                                    put("stream", stream)
                                    put("reasoning", reasoning.key)
                                }.toString()
                                if (agentId == 0L) {
                                    app.container.agentDao.insert(
                                        AgentEntity(
                                            name = name.trim(),
                                            persona = persona.trim(),
                                            systemPrompt = systemPrompt.trim(),
                                            modelConfigId = modelId,
                                            generationJson = generation,
                                            createdAt = now,
                                            updatedAt = now,
                                        ),
                                    )
                                } else {
                                    val existing = app.container.agentDao.findById(agentId) ?: return@launch
                                    app.container.agentDao.update(
                                        existing.copy(
                                            name = name.trim(),
                                            persona = persona.trim(),
                                            systemPrompt = systemPrompt.trim(),
                                            modelConfigId = modelId,
                                            generationJson = generation,
                                            updatedAt = now,
                                        ),
                                    )
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            presets = app.container.pluginManager.listPresets()
                            presetMenu = true
                        }
                    },
                ) { Text("从插件预设填充") }
                DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                    if (presets.isEmpty()) {
                        DropdownMenuItem(text = { Text("没有可用预设（插件需启用且包含 presets.json）") }, onClick = { presetMenu = false })
                    }
                    presets.forEach { item ->
                        DropdownMenuItem(
                            text = { Text("${item.pluginName} · ${item.preset.name}") },
                            onClick = {
                                name = item.preset.name
                                persona = item.preset.persona
                                systemPrompt = item.preset.systemPrompt
                                presetMenu = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = persona,
                onValueChange = { persona = it },
                label = { Text("人设") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("系统提示词") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { modelMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(models.firstOrNull { it.id == modelId }?.displayName ?: "使用默认模型（点击选择）")
            }
            DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                DropdownMenuItem(text = { Text("使用默认模型") }, onClick = {
                    modelId = null
                    modelMenu = false
                })
                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName) },
                        onClick = {
                            modelId = model.id
                            modelMenu = false
                        },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "温度 ${"%.1f".format(temperature)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                valueRange = 0f..2f,
                steps = 19,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "流式输出",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = stream, onCheckedChange = { stream = it })
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "推理深度",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { reasoningMenu = true }) {
                    Text(reasoning.label)
                }
                DropdownMenu(expanded = reasoningMenu, onDismissRequest = { reasoningMenu = false }) {
                    reasoningOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.second) },
                            onClick = {
                                reasoning = option.first
                                reasoningMenu = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            if (agentId != 0L) {
                OutlinedButton(onClick = { onOpenMemory(agentId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("记忆管理")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { onOpenScheduled(agentId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("主动消息")
                }
                Spacer(Modifier.height(12.dp))
                Text("Agent 权限", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (authorizedLevel == null) "未授权：该智能体不能调用 Agent 能力" else "已授权（等级 $authorizedLevel）",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (authorizedLevel == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                if (authorizedLevel == null) {
                    OutlinedButton(
                        onClick = { showPermissionDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("授予 Agent 权限") }
                } else {
                    OutlinedButton(
                        onClick = { showRevokeConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("撤销 Agent 权限") }
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = "温度在思考模式开启时不生效（官方限制）：关闭深度思考后温度才可用。推理强度按官方取值 low / high / max 映射，跟随默认由服务端决定。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showPermissionDialog) {
        com.tongzhouweiji.baichuan.ui.component.AgentPermissionDialog(
            agentName = name.ifBlank { "该智能体" },
            disclaimer = disclaimer,
            onDismiss = { showPermissionDialog = false },
            onConfirm = { level ->
                scope.launch {
                    app.container.agentManager.authorize(agentId, level)
                    authorizedLevel = level
                    showPermissionDialog = false
                }
            },
        )
    }

    if (showRevokeConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRevokeConfirm = false },
            title = { Text("撤销 Agent 权限") },
            text = { Text("撤销后「${name}」将无法调用 Agent 能力，已产生的运行记录保留。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.container.agentManager.revoke(agentId)
                        authorizedLevel = null
                        showRevokeConfirm = false
                    }
                }) { Text("撤销", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showRevokeConfirm = false }) { Text("取消") } },
        )
    }
}
