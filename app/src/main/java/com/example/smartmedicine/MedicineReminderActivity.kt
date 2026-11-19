package com.example.smartmedicine

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.TextView
import android.widget.TimePicker
import com.example.smartmedicine.data.Dates
import com.example.smartmedicine.data.Medicine
import com.example.smartmedicine.reminder.ReminderScheduler
import com.example.smartmedicine.ui.foodLabel
import com.example.smartmedicine.ui.toast
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import java.time.LocalTime

/** Sets the dose times and reminder options. Opened right after adding a medicine, or from Details. */
class MedicineReminderActivity : BaseActivity() {

    companion object {
        const val EXTRA_MEDICINE_ID = "medicine_id"
        const val EXTRA_NEW = "is_new"
        private const val MAX_TIMES = 12
    }

    private lateinit var medicine: Medicine
    private val times = mutableListOf<String>()
    private val snoozeChips = linkedMapOf(
        R.id.chipSnooze5 to 5,
        R.id.chipSnooze10 to 10,
        R.id.chipSnooze15 to 15
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medicine_reminder)
        bindBack()

        val loaded = db.getMedicine(intent.getLongExtra(EXTRA_MEDICINE_ID, -1L))?.takeIf { it.userId == userId }
        if (loaded == null) {
            finish()
            return
        }
        medicine = loaded
        times.addAll(loaded.times)

        findViewById<TextView>(R.id.tvMedicineName).text = loaded.name
        findViewById<TextView>(R.id.tvMedicineSub).text = getString(R.string.dose_sub, loaded.dosage, foodLabel(loaded.food))

        val picker = findViewById<TimePicker>(R.id.timePicker)
        picker.setIs24HourView(DateFormat.is24HourFormat(this))
        val initial = if (times.isNotEmpty()) LocalTime.parse(times.first()) else LocalTime.now()
        picker.hour = initial.hour
        picker.minute = initial.minute

        findViewById<MaterialSwitch>(R.id.switchReminder).isChecked = loaded.reminderEnabled
        findViewById<MaterialSwitch>(R.id.switchRepeat).isChecked = loaded.repeatDaily
        findViewById<MaterialSwitch>(R.id.switchVibrate).isChecked = loaded.vibrate
        findViewById<MaterialSwitch>(R.id.switchRefill).isChecked = loaded.refillAlert
        for ((id, minutes) in snoozeChips) {
            if (minutes == loaded.snoozeMinutes) findViewById<Chip>(id).isChecked = true
        }

        renderTimes()

        findViewById<View>(R.id.btnAddTime).setOnClickListener {
            val key = Dates.timeKey(picker.hour, picker.minute)
            when {
                key in times -> toast(R.string.time_exists)
                times.size >= MAX_TIMES -> toast(R.string.max_times)
                else -> {
                    times.add(key)
                    renderTimes()
                }
            }
        }
        findViewById<View>(R.id.btnSaveReminder).setOnClickListener { save() }
    }

    private fun renderTimes() {
        val group = findViewById<ChipGroup>(R.id.chipGroupTimes)
        group.removeAllViews()
        for (time in times.sorted()) {
            val chip = layoutInflater.inflate(R.layout.chip_time, group, false) as Chip
            chip.text = Dates.formatTime(time)
            chip.setOnCloseIconClickListener {
                if (times.size > 1) {
                    times.remove(time)
                    renderTimes()
                } else {
                    toast(R.string.err_min_one_time)
                }
            }
            group.addView(chip)
        }
    }

    private fun save() {
        val snooze = snoozeChips.entries
            .firstOrNull { findViewById<Chip>(it.key).isChecked }?.value ?: 10

        val updated = medicine.copy(
            times = times.sorted(),
            reminderEnabled = findViewById<MaterialSwitch>(R.id.switchReminder).isChecked,
            repeatDaily = findViewById<MaterialSwitch>(R.id.switchRepeat).isChecked,
            vibrate = findViewById<MaterialSwitch>(R.id.switchVibrate).isChecked,
            refillAlert = findViewById<MaterialSwitch>(R.id.switchRefill).isChecked,
            snoozeMinutes = snooze
        )

        // Cancel using the OLD times, then set alarms for the new ones.
        ReminderScheduler.cancel(this, medicine)
        db.updateMedicine(updated)
        ReminderScheduler.schedule(this, updated)
        toast(R.string.reminder_saved)

        if (intent.getBooleanExtra(EXTRA_NEW, false)) {
            startActivity(
                Intent(this, MedicineDetailsActivity::class.java)
                    .putExtra(MedicineDetailsActivity.EXTRA_MEDICINE_ID, medicine.id)
            )
        }
        finish()
    }
}
