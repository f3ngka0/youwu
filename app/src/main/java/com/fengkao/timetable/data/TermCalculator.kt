package com.fengkao.timetable.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** 学年 + 学期序号（xnm：如 2025 表示 2025-2026 学年；term：1/2/3） */
data class TermSpec(val xnm: Int, val term: Int) {
    /** Academic order, independent of the calendar year in which a term ends. */
    val rank: Int get() = xnm * 3 + term
    val label: String
        get() = "$xnm-${xnm + 1}学年 第${term}学期"
}

object TermCalculator {
    fun shouldReplaceLatest(existing: TermSpec?, incoming: TermSpec): Boolean =
        existing == null || incoming.rank >= existing.rank

    fun automaticTarget(calendar: TermSpec, cached: TermSpec?): TermSpec =
        cached?.takeIf { it.rank > calendar.rank } ?: calendar

    fun today(): LocalDate = LocalDate.now()

    /**
     * 按校历规则推断“当前”学期：
     * 9月起秋季学期（当年学年第1学期，如 2026-09-01 → 2026-2027 学年第1学期）；
     * 3月起春季学期（上学年第2学期，如 2027-03-05 → 2026-2027 学年第2学期）；
     * 1月–2月（寒假/秋季学期考试周）归上学年秋季学期。
     */
    fun autoTerm(today: LocalDate = today()): TermSpec {
        val month = today.monthValue
        return when {
            month >= 9 -> TermSpec(today.year, 1)
            month >= 3 -> TermSpec(today.year - 1, 2)
            else -> TermSpec(today.year - 1, 1)
        }
    }

    /** 学期起始日默认推算：第1学期=8月末的第一个周一；第2学期=2月末的第一个周一 */
    fun defaultTermStart(xnm: Int, term: Int): LocalDate {
        val anchor = when (term) {
            2 -> LocalDate.of(xnm + 1, 2, 23)
            3 -> LocalDate.of(xnm + 1, 6, 1)
            else -> LocalDate.of(xnm, 8, 26)
        }
        return anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
    }

    /** 今天处于第几周（寒假等超出范围时夹取到 1..30） */
    fun weekOf(termStart: LocalDate, today: LocalDate = today()): Int {
        val days = ChronoUnit.DAYS.between(termStart, today)
        if (days < 0) return 1
        return (days / 7).toInt() + 1
    }

    fun weekStart(termStart: LocalDate, week: Int): LocalDate =
        termStart.plusDays(((week - 1) * 7L).coerceAtLeast(0))

    /** 学年学期下拉可选项 */
    fun selectableTerms(center: TermSpec): List<TermSpec> = buildList {
        for (xnm in center.xnm - 1..center.xnm + 1) {
            add(TermSpec(xnm, 1))
            add(TermSpec(xnm, 2))
        }
    }
}
