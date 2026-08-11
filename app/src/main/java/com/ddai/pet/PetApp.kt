package com.ddai.pet

import android.app.Application
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class PetApp : Application() {

    override fun attachBaseContext(base: android.content.Context) {
        // 最早的执行点——在这里写日志，任何早期崩溃都能抓到
        writeLog("=== PetApp.attachBaseContext ENTER ===")
        try {
            super.attachBaseContext(base)
            writeLog("=== PetApp.attachBaseContext OK ===")
        } catch (t: Throwable) {
            writeLog("=== CRASH attachBaseContext: ${t}\n${t.stackTrace.joinToString("\n")} ===")
            throw t
        }
    }

    override fun onCreate() {
        writeLog("=== PetApp.onCreate ENTER ===")
        try {
            super.onCreate()
            writeLog("=== PetApp.onCreate OK ===")
        } catch (t: Throwable) {
            writeLog("=== CRASH onCreate: ${t}\n${t.stackTrace.joinToString("\n")} ===")
            throw t
        }
    }

    private fun writeLog(msg: String) {
        try {
            // 用 getFilesDir 最可靠的私有目录，同时多个位置都写一份
            val dirs = listOf<File>(
                File("/sdcard/Android/media/com.ddai.pet/"),
                File(filesDir, "")
            )
            for (dir in dirs) {
                try {
                    dir.mkdirs()
                    val f = File(dir, "xiaoke_crash.log")
                    FileOutputStream(f, true).use {
                        it.write("$msg\n".toByteArray())
                    }
                } catch (_: Exception) {}
            }
            Log.d("xiaoke", msg)
        } catch (_: Exception) {}
    }
}
