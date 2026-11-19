package com.example.smartmedicine.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.example.smartmedicine.MedicineDetailsActivity
import com.example.smartmedicine.R
import com.example.smartmedicine.data.Medicine
import com.example.smartmedicine.ui.foodLabel

object NotificationHelper {

    const val CHANNEL_DOSE = "dose_reminders"
    const val CHANNEL_DOSE_QUIET = "dose_reminders_quiet"
    const val CHANNEL_REFILL = "refill_alerts"

    private const val REFILL_ID_BASE = 1_500_000_000

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val dose = NotificationChannel(
            CHANNEL_DOSE,
            context.getString(R.string.channel_dose),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_dose_desc)
            enableVibration(true)
        }

        val quiet = NotificationChannel(
            CHANNEL_DOSE_QUIET,
            context.getString(R.string.channel_dose_quiet),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_dose_quiet_desc)
            setSound(null, null)
            enableVibration(false)
        }

        val refill = NotificationChannel(
            CHANNEL_REFILL,
            context.getString(R.string.channel_refill),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_refill_desc)
        }

        manager.createNotificationChannels(listOf(dose, quiet, refill))
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun showDose(context: Context, medicine: Medicine, time: String) {
        if (!canPost(context)) return
        val code = ReminderScheduler.requestCode(medicine.id, time)
        val immutableUpdate = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        // Tapping the notification opens Home with the medicine's details on top.
        val detailsIntent = Intent(context, MedicineDetailsActivity::class.java)
            .putExtra(MedicineDetailsActivity.EXTRA_MEDICINE_ID, medicine.id)
        val openPending = TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(detailsIntent)
            .getPendingIntent(code, immutableUpdate)

        val takenPending = actionIntent(context, ReminderScheduler.ACTION_TAKEN, medicine.id, time, code)
        val snoozePending = actionIntent(context, ReminderScheduler.ACTION_SNOOZE, medicine.id, time, code)

        val channel = if (medicine.vibrate) CHANNEL_DOSE else CHANNEL_DOSE_QUIET
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notifications)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle(context.getString(R.string.notif_dose_title, medicine.name))
            .setContentText(context.getString(R.string.notif_dose_text, medicine.dosage, context.foodLabel(medicine.food)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(0, context.getString(R.string.notif_taken), takenPending)
            .addAction(0, context.getString(R.string.notif_snooze), snoozePending)
            .build()

        NotificationManagerCompat.from(context).notify(code, notification)
    }

    @SuppressLint("MissingPermission")
    fun showRefill(context: Context, medicine: Medicine) {
        if (!canPost(context)) return
        val detailsIntent = Intent(context, MedicineDetailsActivity::class.java)
            .putExtra(MedicineDetailsActivity.EXTRA_MEDICINE_ID, medicine.id)
        val id = REFILL_ID_BASE + medicine.id.toInt()
        val openPending = TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(detailsIntent)
            .getPendingIntent(id, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_REFILL)
            .setSmallIcon(R.drawable.ic_notifications)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle(context.getString(R.string.refill_notice_title, medicine.name))
            .setContentText(context.getString(R.string.refill_notice_body, medicine.stock))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancelDose(context: Context, medicineId: Long, time: String) {
        NotificationManagerCompat.from(context).cancel(ReminderScheduler.requestCode(medicineId, time))
    }

    private fun actionIntent(context: Context, action: String, medicineId: Long, time: String, code: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderScheduler.EXTRA_MEDICINE_ID, medicineId)
            .putExtra(ReminderScheduler.EXTRA_TIME, time)
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
