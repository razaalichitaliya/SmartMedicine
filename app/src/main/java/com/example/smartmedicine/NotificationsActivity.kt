package com.example.smartmedicine

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartmedicine.data.Dates
import com.example.smartmedicine.ui.NotificationAdapter
import com.example.smartmedicine.ui.NotificationRow
import java.time.LocalDate

/** The in-app log of reminders, refill alerts and other messages. */
class NotificationsActivity : BaseActivity() {

    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        bindBack()

        adapter = NotificationAdapter { notification ->
            db.markNotificationRead(notification.id)
            val medicineId = notification.medicineId
            if (medicineId != null && db.getMedicine(medicineId) != null) {
                startActivity(
                    Intent(this, MedicineDetailsActivity::class.java)
                        .putExtra(MedicineDetailsActivity.EXTRA_MEDICINE_ID, medicineId)
                )
            }
            refresh()
        }
        findViewById<RecyclerView>(R.id.rvNotifications).apply {
            layoutManager = LinearLayoutManager(this@NotificationsActivity)
            adapter = this@NotificationsActivity.adapter
            isNestedScrollingEnabled = false
        }

        findViewById<View>(R.id.tvMarkAllRead).setOnClickListener {
            db.markAllNotificationsRead(userId)
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val all = db.getNotifications(userId)
        val today = LocalDate.now()
        val (todays, earlier) = all.partition { Dates.toLocalDate(it.createdAt) == today }

        val rows = ArrayList<NotificationRow>()
        if (todays.isNotEmpty()) {
            rows.add(NotificationRow.Header(R.string.sec_today))
            for (n in todays) rows.add(NotificationRow.Item(n))
        }
        if (earlier.isNotEmpty()) {
            rows.add(NotificationRow.Header(R.string.sec_earlier))
            for (n in earlier) rows.add(NotificationRow.Item(n))
        }
        adapter.submit(rows)
        findViewById<View>(R.id.tvNotifEmpty).visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }
}
