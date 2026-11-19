package com.example.smartmedicine.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smartmedicine.data.DatabaseHelper
import com.example.smartmedicine.data.Dates
import com.example.smartmedicine.data.Medicine
import com.example.smartmedicine.data.NotificationType
import com.example.smartmedicine.data.SessionManager
import com.example.smartmedicine.data.Status
import com.example.smartmedicine.R
import com.example.smartmedicine.ui.foodLabel
import java.time.LocalDate

/** Receives the alarm at dose time, plus the Taken and Snooze buttons on the notification. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicineId = intent.getLongExtra(ReminderScheduler.EXTRA_MEDICINE_ID, -1L)
        val time = intent.getStringExtra(ReminderScheduler.EXTRA_TIME) ?: return
        if (medicineId == -1L) return

        val db = DatabaseHelper.get(context)
        val medicine = db.getMedicine(medicineId) ?: return

        when (intent.action) {
            ReminderScheduler.ACTION_DOSE_DUE -> {
                val isSnooze = intent.getBooleanExtra(ReminderScheduler.EXTRA_IS_SNOOZE, false)
                onDoseDue(context, db, medicine, time, isSnooze)
            }
            ReminderScheduler.ACTION_TAKEN -> {
                DoseActions.take(context, medicine, LocalDate.now().toString(), time)
            }
            ReminderScheduler.ACTION_SNOOZE -> {
                NotificationHelper.cancelDose(context, medicine.id, time)
                ReminderScheduler.snooze(context, medicine, time)
            }
        }
    }

    private fun onDoseDue(context: Context, db: DatabaseHelper, medicine: Medicine, time: String, isSnooze: Boolean) {
        // Queue tomorrow's alarm first so a problem further down never breaks the chain.
        if (!isSnooze && medicine.repeatDaily) {
            ReminderScheduler.scheduleNext(context, medicine, time)
        }

        if (medicine.status != Status.ACTIVE || !medicine.reminderEnabled) return

        val today = LocalDate.now()
        if (!Dates.isInCourse(medicine, today)) return
        if (db.getDoseStatus(medicine.id, today.toString(), time) != null) return

        if (!isSnooze) {
            db.addNotification(
                medicine.userId,
                context.getString(R.string.notif_dose_title, medicine.name),
                context.getString(R.string.notif_dose_text, medicine.dosage, context.foodLabel(medicine.food)),
                NotificationType.DOSE,
                medicine.id
            )
        }
        if (SessionManager(context).notificationsEnabled(medicine.userId)) {
            NotificationHelper.showDose(context, medicine, time)
        }
    }
}
