package com.tongzhouweiji.baichuan.core.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.tongzhouweiji.baichuan.BuildConfig
import com.tongzhouweiji.baichuan.core.db.BaichuanDatabase
import com.tongzhouweiji.baichuan.core.db.entity.AgentEntity
import com.tongzhouweiji.baichuan.core.db.entity.ApiUsageEntity
import com.tongzhouweiji.baichuan.core.db.entity.AttachmentEntity
import com.tongzhouweiji.baichuan.core.db.entity.AuxBindingEntity
import com.tongzhouweiji.baichuan.core.db.entity.ConversationEntity
import com.tongzhouweiji.baichuan.core.db.entity.ConversationSummaryEntity
import com.tongzhouweiji.baichuan.core.db.entity.ErrorEventEntity
import com.tongzhouweiji.baichuan.core.db.entity.MemoryEntity
import com.tongzhouweiji.baichuan.core.db.entity.MessageEntity
import com.tongzhouweiji.baichuan.core.db.entity.ModelEntity
import com.tongzhouweiji.baichuan.core.db.entity.ProviderEntity
import com.tongzhouweiji.baichuan.core.db.entity.ScheduledMessageEntity
import com.tongzhouweiji.baichuan.core.db.entity.SettingEntity
import com.tongzhouweiji.baichuan.core.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 同舟备份：单一 ZIP 包与文件夹两种形式，共用同一套数据标准。
 * 结构：
 *   manifest.json          格式版本、软件版本、创建时间、类别、文件清单（SHA-256、大小）
 *   data/表名.json         各表数据（不含 API Key 明文）
 *   files/attachments/...  附件文件（相对路径）
 */
class BackupManager(
    private val context: Context,
    private val db: BaichuanDatabase,
) {

    companion object {
        const val FORMAT_VERSION = 1
        private const val MANIFEST = "manifest.json"
        private const val DIR_PREFIX = "baichuan-backup-"

        val CATEGORIES = listOf(
            Category.CHAT, Category.AGENTS, Category.MEMORIES, Category.SETTINGS, Category.ATTACHMENTS,
        )
    }

    enum class Category(val key: String, val label: String) {
        CHAT("chat", "聊天记录"),
        AGENTS("agents", "智能体与模型"),
        MEMORIES("memories", "记忆"),
        SETTINGS("settings", "设置"),
        ATTACHMENTS("attachments", "附件文件"),
    }

    @Serializable
    data class BackupManifest(
        val formatVersion: Int,
        val appVersion: String,
        val createdAt: Long,
        val categories: List<String>,
        val files: List<BackupFile>,
    )

    @Serializable
    data class BackupFile(val path: String, val sha256: String, val size: Long)

    data class BackupInfo(
        val fileCount: Int,
        val totalBytes: Long,
        val categories: List<String>,
        val createdAt: Long,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---------- 数据收集（ZIP 与文件夹共用） ----------

    private suspend fun collectEntries(categories: Set<Category>): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        if (Category.CHAT in categories) {
            entries["data/conversations.json"] = encode(db.conversationDao().listAll())
            entries["data/messages.json"] = encode(db.messageDao().listAll())
            entries["data/attachments.json"] = encode(db.attachmentDao().listAll())
            entries["data/summaries.json"] = encode(db.summaryDao().listAll())
            entries["data/error_events.json"] = encode(db.errorEventDao().listAll())
            entries["data/scheduled_messages.json"] = encode(db.scheduledMessageDao().listAll())
            entries["data/api_usage.json"] = encode(db.usageDao().listAll())
        }
        if (Category.AGENTS in categories) {
            entries["data/agents.json"] = encode(db.agentDao().listAll())
            entries["data/providers.json"] = encode(db.providerDao().listAll())
            entries["data/models.json"] = encode(db.providerDao().modelsAll())
            entries["data/aux_bindings.json"] = encode(db.auxBindingDao().listAll())
        }
        if (Category.MEMORIES in categories) {
            entries["data/memories.json"] = encode(db.memoryDao().listAll())
        }
        if (Category.SETTINGS in categories) {
            entries["data/settings.json"] = encode(db.settingDao().listAll())
        }
        if (Category.ATTACHMENTS in categories) {
            db.attachmentDao().listAll().forEach { attachment ->
                val file = File(context.filesDir, attachment.uri)
                if (file.exists()) {
                    entries["files/${attachment.uri}"] = file.readBytes()
                }
            }
        }
        return entries
    }

    private fun buildManifest(entries: Map<String, ByteArray>, categories: Set<Category>): BackupManifest =
        BackupManifest(
            formatVersion = FORMAT_VERSION,
            appVersion = appVersion(),
            createdAt = System.currentTimeMillis(),
            categories = categories.map { it.key }.sorted(),
            files = entries.map { (path, bytes) ->
                BackupFile(path = path, sha256 = sha256(bytes), size = bytes.size.toLong())
            }.sortedBy { it.path },
        )

    // ---------- ZIP 导出 / 导入 ----------

    suspend fun exportToZip(uri: Uri, categories: Set<Category>): BackupInfo = withContext(Dispatchers.IO) {
        val entries = collectEntries(categories)
        val manifest = buildManifest(entries, categories)
        val manifestBytes = json.encodeToString(BackupManifest.serializer(), manifest).toByteArray()
        context.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifestBytes)
                zip.closeEntry()
                entries.forEach { (path, bytes) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        } ?: throw IllegalStateException("无法写入备份文件")
        BackupInfo(
            fileCount = entries.size + 1,
            totalBytes = manifestBytes.size + entries.values.sumOf { it.size.toLong() },
            categories = manifest.categories,
            createdAt = manifest.createdAt,
        )
    }

    suspend fun importFromZip(uri: Uri): BackupInfo = withContext(Dispatchers.IO) {
        val entries = readZip(uri)
        val manifest = readManifest(entries) ?: throw IllegalArgumentException("备份缺少 manifest")
        verify(entries, manifest)
        protectAndApply(entries, manifest)
    }

    // ---------- 文件夹导出 / 导入 ----------

    suspend fun exportToFolder(treeUri: Uri, categories: Set<Category>): BackupInfo = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("无法访问所选目录")
        val dirName = DIR_PREFIX + System.currentTimeMillis()
        val dir = root.createDirectory(dirName) ?: throw IllegalStateException("无法创建备份目录")
        val entries = collectEntries(categories)
        val manifest = buildManifest(entries, categories)
        val manifestBytes = json.encodeToString(BackupManifest.serializer(), manifest).toByteArray()
        writeFile(dir, MANIFEST, manifestBytes)
        entries.forEach { (path, bytes) -> writeFile(dir, path, bytes) }
        BackupInfo(
            fileCount = entries.size + 1,
            totalBytes = manifestBytes.size + entries.values.sumOf { it.size.toLong() },
            categories = manifest.categories,
            createdAt = manifest.createdAt,
        )
    }

    suspend fun importFromFolder(treeUri: Uri): BackupInfo = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("无法访问所选目录")
        val dir = findBackupDir(root) ?: throw IllegalArgumentException("所选目录不是百川备份（缺少 manifest）")
        val entries = readFolderEntries(dir)
        val manifest = readManifest(entries) ?: throw IllegalArgumentException("备份缺少 manifest")
        verify(entries, manifest)
        protectAndApply(entries, manifest)
    }

    private fun findBackupDir(root: DocumentFile): DocumentFile? {
        if (root.findFile(MANIFEST) != null) return root
        return root.listFiles()
            .filter { it.isDirectory }
            .sortedByDescending { it.name }
            .firstOrNull { it.findFile(MANIFEST) != null }
    }

    private fun readFolderEntries(dir: DocumentFile): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        fun walk(current: DocumentFile, prefix: String) {
            current.listFiles().forEach { file ->
                if (file.isDirectory) {
                    walk(file, "$prefix${file.name}/")
                } else {
                    val name = file.name ?: return@forEach
                    val bytes = context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
                    if (bytes != null) entries["$prefix$name"] = bytes
                }
            }
        }
        walk(dir, "")
        return entries
    }

    private fun writeFile(dir: DocumentFile, path: String, bytes: ByteArray) {
        val parts = path.split('/')
        var current = dir
        parts.dropLast(1).forEach { part ->
            current = current.findFile(part)?.takeIf { it.isDirectory }
                ?: current.createDirectory(part)
                ?: throw IllegalStateException("无法创建目录：$part")
        }
        val fileName = parts.last()
        val mime = if (fileName.endsWith(".json")) "application/json" else "application/octet-stream"
        val file = current.findFile(fileName)?.takeIf { it.isFile }
            ?: current.createFile(mime, fileName)
            ?: throw IllegalStateException("无法创建文件：$fileName")
        context.contentResolver.openOutputStream(file.uri)?.use { it.write(bytes) }
            ?: throw IllegalStateException("无法写入文件：$fileName")
    }

    // ---------- 校验与恢复 ----------

    private fun readManifest(entries: Map<String, ByteArray>): BackupManifest? {
        val bytes = entries[MANIFEST] ?: return null
        return json.decodeFromString(BackupManifest.serializer(), String(bytes))
    }

    private fun verify(entries: Map<String, ByteArray>, manifest: BackupManifest) {
        if (manifest.formatVersion > FORMAT_VERSION) {
            throw IllegalArgumentException("备份格式版本过新（${manifest.formatVersion}），请升级应用")
        }
        manifest.files.forEach { entry ->
            val bytes = entries[entry.path] ?: throw IllegalArgumentException("备份缺少文件：${entry.path}")
            if (bytes.size.toLong() != entry.size || sha256(bytes) != entry.sha256) {
                throw IllegalArgumentException("备份文件校验失败：${entry.path}")
            }
        }
    }

    private suspend fun protectAndApply(
        entries: Map<String, ByteArray>,
        manifest: BackupManifest,
    ): BackupInfo {
        val safety = File(context.cacheDir, "pre_restore_${System.currentTimeMillis()}.zip")
        runCatching {
            safety.writeBytes(snapshotToBytes())
        }

        try {
            applyBackup(entries, manifest.categories.toSet())
            return BackupInfo(
                fileCount = entries.size,
                totalBytes = entries.values.sumOf { it.size.toLong() },
                categories = manifest.categories,
                createdAt = manifest.createdAt,
            )
        } catch (e: Exception) {
            Logger.e("导入失败，尝试恢复", e)
            runCatching {
                if (safety.exists()) restoreFromBytes(safety.readBytes())
            }.onFailure { Logger.e("恢复保护数据失败", it) }
            throw e
        }
    }

    private suspend fun applyBackup(entries: Map<String, ByteArray>, categories: Set<String>) {
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
        try {
            db.withTransaction {
                if ("chat" in categories) {
                    db.attachmentDao().clearAll()
                    db.messageDao().clearAll()
                    db.summaryDao().clearAll()
                    db.errorEventDao().clearAll()
                    db.scheduledMessageDao().clearAll()
                    db.usageDao().clearAll()
                    db.conversationDao().clearAll()
                    entries["data/conversations.json"]?.let { db.conversationDao().insertAll(decodeList<ConversationEntity>(it)) }
                    entries["data/messages.json"]?.let { db.messageDao().insertAll(decodeList<MessageEntity>(it)) }
                    entries["data/attachments.json"]?.let { db.attachmentDao().insertAll(decodeList<AttachmentEntity>(it)) }
                    entries["data/summaries.json"]?.let { db.summaryDao().insertAll(decodeList<ConversationSummaryEntity>(it)) }
                    entries["data/error_events.json"]?.let { db.errorEventDao().insertAll(decodeList<ErrorEventEntity>(it)) }
                    entries["data/scheduled_messages.json"]?.let { db.scheduledMessageDao().insertAll(decodeList<ScheduledMessageEntity>(it)) }
                    entries["data/api_usage.json"]?.let { db.usageDao().insertAll(decodeList<ApiUsageEntity>(it)) }
                }
                if ("agents" in categories) {
                    db.auxBindingDao().clearAll()
                    db.providerDao().clearModelsAll()
                    db.providerDao().clearAll()
                    db.agentDao().clearAll()
                    entries["data/agents.json"]?.let { db.agentDao().insertAll(decodeList<AgentEntity>(it)) }
                    entries["data/providers.json"]?.let { db.providerDao().insertAll(decodeList<ProviderEntity>(it)) }
                    entries["data/models.json"]?.let { db.providerDao().insertModels(decodeList<ModelEntity>(it)) }
                    entries["data/aux_bindings.json"]?.let { db.auxBindingDao().insertAll(decodeList<AuxBindingEntity>(it)) }
                }
                if ("memories" in categories) {
                    db.memoryDao().clearAll()
                    entries["data/memories.json"]?.let { db.memoryDao().insertAll(decodeList<MemoryEntity>(it)) }
                }
                if ("settings" in categories) {
                    db.settingDao().clearAll()
                    entries["data/settings.json"]?.let { db.settingDao().insertAll(decodeList<SettingEntity>(it)) }
                }
            }
            if ("attachments" in categories) {
                entries.keys.filter { it.startsWith("files/") }.forEach { path ->
                    val relative = path.removePrefix("files/")
                    val target = File(context.filesDir, relative)
                    target.parentFile?.mkdirs()
                    entries[path]?.let { target.writeBytes(it) }
                }
            }
        } finally {
            db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    private suspend fun snapshotToBytes(): ByteArray {
        val entries = mapOf(
            "data/agents.json" to encode(db.agentDao().listAll()),
            "data/providers.json" to encode(db.providerDao().listAll()),
            "data/models.json" to encode(db.providerDao().modelsAll()),
            "data/aux_bindings.json" to encode(db.auxBindingDao().listAll()),
            "data/conversations.json" to encode(db.conversationDao().listAll()),
            "data/messages.json" to encode(db.messageDao().listAll()),
            "data/attachments.json" to encode(db.attachmentDao().listAll()),
            "data/summaries.json" to encode(db.summaryDao().listAll()),
            "data/error_events.json" to encode(db.errorEventDao().listAll()),
            "data/scheduled_messages.json" to encode(db.scheduledMessageDao().listAll()),
            "data/api_usage.json" to encode(db.usageDao().listAll()),
            "data/memories.json" to encode(db.memoryDao().listAll()),
            "data/settings.json" to encode(db.settingDao().listAll()),
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private suspend fun restoreFromBytes(bytes: ByteArray) {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        applyBackup(entries, setOf("chat", "agents", "memories", "settings"))
    }

    private fun readZip(uri: Uri): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalArgumentException("无法读取备份文件")
        return entries
    }

    private inline fun <reified T> encode(items: List<T>): ByteArray =
        json.encodeToString(ListSerializer(serializer<T>()), items).toByteArray()

    private inline fun <reified T> decodeList(bytes: ByteArray): List<T> =
        json.decodeFromString(ListSerializer(serializer<T>()), String(bytes))

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun appVersion(): String = runCatching { BuildConfig.VERSION_NAME }.getOrDefault("unknown")
}
