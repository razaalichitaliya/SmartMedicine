package com.example.smartmedicine

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import com.example.smartmedicine.data.Dates
import com.example.smartmedicine.data.Prescription
import com.example.smartmedicine.ui.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class ViewPrescriptionActivity : BaseActivity() {

    companion object {
        const val EXTRA_ID = "prescription_id"
    }

    private var prescription: Prescription? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_prescription)
        bindBack()

        val p = db.getPrescription(intent.getLongExtra(EXTRA_ID, -1L))?.takeIf { it.userId == userId }
        if (p == null) {
            finish()
            return
        }
        prescription = p

        findViewById<TextView>(R.id.tvDoctor).text = p.doctor.ifBlank { getString(R.string.doctor_unknown) }
        findViewById<TextView>(R.id.tvDate).text = Dates.formatLong(Dates.parse(p.date))
        findViewById<TextView>(R.id.tvFileName).text = p.title

        val isPdf = p.mime == "application/pdf"
        val file = fileOf(p)
        if (file.exists()) {
            val bitmap = loadPreview(file, isPdf)
            if (bitmap != null) {
                val image = findViewById<ImageView>(R.id.ivPrescription)
                image.setPadding(0, 0, 0, 0)
                image.imageTintList = null
                image.setImageBitmap(bitmap)
            }
            findViewById<TextView>(R.id.tvPreviewHint).text =
                getString(if (isPdf) R.string.preview_pdf else R.string.preview_image)
        } else {
            findViewById<TextView>(R.id.tvPreviewHint).setText(R.string.err_file_missing)
        }

        findViewById<View>(R.id.btnShare).setOnClickListener { share(p) }
        findViewById<View>(R.id.btnOpen).setOnClickListener { openExternally(p) }
        findViewById<View>(R.id.btnDelete).setOnClickListener { confirmDelete(p) }
    }

    private fun fileOf(p: Prescription): File = File(File(filesDir, "prescriptions"), p.fileName)

    private fun uriOf(p: Prescription): Uri? {
        val file = fileOf(p)
        if (!file.exists()) {
            toast(R.string.err_file_missing)
            return null
        }
        return FileProvider.getUriForFile(this, packageName + ".fileprovider", file)
    }

    private fun share(p: Prescription) {
        val uri = uriOf(p) ?: return
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = p.mime
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    private fun openExternally(p: Prescription) {
        val uri = uriOf(p) ?: return
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, p.mime)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(R.string.err_no_viewer)
        }
    }

    private fun confirmDelete(p: Prescription) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dlg_delete_presc_title)
            .setMessage(R.string.dlg_delete_presc_body)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                fileOf(p).delete()
                db.deletePrescription(p.id)
                toast(R.string.presc_deleted)
                finish()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ------------------------------------------------------------------
    //  Previews
    // ------------------------------------------------------------------

    private fun loadPreview(file: File, isPdf: Boolean): Bitmap? =
        try {
            if (isPdf) renderPdf(file) else decodeImage(file)
        } catch (e: Exception) {
            null
        }

    /** First page of a PDF, drawn on a white background. */
    private fun renderPdf(file: File): Bitmap? {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        try {
            if (renderer.pageCount == 0) return null
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            return bitmap
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    /** Photos are scaled down so large camera images don't run out of memory, and rotated upright. */
    private fun decodeImage(file: File): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val options = BitmapFactory.Options()
        options.inSampleSize = sample
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap

        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
