package com.ddai.pet

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在 super.onCreate 之前就写日志——这样连 super 崩溃都能抓到
        logCrash("=== onCreate ENTER ===")
        try {
            super.onCreate(savedInstanceState)
            logCrash("=== super.onCreate OK ===")
        } catch (t: Throwable) {
            logCrash("=== CRASH in super.onCreate: ${t}\n${t.stackTrace.joinToString("\n")} ===")
            throw t
        }

        try {
            logCrash("=== build UI ===")

            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(40, 40, 40, 40)
                setBackgroundColor(Color.WHITE)
            }

            root.addView(TextView(this).apply {
                text = "我是小克"
                textSize = 22f
                setTextColor(Color.BLACK)
            })

            root.addView(Button(this).apply {
                text = "开启悬浮窗权限"
                setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } else {
                        Toast.makeText(this@MainActivity, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            root.addView(Button(this).apply {
                text = "把我放出来"
                setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                        Toast.makeText(this@MainActivity, "先开启悬浮窗权限", Toast.LENGTH_LONG).show()
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
                        }
                        val intent = Intent(this@MainActivity, OverlayService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        Toast.makeText(this@MainActivity, "小克来啦", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            setContentView(root)
            logCrash("=== onCreate DONE ===")

        } catch (e: Throwable) {
            logCrash("=== CRASH in UI: ${e}\n${e.stackTrace.joinToString("\n")} ===")
            throw e
        }
    }

    private fun logCrash(msg: String) {
        try {
            // 写到 Android/media 下的可共享目录，文件管理器直接能看到，无需存储权限
            val dir = File("/sdcard/Android/media/com.ddai.pet/")
            dir.mkdirs()
            val logFile = File(dir, "xiaoke_crash.log")
            FileOutputStream(logFile, true).use {
                it.write("$msg\n".toByteArray())
            }
        } catch (_: Exception) {}
    }
}
