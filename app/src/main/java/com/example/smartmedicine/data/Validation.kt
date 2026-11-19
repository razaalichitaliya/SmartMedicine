package com.example.smartmedicine.data

import android.util.Patterns

object Validation {

    fun isEmail(value: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(value).matches()

    /** At least 8 characters with one letter and one number. */
    fun isStrongPassword(value: String): Boolean =
        value.length >= 8 && value.any { it.isLetter() } && value.any { it.isDigit() }
}
