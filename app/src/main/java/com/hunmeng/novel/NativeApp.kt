package com.hunmeng.novel

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Forest = Color(0xFF14533C)
private val Pale = Color(0xFFF8F4EA)
private val Ink = Color(0xFF23342B)
private enum class Screen { HOME, CATALOG, LIBRARY, DETAILS, READER }

@Composable
fun HunmengNativeApp(deepLink: String?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("native_reader", Context.MODE_PRIVATE) }
    val saved = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet("saved_slugs", emptySet())?.toList().orEmpty())
        }
    }
    var novels by remember { mutableStateOf<List<NativeNovel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var selected by remember { mutableStateOf<NativeNovel?>(null) }
    var currentChapter by remember { mutableStateOf<NativeChapter?>(null) }
    var chapterText by remember { mutableStateOf<NativeChapterText?>(null) }
    var chapterError by remember { mutableStateOf<String?>(null) }
    var readerLoading by remember { mutableStateOf(false) }
    var readerFontSize by remember { mutableFloatStateOf(prefs.getInt("font_size", 19).toFloat()) }
    var readerRequest by remember { mutableIntStateOf(0) }

    fun openDetails(novel: NativeNovel) {
        selected = novel
        screen = Screen.DETAILS
    }

    fun openChapter(novel: NativeNovel, chapter: NativeChapter) {
        selected = novel
        currentChapter = chapter
        chapterText = null
        chapterError = null
        readerLoading = true
        screen = Screen.READER
        prefs.edit().putString("last_chapter_" + novel.slug, chapter.slug).apply()
        readerRequest += 1
    }

    fun toggleSaved(novel: NativeNovel) {
        if (saved.contains(novel.slug)) saved.remove(novel.slug) else saved.add(novel.slug)
        prefs.edit().putStringSet("saved_slugs", saved.toSet()).apply()
    }

    LaunchedEffect(refresh) {
        loading = true
        loadError = null
        try {
            novels = withContext(Dispatchers.IO) { NativeRepository.catalog(context) }
        } catch (_: Exception) {
            loadError = "Не удалось загрузить каталог. Проверьте интернет."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(readerRequest) {
        val novel = selected
        val chapter = currentChapter
        if (readerRequest == 0 || novel == null || chapter == null) return@LaunchedEffect
        readerLoading = true
        chapterError = null
        try {
            chapterText = withContext(Dispatchers.IO) {
                NativeRepository.chapter(context, novel.slug, chapter.slug)
            }
        } catch (_: Exception) {
            chapterError = "Текст главы пока недоступен. Проверьте соединение и повторите."
        } finally {
            readerLoading = false
        }
    }

    LaunchedEffect(deepLink, novels) {
        val parts = try { android.net.Uri.parse(deepLink).pathSegments }
                    catch (_: Exception) { emptyList() }
        if (parts.size >= 2 && parts[0] == "novel") {
            val novel = novels.find { it.slug == parts[1] }
            if (novel != null) {
                val chapter = if (parts.size >= 3) novel.chapters.find { it.slug == parts[2] } else null
                if (chapter != null) openChapter(novel, chapter) else openDetails(novel)
            }
        }
    }

    BackHandler(screen == Screen.DETAILS || screen == Screen.READER) {
        screen = if (screen == Screen.READER) Screen.DETAILS else Screen.CATALOG
    }

    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF93D1AD), background = Color(0xFF141E19),
        surface = Color(0xFF1C2921), onBackground = Color(0xFFF0EDE5),
        onSurface = Color(0xFFF0EDE5)
    ) else lightColorScheme(
        primary = Forest, background = Pale, surface = Pale,
        onBackground = Ink, onSurface = Ink
    )

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                NativeHeader(
                    title = when (screen) {
                        Screen.HOME -> "Hunmeng"
                        Screen.CATALOG -> "Каталог"
                        Screen.LIBRARY -> "Моя библиотека"
                        Screen.DETAILS -> selected?.title ?: "Книга"
                        Screen.READER -> "Читалка"
                    },
                    back = screen == Screen.DETAILS || screen == Screen.READER,
                    onBack = { screen = if (screen == Screen.READER) Screen.DETAILS else Screen.CATALOG }
                )
                Box(Modifier.weight(1f)) {
                    when {
                        loading && novels.isEmpty() -> LoadingScreen()
                        loadError != null && novels.isEmpty() -> ErrorScreen(loadError!!) { refresh += 1 }
                        screen == Screen.HOME -> HomeScreen(
                            novels, onOpen = ::openDetails, onBrowse = { screen = Screen.CATALOG })
                        screen == Screen.CATALOG -> CatalogScreen(novels, onOpen = ::openDetails)
                        screen == Screen.LIBRARY -> LibraryScreen(
                            novels.filter { saved.contains(it.slug) },
                            onOpen = ::openDetails, onBrowse = { screen = Screen.CATALOG })
                        screen == Screen.DETAILS -> selected?.let { book ->
                            DetailsScreen(book, saved.contains(book.slug),
                                lastChapter = prefs.getString("last_chapter_" + book.slug, null),
                                onSave = { toggleSaved(book) },
                                onRead = { openChapter(book, it) })
                        }
                        screen == Screen.READER -> selected?.let { book ->
                            currentChapter?.let { chapter ->
                                ReaderScreen(book, chapter, chapterText, readerLoading, chapterError,
                                    readerFontSize, onFontSize = {
                                        readerFontSize = it
                                        prefs.edit().putInt("font_size", it.toInt()).apply()
                                    }, onRetry = { readerRequest += 1 },
                                    onChapter = { openChapter(book, it) })
                            }
                        }
                    }
                }
                if (screen == Screen.HOME || screen == Screen.CATALOG || screen == Screen.LIBRARY) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        listOf(
                            Triple(Screen.HOME, "Главная", "⌂"),
                            Triple(Screen.CATALOG, "Каталог", "▤"),
                            Triple(Screen.LIBRARY, "Библиотека", "♡")
                        ).forEach { (destination, label, symbol) ->
                            NavigationBarItem(
                                selected = screen == destination,
                                onClick = { screen = destination },
                                icon = { Text(symbol, fontSize = 23.sp) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeHeader(title: String, back: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (back) {
            TextButton(onClick = onBack) { Text("←", fontSize = 25.sp) }
        } else {
            Text("✦", color = MaterialTheme.colorScheme.primary, fontSize = 23.sp)
            Spacer(Modifier.width(10.dp))
        }
        Text(title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = if (back) 21.sp else 26.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Открываем библиотеку…")
        }
    }
}

@Composable
private fun ErrorScreen(message: String, retry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Не удалось открыть библиотеку", fontFamily = FontFamily.Serif, fontSize = 22.sp)
            Text(message)
            Button(onClick = retry) { Text("Повторить") }
        }
    }
}

@Composable
private fun HomeScreen(
    novels: List<NativeNovel>,
    onOpen: (NativeNovel) -> Unit,
    onBrowse: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Forest, Color(0xFF1E4738))))
                    .padding(24.dp)
            ) {
                Text("HUNMENG DIGITAL LIBRARY", color = Color(0xFFD9E9D8),
                    fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(16.dp))
                Text("A thousand worlds.\nOne quiet place.",
                    color = Color(0xFFF7F1E4), fontFamily = FontFamily.Serif,
                    fontSize = 29.sp, lineHeight = 37.sp)
                Spacer(Modifier.height(14.dp))
                Text("Истории, к которым хочется возвращаться.", color = Color(0xFFE6E5DB))
                Spacer(Modifier.height(20.dp))
                Text(novels.size.toString() + " книг · " +
                    novels.sumOf { it.chapters.size } + " глав",
                    color = Color(0xFFF7F1E4), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBrowse,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF7F1E4), contentColor = Forest)) {
                    Text("Смотреть каталог")
                }
            }
        }
        item { SectionTitle("Рекомендуем прочитать") }
        if (novels.isNotEmpty()) item { FeaturedNovel(novels.first(), onOpen) }
        item { SectionTitle("В библиотеке Hunmeng") }
        items(novels.drop(1).take(6), key = { it.slug }) { novel ->
            CompactNovelCard(novel, onOpen)
        }
        item {
            OutlinedButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                Text("Все произведения")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp)
}

@Composable
private fun FeaturedNovel(novel: NativeNovel, onOpen: (NativeNovel) -> Unit) {
    Card(onClick = { onOpen(novel) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)),
        shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            NovelCover(novel, Modifier.width(118.dp).height(172.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("✦ ВЫБОР РЕДАКЦИИ", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(novel.title, fontFamily = FontFamily.Serif, fontSize = 22.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(novel.author, fontSize = 13.sp)
                Text(novel.chapters.size.toString() + " глав", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary)
                Text("Подробнее →", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CompactNovelCard(novel: NativeNovel, onOpen: (NativeNovel) -> Unit) {
    Card(onClick = { onOpen(novel) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NovelCover(novel, Modifier.width(78.dp).height(112.dp))
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(novel.genres.take(2).joinToString(" · "), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary)
                Text(novel.title, fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(novel.author, fontSize = 13.sp, maxLines = 1)
                Text(novel.chapters.size.toString() + " глав", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun NovelCover(novel: NativeNovel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(novel.cover) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(novel.cover) {
        bitmap = withContext(Dispatchers.IO) { NativeRepository.cover(context, novel.cover) }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF305A4B), Color(0xFF142A25)))),
        contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "Обложка: " + novel.title,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
                Text("✦", color = Color(0xFFE8DEC4), fontSize = 28.sp)
                Text("HUNMENG", color = Color(0xFFE8DEC4), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun CatalogScreen(novels: List<NativeNovel>, onOpen: (NativeNovel) -> Unit) {
    var query by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf<String?>(null) }
    val genres = remember(novels) { novels.flatMap { it.genres }.distinct().sorted() }
    val results = remember(novels, query, genre) {
        novels.filter { novel ->
            (genre == null || genre in novel.genres) &&
                (query.isBlank() || listOf(novel.title, novel.author, novel.description,
                    novel.genres.joinToString(" "), novel.tags.joinToString(" "))
                    .any { it.contains(query.trim(), ignoreCase = true) })
        }
    }
    Column {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Поиск по названию, автору, тегам") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = genre == null, onClick = { genre = null },
                label = { Text("Все") }) }
            items(genres) { item ->
                FilterChip(selected = genre == item, onClick = { genre = item },
                    label = { Text(item) })
            }
        }
        LazyColumn(contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Найдено: " + results.size, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary) }
            if (results.isEmpty()) item { Text("Ничего не найдено. Попробуйте другой запрос.") }
            items(results, key = { it.slug }) { CompactNovelCard(it, onOpen) }
        }
    }
}

@Composable
private fun LibraryScreen(novels: List<NativeNovel>,
    onOpen: (NativeNovel) -> Unit, onBrowse: () -> Unit) {
    if (novels.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("♡", fontSize = 58.sp, color = MaterialTheme.colorScheme.primary)
                SectionTitle("Ваша библиотека")
                Text("Добавляйте понравившиеся книги. Список хранится на этом устройстве.")
                Button(onClick = onBrowse) { Text("Перейти в каталог") }
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Сохранено на этом устройстве: " + novels.size) }
            items(novels, key = { it.slug }) { CompactNovelCard(it, onOpen) }
        }
    }
}

@Composable
private fun DetailsScreen(book: NativeNovel, saved: Boolean, lastChapter: String?,
    onSave: () -> Unit, onRead: (NativeChapter) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NovelCover(book, Modifier.width(128.dp).height(184.dp))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(book.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                        fontSize = 24.sp)
                    Text(book.author, fontSize = 14.sp)
                    Text(book.genres.take(3).joinToString(" · "), fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary)
                    Text(book.chapters.size.toString() + " глав", fontSize = 13.sp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val chosen = book.chapters.find { it.slug == lastChapter }
                        ?: book.chapters.firstOrNull()
                    if (chosen != null) onRead(chosen)
                }, enabled = book.chapters.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text(if (lastChapter == null) "Начать чтение" else "Продолжить")
                }
                OutlinedButton(onClick = onSave) {
                    Text(if (saved) "♥ В библиотеке" else "♡ Сохранить")
                }
            }
        }
        item { SectionTitle("О произведении") }
        item { Text(book.description, fontSize = 15.sp, lineHeight = 23.sp) }
        item { SectionTitle("Главы") }
        items(book.chapters, key = { it.slug }) { chapter ->
            Surface(onClick = { onRead(chapter) }, shape = RoundedCornerShape(12.dp),
                color = if (chapter.slug == lastChapter)
                    MaterialTheme.colorScheme.primary.copy(alpha = .10f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f)) {
                Row(Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(chapter.number.toString().padStart(2, '0'),
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                    Text(chapter.title, modifier = Modifier.weight(1f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("›", fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun ReaderScreen(book: NativeNovel, chapter: NativeChapter,
    text: NativeChapterText?, loading: Boolean, error: String?, fontSize: Float,
    onFontSize: (Float) -> Unit, onRetry: () -> Unit, onChapter: (NativeChapter) -> Unit) {
    val index = book.chapters.indexOfFirst { it.slug == chapter.slug }
    val previous = book.chapters.getOrNull(index - 1)
    val next = book.chapters.getOrNull(index + 1)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Аа", fontFamily = FontFamily.Serif, fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
            Slider(value = fontSize, onValueChange = onFontSize,
                valueRange = 15f..28f, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(fontSize.toInt().toString(), fontSize = 13.sp)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = .12f))
        Box(Modifier.weight(1f)) {
            when {
                loading -> LoadingScreen()
                error != null -> ErrorScreen(error, onRetry)
                text == null -> ErrorScreen("Глава недоступна", onRetry)
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Text(book.title.uppercase(), fontSize = 11.sp, letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text(text.title.ifBlank { chapter.title },
                        fontFamily = FontFamily.Serif, fontSize = 26.sp,
                        fontWeight = FontWeight.Bold, lineHeight = 34.sp)
                    Spacer(Modifier.height(22.dp))
                    SelectionContainer {
                        Text(text.content, fontFamily = FontFamily.Serif,
                            fontSize = fontSize.sp, lineHeight = (fontSize * 1.65f).sp)
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = .12f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { if (previous != null) onChapter(previous) },
                enabled = previous != null, modifier = Modifier.weight(1f)) { Text("← Назад") }
            Button(onClick = { if (next != null) onChapter(next) },
                enabled = next != null, modifier = Modifier.weight(1f)) { Text("Далее →") }
        }
    }
}
