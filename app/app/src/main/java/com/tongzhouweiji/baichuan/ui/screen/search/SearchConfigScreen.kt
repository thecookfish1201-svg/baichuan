package com.tongzhouweiji.baichuan.ui.screen.search

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tongzhouweiji.baichuan.BaichuanApplication
import com.tongzhouweiji.baichuan.core.db.entity.ProviderEntity
import com.tongzhouweiji.baichuan.core.db.entity.SettingEntity
import com.tongzhouweiji.baichuan.core.search.SearchManager
import com.tongzhouweiji.baichuan.core.security.SecretStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BaichuanApplication
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf("deepseek") }
    var endpoint by remember { mutableStateOf("https://api.tavily.com/search") }
    var apiKey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selectedProviderId by remember { mutableStateOf<Long?>(null) }
    var providerMenu by remember { mutableStateOf(false) }

    val providers by app.container.providerDao.observeAll().collectAsState(initial = emptyList())
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }

    LaunchedEffect(Unit) {
        mode = app.container.settingDao.get("search_mode") ?: "deepseek"
        endpoint = app.container.settingDao.get("search_base_url") ?: endpoint
        apiKey = SecretStore.decrypt(app.container.settingDao.get("search_key_ref").orEmpty())
        selectedProviderId = app.container.settingDao.get("search_provider_id")?.toLongOrNull()
    }

    fun configFor(provider: ProviderEntity?): SearchManager.SearchConfig? {
        if (mode == "deepseek") {
            val key = SecretStore.decrypt(provider?.keyRef.orEmpty())
            if (provider == null || key.isBlank()) return null
            return SearchManager.SearchConfig(
                mode = SearchManager.Mode.DEEPSEEK,
                endpoint = SearchManager.DEEPSEEK_SEARCH_ENDPOINT,
                apiKey = key,
            )
        }
        if (apiKey.isBlank()) return null
        return SearchManager.SearchConfig(SearchManager.Mode.TAVILY, endpoint, apiKey)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("网络搜索") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                app.container.settingDao.put(SettingEntity("search_mode", mode))
                                selectedProviderId?.let {
                                    app.container.settingDao.put(SettingEntity("search_provider_id", it.toString()))
                                }
                                if (mode == "tavily") {
                                    app.container.settingDao.put(SettingEntity("search_base_url", endpoint.trim()))
                                    app.container.settingDao.put(
                                        SettingEntity("search_key_ref", SecretStore.encrypt(apiKey.trim())),
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
            Text("搜索服务", style = MaterialTheme.typography.bodyMedium)
            Row {
                OutlinedButton(onClick = { mode = "deepseek" }) {
                    Text(if (mode == "deepseek") "· DeepSeek 官方" else "DeepSeek 官方")
                }
                Spacer(Modifier.padding(horizontal = 4.dp))
                OutlinedButton(onClick = { mode = "tavily" }) {
                    Text(if (mode == "tavily") "· 自定义服务" else "自定义服务")
                }
            }

            if (mode == "deepseek") {
                Spacer(Modifier.height(12.dp))
                Text("使用哪个服务商的 Key", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedProvider?.let { "${it.name}（${it.baseUrl}）" } ?: "选择服务商")
                }
                DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                    providers.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text("${provider.name} · ${provider.baseUrl}") },
                            onClick = {
                                selectedProviderId = provider.id
                                providerMenu = false
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (selectedProvider == null) {
                        "尚未选择服务商。请先在上面选一个真实的 DeepSeek 服务商（不要选用来做失败测试的那个）。"
                    } else {
                        "将使用「${selectedProvider.name}」的 Key 调用 DeepSeek 官方 Search，按成功请求计费。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("搜索接口地址") },
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
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "兼容 Tavily 风格：POST 请求，body 含 api_key、query、max_results，返回 results 数组。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        status = null
                        val config = configFor(selectedProvider)
                        if (config == null) {
                            status = if (mode == "deepseek") "请先选择服务商" else "请填写接口地址与 Key"
                            busy = false
                            return@launch
                        }
                        val result = runCatching { SearchManager.search(config, "DeepSeek API 搜索") }
                        status = result.fold(
                            onSuccess = { "连接成功，返回 ${it.size} 条结果" },
                            onFailure = { it.message ?: "连接失败" },
                        )
                        busy = false
                    }
                },
            ) { Text("测试搜索") }
            if (status != null) {
                Text(
                    text = status.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
