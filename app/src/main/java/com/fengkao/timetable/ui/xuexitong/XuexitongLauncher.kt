package com.fengkao.timetable.ui.xuexitong

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri

/** 优先打开学习通 App；未安装时退回网页版通知中心。 */
object XuexitongLauncher {

    private const val PACKAGE = "com.chaoxing.mobile"
    private const val WEB_NOTICE = "https://notice.chaoxing.com/pc/notice/myNotice"

    fun open(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse(WEB_NOTICE))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
