package com.tongzhouweiji.baichuan.core.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tongzhouweiji.baichuan.BaichuanApplication
import com.tongzhouweiji.baichuan.core.db.entity.ScheduledMessageEntity
import com.tongzhouweiji.baichuan.core.security.SecretStore
import com.tongzhouweiji.baichuan.model.ChatMessage
import com.tongzhouweiji.baichuan.model.ChatRequest
import com.tongzhouweiji.baichuan.model.ModelAdapterRegistry
import com.tongzhouweiji.baichuan.model.ProviderConfig
import java.util.Calendar

/**
 * 智能体主动消息。考虑 Android 后台调度限制，不承诺精准时间：
 * 窗口外不发送；智能体停用不拉黑不发送；成功后按随机间隔安排下一次。
 */
class ScheduledMessageWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val scheduledId = inputData.getLong(KEY_SCHEDULED_ID, -1L)
        if (scheduledId <= 0L) return Result.success()
        val app = applicationContext as BaichuanApplication
        val dao = app.container.scheduledMessageDao
        val item = dao.findById(scheduledId) ?: return Result.success()
        if (!item.enabled) return Result.success()

        val agent = app.container.agentDao.findById(item.agentId) ?: return Result.success()
        if (!agent.enabled || agent.blocked) return Result.success()

        if (!inWindow(item)) {
            Scheduler.schedule(applicationContext, item, at = nextWindowStart(item))
            return Result.success()
        }

        val conversation = app.container.chatRepository.ensureConversation(agent.id)
        val parentId = app.container.conversationDao.findById(conversation.id)?.activeLeafMessageId
        val content = if (item.mode == "static" && item.content.isNotBlank()) {
            item.content
        } else {
            generate(app, agent.id).orEmpty()
        }
        if (content.isNotBlank()) {
            app.container.chatRepository.appendMessage(
                conversationId = conversation.id,
                role = "assistant",
                content = content,
                parentId = parentId,
            )
            notify(app, agent.name, content)
        }

        val next = nextRunAt(item)
        dao.markRun(item.id, System.currentTimeMillis(), next)
        Scheduler.schedule(applicationContext, item, at = next)
        return Result.success()
    }

    private suspend fun generate(app: BaichuanApplication, agentId: Long): String? {
        val agent = app.container.agentDao.findById(agentId) ?: return null
        val modelId = agent.modelConfigId ?: app.container.providerDao.defaultModel()?.id ?: return null
        val model = app.container.providerDao.findModelById(modelId) ?: return null
        val provider = app.container.providerDao.findById(model.providerId) ?: return null
        val apiKey = SecretStore.decrypt(provider.keyRef.orEmpty())
        if (apiKey.isEmpty()) return null
        val config = ProviderConfig(
            id = provider.id,
            name = provider.name,
            baseUrl = provider.baseUrl,
            protocol = provider.protocol,
            apiKey = apiKey,
        )
        val adapter = ModelAdapterRegistry.adapterFor(config.protocol)
        val prompt = buildString {
            if (agent.systemPrompt.isNotBlank()) append(agent.systemPrompt).append("\n\n")
            if (agent.persona.isNotBlank()) append("人设：").append(agent.persona).append("\n\n")
            append("现在主动给用户发一条简短的消息，自然一些，不要解释你在主动发送。")
        }
        return runCatching {
            adapter.chat(
                config,
                ChatRequest(
                    model = model.modelName,
                    messages = listOf(ChatMessage(role = "user", content = prompt)),
                    stream = false,
                ),
            ).content.ifBlank { null }
        }.getOrNull()
    }

    private fun inWindow(item: ScheduledMessageEntity): Boolean {
        val minutes = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        return minutes in parseTime(item.windowStart)..parseTime(item.windowEnd)
    }

    private fun nextWindowStart(item: ScheduledMessageEntity): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parseTime(item.windowStart) / 60)
            set(Calendar.MINUTE, parseTime(item.windowStart) % 60)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    private fun nextRunAt(item: ScheduledMessageEntity): Long {
        val min = item.intervalMin.coerceAtLeast(1)
        val max = item.intervalMax.coerceAtLeast(min)
        val delayMinutes = (min..max).random()
        val next = System.currentTimeMillis() + delayMinutes * 60_000L
        return if (inWindow(item)) next else nextWindowStart(item)
    }

    private fun parseTime(value: String): Int {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return (hour * 60 + minute).coerceIn(0, 24 * 60 - 1)
    }

    private fun notify(app: BaichuanApplication, agentName: String, content: String) {
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "智能体消息", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(agentName)
            .setContentText(content.take(60))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(app)
                .notify((System.currentTimeMillis() % 100000).toInt(), notification)
        }
    }

    companion object {
        const val KEY_SCHEDULED_ID = "scheduled_id"
        const val CHANNEL_ID = "baichuan_agent_messages"
    }
}
