package com.hunmeng.novel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Published novel metadata from the same catalog that powers hunmeng-novel.ru. */
data class NativeNovel(
    val slug: String,
    val title: String,
    val author: String,
    val description: String,
    val cover: String,
    val genres: List<String>,
    val tags: List<String>,
    val updatedAt: String,
    val chapters: List<NativeChapter>,
)
data class NativeChapter(val slug: String, val number: Int, val title: String)
data class NativeChapterText(val title: String, val content: String)

/**
 * No WebView or HTML page parsing. Network reads are performed only from Dispatchers.IO.
 * The public catalog and published chapter JSON are cached for offline reopening.
 */
object NativeRepository {
    private const val BASE = "https://hunmeng-novel.ru"
    private val slugPattern = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

    private fun readUrl(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HunmengNovelAndroid/" + BuildConfig.VERSION_NAME)
        }
        try {
            if (conn.responseCode != 200) {
                throw java.io.IOException("HTTP " + conn.responseCode)
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun readCached(context: Context, fileName: String, url: String): String {
        val file = File(context.filesDir, fileName)
        return try {
            val fresh = readUrl(url)
            val pending = File(context.filesDir, fileName + ".tmp")
            pending.writeText(fresh, Charsets.UTF_8)
            if (!pending.renameTo(file)) {
                file.writeText(fresh, Charsets.UTF_8)
                pending.delete()
            }
            fresh
        } catch (error: Exception) {
            if (file.isFile && file.length() > 0) file.readText(Charsets.UTF_8)
            else throw error
        }
    }

    fun catalog(context: Context): List<NativeNovel> {
        val array = JSONArray(readCached(context, "native_catalog.json", BASE + "/catalog.json"))
        val books = ArrayList<NativeNovel>(array.length())
        for (i in 0 until array.length()) {
            val entry = array.getJSONObject(i)
            val chaptersJson = entry.optJSONArray("chapters") ?: JSONArray()
            val chapters = ArrayList<NativeChapter>(chaptersJson.length())
            for (c in 0 until chaptersJson.length()) {
                val chapter = chaptersJson.getJSONObject(c)
                chapters.add(NativeChapter(
                    slug = chapter.optString("slug"),
                    number = chapter.optInt("number", c + 1),
                    title = chapter.optString("title", "Chapter " + (c + 1)),
                ))
            }
            books.add(NativeNovel(
                slug = entry.optString("slug"),
                title = entry.optString("title"),
                author = entry.optString("author"),
                description = entry.optString("description"),
                cover = entry.optString("cover"),
                genres = entry.optJSONArray("genres").asStrings(),
                tags = entry.optJSONArray("tags").asStrings(),
                updatedAt = entry.optString("updatedAt"),
                chapters = chapters,
            ))
        }
        return books.filter { slugPattern.matches(it.slug) }
    }

    fun chapter(context: Context, novelSlug: String, chapterSlug: String): NativeChapterText {
        require(slugPattern.matches(novelSlug) && slugPattern.matches(chapterSlug)) {
            "Invalid chapter identifier"
        }
        val name = "native_chapter_" + novelSlug + "_" + chapterSlug + ".json"
        val url = BASE + "/mobile/chapters/" + novelSlug + "/" + chapterSlug + ".json"
        val json = JSONObject(readCached(context, name, url))
        if (json.optString("novelSlug") != novelSlug || json.optString("slug") != chapterSlug) {
            throw java.io.IOException("Chapter response did not match the requested chapter")
        }
        val content = json.optString("content")
        if (content.isBlank()) throw java.io.IOException("This chapter has no readable text")
        return NativeChapterText(json.optString("title"), content)
    }

    private fun JSONArray?.asStrings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
    }

    fun cover(context: Context, path: String): Bitmap? {
        if (!path.startsWith("/images/") || !path.matches(Regex("^/images/[a-zA-Z0-9_./-]+$"))) {
            return null
        }
        if (path.endsWith(".svg")) return null // A native placeholder is shown for SVG covers.
        val id = MessageDigest.getInstance("SHA-256")
            .digest(path.toByteArray(Charsets.UTF_8)).take(12)
            .joinToString("") { "%02x".format(it) }
        val file = File(context.cacheDir, "cover_" + id)
        if (file.isFile) BitmapFactory.decodeFile(file.absolutePath)?.let { return it }
        return try {
            val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
            }
            try {
                if (conn.responseCode != 200 || conn.contentLengthLong > 6_000_000) return null
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.size > 6_000_000) return null
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) file.writeBytes(bytes)
                bitmap
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
