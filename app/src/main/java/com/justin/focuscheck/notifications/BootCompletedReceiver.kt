package com.justin.focuscheck.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.justin.focuscheck.data.FocusCheckDatabase
import com.justin.focuscheck.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val validAction =
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
                intent.action ==
                Intent.ACTION_MY_PACKAGE_REPLACED

        if (!validAction) {
            return
        }

        val applicationContext =
            context.applicationContext

        NotificationHelper.createNotificationChannel(
            applicationContext
        )

        val pendingResult = goAsync()

        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        ).launch {
            try {
                restoreReminders(
                    context = applicationContext,
                    receivedAction = intent.action
                )
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Failed to restore reminders.",
                    exception
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun restoreReminders(
        context: Context,
        receivedAction: String?
    ) {
        Log.d(
            TAG,
            "Restoring reminders after $receivedAction."
        )

        val settings =
            SettingsRepository(context)
                .settings
                .first()

        val stateRepository =
            ReminderStateRepository(context)

        val runtimeState =
            stateRepository
                .state
                .first()

        ReminderScheduler.cancelAllReminders(
            context
        )

        val settingsValid =
            settings.remindersEnabled &&
                settings.activeWindowIsValid &&
                settings.activeDays.isNotEmpty()

        if (!settingsValid) {
            ReminderCoordinator.stopEverything(
                context = context,
                reason = "Reminder settings were disabled or invalid after restart."
            )

            return
        }

        if (
            !NotificationHelper
                .canPostCheckInNotifications(context)
        ) {
            ReminderCoordinator.stopEverything(
                context = context,
                reason = "Notifications were unavailable after restart."
            )

            return
        }

        val unfinishedTaskCount =
            FocusCheckDatabase
                .getDatabase(context)
                .taskDao()
                .countUnfinishedTasks()

        if (unfinishedTaskCount <= 0) {
            ReminderCoordinator.stopEverything(
                context = context,
                reason = "No unfinished tasks remained after restart."
            )

            return
        }

        val insideActiveWindow =
            ReminderScheduler
                .isInsideActiveWindow(settings)

        if (
            runtimeState.checkInPending &&
            insideActiveWindow
        ) {
            val configuredDelayMillis =
                settings
                    .missedReminderMinutes
                    .toLong()
                    .coerceAtLeast(1L) *
                    60_000L

            val restoredDelayMillis =
                runtimeState
                    .retryDelayMillis
                    ?.coerceAtLeast(60_000L)
                    ?: configuredDelayMillis

            val posted =
                NotificationHelper
                    .showScheduledCheckInNotification(
                        context = context,
                        isTest = false,
                        isRepeat = true
                    )

            if (posted) {
                ReminderScheduler.scheduleRetryReminder(
                    context = context,
                    delayMillis = restoredDelayMillis
                )

                Log.d(
                    TAG,
                    "Pending check-in restored with a retry delay of ${restoredDelayMillis / 60_000L} minute(s)."
                )
            } else {
                ReminderCoordinator.stopEverything(
                    context = context,
                    reason = "The restored pending notification could not be posted."
                )
            }

            return
        }

        if (runtimeState.checkInPending) {
            stateRepository.clearPending()

            NotificationHelper
                .cancelScheduledCheckInNotifications(
                    context
                )
        }

        ReminderScheduler.scheduleNextReminder(
            context = context,
            settings = settings
        )

        Log.d(
            TAG,
            "Normal reminder schedule restored."
        )
    }

    private companion object {
        const val TAG = "FocusCheckBoot"
    }
}
