package com.example.smartmedicine

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartmedicine.data.Status
import com.example.smartmedicine.ui.HistoryAdapter
import com.google.android.material.chip.ChipGroup

/** Completed and stopped medicines, with a filter. */
class HistoryActivity : BaseActivity() {

    private lateinit var adapter: HistoryAdapter
    private var filter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        bindBack()

        adapter = HistoryAdapter { medicine ->
            startActivity(
                Intent(this, MedicineDetailsActivity::class.java)
                    .putExtra(MedicineDetailsActivity.EXTRA_MEDICINE_ID, medicine.id)
            )
        }
        findViewById<RecyclerView>(R.id.rvHistory).apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
            isNestedScrollingEnabled = false
        }

        findViewById<ChipGroup>(R.id.chipGroupFilter).setOnCheckedStateChangeListener { _, checkedIds ->
            filter = when (checkedIds.firstOrNull()) {
                R.id.chipFilterCompleted -> Status.COMPLETED
                R.id.chipFilterStopped -> Status.STOPPED
                else -> null
            }
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        // Courses that ended since the last visit belong here too.
        db.completeFinishedCourses(userId)

        val finished = db.getMedicines(userId).filter { it.status != Status.ACTIVE }
        findViewById<TextView>(R.id.tvTotal).text = finished.size.toString()
        findViewById<TextView>(R.id.tvCompleted).text = finished.count { it.status == Status.COMPLETED }.toString()
        findViewById<TextView>(R.id.tvStopped).text = finished.count { it.status == Status.STOPPED }.toString()

        val shown = filter?.let { wanted -> finished.filter { it.status == wanted } } ?: finished
        adapter.submit(shown)
        findViewById<View>(R.id.tvHistoryEmpty).visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }
}
