package com.fengkao.timetable.data.xuexitong

import kotlinx.serialization.Serializable

@Serializable
enum class NoticeCategory(val label: String) {
    HOMEWORK("作业"),
    SIGNIN("签到"),
    ANNOUNCEMENT("公告"),
    OTHER("通知"),
}

/** 学习通通知中心的一条通知；正文为纯文本，列表接口已经返回全文。 */
@Serializable
data class XuexitongNotice(
    val id: String,
    val title: String,
    val content: String = "",
    val sender: String = "",
    val timeText: String = "",
    val timeMillis: Long = 0L,
    val serverUnread: Boolean = true,
    val category: NoticeCategory = NoticeCategory.OTHER,
    val deadline: String? = null,
    /** 学习通附件：作业/考试标题，或云盘文件名。 */
    val attachmentName: String = "",
    val attachmentUrl: String = "",
    val attachmentKind: String = "",
    val attachmentSize: String = "",
)
