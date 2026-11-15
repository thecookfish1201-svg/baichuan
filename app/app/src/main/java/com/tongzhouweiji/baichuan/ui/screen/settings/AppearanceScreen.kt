package com.tongzhouweiji.baichuan.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tongzhouweiji.baichuan.BaichuanApplication
import com.tongzhouweiji.baichuan.core.db.entity.SettingEntity
import com.tongzhouweiji.baichuan.core.tts.TtsAvailability
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BaichuanApplication
    val scope = rememberCoroutineScope()
    val themeMode by app.container.settingDao.observe("theme_mode").collectAsState(initial = "system")
    val ttsProvider by app.container.settingDao.observe("tts_provider").collectAsState(initial = "")
    val ttsVoice by app.container.settingDao.observe("tts_voice").collectAsState(initial = "stella")
    val ttsAvailability by produceState(initialValue = TtsAvailability.Disabled, ttsProvider) {
        value = app.container.ttsService.availability()
    }
    val ttsStatusText = when (ttsAvailability) {
        TtsAvailability.Disabled -> "未开启，朗读使用系统 TTS"
        TtsAvailability.NoProvider -> "未添加 DeepSeek 服务商，朗读将回退系统 TTS"
        TtsAvailability.NoKey -> "DeepSeek API Key 未配置，朗读将回退系统 TTS"
        TtsAvailability.Ready -> "已就绪：优先使用 DeepSeek 云端音色"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("外观") },
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
            Spacer(Modifier.height(8.dp))
            Text(
                text = "主题",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            OptionRow(
                title = "跟随系统",
                subtitle = "浅色或深色随系统设置切换",
                selected = themeMode == "system",
            ) {
                scope.launch { app.container.settingDao.put(SettingEntity("theme_mode", "system")) }
            }
            OptionRow(
                title = "浅色",
                subtitle = "始终使用浅色主题",
                selected = themeMode == "light",
            ) {
                scope.launch { app.container.settingDao.put(SettingEntity("theme_mode", "light")) }
            }
            OptionRow(
                title = "深色",
                subtitle = "始终使用深色主题",
                selected = themeMode == "dark",
            ) {
                scope.launch { app.container.settingDao.put(SettingEntity("theme_mode", "dark")) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "朗读",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            OptionRow(
                title = "云端朗读（DeepSeek TTS）",
                subtitle = ttsStatusText,
                selected = ttsProvider == "deepseek",
            ) {
                scope.launch {
                    app.container.settingDao.put(
                        SettingEntity("tts_provider", if (ttsProvider == "deepseek") "" else "deepseek"),
                    )
                }
            }
            OptionRow(
                title = "音色：海星（女声）",
                subtitle = "stella，默认音色",
                selected = ttsVoice != "tide",
            ) {
                scope.launch {
                    app.container.settingDao.put(SettingEntity("tts_voice", "stella"))
                }
            }
            OptionRow(
                title = "音色：暗潮（男声）",
                subtitle = "tide",
                selected = ttsVoice == "tide",
            ) {
                scope.launch {
                    app.container.settingDao.put(SettingEntity("tts_voice", "tide"))
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
