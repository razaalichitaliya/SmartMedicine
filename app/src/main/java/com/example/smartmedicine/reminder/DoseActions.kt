package com.example.smartmedicine.reminder

import android.content.Context
import com.example.smartmedicine.R
import com.example.smartmedicine.data.DatabaseHelper
import com.example.smartmedicine.data.DoseStatus
import com.example.smartmedicine.data.Medicine
import com.example.smartmedicine.data.NotificationType
import com.example.smartmedicine.data.SessionManager

object DoseActions {

    /** A refill alert fires when this many doses are left (and again at zero). */
    const val REFILL_THRESHOLD = 3

    /** Logs a dose as taken, updates stock and raises a refill alert when stock runs low. */
    fun take(context: Context, medicine: Medicine, date: String, time: String): Boolean {
        val db = DatabaseHelper.get(context)
        if (!db.markDose(medicine, date, time, DoseStatus.TAKEN)) return false

        NotificationHelper.cancelDose(context, medicine.id, time)

        val updated = db.getMedicine(medicine.id) ?: return true
        if (updated.refillAlert && (updated.stock == REFILL_THRESHOLD || updated.stock == 0)) {
            db.addNotification(
                updated.userId,
                context.getString(R.string.refill_notice_title, updated.name),
                context.getString(R.string.refill_notice_body, updated.stock),
                NotificationType.REFILL,
                updated.id
            )
            if (SessionManager(context).notificationsEnabled(updated.userId)) {
                NotificationHelper.showRefill(context, updated)
            }
        }
        return true
    }
}
