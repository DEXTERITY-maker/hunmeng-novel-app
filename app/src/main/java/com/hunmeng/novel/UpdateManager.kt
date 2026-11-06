package com.hunmeng.novel

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Встроенные обновления.
 * При запуске спрашивает GitHub о последнем релизе. Если версия новее установленной,
 * предлагает обновиться, скачивает APK прямо в системный установщик и запускает установку.
 */
class UpdateManager(private val activity: Activity) {

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/DEXTERITY-maker/hunmeng-novel-app/releases/latest"
        private const val PREFS = "updates"
        private const val KEY_POSTPONED_VERSION = "postponed_version"
        private const val KEY_POSTPONED_AT = "postponed_at"
        private const val REMIND_AFTER_MS = 24L * 60 * 60 * 1000 // «Позже» — спросим снова через сутки

        /** "v0.2.0" → [0, 2, 0]; сравнение по частям. */
        fun isNewer(remote: String, local: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
                .split('.', '-').map { it.toIntOrNull() ?: 0 }
            val r = parts(remote)
            val l = parts(local)
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrElse(i) { 0 }
                val b = l.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }

    private data class Release(val version: String, val notes: String, val apkUrl: String, val apkSize: Long)

    private val main = Handler(Looper.getMainLooper())
    private val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var pendingRelease: Release? = null

    /** Проверка при запуске. manual = true — пользователь сам попросил проверить. */
    fun check(manual: Boolean = false) {
        Thread {
            val release = try { fetchLatest() } catch (e: Exception) { null }
            main.post {
                if (activity.isFinishing) return@post
                when {
                    release == null ->
                        if (manual) toast(R.string.update_check_failed)
                    !isNewer(release.version, BuildConfig.VERSION_NAME) ->
                        if (manual) toast(R.string.update_latest)
                    !manual && wasPostponed(release.version) -> Unit
                    else -> offer(release)
                }
            }
        }.start()
    }

    /** Вызывается из onResume: если ушли в настройки за разрешением — продолжаем установку. */
    fun onResume() {
        val release = pendingRelease ?: return
        if (canInstall()) {
            pendingRelease = null
            download(release)
        }
    }

    private fun fetchLatest(): Release? {
        val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "HunmengNovelApp/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk", ignoreCase = true)) {
                    return Release(
                        version = json.getString("tag_name"),
                        notes = json.optString("body", "").trim(),
                        apkUrl = asset.getString("browser_download_url"),
                        apkSize = asset.optLong("size", -1L),
                    )
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun wasPostponed(version: String): Boolean {
        val postponed = prefs.getString(KEY_POSTPONED_VERSION, null)
        val at = prefs.getLong(KEY_POSTPONED_AT, 0L)
        return postponed == version && System.currentTimeMillis() - at < REMIND_AFTER_MS
    }

    private fun offer(release: Release) {
        val version = release.version.removePrefix("v")
        val message = buildString {
            append(activity.getString(R.string.update_message, version, BuildConfig.VERSION_NAME))
            if (release.notes.isNotEmpty()) append("\n\n").append(cleanNotes(release.notes))
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_now) { _, _ -> startInstall(release) }
            .setNegativeButton(R.string.update_later) { _, _ ->
                prefs.edit()
                    .putString(KEY_POSTPONED_VERSION, release.version)
                    .putLong(KEY_POSTPONED_AT, System.currentTimeMillis())
                    .apply()
            }
            .show()
    }

    /** Убираем из описания релиза разметку Markdown, чтобы текст читался в окне. */
    private fun cleanNotes(notes: String): String =
        notes.lines().joinToString("\n") { line ->
            line.replace(Regex("^#+\\s*"), "")
                .replace(Regex("^[-*]\\s+"), "• ")
                .replace("**", "")
                .replace("`", "")
        }.trim()

    private fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            activity.packageManager.canRequestPackageInstalls()

    private fun startInstall(release: Release) {
        if (canInstall()) {
            download(release)
            return
        }
        // Android 8+: нужно разрешение «Установка неизвестных приложений» для Hunmeng Novel
        pendingRelease = release
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_permission_title)
            .setMessage(R.string.update_permission_text)
            .setPositiveButton(R.string.update_permission_open) { _, _ ->
                try {
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}"))
                    )
                } catch (e: Exception) {
                    pendingRelease = null
                    toast(R.string.update_failed)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> pendingRelease = null }
            .show()
    }

    private fun download(release: Release) {
        val density = activity.resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = release.apkSize <= 0
        }
        val label = TextView(activity).apply { text = "0%" }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(bar)
            addView(label)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.update_downloading)
            .setView(box)
            .setCancelable(false)
            .show()

        Thread {
            val ok = try {
                installViaSession(release) { percent ->
                    main.post {
                        bar.progress = percent
                        label.text = "$percent%"
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
            main.post {
                if (dialog.isShowing) dialog.dismiss()
                if (!ok) toast(R.string.update_failed)
                // При успехе дальше показывается системное окно установки (см. UpdateReceiver)
            }
        }.start()
    }

    /** Скачивает APK прямо в сессию системного установщика и отправляет её на установку. */
    private fun installViaSession(release: Release, onProgress: (Int) -> Unit) {
        val installer = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(activity.packageName) }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "HunmengNovelApp/${BuildConfig.VERSION_NAME}")
            }
            try {
                if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
                val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else release.apkSize
                conn.inputStream.use { input ->
                    session.openWrite("update.apk", 0, total).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPercent = -1
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            if (total > 0) {
                                val percent = (done * 100 / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) { lastPercent = percent; onProgress(percent) }
                            }
                        }
                        session.fsync(out)
                    }
                }
            } finally {
                conn.disconnect()
            }

            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
            val callback = PendingIntent.getBroadcast(
                activity, sessionId,
                Intent(activity, UpdateReceiver::class.java),
                flags,
            )
            session.commit(callback.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }

    private fun toast(res: Int) = Toast.makeText(activity, res, Toast.LENGTH_LONG).show()
}
