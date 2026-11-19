package com.example.smartmedicine

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import com.example.smartmedicine.data.Dates
import com.example.smartmedicine.data.Medicine
import com.example.smartmedicine.reminder.ReminderScheduler
import com.example.smartmedicine.ui.toast
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate

/** Adds a new medicine, or edits an existing one when EXTRA_MEDICINE_ID is passed. */
class AddMedicineActivity : BaseActivity() {

    companion object {
        const val EXTRA_MEDICINE_ID = "medicine_id"
    }

    private val formChips = linkedMapOf(
        R.id.chipTablet to "TABLET",
        R.id.chipCapsule to "CAPSULE",
        R.id.chipSyrup to "SYRUP",
        R.id.chipInjection to "INJECTION",
        R.id.chipDrops to "DROPS"
    )
    private val slotChips = linkedMapOf(
        R.id.chipMorning to "MORNING",
        R.id.chipAfternoon to "AFTERNOON",
        R.id.chipEvening to "EVENING",
        R.id.chipNight to "NIGHT"
    )
    private val foodChips = linkedMapOf(
        R.id.chipBefore to "BEFORE",
        R.id.chipAfter to "AFTER",
        R.id.chipWith to "WITH",
        R.id.chipAnytime to "ANY"
    )

    private var editing: Medicine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_medicine)
        bindBack()

        val editId = intent.getLongExtra(EXTRA_MEDICINE_ID, -1L)
        if (editId != -1L) {
            val medicine = db.getMedicine(editId)?.takeIf { it.userId == userId }
            if (medicine == null) {
                finish()
                return
            }
            editing = medicine
            prefill(medicine)
        }

        listOf(
            R.id.etMedicineName to R.id.tilMedicineName,
            R.id.etDosage to R.id.tilDosage,
            R.id.etDuration to R.id.tilDuration
        ).forEach { (field, layout) ->
            findViewById<TextInputEditText>(field).doOnTextChanged { _, _, _, _ ->
                findViewById<TextInputLayout>(layout).error = null
            }
        }

        findViewById<View>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<View>(R.id.btnNext).setOnClickListener { save() }
    }

    private fun text(id: Int): String = findViewById<TextView>(id).text.toString().trim()

    private fun setText(id: Int, value: String) {
        findViewById<TextInputEditText>(id).setText(value)
    }

    private fun prefill(m: Medicine) {
        findViewById<TextView>(R.id.tvScreenTitle).setText(R.string.edit_med_title)
        findViewById<TextView>(R.id.btnNext).setText(R.string.btn_save_changes)

        setText(R.id.etMedicineName, m.name)
        setText(R.id.etDosage, m.dosage)
        setText(R.id.etDuration, m.durationDays.toString())
        setText(R.id.etStock, m.stock.toString())
        setText(R.id.etNotes, m.notes)

        for ((id, code) in formChips) {
            if (code == m.form) findViewById<Chip>(id).isChecked = true
        }
        for ((id, code) in foodChips) {
            if (code == m.food) findViewById<Chip>(id).isChecked = true
        }
        for ((id, code) in slotChips) {
            findViewById<Chip>(id).isChecked = code in m.slots
        }
    }

    private fun save() {
        val tilName = findViewById<TextInputLayout>(R.id.tilMedicineName)
        val tilDosage = findViewById<TextInputLayout>(R.id.tilDosage)
        val tilDuration = findViewById<TextInputLayout>(R.id.tilDuration)

        val name = text(R.id.etMedicineName)
        val dosage = text(R.id.etDosage)
        val days = text(R.id.etDuration).toIntOrNull() ?: 0
        val slots = slotChips.filter { (id, _) -> findViewById<Chip>(id).isChecked }.values.toList()

        var valid = true
        if (name.isEmpty()) {
            tilName.error = getString(R.string.err_med_name)
            valid = false
        }
        if (dosage.isEmpty()) {
            tilDosage.error = getString(R.string.err_dosage)
            valid = false
        }
        if (days !in 1..365) {
            tilDuration.error = getString(R.string.err_duration)
            valid = false
        }
        if (slots.isEmpty()) {
            toast(R.string.err_pick_time)
            valid = false
        }
        if (!valid) return

        val form = formChips.entries.firstOrNull { findViewById<Chip>(it.key).isChecked }?.value ?: "TABLET"
        val food = foodChips.entries.firstOrNull { findViewById<Chip>(it.key).isChecked }?.value ?: "ANY"
        val notes = text(R.id.etNotes)
        val stockInput = text(R.id.etStock).toIntOrNull()

        val existing = editing
        if (existing == null) {
            val times = slots.mapNotNull { Dates.defaultSlotTimes[it] }.sorted()
            val medicine = Medicine(
                userId = userId,
                name = name,
                dosage = dosage,
                form = form,
                slots = slots,
                food = food,
                times = times,
                durationDays = days,
                startDate = LocalDate.now().toString(),
                notes = notes,
                // Blank stock means "I have the whole course".
                stock = stockInput ?: (days * times.size)
            )
            val id = db.insertMedicine(medicine)
            ReminderScheduler.schedule(this, medicine.copy(id = id))

            startActivity(
                Intent(this, MedicineReminderActivity::class.java)
                    .putExtra(MedicineReminderActivity.EXTRA_MEDICINE_ID, id)
                    .putExtra(MedicineReminderActivity.EXTRA_NEW, true)
            )
            finish()
        } else {
            // Keep custom dose times unless the time-of-day chips changed.
            val times = if (existing.slots.toSet() == slots.toSet()) {
                existing.times
            } else {
                slots.mapNotNull { Dates.defaultSlotTimes[it] }.sorted()
            }
            val updated = existing.copy(
                name = name,
                dosage = dosage,
                form = form,
                slots = slots,
                food = food,
                times = times,
                durationDays = days,
                notes = notes,
                stock = stockInput ?: existing.stock
            )
            ReminderScheduler.cancel(this, existing)
            db.updateMedicine(updated)
            ReminderScheduler.schedule(this, updated)
            toast(R.string.changes_saved)
            finish()
        }
    }
}
