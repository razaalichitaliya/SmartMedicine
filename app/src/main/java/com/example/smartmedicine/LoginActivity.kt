package com.example.smartmedicine

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.example.smartmedicine.data.DatabaseHelper
import com.example.smartmedicine.data.SessionManager
import com.example.smartmedicine.data.Validation
import com.example.smartmedicine.reminder.ReminderScheduler
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Launcher screen. Signed-in users skip straight to Home. */
class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session = SessionManager(this)
        if (session.isLoggedIn) {
            openHome()
            return
        }
        setContentView(R.layout.activity_login)

        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        etEmail.doOnTextChanged { _, _, _, _ -> tilEmail.error = null }
        etPassword.doOnTextChanged { _, _, _, _ -> tilPassword.error = null }

        btnLogin.setOnClickListener {
            val email = etEmail.text?.toString().orEmpty().trim()
            val password = etPassword.text?.toString().orEmpty()

            var valid = true
            if (!Validation.isEmail(email)) {
                tilEmail.error = getString(R.string.err_email)
                valid = false
            }
            if (password.isEmpty()) {
                tilPassword.error = getString(R.string.err_password_required)
                valid = false
            }
            if (!valid) return@setOnClickListener

            btnLogin.isEnabled = false
            val user = DatabaseHelper.get(this).authenticate(email, password)
            btnLogin.isEnabled = true

            if (user == null) {
                tilPassword.error = getString(R.string.err_invalid_login)
                return@setOnClickListener
            }
            session.login(user.id)
            ReminderScheduler.rescheduleAll(this, user.id)
            openHome()
        }

        findViewById<View>(R.id.tvGoToRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun openHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
