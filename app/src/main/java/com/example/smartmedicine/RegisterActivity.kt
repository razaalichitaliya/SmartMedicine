package com.example.smartmedicine

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.example.smartmedicine.data.DatabaseHelper
import com.example.smartmedicine.data.NotificationType
import com.example.smartmedicine.data.SessionManager
import com.example.smartmedicine.data.Validation
import com.example.smartmedicine.ui.toast
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val tilName = findViewById<TextInputLayout>(R.id.tilName)
        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val tilConfirm = findViewById<TextInputLayout>(R.id.tilConfirmPassword)
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirm = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val cbTerms = findViewById<CheckBox>(R.id.cbTerms)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        etName.doOnTextChanged { _, _, _, _ -> tilName.error = null }
        etEmail.doOnTextChanged { _, _, _, _ -> tilEmail.error = null }
        etPassword.doOnTextChanged { _, _, _, _ -> tilPassword.error = null }
        etConfirm.doOnTextChanged { _, _, _, _ -> tilConfirm.error = null }

        btnRegister.setOnClickListener {
            val name = etName.text?.toString().orEmpty().trim()
            val email = etEmail.text?.toString().orEmpty().trim()
            val password = etPassword.text?.toString().orEmpty()
            val confirm = etConfirm.text?.toString().orEmpty()

            var valid = true
            if (name.length < 2) {
                tilName.error = getString(R.string.err_name)
                valid = false
            }
            if (!Validation.isEmail(email)) {
                tilEmail.error = getString(R.string.err_email)
                valid = false
            }
            if (!Validation.isStrongPassword(password)) {
                tilPassword.error = getString(R.string.err_password_rules)
                valid = false
            }
            if (confirm != password) {
                tilConfirm.error = getString(R.string.err_password_mismatch)
                valid = false
            }
            if (!valid) return@setOnClickListener
            if (!cbTerms.isChecked) {
                toast(R.string.err_terms)
                return@setOnClickListener
            }

            val db = DatabaseHelper.get(this)
            if (db.emailExists(email)) {
                tilEmail.error = getString(R.string.err_email_taken)
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            val id = db.createUser(name, email, password)
            btnRegister.isEnabled = true
            if (id == -1L) {
                tilEmail.error = getString(R.string.err_email_taken)
                return@setOnClickListener
            }

            db.addNotification(
                id,
                getString(R.string.welcome_title),
                getString(R.string.welcome_body),
                NotificationType.INFO,
                null
            )

            // New accounts are signed in straight away.
            SessionManager(this).login(id)
            val intent = Intent(this, HomeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        }

        findViewById<View>(R.id.tvGoToLogin).setOnClickListener { finish() }
    }
}
