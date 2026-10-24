package com.fengkao.timetable.ui.xuexitong

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fengkao.timetable.TimetableApp
import com.fengkao.timetable.data.xuexitong.NoticeExtract
import com.fengkao.timetable.data.xuexitong.ScheduleChange
import com.fengkao.timetable.data.xuexitong.TaskItem
import com.fengkao.timetable.data.xuexitong.TaskStatus
import com.fengkao.timetable.data.xuexitong.XuexitongNotice
import com.fengkao.timetable.data.xuexitong.XuexitongNoticeScheduler
import com.fengkao.timetable.data.xuexitong.XuexitongNotifier
import com.fengkao.timetable.data.xuexitong.XuexitongRefresh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class XuexitongUiState(
    val initialized: Boolean = false,
    val phone: String = "",
    val loggedIn: Boolean = false,
    val sessionExpired: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
    val notices: List<XuexitongNotice> = emptyList(),
    /** 由通知二次抽取的待办：42 条通知 → 十几件要办的事。 */
    val tasks: List<TaskItem> = emptyList(),
    /** 从课程公告里抽出的课表异动。 */
    val changes: List<ScheduleChange> = emptyList(),
    val locallyReadIds: Set<String> = emptySet(),
    /** 已归档的通知：默认不出现在列表里，可在「归档」筛选里还原。 */
    val archivedIds: Set<String> = emptySet(),
    /** 已手动标记完成的待办（合并后的 key）。 */
    val doneTaskKeys: Set<String> = emptySet(),
    val unreadCount: Int = 0,
    val lastFetchAt: Long = 0L,
    val notifyEnabled: Boolean = true,
    val loginAccount: String = "",
    val loginPassword: String = "",
) {
    /** 首页卡正面要显示的「最近一件要交的事」；长期考试、已截止、已手动完成的都不算。 */
    val nextTask: TaskItem?
        get() = tasks.firstOrNull {
            !it.longWindow && it.key !in doneTaskKeys &&
                (it.status == TaskStatus.URGENT || it.status == TaskStatus.OPEN)
        }
}

/** 学习通账户与通知的共享状态（首页卡片、通知抽屉、设置页、初次设置共用）。 */
class XuexitongViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as TimetableApp).container.xuexitong
    private val _state = MutableStateFlow(snapshot())
    val state = _state.asStateFlow()

    private var lastAutoRefreshAt = 0L

    init {
        if (repository.hasSession() && repository.notifyEnabled()) {
            XuexitongNoticeScheduler.ensure(application)
        }
    }

    private fun snapshot(message: String? = null, error: Boolean = false): XuexitongUiState {
        val read = repository.localReadIds()
        val notices = repository.notices()
        return XuexitongUiState(
            initialized = true,
            phone = repository.phone(),
            loggedIn = repository.hasSession(),
            sessionExpired = repository.sessionExpired(),
            notices = notices,
            tasks = NoticeExtract.buildTasks(notices),
            changes = NoticeExtract.buildChanges(notices),
            locallyReadIds = read,
            archivedIds = repository.archivedIds(),
            doneTaskKeys = repository.doneTaskKeys(),
            unreadCount = repository.unreadCount(),
            lastFetchAt = repository.lastFetchAt(),
            notifyEnabled = repository.notifyEnabled(),
            loginAccount = repository.phone(),
            message = message,
            error = error,
        )
    }

    private fun syncState(message: String? = null, error: Boolean = false) {
        _state.update { old ->
            snapshot(message, error).copy(
                busy = old.busy,
                loginAccount = old.loginAccount,
                loginPassword = old.loginPassword,
            )
        }
    }

    fun account(value: String) {
        _state.update { it.copy(loginAccount = value.trim(), message = null, error = false) }
    }

    fun password(value: String) {
        _state.update { it.copy(loginPassword = value, message = null, error = false) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = false) }
    }

    /** 进入首页时调用：缓存够新就不打网络，避免每次启动都登录/抓取。 */
    fun enter() {
        if (!repository.hasSession() || _state.value.busy) return
        // 后台 Worker 可能已经抓取过；先回读本地缓存，再决定是否需要联网。
        syncState()
        val now = System.currentTimeMillis()
        val stale = now - repository.lastFetchAt() > 10 * 60_000L
        if (stale && now - lastAutoRefreshAt > 60_000L) {
            lastAutoRefreshAt = now
            refresh(silent = true)
        }
    }

    fun login() {
        val current = _state.value
        if (current.busy) return
        if (current.loginAccount.isBlank() || current.loginPassword.isBlank()) {
            _state.update { it.copy(message = "请输入学习通账号和密码", error = true) }
            return
        }
        _state.update { it.copy(busy = true, message = null, error = false) }
        viewModelScope.launch {
            try {
                repository.login(current.loginAccount, current.loginPassword).getOrThrow()
                XuexitongNoticeScheduler.ensure(getApplication())
                val result = repository.refresh()
                val message = when (result) {
                    is XuexitongRefresh.Success -> "学习通已连接"
                    is XuexitongRefresh.NeedLogin -> result.message
                    is XuexitongRefresh.Failure -> "已连接，通知暂未取到：${result.message}"
                    XuexitongRefresh.NotConfigured -> "学习通已连接"
                }
                syncState(message = message, error = result is XuexitongRefresh.NeedLogin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "学习通登录失败，请重试", error = true) }
            } finally {
                _state.update { it.copy(busy = false, loginPassword = "") }
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = !silent, message = if (silent) it.message else null, error = false) }
        viewModelScope.launch {
            try {
                when (val result = repository.refresh()) {
                    is XuexitongRefresh.Success -> {
                        if (!result.firstBaseline && result.newUnread.isNotEmpty()) {
                            XuexitongNotifier.notifyNew(getApplication(), result.newUnread)
                        }
                        syncState(if (silent) null else "已刷新")
                    }
                    is XuexitongRefresh.NeedLogin -> syncState(result.message, error = true)
                    is XuexitongRefresh.Failure -> syncState(result.message, error = true)
                    XuexitongRefresh.NotConfigured -> syncState("请先登录学习通", error = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                syncState(e.message ?: "学习通通知抓取失败", error = true)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun markRead(id: String) {
        repository.markRead(id)
        syncState()
    }

    fun markAllRead() {
        repository.markAllRead()
        syncState("已全部标记为已读")
    }

    fun archive(id: String) {
        repository.archive(id)
        syncState()
    }

    fun unarchive(id: String) {
        repository.unarchive(id)
        syncState()
    }

    /**
     * 「归档已读」：把已经读过的一批一次性收起来，留下真正待看的。
     * 这是用户最常用的那个动作——已读的留着只会把新通知顶下去。
     */
    fun archiveRead() {
        repository.archiveAllRead()
        syncState()
    }

    fun setTaskDone(key: String, done: Boolean) {
        repository.setTaskDone(key, done)
        syncState()
    }

    fun setNotifyEnabled(value: Boolean) {
        repository.setNotifyEnabled(value)
        if (value && repository.hasSession()) {
            XuexitongNoticeScheduler.ensure(getApplication())
        } else {
            XuexitongNoticeScheduler.cancel(getApplication())
        }
        syncState()
    }

    fun notifyPermissionDenied() {
        repository.setNotifyEnabled(false)
        XuexitongNoticeScheduler.cancel(getApplication())
        syncState("未授予通知权限，已关闭后台提醒；可在系统设置中开启", error = true)
    }

    fun logout() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null, error = false) }
        viewModelScope.launch {
            try {
                repository.logout()
                XuexitongNoticeScheduler.cancel(getApplication())
                _state.update {
                    it.copy(
                        busy = false,
                        phone = "",
                        loggedIn = false,
                        sessionExpired = false,
                        notices = emptyList(),
                        tasks = emptyList(),
                        changes = emptyList(),
                        locallyReadIds = emptySet(),
                        archivedIds = emptySet(),
                        doneTaskKeys = emptySet(),
                        unreadCount = 0,
                        lastFetchAt = 0L,
                        loginAccount = "",
                        loginPassword = "",
                        notifyEnabled = repository.notifyEnabled(),
                        message = "已退出学习通账户",
                        error = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = e.message ?: "退出失败", error = true) }
            }
        }
    }
}
