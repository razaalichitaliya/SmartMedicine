package com.example.smartmedicine

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartmedicine.data.NotificationType
import com.example.smartmedicine.ui.PrescriptionAdapter
import com.example.smartmedicine.ui.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.time.LocalDate

/** Upload a photo or PDF of a prescription and keep it on the phone. */
class PrescriptionActivity : BaseActivity() {

    companion object {
        private const val MAX_BYTES = 10L * 1024L * 1024L
    }

    private lateinit var adapter: PrescriptionAdapter

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) importFile(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prescription)
        bindBack()

        adapter = PrescriptionAdapter { prescription -> openPrescription(prescription.id) }
        findViewById<RecyclerView>(R.id.rvPrescriptions).apply {
            layoutManager = LinearLayoutManager(this@PrescriptionActivity)
            adapter = this@PrescriptionActivity.adapter
            isNestedScrollingEnabled = false
        }

        findViewById<View>(R.id.btnUploadPrescription).setOnClickListener {
            pickFile.launch(arrayOf("image/*", "application/pdf"))
        }
        findViewById<View>(R.id.btnBackHome).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val list = db.getPrescriptions(userId)
        adapter.submit(list)
        findViewById<View>(R.id.tvPrescEmpty).visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openPrescription(id: Long) {
        startActivity(
            Intent(this, ViewPrescriptionActivity::class.java)
                .putExtra(ViewPrescriptionActivity.EXTRA_ID, id)
        )
    }

    /** Copies the chosen file into the app's private storage, then asks for a title. */
    private fun importFile(uri: Uri) {
        val resolver = contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"

        var size = -1L
        var displayName = getString(R.string.view_presc_title)
        resolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) displayName = c.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
            }
        }
        if (size > MAX_BYTES) {
            toast(R.string.err_file_big)
            return
        }

        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        val dir = File(filesDir, "prescriptions")
        dir.mkdirs()
        val dest = File(dir, System.currentTimeMillis().toString() + "." + extension)

        try {
            val input = resolver.openInputStream(uri) ?: throw java.io.IOException("No stream")
            input.use { source ->
                dest.outputStream().use { target -> source.copyTo(target) }
            }
            if (dest.length() > MAX_BYTES) {
                dest.delete()
                toast(R.string.err_file_big)
                return
            }
        } catch (e: Exception) {
            dest.delete()
            toast(R.string.err_file_read)
            return
        }

        askForDetails(dest, mime, displayName.substringBeforeLast('.'))
    }

    private fun askForDetails(file: File, mime: String, defaultTitle: String) {
        val view = layoutInflater.inflate(R.layout.dialog_prescription, null)
        val etTitle = view.findViewById<TextInputEditText>(R.id.etPrescTitle)
        val etDoctor = view.findViewById<TextInputEditText>(R.id.etPrescDoctor)
        etTitle.setText(defaultTitle)

        var saved = false
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dlg_presc_title)
            .setView(view)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                saved = true
                val title = etTitle.text?.toString().orEmpty().trim().ifEmpty { defaultTitle }
                val doctor = etDoctor.text?.toString().orEmpty().trim()
                val id = db.addPrescription(
                    userId, title, doctor, LocalDate.now().toString(), file.name, mime
                )
                db.addNotification(
                    userId,
                    getString(R.string.presc_notif_title),
                    title,
                    NotificationType.INFO,
                    null
                )
                toast(R.string.presc_saved)
                refresh()
                openPrescription(id)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .setOnDismissListener {
                // Cancelled: don't leave an orphan copy of the file behind.
                if (!saved) file.delete()
            }
            .show()
    }
}
