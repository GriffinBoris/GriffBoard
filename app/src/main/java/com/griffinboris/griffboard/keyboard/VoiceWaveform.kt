package com.griffinboris.griffboard.keyboard

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator

@SuppressLint("ViewConstructor") // Created by KeyboardView, never inflated from XML.
class VoiceWaveform(context: Context, color: Int) : View(context) {
    private val samples = FloatArray(512)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeCap = Paint.Cap.ROUND
    }
    private var transcribing = false
    private var opacity = 1f
    private val animation = ValueAnimator.ofFloat(1f, 0.4f).apply {
        duration = 1100
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener { opacity = it.animatedValue as Float; invalidate() }
    }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun setTranscribing(value: Boolean) {
        if (transcribing == value) return
        transcribing = value
        opacity = 1f
        if (value && isAttachedToWindow) animation.start() else animation.cancel()
        invalidate()
    }

    fun addSamples(envelope: FloatArray) {
        val count = minOf(envelope.size, samples.size)
        samples.copyInto(samples, 0, count)
        envelope.copyInto(samples, samples.size - count, envelope.size - count)
        invalidate()
    }

    fun clear() { samples.fill(0f); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val step = width.toFloat() / (samples.size / 2)
        paint.strokeWidth = maxOf(1f, step)
        paint.alpha = (opacity * 255).toInt()
        val center = height / 2f
        for (index in 0 until samples.size / 2) {
            val low = (samples[index * 2] * 3f).coerceIn(-1f, 1f)
            val high = (samples[index * 2 + 1] * 3f).coerceIn(-1f, 1f)
            val x = (index + 0.5f) * step
            canvas.drawLine(x, center - high * center, x, center - low * center, paint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (transcribing) animation.start()
    }

    override fun onDetachedFromWindow() {
        animation.cancel()
        super.onDetachedFromWindow()
    }
}
