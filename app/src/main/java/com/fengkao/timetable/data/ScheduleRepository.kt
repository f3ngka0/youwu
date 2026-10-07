package com.fengkao.timetable.data

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.fengkao.timetable.data.local.CourseChangeStore
import com.fengkao.timetable.data.local.Crypto
import com.fengkao.timetable.data.local.ScheduleCache
import com.fengkao.timetable.data.local.SettingsStore
import com.fengkao.timetable.data.model.ChangeType
import com.fengkao.timetable.data.model.CourseChange
import com.fengkao.timetable.data.model.CachedSchedule
import com.fengkao.timetable.data.model.CourseBlock
import com.fengkao.timetable.data.model.StudentInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * 课表仓库：通过 Chaquopy 调用 new-school-sdk（Python）完成
 * 「无验证码登录 → 拉取课表」，解析后写入本地缓存。
 */
class ScheduleRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val cache: ScheduleCache,
    private val changes: CourseChangeStore,
) {
    private val fetchMutex = Mutex()

    sealed interface SyncOutcome {
        data class Success(val schedule: CachedSchedule) : SyncOutcome
        data class Failure(val message: String) : SyncOutcome
    }

    /**
     * 同步课表。onLog：阶段性进度日志（供登录后的获取进度页展示）。
     * 自动模式（requested == null）下会附带抓取教务系统校历，首次自动填入「第一周的第一天」。
     */
    suspend fun sync(
        requested: TermSpec? = null,
        onLog: (String) -> Unit = {},
    ): SyncOutcome = withContext(Dispatchers.IO) {
        val s = settings.first()
        if (s.account.isBlank()) return@withContext SyncOutcome.Failure("尚未绑定账号")
        val password = runCatching { Crypto.decrypt(s.passwordEnc) }.getOrNull()
            ?: return@withContext SyncOutcome.Failure("本地密码解密失败，请重新登录")

        onLog("启动获取引擎…")
        ensurePython()
        val bridge = Python.getInstance().getModule("schedule_bridge")

        // Saved lastXnm is the last *successful* semester, not the current one. A stale
        // value must never redirect automatic sync to a previous semester.
        val calendarTerm = TermCalculator.autoTerm()
        val cachedTerm = cache.load()?.takeIf { it.courses.isNotEmpty() }?.let { TermSpec(it.xnm, it.term) }
        val automaticTerm = TermCalculator.automaticTarget(calendarTerm, cachedTerm)
        val target = requested ?: automaticTerm.also {
            onLog("当前目标为 ${it.xnm}-${it.xnm + 1} 学年第 ${it.term} 学期")
        }
        // An empty/unavailable current term is a failure. Falling back and committing an
        // older term used to replace the only cache and make the current semester blank.
        var lastError = ""
        for (candidate in listOf(target)) {
            val wantTermStart = requested == null && candidate == target
            onLog("获取 ${candidate.xnm}-${candidate.xnm + 1} 学年第 ${candidate.term} 学期课表…")
            try {
                val raw = fetchMutex.withLock {
                    bridge.callAttr(
                        "fetch_schedule", s.account, password, candidate.xnm, candidate.term, wantTermStart,
                    ).toJava(String::class.java)
                }
                if (raw.isBlank()) throw IllegalStateException("教务系统返回了空课表")
                onLog("解析课程数据…")
                val schedule = parseBridgeJson(raw, s.account, candidate)
                    ?: throw IllegalStateException("课表解析失败")
                if (schedule.courses.isEmpty()) {
                    throw IllegalStateException("该学期暂无课表数据，已保留上次成功获取的课程")
                }

                // 校历开学日期：仅自动模式 + 尚未手动设置过时自动填入「第一周的第一天」
                if (wantTermStart) {
                    val termStartText = runCatching {
                        JSONObject(raw).optString("term_start")
                    }.getOrNull().orEmpty()
                    // 校历文本可能为单数字月/日（如 2026-9-7），分段解析
                    val parts = termStartText.split("-").mapNotNull { it.trim().toIntOrNull() }
                    if (parts.size == 3 && parts.all { it > 0 }) {
                        val day = runCatching {
                            java.time.LocalDate.of(parts[0], parts[1], parts[2]).toEpochDay()
                        }.getOrNull()
                        if (day != null && !s.hasTermStartFor(target)) {
                            settings.setTermStartEpochDay(day, target)
                            onLog("从校历自动填入开学日期 $termStartText")
                        }
                    }
                }

                onLog("检测课程异动并写入缓存…")
                val now = System.currentTimeMillis()
                // 课程异动检测：同一学期且有旧课表时，对比新旧课表记录变化
                val oldCached = cache.load(candidate)
                val detected = if (oldCached != null &&
                    oldCached.xnm == schedule.xnm && oldCached.term == schedule.term &&
                    oldCached.courses.isNotEmpty()
                ) {
                    diffSchedule(oldCached, schedule, now)
                } else {
                    emptyList()
                }
                val isLatest = cache.save(schedule)
                if (isLatest) {
                    changes.save(detected)
                    settings.setLastSync(candidate.xnm, candidate.term, now)
                }
                onLog("完成：共 ${schedule.courses.size} 门课程")
                return@withContext SyncOutcome.Success(schedule)
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                onLog("该学期获取失败：$lastError")
            }
        }
        SyncOutcome.Failure(lastError.ifBlank { "同步失败，请稍后重试" })
    }

    /** 会话校验：直接尝试一次课表同步即可（登录包含在同步流程内） */
    private val pyLock = Any()

    private fun ensurePython() {
        if (Python.isStarted()) return
        // Widget 刷新与 App 同步可能并发到达，Chaquopy 双重 start 会抛 IllegalStateException
        synchronized(pyLock) {
            if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        }
    }

    /**
     * SDK 归一化字段：course/place/teacher/campus/weeks_text/week_day/section/weeks_arr
     */
    private fun parseBridgeJson(raw: String, account: String, term: TermSpec): CachedSchedule? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val items = root.optJSONArray("courses") ?: JSONArray()
        val courses = buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val name = item.optString("course").ifBlank { continue }
                val day = item.optString("week_day").toIntOrNull() ?: item.optInt("week_day", -1)
                if (day !in 1..7) continue
                val weeksArr = item.optJSONArray("weeks_arr") ?: JSONArray()
                val weeks = buildList {
                    for (w in 0 until weeksArr.length()) add(weeksArr.optInt(w))
                }.filter { it > 0 }
                if (weeks.isEmpty()) continue
                val (start, end) = parseSection(item.optString("section").ifBlank { "1" })
                add(
                    CourseBlock(
                        name = name,
                        place = item.optString("place"),
                        teacher = item.optString("teacher"),
                        campus = item.optString("campus"),
                        dayOfWeek = day,
                        startPeriod = start,
                        endPeriod = end,
                        weeks = weeks,
                        weeksText = item.optString("weeks_text"),
                    )
                )
            }
        }.let { blocks ->
            // 同课程同时段条目合并周次
            val merged = LinkedHashMap<String, CourseBlock>()
            for (b in blocks) {
                val key = "${b.name}|${b.dayOfWeek}|${b.startPeriod}|${b.endPeriod}|${b.place}|${b.teacher}"
                val prev = merged[key]
                merged[key] = if (prev == null) b else prev.copy(
                    weeks = (prev.weeks + b.weeks).distinct().sorted(),
                )
            }
            merged.values.sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }))
        }

        val student = StudentInfo(
            name = root.optString("username"),
            studentId = account,
            className = root.optString("class_name"),
        )
        return CachedSchedule(
            xnm = term.xnm,
            term = term.term,
            fetchedAt = System.currentTimeMillis(),
            student = student,
            courses = courses,
        )
    }

    /** 课程异动对比：按课名匹配，检测 新增 / 取消 / 调整（上课时间或教室变化） */
    private fun diffSchedule(old: CachedSchedule, new: CachedSchedule, now: Long): List<CourseChange> {
        fun key(c: CourseBlock) = Triple(c.dayOfWeek, c.startPeriod, c.endPeriod)
        val oldByName = old.courses.groupBy { it.name }
        val newByName = new.courses.groupBy { it.name }
        val out = mutableListOf<CourseChange>()
        fun dowName(d: Int) = "周${"一二三四五六日"[d - 1]}"
        fun CourseBlock.slotDesc() = "${dowName(dayOfWeek)} $startPeriod-${endPeriod}节 · ${place.ifBlank { "待定" }}"

        for ((name, newList) in newByName) {
            val oldList = oldByName[name]
            when {
                oldList == null -> out += CourseChange(
                    type = ChangeType.ADDED, course = name,
                    detail = "新增 ${newList.minByOrNull { it.startPeriod }!!.slotDesc()}",
                    detectedAt = now,
                )
                oldList.map { key(it) to it.place }.toSet() !=
                    newList.map { key(it) to it.place }.toSet() -> {
                    val o = oldList.minByOrNull { it.startPeriod }!!
                    val n = newList.minByOrNull { it.startPeriod }!!
                    out += CourseChange(
                        type = ChangeType.CHANGED, course = name,
                        detail = "${o.slotDesc()} → ${n.slotDesc()}",
                        detectedAt = now,
                    )
                }
            }
        }
        for ((name, oldList) in oldByName) {
            if (name !in newByName) {
                out += CourseChange(
                    type = ChangeType.REMOVED, course = name,
                    detail = "取消 ${oldList.minByOrNull { it.startPeriod }!!.slotDesc()}",
                    detectedAt = now,
                )
            }
        }
        // 排序：调整 > 新增 > 取消，同类按课名
        val order = mapOf(ChangeType.CHANGED to 0, ChangeType.ADDED to 1, ChangeType.REMOVED to 2)
        return out.sortedWith(compareBy({ order[it.type] ?: 3 }, { it.course }))
    }

    /** "1-2" → (1,2)；"3" → (3,3)；"1-2,5-6" 取第一段 */
    private fun parseSection(section: String): Pair<Int, Int> {
        val first = section.split(",", "，").firstOrNull { it.isNotBlank() } ?: "1"
        val nums = Regex("""\d+""").findAll(first).toList()
        return when {
            nums.isEmpty() -> 1 to 1
            nums.size == 1 -> {
                val v = nums[0].value.toIntOrNull() ?: 1
                v to v
            }
            else -> {
                val a = nums[0].value.toIntOrNull() ?: 1
                val b = nums[1].value.toIntOrNull() ?: a
                minOf(a, b) to maxOf(a, b)
            }
        }
    }
}
