package com.hunmeng.novel

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/**
 * Фирменные окна Hunmeng Novel (по утверждённому макету окна обновления):
 * кремовая карточка с большими скруглениями, орнамент с листьями, заголовок с засечками,
 * «пилюля» с версиями, строки с иконками и две кнопки. Есть светлая и тёмная тема.
 */
class HunmengDialog(private val activity: Activity) {

    private class Palette(
        val card: Int, val title: Int, val body: Int,
        val pill: Int, val pillText: Int,
        val iconBox: Int, val icon: Int,
        val primary: Int, val onPrimary: Int,
        val outline: Int, val outlineText: Int,
        val track: Int,
    )

    private val dark = (activity.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val c = if (!dark) Palette(
        card = 0xFFF9F2E6.toInt(), title = 0xFF1C2620.toInt(), body = 0xFF2C3731.toInt(),
        pill = 0xFFE6E5D3.toInt(), pillText = 0xFF1E5641.toInt(),
        iconBox = 0xFFEAE5D6.toInt(), icon = 0xFF194B37.toInt(),
        primary = 0xFF0F5941.toInt(), onPrimary = Color.WHITE,
        outline = 0xFF9DB2A4.toInt(), outlineText = 0xFF0F5941.toInt(),
        track = 0xFFE3E0CF.toInt(),
    ) else Palette(
        card = 0xFF1E2723.toInt(), title = 0xFFF3EDE0.toInt(), body = 0xFFD8D3C6.toInt(),
        pill = 0xFF2B3832.toInt(), pillText = 0xFFBCD6C4.toInt(),
        iconBox = 0xFF2B3832.toInt(), icon = 0xFFBCD6C4.toInt(),
        primary = 0xFF2E7D5E.toInt(), onPrimary = Color.WHITE,
        outline = 0xFF5D7A6A.toInt(), outlineText = 0xFFCFE3D6.toInt(),
        track = 0xFF34433C.toInt(),
    )

    val dialog = Dialog(activity).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
    private val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(24), dp(22), dp(24), dp(24))
        background = GradientDrawable().apply {
            setColor(c.card)
            cornerRadius = dp(30).toFloat()
        }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), activity.resources.displayMetrics).toInt()

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1) + 1, stroke)
    }

    // ---------- Элементы ----------

    fun ornament(): HunmengDialog {
        content.addView(ImageView(activity).apply {
            setImageResource(R.drawable.update_ornament)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        return this
    }

    fun title(text: CharSequence): HunmengDialog {
        content.addView(TextView(activity).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setTextColor(c.title)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        })
        return this
    }

    fun pill(text: CharSequence): HunmengDialog {
        content.addView(TextView(activity).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(c.pillText)
            gravity = Gravity.CENTER
            background = rounded(c.pill, 50)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(18) })
        return this
    }

    fun text(text: CharSequence): HunmengDialog {
        content.addView(TextView(activity).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(c.body)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(18) })
        return this
    }

    /** Строки с иконками (описание версии). Прокручиваются, если текста много. */
    fun rows(items: List<Pair<Int, CharSequence>>): HunmengDialog {
        if (items.isEmpty()) return this
        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.update_whats_new)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(c.pillText)
            gravity = Gravity.START
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        for ((icon, line) in items) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                setPadding(0, 0, 0, dp(14))
            }
            val box = FrameLayout(activity).apply { background = rounded(c.iconBox, 12) }
            box.addView(ImageView(activity).apply {
                setImageResource(icon)
                setColorFilter(c.icon, PorterDuff.Mode.SRC_IN)
            }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            row.addView(box, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
            row.addView(TextView(activity).apply {
                text = line
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
                setTextColor(c.body)
                setLineSpacing(0f, 1.12f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            list.addView(row)
        }
        val scroll = ScrollView(activity).apply { isVerticalScrollBarEnabled = false; addView(list) }
        val maxHeight = (activity.resources.displayMetrics.heightPixels * 0.38).toInt()
        list.measure(
            View.MeasureSpec.makeMeasureSpec(dialogWidth() - dp(48), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.UNSPECIFIED)
        val h = minOf(list.measuredHeight, maxHeight)
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
            .apply { bottomMargin = dp(6) })
        return this
    }

    /** Управление состоянием полоски загрузки из основного потока. */
    class DownloadProgress(
        val onPercent: (Int) -> Unit,
        val onComplete: () -> Unit,
    )

    /**
     * Загрузка APK: процент показывается, если размер известен;
     * при неизвестном размере — анимированная полоска и понятная подпись.
     */
    fun progress(indeterminate: Boolean): DownloadProgress {
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = indeterminate
            progressTintList = ColorStateList.valueOf(c.primary)
            progressBackgroundTintList = ColorStateList.valueOf(c.track)
            indeterminateTintList = ColorStateList.valueOf(c.primary)
        }
        val label = TextView(activity).apply {
            text = if (indeterminate) activity.getString(R.string.update_download_preparing) else "0%"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(c.pillText)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        content.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)))
        content.addView(label)
        return DownloadProgress(
            onPercent = { p ->
                val safePercent = p.coerceIn(0, 99)
                bar.isIndeterminate = false
                bar.progress = safePercent
                label.text = "${safePercent}%"
            },
            onComplete = {
                bar.isIndeterminate = false
                bar.progress = 100
                label.text = activity.getString(R.string.update_download_complete)
            },
        )
    }

    /** Две кнопки: слева контурная (secondary), справа залитая (primary). */
    fun buttons(secondary: CharSequence?, onSecondary: () -> Unit,
                primary: CharSequence, onPrimary: () -> Unit): HunmengDialog {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        fun button(label: CharSequence, filled: Boolean, action: () -> Unit) = TextView(activity).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            if (filled) {
                setTextColor(c.onPrimary)
                background = rounded(c.primary, 50)
            } else {
                setTextColor(c.outlineText)
                background = rounded(c.card, 50, c.outline)
            }
            setOnClickListener { dialog.dismiss(); action() }
        }
        if (secondary != null) {
            row.addView(button(secondary, false, onSecondary),
                LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(12) })
        }
        row.addView(button(primary, true, onPrimary), LinearLayout.LayoutParams(0, dp(54), 1f))
        content.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return this
    }

    // ---------- Показ ----------

    private fun dialogWidth(): Int =
        minOf(activity.resources.displayMetrics.widthPixels - dp(32), dp(440))

    fun show(cancelable: Boolean = true): Dialog {
        dialog.setContentView(content)
        dialog.setCancelable(cancelable)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(dialogWidth(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.45f)
        }
        dialog.show()
        return dialog
    }

    companion object {
        /** Превращает **жирный** из описания версии в настоящий жирный шрифт. */
        fun withBold(source: String): CharSequence {
            val out = SpannableStringBuilder()
            val parts = source.split("**")
            parts.forEachIndexed { i, part ->
                val start = out.length
                out.append(part)
                if (i % 2 == 1) out.setSpan(StyleSpan(Typeface.BOLD), start, out.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return out
        }

        /**
         * Строки описания версии → (иконка, текст).
         * Маркеры в начале пункта: [test] — колба, [safe] — щит; без маркера — стрелка «новое».
         * Строки-заголовки, заканчивающиеся двоеточием, пропускаются.
         */
        fun parseNotes(notes: String): List<Pair<Int, CharSequence>> =
            notes.lines().map { it.trim() }
                .filter { it.isNotEmpty() && !it.endsWith(":") && !it.startsWith("#") }
                .map { raw ->
                    var line = raw.removePrefix("- ").removePrefix("* ").removePrefix("• ").trim()
                    val icon = when {
                        line.startsWith("[test]", true) -> R.drawable.update_icon_test
                        line.startsWith("[safe]", true) -> R.drawable.update_icon_safe
                        else -> R.drawable.update_icon_new
                    }
                    line = line.replace(Regex("^\\[(test|safe|new)]\\s*", RegexOption.IGNORE_CASE), "")
                    icon to withBold(line.replace("`", ""))
                }
    }
}
