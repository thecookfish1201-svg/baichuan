package com.tongzhouweiji.baichuan.ui.screen.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.tongzhouweiji.baichuan.core.backup.BackupManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BaichuanApplication
    val scope = rememberCoroutineScope()
    val manager = remember { BackupManager(context, app.container.database) }

    var categories by remember { mutableStateOf(BackupManager.CATEGORIES.toSet()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = null
                status = runCatching { manager.exportToZip(uri, categories) }.fold(
                    onSuccess = { info ->
                        "导出成功：${info.fileCount} 个文件，${info.totalBytes / 1024} KB"
                    },
                    onFailure = { "导出失败：${it.message}" },
                )
                busy = false
            }
        }
    }

    val folderExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = null
                status = runCatching { manager.exportToFolder(uri, categories) }.fold(
                    onSuccess = { info -> "已导出到文件夹：${info.fileCount} 个文件" },
                    onFailure = { "导出失败：${it.message}" },
                )
                busy = false
            }
        }
    }

    val folderImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = null
                status = runCatching { manager.importFromFolder(uri) }.fold(
                    onSuccess = { info -> "导入成功：已恢复 ${info.fileCount} 个文件" },
                    onFailure = { "导入失败：${it.message}（已尝试回滚）" },
                )
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = null
                status = runCatching { manager.importFromZip(uri) }.fold(
                    onSuccess = { info -> "导入成功：已恢复 ${info.fileCount} 个文件" },
                    onFailure = { "导入失败：${it.message}（已尝试回滚）" },
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
                title = { Text("同舟备份") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("导出内容", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            BackupManager.CATEGORIES.forEach { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            categories = if (category in categories) {
                                categories - category
                            } else {
                                categories + category
                            }
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = category in categories,
                        onCheckedChange = { checked ->
                            categories = if (checked) categories + category else categories - category
                        },
                    )
                    Text(category.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                enabled = !busy && categories.isNotEmpty(),
                onClick = { exportLauncher.launch("baichuan-backup.zip") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导出备份（单个 ZIP 包）") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                enabled = !busy && categories.isNotEmpty(),
                onClick = { folderExportLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导出备份（文件夹）") }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                enabled = !busy,
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("从 ZIP 导入") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                enabled = !busy,
                onClick = { folderImportLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("从文件夹导入") }
            if (busy) {
                Spacer(Modifier.height(12.dp))
                Text("处理中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (status != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = status.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "备份包含：聊天记录、智能体与模型配置、记忆、设置、附件。API Key 不进备份（仅保存加密引用），恢复后需要重新填写。导入前会自动保存一份当前数据的临时备份，导入失败会尝试回滚。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
