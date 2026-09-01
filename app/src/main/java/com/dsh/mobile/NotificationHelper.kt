package com.dsh.mobile

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** 任务完成通知的共享构建逻辑（MainActivity 与 MonitorService 共用）。 */
object NotificationHelper {

    private const val CHANNEL_TASK_DONE = "dsh_task_done"
    private const val NOTIF_ID_TASK_DONE = 1001

    /** 发一条「任务完成」系统通知；未授权通知权限则静默跳过。 */
    fun postTaskDone(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_TASK_DONE,
                    context.getString(R.string.notification_channel_task_done),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val launch = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_DONE)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_task_done_title))
            .setContentText(context.getString(R.string.notification_task_done_text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(launch)
            .build()

        try {
            manager.notify(NOTIF_ID_TASK_DONE, notification)
        } catch (e: Exception) {
            // 通知失败不致命（部分 OEM 限制），忽略。
        }
    }
}
