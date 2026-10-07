package com.fengkao.timetable.ui.profile

internal enum class ProfilePage(val title: String) {
    HOME("我的"), ACCOUNT("个人账户"), PERSONAL_INFO("个人信息"), TEACHING_ACCOUNT("教务系统账户"),
    SCHEDULE("课程表设置"), TERM("学期设置"), PERIODS("上课时间"), COURSES("管理已添加课程"),
    SYNC("同步与数据"), TEACHING_SYNC("教务同步设置"), SERVICES("校园服务账户"), WATER("喝水账户"),
    SHOWER("洗澡账户"), APP("应用设置"), ADVANCED("高级设置"), ABOUT("关于"), LICENSES("开源许可"), INSIGHTS("课程表洞察")
}

internal fun pushProfilePage(stack: List<String>, page: ProfilePage): List<String> =
    if (stack.lastOrNull() == page.name) stack else stack + page.name
internal fun popProfilePage(stack: List<String>): List<String> = if (stack.size > 1) stack.dropLast(1) else listOf(ProfilePage.HOME.name)
internal fun maskAccount(account: String): String = when {
    account.isBlank() -> ""
    account.length <= 4 -> "****"
    account.length <= 8 -> account.take(2) + "****" + account.takeLast(2)
    account.length == 11 -> account.take(3) + "****" + account.takeLast(4)
    else -> account.take(4) + "****" + account.takeLast(4)
}
