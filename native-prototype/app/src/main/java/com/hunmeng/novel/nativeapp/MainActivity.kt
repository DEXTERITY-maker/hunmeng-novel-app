package com.hunmeng.novel.nativeapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Forest = Color(0xFF174B3D)
private val Sand = Color(0xFFF8F4EB)
private val Paper = Color(0xFFFFFDF7)
private val Ink = Color(0xFF233D34)

private sealed interface Screen {
    data object Catalog : Screen
    data object Library : Screen
    data class Detail(val slug: String) : Screen
    data class Reader(val novelSlug: String, val chapterSlug: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HunmengNativeApp() }
    }
}

@Composable
private fun HunmengNativeApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { context.getSharedPreferences("native_library", Context.MODE_PRIVATE) }
    val savedSlugs = remember {
        mutableStateListOf<String>().apply {
            addAll(preferences.getStringSet("saved_slugs", emptySet()).orEmpty())
        }
    }
    var screen by remember { mutableStateOf<Screen>(Screen.Catalog) }
    var books by remember { mutableStateOf<List<Novel>>(emptyList()) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    val api = remember { HunmengApi() }

    LaunchedEffect(refresh) {
        loading = true
        catalogError = null
        val result = withContext(Dispatchers.IO) { runCatching { api.catalog() } }
        result.onSuccess { books = it }.onFailure {
            catalogError = it.message ?: "Could not load the library"
        }
        loading = false
    }

    val back: () -> Unit = {
        screen = when (val current = screen) {
            Screen.Catalog, Screen.Library -> Screen.Catalog
            is Screen.Detail -> Screen.Catalog
            is Screen.Reader -> Screen.Detail(current.novelSlug)
        }
    }
    BackHandler(screen != Screen.Catalog) { back() }

    val toggleSaved: (String) -> Unit = { slug ->
        if (savedSlugs.contains(slug)) savedSlugs.remove(slug) else savedSlugs.add(slug)
        preferences.edit().putStringSet("saved_slugs", savedSlugs.toSet()).apply()
    }
    val openChapter: (Novel, Chapter) -> Unit = { book, chapter ->
        screen = Screen.Reader(book.slug, chapter.slug)
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Forest,
            onPrimary = Paper,
            background = Sand,
            onBackground = Ink,
            surface = Paper,
            onSurface = Ink
        )
    ) {
        val activeBook = when (val current = screen) {
            is Screen.Detail -> books.firstOrNull { it.slug == current.slug }
            is Screen.Reader -> books.firstOrNull { it.slug == current.novelSlug }
            else -> null
        }
        val title = when (screen) {
            Screen.Catalog -> "Hunmeng Novel"
            Screen.Library -> "My library"
            is Screen.Detail -> activeBook?.title ?: "Book"
            is Screen.Reader -> "Reading"
        }
        Scaffold(
            containerColor = Sand,
            topBar = {
                Surface(shadowElevation = 2.dp, color = Paper) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (screen !is Screen.Catalog && screen !is Screen.Library) {
                            TextButton(onClick = back) { Text("← Back") }
                        }
                        Text(
                            text = title,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Forest,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            bottomBar = {
                if (screen == Screen.Catalog || screen == Screen.Library) {
                    NavigationBar(containerColor = Paper) {
                        NavigationBarItem(
                            selected = screen == Screen.Catalog,
                            onClick = { screen = Screen.Catalog },
                            icon = { Text("⌕", fontSize = 25.sp) },
                            label = { Text("Discover") }
                        )
                        NavigationBarItem(
                            selected = screen == Screen.Library,
                            onClick = { screen = Screen.Library },
                            icon = { Text("♡", fontSize = 24.sp) },
                            label = { Text("Saved") }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val current = screen) {
                    Screen.Catalog, Screen.Library -> {
                        val visible = if (current == Screen.Library)
                            books.filter { savedSlugs.contains(it.slug) } else books
                        CatalogScreen(
                            books = visible,
                            loading = loading,
                            error = catalogError,
                            saved = savedSlugs.toSet(),
                            isLibrary = current == Screen.Library,
                            onRetry = { refresh++ },
                            onSelect = { screen = Screen.Detail(it.slug) },
                            onSave = toggleSaved
                        )
                    }
                    is Screen.Detail -> if (activeBook != null) {
                        DetailScreen(
                            book = activeBook,
                            isSaved = savedSlugs.contains(activeBook.slug),
                            onSave = { toggleSaved(activeBook.slug) },
                            onRead = { openChapter(activeBook, it) }
                        )
                    } else {
                        StatusPanel("Book unavailable", "Refresh the catalog and try again.") {
                            screen = Screen.Catalog
                            refresh++
                        }
                    }
                    is Screen.Reader -> if (activeBook != null) {
                        ReaderScreen(
                            book = activeBook,
                            chapterSlug = current.chapterSlug,
                            api = api,
                            onSelectChapter = { screen = Screen.Reader(activeBook.slug, it.slug) }
                        )
                    } else {
                        StatusPanel("Book unavailable", "Refresh the catalog and try again.") {
                            screen = Screen.Catalog
                            refresh++
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogScreen(
    books: List<Novel>,
    loading: Boolean,
    error: String?,
    saved: Set<String>,
    isLibrary: Boolean,
    onRetry: () -> Unit,
    onSelect: (Novel) -> Unit,
    onSave: (String) -> Unit
) {
    var query by rememberSaveable(isLibrary) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        Text(
            if (isLibrary) "Your saved stories" else "A reading room for every journey",
            style = MaterialTheme.typography.headlineSmall,
            color = Forest,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        if (!isLibrary) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search titles and authors") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(14.dp))
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> StatusPanel(
                "Could not connect",
                "The app needs the site's JSON catalog. $error",
                onRetry
            )
            else -> {
                val filtered = books.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.author.contains(query, ignoreCase = true)
                }
                if (filtered.isEmpty()) {
                    StatusPanel(
                        if (isLibrary) "Your library is empty" else "No books found",
                        if (isLibrary) "Save a book to see it here." else "Try a different search.",
                        null
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filtered, key = { it.slug }) { book ->
                            BookCard(
                                book = book,
                                isSaved = saved.contains(book.slug),
                                onOpen = { onSelect(book) },
                                onSave = { onSave(book.slug) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookCard(book: Novel, isSaved: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = Paper),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BookCover(book, Modifier.width(86.dp).height(122.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(book.author, color = Forest, fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.height(7.dp))
                Text(
                    book.genres.take(2).joinToString(" · ").ifBlank { "Novel" },
                    color = Color(0xFF61756A),
                    fontSize = 12.sp
                )
                Text("${book.chapters.size} published chapters", fontSize = 12.sp)
                Spacer(Modifier.height(5.dp))
                TextButton(onClick = onSave) {
                    Text(if (isSaved) "♥ Saved" else "♡ Save to library")
                }
            }
        }
    }
}

@Composable
private fun BookCover(book: Novel, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFFE2E8DC), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("H", fontWeight = FontWeight.Bold, color = Forest, fontSize = 30.sp)
        if (book.cover != null) {
            AsyncImage(
                model = book.cover,
                contentDescription = "Cover: ${book.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun DetailScreen(
    book: Novel,
    isSaved: Boolean,
    onSave: () -> Unit,
    onRead: (Chapter) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                BookCover(book, Modifier.width(112.dp).height(160.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Forest)
                    Text(book.author, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("${book.chapters.size} chapters", color = Color(0xFF63776C))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onSave) {
                        Text(if (isSaved) "♥ In library" else "♡ Save")
                    }
                }
            }
        }
        item {
            Text(book.description, style = MaterialTheme.typography.bodyLarge, lineHeight = 25.sp)
        }
        item {
            if (book.chapters.isNotEmpty()) {
                Button(onClick = { onRead(book.chapters.first()) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Start reading")
                }
            }
        }
        item {
            Text("Chapters", style = MaterialTheme.typography.titleLarge, color = Forest)
        }
        items(book.chapters, key = { it.slug }) { chapter ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onRead(chapter) },
                colors = CardDefaults.cardColors(containerColor = Paper)
            ) {
                Text(
                    text = "${chapter.number}. ${chapter.title}    →",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
internal fun StatusPanel(title: String, detail: String, onRetry: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = Forest, fontSize = 19.sp)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
        if (onRetry != null) OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}
