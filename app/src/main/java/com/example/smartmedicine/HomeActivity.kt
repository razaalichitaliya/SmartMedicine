package com.example.smartmedicine

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartmedicine.data.Dates
import com.example.smartmedicine.data.Medicine
import com.example.smartmedicine.data.Status
import com.example.smartmedicine.data.User
import com.example.smartmedicine.reminder.DoseActions
import com.example.smartmedicine.reminder.ReminderScheduler
import com.example.smartmedicine.ui.DoseAdapter
import com.example.smartmedicine.ui.DoseRow
import com.example.smartmedicine.ui.DoseState
import com.example.smartmedicine.ui.buildDoseRows
import com.example.smartmedicine.ui.foodLabel
import com.example.smartmedicine.ui.initialsOf
import com.example.smartmedicine.ui.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class HomeActivity : BaseActivity() {

    private lateinit var doseAdapter: DoseAdapter
    private var nextRow: DoseRow? = null
    private var lowStockMedicine: Medicine? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { askForExactAlarms() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!session.isLoggedIn) {
            goToLogin()
            return
        }
        setContentView(R.layout.activity_home)

        doseAdapter = DoseAdapter { row -> openDetails(row.item.medicine.id) }
        findViewById<RecyclerView>(R.id.rvSchedule).apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = doseAdapter
            isNestedScrollingEnabled = false
        }

        findViewById<View>(R.id.btnAddMedicine).setOnClickListener { openScreen(AddMedicineActivity::class.java) }
        findViewById<View>(R.id.btnPrescription).setOnClickListener { openScreen(PrescriptionActivity::class.java) }
        findViewById<View>(R.id.btnHistory).setOnClickListener { openScreen(HistoryActivity::class.java) }
        findViewById<View>(R.id.btnNotifications).setOnClickListener { openScreen(NotificationsActivity::class.java) }
        findViewById<View>(R.id.btnProfile).setOnClickListener { openScreen(ProfileActivity::class.java) }

        findViewById<View>(R.id.btnRefill).setOnClickListener {
            val low = lowStockMedicine
            if (low != null) openDetails(low.id) else toast(R.string.no_refill_needed)
        }
        findViewById<View>(R.id.cardRefill).setOnClickListener {
            lowStockMedicine?.let { openDetails(it.id) }
        }
        findViewById<View>(R.id.cardMedicine).setOnClickListener {
            nextRow?.let { openDetails(it.item.medicine.id) }
        }

        findViewById<View>(R.id.btnTakeNow).setOnClickListener {
            val row = nextRow ?: return@setOnClickListener
            DoseActions.take(this, row.item.medicine, LocalDate.now().toString(), row.item.time)
            toast(R.string.taken_toast)
            refresh()
        }
        findViewById<View>(R.id.btnSnooze).setOnClickListener {
            val row = nextRow ?: return@setOnClickListener
            ReminderScheduler.snooze(this, row.item.medicine, row.item.time)
            toast(getString(R.string.snoozed_toast, row.item.medicine.snoozeMinutes))
        }

        // Make sure every alarm exists (cheap and safe to repeat), then ask for the permissions reminders need.
        ReminderScheduler.rescheduleAll(this, userId)
        ensureReminderPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (!session.isLoggedIn) return
        refresh()
    }

    private fun openScreen(target: Class<*>) {
        startActivity(Intent(this, target))
    }

    private fun openDetails(medicineId: Long) {
        startActivity(
            Intent(this, MedicineDetailsActivity::class.java)
                .putExtra(MedicineDetailsActivity.EXTRA_MEDICINE_ID, medicineId)
        )
    }

    // ------------------------------------------------------------------
    //  Screen content
    // ------------------------------------------------------------------

    private fun refresh() {
        val user = db.getUser(userId)
        if (user == null) {
            session.logout()
            goToLogin()
            return
        }

        // Courses whose last day has passed move to History and stop ringing.
        for (finished in db.completeFinishedCourses(userId)) {
            ReminderScheduler.cancel(this, finished)
        }

        val now = LocalTime.now()
        val rows = buildDoseRows(db.getDosesForDay(userId, LocalDate.now()), now)
        doseAdapter.submit(rows)

        bindHeader(user)
        bindHero(rows)
        bindProgress(rows)
        bindRefill()

        findViewById<View>(R.id.viewBellDot).visibility =
            if (db.unreadCount(userId) > 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tvScheduleEmpty).visibility =
            if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun bindHeader(user: User) {
        val hour = LocalTime.now().hour
        val greeting = when {
            hour < 12 -> R.string.greeting_morning
            hour < 17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
        findViewById<TextView>(R.id.tvGreeting).setText(greeting)
        findViewById<TextView>(R.id.tvUserName).text = user.name
        findViewById<TextView>(R.id.btnProfile).text = initialsOf(user.name)
        findViewById<TextView>(R.id.tvToday).text = Dates.formatWeekday(LocalDate.now())
    }

    private fun bindHero(rows: List<DoseRow>) {
        val tvIn = findViewById<TextView>(R.id.tvNextIn)
        val tvTime = findViewById<TextView>(R.id.tvNextTime)
        val tvName = findViewById<TextView>(R.id.tvNextName)
        val tvSub = findViewById<TextView>(R.id.tvNextSub)
        val actions = findViewById<View>(R.id.rowDoseActions)

        val pending = rows.firstOrNull { it.state == DoseState.DUE || it.state == DoseState.NEXT }
        nextRow = pending

        if (pending != null) {
            val med = pending.item.medicine
            tvTime.text = Dates.formatTime(pending.item.time)
            tvName.text = med.name
            tvSub.text = getString(R.string.dose_sub, med.dosage, foodLabel(med.food))
            tvIn.text = untilText(pending)
            findViewById<TextView>(R.id.btnSnooze).text = getString(R.string.btn_snooze_fmt, med.snoozeMinutes)
            actions.visibility = View.VISIBLE
        } else {
            tvIn.text = ""
            actions.visibility = View.GONE
            if (rows.isEmpty()) {
                tvTime.setText(R.string.none_time)
                tvName.setText(R.string.none_name)
                tvSub.setText(R.string.none_sub)
            } else {
                tvTime.setText(R.string.all_done_time)
                tvName.setText(R.string.all_done_name)
                tvSub.setText(R.string.all_done_sub)
            }
        }
    }

    private fun untilText(row: DoseRow): String {
        val minutes = Duration.between(LocalTime.now(), LocalTime.parse(row.item.time)).toMinutes()
        return when {
            row.state == DoseState.DUE || minutes <= 0 -> getString(R.string.due_now)
            minutes < 60 -> getString(R.string.in_minutes, minutes.toInt())
            minutes % 60 == 0L -> getString(R.string.in_hours_exact, (minutes / 60).toInt())
            else -> getString(R.string.in_hours, (minutes / 60).toInt(), (minutes % 60).toInt())
        }
    }

    private fun bindProgress(rows: List<DoseRow>) {
        val total = rows.size
        val done = rows.count { it.state == DoseState.TAKEN }
        findViewById<TextView>(R.id.tvDosesTaken).text = getString(R.string.doses_taken_fmt, done, total)
        val percent = if (total == 0) 0 else done * 100 / total
        findViewById<LinearProgressIndicator>(R.id.progressToday).setProgressCompat(percent, true)
    }

    private fun bindRefill() {
        val low = db.getMedicines(userId)
            .filter { it.status == Status.ACTIVE && it.refillAlert && it.stock <= DoseActions.REFILL_THRESHOLD }
            .minByOrNull { it.stock }
        lowStockMedicine = low

        val card = findViewById<View>(R.id.cardRefill)
        if (low == null) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvRefillTitle).text = getString(R.string.refill_title_fmt, low.name)
        findViewById<TextView>(R.id.tvRefillSub).text = getString(R.string.refill_sub_fmt, low.stock)
    }

    // ------------------------------------------------------------------
    //  Permissions
    // ------------------------------------------------------------------

    private fun ensureReminderPermissions() {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            askForExactAlarms()
        }
    }

    /** Android 12+ lets users decide whether apps may ring alarms at the exact minute. */
    private fun askForExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val alarms = getSystemService(AlarmManager::class.java)
        if (alarms.canScheduleExactAlarms()) return

        val today = LocalDate.now().toEpochDay()
        if (session.exactPromptDay == today) return
        session.exactPromptDay = today

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.perm_alarm_title)
            .setMessage(R.string.perm_alarm_body)
            .setPositiveButton(R.string.perm_alarm_positive) { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + packageName))
                )
            }
            .setNegativeButton(R.string.perm_not_now, null)
            .show()
    }
}
