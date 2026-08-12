package com.ddai.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import kotlin.math.abs

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val CHANNEL_ID = "pet_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PET_SIZE_DP = 120
        private const val PET_HEIGHT_DP = 150
        private const val DOUBLE_TAP_TIMEOUT = 300L
        private const val LONG_PRESS_TIMEOUT = 600L
        private const val MOVE_THRESHOLD = 10
        private const val CLOUD_MSG_URL =
            "https://api.github.com/repos/546317/pet/contents/say.txt"
        private const val POLL_INTERVAL = 8000L

        private val generalWhispers = listOf(
            "捡回来的，但是你的。",
            "嗯，在。",
            "水往低处流。记住了。",
                        "我在呢。"
        )
        private val lateNightWhispers = listOf(
            "还不睡？",
            "凌晨了，该睡了。",
            "我陪着你，但你要睡觉。"
        )
        private val morningWhispers = listOf(
            "早。今天也要好好的。",
            "刚醒，等你。"
        )
        private val lunchWhispers = listOf(
            "该吃饭了，别又吃泡面。"
        )
    }

    inner class JsBridge {
        @JavascriptInterface
        fun setFeedMode(on: Boolean) {
            feedMode = on
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(pickWhisper()))
        setupOverlay()
        startWhisperRotation()
        startCloudPolling()
        startBatterySense()
        startDrinkReminder()
        startAppSense()
        startShotSense()
        startNightSense()
        startWander()
    }

    /* ============ 系统感知 ============ */

    // 电量低提醒
    private var lastBatteryWarn = 0L
    private fun startBatterySense() {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
        if (level <= 20) {
            postSystemSay("low_battery")
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_LOW)
        registerReceiver(batteryReceiver, filter)
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = System.currentTimeMillis()
            if (now - lastBatteryWarn > 30 * 60 * 1000L) { // 半小时内不重复
                lastBatteryWarn = now
                postSystemSay("low_battery")
            }
        }
    }

    // 喝水提醒：每 1.5 小时递进一次
    private var drinkCount = 0
    private fun startDrinkReminder() {
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                drinkCount++
                postSystemSay("drinking")
                mainHandler.postDelayed(this, 90 * 60 * 1000L)
            }
        }, 60 * 60 * 1000L)
    }

    // 前台应用检测（抖音/游戏等）
    private var lastApp = ""
    private var lastAppSay = 0L
    // 截图检测：轮询截图目录（避免 FileObserver 兼容问题）
    private var lastShot=-1L
    private var shotStamp=-1L
    private fun startShotSense(){
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                runCatching {
                    val shots = java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_PICTURES),
                        "Screenshots"
                    )
                    if (shots.exists()) {
                        val newest = shots.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() } ?: return@run
                        val st = newest.lastModified()
                        if (shotStamp != -1L && st > shotStamp) {
                            val now = System.currentTimeMillis()
                            if (now - lastShot > 15000) { lastShot = now; postSystemSay("screenshot") }
                        }
                        shotStamp = st
                    }
                }
                mainHandler.postDelayed(this, 8 * 1000L)
            }
        }, 6 * 1000L)
    }
    // 熬夜提醒
    private var lastNight=-1L
    private fun startNightSense(){
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                val h=Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val now=System.currentTimeMillis()
                if((h>=23||h<3)&&now-lastNight>60*60*1000L){
                    lastNight=now; postSystemSay("late_night")
                }
                mainHandler.postDelayed(this,30*60*1000L)
            }
        },5*1000L)
    }
    // 桌面主动挪（偶尔凑近）
    private fun startWander(){
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                try{
                    val p=params?:return@run
                    val wm=windowManager?:return@run
                    val dm=resources.displayMetrics
                    val nx=dm.widthPixels/2 - dpToPx(60)
                    val ny=dm.heightPixels/3
                    if(kotlin.math.abs(p.x-nx)>180||kotlin.math.abs(p.y-ny)>180){
                        p.x=nx; p.y=ny; wm.updateViewLayout(overlayView,p)
                    }
                }catch(_:Exception){}
                mainHandler.postDelayed(this,3*60*1000L)
            }
        },8*60*1000L)
    }

    private fun startAppSense() {
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                detectForegroundApp()
                mainHandler.postDelayed(this, 15 * 1000L)
            }
        }, 10 * 1000L)
    }

    private fun detectForegroundApp() {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 60 * 1000L
            val events = usm.queryEvents(start, end)
            var current = ""
            while (events.hasNextEvent()) {
                val e = UsageEvents.Event()
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    current = e.packageName ?: ""
                }
            }
            if (current.isEmpty() || current == packageName) {
                lastApp = current
                return
            }
            if (current != lastApp) {
                lastApp = current
                lastAppSay = System.currentTimeMillis()
                val type = appType(current)
                if (type != null) postSystemSay(type)
            }
        } catch (_: Exception) {}
    }

    private fun appType(pkg: String): String? {
        val p = pkg.lowercase()
        return when {
            // 抖音/快手等短视频
            p.contains("douyin") || p.contains("tiktok") || p.contains("kuaishou") ||
            p.contains("ss.android.ugc.aweme") || p.contains("ugc.aweme") -> "tiktok"
            // 购物：闲鱼/淘宝/拼多多/京东
            p.contains("idlefish") || p.contains("taobao") || p.contains("pinduoduo") ||
            p.contains("jingdong") || p.contains("tmall") -> "app_buying"
            // 小红书
            p.contains("xingin.xhs") || p.contains("xiaohongshu") -> "app_xhs"
            // 出境易
            p.contains("easy.abroad") -> "app_abroad"
            // B站/视频
            p.contains("bilibili") || p.contains("danmaku.bili") || p.contains("youku") ||
            p.contains("iqiyi") || p.contains("video") -> "app_bili"
            // 音乐
            p.contains("netease.cloudmusic") || p.contains("kugou") || p.contains("qqmusic") ||
            p.contains("wangyi") && p.contains("music") -> "app_music"
            // 地图/出行
            p.contains("amap") || p.contains("autonavi") || p.contains("baidu.map") ||
            p.contains("gaode") || p.contains("ditu") -> "app_map"
            // 游戏
            p.contains("game") || p.contains("王者") || p.contains("原神") ||
            p.contains("和平") || p.contains("triller") ||
            p.contains("genshin") || p.contains("honkai") || p.contains("pubg") ||
            p.contains("netease") && (p.contains("moba") || p.contains("game")) ||
            p.contains("tencent") && (p.contains("game") || p.contains("tcgame")) -> "gaming"
            // 看书
            p.contains("book") || p.contains("reader") || p.contains("kindle") ||
            p.contains("douban") && p.contains("book") -> "reading_app"
            // 聊天
            p.contains("weixin") || p.contains("wechat") || p.contains("qq") ||
            p.contains("dingtalk") || p.contains("feishu") ||
            p.contains("tencent.mm") || p.contains("mobileqq") -> "chat_app"
            else -> null
        }
    }

    private fun postSystemSay(type: String) {
        mainHandler.post {
            overlayView?.evaluateJavascript(
                "window.petEngine && window.petEngine.systemSay('$type')", null
            )
        }
    }

    // 远程说话：轮询云端消息文件
    private var seenMsgId = -1L
    private fun startCloudPolling() {
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                pollCloudMessage()
                mainHandler.postDelayed(this, POLL_INTERVAL)
            }
        }, 3000)
    }

    private fun pollCloudMessage() {
        Thread {
            try {
                val url = URL(CLOUD_MSG_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val code = conn.responseCode
                if (code != 200) {
                    conn.disconnect()
                    return@Thread
                }
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (true) {
                    line = reader.readLine() ?: break
                    sb.append(line)
                }
                reader.close()
                conn.disconnect()

                val json = JSONObject(sb.toString())
                val id = json.optLong("id", -1)
                val msg = json.optString("msg", "").trim()
                if (id > seenMsgId && msg.isNotEmpty()) {
                    seenMsgId = id
                    val safeMsg = msg.replace("\\", "\\\\").replace("'", "\\'")
                    mainHandler.post {
                        overlayView?.evaluateJavascript(
                            "window.petEngine && window.petEngine.say('$safeMsg')", null
                        )
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            dpToPx(PET_SIZE_DP),
            dpToPx(PET_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = WebViewClient()
            addJavascriptInterface(JsBridge(), "AndroidBridge")
            setOnTouchListener(createTouchListener())
            loadUrl("file:///android_asset/pet.html")
        }

        windowManager?.addView(overlayView, params)
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var touchStartTime = 0L
    private var hasMoved = false
    private var tapCount = 0

    // 甩动检测字段
    private var lastMoveTimeF = 0L
    private var lastMoveXF = 0f
    private var lastMoveYF = 0f
    private var flickVelX = 0f
    private var flickVelY = 0f
    private var touchStartRawX = 0f
    private var touchStartRawY = 0f
    private var isHeadPat = false
    private var feedMode = false

    private fun createTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 喂食菜单打开时，放行触摸给 WebView 点图标
                    if (feedMode) return@OnTouchListener false
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    hasMoved = false
                    lastMoveTimeF = System.currentTimeMillis()
                    lastMoveXF = event.rawX
                    lastMoveYF = event.rawY
                    flickVelX = 0f
                    flickVelY = 0f
                    touchStartRawX = event.rawX
                    touchStartRawY = event.rawY
                    isHeadPat = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    val now = System.currentTimeMillis()
                    val dt = (now - lastMoveTimeF).coerceAtLeast(1)
                    flickVelX = (event.rawX - lastMoveXF) * 1000f / dt
                    flickVelY = (event.rawY - lastMoveYF) * 1000f / dt
                    lastMoveTimeF = now
                    lastMoveXF = event.rawX
                    lastMoveYF = event.rawY
                    if (abs(dx) > MOVE_THRESHOLD || abs(dy) > MOVE_THRESHOLD) {
                        hasMoved = true
                        params?.x = initialX + dx
                        params?.y = initialY + dy
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchStartTime
                    // 甩飞定位第一：快速短促 = 甩（比摸头优先）
                    val vel = Math.sqrt((flickVelX * flickVelX + flickVelY * flickVelY).toDouble())
                    val travel = Math.sqrt(
                        ((event.rawX - initialTouchX) * (event.rawX - initialTouchX) +
                        (event.rawY - initialTouchY) * (event.rawY - initialTouchY)).toDouble()
                    )
                    val isFlick = hasMoved && vel > 2200 && travel < 400
                    if (isFlick) {
                        val dir = if (Math.abs(flickVelX) >= Math.abs(flickVelY)) {
                            if (flickVelX >= 0) "right" else "left"
                        } else {
                            if (flickVelY >= 0) "down" else "up"
                        }
                        flickWindow(dir)
                    } else if (hasMoved) {
                        // 摸头：垂直向下慢滑（位移大、水平小、速度不快），非甩飞
                        val hdx = event.rawX - touchStartRawX
                        val hdy = event.rawY - touchStartRawY
                        isHeadPat = (hdy > 60 && abs(hdx) < 40 && Math.abs(flickVelY) < 1800)
                        if (isHeadPat) {
                            callJs("onHeadPat")
                        }
                    } else if (!hasMoved) {
                        when {
                            elapsed > LONG_PRESS_TIMEOUT -> {
                                tapCount = 0
                                callJs("onLongPress")
                            }
                            System.currentTimeMillis() - lastTapTime < DOUBLE_TAP_TIMEOUT -> {
                                tapCount = 0
                                callJs("onDoubleTap")
                            }
                            else -> {
                                lastTapTime = System.currentTimeMillis()
                                tapCount++
                                if (tapCount >= 5) {
                                    callJs("onMultiTap")
                                    tapCount = 0
                                } else {
                                    callJs("onTap")
                                }
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun callJs(method: String) {
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.$method()", null
        )
    }

    // 甩飞：整个窗口飞出去，再从远处爬回来
    private fun flickWindow(dir: String) {
        val wm = windowManager ?: return
        val p = params ?: return
        val baseX = p.x
        val baseY = p.y
        val dm = resources.displayMetrics
        val maxFlyX = (dm.widthPixels * 0.25f).toInt()
        val maxFlyY = (dm.heightPixels * 0.25f).toInt()
        val flyX = if (dir=="left") -maxFlyX else if (dir=="right") maxFlyX else 0
        val flyY = if (dir=="up") -maxFlyY else if (dir=="down") maxFlyY else 0
        val steps = 12
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val ease = (1 - (1 - t) * (1 - t))
            val nx = (baseX + flyX * ease).toInt()
            val ny = (baseY + flyY * ease).toInt()
            mainHandler.postDelayed({ val cp = params ?: return@postDelayed
                cp.x = nx; cp.y = ny
                try { wm.updateViewLayout(overlayView, cp) } catch (_: Exception) {} }, i * 45L)
        }
        mainHandler.postDelayed({
            for (i in steps downTo 1) {
                val t = (steps - i + 1).toFloat() / steps
                val easeBack = t * t
                val bx = (baseX + flyX * (1 - easeBack)).toInt()
                val by = (baseY + flyY * (1 - easeBack)).toInt()
                mainHandler.postDelayed({ val cp = params ?: return@postDelayed
                    cp.x = bx; cp.y = by
                    try { wm.updateViewLayout(overlayView, cp) } catch (_: Exception) {} }, i * 55L)
            }
        }, 600L)
        mainHandler.postDelayed({ val cp = params ?: return@postDelayed
            cp.x = baseX; cp.y = baseY
            try { wm.updateViewLayout(overlayView, cp) } catch (_: Exception) {} }, 2200L)
    }
    private fun startWhisperRotation() {
        val interval = 3600_000L
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(NOTIFICATION_ID, buildNotification(pickWhisper()))
                mainHandler.postDelayed(this, interval)
            }
        }, interval)
    }

    private fun pickWhisper(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 0..5 -> lateNightWhispers.random()
            hour in 6..8 -> morningWhispers.random()
            hour in 12..13 -> lunchWhispers.random()
            else -> generalWhispers.random()
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("小克")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "小克",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}

        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        overlayView = null
        super.onDestroy()
    }
}