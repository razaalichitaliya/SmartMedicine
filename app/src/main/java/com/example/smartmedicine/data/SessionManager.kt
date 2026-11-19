package com.example.smartmedicine.data

import android.content.Context
import android.content.SharedPreferences

/** Remembers who is signed in, so the app opens straight to Home on the next launch. */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE)

    val userId: Long
        get() = prefs.getLong(KEY_USER_ID, -1L)

    val isLoggedIn: Boolean
        get() = userId != -1L

    fun login(userId: Long) {
        prefs.edit().putLong(KEY_USER_ID, userId).apply()
    }

    fun logout() {
        prefs.edit().remove(KEY_USER_ID).apply()
    }

    fun notificationsEnabled(userId: Long): Boolean =
        prefs.getBoolean(KEY_NOTIFICATIONS + userId, true)

    fun setNotificationsEnabled(userId: Long, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS + userId, enabled).apply()
    }

    /** Epoch day on which we last asked for the exact-alarm permission (at most one prompt per day). */
    var exactPromptDay: Long
        get() = prefs.getLong(KEY_EXACT_PROMPT, -1L)
        set(value) {
            prefs.edit().putLong(KEY_EXACT_PROMPT, value).apply()
        }

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_NOTIFICATIONS = "notifications_enabled_"
        const val KEY_EXACT_PROMPT = "exact_prompt_day"
    }
}
