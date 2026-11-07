package com.hunmeng.novel.nativeapp

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads only Hunmeng's public, published-content JSON.
 * Does not use HTML scraping, WebView, credentials or Supabase admin keys.
 */
internal class HunmengApi {
    companion object {
        private const val BASE_URL = "https://hunmeng-novel.ru"
        private val slugPattern = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
        internal fun isValidSlug(value: String): Boolean = slugPattern.matches(value)
        internal fun coverUrl(path: String): String? =
            if (path.startsWith("/images/") && !path.contains("..") &&
                !path.contains("?") && !path.contains("#")) "$BASE_URL$path" else null
    }

    fun catalog(): List<Novel> {
        val array = JSONArray(get("/catalog.json"))
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val slug = item.optString("slug")
            if (!isValidSlug(slug)) return@mapNotNull null

            val chapters = item.optJSONArray("chapters") ?: JSONArray()
            val parsedChapters = (0 until chapters.length()).mapNotNull { c ->
                val chapter = chapters.optJSONObject(c) ?: return@mapNotNull null
                val chapterSlug = chapter.optString("slug")
                if (!isValidSlug(chapterSlug)) return@mapNotNull null
                Chapter(
                    slug = chapterSlug,
                    number = chapter.optInt("number", c + 1),
                    title = chapter.optString("title", "Chapter ${c + 1}")
                )
            }.sortedBy { it.number }

            val genresJson = item.optJSONArray("genres") ?: JSONArray()
            val genres = (0 until genresJson.length()).mapNotNull { g ->
                genresJson.optString(g).takeIf(String::isNotBlank)
            }
            Novel(
                slug = slug,
                title = item.optString("title", slug),
                author = item.optString("author", "Unknown author"),
                description = item.optString("description", ""),
                cover = coverUrl(item.optString("cover", "")),
                genres = genres,
                chapters = parsedChapters
            )
        }
    }

    fun chapter(novelSlug: String, chapterSlug: String): ChapterText {
        require(isValidSlug(novelSlug) && isValidSlug(chapterSlug)) { "Invalid chapter path" }
        val encodedNovel = Uri.encode(novelSlug)
        val encodedChapter = Uri.encode(chapterSlug)
        val obj = JSONObject(get("/mobile/chapters/$encodedNovel/$encodedChapter.json"))
        if (obj.optString("novelSlug") != novelSlug || obj.optString("slug") != chapterSlug ||
            obj.optString("format") != "markdown") {
            throw IOException("Unexpected chapter response")
        }
        return ChapterText(obj.optString("title", "Chapter"), obj.optString("content", ""))
    }

    private fun get(path: String): String {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 20000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Server returned HTTP $code")
            val type = connection.contentType.orEmpty()
            if (!type.contains("application/json", ignoreCase = true)) {
                throw IOException("The server returned a non-JSON response")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

internal data class Chapter(
    val slug: String,
    val number: Int,
    val title: String
)
internal data class Novel(
    val slug: String,
    val title: String,
    val author: String,
    val description: String,
    val cover: String?,
    val genres: List<String>,
    val chapters: List<Chapter>
)
internal data class ChapterText(val title: String, val content: String)
