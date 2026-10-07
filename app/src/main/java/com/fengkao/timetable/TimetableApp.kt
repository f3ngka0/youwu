package com.fengkao.timetable

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fengkao.timetable.data.ScheduleRepository
import com.fengkao.timetable.data.local.ScheduleCache
import com.fengkao.timetable.data.local.CourseChangeStore
import com.fengkao.timetable.data.local.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Duration

/** 手工依赖容器（规模小，不引入 Hilt） */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settings = SettingsStore(appContext)
    val cache = ScheduleCache(appContext)
    val changes = CourseChangeStore(appContext)
    val repository = ScheduleRepository(appContext, settings, cache, changes)
    // 展示本进程内最近的后台同步结果，不改变原任务或持久化结构。
    val backgroundSyncError = MutableStateFlow<String?>(null)
}

class TimetableApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 深浅模式切换：玻璃底图按新配置重绘（进程存活时立即生效）
        com.fengkao.timetable.widget.WidgetUpdater.requestUpdateAll(this)
    }
}

/** 周期性后台同步：登录态有效时自动拉取最新课表并更新本地缓存 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TimetableApp ?: return Result.failure()
        val container = app.container
        val s = runCatching { container.settings.first() }.getOrNull()
            ?: return Result.retry()
        if (!s.autoSync || s.account.isBlank() || s.passwordEnc.isBlank()) {
            return Result.success()
        }
        container.backgroundSyncError.value = null
        return when (val outcome = container.repository.sync()) {
            is ScheduleRepository.SyncOutcome.Success -> Result.success()
            is ScheduleRepository.SyncOutcome.Failure -> {
                container.backgroundSyncError.value = outcome.message
                if (runAttemptCount < 3) Result.retry() else Result.success()
            }
        }
    }
}

object SyncScheduler {
    const val WORK_NAME = "timetable_periodic_sync"
    const val PERIOD_HOURS = 6L

    fun ensure(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofHours(PERIOD_HOURS))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
