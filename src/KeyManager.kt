package com.example.mediadl

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.Instant

enum class Role { ADMIN, USER, INVALID }

/** ผลลัพธ์การตรวจสอบ Key พร้อมรายละเอียดไว้แสดงบนหน้าจอ */
data class KeyCheckResult(
    val role: Role,
    val slot: Int? = null,
    val expiresAt: String? = null,
    val reason: String? = null // เหตุผลตอน role == INVALID เช่น "หมดอายุ", "ถูกระงับ"
)

/**
 * อ่านรายการ Key จาก key.json
 *
 * รูปแบบไฟล์ (ตามระบบ Key จริง):
 * {
 *   "keys": [
 *     {
 *       "key": "ADMIN-XXXX-XXXX-XXXX",
 *       "role": "admin" | "user",
 *       "slot": 1,                 // ใส่เฉพาะ user ไว้แยกโควตา
 *       "active": true,
 *       "expiresAt": "2027-09-19T11:14:00.607938Z",
 *       "createdAt": "2026-09-19T11:14:00.608067Z"
 *     },
 *     ...
 *   ]
 * }
 *
 * ลำดับการค้นหาไฟล์:
 * 1. ไฟล์ภายนอกที่ app เข้าถึงได้เอง (ไม่ต้องขอ permission):
 *    /sdcard/Android/data/com.example.mediadl/files/key.json
 *    -> อัปเดต/หมุนเวียน Key ได้โดยไม่ต้องคอมไพล์ใหม่ แค่เขียนไฟล์นี้ทับ (เช่น ดึงมาจากเซิร์ฟเวอร์เป็นระยะ)
 * 2. ถ้าไม่มีไฟล์ภายนอก จะ fallback ไปใช้ assets/key.json ที่ติดมากับ APK
 */
object KeyManager {

    fun externalKeyFile(context: Context): File? {
        val dir = context.getExternalFilesDir(null) ?: return null
        return File(dir, "key.json")
    }

    private fun loadJson(context: Context): JSONObject {
        val extFile = externalKeyFile(context)
        if (extFile != null && extFile.exists()) {
            return JSONObject(extFile.readText())
        }
        val text = context.assets.open("key.json").bufferedReader().use { it.readText() }
        return JSONObject(text)
    }

    fun checkKey(context: Context, key: String): KeyCheckResult {
        if (key.isBlank()) return KeyCheckResult(Role.INVALID, reason = "กรุณากรอก Key")

        return try {
            val json = loadJson(context)
            val keys = json.optJSONArray("keys")
                ?: return KeyCheckResult(Role.INVALID, reason = "ไฟล์ key.json ไม่ถูกต้อง")

            var entry: JSONObject? = null
            for (i in 0 until keys.length()) {
                val obj = keys.getJSONObject(i)
                if (obj.optString("key") == key) {
                    entry = obj
                    break
                }
            }

            if (entry == null) {
                return KeyCheckResult(Role.INVALID, reason = "ไม่พบ Key นี้ในระบบ")
            }

            val active = entry.optBoolean("active", true)
            if (!active) {
                return KeyCheckResult(Role.INVALID, reason = "Key นี้ถูกระงับการใช้งาน")
            }

            val expiresAt = entry.optString("expiresAt", null)
            if (!expiresAt.isNullOrBlank()) {
                try {
                    val expiry = Instant.parse(expiresAt)
                    if (Instant.now().isAfter(expiry)) {
                        return KeyCheckResult(Role.INVALID, expiresAt = expiresAt, reason = "Key หมดอายุแล้ว")
                    }
                } catch (e: Exception) {
                    // รูปแบบวันที่อ่านไม่ได้ ให้ถือว่าผ่าน แต่ไม่บล็อกผู้ใช้เพราะรูปแบบไฟล์ผิดพลาด
                }
            }

            val role = when (entry.optString("role").lowercase()) {
                "admin" -> Role.ADMIN
                "user" -> Role.USER
                else -> Role.INVALID
            }
            val slot = if (entry.has("slot")) entry.optInt("slot") else null

            if (role == Role.INVALID) {
                return KeyCheckResult(Role.INVALID, reason = "role ของ Key นี้ไม่ถูกต้อง")
            }

            KeyCheckResult(role = role, slot = slot, expiresAt = expiresAt)
        } catch (e: Exception) {
            KeyCheckResult(Role.INVALID, reason = "อ่านไฟล์ key.json ไม่สำเร็จ: ${e.message}")
        }
    }
}
