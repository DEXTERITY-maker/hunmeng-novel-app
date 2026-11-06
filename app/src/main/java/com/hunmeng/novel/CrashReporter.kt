package com.hunmeng.novel

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Запоминает текст последней ошибки. При следующем запуске показывает его
 * с кнопкой «Скопировать», чтобы его можно было прислать разработчику.
 */
object CrashReporter {

    private const val PREFS = "crash"
    private const val KEY = "last_crash"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, previous))
    }

    private class Handler(val context: Context, val previous: Thread.UncaughtExceptionHandler?) :
        Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            try { save(context, e, commitNow = true) } catch (ignored: Throwable) { }
            previous?.uncaughtException(t, e)
        }
    }

    fun save(context: Context, e: Throwable, commitNow: Boolean = false) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val text = "Hunmeng Novel ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}, " +
            "${Build.MANUFACTURER} ${Build.MODEL}\n\n" + sw.toString().take(6000)
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, text)
        if (commitNow) edit.commit() else edit.apply()
    }

    fun showLastCrash(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val text = prefs.getString(KEY, null) ?: return
        prefs.edit().remove(KEY).apply()
        HunmengDialog(activity)
            .title(activity.getString(R.string.crash_title))
            .text(activity.getString(R.string.crash_text))
            .rows(listOf(R.drawable.update_icon_test to text.lines().take(6).joinToString("\n")))
            .buttons(activity.getString(R.string.crash_close), {}, activity.getString(R.string.crash_copy)) {
                val clipboard = activity.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("Hunmeng Novel error", text))
                Toast.makeText(activity, R.string.crash_copied, Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
