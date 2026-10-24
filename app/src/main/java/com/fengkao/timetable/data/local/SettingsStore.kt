package com.fengkao.timetable.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fengkao.timetable.data.model.BusyBlock
import com.fengkao.timetable.data.TermSpec
import com.fengkao.timetable.data.TermCalculator
import com.fengkao.timetable.data.model.CourseMeta
import com.fengkao.timetable.data.model.PeriodTime
import com.fengkao.timetable.data.model.defaultPeriodTimes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

const val DEFAULT_BASE_URL = "https://jwxt.gxuwz.edu.cn"

data class AppSettings(
    val account: String = "",
    val passwordEnc: String = "",
    val baseUrl: String = DEFAULT_BASE_URL,
    val rememberPassword: Boolean = true,
    val autoSync: Boolean = true,
    val predictiveBack: Boolean = false,
    val welcomeInitialized: Boolean = false,
    val welcomeCompleted: Boolean = false,
    val welcomeStep: Int = 0,
    /** 0 表示自动推算学期起始日 */
    val termStartEpochDay: Long = 0L,
    val termStartXnm: Int = 0,
    val termStartTerm: Int = 0,
    val lastXnm: Int = 0,
    val lastTerm: Int = 0,
    val lastSyncAt: Long = 0L,
    val periodTimesJson: String = "",
    /** 学期总周数（默认 16 周） */
    val termWeeks: Int = 16,
    /** 课表是否显示周六周日（默认开） */
    val showWeekend: Boolean = true,
    /** 本地隐藏的课程（stableKey 集合，仅影响显示，不影响同步数据） */
    val hiddenCourses: Set<String> = emptySet(),
    /** 桌面 Widget 课程显示简称（课名→简称） */
    val nameOverrides: Map<String, String> = emptyMap(),
    /** 每门课的本地标签：类型 / 学分 / 硬核标记（统计页画像用） */
    val courseMeta: Map<String, CourseMeta> = emptyMap(),
    /** 总学费（元），用于学费回本统计 */
    val tuitionFee: Int = 6000,
    /** 用户手动添加的课程（本地，与教务课表合并显示） */
    val customCourses: List<com.fengkao.timetable.data.model.CustomCourse> = emptyList(),
    /** 统计页「空闲时间块分析」：用户手动添加的每周重复事务占用 */
    val busyBlocks: List<BusyBlock> = emptyList(),
    /** 用户点了「不再提示这个版本」的版本号，同一个版本不再弹更新 */
    val skippedUpdateVersion: String = "",
    /** 上次检查更新的时间（毫秒），用于自动检查的频控 */
    val lastUpdateCheckAt: Long = 0L,
) {
    fun hasTermStartFor(term: TermSpec): Boolean {
        if (termStartEpochDay <= 0) return false
        val ownerXnm = termStartXnm.takeIf { it > 0 } ?: lastXnm
        val ownerTerm = termStartTerm.takeIf { it > 0 } ?: lastTerm
        return ownerXnm == term.xnm && ownerTerm == term.term
    }

    fun termStartFor(term: TermSpec): LocalDate =
        if (hasTermStartFor(term)) runCatching { LocalDate.ofEpochDay(termStartEpochDay) }
            .getOrElse { TermCalculator.defaultTermStart(term.xnm, term.term) }
        else TermCalculator.defaultTermStart(term.xnm, term.term)

    fun periodTimes(): List<PeriodTime> =
        if (periodTimesJson.isBlank()) defaultPeriodTimes()
        else runCatching {
            json.decodeFromString(ListSerializer(PeriodTime.serializer()), periodTimesJson)
        }.getOrDefault(defaultPeriodTimes())
}

private val json = Json { ignoreUnknownKeys = true }

private val HIDDEN_SERIALIZER = ListSerializer(String.serializer())
private val strMapSerializer = MapSerializer(String.serializer(), String.serializer())
private val metaMapSerializer = MapSerializer(String.serializer(), CourseMeta.serializer())

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "timetable_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val account = stringPreferencesKey("account")
        val passwordEnc = stringPreferencesKey("password_enc")
        val baseUrl = stringPreferencesKey("base_url")
        val rememberPassword = booleanPreferencesKey("remember_password")
        val autoSync = booleanPreferencesKey("auto_sync")
        val predictiveBack = booleanPreferencesKey("predictive_back")
        val welcomeInitialized = booleanPreferencesKey("welcome_initialized")
        val welcomeCompleted = booleanPreferencesKey("welcome_completed")
        val welcomeStep = intPreferencesKey("welcome_step")
        val termStart = longPreferencesKey("term_start_epoch_day")
        val termStartXnm = intPreferencesKey("term_start_xnm")
        val termStartTerm = intPreferencesKey("term_start_term")
        val lastXnm = intPreferencesKey("last_xnm")
        val lastTerm = intPreferencesKey("last_term")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val periodTimes = stringPreferencesKey("period_times_json")
        val termWeeks = intPreferencesKey("term_weeks")
        val showWeekend = booleanPreferencesKey("show_weekend")
        val hiddenCourses = stringPreferencesKey("hidden_courses_json")
        val nameOverrides = stringPreferencesKey("name_overrides_json")
        val courseMeta = stringPreferencesKey("course_meta_json")
        val tuitionFee = intPreferencesKey("tuition_fee")
        val customCourses = stringPreferencesKey("custom_courses_json")
        val busyBlocks = stringPreferencesKey("busy_blocks_json")
        val skippedUpdateVersion = stringPreferencesKey("skipped_update_version")
        val lastUpdateCheckAt = longPreferencesKey("last_update_check_at")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            account = p[Keys.account].orEmpty(),
            passwordEnc = p[Keys.passwordEnc].orEmpty(),
            baseUrl = p[Keys.baseUrl]?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL,
            rememberPassword = p[Keys.rememberPassword] ?: true,
            autoSync = p[Keys.autoSync] ?: true,
            predictiveBack = p[Keys.predictiveBack] ?: false,
            welcomeInitialized = p[Keys.welcomeInitialized] ?: false,
            welcomeCompleted = p[Keys.welcomeCompleted] ?: false,
            welcomeStep = (p[Keys.welcomeStep] ?: 0).coerceIn(0, WelcomeProgress.LAST_STEP),
            termStartEpochDay = p[Keys.termStart] ?: 0L,
            termStartXnm = p[Keys.termStartXnm] ?: 0,
            termStartTerm = p[Keys.termStartTerm] ?: 0,
            lastXnm = p[Keys.lastXnm] ?: 0,
            lastTerm = p[Keys.lastTerm] ?: 0,
            lastSyncAt = p[Keys.lastSyncAt] ?: 0L,
            periodTimesJson = p[Keys.periodTimes].orEmpty(),
            termWeeks = (p[Keys.termWeeks] ?: 16).coerceIn(4, 30),
            showWeekend = p[Keys.showWeekend] ?: true,
            hiddenCourses = p[Keys.hiddenCourses]
                ?.let { raw -> runCatching { json.decodeFromString(HIDDEN_SERIALIZER, raw) }.getOrNull() }
                ?.toSet()
                ?: emptySet(),
            courseMeta = p[Keys.courseMeta]
                ?.let { raw ->
                    runCatching {
                        Json.decodeFromString(metaMapSerializer, raw)
                    }.getOrNull()
                }
                ?: emptyMap(),
            tuitionFee = p[Keys.tuitionFee] ?: 6000,
            customCourses = p[Keys.customCourses]
                ?.let { raw ->
                    runCatching {
                        Json.decodeFromString(ListSerializer(com.fengkao.timetable.data.model.CustomCourse.serializer()), raw)
                    }.getOrNull()
                }
                ?: emptyList(),
            nameOverrides = p[Keys.nameOverrides]
                ?.let { raw ->
                    runCatching {
                        Json.decodeFromString(strMapSerializer, raw)
                    }.getOrNull()
                }
                ?: emptyMap(),
            busyBlocks = p[Keys.busyBlocks]
                ?.let { raw ->
                    runCatching {
                        json.decodeFromString(ListSerializer(BusyBlock.serializer()), raw)
                    }.getOrNull()
                }
                ?: emptyList(),
            skippedUpdateVersion = p[Keys.skippedUpdateVersion].orEmpty(),
            lastUpdateCheckAt = p[Keys.lastUpdateCheckAt] ?: 0L,
        )
    }

    suspend fun first(): AppSettings = flow.first()

    suspend fun initializeWelcome(hasExistingProfile: Boolean) = context.dataStore.edit { p ->
        if (p[Keys.welcomeInitialized] != true) {
            val progress = WelcomeProgress.resolve(false, false, 0, hasExistingProfile)
            p[Keys.welcomeInitialized] = true
            p[Keys.welcomeCompleted] = progress.completed
            p[Keys.welcomeStep] = progress.step
        }
    }

    suspend fun setWelcomeStep(step: Int) = context.dataStore.edit {
        it[Keys.welcomeStep] = step.coerceIn(0, WelcomeProgress.LAST_STEP)
    }

    suspend fun completeWelcome() = context.dataStore.edit {
        it[Keys.welcomeInitialized] = true
        it[Keys.welcomeCompleted] = true
        it[Keys.welcomeStep] = WelcomeProgress.LAST_STEP
    }

    suspend fun restartWelcome() = context.dataStore.edit {
        it[Keys.welcomeInitialized] = true
        it[Keys.welcomeCompleted] = false
        it[Keys.welcomeStep] = 0
    }

    suspend fun saveCredentials(account: String, password: String?, baseUrl: String, remember: Boolean) {
        context.dataStore.edit { p ->
            p[Keys.account] = account
            if (password != null && remember) {
                p[Keys.passwordEnc] = runCatching { Crypto.encrypt(password) }.getOrDefault("")
            } else {
                p.remove(Keys.passwordEnc)
            }
            p[Keys.baseUrl] = baseUrl
            p[Keys.rememberPassword] = remember
        }
    }

    suspend fun setBaseUrl(value: String) = context.dataStore.edit { it[Keys.baseUrl] = value }
    suspend fun setAutoSync(value: Boolean) = context.dataStore.edit { it[Keys.autoSync] = value }
    suspend fun setPredictiveBack(value: Boolean) = context.dataStore.edit { it[Keys.predictiveBack] = value }
    suspend fun setTermStartEpochDay(value: Long, term: TermSpec) = context.dataStore.edit {
        it[Keys.termStart] = value
        it[Keys.termStartXnm] = term.xnm
        it[Keys.termStartTerm] = term.term
    }

    suspend fun setLastSync(xnm: Int, term: Int, at: Long) = context.dataStore.edit {
        val previousRank = (it[Keys.lastXnm] ?: 0) * 3 + (it[Keys.lastTerm] ?: 0)
        val incomingRank = xnm * 3 + term
        if (incomingRank > previousRank || (incomingRank == previousRank && at >= (it[Keys.lastSyncAt] ?: 0L))) {
            if ((it[Keys.termStart] ?: 0L) > 0 && it[Keys.termStartXnm] == null && previousRank > 0) {
                it[Keys.termStartXnm] = it[Keys.lastXnm] ?: 0
                it[Keys.termStartTerm] = it[Keys.lastTerm] ?: 0
            }
            it[Keys.lastXnm] = xnm
            it[Keys.lastTerm] = term
            it[Keys.lastSyncAt] = at
        }
    }

    suspend fun setPeriodTimes(list: List<PeriodTime>) = context.dataStore.edit {
        it[Keys.periodTimes] = json.encodeToString(ListSerializer(PeriodTime.serializer()), list)
    }

    suspend fun setTermWeeks(weeks: Int) = context.dataStore.edit {
        it[Keys.termWeeks] = weeks.coerceIn(4, 30)
    }

    suspend fun setShowWeekend(value: Boolean) = context.dataStore.edit {
        it[Keys.showWeekend] = value
    }

    suspend fun setHiddenCourses(hidden: Set<String>) = context.dataStore.edit {
        if (hidden.isEmpty()) it.remove(Keys.hiddenCourses)
        else it[Keys.hiddenCourses] = json.encodeToString(HIDDEN_SERIALIZER, hidden.toList())
    }

    /** 设置课程标签（类型/学分/硬核） */
    suspend fun setCourseMeta(fullName: String, meta: CourseMeta) = context.dataStore.edit { p ->
        val cur = runCatching {
            Json.decodeFromString(metaMapSerializer, p[Keys.courseMeta] ?: "{}")
        }.getOrNull() ?: emptyMap()
        p[Keys.courseMeta] = json.encodeToString(metaMapSerializer, cur + (fullName to meta))
    }

    /** 添加/删除用户手动课程 */
    suspend fun addCustomCourse(c: com.fengkao.timetable.data.model.CustomCourse) = context.dataStore.edit { p ->
        val cur = runCatching {
            Json.decodeFromString(ListSerializer(com.fengkao.timetable.data.model.CustomCourse.serializer()), p[Keys.customCourses] ?: "[]")
        }.getOrNull() ?: emptyList()
        p[Keys.customCourses] = json.encodeToString(
            ListSerializer(com.fengkao.timetable.data.model.CustomCourse.serializer()),
            cur + c,
        )
    }

    suspend fun removeCustomCourse(c: com.fengkao.timetable.data.model.CustomCourse) = context.dataStore.edit { p ->
        val cur = runCatching {
            Json.decodeFromString(ListSerializer(com.fengkao.timetable.data.model.CustomCourse.serializer()), p[Keys.customCourses] ?: "[]")
        }.getOrNull() ?: emptyList()
        val next = cur.filterNot { it == c }
        if (next.isEmpty()) p.remove(Keys.customCourses)
        else p[Keys.customCourses] = json.encodeToString(
            ListSerializer(com.fengkao.timetable.data.model.CustomCourse.serializer()),
            next,
        )
    }

    /** 设置总学费（元） */
    suspend fun setTuitionFee(fee: Int) = context.dataStore.edit {
        it[Keys.tuitionFee] = fee.coerceIn(0, 1_000_000)
    }

    /** 设置事务占用列表（空闲时间块分析用；空列表则清除 key） */
    suspend fun setBusyBlocks(list: List<BusyBlock>) = context.dataStore.edit {
        if (list.isEmpty()) it.remove(Keys.busyBlocks)
        else it[Keys.busyBlocks] = json.encodeToString(ListSerializer(BusyBlock.serializer()), list)
    }

    /** 设置某门课在桌面 Widget 上的显示简称（short 为空则恢复全称） */
    suspend fun setNameOverride(fullName: String, short: String) = context.dataStore.edit { p ->
        val cur = runCatching {
            Json.decodeFromString(strMapSerializer, p[Keys.nameOverrides] ?: "{}")
        }.getOrNull() ?: emptyMap()
        val next = if (short.isBlank()) cur - fullName else cur + (fullName to short)
        if (next.isEmpty()) p.remove(Keys.nameOverrides)
        else p[Keys.nameOverrides] = json.encodeToString(strMapSerializer, next)
    }

    /** 记住「这个版本不再提示」，下次同版本不再弹窗 */
    suspend fun skipUpdateVersion(version: String) = context.dataStore.edit {
        it[Keys.skippedUpdateVersion] = version
    }

    /** 记录一次检查更新的时间（自动检查的频控用） */
    suspend fun setLastUpdateCheckAt(at: Long) = context.dataStore.edit {
        it[Keys.lastUpdateCheckAt] = at
    }

    suspend fun logout() = context.dataStore.edit {
        it.remove(Keys.account)
        it.remove(Keys.passwordEnc)
        it.remove(Keys.lastXnm)
        it.remove(Keys.lastTerm)
        it.remove(Keys.lastSyncAt)
    }
}
