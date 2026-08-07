package com.justin.focuscheck.notifications

import android.content.Context
import android.util.Log
import com.justin.focuscheck.data.FocusCheckDatabase
import com.justin.focuscheck.settings.SettingsRepository
import kotlinx.coroutines.flow.first

object ReminderCoordinator {

    private const val TAG = "FocusCheckReminder"

    suspend fun reconcile(
        context: Context,
        reason: String
    ) {
        val applicationContext =
            context.applicationContext

        NotificationHelper.createNotificationChannel(
            applicationContext
        )

        val settings =
            SettingsRepository(applicationContext)
                .settings
                .first()

        val stateRepository =
            ReminderStateRepository(
                applicationContext
            )

        val runtimeState =
            stateRepository
                .state
                .first()

        val unfinishedTaskCount =
            FocusCheckDatabase
                .getDatabase(applicationContext)
                .taskDao()
                .countUnfinishedTasks()

        Log.d(
            TAG,
            "Reconciling reminders after $reason. " +
                    "Pending=${runtimeState.checkInPending}, " +
                    "unfinishedTasks=$unfinishedTaskCount."
        )

        val settingsValid =
            settings.remindersEnabled &&
                    settings.activeWindowIsValid &&
                    settings.activeDays.isNotEmpty()

        if (!settingsValid) {
            stopEverything(
                context = applicationContext,
                stateRepository = stateRepository,
                reason = "Reminder settings are disabled or invalid."
            )

            return
        }

        if (
            !NotificationHelper
                .canPostCheckInNotifications(
                    applicationContext
                )
        ) {
            stopEverything(
                context = applicationContext,
                stateRepository = stateRepository,
                reason = "Notifications are unavailable."
            )

            return
        }

        if (unfinishedTaskCount <= 0) {
            stopEverything(
                context = applicationContext,
                stateRepository = stateRepository,
                reason = "There are no unfinished tasks."
            )

            return
        }

        if (
            !ReminderScheduler
                .isInsideActiveWindow(settings)
        ) {
            if (runtimeState.checkInPending) {
                stateRepository.clearPending()
            }

            ReminderScheduler.cancelRetryReminder(
                applicationContext
            )

            NotificationHelper
                .cancelScheduledCheckInNotifications(
                    applicationContext
                )

            ReminderScheduler.scheduleNextReminder(
                context = applicationContext,
                settings = settings
            )

            Log.d(
                TAG,
                "Pending state cleared outside the active window; the next normal reminder was scheduled."
            )

            return
        }

        if (runtimeState.checkInPending) {
            ReminderScheduler.cancelRegularReminder(
                applicationContext
            )

            Log.d(
                TAG,
                "An unresolved check-in is active. Its existing retry alarm was preserved."
            )

            return
        }

        ReminderScheduler.cancelRetryReminder(
            applicationContext
        )

        NotificationHelper
            .cancelScheduledCheckInNotifications(
                applicationContext
            )

        ReminderScheduler.scheduleNextReminder(
            context = applicationContext,
            settings = settings
        )
    }

    suspend fun acknowledgeCheckIn(
        context: Context,
        hasUnfinishedTasks: Boolean? = null,
        reason: String = "Check-in acknowledged"
    ) {
        val applicationContext =
            context.applicationContext

        val stateRepository =
            ReminderStateRepository(
                applicationContext
            )

        stateRepository.clearPending()

        ReminderScheduler.cancelRetryReminder(
            applicationContext
        )

        NotificationHelper
            .cancelScheduledCheckInNotifications(
                applicationContext
            )

        val settings =
            SettingsRepository(applicationContext)
                .settings
                .first()

        val unfinishedTaskCount =
            hasUnfinishedTasks?.let {
                if (it) 1 else 0
            } ?: FocusCheckDatabase
                .getDatabase(applicationContext)
                .taskDao()
                .countUnfinishedTasks()

        val canScheduleNext =
            unfinishedTaskCount > 0 &&
                    settings.remindersEnabled &&
                    settings.activeWindowIsValid &&
                    settings.activeDays.isNotEmpty() &&
                    NotificationHelper
                        .canPostCheckInNotifications(
                            applicationContext
                        )

        if (canScheduleNext) {
            ReminderScheduler.scheduleNextReminder(
                context = applicationContext,
                settings = settings
            )
        } else {
            ReminderScheduler.cancelRegularReminder(
                applicationContext
            )
        }

        Log.d(
            TAG,
            "$reason. Next regular reminder scheduled=$canScheduleNext."
        )
    }

    suspend fun stopEverything(
        context: Context,
        reason: String
    ) {
        stopEverything(
            context = context.applicationContext,
            stateRepository = ReminderStateRepository(
                context.applicationContext
            ),
            reason = reason
        )
    }

    private suspend fun stopEverything(
        context: Context,
        stateRepository: ReminderStateRepository,
        reason: String
    ) {
        stateRepository.clearPending()

        ReminderScheduler.cancelAllReminders(
            context
        )

        NotificationHelper
            .cancelScheduledCheckInNotifications(
                context
            )

        Log.d(
            TAG,
            "All reminder activity stopped: $reason"
        )
    }
}