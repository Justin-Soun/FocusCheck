package com.justin.focuscheck.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.justin.focuscheck.MainActivity
import com.justin.focuscheck.R

object NotificationHelper {

    const val CHECK_IN_CHANNEL_ID =
        "progress_check_ins"

    const val EXTRA_OPEN_CHECK_IN =
        "com.justin.focuscheck.extra.OPEN_CHECK_IN"

    private const val TAG = "FocusCheckNotify"

    private const val IMMEDIATE_TEST_NOTIFICATION_ID =
        1001

    private const val CHECK_IN_NOTIFICATION_ID =
        2001

    private const val SCHEDULED_TEST_NOTIFICATION_ID =
        2002

    fun createNotificationChannel(
        context: Context
    ) {
        val notificationManager =
            context.getSystemService(
                NotificationManager::class.java
            )

        val channel = NotificationChannel(
            CHECK_IN_CHANNEL_ID,
            "Progress check-ins",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description =
                "Scheduled progress check-ins and repeated reminders"

            enableVibration(true)
        }

        notificationManager.createNotificationChannel(
            channel
        )
    }

    fun hasNotificationPermission(
        context: Context
    ): Boolean {
        return Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun canPostCheckInNotifications(
        context: Context
    ): Boolean {
        createNotificationChannel(context)

        if (!hasNotificationPermission(context)) {
            Log.d(
                TAG,
                "Notifications unavailable: POST_NOTIFICATIONS is not granted."
            )

            return false
        }

        val manager =
            NotificationManagerCompat.from(context)

        if (!manager.areNotificationsEnabled()) {
            Log.d(
                TAG,
                "Notifications unavailable: app notifications are blocked."
            )

            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val platformManager =
                context.getSystemService(
                    NotificationManager::class.java
                )

            val channel =
                platformManager.getNotificationChannel(
                    CHECK_IN_CHANNEL_ID
                )

            if (
                channel != null &&
                channel.importance ==
                NotificationManager.IMPORTANCE_NONE
            ) {
                Log.d(
                    TAG,
                    "Notifications unavailable: the progress check-in channel is blocked."
                )

                return false
            }
        }

        return true
    }

    @SuppressLint("MissingPermission")
    fun showTestNotification(
        context: Context
    ): Boolean {
        if (!canPostCheckInNotifications(context)) {
            return false
        }

        val openAppIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            IMMEDIATE_TEST_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            NotificationCompat.Builder(
                context,
                CHECK_IN_CHANNEL_ID
            )
                .setSmallIcon(
                    R.drawable.ic_notification
                )
                .setContentTitle("Focus Check")
                .setContentText(
                    "Time to report what you are working on."
                )
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            "Time to report what you are working on and what you have accomplished."
                        )
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setCategory(
                    NotificationCompat.CATEGORY_REMINDER
                )
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()

        return notifySafely(
            context = context,
            notificationId =
                IMMEDIATE_TEST_NOTIFICATION_ID,
            notification = notification,
            logDescription = "Immediate test notification"
        )
    }

    @SuppressLint("MissingPermission")
    fun showScheduledCheckInNotification(
        context: Context,
        isTest: Boolean,
        isRepeat: Boolean = false
    ): Boolean {
        if (!canPostCheckInNotifications(context)) {
            return false
        }

        val openCheckInIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtra(
                EXTRA_OPEN_CHECK_IN,
                true
            )
        }

        val requestCode =
            if (isTest) {
                SCHEDULED_TEST_NOTIFICATION_ID
            } else {
                CHECK_IN_NOTIFICATION_ID
            }

        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode,
            openCheckInIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val notificationTitle = when {
            isTest ->
                "Focus Check alarm test"

            isRepeat ->
                "Check-in still waiting"

            else ->
                "Progress check-in"
        }

        val notificationText =
            if (isRepeat) {
                "You still need to report what you are working on."
            } else {
                "What are you working on right now?"
            }

        val notification =
            NotificationCompat.Builder(
                context,
                CHECK_IN_CHANNEL_ID
            )
                .setSmallIcon(
                    R.drawable.ic_notification
                )
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            if (isRepeat) {
                                "Your progress check-in has not been completed. Open Focus Check and report your current task."
                            } else {
                                "Open Focus Check and report what you are working on, what you accomplished, and whether you are switching tasks."
                            }
                        )
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setCategory(
                    NotificationCompat.CATEGORY_REMINDER
                )
                .setContentIntent(contentIntent)
                .addAction(
                    R.drawable.ic_notification,
                    "CHECK IN",
                    contentIntent
                )
                .setOnlyAlertOnce(false)
                .setOngoing(!isTest)
                .setAutoCancel(isTest)
                .build()

        val notificationId =
            if (isTest) {
                SCHEDULED_TEST_NOTIFICATION_ID
            } else {
                CHECK_IN_NOTIFICATION_ID
            }

        return notifySafely(
            context = context,
            notificationId = notificationId,
            notification = notification,
            logDescription = when {
                isTest -> "Scheduled test notification"
                isRepeat -> "Repeated check-in notification"
                else -> "Initial check-in notification"
            }
        )
    }

    fun cancelScheduledCheckInNotifications(
        context: Context
    ) {
        val manager =
            NotificationManagerCompat.from(context)

        manager.cancel(
            CHECK_IN_NOTIFICATION_ID
        )

        manager.cancel(
            SCHEDULED_TEST_NOTIFICATION_ID
        )

        Log.d(
            TAG,
            "Scheduled check-in notifications were canceled."
        )
    }

    private fun notifySafely(
        context: Context,
        notificationId: Int,
        notification: android.app.Notification,
        logDescription: String
    ): Boolean {
        return try {
            NotificationManagerCompat
                .from(context)
                .notify(
                    notificationId,
                    notification
                )

            Log.d(
                TAG,
                "$logDescription posted with ID $notificationId."
            )

            true
        } catch (exception: SecurityException) {
            Log.e(
                TAG,
                "$logDescription could not be posted because notification permission changed.",
                exception
            )

            false
        } catch (exception: RuntimeException) {
            Log.e(
                TAG,
                "$logDescription could not be posted.",
                exception
            )

            false
        }
    }
}
