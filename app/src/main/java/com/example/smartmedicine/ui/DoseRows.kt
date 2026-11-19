package com.example.smartmedicine.ui

import com.example.smartmedicine.data.DoseItem
import com.example.smartmedicine.data.DoseStatus
import java.time.LocalTime

enum class DoseState { TAKEN, SKIPPED, DUE, NEXT, UPCOMING }

data class DoseRow(val item: DoseItem, val state: DoseState)

/**
 * Works out how each dose of the day should look.
 * DUE = time has come and it isn't logged yet, NEXT = the first dose still ahead, UPCOMING = later ones.
 */
fun buildDoseRows(items: List<DoseItem>, now: LocalTime): List<DoseRow> {
    var nextAssigned = false
    return items.map { item ->
        val doseTime = LocalTime.parse(item.time)
        val state = when {
            item.loggedStatus == DoseStatus.TAKEN -> DoseState.TAKEN
            item.loggedStatus == DoseStatus.SKIPPED -> DoseState.SKIPPED
            !doseTime.isAfter(now) -> DoseState.DUE
            !nextAssigned -> {
                nextAssigned = true
                DoseState.NEXT
            }
            else -> DoseState.UPCOMING
        }
        DoseRow(item, state)
    }
}
