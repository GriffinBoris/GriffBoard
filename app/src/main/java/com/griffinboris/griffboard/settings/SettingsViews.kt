package com.griffinboris.griffboard.settings

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

class SettingsViews(private val context: Context) {
    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun label(value: String, size: Float = 16f, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, context.dp(4), 0, context.dp(4))
    }
    fun button(value: String, action: () -> Unit) = MaterialButton(context).apply {
        text = value
        isAllCaps = false
        minHeight = context.dp(48)
        setOnClickListener { action() }
    }
    fun card(content: View) = MaterialCardView(context).apply {
        radius = context.dp(20).toFloat()
        cardElevation = 0f
        strokeWidth = context.dp(1)
        setContentPadding(context.dp(18), context.dp(12), context.dp(18), context.dp(12))
        addView(content)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(12) }
    }
    fun section(title: String) = label(title, 21f, true).apply {
        setPadding(0, context.dp(22), 0, context.dp(12))
        isAccessibilityHeading = true
    }
}
