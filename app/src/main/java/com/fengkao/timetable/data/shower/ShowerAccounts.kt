package com.fengkao.timetable.data.shower

/** A pending valve transaction pins its account, even if that login has expired. */
internal fun resolveShowerAccount(primary: ShowerAuth?, secondary: ShowerAuth?, selectedPhone: String,
    controllingPhone: String? = null): ShowerAuth? {
    val accounts = listOfNotNull(primary, secondary).distinctBy { it.phone }
    val phone = controllingPhone ?: selectedPhone
    return if (phone.isBlank()) accounts.firstOrNull() else accounts.firstOrNull { it.phone == phone }
}
