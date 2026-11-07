package com.hunmeng.novel.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A Compose reader: no Android WebView, HTML injection, or browser rendering. */
@Composable
internal fun ReaderScreen(
    book: Novel,
    chapterSlug: String,
    api: HunmengApi,
    onSelectChapter: (Chapter) -> Unit
) {
    var content by remember(book.slug, chapterSlug) { mutableStateOf<ChapterText?>(null) }
    var error by remember(book.slug, chapterSlug) { mutableStateOf<String?>(null) }
    var retry by remember(book.slug, chapterSlug) { mutableIntStateOf(0) }
    var size by rememberSaveable { mutableFloatStateOf(18f) }
    val chapterIndex = book.chapters.indexOfFirst { it.slug == chapterSlug }
    val previous = book.chapters.getOrNull(chapterIndex - 1)
    val next = if (chapterIndex >= 0) book.chapters.getOrNull(chapterIndex + 1) else null

    LaunchedEffect(book.slug, chapterSlug, retry) {
        content = null
        error = null
        withContext(Dispatchers.IO) {
            runCatching { api.chapter(book.slug, chapterSlug) }
        }.onSuccess { content = it }.onFailure {
            error = it.message ?: "Could not open this chapter"
        }
    }

    when {
        error != null -> StatusPanel("Chapter unavailable", error.orEmpty()) { retry++ }
        content == null -> Column(Modifier.fillMaxSize().padding(24.dp)) {
            CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
            Text("Loading chapter…")
        }
        else -> {
            val chapter = content ?: return
            val paragraphs = remember(chapter.content) { toReaderParagraphs(chapter.content) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(book.title, style = MaterialTheme.typography.labelLarge, color = Color(0xFF527463))
                    Text(chapter.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { size = (size - 2).coerceAtLeast(14f) }) {
                            Text("A−")
                        }
                        OutlinedButton(onClick = { size = (size + 2).coerceAtMost(30f) }) {
                            Text("A+")
                        }
                    }
                }
                items(paragraphs) { paragraph ->
                    Text(
                        paragraph,
                        fontSize = size.sp,
                        lineHeight = (size * 1.6f).sp,
                        color = Color(0xFF293D34)
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { previous?.let(onSelectChapter) },
                            enabled = previous != null
                        ) { Text("← Previous") }
                        Button(
                            onClick = { next?.let(onSelectChapter) },
                            enabled = next != null
                        ) { Text("Next →") }
                    }
                }
            }
        }
    }
}

/**
 * Text-only Markdown approximation for the first prototype.
 * We intentionally do not interpret HTML or execute scripts.
 * In the next version this will become a structured Markdown renderer.
 */
internal fun toReaderParagraphs(raw: String): List<String> =
    raw.replace("\r\n", "\n")
        .replace(Regex("(?m)^#{1,6}\\s+"), "")
        .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace("**", "")
        .replace("__", "")
        .trim()
        .split(Regex("\\n\\s*\\n"))
        .map(String::trim)
        .filter(String::isNotBlank)
