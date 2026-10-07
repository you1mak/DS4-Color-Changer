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
import kotlin.math.roundToInt

class BrightnessSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var listener: ((Int) -> Unit)? = null
    private var dragStateListener: ((Boolean) -> Unit)? = null

    private var value = 0
    private var touchStartX = 0f
    private var touchStartValue = 0
    private var active = false
    private var trackShader: LinearGradient? = null

    fun setValue(value: Int, notify: Boolean = false) {
        this.value = value.coerceIn(-255, 255)
        invalidate()
        if (notify) listener?.invoke(this.value)
    }

    fun getValue(): Int = value

    fun changeBy(delta: Int) {
        val newValue = (value + delta).coerceIn(-255, 255)
        if (newValue == value) {
            invalidate()
            return
        }
        value = newValue
        invalidate()
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        listener?.invoke(value)
    }

    fun isDragging(): Boolean = active

    fun setOnValueChangedListener(listener: (Int) -> Unit) {
        this.listener = listener
    }

    fun setOnDragStateChangedListener(listener: (Boolean) -> Unit) {
        this.dragStateListener = listener
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val left = paddingLeft.toFloat() + 10f
        val right = w - paddingRight.toFloat() - 10f
        trackShader = LinearGradient(
            left,
            0f,
            right,
            0f,
            Color.BLACK,
            Color.WHITE,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f
        val left = paddingLeft.toFloat() + 10f
        val right = width - paddingRight.toFloat() - 10f
        val trackHeight = 12f
        val radius = 14f
        val span = right - left

        trackPaint.shader = trackShader
        canvas.drawRoundRect(
            RectF(left, centerY - trackHeight / 2f, right, centerY + trackHeight / 2f),
            trackHeight,
            trackHeight,
            trackPaint
        )
        trackPaint.shader = null

        val fraction = (value + 255) / 510f
        val thumbX = left + fraction * span

        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 3f
        borderPaint.color = Color.WHITE
        canvas.drawCircle(thumbX, centerY, radius + 1f, borderPaint)
        borderPaint.style = Paint.Style.FILL

        thumbPaint.color = Color.WHITE
        canvas.drawCircle(thumbX, centerY, radius, thumbPaint)
        thumbPaint.color = Color.DKGRAY
        canvas.drawCircle(thumbX, centerY, 4f, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = true
                touchStartX = event.x
                touchStartValue = value
                dragStateListener?.invoke(true)
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!active) return true
                // Continue tracking outside the slider until the finger is released.
                updateFromTouch(event.x)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (active) updateFromTouch(event.x)
                active = false
                dragStateListener?.invoke(false)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun updateFromTouch(x: Float) {
        val left = paddingLeft.toFloat() + 10f
        val right = width - paddingRight.toFloat() - 10f
        val span = (right - left).coerceAtLeast(1f)
        val delta = ((x - touchStartX) / span * 510f).roundToInt()
        val newValue = (touchStartValue + delta).coerceIn(-255, 255)
        if (newValue != value) {
            value = newValue
            invalidate()
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            listener?.invoke(value)
        } else {
            invalidate()
        }
    }
}
