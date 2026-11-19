package com.example.smartmedicine.data

data class User(
    val id: Long,
    val name: String,
    val email: String
)

object Status {
    const val ACTIVE = "ACTIVE"
    const val COMPLETED = "COMPLETED"
    const val STOPPED = "STOPPED"
}

object DoseStatus {
    const val TAKEN = "TAKEN"
    const val SKIPPED = "SKIPPED"
}

object NotificationType {
    const val DOSE = "DOSE"
    const val REFILL = "REFILL"
    const val INFO = "INFO"
}

/**
 * One medicine and its schedule.
 *  - slots: the time-of-day chips the user picked (MORNING, AFTERNOON, EVENING, NIGHT)
 *  - times: the real dose times as 24-hour "HH:mm" strings
 *  - stock: doses left (one dose is removed each time a dose is marked as taken)
 */
data class Medicine(
    val id: Long = 0L,
    val userId: Long,
    val name: String,
    val dosage: String,
    val form: String,
    val slots: List<String>,
    val food: String,
    val times: List<String>,
    val durationDays: Int,
    val startDate: String,
    val notes: String,
    val stock: Int,
    val reminderEnabled: Boolean = true,
    val repeatDaily: Boolean = true,
    val vibrate: Boolean = true,
    val refillAlert: Boolean = true,
    val snoozeMinutes: Int = 10,
    val status: String = Status.ACTIVE
)

/** One scheduled dose on a given day. loggedStatus is null while it is still pending. */
data class DoseItem(
    val medicine: Medicine,
    val time: String,
    val loggedStatus: String?
)

data class Prescription(
    val id: Long,
    val userId: Long,
    val title: String,
    val doctor: String,
    val date: String,
    val fileName: String,
    val mime: String
)

data class AppNotification(
    val id: Long,
    val userId: Long,
    val title: String,
    val message: String,
    val type: String,
    val medicineId: Long?,
    val isRead: Boolean,
    val createdAt: Long
)
