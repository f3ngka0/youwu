package com.fengkao.timetable.data.xuexitong

internal data class NoticeMergeResult(
    val notices: List<XuexitongNotice>,
    val newUnread: List<XuexitongNotice>,
    val unreadCount: Int,
)

/**
 * 把「本次抓取」与「本地历史」合并，找出需要推送的新未读。
 * 首次抓取只建立基线，不把历史未读当成新通知。
 */
internal fun mergeNotices(
    fetched: List<XuexitongNotice>,
    old: List<XuexitongNotice>,
    locallyRead: Set<String>,
    firstBaseline: Boolean,
    limit: Int = 200,
): NoticeMergeResult {
    val oldIds = old.mapTo(mutableSetOf()) { it.id }
    val fetchedIds = fetched.mapTo(mutableSetOf()) { it.id }
    val merged = (fetched + old.filter { it.id !in fetchedIds })
        .sortedWith(compareByDescending<XuexitongNotice> { it.timeMillis }.thenBy { it.id })
        .take(limit)
    val newUnread = if (firstBaseline) {
        emptyList()
    } else {
        fetched.filter { it.serverUnread && it.id !in oldIds && it.id !in locallyRead }
    }
    return NoticeMergeResult(
        notices = merged,
        newUnread = newUnread,
        unreadCount = merged.count { it.serverUnread && it.id !in locallyRead },
    )
}
