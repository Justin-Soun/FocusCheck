package com.justin.focuscheck.settings

data class ReminderSettings(
    val remindersEnabled: Boolean = false,
    val startHour: Int = 8,
    val startMinute: Int = 0,
    val endHour: Int = 18,
    val endMinute: Int = 0,
    val checkInIntervalMinutes: Int = 30,
    val missedReminderMinutes: Int = 10,
    val activeDays: Set<Int> = DEFAULT_ACTIVE_DAYS
) {
    val activeWindowIsValid: Boolean
        get() {
            val startTotalMinutes =
                startHour * 60 + startMinute

            val endTotalMinutes =
                endHour * 60 + endMinute

            return endTotalMinutes > startTotalMinutes
        }

    companion object {
        val DEFAULT_ACTIVE_DAYS = setOf(
            1, // Monday
            2, // Tuesday
            3, // Wednesday
            4, // Thursday
            5  // Friday
        )
    }
}