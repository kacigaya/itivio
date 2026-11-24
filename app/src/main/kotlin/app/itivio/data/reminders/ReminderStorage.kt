package app.itivio.data.reminders

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.itivio.domain.Reminder
import java.time.Instant
import kotlinx.coroutines.flow.first

/** The reminder that the app keeps across process death and a reboot, with the text of its notification. */
internal data class StoredReminder(val reminder: Reminder, val text: String)

/**
 * The stored reminder. It stays in the `settings` DataStore file, which needs no schema and no
 * migration: a file without these keys holds no reminder, and a damaged file reads as no reminder.
 */
internal interface ReminderStorage {
    suspend fun read(): StoredReminder?

    suspend fun save(stored: StoredReminder)

    suspend fun clear()
}

internal class DataStoreReminderStorage(private val store: DataStore<Preferences>) : ReminderStorage {
    override suspend fun read(): StoredReminder? {
        val preferences = store.data.first()
        val journeyId = preferences[REMINDER_JOURNEY] ?: return null
        val at = preferences[REMINDER_AT] ?: return null
        val lead = preferences[REMINDER_LEAD] ?: return null
        return StoredReminder(Reminder(journeyId, lead, Instant.ofEpochMilli(at)), preferences[REMINDER_TEXT].orEmpty())
    }

    override suspend fun save(stored: StoredReminder) {
        store.edit {
            it[REMINDER_JOURNEY] = stored.reminder.journeyId
            it[REMINDER_AT] = stored.reminder.at.toEpochMilli()
            it[REMINDER_LEAD] = stored.reminder.leadMinutes
            it[REMINDER_TEXT] = stored.text
        }
    }

    override suspend fun clear() {
        store.edit {
            it.remove(REMINDER_JOURNEY)
            it.remove(REMINDER_AT)
            it.remove(REMINDER_LEAD)
            it.remove(REMINDER_TEXT)
        }
    }
}

private val REMINDER_JOURNEY = stringPreferencesKey("reminder_journey")
private val REMINDER_AT = longPreferencesKey("reminder_at")
private val REMINDER_LEAD = intPreferencesKey("reminder_lead")
private val REMINDER_TEXT = stringPreferencesKey("reminder_text")
