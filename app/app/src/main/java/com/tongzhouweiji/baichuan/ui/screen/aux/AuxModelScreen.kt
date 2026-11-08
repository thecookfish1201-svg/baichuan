package com.tongzhouweiji.baichuan.ui.screen.aux

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tongzhouweiji.baichuan.BaichuanApplication
import com.tongzhouweiji.baichuan.core.db.entity.AuxBindingEntity
import kotlinx.coroutines.launch

private val capabilities = listOf(
    "compression" to "上下文压缩",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuxModelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BaichuanApplication
    val scope = rememberCoroutineScope()
    val bindings by app.container.auxBindingDao.observeAll().collectAsState(initial = emptyList())
    val models by app.container.providerDao.observeAllModels().collectAsState(initial = emptyList())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("辅助模型") },
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
                .padding(16.dp),
        ) {
            capabilities.forEach { (capability, label) ->
                val current = bindings.firstOrNull { it.capability == capability }
                val currentModel = models.firstOrNull { it.id == current?.modelId }
                CapabilityRow(
                    label = label,
                    currentModel = currentModel?.displayName ?: "使用主模型（默认）",
                    models = models.map { it.id to it.displayName },
                    onSelect = { modelId ->
                        scope.launch {
                            app.container.auxBindingDao.clear(capability)
                            if (modelId != null) {
                                app.container.auxBindingDao.insert(
                                    AuxBindingEntity(
                                        capability = capability,
                                        modelId = modelId,
                                        providerId = models.firstOrNull { it.id == modelId }?.providerId,
                                    ),
                                )
                            }
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "上下文压缩默认使用主模型；绑定辅助模型后，长对话压缩会使用该模型，节省主模型上下文与费用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CapabilityRow(
    label: String,
    currentModel: String,
    models: List<Pair<Long, String>>,
    onSelect: (Long?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                currentModel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { menu = true }) { Text("选择") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("使用主模型") },
                onClick = {
                    menu = false
                    onSelect(null)
                },
            )
            models.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        menu = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}
