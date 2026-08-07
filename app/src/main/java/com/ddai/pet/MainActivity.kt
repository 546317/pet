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
        super.onCreate(savedInstanceState)

        try {
            // 崩溃日志文件，方便排查
            val logFile = File(getExternalFilesDir(null), "crash.log")
            logFile.parentFile?.mkdirs()
            fun log(msg: String) {
                try {
                    FileOutputStream(logFile, true).use { it.write("$msg\n".toByteArray()) }
                } catch (_: Exception) {}
            }
            log("=== onCreate start ===")

            // 完全代码构建界面，不依赖 layout/activity_main.xml
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

            log("=== setContentView ===")
            setContentView(root)
            log("=== onCreate done ===")

        } catch (e: Throwable) {
            // 捕获崩溃，写入日志
            try {
                val logFile = File(getExternalFilesDir(null), "crash.log")
                logFile.parentFile?.mkdirs()
                FileOutputStream(logFile, true).use {
                    it.write("=== CRASH ===\n".toByteArray())
                    it.write(e.toString().toByteArray())
                    it.write("\n".toByteArray())
                    e.stackTrace.forEach { st ->
                        it.write(st.toString().toByteArray())
                        it.write("\n".toByteArray())
                    }
                }
            } catch (_: Exception) {}
            throw e
        }
    }
}