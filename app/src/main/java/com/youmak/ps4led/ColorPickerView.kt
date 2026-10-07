package com.youmak.ps4led

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Rgb(val r: Int, val g: Int, val b: Int)

    private enum class TouchControl { NONE, SV, HUE }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val svPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blackGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var hue = 240f
    private var sat = 1f
    private var value = 0.31f
    private var listener: ((Rgb) -> Unit)? = null

    private var svBounds = RectF()
    private var hueBounds = RectF()
    private var touchControl = TouchControl.NONE

    private var svHueShader: LinearGradient? = null
    private var blackShader: LinearGradient? = null
    private var hueShader: LinearGradient? = null

    fun setOnColorChangedListener(listener: (Rgb) -> Unit) {
        this.listener = listener
        listener(currentRgb())
    }

    fun setRgb(r: Int, g: Int, b: Int) {
        val hsv = FloatArray(3)
        Color.RGBToHSV(r.coerceIn(1, 255), g.coerceIn(1, 255), b.coerceIn(1, 255), hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        rebuildSvShader()
        invalidate()
        listener?.invoke(currentRgb())
    }

    fun currentRgb(): Rgb {
        val c = Color.HSVToColor(floatArrayOf(hue, sat, value))
        return Rgb(
            Color.red(c).coerceAtLeast(1),
            Color.green(c).coerceAtLeast(1),
            Color.blue(c).coerceAtLeast(1)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val left = 8f
        val top = 10f
        val hueWidth = 68f
        val svW = max(2f, w - hueWidth - 20f)
        val svH = max(2f, h - 52f)
        svBounds = RectF(left, top, left + svW, top + svH)
        hueBounds = RectF(left + svW + 12f, top, left + svW + 12f + hueWidth, top + svH)
        rebuildShaders()
    }

    private fun rebuildShaders() {
        if (width <= 0 || height <= 0) return
        val h = svBounds.height()
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))

        svHueShader = LinearGradient(
            svBounds.left,
            0f,
            svBounds.right,
            0f,
            Color.WHITE,
            hueColor,
            Shader.TileMode.CLAMP
        )
        blackShader = LinearGradient(
            0f,
            svBounds.top,
            0f,
            svBounds.bottom,
            Color.TRANSPARENT,
            Color.BLACK,
            Shader.TileMode.CLAMP
        )

        val hsvColors = intArrayOf(
            Color.RED,
            Color.MAGENTA,
            Color.BLUE,
            Color.CYAN,
            Color.GREEN,
            Color.YELLOW,
            Color.RED
        )
        hueShader = LinearGradient(
            0f,
            hueBounds.top,
            0f,
            hueBounds.bottom,
            hsvColors,
            null,
            Shader.TileMode.CLAMP
        )

        svPaint.shader = svHueShader
        blackGradientPaint.shader = blackShader
        huePaint.shader = hueShader
    }

    private fun rebuildSvShader() {
        if (svBounds.width() <= 0f || svBounds.height() <= 0f) return
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        svHueShader = LinearGradient(
            svBounds.left,
            0f,
            svBounds.right,
            0f,
            Color.WHITE,
            hueColor,
            Shader.TileMode.CLAMP
        )
        svPaint.shader = svHueShader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (svBounds.width() <= 0f || svBounds.height() <= 0f) return

        // HSV square: hue base -> saturation gradient -> value/black gradient.
        canvas.drawRect(svBounds, svPaint)
        canvas.drawRect(svBounds, blackGradientPaint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.WHITE
        val sx = svBounds.left + sat * svBounds.width()
        val sy = svBounds.top + (1f - value) * svBounds.height()
        canvas.drawCircle(sx, sy, 9f, paint)

        // Keep the hue touch mapping exactly as it is; rotate only the visual strip by 180°.
        canvas.save()
        canvas.rotate(180f, hueBounds.centerX(), hueBounds.centerY())
        canvas.drawRect(hueBounds, huePaint)
        canvas.restore()

        // The indicator follows the actual touch position (same mapping as touch input),
        // independent of the strip's visual rotation.
        paint.strokeWidth = 5f
        val hy = hueBounds.top + (hue / 360f) * hueBounds.height()
        canvas.drawRoundRect(
            RectF(hueBounds.left - 2f, hy - 4f, hueBounds.right + 2f, hy + 4f),
            4f,
            4f,
            paint
        )
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchControl = when {
                    svBounds.contains(event.x, event.y) -> TouchControl.SV
                    hueBounds.contains(event.x, event.y) -> TouchControl.HUE
                    else -> TouchControl.NONE
                }
                if (touchControl == TouchControl.NONE) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (touchControl == TouchControl.NONE) return true
                // Keep the same control captured even when the finger leaves its bounds.
                updateFromTouch(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (touchControl != TouchControl.NONE) {
                    updateFromTouch(event.x, event.y)
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                touchControl = TouchControl.NONE
                return true
            }
        }
        return true
    }

    private fun updateFromTouch(x: Float, y: Float) {
        when (touchControl) {
            TouchControl.SV -> {
                sat = ((x - svBounds.left) / svBounds.width()).coerceIn(0f, 1f)
                value = (1f - (y - svBounds.top) / svBounds.height()).coerceIn(0f, 1f)
            }

            TouchControl.HUE -> {
                hue = ((y - hueBounds.top) / hueBounds.height() * 360f).coerceIn(0f, 360f)
                rebuildSvShader()
            }

            TouchControl.NONE -> return
        }
        invalidate()
        listener?.invoke(currentRgb())
    }
}
