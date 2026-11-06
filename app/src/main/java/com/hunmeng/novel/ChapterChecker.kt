package com.hunmeng.novel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Уведомления о новых главах.
 * Раз в несколько часов (через системный планировщик) смотрит список глав на сайте
 * и в Supabase (главы сериалов по расписанию). Новые главы, которых раньше не было,
 * показывает уведомлением. Нажатие открывает главу прямо в приложении.
 */
object ChapterChecker {

    private const val SITE = "https://hunmeng-novel.ru"
    private const val CATALOG_URL = "$SITE/catalog.json"
    private const val SUPABASE_URL = "https://kvgnjsntumofwqhywiea.supabase.co"
    // Публичный (publishable) ключ сайта — тот же, что открыт в браузере; секретов здесь нет.
    private const val SUPABASE_KEY = "sb_publishable_8nleVqBbbyDEbgyMWPkS6A_oEHkisoN"

    private const val PREFS = "chapters"
    private const val KEY_KNOWN = "known_keys"
    private const val KEY_BASELINE = "baseline_done"

    private const val CHANNEL_ID = "new_chapters"
    private const val JOB_ID = 1001
    private const val CHECK_EVERY_MS = 3L * 60 * 60 * 1000 // раз в 3 часа
    private const val MAX_SEPARATE = 3 // больше — одно общее уведомление

    data class Chapter(val novelSlug: String, val novelTitle: String, val number: Int,
                       val title: String, val url: String) {
        val key get() = "$novelSlug:$number"
    }

    /** Ставит периодическую проверку (повторный вызов ничего не дублирует). */
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) != null) return
        fun job(persisted: Boolean) = JobInfo.Builder(JOB_ID, ComponentName(context, ChapterCheckJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(CHECK_EVERY_MS)
            .setPersisted(persisted)
            .build()
        try {
            scheduler.schedule(job(persisted = true)) // переживает перезагрузку телефона
        } catch (e: Exception) {
            // vivo/iQOO и другие оболочки без «Автозапуска» запрещают такую задачу —
            // ставим обычную: она работает, пока телефон не перезагрузят (при запуске приложения поставится снова)
            try { scheduler.schedule(job(persisted = false)) } catch (ignored: Exception) { }
        }
    }

    /** Первый запуск: запоминаем все уже вышедшие главы, чтобы не завалить уведомлениями. */
    fun baselineIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BASELINE, false)) return
        Thread { try { run(context) } catch (ignored: Exception) { } }.start()
    }

    /** Основная проверка. Возвращает false, если сайт недоступен (планировщик повторит позже). */
    fun run(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val catalog = fetchCatalog() ?: return false
        val serial = fetchSerialChapters(catalog.second) // может быть пустым — не страшно
        val all = (catalog.first + serial).distinctBy { it.key }

        val known = prefs.getStringSet(KEY_KNOWN, emptySet())!!.toMutableSet()
        val firstRun = !prefs.getBoolean(KEY_BASELINE, false)
        val fresh = all.filter { it.key !in known }.sortedWith(compareBy({ it.novelTitle }, { it.number }))

        known.addAll(all.map { it.key }) // список только растёт: удалённые главы не «всплывут» заново
        prefs.edit().putStringSet(KEY_KNOWN, known).putBoolean(KEY_BASELINE, true).apply()

        if (!firstRun && fresh.isNotEmpty()) notify(context, fresh)
        return true
    }

    // ---------- Загрузка данных ----------

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "HunmengNovelApp/${BuildConfig.VERSION_NAME}")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun absolute(url: String) = if (url.startsWith("http")) url else SITE + url

    /** Главы из catalog.json и словарь «slug книги → название». */
    private fun fetchCatalog(): Pair<List<Chapter>, Map<String, String>>? {
        val text = get(CATALOG_URL) ?: return null
        val novels = try { JSONArray(text) } catch (e: Exception) { return null }
        val chapters = mutableListOf<Chapter>()
        val titles = mutableMapOf<String, String>()
        for (i in 0 until novels.length()) {
            val n = novels.getJSONObject(i)
            val slug = n.optString("slug")
            if (slug.isEmpty()) continue
            val novelTitle = n.optString("title", slug)
            titles[slug] = novelTitle
            val list = n.optJSONArray("chapters") ?: continue
            for (j in 0 until list.length()) {
                val c = list.getJSONObject(j)
                val number = c.optInt("number", 0)
                if (number <= 0) continue
                val url = c.optString("url").ifEmpty { "/novel/$slug/chapter-$number/" }
                chapters += Chapter(slug, novelTitle, number, c.optString("title"), absolute(url))
            }
        }
        return chapters to titles
    }

    /** Главы сериалов, открытые по расписанию (Supabase отдаёт только уже вышедшие). */
    private fun fetchSerialChapters(titles: Map<String, String>): List<Chapter> {
        val text = get(
            "$SUPABASE_URL/rest/v1/serial_chapters?select=novel_slug,number,title&order=number.asc",
            mapOf("apikey" to SUPABASE_KEY),
        ) ?: return emptyList()
        val rows = try { JSONArray(text) } catch (e: Exception) { return emptyList() }
        val result = mutableListOf<Chapter>()
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            val slug = r.optString("novel_slug")
            val number = r.optInt("number", 0)
            val novelTitle = titles[slug] ?: continue // книги нет на сайте — пропускаем
            if (number <= 0) continue
            result += Chapter(slug, novelTitle, number, r.optString("title"),
                "$SITE/novel/$slug/chapter-$number/")
        }
        return result
    }

    // ---------- Уведомления ----------

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.notif_channel_desc) }
        manager.createNotificationChannel(channel)
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") ==
            PackageManager.PERMISSION_GRANTED

    private fun openIntent(context: Context, url: String, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @Suppress("DEPRECATION")
    private fun builder(context: Context): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL_ID)
        else Notification.Builder(context)

    private fun notify(context: Context, fresh: List<Chapter>) {
        if (!canNotify(context)) return
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val color = 0xFF2F6B4F.toInt()

        if (fresh.size <= MAX_SEPARATE) {
            for (ch in fresh) {
                val text = context.getString(R.string.notif_chapter, ch.number, ch.title).trimEnd(':', ' ')
                val n = builder(context)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(color)
                    .setContentTitle(ch.novelTitle)
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setContentIntent(openIntent(context, ch.url, ch.key.hashCode()))
                    .setAutoCancel(true)
                    .build()
                manager.notify(ch.key.hashCode(), n)
            }
        } else {
            val novels = fresh.map { it.novelTitle }.distinct()
            val inbox = Notification.InboxStyle()
            fresh.take(6).forEach { inbox.addLine("${it.novelTitle} — ${it.number}") }
            val n = builder(context)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(color)
                .setContentTitle(context.getString(R.string.notif_many, fresh.size))
                .setContentText(novels.joinToString(", "))
                .setStyle(inbox)
                .setContentIntent(openIntent(context, "$SITE/updates/", 1))
                .setAutoCancel(true)
                .build()
            manager.notify(1, n)
        }
    }
}
