package com.fengkao.timetable.data.xuexitong

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fengkao.timetable.TimetableApp
import java.time.Duration
import java.util.concurrent.TimeUnit

/** 后台低频抓取学习通通知；首次建立基线，之后只推新增未读。 */
class XuexitongNoticeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TimetableApp ?: return Result.failure()
        val repository = app.container.xuexitong
        if (!repository.hasSession() || !repository.notifyEnabled()) return Result.success()
        return when (val result = repository.refresh()) {
            is XuexitongRefresh.Success -> {
                if (!result.firstBaseline && result.newUnread.isNotEmpty()) {
                    XuexitongNotifier.notifyNew(app, result.newUnread)
                }
                Result.success()
            }
            is XuexitongRefresh.NeedLogin -> {
                // 自动重登也失败时暂停轮询，避免每 30 分钟重复尝试触发风控。
                XuexitongNoticeScheduler.cancel(app)
                Result.success()
            }
            is XuexitongRefresh.Failure ->
                if (runAttemptCount < 3) Result.retry() else Result.success()
            XuexitongRefresh.NotConfigured -> Result.success()
        }
    }
}

object XuexitongNoticeScheduler {

    const val WORK_NAME = "xuexitong_notice_poll"
    const val PERIOD_MINUTES = 30L

    fun ensure(context: Context) {
        val request = PeriodicWorkRequestBuilder<XuexitongNoticeWorker>(
            Duration.ofMinutes(PERIOD_MINUTES),
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .setInitialDelay(Duration.ofMinutes((0L..10L).random()))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
