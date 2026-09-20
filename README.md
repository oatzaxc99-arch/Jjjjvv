# MediaDL (โครงสร้างแบบไม่มีโฟลเดอร์ลึก)

โปรเจกต์นี้ปรับให้แบนที่สุดเท่าที่ Android/Gradle จะยอมได้ โดยใช้ `sourceSets` แบบกำหนดเอง แทนโครงสร้างมาตรฐาน `app/src/main/java/com/.../` เหลือแค่โฟลเดอร์ที่**หลีกเลี่ยงไม่ได้จริง** (ระบบ resource ของ Android บังคับชื่อ `res/layout`, `res/values`)

## รายชื่อไฟล์ทั้งหมด (11 ไฟล์ + 1 ไฟล์ CI)

ถ้าจะสร้างทีละไฟล์ผ่านเว็บ GitHub (**Add file → Create new file**) ให้พิมพ์ชื่อไฟล์ตรงตามนี้เป๊ะๆ (พิมพ์ `/` มันจะสร้างโฟลเดอร์ที่จำเป็นให้เองอัตโนมัติ):

```
build.gradle
settings.gradle
gradle.properties
AndroidManifest.xml
src/MainActivity.kt
src/KeyManager.kt
src/DownloadHelper.kt
assets/key.json
res/layout/activity_main.xml
res/values/strings.xml
res/values/themes.xml
.github/workflows/build.yml
```

สังเกตว่า**ไม่มี** `app/` และ**ไม่มี** `com/example/mediadl/` แล้ว — ไฟล์ Kotlin ทั้ง 3 ไฟล์อยู่ในโฟลเดอร์ `src/` ชั้นเดียวเท่านั้น (Kotlin ไม่บังคับให้โฟลเดอร์ตรงกับชื่อ package เหมือน Java จึงทำแบบนี้ได้)

## วิธี build ผ่าน GitHub Actions (ไม่ต้องใช้ Termux)

เหมือนเดิมทุกอย่าง แค่โครงสร้างไฟล์เปลี่ยน:

1. สร้าง repo ใหม่บน GitHub (เปล่าๆ ไม่ต้องมี README)
2. สร้างไฟล์ทั้ง 12 ไฟล์ตามรายชื่อด้านบน (ก็อปเนื้อหาจากไฟล์ในซิปนี้ไปวาง) แล้ว commit
3. ไปแท็บ **Actions** → กด **Run workflow** (หรือรอให้เริ่มอัตโนมัติหลัง commit)
4. รอ ~3-8 นาที จนเขียว ✅
5. เข้า run ที่เสร็จแล้ว → เลื่อนลงหา **Artifacts** → โหลดไฟล์ `app-debug`
6. แตกซิปที่โหลดมา จะได้ `.apk` ติดตั้งบนมือถือได้เลย

## ระบบ Key

เหมือนเดิม — อ่านจาก `assets/key.json` (ติดไปกับ APK) ก่อน ถ้าอยากอัปเดต Key โดยไม่ต้อง build ใหม่ ให้วางไฟล์ `key.json` ทับที่:

```
/sdcard/Android/data/com.example.mediadl/files/key.json
```

รูปแบบไฟล์:

```json
{
  "keys": [
    {
      "key": "ADMIN-XXXX-XXXX-XXXX",
      "role": "admin",
      "active": true,
      "expiresAt": "2027-09-19T11:14:00.607938Z",
      "createdAt": "2026-09-19T11:14:00.608067Z"
    },
    {
      "key": "USER-XXXX-XXXX-XXXX",
      "role": "user",
      "slot": 1,
      "active": true,
      "expiresAt": "2026-10-19T11:14:00.608726Z",
      "createdAt": "2026-09-19T11:14:00.608816Z"
    }
  ]
}
```

## ต่อยอดในเวอร์ชันถัดไป (แนะนำ)

- เพิ่มการดึง Key จากเซิร์ฟเวอร์ (เขียนทับไฟล์ `key.json` ใน path ด้านบนเป็นระยะ)
- เพิ่มคิวดาวน์โหลดหลายไฟล์พร้อมกัน
- แยกสิทธิ์ Admin ให้ตั้งค่า/ดูสถิติการใช้งานได้ ส่วน User ดาวน์โหลดได้อย่างเดียว
- เพิ่ม Notification แสดงความคืบหน้าเวลาแอปอยู่เบื้องหลัง
