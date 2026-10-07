package com.fengkao.timetable.data.local

import android.content.Context
import com.fengkao.timetable.data.model.CachedSchedule
import com.fengkao.timetable.data.TermSpec
import com.fengkao.timetable.data.TermCalculator
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 课表本地缓存（应用私有目录 JSON 文件），保证离线可看、秒开 */
class ScheduleCache(directory: File) {
    constructor(context: Context) : this(context.filesDir)

    private val file: File = File(directory, "schedule_cache.json")
    private val terms = File(directory, "schedule_terms")
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized fun save(schedule: CachedSchedule): Boolean {
        val encoded = json.encodeToString(CachedSchedule.serializer(), schedule)
        // Preserve the pre-v1.5 single-file cache before replacing it with a new semester.
        val legacy = read(file)
        if (legacy != null) {
            val previousFile = File(terms, "${legacy.xnm}_${legacy.term}.json")
            if (!previousFile.exists()) writeAtomically(previousFile,
                json.encodeToString(CachedSchedule.serializer(), legacy))
        }
        val latest = load()
        val newerOrSame = TermCalculator.shouldReplaceLatest(
            latest?.let { TermSpec(it.xnm, it.term) }, TermSpec(schedule.xnm, schedule.term))
        writeAtomically(File(terms, "${schedule.xnm}_${schedule.term}.json"), encoded)
        if (newerOrSame) writeAtomically(file, encoded)
        return newerOrSame
    }

    @Synchronized fun load(): CachedSchedule? =
        (listOfNotNull(read(file)) + terms.listFiles().orEmpty().mapNotNull(::read))
            .maxWithOrNull(compareBy<CachedSchedule>({ TermSpec(it.xnm, it.term).rank }, { it.fetchedAt }))

    @Synchronized fun load(term: TermSpec): CachedSchedule? =
        read(File(terms, "${term.xnm}_${term.term}.json"))
            ?: load()?.takeIf { it.xnm == term.xnm && it.term == term.term }

    private fun read(target: File): CachedSchedule? = runCatching {
        if (!target.exists()) null else json.decodeFromString(CachedSchedule.serializer(), target.readText())
    }.getOrNull()

    private fun writeAtomically(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(text)
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun clear() {
        runCatching { file.delete() }
        runCatching { terms.deleteRecursively() }
    }
}
