package com.example.smartmedicine

import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmedicine.data.DatabaseHelper
import com.example.smartmedicine.data.SessionManager

/** Shared plumbing for every signed-in screen. */
abstract class BaseActivity : AppCompatActivity() {

    protected val db: DatabaseHelper by lazy { DatabaseHelper.get(this) }
    protected val session: SessionManager by lazy { SessionManager(this) }

    /** Id of the signed-in user, or -1 when nobody is signed in. */
    protected val userId: Long
        get() = session.userId

    protected fun bindBack() {
        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }
    }

    protected fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }
}
