package com.dsh.mobile

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 触感反馈封装：轻触 / 成功两种强度，兼容 API 24+（Vibrator 与 VibratorManager 分支）。
 */
object Haptics {

    /** 轻触（按钮按压 / 扫码确认） */
    fun light(context: Context) {
        vibrate(context, 18, null)
    }

    /** 成功（识别到二维码等），双脉冲更有确认感 */
    fun success(context: Context) {
        vibrate(context, 0, longArrayOf(0L, 24L, 60L, 44L))
    }

    private fun vibrate(context: Context, ms: Long, timings: LongArray?) {
        try {
            val v = getVibrator(context)
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (timings != null) {
                    VibrationEffect.createWaveform(timings, -1)
                } else {
                    VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                v.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                if (timings != null) v.vibrate(timings, -1) else v.vibrate(ms)
            }
        } catch (_: Exception) {
            // 无振动器或权限异常时静默忽略
        }
    }

    private fun getVibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            mgr.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
}
