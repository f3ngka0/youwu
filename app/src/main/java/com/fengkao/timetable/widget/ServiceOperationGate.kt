package com.fengkao.timetable.widget

import kotlinx.coroutines.sync.Mutex

/** Every foreground entry point shares a non-queuing control gate. */
internal object ServiceOperationGate {
    val water = Mutex()
    val shower = Mutex()
}
