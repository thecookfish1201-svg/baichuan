package com.tongzhouweiji.baichuan.ui.screen.recovery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 数据库无法打开时的恢复界面：不删除数据，提供重试与说明。 */
@Composable
fun DatabaseRecoveryScreen(error: String?, onRetry: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
        ) {
            Text("数据库暂时无法打开", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "百川没有删除你的本地数据。数据库文件与原始内容保持原样，请先不要卸载应用。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text("你可以：", style = MaterialTheme.typography.bodyMedium)
            Text("1. 点击下方重试；", style = MaterialTheme.typography.bodySmall)
            Text("2. 保留应用数据目录中的数据库文件，反馈问题时一并提供（不要上传含隐私的完整备份）；", style = MaterialTheme.typography.bodySmall)
            Text("3. 如果之前导出过同舟备份，可在问题解决后从备份恢复。", style = MaterialTheme.typography.bodySmall)
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "错误信息：$error",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("重试打开数据库")
            }
        }
    }
}
