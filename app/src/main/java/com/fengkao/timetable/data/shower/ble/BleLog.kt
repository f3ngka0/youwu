package com.fengkao.timetable.data.shower.ble

/** Avoid logging device frames, account tokens or transaction data. */
internal object BleLog {
    fun i(message: String) = Unit
    fun w(message: String) = Unit
    fun e(message: String, cause: Throwable?) = Unit
}
