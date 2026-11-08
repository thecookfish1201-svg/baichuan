package com.tongzhouweiji.baichuan.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tongzhouweiji.baichuan.core.db.dao.AgentRunDao
import com.tongzhouweiji.baichuan.core.db.entity.AgentConsentEntity
import com.tongzhouweiji.baichuan.core.db.entity.AgentRunEntity
import com.tongzhouweiji.baichuan.core.db.entity.AgentActionEntity
import com.tongzhouweiji.baichuan.core.db.dao.AgentDao
import com.tongzhouweiji.baichuan.core.db.dao.AttachmentDao
import com.tongzhouweiji.baichuan.core.db.dao.ConversationDao
import com.tongzhouweiji.baichuan.core.db.dao.ErrorEventDao
import com.tongzhouweiji.baichuan.core.db.dao.MemoryDao
import com.tongzhouweiji.baichuan.core.db.dao.MessageDao
import com.tongzhouweiji.baichuan.core.db.dao.ProviderDao
import com.tongzhouweiji.baichuan.core.db.dao.ScheduledMessageDao
import com.tongzhouweiji.baichuan.core.db.dao.SettingDao
import com.tongzhouweiji.baichuan.core.db.dao.SummaryDao
import com.tongzhouweiji.baichuan.core.db.dao.PluginDao
import com.tongzhouweiji.baichuan.core.db.dao.PluginPermissionDao
import com.tongzhouweiji.baichuan.core.db.dao.McpDao
import com.tongzhouweiji.baichuan.core.db.entity.PluginEntity
import com.tongzhouweiji.baichuan.core.db.entity.PluginPermissionEntity
import com.tongzhouweiji.baichuan.core.db.entity.McpServerEntity
import com.tongzhouweiji.baichuan.core.db.entity.McpToolEntity
import com.tongzhouweiji.baichuan.core.db.entity.McpCallLogEntity
import com.tongzhouweiji.baichuan.core.db.dao.UsageDao
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

@Database(
    entities = [
        AgentEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        ProviderEntity::class,
        ModelEntity::class,
        AuxBindingEntity::class,
        SettingEntity::class,
        MemoryEntity::class,
        ConversationSummaryEntity::class,
        ErrorEventEntity::class,
        ScheduledMessageEntity::class,
        ApiUsageEntity::class,
        PluginEntity::class,
        PluginPermissionEntity::class,
        McpServerEntity::class,
        McpToolEntity::class,
        McpCallLogEntity::class,
        AgentConsentEntity::class,
        AgentRunEntity::class,
        AgentActionEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class BaichuanDatabase : RoomDatabase() {
    abstract fun agentDao(): AgentDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun settingDao(): SettingDao
    abstract fun providerDao(): ProviderDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun memoryDao(): MemoryDao
    abstract fun errorEventDao(): ErrorEventDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun summaryDao(): SummaryDao
    abstract fun usageDao(): UsageDao
    abstract fun auxBindingDao(): com.tongzhouweiji.baichuan.core.db.dao.AuxBindingDao
    abstract fun pluginDao(): PluginDao
    abstract fun pluginPermissionDao(): PluginPermissionDao
    abstract fun mcpDao(): McpDao
    abstract fun agentRunDao(): AgentRunDao

    companion object {
        private const val NAME = "baichuan.db"

        /** v1 → v2：messages 增加思考链两列。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = columnsOf(db, "messages")
                if ("errorSummary" !in columns) {
                    db.execSQL("ALTER TABLE `messages` ADD COLUMN `errorSummary` TEXT")
                }
                if ("reasoningContent" !in columns) {
                    db.execSQL("ALTER TABLE `messages` ADD COLUMN `reasoningContent` TEXT")
                }
                if ("reasoningMs" !in columns) {
                    db.execSQL("ALTER TABLE `messages` ADD COLUMN `reasoningMs` INTEGER")
                }
            }
        }

        /** v2 → v3：阶段三新增记忆、压缩摘要、错误事件、自动消息、用量表与 sourcesJson。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val messageColumns = columnsOf(db, "messages")
                if ("sourcesJson" !in messageColumns) {
                    db.execSQL("ALTER TABLE `messages` ADD COLUMN `sourcesJson` TEXT")
                }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `memories` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`agentId` INTEGER NOT NULL, `content` TEXT NOT NULL, " +
                        "`pinned` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`agentId`) REFERENCES `agents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_agentId` ON `memories` (`agentId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversation_summaries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`conversationId` INTEGER NOT NULL, `upToMessageId` INTEGER NOT NULL, " +
                        "`summary` TEXT NOT NULL, `tokenCount` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_conversation_summaries_conversationId` " +
                        "ON `conversation_summaries` (`conversationId`)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `error_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`agentId` INTEGER, `conversationId` INTEGER, `messageId` INTEGER, " +
                        "`code` TEXT NOT NULL, `summary` TEXT NOT NULL, `detail` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `deletedAt` INTEGER)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_error_events_agentId_createdAt` " +
                        "ON `error_events` (`agentId`, `createdAt`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_error_events_createdAt` ON `error_events` (`createdAt`)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scheduled_messages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`agentId` INTEGER NOT NULL, `mode` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                        "`windowStart` TEXT NOT NULL, `windowEnd` TEXT NOT NULL, " +
                        "`intervalMin` INTEGER NOT NULL, `intervalMax` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, `nextRunAt` INTEGER, `lastRunAt` INTEGER, " +
                        "`failureCount` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`agentId`) REFERENCES `agents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scheduled_messages_agentId` " +
                        "ON `scheduled_messages` (`agentId`)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `api_usage` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `scope` TEXT NOT NULL, " +
                        "`providerId` INTEGER, `modelId` INTEGER, `conversationId` INTEGER, " +
                        "`messageId` INTEGER, `requestKind` TEXT NOT NULL, " +
                        "`promptTokens` INTEGER, `completionTokens` INTEGER, `cachedTokens` INTEGER, " +
                        "`reasoningTokens` INTEGER, `latencyMs` INTEGER, `success` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_api_usage_createdAt` ON `api_usage` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_api_usage_scope` ON `api_usage` (`scope`)")
            }
        }


        /** v3 → v4：阶段五新增插件与 MCP 六张表。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plugins` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pluginId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `author` TEXT NOT NULL, `version` TEXT NOT NULL, " +
                        "`apiVersion` TEXT NOT NULL, `type` TEXT NOT NULL, `permissionsJson` TEXT NOT NULL, " +
                        "`state` TEXT NOT NULL, `installPath` TEXT NOT NULL, `previousVersion` TEXT, " +
                        "`installedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plugins_pluginId` ON `plugins` (`pluginId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plugin_permissions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pluginId` TEXT NOT NULL, " +
                        "`permission` TEXT NOT NULL, `granted` INTEGER NOT NULL, `grantedAt` INTEGER)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_plugin_permissions_pluginId_permission` " +
                        "ON `plugin_permissions` (`pluginId`, `permission`)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mcp_servers` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`transport` TEXT NOT NULL, `endpoint` TEXT NOT NULL, `headersRef` TEXT, " +
                        "`enabled` INTEGER NOT NULL, `lastStatus` TEXT NOT NULL, `lastConnectedAt` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_mcp_servers_name` ON `mcp_servers` (`name`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mcp_tools` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `serverId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `description` TEXT NOT NULL, `schemaJson` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`serverId`) REFERENCES `mcp_servers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mcp_tools_serverId` ON `mcp_tools` (`serverId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mcp_call_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `serverId` INTEGER NOT NULL, " +
                        "`toolName` TEXT NOT NULL, `argumentsDigest` TEXT NOT NULL, `resultDigest` TEXT NOT NULL, " +
                        "`success` INTEGER NOT NULL, `errorCode` TEXT, `durationMs` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_mcp_call_logs_createdAt` ON `mcp_call_logs` (`createdAt`)",
                )
            }
        }


        /** v4 → v5：阶段六新增 Agent 同意、运行与工具调用记录三张表。 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agent_consents` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `docVersion` TEXT NOT NULL, " +
                        "`permissionLevel` TEXT NOT NULL, `acceptedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agent_runs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `agentId` INTEGER, " +
                        "`goal` TEXT NOT NULL, `permissionLevel` TEXT NOT NULL, `state` TEXT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `cancelReason` TEXT)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_runs_startedAt` ON `agent_runs` (`startedAt`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agent_actions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `runId` INTEGER NOT NULL, " +
                        "`toolName` TEXT NOT NULL, `argsSummary` TEXT NOT NULL, `confirmed` INTEGER NOT NULL, " +
                        "`resultSummary` TEXT, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_actions_runId` ON `agent_actions` (`runId`)")
            }
        }


        /** v5 → v6：Agent 授权从全局改为按智能体绑定。 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `agent_consents` ADD COLUMN `agentId` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_agent_consents_agentId` ON `agent_consents` (`agentId`)",
                )
            }
        }

        private fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> {
            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(nameIndex))
                }
            }
            return columns
        }

        fun build(context: Context): BaichuanDatabase =
            Room.databaseBuilder(context, BaichuanDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                // 兜底：遇到无法迁移的旧版本时清库重建，保证不闪退（会丢本地数据）。
                .fallbackToDestructiveMigration()
                .build()
    }
}
