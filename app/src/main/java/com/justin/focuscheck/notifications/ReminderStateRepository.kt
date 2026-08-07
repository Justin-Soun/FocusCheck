package com.justin.focuscheck.notifications

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.reminderRuntimeDataStore by preferencesDataStore(
    name = "reminder_runtime_state"
)

data class ReminderRuntimeState(
    val checkInPending: Boolean = false,
    val startedAt: Long? = null,
    val retryDelayMillis: Long? = null
)

class ReminderStateRepository(
    private val context: Context
) {

    private object Keys {
        val CHECK_IN_PENDING =
            booleanPreferencesKey("check_in_pending")

        val STARTED_AT =
            longPreferencesKey("check_in_started_at")

        val RETRY_DELAY_MILLIS =
            longPreferencesKey("retry_delay_millis")
    }

    val state: Flow<ReminderRuntimeState> =
        context.reminderRuntimeDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                ReminderRuntimeState(
                    checkInPending =
                        preferences[Keys.CHECK_IN_PENDING]
                            ?: false,

                    startedAt =
                        preferences[Keys.STARTED_AT],

                    retryDelayMillis =
                        preferences[Keys.RETRY_DELAY_MILLIS]
                )
            }

    suspend fun markPending(
        timestamp: Long = System.currentTimeMillis(),
        retryDelayMillis: Long? = null
    ) {
        val safeRetryDelayMillis =
            retryDelayMillis
                ?.takeIf { it > 0L }
                ?.coerceAtLeast(60_000L)

        context.reminderRuntimeDataStore.edit {
                preferences ->

            preferences[Keys.CHECK_IN_PENDING] = true
            preferences[Keys.STARTED_AT] = timestamp

            if (safeRetryDelayMillis != null) {
                preferences[Keys.RETRY_DELAY_MILLIS] =
                    safeRetryDelayMillis
            } else {
                preferences.remove(
                    Keys.RETRY_DELAY_MILLIS
                )
            }
        }
    }

    suspend fun clearPending() {
        context.reminderRuntimeDataStore.edit {
                preferences ->

            preferences[Keys.CHECK_IN_PENDING] = false
            preferences.remove(Keys.STARTED_AT)
            preferences.remove(
                Keys.RETRY_DELAY_MILLIS
            )
        }
    }
}
