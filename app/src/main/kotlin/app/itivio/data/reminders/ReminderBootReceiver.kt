package app.itivio.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import app.itivio.domain.ReminderScheduler
import org.koin.core.context.GlobalContext

/**
 * Sets the stored reminder again after a reboot, which clears every alarm. The scheduler comes from
 * the Koin graph, because one `settings` DataStore instance serves the whole process.
 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val scheduler = GlobalContext.getOrNull()?.get<ReminderScheduler>() ?: return
        // The re-arm reads the stored reminder, so it outlives this callback.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                scheduler.restore()
            } finally {
                pending.finish()
            }
        }
    }
}
