package com.fengkao.timetable.data.local

import android.content.Context
import com.fengkao.timetable.data.model.CourseNote
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * 课程笔记（作业 / 笔记 / 考点）与配图。
 *
 * 配图从相册选进来后立刻复制到 `files/notes/`：系统给的是临时 content:// 授权，
 * 进程重启或原图被删就会失效，直接存 URI 会让笔记变成一堆破图。
 */
class CourseNoteStore(context: Context) {

    private val appContext = context.applicationContext
    private val file: File = File(appContext.filesDir, "course_notes.json")
    private val imageDir: File = File(appContext.filesDir, "notes").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): List<CourseNote> = runCatching {
        if (!file.exists()) return emptyList()
        json.decodeFromString(ListSerializer(CourseNote.serializer()), file.readText())
    }.getOrDefault(emptyList())

    fun forCourse(course: String): List<CourseNote> =
        all().filter { it.course == course }.sortedWith(
            compareBy<CourseNote>(
                // 有提醒且未完成的排前面：那才是「要办的事」
                { if (!it.done && it.remindAt != null) 0 else 1 },
                { it.remindAt ?: Long.MAX_VALUE },
                { -it.updatedAt },
            )
        )

    fun save(notes: List<CourseNote>) {
        runCatching {
            file.writeText(json.encodeToString(ListSerializer(CourseNote.serializer()), notes))
        }
    }

    fun upsert(note: CourseNote) {
        val next = all().toMutableList()
        val index = next.indexOfFirst { it.id == note.id }
        if (index >= 0) next[index] = note else next += note
        save(next)
    }

    fun delete(id: String) {
        val next = all().toMutableList()
        val removed = next.firstOrNull { it.id == id }
        next.removeAll { it.id == id }
        save(next)
        removed?.images?.forEach { path -> runCatching { File(path).delete() } }
    }

    fun setDone(id: String, done: Boolean) {
        upsertOne(id) { it.copy(done = done, updatedAt = System.currentTimeMillis()) }
    }

    fun addImageBytes(bytes: ByteArray): String? = runCatching {
        val target = File(imageDir, "note_${UUID.randomUUID()}.jpg")
        target.writeBytes(bytes)
        target.absolutePath
    }.getOrNull()

    private fun upsertOne(id: String, transform: (CourseNote) -> CourseNote) {
        val note = all().firstOrNull { it.id == id } ?: return
        upsert(transform(note))
    }
}
