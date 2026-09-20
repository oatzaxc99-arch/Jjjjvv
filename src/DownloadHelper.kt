package com.example.mediadl

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.net.HttpURLConnection
import java.net.URL

object DownloadHelper {

    /**
     * ดาวน์โหลดไฟล์จาก [urlString] แล้วบันทึกลงในโฟลเดอร์ที่ผู้ใช้เลือกไว้ ([treeUri])
     * onProgress(percent) จะถูกเรียกระหว่างดาวน์โหลด (0-100), หรือ -1 ถ้าไม่ทราบขนาดไฟล์
     * คืนค่า Result<String> เป็นชื่อไฟล์ที่บันทึกสำเร็จ หรือ error
     */
    fun download(
        context: Context,
        urlString: String,
        treeUri: Uri,
        onProgress: (Int) -> Unit
    ): Result<String> {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.connect()

            if (conn.responseCode !in 200..299) {
                return Result.failure(Exception("เซิร์ฟเวอร์ตอบกลับรหัส ${conn.responseCode}"))
            }

            val contentType = conn.contentType ?: "application/octet-stream"
            val totalLength = conn.contentLength
            val fileName = guessFileName(urlString, contentType)

            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: return Result.failure(Exception("เปิดโฟลเดอร์ปลายทางไม่ได้"))

            // ลบไฟล์ชื่อซ้ำถ้ามี แล้วสร้างใหม่
            treeDoc.findFile(fileName)?.delete()
            val newFile = treeDoc.createFile(contentType, fileName)
                ?: return Result.failure(Exception("สร้างไฟล์ปลายทางไม่ได้"))

            context.contentResolver.openOutputStream(newFile.uri).use { out ->
                if (out == null) return Result.failure(Exception("เปิดไฟล์ปลายทางไม่ได้"))
                conn.inputStream.use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalLength > 0) {
                            onProgress(((totalRead * 100) / totalLength).toInt())
                        } else {
                            onProgress(-1)
                        }
                    }
                }
            }
            conn.disconnect()
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun guessFileName(urlString: String, contentType: String): String {
        val lastSegment = Uri.parse(urlString).lastPathSegment
        if (!lastSegment.isNullOrBlank() && lastSegment.contains(".")) {
            return lastSegment
        }
        val ext = when {
            contentType.contains("mp4") -> "mp4"
            contentType.contains("mpeg") || contentType.contains("mp3") -> "mp3"
            contentType.contains("wav") -> "wav"
            contentType.contains("image") -> "jpg"
            else -> "bin"
        }
        return "media_${System.currentTimeMillis()}.$ext"
    }
}
