package com.tongzhouweiji.baichuan.core.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tongzhouweiji.baichuan.core.db.entity.ScheduledMessageEntity
import java.util.concurrent.TimeUnit

/** 主动消息调度：每个配置一条唯一 Work，按 nextRunAt 延迟执行。 */
object Scheduler {

    fun schedule(context: Context, item: ScheduledMessageEntity, at: Long) {
        if (!item.enabled) {
            cancel(context, item.id)
            return
        }
        val delay = (at - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ScheduledMessageWorker.KEY_SCHEDULED_ID to item.id))
            .addTag(tagOf(item.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            tagOf(item.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context, id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(tagOf(id))
    }

    private fun tagOf(id: Long): String = "baichuan_scheduled_$id"
}
