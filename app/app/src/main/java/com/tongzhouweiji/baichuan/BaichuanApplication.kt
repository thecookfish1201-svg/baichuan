package com.tongzhouweiji.baichuan

import android.app.Application
import com.tongzhouweiji.baichuan.core.AppContainer
import com.tongzhouweiji.baichuan.core.log.Logger
import com.tongzhouweiji.baichuan.core.work.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BaichuanApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        restoreScheduledMessages()
        appScope.launch { runCatching { container.refreshTheme() } }
    }

    /**
     * 应用启动时恢复主动消息调度：WorkManager 通常能保持任务，
     * 但重装、清数据或系统清理后会丢失，这里补一次调度保证功能可用。
     */
    private fun restoreScheduledMessages() {
        appScope.launch {
            runCatching {
                val items = container.scheduledMessageDao.listEnabled()
                val now = System.currentTimeMillis()
                items.forEach { item ->
                    val next = item.nextRunAt?.takeIf { it > now } ?: (now + 60_000L)
                    Scheduler.schedule(this@BaichuanApplication, item, next)
                }
                Logger.d("已恢复 ${items.size} 条主动消息调度")
            }.onFailure { Logger.w("恢复主动消息调度失败", it) }
        }
    }
}
