package com.deniz.eda.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView

/**
 * Theme chooser. A profile previews and applies its own artwork, palette,
 * typeface, glyph family, card geometry and dashboard layout signature.
 */
class ThemeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeConfig.refresh(this)
        val selected = ThemeConfig.current
        window.statusBarColor = selected.background.toInt()
        window.navigationBarColor = selected.background.toInt()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(selected.background.toInt())
        }
        val title = TextView(this).apply {
            text = "EDA • TEMA SEÇİMİ"
            textSize = 22f
            typeface = android.graphics.Typeface.create(
                ThemeConfig.androidTypeface(selected, android.graphics.Typeface.BOLD or selected.fontStyle)
            )
            setTextColor(AndroidColor.WHITE)
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(title)
        val subtitle = TextView(this).apply {
            text = "14 ayrı görünüm: renk, yazı tipi, simgeler, kart biçimi ve ana ekran düzeni."
            textSize = 14f
            typeface = ThemeConfig.androidTypeface(selected)
            setTextColor(selected.muted.toInt())
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(subtitle)

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ThemeConfig.palettes.forEach { item ->
            val isSelected = ThemeConfig.selectedId(this) == item.id
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply {
                    setColor(item.panel.toInt())
                    cornerRadius = dp(ThemeConfig.cornerRadius(item, 16)).toFloat()
                    setStroke(
                        dp(if (isSelected) 2 else 1),
                        (if (isSelected) item.accent else item.muted).toInt()
                    )
                }
                isClickable = true
                isFocusable = true
                contentDescription = "${item.name}, ${ThemeConfig.fontLabel(item)}, ${ThemeConfig.shapeLabel(item)}"
            }

            val artworkId = resources.getIdentifier("ic_theme_${item.id}", "drawable", packageName)
            val preview = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    setColor(item.panel.toInt())
                    cornerRadius = dp(ThemeConfig.cornerRadius(item, 12)).toFloat()
                    setStroke(dp(1), item.accent.toInt())
                }
                clipToOutline = true
                if (artworkId != 0) setImageResource(artworkId)
                contentDescription = "${item.name} tema görseli"
            }
            val previewParams = LinearLayout.LayoutParams(dp(76), dp(86))
            previewParams.marginEnd = dp(12)
            row.addView(preview, previewParams)

            val sampleIcon = TextView(this).apply {
                text = ThemeConfig.icon("brand", item)
                textSize = 23f
                typeface = ThemeConfig.androidTypeface(item)
                setTextColor(item.accent.toInt())
                setPadding(0, 0, dp(10), 0)
                gravity = Gravity.CENTER
                contentDescription = "${item.name} simge ailesi"
            }
            row.addView(sampleIcon)

            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this).apply {
                text = item.name
                textSize = 16f
                typeface = android.graphics.Typeface.create(
                    ThemeConfig.androidTypeface(item, android.graphics.Typeface.BOLD or item.fontStyle)
                )
                setTextColor(AndroidColor.WHITE)
            })
            labels.addView(TextView(this).apply {
                text = if (isSelected) "✓ Şu anda seçili" else "Temayı uygula"
                textSize = 12f
                typeface = ThemeConfig.androidTypeface(item)
                setTextColor(item.muted.toInt())
            })
            labels.addView(TextView(this).apply {
                text = "${ThemeConfig.fontLabel(item)}  •  ${ThemeConfig.shapeLabel(item)}"
                textSize = 10f
                typeface = ThemeConfig.androidTypeface(item)
                setTextColor(item.secondary.toInt())
                maxLines = 2
            })
            labels.addView(TextView(this).apply {
                text = "${ThemeConfig.icon("home", item)}  ${ThemeConfig.icon("control", item)}  ${ThemeConfig.icon("system", item)}"
                textSize = 14f
                typeface = ThemeConfig.androidTypeface(item)
                setTextColor(item.accent.toInt())
            })
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            row.setOnClickListener {
                ThemeConfig.save(this, item.id)
                setResult(Activity.RESULT_OK)
                finish()
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            list.addView(row, lp)
        }

        val scroll = ScrollView(this).apply { isFillViewport = true; addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
