package com.example.smartmedicine.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.smartmedicine.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The app's SQLite database. Every table is scoped by user_id, so several accounts
 * can share one phone without seeing each other's data.
 */
class DatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    private val appContext: Context = context.applicationContext

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE medicines (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                dosage TEXT NOT NULL,
                form TEXT NOT NULL,
                slots TEXT NOT NULL,
                food TEXT NOT NULL,
                times TEXT NOT NULL,
                duration_days INTEGER NOT NULL,
                start_date TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                stock INTEGER NOT NULL DEFAULT 0,
                reminder_enabled INTEGER NOT NULL DEFAULT 1,
                repeat_daily INTEGER NOT NULL DEFAULT 1,
                vibrate INTEGER NOT NULL DEFAULT 1,
                refill_alert INTEGER NOT NULL DEFAULT 1,
                snooze_min INTEGER NOT NULL DEFAULT 10,
                status TEXT NOT NULL DEFAULT 'ACTIVE',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE dose_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                medicine_id INTEGER NOT NULL REFERENCES medicines(id) ON DELETE CASCADE,
                user_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                time TEXT NOT NULL,
                status TEXT NOT NULL,
                logged_at INTEGER NOT NULL,
                UNIQUE (medicine_id, date, time)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE prescriptions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                title TEXT NOT NULL,
                doctor TEXT NOT NULL DEFAULT '',
                date TEXT NOT NULL,
                file_name TEXT NOT NULL,
                mime TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                title TEXT NOT NULL,
                message TEXT NOT NULL,
                type TEXT NOT NULL,
                medicine_id INTEGER,
                is_read INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_medicines_user ON medicines(user_id)")
        db.execSQL("CREATE INDEX idx_logs_user_date ON dose_logs(user_id, date)")
        db.execSQL("CREATE INDEX idx_notifications_user ON notifications(user_id, created_at)")
        db.execSQL("CREATE INDEX idx_prescriptions_user ON prescriptions(user_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema version 1: nothing to migrate yet.
    }

    // ------------------------------------------------------------------
    //  Users
    // ------------------------------------------------------------------

    fun emailExists(email: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM users WHERE email = ?",
            arrayOf(email.trim().lowercase())
        ).use { it.moveToFirst() }

    /** Creates an account and returns its id, or -1 if the email is already registered. */
    fun createUser(name: String, email: String, password: String): Long {
        if (emailExists(email)) return -1L
        val salt = PasswordHasher.newSalt()
        val values = ContentValues().apply {
            put("name", name.trim())
            put("email", email.trim().lowercase())
            put("password_hash", PasswordHasher.hash(password, salt))
            put("salt", salt)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("users", null, values)
    }

    /** Returns the user when the email and password match, otherwise null. */
    fun authenticate(email: String, password: String): User? {
        readableDatabase.rawQuery(
            "SELECT id, name, email, password_hash, salt FROM users WHERE email = ?",
            arrayOf(email.trim().lowercase())
        ).use { c ->
            if (!c.moveToFirst()) return null
            val ok = PasswordHasher.verify(password, c.getString(4), c.getString(3))
            return if (ok) User(c.getLong(0), c.getString(1), c.getString(2)) else null
        }
    }

    fun getUser(id: Long): User? =
        readableDatabase.rawQuery(
            "SELECT id, name, email FROM users WHERE id = ?",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) User(c.getLong(0), c.getString(1), c.getString(2)) else null
        }

    fun updateUserName(id: Long, name: String) {
        val values = ContentValues().apply { put("name", name.trim()) }
        writableDatabase.update("users", values, "id = ?", arrayOf(id.toString()))
    }

    /** Changes the password after checking the current one. Returns false if the current one is wrong. */
    fun changePassword(id: Long, currentPassword: String, newPassword: String): Boolean {
        val stored = readableDatabase.rawQuery(
            "SELECT password_hash, salt FROM users WHERE id = ?",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) Pair(c.getString(0), c.getString(1)) else null
        } ?: return false

        if (!PasswordHasher.verify(currentPassword, stored.second, stored.first)) return false

        val salt = PasswordHasher.newSalt()
        val values = ContentValues().apply {
            put("password_hash", PasswordHasher.hash(newPassword, salt))
            put("salt", salt)
        }
        writableDatabase.update("users", values, "id = ?", arrayOf(id.toString()))
        return true
    }

    // ------------------------------------------------------------------
    //  Medicines
    // ------------------------------------------------------------------

    private fun Cursor.colStr(col: String): String = getString(getColumnIndexOrThrow(col)) ?: ""
    private fun Cursor.colLong(col: String): Long = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.colInt(col: String): Int = getInt(getColumnIndexOrThrow(col))
    private fun Cursor.colBool(col: String): Boolean = getInt(getColumnIndexOrThrow(col)) == 1

    private fun csv(value: String): List<String> =
        value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun medicineFrom(c: Cursor): Medicine = Medicine(
        id = c.colLong("id"),
        userId = c.colLong("user_id"),
        name = c.colStr("name"),
        dosage = c.colStr("dosage"),
        form = c.colStr("form"),
        slots = csv(c.colStr("slots")),
        food = c.colStr("food"),
        times = csv(c.colStr("times")),
        durationDays = c.colInt("duration_days"),
        startDate = c.colStr("start_date"),
        notes = c.colStr("notes"),
        stock = c.colInt("stock"),
        reminderEnabled = c.colBool("reminder_enabled"),
        repeatDaily = c.colBool("repeat_daily"),
        vibrate = c.colBool("vibrate"),
        refillAlert = c.colBool("refill_alert"),
        snoozeMinutes = c.colInt("snooze_min"),
        status = c.colStr("status")
    )

    private fun medicineValues(m: Medicine): ContentValues = ContentValues().apply {
        put("user_id", m.userId)
        put("name", m.name)
        put("dosage", m.dosage)
        put("form", m.form)
        put("slots", m.slots.joinToString(","))
        put("food", m.food)
        put("times", m.times.joinToString(","))
        put("duration_days", m.durationDays)
        put("start_date", m.startDate)
        put("notes", m.notes)
        put("stock", m.stock)
        put("reminder_enabled", if (m.reminderEnabled) 1 else 0)
        put("repeat_daily", if (m.repeatDaily) 1 else 0)
        put("vibrate", if (m.vibrate) 1 else 0)
        put("refill_alert", if (m.refillAlert) 1 else 0)
        put("snooze_min", m.snoozeMinutes)
        put("status", m.status)
    }

    fun insertMedicine(m: Medicine): Long {
        val values = medicineValues(m)
        values.put("created_at", System.currentTimeMillis())
        return writableDatabase.insert("medicines", null, values)
    }

    fun updateMedicine(m: Medicine) {
        writableDatabase.update("medicines", medicineValues(m), "id = ?", arrayOf(m.id.toString()))
    }

    fun getMedicine(id: Long): Medicine? =
        readableDatabase.rawQuery(
            "SELECT * FROM medicines WHERE id = ?",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) medicineFrom(c) else null
        }

    fun getMedicines(userId: Long): List<Medicine> =
        readableDatabase.rawQuery(
            "SELECT * FROM medicines WHERE user_id = ? ORDER BY created_at DESC",
            arrayOf(userId.toString())
        ).use { c ->
            val list = ArrayList<Medicine>()
            while (c.moveToNext()) list.add(medicineFrom(c))
            list
        }

    fun setStatus(id: Long, status: String) {
        val values = ContentValues().apply { put("status", status) }
        writableDatabase.update("medicines", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteMedicine(id: Long) {
        writableDatabase.execSQL("UPDATE notifications SET medicine_id = NULL WHERE medicine_id = ?", arrayOf<Any>(id))
        writableDatabase.delete("medicines", "id = ?", arrayOf(id.toString()))
    }

    private fun adjustStock(id: Long, delta: Int) {
        writableDatabase.execSQL(
            "UPDATE medicines SET stock = MAX(0, stock + ?) WHERE id = ?",
            arrayOf<Any>(delta, id)
        )
    }

    fun activeCount(userId: Long): Int =
        DatabaseUtils.longForQuery(
            readableDatabase,
            "SELECT COUNT(*) FROM medicines WHERE user_id = ? AND status = 'ACTIVE'",
            arrayOf(userId.toString())
        ).toInt()

    /** Marks courses whose last day has passed as completed. Returns the medicines that just finished. */
    fun completeFinishedCourses(userId: Long): List<Medicine> {
        val today = LocalDate.now()
        val finished = getMedicines(userId).filter {
            it.status == Status.ACTIVE && today.isAfter(Dates.endDate(it))
        }
        for (m in finished) {
            setStatus(m.id, Status.COMPLETED)
            addNotification(
                userId,
                appContext.getString(R.string.course_done_title, m.name),
                appContext.getString(R.string.course_done_body),
                NotificationType.INFO,
                m.id
            )
        }
        return finished
    }

    // ------------------------------------------------------------------
    //  Dose logs and schedule
    // ------------------------------------------------------------------

    fun getDoseStatus(medicineId: Long, date: String, time: String): String? =
        readableDatabase.rawQuery(
            "SELECT status FROM dose_logs WHERE medicine_id = ? AND date = ? AND time = ?",
            arrayOf(medicineId.toString(), date, time)
        ).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /**
     * Records a dose as taken or skipped. Returns false when it was already logged that way.
     * Taking a dose uses up one unit of stock; changing a taken dose back gives it back.
     */
    fun markDose(medicine: Medicine, date: String, time: String, status: String): Boolean {
        val previous = getDoseStatus(medicine.id, date, time)
        if (previous == status) return false

        val values = ContentValues().apply {
            put("medicine_id", medicine.id)
            put("user_id", medicine.userId)
            put("date", date)
            put("time", time)
            put("status", status)
            put("logged_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("dose_logs", null, values, SQLiteDatabase.CONFLICT_REPLACE)

        if (status == DoseStatus.TAKEN) {
            adjustStock(medicine.id, -1)
        } else if (previous == DoseStatus.TAKEN) {
            adjustStock(medicine.id, 1)
        }
        return true
    }

    private fun dosesFor(meds: List<Medicine>, userId: Long, day: LocalDate): List<DoseItem> {
        val logs = HashMap<String, String>()
        readableDatabase.rawQuery(
            "SELECT medicine_id, time, status FROM dose_logs WHERE user_id = ? AND date = ?",
            arrayOf(userId.toString(), day.toString())
        ).use { c ->
            while (c.moveToNext()) {
                logs[c.getLong(0).toString() + "|" + c.getString(1)] = c.getString(2)
            }
        }
        return meds
            .filter { Dates.isInCourse(it, day) }
            .flatMap { m -> m.times.map { t -> DoseItem(m, t, logs[m.id.toString() + "|" + t]) } }
            .sortedBy { it.time }
    }

    /** Every dose of every active medicine for one day, earliest first. */
    fun getDosesForDay(userId: Long, day: LocalDate): List<DoseItem> =
        dosesFor(getMedicines(userId).filter { it.status == Status.ACTIVE }, userId, day)

    /** Percentage of due doses taken over the last [days] days, or null when nothing was due. */
    fun adherence(userId: Long, days: Int = 30): Int? {
        val meds = getMedicines(userId).filter { it.status != Status.STOPPED }
        val now = LocalDateTime.now()
        var due = 0
        var taken = 0
        for (i in 0 until days) {
            val day = LocalDate.now().minusDays(i.toLong())
            for (d in dosesFor(meds, userId, day)) {
                if (day.atTime(LocalTime.parse(d.time)).isAfter(now)) continue
                due++
                if (d.loggedStatus == DoseStatus.TAKEN) taken++
            }
        }
        return if (due == 0) null else taken * 100 / due
    }

    /** Consecutive days (ending today or yesterday) on which every due dose was taken. */
    fun streak(userId: Long): Int {
        val meds = getMedicines(userId).filter { it.status != Status.STOPPED }
        val now = LocalDateTime.now()
        var streak = 0
        for (i in 0 until 120) {
            val day = LocalDate.now().minusDays(i.toLong())
            val due = dosesFor(meds, userId, day).filter {
                !day.atTime(LocalTime.parse(it.time)).isAfter(now)
            }
            if (due.isEmpty()) continue
            if (due.all { it.loggedStatus == DoseStatus.TAKEN }) {
                streak++
            } else if (i > 0) {
                break
            }
        }
        return streak
    }

    // ------------------------------------------------------------------
    //  Prescriptions
    // ------------------------------------------------------------------

    private fun prescriptionFrom(c: Cursor): Prescription = Prescription(
        id = c.colLong("id"),
        userId = c.colLong("user_id"),
        title = c.colStr("title"),
        doctor = c.colStr("doctor"),
        date = c.colStr("date"),
        fileName = c.colStr("file_name"),
        mime = c.colStr("mime")
    )

    fun addPrescription(userId: Long, title: String, doctor: String, date: String, fileName: String, mime: String): Long {
        val values = ContentValues().apply {
            put("user_id", userId)
            put("title", title)
            put("doctor", doctor)
            put("date", date)
            put("file_name", fileName)
            put("mime", mime)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("prescriptions", null, values)
    }

    fun getPrescriptions(userId: Long): List<Prescription> =
        readableDatabase.rawQuery(
            "SELECT * FROM prescriptions WHERE user_id = ? ORDER BY created_at DESC",
            arrayOf(userId.toString())
        ).use { c ->
            val list = ArrayList<Prescription>()
            while (c.moveToNext()) list.add(prescriptionFrom(c))
            list
        }

    fun getPrescription(id: Long): Prescription? =
        readableDatabase.rawQuery(
            "SELECT * FROM prescriptions WHERE id = ?",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) prescriptionFrom(c) else null
        }

    fun deletePrescription(id: Long) {
        writableDatabase.delete("prescriptions", "id = ?", arrayOf(id.toString()))
    }

    // ------------------------------------------------------------------
    //  Notifications
    // ------------------------------------------------------------------

    fun addNotification(userId: Long, title: String, message: String, type: String, medicineId: Long?): Long {
        val values = ContentValues().apply {
            put("user_id", userId)
            put("title", title)
            put("message", message)
            put("type", type)
            if (medicineId != null) put("medicine_id", medicineId) else putNull("medicine_id")
            put("is_read", 0)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("notifications", null, values)
    }

    fun getNotifications(userId: Long, limit: Int = 100): List<AppNotification> =
        readableDatabase.rawQuery(
            "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
            arrayOf(userId.toString(), limit.toString())
        ).use { c ->
            val list = ArrayList<AppNotification>()
            val medicineIdx = c.getColumnIndexOrThrow("medicine_id")
            while (c.moveToNext()) {
                list.add(
                    AppNotification(
                        id = c.colLong("id"),
                        userId = c.colLong("user_id"),
                        title = c.colStr("title"),
                        message = c.colStr("message"),
                        type = c.colStr("type"),
                        medicineId = if (c.isNull(medicineIdx)) null else c.getLong(medicineIdx),
                        isRead = c.colBool("is_read"),
                        createdAt = c.colLong("created_at")
                    )
                )
            }
            list
        }

    fun unreadCount(userId: Long): Int =
        DatabaseUtils.longForQuery(
            readableDatabase,
            "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0",
            arrayOf(userId.toString())
        ).toInt()

    fun markNotificationRead(id: Long) {
        val values = ContentValues().apply { put("is_read", 1) }
        writableDatabase.update("notifications", values, "id = ?", arrayOf(id.toString()))
    }

    fun markAllNotificationsRead(userId: Long) {
        val values = ContentValues().apply { put("is_read", 1) }
        writableDatabase.update("notifications", values, "user_id = ?", arrayOf(userId.toString()))
    }

    companion object {
        private const val DB_NAME = "smart_medicine.db"
        private const val DB_VERSION = 1

        @Volatile
        private var instance: DatabaseHelper? = null

        fun get(context: Context): DatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context).also { instance = it }
            }
    }
}
