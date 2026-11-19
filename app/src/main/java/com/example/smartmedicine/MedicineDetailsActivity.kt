package com.example.smartmedicine

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import com.example.smartmedicine.data.Dates
import com.example.smartmedicine.data.Medicine
import com.example.smartmedicine.data.Status
import com.example.smartmedicine.reminder.DoseActions
import com.example.smartmedicine.reminder.ReminderScheduler
import com.example.smartmedicine.ui.DoseState
import com.example.smartmedicine.ui.applyStatus
import com.example.smartmedicine.ui.buildDoseRows
import com.example.smartmedicine.ui.foodLabel
import com.example.smartmedicine.ui.formLabel
import com.example.smartmedicine.ui.slotLabel
import com.example.smartmedicine.ui.statusLabel
import com.example.smartmedicine.ui.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.time.LocalDate
import java.time.LocalTime

class MedicineDetailsActivity : BaseActivity() {

    companion object {
        const val EXTRA_MEDICINE_ID = "medicine_id"
        private const val MENU_EDIT = 1
        private const val MENU_STOP = 2
        private const val MENU_RESTART = 3
        private const val MENU_DELETE = 4
    }

    private var medicineId = -1L
    private var medicine: Medicine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medicine_details)
        bindBack()
        medicineId = intent.getLongExtra(EXTRA_MEDICINE_ID, -1L)

        findViewById<View>(R.id.btnMore).setOnClickListener { showMenu(it) }
        findViewById<View>(R.id.btnEditReminder).setOnClickListener {
            startActivity(
                Intent(this, MedicineReminderActivity::class.java)
                    .putExtra(MedicineReminderActivity.EXTRA_MEDICINE_ID, medicineId)
            )
        }
        findViewById<View>(R.id.btnMarkTaken).setOnClickListener { markNextDoseTaken() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val m = db.getMedicine(medicineId)?.takeIf { it.userId == userId }
        if (m == null) {
            finish()
            return
        }
        medicine = m

        findViewById<TextView>(R.id.tvMedicineName).text = m.name
        findViewById<TextView>(R.id.tvMedicineSub).text = formLabel(m.form)

        val statusView = findViewById<TextView>(R.id.tvStatus)
        statusView.text = statusLabel(m.status)
        statusView.applyStatus(
            when (m.status) {
                Status.COMPLETED -> "blue"
                Status.STOPPED -> "red"
                else -> "green"
            }
        )

        findViewById<TextView>(R.id.tvDosage).text = m.dosage
        findViewById<TextView>(R.id.tvDuration).text =
            resources.getQuantityString(R.plurals.days, m.durationDays, m.durationDays)
        findViewById<TextView>(R.id.tvFrequency).text = getString(R.string.freq_fmt, m.times.size)

        findViewById<TextView>(R.id.tvTiming).text =
            if (m.slots.isEmpty()) getString(R.string.value_none) else m.slots.joinToString(", ") { slotLabel(it) }
        findViewById<TextView>(R.id.tvFood).text = foodLabel(m.food)
        findViewById<TextView>(R.id.tvReminder).text =
            if (m.reminderEnabled) m.times.joinToString(", ") { Dates.formatTime(it) } else getString(R.string.reminder_off)
        findViewById<TextView>(R.id.tvStock).text =
            resources.getQuantityString(R.plurals.doses, m.stock, m.stock)

        val today = LocalDate.now()
        val finished = m.status == Status.COMPLETED || today.isAfter(Dates.endDate(m))
        val day = Dates.dayNumber(m, today)
        findViewById<TextView>(R.id.tvProgressText).text =
            if (finished) getString(R.string.progress_done) else getString(R.string.progress_fmt, day, m.durationDays)
        findViewById<LinearProgressIndicator>(R.id.progressCourse)
            .setProgressCompat(if (finished) 100 else day * 100 / m.durationDays, true)

        findViewById<TextView>(R.id.tvNotes).text =
            if (m.notes.isBlank()) getString(R.string.no_notes) else m.notes

        findViewById<View>(R.id.btnMarkTaken).visibility =
            if (m.status == Status.ACTIVE) View.VISIBLE else View.GONE
    }

    private fun showMenu(anchor: View) {
        val m = medicine ?: return
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_EDIT, 0, R.string.menu_edit)
        if (m.status == Status.ACTIVE) {
            popup.menu.add(0, MENU_STOP, 1, R.string.menu_stop)
        } else {
            popup.menu.add(0, MENU_RESTART, 1, R.string.menu_restart)
        }
        popup.menu.add(0, MENU_DELETE, 2, R.string.menu_delete)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_EDIT -> startActivity(
                    Intent(this, AddMedicineActivity::class.java)
                        .putExtra(AddMedicineActivity.EXTRA_MEDICINE_ID, m.id)
                )
                MENU_STOP -> stopMedicine(m)
                MENU_RESTART -> restartMedicine(m)
                MENU_DELETE -> confirmDelete(m)
            }
            true
        }
        popup.show()
    }

    private fun stopMedicine(m: Medicine) {
        db.setStatus(m.id, Status.STOPPED)
        ReminderScheduler.cancel(this, m)
        toast(R.string.stopped_toast)
        load()
    }

    private fun restartMedicine(m: Medicine) {
        val restarted = m.copy(startDate = LocalDate.now().toString(), status = Status.ACTIVE)
        db.updateMedicine(restarted)
        ReminderScheduler.schedule(this, restarted)
        toast(R.string.restarted_toast)
        load()
    }

    private fun confirmDelete(m: Medicine) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dlg_delete_med_title)
            .setMessage(R.string.dlg_delete_med_body)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                ReminderScheduler.cancel(this, m)
                db.deleteMedicine(m.id)
                toast(R.string.deleted_toast)
                finish()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /** Logs the earliest dose of this medicine that is due or coming up today. */
    private fun markNextDoseTaken() {
        val m = medicine ?: return
        val today = LocalDate.now()
        val rows = buildDoseRows(
            db.getDosesForDay(userId, today).filter { it.medicine.id == m.id },
            LocalTime.now()
        )
        val next = rows.firstOrNull { it.state == DoseState.DUE || it.state == DoseState.NEXT }
        if (next == null) {
            toast(R.string.no_pending_dose)
            return
        }
        DoseActions.take(this, m, today.toString(), next.item.time)
        toast(R.string.taken_toast)
        load()
    }
}
