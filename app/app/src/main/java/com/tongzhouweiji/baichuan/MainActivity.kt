package com.tongzhouweiji.baichuan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.tongzhouweiji.baichuan.ui.BaichuanApp
import com.tongzhouweiji.baichuan.ui.theme.BaichuanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as BaichuanApplication
        val databaseFailure = runCatching { app.container.database }.exceptionOrNull()
        setContent {
            val themeMode by app.container.settingDao
                .observe("theme_mode")
                .collectAsState(initial = "system")
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            BaichuanTheme(darkTheme = darkTheme) {
                if (databaseFailure != null) {
                    com.tongzhouweiji.baichuan.ui.screen.recovery.DatabaseRecoveryScreen(
                        error = databaseFailure.message,
                        onRetry = { recreate() },
                    )
                } else {
                    NotificationPermissionRequester()
                    BaichuanApp()
                }
            }
        }
    }
}

/** Android 13+ 通知权限。拒绝时主动消息仍生成，但不弹通知。 */
@Composable
private fun NotificationPermissionRequester() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
