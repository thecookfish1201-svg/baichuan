package com.tongzhouweiji.baichuan.core

import android.content.Context
import com.tongzhouweiji.baichuan.chat.ChatRepository
import com.tongzhouweiji.baichuan.core.db.BaichuanDatabase
import com.tongzhouweiji.baichuan.core.log.Logger

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /**
     * 打开数据库。覆盖安装时如果旧库 schema 与当前不兼容，
     * 兜底删除本地库重建，保证应用能启动（会丢失本地数据，仅限开发期）。
     */
    val database: BaichuanDatabase by lazy {
        runCatching {
            BaichuanDatabase.build(appContext).also { it.openHelper.writableDatabase }
        }.getOrElse { error ->
            Logger.e("数据库打开失败，清库重建", error)
            appContext.deleteDatabase("baichuan.db")
            BaichuanDatabase.build(appContext)
        }
    }

    val agentDao by lazy { database.agentDao() }
    val conversationDao by lazy { database.conversationDao() }
    val messageDao by lazy { database.messageDao() }
    val settingDao by lazy { database.settingDao() }
    val providerDao by lazy { database.providerDao() }
    val attachmentDao by lazy { database.attachmentDao() }
    val memoryDao by lazy { database.memoryDao() }
    val errorEventDao by lazy { database.errorEventDao() }
    val scheduledMessageDao by lazy { database.scheduledMessageDao() }
    val summaryDao by lazy { database.summaryDao() }
    val usageDao by lazy { database.usageDao() }
    val auxBindingDao by lazy { database.auxBindingDao() }
    val pluginDao by lazy { database.pluginDao() }
    val pluginPermissionDao by lazy { database.pluginPermissionDao() }
    val mcpDao by lazy { database.mcpDao() }
    val agentRunDao by lazy { database.agentRunDao() }

    val attachmentStore by lazy { com.tongzhouweiji.baichuan.core.media.AttachmentStore(appContext) }

    val chatRepository by lazy { ChatRepository(database) }

    val pluginManager by lazy { com.tongzhouweiji.baichuan.core.plugin.PluginManager(appContext, database) }

    val agentManager by lazy { com.tongzhouweiji.baichuan.core.agent.AgentManager(appContext, database) }

    val agentRunner by lazy { com.tongzhouweiji.baichuan.core.agent.AgentRunner(database) }

    /** 插件主题主色（内存态，启用/停用时刷新）。 */
    private val _themePrimary =
        androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.Color?>(null)

    val themePrimary: androidx.compose.ui.graphics.Color?
        get() = _themePrimary.value

    suspend fun refreshTheme() {
        val hex = runCatching { settingDao.get("plugin_theme_primary") }.getOrNull()
        _themePrimary.value = hex?.takeIf { it.isNotBlank() }?.let { parseHexColor(it) }
    }

    private fun parseHexColor(hex: String): androidx.compose.ui.graphics.Color? = runCatching {
        val value = hex.removePrefix("#")
        when (value.length) {
            6 -> androidx.compose.ui.graphics.Color(0xFF000000 or value.toLong(16))
            8 -> androidx.compose.ui.graphics.Color(value.toLong(16))
            else -> null
        }
    }.getOrNull()
}
