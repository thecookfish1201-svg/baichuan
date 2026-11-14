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
        private const val STAGING_ROOT = "plugin-staging"

        private const val MAX_ARCHIVE_ENTRIES = 100
        private const val MAX_TOTAL_BYTES = 8L * 1024 * 1024
        private const val MAX_ENTRY_BYTES = 4L * 1024 * 1024
        private const val MAX_PATH_LENGTH = 200
        private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
        private val VERSION_PATTERN = Regex("^[0-9]+(\\.[0-9]+){0,2}([-+][0-9A-Za-z.]+)?$")
        private val PERMISSION_PATTERN = Regex("^[a-z][a-z0-9_.-]{0,63}$")
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

    /** 插件 ID 白名单：小写字母/数字/点/下划线/连字符，不能含路径片段。 */
    fun validateId(id: String) {
        require(id.isNotEmpty() && id.length <= 64) { "插件 id 长度不合法" }
        require(ID_PATTERN.matches(id)) {
            "插件 id 只能包含小写字母、数字、点、下划线、连字符，且以字母或数字开头"
        }
        require(!id.contains("..")) { "插件 id 不能包含连续的路径分隔符" }
    }

    /** 解包条目路径校验：拒绝绝对路径、穿越、反斜杠、控制字符与空段。 */
    fun validateEntryPath(path: String) {
        require(path.isNotEmpty() && path.length <= MAX_PATH_LENGTH) { "插件包路径不合法" }
        require(!path.startsWith("/") && !path.startsWith("\\")) { "插件包不能包含绝对路径" }
        require(!path.contains('\\')) { "插件包路径不能包含反斜杠" }
        require(!path.contains('\u0000')) { "插件包路径包含非法字符" }
        require(!path.contains("..")) { "插件包路径不能包含 .." }
        require(path.none { it.isISOControl() }) { "插件包路径包含控制字符" }
        val segments = path.split('/')
        require(segments.none { it.isEmpty() || it == "." || it == ".." }) { "插件包路径包含空段或相对段" }
    }

    /** 从 ZIP 安装：限制大小与条目、校验路径、staging 安装、失败回滚。 */
    suspend fun install(uri: Uri): PluginEntity = withContext(Dispatchers.IO) {
        val entries = readZipLimited(uri)
        val manifestBytes = entries[MANIFEST] ?: throw IllegalArgumentException("插件缺少 manifest.json")
        val manifest = parseManifest(manifestBytes)
        validateManifest(manifest)

        val staging = File(context.cacheDir, "$STAGING_ROOT/${System.nanoTime()}")
        val target = pluginDir(manifest.id)
        val backup = File(target.parentFile, "${manifest.id}.old-${System.nanoTime()}")
        var committed = false
        try {
            staging.mkdirs()
            val base = staging.canonicalFile
            entries.forEach { (path, bytes) ->
                if (path == MANIFEST) return@forEach
                val file = File(staging, path)
                val canonical = file.canonicalFile
                require(canonical == base || canonical.path.startsWith(base.path + File.separator)) {
                    "插件包路径越界：$path"
                }
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
            }
            if (target.exists() && !target.renameTo(backup)) {
                throw IllegalStateException("无法备份旧插件目录")
            }
            if (!staging.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                throw IllegalStateException("无法写入插件目录")
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
            committed = true
            backup.deleteRecursively()
            entity
        } catch (e: Exception) {
            if (!committed) {
                runCatching { if (target.exists()) target.deleteRecursively() }
                runCatching { if (backup.exists()) backup.renameTo(target) }
            }
            throw e
        } finally {
            runCatching { staging.deleteRecursively() }
        }
    }

    /** 流式读取 ZIP 并施加条目数、单文件与总大小限制。 */
    private fun readZipLimited(uri: Uri): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        var total = 0L
        var count = 0
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("无法读取插件包")
        input.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    count += 1
                    require(count <= MAX_ARCHIVE_ENTRIES) { "插件包条目过多（上限 $MAX_ARCHIVE_ENTRIES）" }
                    if (!entry.isDirectory) {
                        validateEntryPath(entry.name)
                        require(!entries.containsKey(entry.name)) { "插件包存在重复路径：${entry.name}" }
                        val bytes = readEntryLimited(zip, MAX_ENTRY_BYTES)
                        total += bytes.size
                        require(total <= MAX_TOTAL_BYTES) { "插件包解压后超过大小上限" }
                        entries[entry.name] = bytes
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return entries
    }

    private fun readEntryLimited(zip: ZipInputStream, limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            require(out.size() <= limit) { "插件包内单个文件超过大小上限" }
            require(out.size() <= MAX_TOTAL_BYTES) { "插件包解压后超过大小上限" }
        }
        return out.toByteArray()
    }

    private fun parseManifest(bytes: ByteArray): Manifest {
        return try {
            json.decodeFromString(Manifest.serializer(), String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw IllegalArgumentException("manifest.json 格式错误：${e.message}")
        }
    }

    private fun validateManifest(manifest: Manifest) {
        validateId(manifest.id)
        require(manifest.name.isNotBlank() && manifest.name.length <= 64) { "插件名称不合法" }
        require(manifest.author.length <= 64) { "插件作者字段过长" }
        require(VERSION_PATTERN.matches(manifest.version)) { "插件版本号格式不合法" }
        require(manifest.apiVersion.matches(Regex("^[0-9]+\\.[0-9]+$"))) { "API 版本格式不合法" }
        require(manifest.type in setOf("config", "code")) { "未知插件类型" }
        require(manifest.permissions.size <= 20) { "权限声明过多" }
        require(manifest.permissions.all { PERMISSION_PATTERN.matches(it) }) { "权限标识不合法" }
        require(manifest.permissions.distinct().size == manifest.permissions.size) { "权限声明重复" }
        require(manifest.dependencies.size <= 20) { "依赖声明过多" }
        require(manifest.dependencies.all { ID_PATTERN.matches(it) && !it.contains("..") }) { "依赖 id 不合法" }
        val major = manifest.apiVersion.substringBefore('.').toIntOrNull() ?: 0
        require(major <= API_MAJOR) {
            "插件需要未济接口 ${manifest.apiVersion}，当前 $API_MAJOR.0，不兼容"
        }
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
