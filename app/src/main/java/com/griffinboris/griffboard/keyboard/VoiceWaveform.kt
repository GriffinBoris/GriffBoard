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
    private val barPeaks = FloatArray(512)
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
        val density = resources.displayMetrics.density
        val bucketCount = samples.size / 2
        val barCount = (width / (6f * density)).toInt().coerceIn(1, bucketCount)
        val step = width.toFloat() / barCount
        paint.strokeWidth = minOf(3f * density, step / 2f)
        paint.alpha = (opacity * 255).toInt()
        val center = height / 2f
        val amplitude = (center - paint.strokeWidth / 2f).coerceAtLeast(0f) * 0.75f
        for (index in 0 until barCount) {
            val start = index * bucketCount / barCount
            val end = (index + 1) * bucketCount / barCount
            var low = samples[start * 2]
            var high = samples[start * 2 + 1]
            for (bucket in start + 1 until end) {
                low = minOf(low, samples[bucket * 2])
                high = maxOf(high, samples[bucket * 2 + 1])
            }
            barPeaks[index * 2] = low
            barPeaks[index * 2 + 1] = high
        }
        for (index in 0 until barCount) {
            val previous = maxOf(0, index - 1) * 2
            val next = minOf(barCount - 1, index + 1) * 2
            val low = barPeaks[previous] * 0.2f + barPeaks[index * 2] * 0.6f + barPeaks[next] * 0.2f
            val high = barPeaks[previous + 1] * 0.2f + barPeaks[index * 2 + 1] * 0.6f + barPeaks[next + 1] * 0.2f
            val x = (index + 0.5f) * step
            canvas.drawLine(x, center - (high * 3f).coerceIn(-1f, 1f) * amplitude,
                x, center - (low * 3f).coerceIn(-1f, 1f) * amplitude, paint)
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
