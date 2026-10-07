package com.fengkao.timetable.data.local

/** Existing users bypass setup; a new user's saved account does not end an unfinished guide. */
data class WelcomeProgress(val completed: Boolean, val step: Int) {
    companion object {
        const val LAST_STEP = 4

        fun resolve(initialized: Boolean, completed: Boolean, step: Int, hasExistingProfile: Boolean): WelcomeProgress =
            if (initialized) WelcomeProgress(completed, step.coerceIn(0, LAST_STEP))
            else WelcomeProgress(hasExistingProfile, if (hasExistingProfile) LAST_STEP else 0)
    }
}
