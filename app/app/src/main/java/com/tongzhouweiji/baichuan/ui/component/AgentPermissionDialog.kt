package com.tongzhouweiji.baichuan.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

/**
 * Agent 授权须知弹窗：居中模态、背景遮罩、完整免责说明、10 秒阅读门槛。
 * 取消不授权；确认后按所选等级授权。
 */
@Composable
fun AgentPermissionDialog(
    agentName: String,
    disclaimer: String,
    onDismiss: () -> Unit,
    onConfirm: (level: String) -> Unit,
) {
    var countdown by remember { mutableStateOf(10) }
    var level by remember { mutableStateOf("A") }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = "注意事项与免责说明",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "为「$agentName」授予 Agent 权限前，请阅读以下说明。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = disclaimer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("权限等级", style = MaterialTheme.typography.bodyMedium)
                Row {
                    OutlinedButton(onClick = { level = "A" }) {
                        Text(if (level == "A") "· 仅操控 APP" else "仅操控 APP")
                    }
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    OutlinedButton(onClick = { level = "B" }) {
                        Text(if (level == "B") "· 授权系统操作" else "授权系统操作")
                    }
                }
                Text(
                    text = "等级 B 仍需遵守 Android 系统授权，不是无限制访问。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                if (countdown > 0) {
                    Text(
                        text = "请阅读以上说明，$countdown 秒后可确认",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Spacer(Modifier.height(2.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = countdown == 0,
                        onClick = { onConfirm(level) },
                    ) { Text("确认授权") }
                }
            }
        }
    }
}
