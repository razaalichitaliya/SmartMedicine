package com.example.smartmedicine.ui

import android.content.Context
import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.example.smartmedicine.R
import com.example.smartmedicine.data.Status

fun Context.toast(@StringRes res: Int) {
    Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
}

fun Context.toast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

fun Context.foodLabel(code: String): String = getString(
    when (code) {
        "BEFORE" -> R.string.food_before
        "AFTER" -> R.string.food_after
        "WITH" -> R.string.food_with
        else -> R.string.food_any
    }
)

fun Context.formLabel(code: String): String = getString(
    when (code) {
        "CAPSULE" -> R.string.form_capsule
        "SYRUP" -> R.string.form_syrup
        "INJECTION" -> R.string.form_injection
        "DROPS" -> R.string.form_drops
        else -> R.string.form_tablet
    }
)

fun Context.slotLabel(code: String): String = getString(
    when (code) {
        "AFTERNOON" -> R.string.when_afternoon
        "EVENING" -> R.string.when_evening
        "NIGHT" -> R.string.when_night
        else -> R.string.when_morning
    }
)

fun Context.statusLabel(status: String): String = getString(
    when (status) {
        Status.COMPLETED -> R.string.status_completed
        Status.STOPPED -> R.string.status_stopped
        else -> R.string.status_active
    }
)

fun initialsOf(name: String): String {
    val letters = name.trim().split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
    return if (letters.isEmpty()) "?" else letters
}

/** Colours a status pill: green, amber, gray, red or blue. */
fun TextView.applyStatus(color: String) {
    setBackgroundResource(
        when (color) {
            "green" -> R.drawable.bg_status_green
            "amber" -> R.drawable.bg_status_amber
            "red" -> R.drawable.bg_status_red
            "blue" -> R.drawable.bg_status_blue
            else -> R.drawable.bg_status_gray
        }
    )
    setTextColor(
        ContextCompat.getColor(
            context,
            when (color) {
                "green" -> R.color.status_green_text
                "amber" -> R.color.status_amber_text
                "red" -> R.color.status_red_text
                "blue" -> R.color.status_blue_text
                else -> R.color.status_gray_text
            }
        )
    )
}

/** Shows an icon on a soft coloured tile: mint, amber, blue, violet, red or gray. */
fun ImageView.applyTile(@DrawableRes icon: Int, color: String) {
    setImageResource(icon)
    setBackgroundResource(
        when (color) {
            "amber" -> R.drawable.bg_tile_amber
            "blue" -> R.drawable.bg_tile_blue
            "violet" -> R.drawable.bg_tile_violet
            "red" -> R.drawable.bg_tile_red
            "gray" -> R.drawable.bg_tile_gray
            else -> R.drawable.bg_tile_mint
        }
    )
    val tint = ContextCompat.getColor(
        context,
        when (color) {
            "amber" -> R.color.amber_icon
            "blue" -> R.color.blue
            "violet" -> R.color.violet
            "red" -> R.color.red
            "gray" -> R.color.text_secondary
            else -> R.color.primary
        }
    )
    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(tint))
}
