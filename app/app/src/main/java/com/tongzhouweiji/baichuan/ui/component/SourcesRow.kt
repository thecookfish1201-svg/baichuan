package com.tongzhouweiji.baichuan.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 搜索来源活动条：默认折叠成一行「来源 N 条」，点击展开标题列表。
 * 与最终回答保持视觉主次：折叠态低对比，展开态可点击打开链接。
 */
@Composable
fun SourcesRow(
    sourcesJson: String?,
    onOpenUrl: (String) -> Unit = {},
) {
    if (sourcesJson.isNullOrBlank()) return
    val sources = remember(sourcesJson) { parseSources(sourcesJson) }
    if (sources.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "来源 ${sources.size} 条",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起来源" else "展开来源",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        if (expanded) {
            sources.forEach { source ->
                Text(
                    text = source.first,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenUrl(source.second) }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

private fun parseSources(json: String): List<Pair<String, String>> = runCatching {
    val array = Json.parseToJsonElement(json).jsonArray
    array.mapNotNull { element ->
        val obj = element.jsonObject
        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        title to url
    }
}.getOrDefault(emptyList())
