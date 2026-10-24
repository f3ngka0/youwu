package com.fengkao.timetable.ui

import android.app.Application
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fengkao.timetable.TimetableApp
import com.fengkao.timetable.SyncScheduler
import com.fengkao.timetable.data.ScheduleRepository
import com.fengkao.timetable.data.TermCalculator
import com.fengkao.timetable.data.TermSpec
import com.fengkao.timetable.data.local.AppSettings
import com.fengkao.timetable.data.model.CachedSchedule
import com.fengkao.timetable.data.model.PeriodTime
import com.fengkao.timetable.data.model.CourseChange
import com.fengkao.timetable.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class MainUiState(
    val bootstrapped: Boolean = false,
    val loggedIn: Boolean = false,
    val selectedTab: Int = 0,
    val settings: AppSettings = AppSettings(),
    /** 当前展示的学期 */
    val term: TermSpec? = null,
    /** 当前展示的周次 */
    val week: Int = 1,
    /** 当前学期的课表缓存 */
    val schedule: CachedSchedule? = null,
    val syncing: Boolean = false,
    val backgroundSyncing: Boolean = false,
    val backgroundSyncError: String? = null,
    val courseChanges: List<CourseChange> = emptyList(),
    val courseChangesLoaded: Boolean = false,
    /** 课程笔记（作业 / 笔记 / 考点），全量常驻：一门课几十条，没必要按需查盘 */
    val courseNotes: List<com.fengkao.timetable.data.model.CourseNote> = emptyList(),
    val courseNotesLoaded: Boolean = false,
    val syncMessage: String? = null,
    val loginBusy: Boolean = false,
    val loginError: String? = null,
    /** 登录后的首次课表获取：进行中（获取完才进入主界面） */
    val firstFetchActive: Boolean = false,
    /** 首次获取失败（停留进度页，可重试/跳过） */
    val firstFetchFailed: Boolean = false,
    /** 获取过程日志（进度页展示） */
    val fetchLogs: List<String> = emptyList(),
    /** 来自桌面 Widget 深链的课程 stableKey，打开详情后即消费置空 */
    val widgetCourseKey: String? = null,
    /** 点击学习通通知 → 首页自动打开通知中心抽屉 */
    val openNoticesRequested: Boolean = false,
    /** 点击笔记提醒 → 打开对应课程详情（值为课程名） */
    val noteCourseRequested: String? = null,
    val welcomeSaving: Boolean = false,
    val welcomeError: String? = null,
    /** 正在检查更新 */
    val updateChecking: Boolean = false,
    /** 查到新版本且用户没选「不再提示」时才非空：驱动首页弹窗 */
    val updateAvailable: com.fengkao.timetable.data.update.UpdateInfo? = null,
    /** 手动检查更新的一次性提示（已是最新 / 检查失败），显示在关于页 */
    val updateHint: String? = null,
    /** 一次性提示是否为错误色 */
    val updateHintError: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container get() = (getApplication() as TimetableApp).container

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val today: LocalDate = TermCalculator.today()
    private var manualTermSelection = false

    init {
        bootstrap()
        observeStoredSettings()
        viewModelScope.launch {
            container.backgroundSyncError.collect { error ->
                _state.update { it.copy(backgroundSyncError = error) }
            }
        }
        viewModelScope.launch {
            WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(SyncScheduler.WORK_NAME).collect { work ->
                _state.update { it.copy(backgroundSyncing = work.any { item -> item.state == WorkInfo.State.RUNNING } && it.settings.autoSync && it.loggedIn) }
            }
        }
    }

    /** 观察原有设置与后台同步结果，不创建新的同步任务。 */
    private fun observeStoredSettings() = viewModelScope.launch {
        state.filter { it.bootstrapped }.first()
        container.settings.flow.collect { settings ->
            val previous = _state.value.settings
            val fresh = if (settings.lastSyncAt > 0 && settings.lastSyncAt != previous.lastSyncAt && settings.account == previous.account) {
                withContext(Dispatchers.IO) { container.cache.load() }
            } else null
            _state.update {
                val selectedTerm = it.term
                val freshTerm = fresh?.let { schedule -> TermSpec(schedule.xnm, schedule.term) }
                val adopt = freshTerm != null && (selectedTerm == freshTerm ||
                    (!manualTermSelection && (selectedTerm == null || freshTerm.rank > selectedTerm.rank)))
                val shownTerm = if (adopt) freshTerm else selectedTerm
                it.copy(settings = settings, term = shownTerm,
                    week = freshTerm?.takeIf { adopt }?.let { term ->
                        TermCalculator.weekOf(termStartOf(settings, term), today).coerceIn(1, settings.termWeeks)
                    } ?: it.week,
                    schedule = if (adopt) fresh else it.schedule,
                    syncMessage = if (adopt) null else it.syncMessage)
            }
        }
    }

    fun loadCourseChanges() = viewModelScope.launch {
        val changes = withContext(Dispatchers.IO) { container.changes.load() }
        _state.update { it.copy(courseChanges = changes, courseChangesLoaded = true) }
    }

    fun loadCourseNotes() = viewModelScope.launch {
        val notes = withContext(Dispatchers.IO) { container.notes.all() }
        _state.update { it.copy(courseNotes = notes, courseNotesLoaded = true) }
    }

    /** 新增或覆盖一条课程笔记。 */
    fun saveCourseNote(note: com.fengkao.timetable.data.model.CourseNote) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            container.notes.upsert(note)
            // 提醒落库后立刻重排闹钟，否则设了提醒也不会响
            com.fengkao.timetable.notify.NoteReminders.reschedule(getApplication())
        }
        loadCourseNotes()
    }

    fun deleteCourseNote(id: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            container.notes.delete(id)
            com.fengkao.timetable.notify.NoteReminders.reschedule(getApplication())
        }
        loadCourseNotes()
    }

    fun setCourseNoteDone(id: String, done: Boolean) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            container.notes.setDone(id, done)
            // 已完成的提醒要撤掉，未完成的要重新排队
            com.fengkao.timetable.notify.NoteReminders.reschedule(getApplication())
        }
        loadCourseNotes()
    }

    /** 把相册选中的图片复制进私有目录，返回落盘后的绝对路径。 */
    suspend fun importNoteImage(bytes: ByteArray): String? =
        withContext(Dispatchers.IO) { container.notes.addImageBytes(bytes) }

    private fun bootstrap() = viewModelScope.launch {
        var s = container.settings.first()
        val cached = withContext(Dispatchers.IO) { container.cache.load() }
        if (!s.welcomeInitialized) {
            container.settings.initializeWelcome(s.account.isNotBlank() || cached != null)
            s = container.settings.first()
        }
        val calendarTerm = TermCalculator.autoTerm(today)
        val currentCache = container.cache.load(calendarTerm)
        // Keep showing the last good semester until the new one has actual courses.
        val schedule = listOfNotNull(currentCache?.takeIf { it.courses.isNotEmpty() }, cached)
            .maxWithOrNull(compareBy { schedule: CachedSchedule -> TermSpec(schedule.xnm, schedule.term).rank })
        val term = schedule?.let { TermSpec(it.xnm, it.term) } ?: calendarTerm
        _state.update {
            it.copy(
                bootstrapped = true,
                loggedIn = s.account.isNotBlank(),
                settings = s,
                term = term,
                week = TermCalculator.weekOf(termStartOf(s, term), today).coerceIn(1, s.termWeeks),
                schedule = schedule,
            )
        }
        if (s.account.isNotBlank()) {
            SyncScheduler.ensure(getApplication())
            if (s.autoSync) sync()
        }
        refreshWidgets()
    }

    /** 课表/设置变化后同步桌面 Widget 显示 */
    private fun refreshWidgets() = WidgetUpdater.requestUpdateAll(getApplication())

    fun setWelcomeStep(step: Int) = viewModelScope.launch {
        try {
            container.settings.setWelcomeStep(step)
            _state.update { it.copy(welcomeError = null) }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { _state.update { it.copy(welcomeError = "引导进度保存失败，请重试") } }
    }

    fun finishWelcome() {
        if (_state.value.welcomeSaving) return
        _state.update { it.copy(welcomeSaving = true, welcomeError = null) }
        viewModelScope.launch {
            try {
                container.settings.completeWelcome()
                val settings = container.settings.first()
                _state.update { it.copy(settings = settings, selectedTab = 0) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(welcomeError = "设置完成状态保存失败，请重试") } }
            finally { _state.update { it.copy(welcomeSaving = false) } }
        }
    }

    fun reopenWelcome() = viewModelScope.launch {
        container.settings.restartWelcome()
        val settings = container.settings.first()
        _state.update { it.copy(settings = settings, welcomeError = null) }
    }

    fun termStartOf(s: AppSettings, term: TermSpec): LocalDate =
        s.termStartFor(term)

    // ------------------------------------------------------------ 登录/登出

    fun login(account: String, password: String, baseUrl: String, remember: Boolean) {
        if (_state.value.loginBusy || _state.value.firstFetchActive || _state.value.syncing) return
        if (account.isBlank() || password.isBlank()) {
            _state.update { it.copy(loginError = "请输入学号和密码") }
            return
        }
        _state.update { it.copy(loginBusy = true, loginError = null) }
        viewModelScope.launch {
            container.settings.saveCredentials(account, password.takeIf { remember }, baseUrl, remember)
            container.cache.clear()
            val s = container.settings.first()
            val term = TermCalculator.autoTerm(today)
            _state.update {
                it.copy(
                    loggedIn = true, loginBusy = false,
                    settings = s, term = term,
                    week = TermCalculator.weekOf(termStartOf(s, term), today),
                    firstFetchActive = true, firstFetchFailed = false, fetchLogs = emptyList(),
                )
            }
            SyncScheduler.ensure(getApplication())
            logTo("连接教务系统，登录账号…")
            // 自动模式（requested=null）：仓库内部按时间定位学期、逐候选回退，并附带校历抓取
            val outcome = doSync(null) { msg -> logTo(msg) }
            if (outcome is ScheduleRepository.SyncOutcome.Failure) {
                logTo("获取失败：${outcome.message}")
                _state.update { it.copy(firstFetchActive = false, firstFetchFailed = true) }
            } else {
                // 校历开学日期可能在本次同步中自动填入，重算当前周
                reloadSettings()
                jumpToday()
                _state.update { it.copy(firstFetchActive = false, firstFetchFailed = false) }
            }
            refreshWidgets()
        }
    }

    /** 首次获取失败后重试 */
    fun retryFirstFetch() {
        if (_state.value.firstFetchActive) return
        viewModelScope.launch {
            _state.update { it.copy(firstFetchActive = true, firstFetchFailed = false, fetchLogs = emptyList()) }
            logTo("连接教务系统，登录账号…")
            // 自动模式：附带校历抓取（同首次登录）
            val outcome = doSync(null) { msg -> logTo(msg) }
            if (outcome is ScheduleRepository.SyncOutcome.Failure) {
                logTo("获取失败：${outcome.message}")
                _state.update { it.copy(firstFetchActive = false, firstFetchFailed = true) }
            } else {
                // 校历开学日期可能在本次同步中自动填入，重算当前周
                reloadSettings()
                jumpToday()
                _state.update { it.copy(firstFetchActive = false, firstFetchFailed = false) }
            }
            refreshWidgets()
        }
    }

    /** 首次获取失败后跳过（使用本地缓存/空数据进入应用） */
    fun skipFirstFetch() = _state.update { it.copy(firstFetchActive = false, firstFetchFailed = false) }

    private fun logTo(msg: String) =
        _state.update { s -> s.copy(fetchLogs = (s.fetchLogs + msg).takeLast(10)) }

    fun logout() {
        viewModelScope.launch {
            container.backgroundSyncError.value = null
            container.settings.logout()
            container.cache.clear()
            _state.update {
                MainUiState(
                    bootstrapped = true,
                    loggedIn = false,
                    settings = it.settings.copy(account = "", passwordEnc = ""),
                )
            }
            refreshWidgets()
        }
    }

    // ------------------------------------------------------------ 同步

    /** 刷新当前展示的学期课表 */
    fun refresh() {
        sync(_state.value.term)
    }

    fun selectTerm(term: TermSpec) {
        if (term == _state.value.term) return
        manualTermSelection = true
        val s = _state.value.settings
        val cached = container.cache.load(term)
        if (cached == null) {
            // Do not blank a working timetable while an uncached term is loading.
            sync(term)
            return
        }
        _state.update {
            it.copy(
                term = term,
                week = TermCalculator.weekOf(termStartOf(s, term), today),
                schedule = cached,
                syncMessage = null,
            )
        }
        sync(term)
    }

    /** 后台静默同步（定期/手动刷新），无进度页 */
    fun sync(term: TermSpec? = null) = viewModelScope.launch { doSync(term) }

    /** 可等待的同步：供登录后的首次获取 await 使用；onLog 输出阶段日志 */
    private suspend fun doSync(
        term: TermSpec? = null,
        onLog: (String) -> Unit = {},
    ): ScheduleRepository.SyncOutcome {
        if (_state.value.syncing) {
            return ScheduleRepository.SyncOutcome.Failure("已有同步任务在进行")
        }
        _state.update { it.copy(syncing = true, syncMessage = null) }
        val outcome = container.repository.sync(term, onLog)
        when (outcome) {
            is ScheduleRepository.SyncOutcome.Success -> {
                container.backgroundSyncError.value = null
                val s = container.settings.first()
                val fresh = outcome.schedule
                _state.update {
                    val freshTerm = TermSpec(fresh.xnm, fresh.term)
                    val adopt = term != null || (!manualTermSelection && (it.term == null || freshTerm.rank >= it.term.rank))
                    val show = if (adopt) fresh else it.schedule
                    val shownTerm = if (adopt) freshTerm else it.term
                    it.copy(syncing = false, settings = s, term = shownTerm,
                        week = if (adopt) TermCalculator.weekOf(termStartOf(s, freshTerm), today).coerceIn(1, s.termWeeks) else it.week,
                        schedule = show, syncMessage = null)
                }
                refreshWidgets()
            }
            is ScheduleRepository.SyncOutcome.Failure ->
                _state.update { it.copy(syncing = false, syncMessage = outcome.message) }
        }
        return outcome
    }

    // ------------------------------------------------------------ UI 状态操作

    fun setTab(index: Int) = _state.update { it.copy(selectedTab = index.coerceIn(0, 3)) }

    /** 桌面 Widget 点击课程 → 打开详情 */
    fun openCourseFromWidget(key: String) =
        _state.update { it.copy(widgetCourseKey = key) }

    fun consumeWidgetCourseKey() =
        _state.update { it.copy(widgetCourseKey = null) }

    /** 系统通知点击 → 首页打开学习通通知中心 */
    fun requestOpenNotices() = _state.update { it.copy(selectedTab = 0, openNoticesRequested = true) }

    fun consumeOpenNotices() = _state.update { it.copy(openNoticesRequested = false) }

    /** 笔记提醒通知点击 → 首页打开该课程详情 */
    fun openNotesForCourse(course: String) =
        _state.update { it.copy(selectedTab = 0, noteCourseRequested = course) }

    fun consumeNoteCourseRequest() =
        _state.update { it.copy(noteCourseRequested = null) }

    fun selectWeek(week: Int) =
        _state.update { it.copy(week = week.coerceIn(1, it.settings.termWeeks), syncMessage = null) }

    fun changeWeek(delta: Int) = selectWeek(_state.value.week + delta)

    fun jumpToday() {
        val s = _state.value.settings
        val term = _state.value.term ?: return
        _state.update {
            it.copy(week = TermCalculator.weekOf(termStartOf(s, term), today).coerceIn(1, s.termWeeks))
        }
    }

    fun setAutoSync(value: Boolean) {
        viewModelScope.launch {
            container.settings.setAutoSync(value)
            reloadSettings()
        }
    }

    fun setPredictiveBack(value: Boolean) = viewModelScope.launch {
        container.settings.setPredictiveBack(value)
    }

    fun setBaseUrl(value: String) {
        viewModelScope.launch {
            container.settings.setBaseUrl(value.trim())
            reloadSettings()
        }
    }

    fun setTermStartEpochDay(value: Long) {
        viewModelScope.launch {
            val term = _state.value.term ?: TermCalculator.autoTerm()
            container.settings.setTermStartEpochDay(value, term)
            reloadSettings()
            jumpToday()
        }
    }

    fun setPeriodTimes(list: List<PeriodTime>) {
        viewModelScope.launch {
            container.settings.setPeriodTimes(list)
            reloadSettings()
        }
    }

    /** 学期周数（4..30），调整后回到“当前周” */
    fun setTermWeeks(weeks: Int) {
        viewModelScope.launch {
            container.settings.setTermWeeks(weeks)
            reloadSettings()
            jumpToday()
        }
    }

    /** 是否在课表中显示周六周日 */
    fun setShowWeekend(value: Boolean) {
        viewModelScope.launch {
            container.settings.setShowWeekend(value)
            reloadSettings()
        }
    }

    /** 隐藏/恢复一门课（仅本地显示，不影响同步） */
    fun toggleCourseHidden(key: String) {
        viewModelScope.launch {
            val s = container.settings.first()
            val next = if (key in s.hiddenCourses) s.hiddenCourses - key else s.hiddenCourses + key
            container.settings.setHiddenCourses(next)
            reloadSettings()
        }
    }

    /** 统计页：设置课程标签（类型/学分/硬核） */
    fun setCourseMeta(fullName: String, meta: com.fengkao.timetable.data.model.CourseMeta) = viewModelScope.launch {
        container.settings.setCourseMeta(fullName, meta)
        reloadSettings()
    }

    /** 统计页：设置总学费（元） */
    fun setTuitionFee(fee: Int) = viewModelScope.launch {
        container.settings.setTuitionFee(fee)
        reloadSettings()
    }

    /** 添加用户手动课程（本地存储，与教务课表合并显示） */
    fun addCustomCourse(c: com.fengkao.timetable.data.model.CustomCourse) = viewModelScope.launch {
        container.settings.addCustomCourse(c)
        reloadSettings()
    }

    /** 删除用户手动课程 */
    fun removeCustomCourse(c: com.fengkao.timetable.data.model.CustomCourse) = viewModelScope.launch {
        container.settings.removeCustomCourse(c)
        reloadSettings()
    }

    /** 恢复全部被隐藏的课程 */
    fun restoreAllCourses() {
        viewModelScope.launch {
            container.settings.setHiddenCourses(emptySet())
            reloadSettings()
        }
    }

    /** 统计页「空闲时间块分析」：添加每周重复的事务占用 */
    fun addBusyBlock(title: String, dayOfWeek: Int, startMin: Int, endMin: Int) {
        if (title.isBlank() || endMin <= startMin) return
        viewModelScope.launch {
            val s = container.settings.first()
            val nextId = (s.busyBlocks.maxOfOrNull { it.id } ?: 0L) + 1
            val block = com.fengkao.timetable.data.model.BusyBlock(
                id = nextId,
                title = title.trim(),
                dayOfWeek = dayOfWeek.coerceIn(1, 7),
                startMin = startMin.coerceIn(0, 24 * 60),
                endMin = endMin.coerceIn(0, 24 * 60),
            )
            container.settings.setBusyBlocks(s.busyBlocks + block)
            reloadSettings()
        }
    }

    /** 统计页「空闲时间块分析」：删除事务占用 */
    fun removeBusyBlock(id: Long) {
        viewModelScope.launch {
            val s = container.settings.first()
            container.settings.setBusyBlocks(s.busyBlocks.filterNot { it.id == id })
            reloadSettings()
        }
    }

    private suspend fun reloadSettings() {
        val s = container.settings.first()
        _state.update { it.copy(settings = s) }
        refreshWidgets()
    }

    // ---------------- 检查更新 ----------------

    /**
     * 检查 GitHub 上是否有更新的 Release。
     *
     * @param manual 手动点「检查更新」：结果要回显（已是最新 / 失败）；
     * 自动检查只在后台默默失败（断网、被墙、GitHub 抽风时弹错误框只会吓人）。
     */
    fun checkUpdate(manual: Boolean = false) {
        if (_state.value.updateChecking) return
        _state.update { it.copy(updateChecking = true, updateHint = if (manual) null else it.updateHint) }
        viewModelScope.launch {
            val current = com.fengkao.timetable.BuildConfig.VERSION_NAME
            val checker = com.fengkao.timetable.data.update.UpdateChecker(currentVersion = current)
            val result = checker.check()
            val settings = container.settings.first()
            runCatching { container.settings.setLastUpdateCheckAt(System.currentTimeMillis()) }
            val found = result.getOrNull()
            _state.update { st ->
                when {
                    found == null -> st.copy(
                        updateChecking = false,
                        updateHint = if (manual) "检查更新失败，请检查网络后重试" else null,
                        updateHintError = manual,
                    )
                    !found.newer -> st.copy(
                        updateChecking = false,
                        updateHint = if (manual) "已是最新版本 ${current}" else null,
                        updateHintError = false,
                        updateAvailable = null,
                    )
                    // 用户点过「不再提示这个版本」：安静跳过，不打扰
                    found.version == settings.skippedUpdateVersion -> st.copy(
                        updateChecking = false,
                        updateHint = if (manual) "发现新版本 ${found.version}（已设为不再提示）" else null,
                        updateHintError = false,
                        updateAvailable = null,
                    )
                    else -> st.copy(updateChecking = false, updateHint = null, updateAvailable = found)
                }
            }
        }
    }

    /** 关掉更新弹窗（「以后再说」），不记住任何东西，下次仍会提示 */
    fun dismissUpdatePrompt() = _state.update { it.copy(updateAvailable = null) }

    /** 用户选择「这个版本不再提示」 */
    fun skipUpdateVersion(version: String) {
        _state.update { it.copy(updateAvailable = null) }
        viewModelScope.launch { runCatching { container.settings.skipUpdateVersion(version) } }
    }

    /** 冷启动自动检查：距上次检查超过 12 小时才查，避免每次杀进程回来都打 GitHub */
    fun autoCheckUpdate() {
        viewModelScope.launch {
            val s = container.settings.first()
            val now = System.currentTimeMillis()
            if (now - s.lastUpdateCheckAt < 12 * 60 * 60 * 1000L) return@launch
            checkUpdate(manual = false)
        }
    }
}
