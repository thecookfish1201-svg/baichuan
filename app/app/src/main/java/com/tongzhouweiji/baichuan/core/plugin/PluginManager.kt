package com.tongzhouweiji.baichuan.core.plugin

import android.content.Context
import android.net.Uri
import com.tongzhouweiji.baichuan.core.db.BaichuanDatabase
import com.tongzhouweiji.baichuan.core.db.entity.PluginEntity
import com.tongzhouweiji.baichuan.core.db.entity.PluginPermissionEntity
import com.tongzhouweiji.baichuan.core.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 未济接口运行时（第一版）：配置型插件的安装、启用、停用、卸载与主题加载。
 * 代码型插件不执行，只登记状态并提示需要评估安全机制。
 */
class PluginManager(
    private val context: Context,
    private val db: BaichuanDatabase,
) {

    companion object {
        const val API_MAJOR = 1
        private const val MANIFEST = "manifest.json"
        private const val THEME = "theme.json"
        private const val PLUGIN_ROOT = "plugins"
    }

    @Serializable
    data class Manifest(
        val id: String,
        val name: String,
        val author: String = "",
        val version: String = "1.0.0",
        val apiVersion: String = "1.0",
        val type: String = "config",
        val dependencies: List<String> = emptyList(),
        val permissions: List<String> = emptyList(),
    )

    @Serializable
    data class ThemeConfig(
        val primary: String? = null,
        val chatBubble: String? = null,
    )

    /** 配置型插件的角色卡/预设。 */
    @Serializable
    data class AgentPreset(
        val name: String,
        val persona: String = "",
        val systemPrompt: String = "",
    )

    data class PresetWithOwner(
        val pluginId: String,
        val pluginName: String,
        val preset: AgentPreset,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun pluginDir(pluginId: String): File = File(context.filesDir, "$PLUGIN_ROOT/$pluginId")

    /** 从 ZIP 安装。返回安装后的插件记录。 */
    suspend fun install(uri: Uri): PluginEntity = withContext(Dispatchers.IO) {
        val entries = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalArgumentException("无法读取插件包")

        val manifestBytes = entries[MANIFEST] ?: throw IllegalArgumentException("插件缺少 manifest.json")
        val manifest = json.decodeFromString(Manifest.serializer(), String(manifestBytes))
        if (manifest.id.isBlank()) throw IllegalArgumentException("插件 id 不能为空")
        if (manifest.type !in setOf("config", "code")) throw IllegalArgumentException("未知插件类型")
        val major = manifest.apiVersion.substringBefore('.').toIntOrNull() ?: 0
        if (major > API_MAJOR) {
            throw IllegalArgumentException("插件需要未济接口 ${manifest.apiVersion}，当前 $API_MAJOR.0，不兼容")
        }

        val dir = pluginDir(manifest.id)
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
        entries.forEach { (path, bytes) ->
            if (path == MANIFEST) return@forEach
            val target = File(dir, path)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }

        val existing = db.pluginDao().findByPluginId(manifest.id)
        val now = System.currentTimeMillis()
        val entity = PluginEntity(
            id = existing?.id ?: 0,
            pluginId = manifest.id,
            name = manifest.name,
            author = manifest.author,
            version = manifest.version,
            apiVersion = manifest.apiVersion,
            type = manifest.type,
            permissionsJson = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
                manifest.permissions,
            ),
            state = "installed",
            installPath = "$PLUGIN_ROOT/${manifest.id}",
            previousVersion = existing?.version,
            installedAt = existing?.installedAt ?: now,
            updatedAt = now,
        )
        if (existing != null) {
            db.pluginDao().update(entity)
            db.pluginPermissionDao().deleteByPlugin(manifest.id)
        } else {
            db.pluginDao().insert(entity)
        }
        if (manifest.permissions.isNotEmpty()) {
            db.pluginPermissionDao().insertAll(
                manifest.permissions.map { permission ->
                    PluginPermissionEntity(pluginId = manifest.id, permission = permission)
                },
            )
        }
        entity
    }

    /** 安装内置测试插件（来自 assets）。 */
    suspend fun installBundled(assetName: String): PluginEntity = withContext(Dispatchers.IO) {
        val bytes = context.assets.open(assetName).use { it.readBytes() }
        val temp = File(context.cacheDir, "bundled-plugin.zip")
        temp.writeBytes(bytes)
        install(Uri.fromFile(temp))
    }

    suspend fun setEnabled(pluginId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val plugin = db.pluginDao().findByPluginId(pluginId) ?: return@withContext
        if (plugin.type == "code" && enabled) {
            throw IllegalStateException("代码型插件需要先完成安全评估，当前版本不执行")
        }
        db.pluginDao().updateState(pluginId, if (enabled) "enabled" else "disabled", System.currentTimeMillis())
        if (plugin.type == "config") {
            if (enabled) applyTheme(plugin) else clearTheme(pluginId)
        }
    }

    suspend fun uninstall(pluginId: String, deleteData: Boolean) = withContext(Dispatchers.IO) {
        val plugin = db.pluginDao().findByPluginId(pluginId) ?: return@withContext
        clearTheme(pluginId)
        if (deleteData) pluginDir(pluginId).deleteRecursively()
        db.pluginPermissionDao().deleteByPlugin(pluginId)
        db.pluginDao().delete(plugin)
    }

    /** 扫描已启用插件中的 presets.json，返回可用预设。 */
    suspend fun listPresets(): List<PresetWithOwner> = withContext(Dispatchers.IO) {
        val result = mutableListOf<PresetWithOwner>()
        db.pluginDao().listEnabled().forEach { plugin ->
            updateStateIfNeeded(plugin)
            val file = File(pluginDir(plugin.pluginId), "presets.json")
            if (!file.exists()) return@forEach
            runCatching {
                val presets = json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(AgentPreset.serializer()),
                    file.readText(),
                )
                presets.forEach { preset ->
                    result.add(PresetWithOwner(plugin.pluginId, plugin.name, preset))
                }
            }.onFailure { Logger.w("预设解析失败：${plugin.pluginId}", it) }
        }
        result
    }

    private fun updateStateIfNeeded(plugin: PluginEntity) {
        // 占位：后续可在此做插件健康检查
    }

    fun loadTheme(pluginId: String): ThemeConfig? {
        val file = File(pluginDir(pluginId), THEME)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(ThemeConfig.serializer(), file.readText())
        }.onFailure { Logger.w("主题解析失败", it) }.getOrNull()
    }

    private suspend fun applyTheme(plugin: PluginEntity) {
        val theme = loadTheme(plugin.pluginId) ?: return
        val primary = theme.primary ?: return
        db.settingDao().put(
            com.tongzhouweiji.baichuan.core.db.entity.SettingEntity("plugin_theme_primary", primary),
        )
        db.settingDao().put(
            com.tongzhouweiji.baichuan.core.db.entity.SettingEntity("plugin_theme_owner", plugin.pluginId),
        )
    }

    private suspend fun clearTheme(pluginId: String) {
        val owner = db.settingDao().get("plugin_theme_owner")
        if (owner == pluginId) {
            db.settingDao().put(
                com.tongzhouweiji.baichuan.core.db.entity.SettingEntity("plugin_theme_primary", ""),
            )
            db.settingDao().put(
                com.tongzhouweiji.baichuan.core.db.entity.SettingEntity("plugin_theme_owner", ""),
            )
        }
    }
}
