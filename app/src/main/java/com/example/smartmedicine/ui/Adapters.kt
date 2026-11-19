package com.example.smartmedicine.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.example.smartmedicine.R
import com.example.smartmedicine.data.AppNotification
import com.example.smartmedicine.data.Dates
import com.example.smartmedicine.data.Medicine
import com.example.smartmedicine.data.NotificationType
import com.example.smartmedicine.data.Prescription
import com.example.smartmedicine.data.Status
import java.time.LocalDate

// ----------------------------------------------------------------------
//  Today's schedule (Home)
// ----------------------------------------------------------------------
class DoseAdapter(private val onClick: (DoseRow) -> Unit) : RecyclerView.Adapter<DoseAdapter.Holder>() {

    private var rows: List<DoseRow> = emptyList()

    fun submit(newRows: List<DoseRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.tvDoseTime)
        val name: TextView = view.findViewById(R.id.tvDoseName)
        val sub: TextView = view.findViewById(R.id.tvDoseSub)
        val status: TextView = view.findViewById(R.id.tvDoseStatus)
        val dot: View = view.findViewById(R.id.railDot)
        val line: View = view.findViewById(R.id.railLine)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_dose, parent, false))

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        val ctx = holder.itemView.context
        val med = row.item.medicine

        holder.time.text = Dates.formatTime(row.item.time)
        holder.name.text = med.name
        holder.sub.text = ctx.getString(R.string.dose_sub, med.dosage, ctx.foodLabel(med.food))
        holder.line.visibility = if (position == rows.size - 1) View.INVISIBLE else View.VISIBLE

        val (label, color, dot) = when (row.state) {
            DoseState.TAKEN -> Triple(R.string.status_taken, "green", R.drawable.bg_dot_primary)
            DoseState.SKIPPED -> Triple(R.string.status_skipped, "gray", R.drawable.bg_dot_ring)
            DoseState.DUE -> Triple(R.string.status_due, "amber", R.drawable.bg_dot_amber)
            DoseState.NEXT -> Triple(R.string.status_next, "amber", R.drawable.bg_dot_amber)
            DoseState.UPCOMING -> Triple(R.string.status_upcoming, "gray", R.drawable.bg_dot_ring)
        }
        holder.status.setText(label)
        holder.status.applyStatus(color)
        holder.dot.setBackgroundResource(dot)
        holder.itemView.setOnClickListener { onClick(row) }
    }
}

// ----------------------------------------------------------------------
//  Saved prescriptions
// ----------------------------------------------------------------------
class PrescriptionAdapter(private val onClick: (Prescription) -> Unit) : RecyclerView.Adapter<PrescriptionAdapter.Holder>() {

    private var items: List<Prescription> = emptyList()

    fun submit(list: List<Prescription>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivPrescIcon)
        val title: TextView = view.findViewById(R.id.tvPrescTitle)
        val sub: TextView = view.findViewById(R.id.tvPrescSub)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_prescription, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val p = items[position]
        val ctx = holder.itemView.context
        val isPdf = p.mime == "application/pdf"
        holder.icon.applyTile(R.drawable.ic_description, if (isPdf) "red" else "blue")
        holder.title.text = p.title
        holder.sub.text = ctx.getString(
            R.string.presc_sub_fmt,
            Dates.formatLong(Dates.parse(p.date)),
            ctx.getString(if (isPdf) R.string.kind_pdf else R.string.kind_photo)
        )
        holder.itemView.setOnClickListener { onClick(p) }
    }
}

// ----------------------------------------------------------------------
//  Finished and stopped medicines
// ----------------------------------------------------------------------
class HistoryAdapter(private val onClick: (Medicine) -> Unit) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    private var items: List<Medicine> = emptyList()

    fun submit(list: List<Medicine>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivHistIcon)
        val name: TextView = view.findViewById(R.id.tvHistName)
        val sub: TextView = view.findViewById(R.id.tvHistSub)
        val status: TextView = view.findViewById(R.id.tvHistStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val m = items[position]
        val ctx = holder.itemView.context
        holder.name.text = m.name
        holder.sub.text = ctx.getString(
            R.string.hist_sub_fmt,
            ctx.resources.getQuantityString(R.plurals.days, m.durationDays, m.durationDays),
            Dates.formatShort(Dates.parse(m.startDate)),
            Dates.formatShort(Dates.endDate(m))
        )
        if (m.status == Status.COMPLETED) {
            holder.icon.applyTile(R.drawable.ic_check_circle, "mint")
            holder.status.setText(R.string.status_completed)
            holder.status.applyStatus("green")
        } else {
            holder.icon.applyTile(R.drawable.ic_warning, "red")
            holder.status.setText(R.string.status_stopped)
            holder.status.applyStatus("red")
        }
        holder.itemView.setOnClickListener { onClick(m) }
    }
}

// ----------------------------------------------------------------------
//  Notifications, grouped under Today / Earlier headers
// ----------------------------------------------------------------------
sealed class NotificationRow {
    data class Header(@StringRes val title: Int) : NotificationRow()
    data class Item(val notification: AppNotification) : NotificationRow()
}

class NotificationAdapter(private val onClick: (AppNotification) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<NotificationRow> = emptyList()

    fun submit(newRows: List<NotificationRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvSection)
    }

    class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivNotifIcon)
        val title: TextView = view.findViewById(R.id.tvNotifTitle)
        val message: TextView = view.findViewById(R.id.tvNotifMsg)
        val time: TextView = view.findViewById(R.id.tvNotifTime)
        val unread: View = view.findViewById(R.id.viewUnread)
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is NotificationRow.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_section_header, parent, false))
        } else {
            ItemHolder(inflater.inflate(R.layout.item_notification, parent, false))
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is NotificationRow.Header -> (holder as HeaderHolder).title.setText(row.title)
            is NotificationRow.Item -> bindItem(holder as ItemHolder, row.notification)
        }
    }

    private fun bindItem(holder: ItemHolder, n: AppNotification) {
        val ctx = holder.itemView.context
        holder.title.text = n.title
        holder.message.text = n.message

        val date = Dates.toLocalDate(n.createdAt)
        val today = LocalDate.now()
        holder.time.text = when {
            date == today -> Dates.formatClock(n.createdAt)
            date == today.minusDays(1) -> ctx.getString(R.string.time_yesterday)
            else -> Dates.formatShort(date)
        }

        when (n.type) {
            NotificationType.DOSE -> holder.icon.applyTile(R.drawable.ic_notifications, "mint")
            NotificationType.REFILL -> holder.icon.applyTile(R.drawable.ic_warning, "amber")
            else -> holder.icon.applyTile(R.drawable.ic_check_circle, "blue")
        }
        holder.unread.visibility = if (n.isRead) View.INVISIBLE else View.VISIBLE
        holder.itemView.setOnClickListener { onClick(n) }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }
}
