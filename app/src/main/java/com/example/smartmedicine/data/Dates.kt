package com.example.smartmedicine.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Dates {

    /** Default dose time for each time-of-day chip on the Add Medicine screen. */
    val defaultSlotTimes: Map<String, String> = linkedMapOf(
        "MORNING" to "08:00",
        "AFTERNOON" to "14:00",
        "EVENING" to "18:00",
        "NIGHT" to "21:00"
    )

    fun parse(date: String): LocalDate = LocalDate.parse(date)

    fun endDate(m: Medicine): LocalDate = parse(m.startDate).plusDays((m.durationDays - 1).toLong())

    fun isInCourse(m: Medicine, day: LocalDate): Boolean =
        !day.isBefore(parse(m.startDate)) && !day.isAfter(endDate(m))

    /** 1-based day of the course, clamped to the course length. */
    fun dayNumber(m: Medicine, today: LocalDate): Int {
        val n = ChronoUnit.DAYS.between(parse(m.startDate), today).toInt() + 1
        return n.coerceIn(1, maxOf(1, m.durationDays))
    }

    fun timeKey(hour: Int, minute: Int): String = String.format(Locale.US, "%02d:%02d", hour, minute)

    fun formatTime(hhmm: String): String =
        LocalTime.parse(hhmm).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

    fun formatShort(day: LocalDate): String =
        day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

    fun formatLong(day: LocalDate): String =
        day.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

    fun formatWeekday(day: LocalDate): String =
        day.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))

    fun formatClock(epochMillis: Long): String =
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}
