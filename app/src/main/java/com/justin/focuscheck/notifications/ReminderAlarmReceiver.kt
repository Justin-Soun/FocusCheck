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

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val applicationContext =
            context.applicationContext

        val isTest =
            intent.getBooleanExtra(
                ReminderScheduler.EXTRA_IS_TEST,
                false
            )

        val isRetry =
            intent.getBooleanExtra(
                ReminderScheduler.EXTRA_IS_RETRY,
                false
            )

        Log.d(
            TAG,
            "Alarm received. Test=$isTest, retry=$isRetry, action=${intent.action}."
        )

        if (isTest) {
            val posted =
                NotificationHelper
                    .showScheduledCheckInNotification(
                        context = applicationContext,
                        isTest = true,
                        isRepeat = false
                    )

            Log.d(
                TAG,
                "Scheduled test notification posted=$posted."
            )

            return
        }

        val pendingResult = goAsync()

        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        ).launch {
            try {
                handleReminderAlarm(
                    context = applicationContext,
                    isRetry = isRetry
                )
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Reminder alarm handling failed.",
                    exception
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReminderAlarm(
        context: Context,
        isRetry: Boolean
    ) {
        val settings =
            SettingsRepository(context)
                .settings
                .first()

        val stateRepository =
            ReminderStateRepository(context)

        val settingsValid =
            settings.remindersEnabled &&
                settings.activeWindowIsValid &&
                settings.activeDays.isNotEmpty()

        if (!settingsValid) {
            ReminderCoordinator.stopEverything(
                context = context,
                reason = "An alarm fired while reminder settings were disabled or invalid."
            )

            return
        }

        if (
            !NotificationHelper
                .canPostCheckInNotifications(context)
        ) {
            ReminderCoordinator.stopEverything(
                context = context,
                reason = "An alarm fired while notifications were unavailable."
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
                reason = "An alarm fired with no unfinished tasks."
            )

            return
        }

        if (
            !ReminderScheduler
                .isInsideActiveWindow(settings)
        ) {
            stateRepository.clearPending()

            ReminderScheduler.cancelRetryReminder(
                context
            )

            NotificationHelper
                .cancelScheduledCheckInNotifications(
                    context
                )

            ReminderScheduler.scheduleNextReminder(
                context = context,
                settings = settings
            )

            Log.d(
                TAG,
                "Alarm skipped outside the active window; next normal reminder scheduled."
            )

            return
        }

        val configuredRetryDelayMillis =
            settings
                .missedReminderMinutes
                .toLong()
                .coerceAtLeast(1L) *
                60_000L

        if (isRetry) {
            handleRetryAlarm(
                context = context,
                stateRepository = stateRepository,
                configuredRetryDelayMillis =
                    configuredRetryDelayMillis,
                settings = settings
            )

            return
        }

        handleRegularAlarm(
            context = context,
            stateRepository = stateRepository,
            configuredRetryDelayMillis =
                configuredRetryDelayMillis,
            settings = settings
        )
    }

    private suspend fun handleRetryAlarm(
        context: Context,
        stateRepository: ReminderStateRepository,
        configuredRetryDelayMillis: Long,
        settings: com.justin.focuscheck.settings.ReminderSettings
    ) {
        val runtimeState =
            stateRepository
                .state
                .first()

        if (!runtimeState.checkInPending) {
            ReminderScheduler.cancelRetryReminder(
                context
            )

            NotificationHelper
                .cancelScheduledCheckInNotifications(
                    context
                )

            ReminderScheduler.scheduleNextReminder(
                context = context,
                settings = settings
            )

            Log.d(
                TAG,
                "Stale retry alarm ignored because no check-in is pending."
            )

            return
        }

        val posted =
            NotificationHelper
                .showScheduledCheckInNotification(
                    context = context,
                    isTest = false,
                    isRepeat = true
                )

        if (!posted) {
            stateRepository.clearPending()

            ReminderScheduler.cancelRetryReminder(
                context
            )

            ReminderScheduler.scheduleNextReminder(
                context = context,
                settings = settings
            )

            Log.d(
                TAG,
                "Retry cycle ended because the notification could not be posted."
            )

            return
        }

        ReminderScheduler.scheduleRetryReminder(
            context = context,
            delayMillis = configuredRetryDelayMillis
        )

        Log.d(
            TAG,
            "Repeated check-in posted; next retry uses the configured interval."
        )
    }

    private suspend fun handleRegularAlarm(
        context: Context,
        stateRepository: ReminderStateRepository,
        configuredRetryDelayMillis: Long,
        settings: com.justin.focuscheck.settings.ReminderSettings
    ) {
        val runtimeState =
            stateRepository
                .state
                .first()

        if (runtimeState.checkInPending) {
            ReminderScheduler.cancelRegularReminder(
                context
            )

            Log.d(
                TAG,
                "Duplicate regular alarm ignored because a check-in is already pending."
            )

            return
        }

        val posted =
            NotificationHelper
                .showScheduledCheckInNotification(
                    context = context,
                    isTest = false,
                    isRepeat = false
                )

        if (!posted) {
            ReminderScheduler.scheduleNextReminder(
                context = context,
                settings = settings
            )

            Log.d(
                TAG,
                "Regular check-in notification was not posted; normal scheduling continued."
            )

            return
        }

        stateRepository.markPending(
            retryDelayMillis =
                configuredRetryDelayMillis
        )

        ReminderScheduler.scheduleRetryReminder(
            context = context,
            delayMillis = configuredRetryDelayMillis
        )

        Log.d(
            TAG,
            "Initial check-in posted and retry cycle started."
        )
    }

    private companion object {
        const val TAG = "FocusCheckAlarm"
    }
}
