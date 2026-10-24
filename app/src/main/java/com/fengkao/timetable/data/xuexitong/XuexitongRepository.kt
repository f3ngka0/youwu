package com.fengkao.timetable.data.xuexitong

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface XuexitongRefresh {
    data class Success(
        val notices: List<XuexitongNotice>,
        val unreadCount: Int,
        val newUnread: List<XuexitongNotice>,
        /** 本次是首次抓取，只建立基线，不推系统通知。 */
        val firstBaseline: Boolean,
    ) : XuexitongRefresh

    data class NeedLogin(val message: String) : XuexitongRefresh
    data class Failure(val message: String) : XuexitongRefresh
    data object NotConfigured : XuexitongRefresh
}

/**
 * 学习通通知仓库：登录态、通知拉取、本地已读与「新通知」判定。
 *
 * 已读状态只在本机维护：通知列表接口没有稳定的已读回写协议，直接改服务端状态
 * 反而可能影响官方 App。因此「全部已读」是本地覆盖，下一次抓取仍以服务端未读
 * 为准，但已被本地标记的通知不会再计入未读。
 */
class XuexitongRepository(context: Context) {

    private val store = XuexitongStore(context)
    private val cookieJar = XuexitongCookieJar(store.cookies()) { store.saveCookies(it) }
    private val api = XuexitongApi(cookieJar = cookieJar)
    private val mutex = Mutex()

    fun phone(): String = store.phone()

    fun hasSession(): Boolean = store.phone().isNotBlank() && store.cookies().isNotBlank()

    fun sessionExpired(): Boolean = store.sessionExpired()

    fun notices(): List<XuexitongNotice> = store.notices()

    fun localReadIds(): Set<String> = store.localReadIds()

    fun unreadCount(): Int {
        val read = store.localReadIds()
        val archived = store.archivedIds()
        return store.notices().count { it.serverUnread && it.id !in read && it.id !in archived }
    }

    fun lastFetchAt(): Long = store.lastFetchAt()

    fun notifyEnabled(): Boolean = store.notifyEnabled()

    fun setNotifyEnabled(value: Boolean) = store.setNotifyEnabled(value)

    fun markRead(id: String) {
        store.markLocallyRead(listOf(id))
    }

    fun markAllRead() {
        store.markLocallyRead(store.notices().map { it.id })
    }

    // ---------------------------------------------------------------- 归档 / 已完成

    fun archivedIds(): Set<String> = store.archivedIds()

    /** 归档一条通知：同时视为已读，否则未读角标会一直挂着一条看不见的通知。 */
    fun archive(id: String) {
        store.setArchived(store.archivedIds() + id)
        store.markLocallyRead(listOf(id))
    }

    fun unarchive(id: String) {
        store.setArchived(store.archivedIds() - id)
    }

    /** 归档「全部已读」：把当前所有未读一次性收进归档，列表立刻清爽。 */
    fun archiveAllRead() {
        val read = store.localReadIds()
        val targets = store.notices()
            .filter { !it.serverUnread || it.id in read }
            .map { it.id }
        if (targets.isEmpty()) return
        store.setArchived(store.archivedIds() + targets)
    }

    fun doneTaskKeys(): Set<String> = store.doneTaskKeys()

    fun setTaskDone(key: String, done: Boolean) {
        val keys = store.doneTaskKeys().toMutableSet()
        if (done) keys += key else keys -= key
        store.setDoneTaskKeys(keys)
    }

    suspend fun login(account: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val name = account.trim()
                if (name.isBlank() || password.isBlank()) {
                    throw IOException("请输入学习通账号和密码")
                }
                val switching = store.phone().isNotBlank() && store.phone() != name
                val previousCookies = cookieJar.dump()
                if (switching) cookieJar.clear()
                val result = api.login(name, password)
                if (!result.ok) {
                    // 换号失败时恢复旧账号的登录态，避免把原有会话一并清掉。
                    if (switching) {
                        cookieJar.load(previousCookies)
                        store.saveCookies(previousCookies)
                    }
                    throw IOException(result.message)
                }
                store.saveLogin(name, password, cookieJar.dump())
                store.setSessionExpired(false)
            }
        }
    }

    suspend fun refresh(): XuexitongRefresh = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!hasSession()) return@withLock XuexitongRefresh.NotConfigured
            try {
                val first = !store.baselineDone()
                // 首次多取一页建立较完整的历史；日常轮询只取第一页，降低风控与流量。
                val fetched = fetchWithRelogin(if (first) 2 else 1)
                val old = store.notices()
                val read = store.localReadIds()
                val merged = mergeNotices(fetched, old, read, first)
                store.saveNotices(merged.notices)
                store.setLastFetchAt(System.currentTimeMillis())
                if (fetched.isNotEmpty()) store.setBaselineDone()
                store.setSessionExpired(false)
                XuexitongRefresh.Success(
                    notices = merged.notices,
                    unreadCount = merged.unreadCount,
                    newUnread = merged.newUnread,
                    firstBaseline = first,
                )
            } catch (e: XuexitongSessionExpired) {
                store.setSessionExpired(true)
                XuexitongRefresh.NeedLogin(e.message ?: "学习通登录已失效，请重新登录")
            } catch (e: Exception) {
                XuexitongRefresh.Failure(e.message ?: "学习通通知抓取失败")
            }
        }
    }

    private fun fetchWithRelogin(pages: Int): List<XuexitongNotice> {
        return try {
            api.fetchNotices(maxPages = pages)
        } catch (first: XuexitongSessionExpired) {
            val phone = store.phone()
            val password = store.password() ?: throw first
            val result = api.login(phone, password)
            if (!result.ok) throw XuexitongSessionExpired("学习通登录已失效，请重新登录")
            store.saveCookies(cookieJar.dump())
            api.fetchNotices(maxPages = pages)
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        mutex.withLock {
            cookieJar.clear()
            store.clear()
        }
    }
}
