package com.example.smartmedicine

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.smartmedicine.data.Validation
import com.example.smartmedicine.reminder.NotificationHelper
import com.example.smartmedicine.reminder.ReminderScheduler
import com.example.smartmedicine.ui.initialsOf
import com.example.smartmedicine.ui.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Account details, stats and settings. */
class ProfileActivity : BaseActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* nothing more to do */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        bindBack()

        val switchNotifications = findViewById<MaterialSwitch>(R.id.switchNotifications)
        switchNotifications.isChecked = session.notificationsEnabled(userId)
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            session.setNotificationsEnabled(userId, isChecked)
            if (isChecked && needsNotificationPermission()) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        findViewById<View>(R.id.rowNotifications).setOnClickListener { switchNotifications.toggle() }

        findViewById<View>(R.id.rowSound).setOnClickListener { openChannelSettings() }
        findViewById<View>(R.id.rowPersonal).setOnClickListener { showEditName() }
        findViewById<View>(R.id.rowPassword).setOnClickListener { showChangePassword() }
        findViewById<View>(R.id.rowHelp).setOnClickListener { showHelp() }
        findViewById<View>(R.id.btnLogout).setOnClickListener { confirmLogout() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val user = db.getUser(userId)
        if (user == null) {
            session.logout()
            goToLogin()
            return
        }
        findViewById<TextView>(R.id.tvAvatar).text = initialsOf(user.name)
        findViewById<TextView>(R.id.tvUserName).text = user.name
        findViewById<TextView>(R.id.tvUserEmail).text = user.email

        findViewById<TextView>(R.id.tvStatMedicines).text = db.activeCount(userId).toString()
        val adherence = db.adherence(userId)
        findViewById<TextView>(R.id.tvStatAdherence).text =
            if (adherence == null) getString(R.string.value_none) else adherence.toString() + "%"
        findViewById<TextView>(R.id.tvStatStreak).text = db.streak(userId).toString()
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Sound and vibration are controlled per channel by Android, so send people to the system page. */
    private fun openChannelSettings() {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_DOSE)
        startActivity(intent)
    }

    private fun showEditName() {
        val user = db.getUser(userId) ?: return
        val view = layoutInflater.inflate(R.layout.dialog_name, null)
        val til = view.findViewById<TextInputLayout>(R.id.tilName)
        val et = view.findViewById<TextInputEditText>(R.id.etName)
        et.setText(user.name)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dlg_name_title)
            .setView(view)
            .setPositiveButton(R.string.btn_save, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = et.text?.toString().orEmpty().trim()
                if (name.length < 2) {
                    til.error = getString(R.string.err_name)
                    return@setOnClickListener
                }
                db.updateUserName(userId, name)
                toast(R.string.name_saved)
                refresh()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showChangePassword() {
        val view = layoutInflater.inflate(R.layout.dialog_password, null)
        val tilCurrent = view.findViewById<TextInputLayout>(R.id.tilCurrentPassword)
        val tilNew = view.findViewById<TextInputLayout>(R.id.tilNewPassword)
        val tilConfirm = view.findViewById<TextInputLayout>(R.id.tilConfirmNewPassword)
        val etCurrent = view.findViewById<TextInputEditText>(R.id.etCurrentPassword)
        val etNew = view.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirm = view.findViewById<TextInputEditText>(R.id.etConfirmNewPassword)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dlg_password_title)
            .setView(view)
            .setPositiveButton(R.string.btn_save, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                tilCurrent.error = null
                tilNew.error = null
                tilConfirm.error = null

                val current = etCurrent.text?.toString().orEmpty()
                val newPassword = etNew.text?.toString().orEmpty()
                val confirm = etConfirm.text?.toString().orEmpty()

                var valid = true
                if (current.isEmpty()) {
                    tilCurrent.error = getString(R.string.err_password_required)
                    valid = false
                }
                if (!Validation.isStrongPassword(newPassword)) {
                    tilNew.error = getString(R.string.err_password_rules)
                    valid = false
                }
                if (confirm != newPassword) {
                    tilConfirm.error = getString(R.string.err_password_mismatch)
                    valid = false
                }
                if (!valid) return@setOnClickListener

                if (!db.changePassword(userId, current, newPassword)) {
                    tilCurrent.error = getString(R.string.err_current_password)
                    return@setOnClickListener
                }
                toast(R.string.password_changed)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dlg_help_title)
            .setMessage(R.string.dlg_help_body)
            .setPositiveButton(R.string.btn_close, null)
            .show()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dlg_logout_title)
            .setMessage(R.string.dlg_logout_body)
            .setPositiveButton(R.string.btn_logout) { _, _ ->
                ReminderScheduler.cancelAll(this, userId)
                session.logout()
                goToLogin()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
