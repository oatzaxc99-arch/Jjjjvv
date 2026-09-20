package com.example.mediadl

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var etKey: EditText
    private lateinit var btnCheckKey: Button
    private lateinit var tvKeyStatus: TextView

    private lateinit var etUrl: EditText
    private lateinit var btnChooseFolder: Button
    private lateinit var tvFolder: TextView
    private lateinit var btnDownload: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLog: TextView

    private var currentRole: Role = Role.INVALID
    private var destFolderUri: Uri? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("mediadl_prefs", MODE_PRIVATE) }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            destFolderUri = uri
            prefs.edit().putString("dest_folder", uri.toString()).apply()
            tvFolder.text = "โฟลเดอร์: ${uri.path}"
            updateDownloadButtonState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etKey = findViewById(R.id.etKey)
        btnCheckKey = findViewById(R.id.btnCheckKey)
        tvKeyStatus = findViewById(R.id.tvKeyStatus)

        etUrl = findViewById(R.id.etUrl)
        btnChooseFolder = findViewById(R.id.btnChooseFolder)
        tvFolder = findViewById(R.id.tvFolder)
        btnDownload = findViewById(R.id.btnDownload)
        progressBar = findViewById(R.id.progressBar)
        tvLog = findViewById(R.id.tvLog)

        prefs.getString("dest_folder", null)?.let {
            destFolderUri = Uri.parse(it)
            tvFolder.text = "โฟลเดอร์: ${destFolderUri?.path}"
        }

        btnCheckKey.setOnClickListener { onCheckKey() }
        btnChooseFolder.setOnClickListener { folderPicker.launch(null) }
        btnDownload.setOnClickListener { onDownloadClicked() }

        updateDownloadButtonState()
    }

    private fun onCheckKey() {
        val key = etKey.text.toString().trim()
        val result = KeyManager.checkKey(this, key)
        currentRole = result.role
        tvKeyStatus.text = when (result.role) {
            Role.ADMIN -> "สถานะ: ✅ Admin (หมดอายุ: ${formatExpiry(result.expiresAt)})"
            Role.USER -> {
                val slotText = result.slot?.let { " • Slot $it" } ?: ""
                "สถานะ: ✅ User$slotText (หมดอายุ: ${formatExpiry(result.expiresAt)})"
            }
            Role.INVALID -> "สถานะ: ❌ ${result.reason ?: "Key ไม่ถูกต้อง"}"
        }
        updateDownloadButtonState()
    }

    private fun formatExpiry(expiresAt: String?): String {
        if (expiresAt.isNullOrBlank()) return "ไม่มีกำหนด"
        return try {
            java.time.Instant.parse(expiresAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .toString()
        } catch (e: Exception) {
            expiresAt
        }
    }

    private fun updateDownloadButtonState() {
        btnDownload.isEnabled = currentRole != Role.INVALID && destFolderUri != null
    }

    private fun onDownloadClicked() {
        val url = etUrl.text.toString().trim()
        val folder = destFolderUri

        if (currentRole == Role.INVALID) {
            Toast.makeText(this, "กรุณาตรวจสอบ Key ให้ถูกต้องก่อน", Toast.LENGTH_SHORT).show()
            return
        }
        if (url.isBlank()) {
            Toast.makeText(this, "กรุณาใส่ URL", Toast.LENGTH_SHORT).show()
            return
        }
        if (folder == null) {
            Toast.makeText(this, "กรุณาเลือกโฟลเดอร์ปลายทาง", Toast.LENGTH_SHORT).show()
            return
        }

        btnDownload.isEnabled = false
        progressBar.progress = 0
        appendLog("เริ่มดาวน์โหลด: $url")

        executor.execute {
            val result = DownloadHelper.download(this, url, folder) { percent ->
                runOnUiThread {
                    if (percent in 0..100) {
                        progressBar.isIndeterminate = false
                        progressBar.progress = percent
                    } else {
                        progressBar.isIndeterminate = true
                    }
                }
            }
            runOnUiThread {
                progressBar.isIndeterminate = false
                result.onSuccess { fileName ->
                    progressBar.progress = 100
                    appendLog("✅ บันทึกไฟล์สำเร็จ: $fileName")
                    Toast.makeText(this, "ดาวน์โหลดเสร็จแล้ว: $fileName", Toast.LENGTH_LONG).show()
                }.onFailure { e ->
                    appendLog("❌ ดาวน์โหลดล้มเหลว: ${e.message}")
                    Toast.makeText(this, "ดาวน์โหลดล้มเหลว: ${e.message}", Toast.LENGTH_LONG).show()
                }
                btnDownload.isEnabled = true
            }
        }
    }

    private fun appendLog(line: String) {
        tvLog.text = "${tvLog.text}\n$line".trim()
    }
}
