package com.tongzhouweiji.baichuan.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tongzhouweiji.baichuan.R
import com.tongzhouweiji.baichuan.ui.component.ListRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenAppearance: () -> Unit = {},
    onOpenErrors: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenAux: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenAgentRuns: () -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.tab_profile)) },
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
                .verticalScroll(rememberScrollState()),
        ) {
            ProfileHeader()
            Spacer(Modifier.height(10.dp))
            ListRow(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.profile_settings),
                subtitle = "模型、存储与通用设置",
            )
            ListRow(
                icon = Icons.Default.Star,
                title = stringResource(R.string.profile_appearance),
                subtitle = "微信式 / DeepSeek 式与深色模式",
                onClick = onOpenAppearance,
            )
            ListRow(
                icon = Icons.Default.Search,
                title = "网络搜索",
                subtitle = "Tavily 风格搜索服务配置",
                onClick = onOpenSearch,
            )
            ListRow(
                icon = Icons.Default.Settings,
                title = "辅助模型",
                subtitle = "上下文压缩使用独立模型",
                onClick = onOpenAux,
            )
            ListRow(
                icon = Icons.Default.Build,
                title = "Agent 运行记录",
                subtitle = "授权智能体的任务与工具调用轨迹",
                onClick = onOpenAgentRuns,
            )
            ListRow(
                icon = Icons.Default.Warning,
                title = "错误记录",
                subtitle = "系统事件与错误详情",
                onClick = onOpenErrors,
            )
            ListRow(
                icon = Icons.Default.Share,
                title = stringResource(R.string.profile_backup),
                subtitle = "导出与导入本地数据",
                onClick = onOpenBackup,
            )
            ListRow(
                icon = Icons.Default.Info,
                title = stringResource(R.string.profile_about),
                subtitle = "开源协议与版本信息",
                showDivider = false,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.profile_version),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProfileHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(
                text = "百川用户",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "本地数据 · 未配置模型",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
