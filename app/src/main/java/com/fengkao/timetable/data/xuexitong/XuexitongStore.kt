package com.fengkao.timetable.data.xuexitong

import android.content.Context
import com.fengkao.timetable.data.local.Crypto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 学习通账号、Cookie 与通知缓存；与教务、喝水、洗澡的凭据完全隔离。 */
class XuexitongStore(context: Context) {

    private val prefs = context.getSharedPreferences("xuexitong_account", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun phone(): String = prefs.getString(KEY_PHONE, "").orEmpty()

    fun password(): String? = prefs.getString(KEY_PASSWORD, null)
        ?.let { runCatching { Crypto.decrypt(it) }.getOrNull() }
        ?.takeIf { it.isNotBlank() }

    fun saveLogin(phone: String, password: String, cookiesJson: String) {
        val changed = phone() != phone
        val editor = prefs.edit()
            .putString(KEY_PHONE, phone)
            .putString(KEY_PASSWORD, runCatching { Crypto.encrypt(password) }.getOrDefault(""))
            .putString(KEY_COOKIES, runCatching { Crypto.encrypt(cookiesJson) }.getOrDefault(""))
            .putBoolean(KEY_SESSION_EXPIRED, false)
        if (changed) {
            editor.remove(KEY_NOTICES)
                .remove(KEY_LOCAL_READ)
                .remove(KEY_ARCHIVED)
                .remove(KEY_DONE_TASKS)
                .remove(KEY_BASELINE_DONE)
                .remove(KEY_LAST_FETCH)
        }
        check(editor.commit()) { "学习通登录信息保存失败，请重试" }
    }

    fun cookies(): String = prefs.getString(KEY_COOKIES, null)
        ?.let { runCatching { Crypto.decrypt(it) }.getOrNull() }
        .orEmpty()

    fun saveCookies(cookiesJson: String) {
        prefs.edit()
            .putString(KEY_COOKIES, runCatching { Crypto.encrypt(cookiesJson) }.getOrDefault(""))
            .apply()
    }

    fun notices(): List<XuexitongNotice> = runCatching {
        json.decodeFromString(
            ListSerializer(XuexitongNotice.serializer()),
            prefs.getString(KEY_NOTICES, "[]").orEmpty(),
        )
    }.getOrDefault(emptyList())

    fun saveNotices(notices: List<XuexitongNotice>) {
        val encoded = runCatching {
            json.encodeToString(ListSerializer(XuexitongNotice.serializer()), notices.take(200))
        }.getOrDefault("[]")
        prefs.edit().putString(KEY_NOTICES, encoded).apply()
    }

    fun localReadIds(): Set<String> =
        prefs.getStringSet(KEY_LOCAL_READ, emptySet()).orEmpty().toSet()

    fun markLocallyRead(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val merged = localReadIds().toMutableSet().apply { addAll(ids) }
        // 只保留仍存在的通知，避免集合无限增长。
        val known = notices().mapTo(mutableSetOf()) { it.id }
        merged.retainAll(known + ids)
        prefs.edit().putStringSet(KEY_LOCAL_READ, merged).apply()
    }

    /** 归档：不再出现在默认列表，但仍保留在「归档」筛选里可还原。 */
    fun archivedIds(): Set<String> = prefs.getStringSet(KEY_ARCHIVED, emptySet()).orEmpty().toSet()

    fun setArchived(ids: Collection<String>) {
        val known = notices().mapTo(mutableSetOf()) { it.id }
        val kept = ids.toSet().filterTo(mutableSetOf()) { it in known }
        prefs.edit().putStringSet(KEY_ARCHIVED, kept).apply()
    }

    /**
     * 已完成的待办：只存合并后待办的稳定 key（同一作业的多条提醒共用一个 key），
     * 这样重新抓取、提醒数量变化都不会把「已完成」状态弄丢。
     */
    fun doneTaskKeys(): Set<String> = prefs.getStringSet(KEY_DONE_TASKS, emptySet()).orEmpty().toSet()

    fun setDoneTaskKeys(keys: Collection<String>) {
        prefs.edit().putStringSet(KEY_DONE_TASKS, keys.toSet()).apply()
    }

    fun baselineDone(): Boolean = prefs.getBoolean(KEY_BASELINE_DONE, false)

    fun setBaselineDone() {
        prefs.edit().putBoolean(KEY_BASELINE_DONE, true).apply()
    }

    fun lastFetchAt(): Long = prefs.getLong(KEY_LAST_FETCH, 0L)

    fun setLastFetchAt(value: Long) {
        prefs.edit().putLong(KEY_LAST_FETCH, value).apply()
    }

    fun notifyEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFY_ENABLED, true)

    fun setNotifyEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_ENABLED, value).apply()
    }

    fun sessionExpired(): Boolean = prefs.getBoolean(KEY_SESSION_EXPIRED, false)

    fun setSessionExpired(value: Boolean) {
        prefs.edit().putBoolean(KEY_SESSION_EXPIRED, value).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_PHONE = "phone"
        const val KEY_PASSWORD = "password_enc"
        const val KEY_COOKIES = "cookies_enc"
        const val KEY_NOTICES = "notices_json"
        const val KEY_LOCAL_READ = "local_read"
        const val KEY_ARCHIVED = "archived_ids"
        const val KEY_DONE_TASKS = "done_task_keys"
        const val KEY_BASELINE_DONE = "baseline_done"
        const val KEY_LAST_FETCH = "last_fetch_at"
        const val KEY_NOTIFY_ENABLED = "notify_enabled"
        const val KEY_SESSION_EXPIRED = "session_expired"
    }
}
