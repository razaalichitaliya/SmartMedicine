package com.example.smartmedicine.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.smartmedicine.data.DatabaseHelper
import com.example.smartmedicine.data.Dates
import com.example.smartmedicine.data.Medicine
import com.example.smartmedicine.data.Status
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Sets and cancels the alarms that fire a reminder at each dose time. */
object ReminderScheduler {

    const val ACTION_DOSE_DUE = "com.example.smartmedicine.action.DOSE_DUE"
    const val ACTION_TAKEN = "com.example.smartmedicine.action.DOSE_TAKEN"
    const val ACTION_SNOOZE = "com.example.smartmedicine.action.DOSE_SNOOZE"
    const val EXTRA_MEDICINE_ID = "medicine_id"
    const val EXTRA_TIME = "time"
    const val EXTRA_IS_SNOOZE = "is_snooze"

    /** Stable, unique id for one medicine at one clock time. Used for alarms and notifications. */
    fun requestCode(medicineId: Long, time: String): Int {
        val t = LocalTime.parse(time)
        return (medicineId * 10_000L + t.hour * 100 + t.minute).toInt()
    }

    /** Sets (or replaces) the next alarm for every dose time of this medicine. */
    fun schedule(context: Context, medicine: Medicine) {
        if (medicine.status != Status.ACTIVE || !medicine.reminderEnabled) {
            cancelAlarms(context, medicine)
            return
        }
        for (time in medicine.times) scheduleNext(context, medicine, time)
    }

    /** Sets the next alarm for one dose time: today if it is still ahead, otherwise the next day in the course. */
    fun scheduleNext(context: Context, medicine: Medicine, time: String) {
        val triggerAt = nextTriggerMillis(medicine, time, LocalDateTime.now()) ?: return
        setAlarm(context, triggerAt, dosePendingIntent(context, medicine.id, time, snooze = false))
    }

    /** One-off alarm [Medicine.snoozeMinutes] from now. */
    fun snooze(context: Context, medicine: Medicine, time: String) {
        val triggerAt = System.currentTimeMillis() + medicine.snoozeMinutes * 60_000L
        setAlarm(context, triggerAt, dosePendingIntent(context, medicine.id, time, snooze = true))
    }

    /** Cancels this medicine's pending alarms (including snoozes). */
    fun cancelAlarms(context: Context, medicine: Medicine) {
        val alarms = alarmManager(context)
        for (time in medicine.times) {
            alarms.cancel(dosePendingIntent(context, medicine.id, time, snooze = false))
            alarms.cancel(dosePendingIntent(context, medicine.id, time, snooze = true))
        }
    }

    /** Cancels alarms and removes any reminder notification still on screen. */
    fun cancel(context: Context, medicine: Medicine) {
        cancelAlarms(context, medicine)
        for (time in medicine.times) NotificationHelper.cancelDose(context, medicine.id, time)
    }

    fun rescheduleAll(context: Context, userId: Long) {
        for (medicine in DatabaseHelper.get(context).getMedicines(userId)) schedule(context, medicine)
    }

    fun cancelAll(context: Context, userId: Long) {
        for (medicine in DatabaseHelper.get(context).getMedicines(userId)) cancel(context, medicine)
    }

    /** The next moment this dose should ring, or null once the course is over. */
    fun nextTriggerMillis(medicine: Medicine, time: String, from: LocalDateTime): Long? {
        val doseTime = LocalTime.parse(time)
        var candidate = from.toLocalDate().atTime(doseTime)
        if (!candidate.isAfter(from)) candidate = candidate.plusDays(1)

        val start = Dates.parse(medicine.startDate)
        if (candidate.toLocalDate().isBefore(start)) candidate = start.atTime(doseTime)
        if (candidate.toLocalDate().isAfter(Dates.endDate(medicine))) return null

        return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun dosePendingIntent(context: Context, medicineId: Long, time: String, snooze: Boolean): PendingIntent {
        val code = requestCode(medicineId, time)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DOSE_DUE
            putExtra(EXTRA_MEDICINE_ID, medicineId)
            putExtra(EXTRA_TIME, time)
            putExtra(EXTRA_IS_SNOOZE, snooze)
        }
        return PendingIntent.getBroadcast(
            context,
            if (snooze) code.inv() else code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Exact alarm when Android allows it, otherwise an inexact one that may arrive a few minutes late. */
    private fun setAlarm(context: Context, triggerAt: Long, pendingIntent: PendingIntent) {
        val alarms = alarmManager(context)
        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (canUseExact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }
}
