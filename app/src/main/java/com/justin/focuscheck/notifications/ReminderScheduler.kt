package com.justin.focuscheck.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.justin.focuscheck.settings.ReminderSettings
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

object ReminderScheduler {

    const val EXTRA_IS_TEST =
        "com.justin.focuscheck.extra.IS_TEST"

    const val EXTRA_IS_RETRY =
        "com.justin.focuscheck.extra.IS_RETRY"

    private const val TAG = "FocusCheckAlarm"

    private const val REGULAR_REQUEST_CODE = 3001
    private const val TEST_REQUEST_CODE = 3002
    private const val RETRY_REQUEST_CODE = 3003

    private const val ACTION_REGULAR =
        "com.justin.focuscheck.action.REGULAR_REMINDER"

    private const val ACTION_TEST =
        "com.justin.focuscheck.action.TEST_REMINDER"

    private const val ACTION_RETRY =
        "com.justin.focuscheck.action.RETRY_REMINDER"

    private const val MINIMUM_RETRY_DELAY_MILLIS =
        60_000L

    fun scheduleNextReminder(
        context: Context,
        settings: ReminderSettings
    ): Long? {
        cancelRegularReminder(context)

        if (!settingsAreValid(settings)) {
            Log.d(
                TAG,
                "Regular reminder not scheduled: settings are disabled or invalid."
            )

            return null
        }

        val nextReminder =
            calculateNextReminder(
                settings = settings,
                now = ZonedDateTime.now()
            )

        if (nextReminder == null) {
            Log.d(
                TAG,
                "Regular reminder not scheduled: no valid active window was found."
            )

            return null
        }

        val triggerAtMillis =
            nextReminder.toInstant().toEpochMilli()

        scheduleAlarm(
            context = context,
            triggerAtMillis = triggerAtMillis,
            alarmType = AlarmType.REGULAR
        )

        Log.d(
            TAG,
            "Regular reminder scheduled for $nextReminder."
        )

        return triggerAtMillis
    }

    fun scheduleTestReminder(
        context: Context,
        delayMillis: Long = 60_000L
    ): Long {
        cancelTestReminder(context)

        val safeDelayMillis =
            delayMillis.coerceAtLeast(
                MINIMUM_RETRY_DELAY_MILLIS
            )

        val triggerAtMillis =
            System.currentTimeMillis() +
                safeDelayMillis

        scheduleAlarm(
            context = context,
            triggerAtMillis = triggerAtMillis,
            alarmType = AlarmType.TEST
        )

        Log.d(
            TAG,
            "Test reminder scheduled for ${formatTriggerTime(triggerAtMillis)}."
        )

        return triggerAtMillis
    }

    fun scheduleRetryReminder(
        context: Context,
        delayMillis: Long
    ): Long {
        cancelRetryReminder(context)

        val safeDelayMillis =
            delayMillis.coerceAtLeast(
                MINIMUM_RETRY_DELAY_MILLIS
            )

        val triggerAtMillis =
            System.currentTimeMillis() +
                safeDelayMillis

        scheduleAlarm(
            context = context,
            triggerAtMillis = triggerAtMillis,
            alarmType = AlarmType.RETRY
        )

        Log.d(
            TAG,
            "Retry reminder scheduled for " +
                "${formatTriggerTime(triggerAtMillis)} " +
                "after ${safeDelayMillis / 60_000L} minute(s)."
        )

        return triggerAtMillis
    }

    fun cancelRegularReminder(
        context: Context
    ) {
        cancelAlarm(
            context = context,
            alarmType = AlarmType.REGULAR
        )
    }

    fun cancelTestReminder(
        context: Context
    ) {
        cancelAlarm(
            context = context,
            alarmType = AlarmType.TEST
        )
    }

    fun cancelRetryReminder(
        context: Context
    ) {
        cancelAlarm(
            context = context,
            alarmType = AlarmType.RETRY
        )
    }

    fun cancelAllReminders(
        context: Context
    ) {
        cancelRegularReminder(context)
        cancelTestReminder(context)
        cancelRetryReminder(context)

        Log.d(
            TAG,
            "All Focus Check alarms were canceled."
        )
    }

    fun isInsideActiveWindow(
        settings: ReminderSettings,
        now: ZonedDateTime = ZonedDateTime.now()
    ): Boolean {
        if (!settingsAreValid(settings)) {
            return false
        }

        if (
            now.dayOfWeek.value !in
            settings.activeDays
        ) {
            return false
        }

        val startTime =
            now.toLocalDate()
                .atTime(
                    settings.startHour,
                    settings.startMinute
                )
                .atZone(now.zone)

        val endTime =
            now.toLocalDate()
                .atTime(
                    settings.endHour,
                    settings.endMinute
                )
                .atZone(now.zone)

        return !now.isBefore(startTime) &&
            now.isBefore(endTime)
    }

    private fun settingsAreValid(
        settings: ReminderSettings
    ): Boolean {
        return settings.remindersEnabled &&
            settings.activeWindowIsValid &&
            settings.activeDays.isNotEmpty()
    }

    private fun cancelAlarm(
        context: Context,
        alarmType: AlarmType
    ) {
        val alarmManager =
            context.getSystemService(
                AlarmManager::class.java
            )

        alarmManager.cancel(
            createAlarmPendingIntent(
                context = context,
                alarmType = alarmType
            )
        )

        Log.d(
            TAG,
            "${alarmType.logLabel} alarm canceled."
        )
    }

    private fun scheduleAlarm(
        context: Context,
        triggerAtMillis: Long,
        alarmType: AlarmType
    ) {
        val alarmManager =
            context.getSystemService(
                AlarmManager::class.java
            )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            createAlarmPendingIntent(
                context = context,
                alarmType = alarmType
            )
        )
    }

    private fun createAlarmPendingIntent(
        context: Context,
        alarmType: AlarmType
    ): PendingIntent {
        val intent = Intent(
            context,
            ReminderAlarmReceiver::class.java
        ).apply {
            action = alarmType.action

            putExtra(
                EXTRA_IS_TEST,
                alarmType == AlarmType.TEST
            )

            putExtra(
                EXTRA_IS_RETRY,
                alarmType == AlarmType.RETRY
            )
        }

        return PendingIntent.getBroadcast(
            context,
            alarmType.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun calculateNextReminder(
        settings: ReminderSettings,
        now: ZonedDateTime
    ): ZonedDateTime? {
        val zone = now.zone

        val intervalMinutes =
            settings
                .checkInIntervalMinutes
                .toLong()
                .coerceAtLeast(1L)

        for (dayOffset in 0L..7L) {
            val date =
                now.toLocalDate().plusDays(dayOffset)

            if (
                date.dayOfWeek.value !in
                settings.activeDays
            ) {
                continue
            }

            val startTime =
                date.atTime(
                    settings.startHour,
                    settings.startMinute
                ).atZone(zone)

            val endTime =
                date.atTime(
                    settings.endHour,
                    settings.endMinute
                ).atZone(zone)

            if (dayOffset > 0L) {
                return startTime
            }

            if (!now.isAfter(startTime)) {
                return startTime
            }

            if (now.isBefore(endTime)) {
                val minutesSinceStart =
                    Duration
                        .between(startTime, now)
                        .toMinutes()
                        .coerceAtLeast(0L)

                val completedIntervals =
                    minutesSinceStart /
                        intervalMinutes

                val nextOffsetMinutes =
                    (completedIntervals + 1L) *
                        intervalMinutes

                val candidate =
                    startTime.plusMinutes(
                        nextOffsetMinutes
                    )

                if (candidate.isBefore(endTime)) {
                    return candidate
                }
            }
        }

        return null
    }

    private fun formatTriggerTime(
        triggerAtMillis: Long
    ): ZonedDateTime {
        return Instant
            .ofEpochMilli(triggerAtMillis)
            .atZone(ZonedDateTime.now().zone)
    }

    private enum class AlarmType(
        val requestCode: Int,
        val action: String,
        val logLabel: String
    ) {
        REGULAR(
            requestCode = REGULAR_REQUEST_CODE,
            action = ACTION_REGULAR,
            logLabel = "Regular reminder"
        ),

        TEST(
            requestCode = TEST_REQUEST_CODE,
            action = ACTION_TEST,
            logLabel = "Test reminder"
        ),

        RETRY(
            requestCode = RETRY_REQUEST_CODE,
            action = ACTION_RETRY,
            logLabel = "Retry reminder"
        )
    }
}
