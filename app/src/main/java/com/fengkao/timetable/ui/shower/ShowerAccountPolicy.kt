package com.fengkao.timetable.ui.shower

/** 未确认的控制或本人用水尚未结束时，需要保留关水所需的登录凭据。 */
internal fun canLogoutShower(active: Boolean, owner: Boolean, unknown: Boolean, pending: Boolean): Boolean =
    !(active && owner || unknown || pending)
