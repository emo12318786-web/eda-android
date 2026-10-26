package com.deniz.eda.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView

/** Basit, yerel tema seçici. Yalnızca renk tercihini kaydeder. */
class ThemeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeConfig.refresh(this)
        val palette = ThemeConfig.current
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(palette.background.toInt())
        }
        val title = TextView(this).apply {
            text = "EDA • TEMA SEÇİMİ"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AndroidColor.WHITE)
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(title)
        val subtitle = TextView(this).apply {
            text = "Bir görünüm seç. Tercihin otomatik kaydedilir."
            textSize = 14f
            setTextColor(palette.muted.toInt())
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(subtitle)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ThemeConfig.palettes.forEachIndexed { index, item ->
            val isSelected = ThemeConfig.selectedId(this) == item.id
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = GradientDrawable().apply {
                    setColor(item.panel.toInt())
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(if (isSelected) 2 else 1),
                        (if (isSelected) item.accent else item.muted).toInt())
                }
                isClickable = true
                isFocusable = true
            }
            // Thumbnail proves that this theme has its own bundled raster wallpaper.
            val artworkId = resources.getIdentifier("ic_theme_${item.id}", "drawable", packageName)
            val preview = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    setColor(item.panel.toInt())
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), item.accent.toInt())
                }
                clipToOutline = true
                if (artworkId != 0) setImageResource(artworkId)
            }
            val previewParams = LinearLayout.LayoutParams(dp(76), dp(86))
            previewParams.rightMargin = dp(12)
            row.addView(preview, previewParams)

            val swatch = TextView(this).apply {
                text = "●"
                textSize = 22f
                setTextColor(item.accent.toInt())
                setPadding(0, 0, dp(10), 0)
            }
            row.addView(swatch)
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this).apply {
                text = item.name
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(AndroidColor.WHITE)
            })
            labels.addView(TextView(this).apply {
                text = if (isSelected) "✓ Şu anda seçili" else "Temayı uygula"
                textSize = 12f
                setTextColor(item.muted.toInt())
            })
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener {
                ThemeConfig.save(this, item.id)
                // Apply immediately and return to the dashboard, which refreshes its
                // background, card palette and accent colors from the saved theme.
                setResult(Activity.RESULT_OK)
                finish()
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(8)
            list.addView(row, lp)
        }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
