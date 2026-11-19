package com.example.smartmedicine.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smartmedicine.data.SessionManager

/** Alarms are wiped when the phone restarts or the app is updated, so set them up again. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val session = SessionManager(context)
        if (session.isLoggedIn) {
            ReminderScheduler.rescheduleAll(context, session.userId)
        }
    }
}
