package com.dsh.mobile

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * 扫码取景覆盖层：四周压暗、中央透明，四角绘制取景括号，并有一条上下循环扫动的聚焦线。
 * 不使用图层合成（PorterDuff），仅以四块矩形压暗，保证内存占用极低且无透明通道开销。
 */
class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.ds_scrim)
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = context.getColor(R.color.ds_corner)
    }

    private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = (context.getColor(R.color.ds_corner) and 0x00FFFFFF) or 0x66000000
    }

    // 扫描窗口（取景框）几何
    private var left = 0f
    private var top = 0f
    private var right = 0f
    private var bottom = 0f

    // 扫描聚焦线：0..1 上下往复
    private var scanLine = 0f
    private var animator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startScanLine()
    }

    override fun onDetachedFromWindow() {
        stopScanLine()
        super.onDetachedFromWindow()
    }

    private fun startScanLine() {
        if (animator != null) return
        val a = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                scanLine = va.animatedValue as Float
                postInvalidateOnAnimation()
            }
        }
        animator = a
        a.start()
    }

    private fun stopScanLine() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val size = min(w, h) * 0.68f
        left = (w - size) / 2f
        top = (h - size) / 2f
        right = left + size
        bottom = top + size

        // 四周压暗，中央留透明
        canvas.drawRect(0f, 0f, w, top, scrimPaint)
        canvas.drawRect(0f, bottom, w, h, scrimPaint)
        canvas.drawRect(0f, top, left, bottom, scrimPaint)
        canvas.drawRect(right, top, w, bottom, scrimPaint)

        val len = size * 0.16f
        // 左上
        canvas.drawLine(left, top + len, left, top, cornerPaint)
        canvas.drawLine(left, top, left + len, top, cornerPaint)
        // 右上
        canvas.drawLine(right, top + len, right, top, cornerPaint)
        canvas.drawLine(right, top, right - len, top, cornerPaint)
        // 左下
        canvas.drawLine(left, bottom - len, left, bottom, cornerPaint)
        canvas.drawLine(left, bottom, left + len, bottom, cornerPaint)
        // 右下
        canvas.drawLine(right, bottom - len, right, bottom, cornerPaint)
        canvas.drawLine(right, bottom, right - len, bottom, cornerPaint)

        // 上下往复的扫描聚焦线
        val cy = top + len + scanLine * (bottom - top - 2f * len)
        canvas.drawLine(left + len * 0.5f, cy, right - len * 0.5f, cy, scanLinePaint)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
