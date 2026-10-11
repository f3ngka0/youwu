package com.fengkao.timetable.data.shower

/** A pending valve transaction pins its account, even if that login has expired. */
internal fun resolveShowerAccount(primary: ShowerAuth?, secondary: ShowerAuth?, selectedPhone: String,
    controllingPhone: String? = null): ShowerAuth? {
    val accounts = listOfNotNull(primary, secondary).distinctBy { it.phone }
    val phone = controllingPhone ?: selectedPhone
    return if (phone.isBlank()) accounts.firstOrNull() else accounts.firstOrNull { it.phone == phone }
}

fun normalizeShowerAccountNote(note: String): String = note.filterNot(Char::isISOControl).trim().take(24)
fun maskedShowerPhone(phone: String): String =
    if (phone.length == 11) phone.take(3) + "****" + phone.takeLast(4) else phone
fun showerAccountDisplayName(phone: String, note: String?): String =
    normalizeShowerAccountNote(note.orEmpty()).ifBlank { maskedShowerPhone(phone) }
