package com.tongzhouweiji.baichuan.ui.screen.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tongzhouweiji.baichuan.R
import com.tongzhouweiji.baichuan.ui.component.ListRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(
    onOpenAgents: () -> Unit = {},
    onOpenModelConfig: () -> Unit = {},
    onOpenPlugins: () -> Unit = {},
    onOpenMcp: () -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.tab_manage)) },
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
                .padding(top = 10.dp),
        ) {
            ListRow(
                icon = Icons.Default.Person,
                title = stringResource(R.string.manage_agents),
                subtitle = "创建、编辑与权限配置",
                onClick = onOpenAgents,
            )
            ListRow(
                icon = Icons.Default.Warning,
                title = stringResource(R.string.manage_blocklist),
                subtitle = "已拉黑的智能体",
                showDivider = false,
            )
            GroupGap()
            ListRow(
                icon = Icons.Default.Build,
                title = stringResource(R.string.manage_plugins),
                subtitle = "未济接口插件管理",
                onClick = onOpenPlugins,
            )
            ListRow(
                icon = Icons.Default.AccountCircle,
                title = stringResource(R.string.manage_mcp),
                subtitle = "外部工具与服务连接",
                onClick = onOpenMcp,
            )
            ListRow(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.manage_permissions),
                subtitle = "插件与 Agent 的授权记录",
                showDivider = false,
            )
            GroupGap()
            ListRow(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.manage_settings),
                subtitle = "模型、存储与通用设置",
                showDivider = false,
                onClick = onOpenModelConfig,
            )
        }
    }
}

@Composable
private fun GroupGap() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp),
    )
}
