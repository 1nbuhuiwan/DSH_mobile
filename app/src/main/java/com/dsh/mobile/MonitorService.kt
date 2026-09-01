package com.dsh.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 前台「常驻监控」服务。
 *
 * 作用：保持应用进程存活，让注入脚本在后台仍能运行（检测任务完成并弹通知）。
 * 同时这里也做一层**原生兜底轮询**：直接调用 DSH 的 `/api/rpc session.list`
 * （每个会话带 `running` 布尔），检测「running=true → false」即 DeepSeek 回合完成，
 * 弹【任务完成】通知。即使后台 WebView 里的 JS 被系统暂停，这层原生轮询也依然可靠。
 */
class MonitorService : Service() {

    private var pollingStop = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CH_MONITOR,
                        getString(R.string.notification_channel_monitor),
                        NotificationManager.IMPORTANCE_LOW // 常驻，用低优先级，不打扰
                    )
                )
            }

            val launch = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification: Notification = NotificationCompat.Builder(this, CH_MONITOR)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.notification_monitor_title))
                .setContentText(getString(R.string.notification_monitor_text))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(launch)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID_MONITOR, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID_MONITOR, notification)
            }

            startPolling()
        } catch (e: Exception) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingStop = true
    }

    // ------------------------------------------------------------ polling

    /** 后台线程轮询 DSH 会话 running 状态；检测到「正在跑 → 停止」即弹任务完成通知。 */
    private fun startPolling() {
        pollThread = Thread {
            var wasRunning: Boolean? = null
            while (!pollingStop) {
                try {
                    val base = getSharedPreferences("dsh_mobile", MODE_PRIVATE).getString("dsh_base", null)
                    if (base != null) {
                        val running = pollRunning(base)
                        if (running != null) {
                            if (wasRunning == true && running == false) {
                                wasRunning = false
                                NotificationHelper.postTaskDone(this@MonitorService)
                            } else {
                                wasRunning = running
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 单次轮询失败不致命，接着重试
                }
                try { Thread.sleep(1600) } catch (e: InterruptedException) { break }
            }
        }
        pollThread?.isDaemon = true
        pollThread?.start()
    }

    /** 调用 `/api/rpc session.list`，返回当前是否有会话正在运行；失败返回 null。 */
    private fun pollRunning(base: String): Boolean? {
        val api = base.trimEnd('/') + "/api/rpc"
        val cookie = CookieManager.getInstance().getCookie(base) ?: ""
        val conn: HttpURLConnection = try {
            (URL(api).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (cookie.isNotBlank()) setRequestProperty("Cookie", cookie)
            }
        } catch (e: Exception) {
            return null
        }

        return try {
            conn.outputStream.use { it.write("""{"method":"session.list","payload":{}}""".toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) return null
            val text = conn.inputStream.bufferedReader().readText()
            val j = JSONObject(text)
            val value = j.optJSONObject("value") ?: return null
            val items = value.optJSONArray("items") ?: return false
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                if (it.optBoolean("running", false)) return true
            }
            false
        } catch (e: Exception) {
            null
        } finally {
            try { conn.disconnect() } catch (e: Exception) {}
        }
    }

    private var pollThread: Thread? = null

    companion object {
        const val CH_MONITOR = "dsh_monitor"
        const val NOTIF_ID_MONITOR = 1002
    }
}
