package com.example.myapplication

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Html
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myapplication.ui.theme.AppTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

// --- Models & Enums ---[cite: 6]

enum class BookFormat { EBOOK, PRINTED, AUDIO }
enum class ReadingTab { TIMER, DIARY, NOTES }

enum class AppSection(val route: String, val labelUk: String, val labelEn: String) {
    READING("reading", "Читання", "Reading"),
    BOOKS("books", "Книжки", "Library"),
    ADD("addBook", "Додати", "Add"),
    STUDY("study", "Вивчення", "Study"),
    SETTINGS("settings", "Налашт.", "Settings")
}

data class ReadingSession(
    val id: Int,
    val bookId: Int,
    val date: String,
    val minutes: Int,
    val pagesRead: Int
)

data class Note(
    val id: Int,
    val bookId: Int,
    val text: String,
    val createdAt: String
)

data class Book(
    val id: Int,
    val title: String,
    val author: String,
    val format: BookFormat,
    val totalPages: Int,
    val currentPage: Int,
    val coverEmoji: String,
    val accent: Color,
    val estimatedMinutesLeft: Int,
    val description: String = "",
    val rating: Float = 0f,
    val startedAt: String = todayDate(),
    val imageUrl: String? = null,
    val fileUri: String? = null,
    val dateAdded: Long = System.currentTimeMillis()
)

// --- Utils ---[cite: 6]
private fun nowDateTime(): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
private fun todayDate(): String = SimpleDateFormat("dd MMMM yyyy", Locale("uk")).format(Date())
private fun percent(current: Int, total: Int): Int = if (total <= 0) 0 else (current * 100 / total)
private fun pagesLeft(book: Book): Int = max(0, book.totalPages - book.currentPage)

// --- Main Activity ---[cite: 6]

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)

        setContent {
            val viewModel: MainViewModel = viewModel()
            val state by viewModel.state.collectAsState()

            val colorScheme = when (state.themeMode) {
                AppThemeMode.LIGHT -> lightColorScheme()
                AppThemeMode.DARK -> darkColorScheme()
                AppThemeMode.PURPLE -> darkColorScheme(
                    primary = Color(0xFFD0BCFF),
                    background = Color(0xFF190924),
                    surface = Color(0xFF2B143D),
                    surfaceVariant = Color(0xFF4A2A69),
                    onSurface = Color.White,
                    onSurfaceVariant = Color(0xFFEADDFF),
                    secondaryContainer = Color(0xFF4A2A69),
                    onSecondaryContainer = Color.White
                )
            }

            AppTheme {
                MaterialTheme(colorScheme = colorScheme) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Crossfade(targetState = state.isLoggedIn, label = "AuthCrossfade") { isLoggedIn ->
                            if (isLoggedIn) {
                                MainAppGraph(viewModel, state)
                            } else {
                                AuthScreenGraph(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Auth Navigation ---[cite: 6]

@Composable
fun AuthScreenGraph(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        NavHost(navController = navController, startDestination = "login", modifier = Modifier.padding(padding)) {
            composable("login") {
                LoginScreen(
                    onLogin = { email, pass -> viewModel.login(email, pass) },
                    loginError = state.loginError,
                    onClearError = { viewModel.clearLoginError() },
                    onNavigateToRegister = { navController.navigate("register") }
                )
            }
            composable("register") {
                RegisterScreen(
                    onRegister = { name, email, pass ->
                        if (!viewModel.register(name, email, pass)) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Цей email вже зареєстровано!") }
                        }
                    },
                    onNavigateToLogin = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    loginError: String?,
    onClearError: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Ласкаво просимо", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Увійдіть у свій акаунт", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; onClearError() },
            label = { Text("Email або Нікнейм") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            isError = loginError != null,
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; onClearError() },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            isError = loginError != null,
            singleLine = true
        )

        // Повідомлення про помилку
        if (loginError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = loginError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onLogin(email, password) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Увійти", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToRegister) {
            Text("Немає акаунту? Створити")
        }
    }
}

@Composable
fun RegisterScreen(onRegister: (String, String, String) -> Unit, onNavigateToLogin: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Створення акаунту", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Ваше ім'я") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onRegister(name, email, password) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Зареєструватися", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("Вже є акаунт? Увійти")
        }
    }
}

// --- Main App Logic ---[cite: 6]

@Composable
fun MainAppGraph(viewModel: MainViewModel, state: AppState) {
    val navController = rememberNavController()
    val activeTimer by viewModel.activeTimer.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val showMessage: (String) -> Unit = { msg ->
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val entry by navController.currentBackStackEntryAsState()
            val route = entry?.destination?.route?.substringBefore("/") ?: ""
            val visibleRoutes = setOf("reading", "books", "addBook", "study", "settings")
            if (route in visibleRoutes) {
                AppBottomBar(route = route, language = state.language, onNavigate = { target ->
                    if (route != target.route) {
                        navController.navigate(target.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                })
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppSection.READING.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(AppSection.READING.route) {
                ReadingHomeScreen(
                    books = state.books.filter { it.currentPage in 1 until it.totalPages || (it.fileUri != null && it.currentPage > 0) },
                    isUk = state.language == AppLanguage.UK,
                    onOpenBook = { book -> navController.navigate("book/${book.id}") },
                    onStopReading = { bookId ->
                        viewModel.stopReadingBook(bookId)
                        showMessage(if(state.language == AppLanguage.UK) "Книгу прибрано з читання" else "Book removed from reading")
                    },
                    onNavigateToLibrary = { navController.navigate(AppSection.BOOKS.route) }
                )
            }
            composable(AppSection.BOOKS.route) {
                val context = LocalContext.current
                BooksCatalogScreen(
                    state = state,
                    onOpenBook = { book -> navController.navigate("book/${book.id}") },
                    onSortChange = { viewModel.updateSortType(it) },
                    onCreateCollection = { viewModel.createCollection(it) },
                    onToggleCollection = { bookId, colId -> viewModel.toggleBookInCollection(bookId, colId) },
                    onOpenFileReading = { uri, fileName ->
                        try {
                            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                        } catch (e: Exception) { }

                        val bookId = viewModel.getOrCreateLocalBook(uri.toString(), fileName)
                        navController.navigate("internal_reader/$bookId")
                    },
                    onDeleteBook = { bookId -> viewModel.deleteBook(bookId) },
                    onNavigateToAdd = { navController.navigate(AppSection.ADD.route) }
                )
            }
            composable(
                route = "internal_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId") ?: return@composable
                val book = state.books.find { it.id == bookId } ?: return@composable

                InternalReaderScreen(
                    book = book,
                    onClose = { page, total, mins ->
                        // передаємо previousPage щоб порахувати скільки сторінок прочитано за сесію
                        viewModel.saveLocalBookProgress(bookId, page, total, mins, book.currentPage)
                        navController.popBackStack()
                    },
                    onAddToNotes = { text ->
                        viewModel.addStudyNote(text, false)
                        viewModel.addNote(Note(id = viewModel.getNextNoteId(), bookId = bookId, text = text, createdAt = nowDateTime()))
                        showMessage("Додано в нотатки!")
                    }
                )
            }
            composable(AppSection.STUDY.route) {
                StudyNotesScreen(
                    state = state,
                    isUk = state.language == AppLanguage.UK,
                    onAddNote = { text, isImp -> viewModel.addStudyNote(text, isImp) },
                    onDeleteNote = { id -> viewModel.deleteStudyNote(id) },
                    onToggleImportance = { id -> viewModel.toggleStudyNoteImportance(id) },
                    onExplainNote = { text -> viewModel.explainNoteWithAi(text) },
                    onClearExplanation = { viewModel.clearAiExplanation() }
                )
            }
            composable(AppSection.ADD.route) {
                AddBookSearchScreen(
                    state = state,
                    onBack = { viewModel.clearSearch(); navController.popBackStack() },
                    onSearch = { query -> viewModel.searchBookApi(query) },
                    onAddBook = { book ->
                        viewModel.addBook(book)
                        navController.navigate(AppSection.BOOKS.route) { popUpTo(AppSection.READING.route) }
                    }
                )
            }
            composable(AppSection.SETTINGS.route) {
                SettingsScreen(
                    state = state,
                    books = state.books,
                    sessions = state.sessions,
                    onThemeChange = { viewModel.setTheme(it) },
                    onLanguageChange = { viewModel.setLanguage(it) },
                    onLogout = { viewModel.logout() }
                )
            }
            composable(
                route = "book/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId") ?: return@composable
                val book = state.books.find { it.id == bookId } ?: return@composable
                BookDetailScreen(
                    book = book,
                    activeTimer = activeTimer,
                    notes = state.notes.filter { it.bookId == bookId },
                    sessions = state.sessions.filter { it.bookId == bookId },
                    isUk = state.language == AppLanguage.UK,
                    onBack = { navController.popBackStack() },
                    onStartTimer = { startPage -> viewModel.startReadingTimer(bookId, startPage) },
                    onStopTimer = { endPage ->
                        if (viewModel.stopReadingTimer(bookId, endPage)) {
                            showMessage("Сесію збережено!")
                        }
                    },
                    onAddTextNote = { text ->
                        if (text.isNotBlank()) viewModel.addNote(Note(viewModel.getNextNoteId(), bookId, text.trim(), nowDateTime()))
                    },
                    onOpenReader = {
                        if (book.fileUri != null) {
                            navController.navigate("internal_reader/${book.id}")
                        } else {
                            showMessage("Це не локальний файл")
                        }
                    },
                    onRateBook = { rating -> viewModel.setBookRating(bookId, rating) },
                    onShowMessage = showMessage
                )
            }
        }
    }
}

// --- Navigation UI ---[cite: 6]

@Composable
private fun AppBottomBar(route: String, language: AppLanguage, onNavigate: (AppSection) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val items = listOf(
            AppSection.READING to Icons.Default.AutoStories,
            AppSection.BOOKS to Icons.AutoMirrored.Filled.MenuBook,
            AppSection.ADD to Icons.Default.Add,
            AppSection.STUDY to Icons.Default.School,
            AppSection.SETTINGS to Icons.Default.Settings
        )
        items.forEach { (section, icon) ->
            NavigationBarItem(
                selected = route == section.route,
                onClick = { onNavigate(section) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(if (language == AppLanguage.UK) section.labelUk else section.labelEn, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

// --- Settings Screen ---[cite: 6]
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: AppState,
    books: List<Book>,
    sessions: List<ReadingSession>,
    onThemeChange: (AppThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onLogout: () -> Unit
) {
    val isUk = state.language == AppLanguage.UK

    Scaffold(
        topBar = { TopAppBar(title = { Text(if(isUk) "Налаштування" else "Settings", fontWeight = FontWeight.Bold) }) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if(isUk) "Аналітика читання" else "Reading Analytics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                MetricTile(if(isUk) "У бібліотеці" else "In Library", "${books.size} ${if(isUk) "книг" else "books"}", true)
                MetricTile(if(isUk) "Читаю зараз" else "Reading Now", "${books.count { it.currentPage in 1 until it.totalPages }} ${if(isUk) "книг" else "books"}", true)
                MetricTile(if(isUk) "Завершено" else "Completed", "${books.count { it.currentPage >= it.totalPages }} ${if(isUk) "книг" else "books"}", true)
                MetricTile(if(isUk) "Загальний час" else "Total Time", "${sessions.sumOf { it.minutes }} ${if(isUk) "хвилин" else "mins"}", true)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if(isUk) "Вигляд додатку" else "App Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.themeMode == AppThemeMode.LIGHT, onClick = { onThemeChange(AppThemeMode.LIGHT) }, label = { Text(if(isUk) "Світла" else "Light") })
                    FilterChip(selected = state.themeMode == AppThemeMode.DARK, onClick = { onThemeChange(AppThemeMode.DARK) }, label = { Text(if(isUk) "Темна" else "Dark") })
                    FilterChip(selected = state.themeMode == AppThemeMode.PURPLE, onClick = { onThemeChange(AppThemeMode.PURPLE) }, label = { Text(if(isUk) "Фіолетова" else "Purple") })
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if(isUk) "Мова" else "Language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.language == AppLanguage.UK, onClick = { onLanguageChange(AppLanguage.UK) }, label = { Text("Українська") })
                    FilterChip(selected = state.language == AppLanguage.EN, onClick = { onLanguageChange(AppLanguage.EN) }, label = { Text("English") })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                Spacer(Modifier.width(8.dp))
                Text(if(isUk) "Вийти з акаунту" else "Log Out", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


// --- Reading Home Screen ---[cite: 6]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingHomeScreen(books: List<Book>, isUk: Boolean, onOpenBook: (Book) -> Unit, onStopReading: (Int) -> Unit, onNavigateToLibrary: () -> Unit) {
    var showConfirmDialog by remember { mutableStateOf<Book?>(null) }

    if (showConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            title = { Text(if(isUk) "Припинити читання?" else "Stop Reading?") },
            text = { Text(if(isUk) "Прогрес книги '${showConfirmDialog?.title}' буде скинуто. Ви впевнені?" else "Progress for '${showConfirmDialog?.title}' will be lost. Are you sure?") },
            confirmButton = {
                TextButton(onClick = { onStopReading(showConfirmDialog!!.id); showConfirmDialog = null }) {
                    Text(if(isUk) "Так, прибрати" else "Yes, remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = null }) { Text(if(isUk) "Скасувати" else "Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if(isUk) "Читаю зараз" else "Reading Now", fontWeight = FontWeight.Bold) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            if (books.isEmpty()) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 64.dp)) {
                        Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        Spacer(Modifier.height(24.dp))
                        Text(if(isUk) "Немає активних книг" else "No Active Books", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(if(isUk) "Ви ще не почали читати жодної книги." else "You haven't started reading any books yet.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = onNavigateToLibrary,
                            modifier = Modifier.height(50.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if(isUk) "Обрати книгу" else "Choose a Book", fontSize = 16.sp)
                        }
                    }
                }
            } else {
                items(items = books, key = { it.id }) { book ->
                    ReadingCardWithRemove(
                        book = book,
                        isUk = isUk,
                        onClick = { onOpenBook(book) },
                        onRemove = { showConfirmDialog = book },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingCardWithRemove(book: Book, isUk: Boolean, onClick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val p = percent(book.currentPage, book.totalPages)
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                DynamicBookCover(book = book, modifier = Modifier.size(width = 80.dp, height = 116.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(book.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (book.fileUri != null) (if(isUk) "Локальний файл" else "Local File") else book.author, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("$p% ${if(isUk) "прочитано" else "read"} · ${pagesLeft(book)} ${if(isUk) "стор. залишилось" else "pages left"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(
                        progress = { p / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// --- Books Catalog Screen ---[cite: 6]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BooksCatalogScreen(
    state: AppState,
    onOpenBook: (Book) -> Unit,
    onSortChange: (SortType) -> Unit,
    onCreateCollection: (String) -> Unit,
    onToggleCollection: (Int, String) -> Unit,
    onOpenFileReading: (Uri, String) -> Unit,
    onDeleteBook: (Int) -> Unit = {},
    onNavigateToAdd: () -> Unit
) {
    val context = LocalContext.current
    val isUk = state.language == AppLanguage.UK
    var selectedCollection by remember { mutableStateOf<CustomCollection?>(null) }
    var sortExpanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }

    val sortedBooks = remember(state.books, state.sortType, selectedCollection) {
        val filtered = if (selectedCollection == null) state.books
        else state.books.filter { it.id in selectedCollection!!.bookIds }
        when (state.sortType) {
            SortType.TITLE -> filtered.sortedBy { it.title }
            SortType.AUTHOR -> filtered.sortedBy { it.author }
            SortType.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
            SortType.PAGES -> filtered.sortedByDescending { it.totalPages }
            SortType.RATING -> filtered.sortedByDescending { it.rating }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "Document"
            onOpenFileReading(uri, fileName)
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if(isUk) "Новий список" else "New List") },
            text = {
                OutlinedTextField(value = newCollectionName, onValueChange = { newCollectionName = it }, placeholder = { Text(if(isUk) "Назва списку" else "List Name") }, singleLine = true, shape = RoundedCornerShape(12.dp))
            },
            confirmButton = {
                Button(onClick = { onCreateCollection(newCollectionName); newCollectionName = ""; showCreateDialog = false }, enabled = newCollectionName.isNotBlank()) { Text(if(isUk) "Створити" else "Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text(if(isUk) "Скасувати" else "Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if(isUk) "Бібліотека" else "Library", fontWeight = FontWeight.Bold) },
                actions = {
                    Box {
                        IconButton(onClick = { sortExpanded = true }) { Icon(Icons.AutoMirrored.Filled.Sort, null) }
                        DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                            DropdownMenuItem(text = { Text(if(isUk) "За датою" else "By Date") }, onClick = { onSortChange(SortType.DATE_ADDED); sortExpanded = false })
                            DropdownMenuItem(text = { Text(if(isUk) "За назвою (A-Z)" else "By Title (A-Z)") }, onClick = { onSortChange(SortType.TITLE); sortExpanded = false })
                            DropdownMenuItem(text = { Text(if(isUk) "За автором" else "By Author") }, onClick = { onSortChange(SortType.AUTHOR); sortExpanded = false })
                            DropdownMenuItem(text = { Text(if(isUk) "За сторінками" else "By Pages") }, onClick = { onSortChange(SortType.PAGES); sortExpanded = false })
                            DropdownMenuItem(text = { Text(if(isUk) "За оцінкою" else "By Rating") }, onClick = { onSortChange(SortType.RATING); sortExpanded = false })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(selected = selectedCollection == null, onClick = { selectedCollection = null }, label = { Text(if(isUk) "Усі книги" else "All Books") })
                }
                items(state.collections) { collection ->
                    val displayName = when {
                        collection.id.startsWith("fav_") -> if (isUk) "Улюблене" else "Favorites"
                        else -> collection.name
                    }
                    FilterChip(
                        selected = selectedCollection?.id == collection.id,
                        onClick = { selectedCollection = if (selectedCollection?.id == collection.id) null else collection },
                        label = { Text(displayName) }
                    )
                }
                item {
                    AssistChip(onClick = { showCreateDialog = true }, label = { Text(if(isUk) "Новий список" else "New List") }, leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) })
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Card(
                        onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(if(isUk) "Відкрити локальний файл" else "Open Local File", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                                    Text("PDF, FB2, EPUB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
                if (sortedBooks.isEmpty()) {
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 64.dp)) {
                            Icon(Icons.Default.CollectionsBookmark, contentDescription = null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            Spacer(Modifier.height(24.dp))
                            Text(if(isUk) "Тут порожньо" else "It's empty here", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(if(isUk) "Ваша бібліотека поки що порожня. Час додати нову книгу!" else "Your library is empty. Add a new book!", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(32.dp))
                            Button(
                                onClick = onNavigateToAdd,
                                modifier = Modifier.height(50.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(if(isUk) "Шукати книгу" else "Find a Book", fontSize = 16.sp)
                            }
                        }
                    }
                } else {
                    items(items = sortedBooks, key = { it.id }) { book ->
                        CatalogBookCard(
                            book = book,
                            collections = state.collections,
                            isUk = isUk,
                            onClick = { onOpenBook(book) },
                            onToggleCollection = { colId -> onToggleCollection(book.id, colId) },
                            onDelete = { onDeleteBook(book.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CatalogBookCard(
    book: Book,
    collections: List<CustomCollection>,
    isUk: Boolean,
    onClick: () -> Unit,
    onToggleCollection: (String) -> Unit,
    onDelete: () -> Unit = {}
) {
    val favCollection = collections.find { it.name == "Улюблене" || it.name == "Favorites" }
    val isFav = favCollection?.bookIds?.contains(book.id) == true
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DynamicBookCover(book = book, modifier = Modifier.size(width = 80.dp, height = 116.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(book.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (book.fileUri != null) (if(isUk) "Локальний файл" else "Local File") else book.author, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

                if (book.rating > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(book.rating.toInt().toString(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column {
                IconButton(onClick = { favCollection?.let { onToggleCollection(it.id) } }) {
                    Icon(
                        imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Улюблене",
                        tint = if (isFav) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Папки", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        Text(if(isUk) "Додати в..." else "Add to...", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        collections.forEach { col ->
                            val inCol = col.bookIds.contains(book.id)
                            val colDisplayName = when {
                                col.id.startsWith("fav_") -> if (isUk) "Улюблене" else "Favorites"
                                else -> col.name
                            }
                            DropdownMenuItem(
                                text = { Text(colDisplayName) },
                                trailingIcon = { if (inCol) Icon(Icons.Default.Check, null) },
                                onClick = {
                                    onToggleCollection(col.id)
                                    menuExpanded = false
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if(isUk) "Видалити" else "Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- Study Notes Screen ---[cite: 6]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyNotesScreen(
    state: AppState,
    isUk: Boolean,
    onAddNote: (String, Boolean) -> Unit,
    onDeleteNote: (String) -> Unit,
    onToggleImportance: (String) -> Unit,
    onExplainNote: (String) -> Unit,
    onClearExplanation: () -> Unit
) {
    var newNoteText by remember { mutableStateOf("") }
    var isImportant by remember { mutableStateOf(false) }

    if (state.isAiLoading || state.aiExplanation != null) {
        AlertDialog(
            onDismissRequest = onClearExplanation,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(if(isUk) "Допомога ШІ" else "AI Assistant")
                }
            },
            text = {
                if (state.isAiLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(if(isUk) "Аналізую нотатку..." else "Analyzing note...")
                    }
                } else {
                    Text(state.aiExplanation ?: "", modifier = Modifier.verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = {
                TextButton(onClick = onClearExplanation) {
                    Text(if(isUk) "Зрозуміло" else "Got it")
                }
            }
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if(isUk) "Вивчення" else "Study", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newNoteText, onValueChange = { newNoteText = it },
                        placeholder = { Text(if(isUk) "Що хочете запам'ятати?" else "What to remember?") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isImportant = !isImportant }) {
                            Checkbox(checked = isImportant, onCheckedChange = { isImportant = it })
                            Text(if(isUk) "Важливо" else "Important")
                        }
                        Button(
                            onClick = { onAddNote(newNoteText, isImportant); newNoteText = ""; isImportant = false },
                            enabled = newNoteText.isNotBlank()
                        ) { Text(if(isUk) "Зберегти" else "Save") }
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items = state.studyNotes, key = { it.id }) { note ->
                    val color = if (note.isImportant) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    val textColor = if (note.isImportant) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface

                    Card(
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        colors = CardDefaults.cardColors(containerColor = color)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                if (note.isImportant) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.StarOutline, null, tint = textColor, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if(isUk) "Важливо" else "Important", style = MaterialTheme.typography.labelSmall, color = textColor)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                                Text(note.text, color = textColor)
                            }
                            Row {
                                IconButton(onClick = { onExplainNote(note.text) }) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = "Пояснити", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { onToggleImportance(note.id) }) {
                                    Icon(if (note.isImportant) Icons.Filled.Star else Icons.Outlined.StarOutline, null, tint = if (note.isImportant) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onDeleteNote(note.id) }) {
                                    Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Add Book Search Screen ---[cite: 6]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBookSearchScreen(state: AppState, onBack: () -> Unit, onSearch: (String) -> Unit, onAddBook: (Book) -> Unit) {
    var query by remember { mutableStateOf("") }
    val isUk = state.language == AppLanguage.UK
    var addedBookIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(if (isUk) "Знайти книгу" else "Find Book") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Пошукове поле
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = {
                    Text(if (isUk) "Назва, автор або ISBN" else "Title, author or ISBN")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch(query) })
            )

            // Підказка
            Text(
                text = if (isUk) "Підтримується пошук українською та англійською мовами"
                else "Search works in Ukrainian and English",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Кнопка пошуку
            Button(
                onClick = { onSearch(query) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = query.isNotBlank() && !state.isSearching
            ) {
                if (state.isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(if (isUk) "Шукаємо..." else "Searching...")
                } else {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isUk) "Шукати" else "Search", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Помилка або порожній результат
            if (state.searchError != null && !state.isSearching) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(state.searchError, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Список результатів
            if (state.searchResults.isNotEmpty()) {
                Text(
                    text = if (isUk) "Знайдено ${state.searchResults.size} книг(и):"
                    else "Found ${state.searchResults.size} book(s):",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(items = state.searchResults, key = { it.title + it.author }) { resultBook ->
                        val bookKey = resultBook.title + resultBook.author
                        val isAdded = addedBookIds.contains(bookKey)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAdded)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Обкладинка
                                DynamicBookCover(
                                    book = resultBook,
                                    modifier = Modifier.size(width = 62.dp, height = 90.dp)
                                )

                                // Інфо
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = resultBook.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = resultBook.author,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (resultBook.totalPages > 0) {
                                        Text(
                                            text = if (isUk) "${resultBook.totalPages} стор." else "${resultBook.totalPages} pages",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    // Кнопка додати
                                    Button(
                                        onClick = {
                                            onAddBook(resultBook)
                                            addedBookIds = addedBookIds + bookKey
                                        },
                                        enabled = !isAdded,
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        if (isAdded) {
                                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isUk) "Додано" else "Added", fontSize = 13.sp)
                                        } else {
                                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isUk) "Додати" else "Add", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Book Detail Screen ---[cite: 6]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookDetailScreen(
    book: Book,
    activeTimer: TimerData?,
    notes: List<Note>,
    sessions: List<ReadingSession>,
    isUk: Boolean,
    onBack: () -> Unit,
    onStartTimer: (Int) -> Unit,
    onStopTimer: (Int) -> Unit,
    onAddTextNote: (String) -> Unit,
    onOpenReader: () -> Unit,
    onRateBook: (Float) -> Unit,
    onShowMessage: (String) -> Unit
) {
    var activeTab by remember { mutableStateOf(ReadingTab.TIMER) }
    var noteText by remember { mutableStateOf("") }
    val progressValue = percent(book.currentPage, book.totalPages).toFloat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DynamicBookCover(book = book, modifier = Modifier.size(width = 150.dp, height = 220.dp))

            Row(horizontalArrangement = Arrangement.Center) {
                for (i in 1..5) {
                    IconButton(onClick = { onRateBook(i.toFloat()) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (i <= book.rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Оцінка $i",
                            tint = if (i <= book.rating) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            if (book.fileUri != null) {
                Button(onClick = onOpenReader, modifier = Modifier.fillMaxWidth(0.6f)) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if(isUk) "Читати книгу" else "Read Book")
                }
            }

            Text("${progressValue.toInt()}%", fontSize = 42.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { progressValue / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            ReadingTabs(activeTab = activeTab, onTabChange = { activeTab = it })

            when (activeTab) {
                ReadingTab.TIMER -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActiveReadingTimerBlock(
                            book = book, activeTimer = activeTimer, isUk = isUk,
                            onStartTimer = onStartTimer, onStopTimer = onStopTimer, onShowMessage = onShowMessage
                        )

                        if (sessions.isNotEmpty()) {
                            Text(if(isUk) "Історія сесій" else "Sessions History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                            sessions.forEach { session ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(session.date, fontWeight = FontWeight.Medium)
                                            Text("${session.minutes} ${if(isUk) "хв" else "min"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                        }
                                        Text("+${session.pagesRead} ${if(isUk) "стор." else "pgs"}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                ReadingTab.DIARY -> NotesSection(
                    notes = notes, noteText = noteText, onNoteChange = { noteText = it },
                    onAddText = { onAddTextNote(noteText); noteText = "" }, isUk = isUk
                )
                ReadingTab.NOTES -> PlaceholderIllustrationBlock(
                    if(isUk) "Опис" else "Description",
                    book.description.ifBlank { if(isUk) "Опис відсутній" else "No description" }
                )
            }
        }
    }
}

@Composable
private fun ActiveReadingTimerBlock(
    book: Book,
    activeTimer: TimerData?,
    isUk: Boolean,
    onStartTimer: (Int) -> Unit,
    onStopTimer: (Int) -> Unit,
    onShowMessage: (String) -> Unit
) {
    if (book.fileUri != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if(isUk) "Автоматичне відстеження" else "Auto Tracking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if(isUk) "Час читання та прочитані сторінки зберігаються автоматично. Просто відкрийте книгу та почніть читати!"
                    else "Reading time and pages are saved automatically. Just open the book and start reading!",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    } else {
        val isRunning = activeTimer?.bookId == book.id
        var startPageInput by remember(book.currentPage) { mutableStateOf(book.currentPage.toString()) }
        var endPageInput by remember { mutableStateOf("") }
        var showEndPageDialog by remember { mutableStateOf(false) }

        var elapsedSeconds by remember { mutableStateOf(0L) }

        LaunchedEffect(isRunning, activeTimer) {
            if (isRunning && activeTimer != null) {
                while (true) {
                    elapsedSeconds = (System.currentTimeMillis() - activeTimer.startTimeMs) / 1000
                    delay(1000L)
                }
            } else {
                elapsedSeconds = 0L
            }
        }

        val formatTime = { seconds: Long ->
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            if (h > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
            else String.format(Locale.getDefault(), "%02d:%02d", m, s)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = if (isRunning) Alignment.CenterHorizontally else Alignment.Start
            ) {
                if (isRunning) {
                    Text(if(isUk) "Читання в процесі" else "Reading in progress", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Text(
                        text = formatTime(elapsedSeconds),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(
                        onClick = { showEndPageDialog = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    ) {
                        Text(if(isUk) "Завершити" else "Finish", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(if(isUk) "Відстеження прогресу" else "Track Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = startPageInput,
                        onValueChange = { startPageInput = it },
                        label = { Text(if(isUk) "Початкова сторінка" else "Start Page") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Button(
                        onClick = {
                            val startP = startPageInput.toIntOrNull()
                            if (startP == null || startP < 0 || startP > book.totalPages) onShowMessage(if(isUk) "Некоректна початкова сторінка" else "Invalid start page")
                            else {
                                onStartTimer(startP)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.Timer, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if(isUk) "Почати читання" else "Start Reading", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showEndPageDialog) {
            AlertDialog(
                onDismissRequest = { showEndPageDialog = false },
                title = { Text(if(isUk) "Чудова робота!" else "Great job!") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if(isUk) "На якій сторінці ви зупинилися?" else "What page did you stop on?")
                        OutlinedTextField(
                            value = endPageInput,
                            onValueChange = { endPageInput = it },
                            label = { Text(if(isUk) "Кінцева сторінка" else "End Page") },
                            placeholder = { Text(if(isUk) "Почато з: ${activeTimer?.startPage ?: 0}" else "Started at: ${activeTimer?.startPage ?: 0}") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val endP = endPageInput.toIntOrNull()
                        val startP = activeTimer?.startPage ?: 0
                        if (endP == null || endP < startP || endP > book.totalPages) {
                            onShowMessage(if(isUk) "Будь ласка, введіть сторінку більшу за $startP" else "Please enter a page greater than $startP")
                        } else {
                            showEndPageDialog = false
                            onStopTimer(endP)
                            endPageInput = ""
                        }
                    }) {
                        Text(if(isUk) "Зберегти прогрес" else "Save Progress")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEndPageDialog = false }) {
                        Text(if(isUk) "Назад до таймера" else "Back to timer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
}

// --- Shared UI Components ---[cite: 6]

@Composable
private fun DynamicBookCover(book: Book, modifier: Modifier = Modifier) {
    if (book.imageUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(book.imageUrl).crossfade(true).build(),
            contentDescription = "Cover", modifier = modifier.clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(book.accent.copy(alpha = 0.8f), book.accent, Color.DarkGray))).padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(book.coverEmoji, fontSize = 32.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(book.title, color = Color.White, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ReadingTabs(activeTab: ReadingTab, onTabChange: (ReadingTab) -> Unit) {
    val items = listOf(
        ReadingTab.TIMER to Icons.Default.Timer,
        ReadingTab.DIARY to Icons.Default.Checklist,
        ReadingTab.NOTES to Icons.AutoMirrored.Filled.Notes
    )
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (tab, icon) ->
            Surface(
                modifier = Modifier.clickable { onTabChange(tab) }, shape = RoundedCornerShape(18.dp),
                color = if (tab == activeTab) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                border = if (tab == activeTab) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, null)
                    Text(tab.name)
                }
            }
        }
    }
}

@Composable
private fun MetricTile(title: String, value: String, wide: Boolean = false) {
    Card(
        modifier = if (wide) Modifier.fillMaxWidth() else Modifier.width(150.dp),
        shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NotesSection(notes: List<Note>, noteText: String, isUk: Boolean, onNoteChange: (String) -> Unit, onAddText: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(value = noteText, onValueChange = onNoteChange, placeholder = { Text(if(isUk) "Думки/Цитати" else "Thoughts/Quotes") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            Button(onClick = onAddText, modifier = Modifier.fillMaxWidth(), enabled = noteText.isNotBlank()) { Text(if(isUk) "Додати" else "Add") }
            notes.forEach { note ->
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(note.text)
                        Text(note.createdAt, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderIllustrationBlock(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --- Internal Reader Logic ---[cite: 6]

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "unknown"
}

suspend fun parsePdfToPages(context: Context, uri: Uri): List<String> = withContext(Dispatchers.IO) {
    val pages = mutableListOf<String>()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val pageCount = document.numberOfPages
            for (i in 1..pageCount) {
                stripper.startPage = i
                stripper.endPage = i
                val pageText = stripper.getText(document).trim()
                pages.add(pageText.ifBlank { "(Порожня сторінка або скановане зображення)" })
            }
            document.close()
        }
    } catch (e: Exception) {
        pages.add("Помилка читання PDF: ${e.localizedMessage}")
    }
    return@withContext pages
}

suspend fun parseFb2ToPages(context: Context, uri: Uri): List<String> = withContext(Dispatchers.IO) {
    val pages = mutableListOf<String>()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            val currentText = StringBuilder()
            var isInsideParagraph = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "p" || parser.name == "v") {
                            isInsideParagraph = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (isInsideParagraph && parser.text.isNotBlank()) {
                            currentText.append(parser.text.replace('\n', ' ').trim()).append(" ")
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "p" || parser.name == "v") {
                            isInsideParagraph = false
                            currentText.append("\n\n")
                        }
                    }
                }
                eventType = parser.next()
            }

            val fullText = currentText.toString().trim()
            if (fullText.isEmpty()) {
                pages.add("Текст не знайдено або файл порожній.")
            } else {
                val chunkSize = 2000
                var startIndex = 0
                while (startIndex < fullText.length) {
                    var endIndex = min(startIndex + chunkSize, fullText.length)
                    if (endIndex < fullText.length) {
                        while (endIndex > startIndex && !fullText[endIndex].isWhitespace()) {
                            endIndex--
                        }
                        if (endIndex == startIndex) endIndex = min(startIndex + chunkSize, fullText.length)
                    }
                    pages.add(fullText.substring(startIndex, endIndex).trim())
                    startIndex = endIndex
                }
            }
        }
    } catch (e: Exception) {
        pages.add("Помилка читання FB2: ${e.localizedMessage}")
    }
    return@withContext pages
}

suspend fun parseEpubToPages(context: Context, uri: Uri): List<String> = withContext(Dispatchers.IO) {
    val pages = mutableListOf<String>()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val zipInputStream = ZipInputStream(inputStream)
            var zipEntry = zipInputStream.nextEntry
            val fullTextBuilder = StringBuilder()

            while (zipEntry != null) {
                if (!zipEntry.isDirectory) {
                    val name = zipEntry.name.lowercase()
                    if (name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm")) {
                        val scanner = Scanner(zipInputStream, "UTF-8").useDelimiter("\\A")
                        if (scanner.hasNext()) {
                            val htmlContent = scanner.next()
                            val textOnly = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY).toString()
                            if (textOnly.isNotBlank()) {
                                fullTextBuilder.append(textOnly.trim()).append("\n\n")
                            }
                        }
                    }
                }
                zipEntry = zipInputStream.nextEntry
            }
            zipInputStream.close()

            val fullText = fullTextBuilder.toString().trim()
            if (fullText.isEmpty()) {
                pages.add("Текст не знайдено або файл порожній (EPUB).")
            } else {
                val chunkSize = 2000
                var startIndex = 0
                while (startIndex < fullText.length) {
                    var endIndex = min(startIndex + chunkSize, fullText.length)
                    if (endIndex < fullText.length) {
                        while (endIndex > startIndex && !fullText[endIndex].isWhitespace()) {
                            endIndex--
                        }
                        if (endIndex == startIndex) endIndex = min(startIndex + chunkSize, fullText.length)
                    }
                    pages.add(fullText.substring(startIndex, endIndex).trim())
                    startIndex = endIndex
                }
            }
        }
    } catch (e: Exception) {
        pages.add("Помилка читання EPUB: ${e.localizedMessage}")
    }
    return@withContext pages
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalReaderScreen(
    book: Book,
    onClose: (currentPage: Int, totalPages: Int, minutesRead: Int) -> Unit,
    onAddToNotes: (String) -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var bookPages by remember { mutableStateOf<List<String>>(emptyList()) }
    var fontSize by remember { mutableStateOf(18) }

    val startTime = remember { System.currentTimeMillis() }
    var sessionSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            sessionSeconds++
        }
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = max(0, book.currentPage - 1))

    LaunchedEffect(book.fileUri) {
        if (book.fileUri != null) {
            isLoading = true
            val uri = Uri.parse(book.fileUri)
            val fileName = getFileName(context, uri).lowercase()

            val isFb2 = fileName.endsWith(".fb2") || fileName.endsWith(".xml")
            val isEpub = fileName.endsWith(".epub")

            val pages = when {
                isFb2 -> parseFb2ToPages(context, uri)
                isEpub -> parseEpubToPages(context, uri)
                else -> parsePdfToPages(context, uri)
            }

            if (pages.isNotEmpty() && pages[0].startsWith("Помилка")) {
                errorMessage = pages[0]
            } else {
                bookPages = pages
            }
            isLoading = false
        }
    }

    val handleClose = {
        val minutesRead = sessionSeconds / 60
        val currentPage = listState.firstVisibleItemIndex + 1
        val totalPages = if (bookPages.isNotEmpty()) bookPages.size else book.totalPages
        onClose(currentPage, totalPages, minutesRead)
    }

    BackHandler { handleClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
                        Text(
                            "Час: ${sessionSeconds / 60} хв  •  Стор. ${listState.firstVisibleItemIndex + 1}/${if (bookPages.isNotEmpty()) bookPages.size else book.totalPages}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = handleClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { if (fontSize > 12) fontSize-- }) { Icon(Icons.Default.TextDecrease, null) }
                    IconButton(onClick = { if (fontSize < 32) fontSize++ }) { Icon(Icons.Default.TextIncrease, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                errorMessage != null -> Text(text = errorMessage!!, modifier = Modifier.align(Alignment.Center).padding(16.dp))
                bookPages.isNotEmpty() -> {
                    BookTextReader(
                        pages = bookPages,
                        fontSize = fontSize,
                        listState = listState,
                        onAddToNotes = onAddToNotes
                    )
                }
            }
        }
    }
}

@Composable
fun BookTextReader(
    pages: List<String>,
    fontSize: Int,
    listState: LazyListState,
    onAddToNotes: (String) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.8f, 3.5f)
        offsetX = (offsetX + panChange.x).coerceIn(-600f, 600f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = transformState)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX
                ),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            itemsIndexed(pages) { index, pageText ->
                BookPageTextCard(
                    pageNumber = index + 1,
                    text = pageText,
                    fontSize = fontSize,
                    onAddToNotes = onAddToNotes
                )
            }
        }

        if (scale != 1f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f)
            ) {
                Text("${(scale * 100).toInt()}%", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun BookPageTextCard(
    pageNumber: Int,
    text: String,
    fontSize: Int,
    onAddToNotes: (String) -> Unit
) {
    val context = LocalContext.current
    var textState by remember(text) { mutableStateOf(TextFieldValue(text)) }
    var highlights by remember { mutableStateOf<List<TextRange>>(emptyList()) }
    var isMenuShown by remember { mutableStateOf(false) }
    var copyAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var selectAllAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val customTextToolbar = remember {
        object : TextToolbar {
            override val status: TextToolbarStatus get() = if (isMenuShown) TextToolbarStatus.Shown else TextToolbarStatus.Hidden
            override fun hide() { isMenuShown = false }
            override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
                copyAction = onCopyRequested
                selectAllAction = onSelectAllRequested
                isMenuShown = true
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Сторінка $pageNumber", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            Spacer(Modifier.height(10.dp))

            CompositionLocalProvider(LocalTextToolbar provides customTextToolbar) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = textState, onValueChange = { textState = it }, readOnly = true,
                        textStyle = TextStyle(fontSize = fontSize.sp, lineHeight = (fontSize * 1.6).sp, color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = { annotatedText ->
                            val buildString = buildAnnotatedString {
                                append(annotatedText.text)
                                highlights.forEach { range -> addStyle(style = SpanStyle(background = Color.Yellow.copy(alpha = 0.4f)), start = range.min, end = range.max) }
                            }
                            TransformedText(buildString, OffsetMapping.Identity)
                        }
                    )

                    if (isMenuShown && !textState.selection.collapsed) {
                        val selMin = textState.selection.min
                        val selMax = textState.selection.max
                        val actuallySelectedText = textState.text.substring(selMin, selMax)
                        val isHighlighted = highlights.any { h -> max(selMin, h.min) < min(selMax, h.max) || (selMin == h.min && selMax == h.max) }

                        Popup(
                            alignment = Alignment.BottomCenter,
                            offset = IntOffset(0, -150),
                            properties = PopupProperties(focusable = false)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.inverseSurface,
                                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                shadowElevation = 16.dp
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                                    if (copyAction != null) {
                                        TextButton(onClick = { copyAction?.invoke(); isMenuShown = false }) { Text("Копіювати", color = MaterialTheme.colorScheme.inverseOnSurface) }
                                        Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.Gray.copy(alpha = 0.5f)))
                                    }
                                    if (selectAllAction != null) {
                                        TextButton(onClick = { selectAllAction?.invoke() }) { Text("Усе", color = MaterialTheme.colorScheme.inverseOnSurface) }
                                        Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.Gray.copy(alpha = 0.5f)))
                                    }
                                    TextButton(
                                        onClick = {
                                            if (isHighlighted) highlights = highlights.filterNot { h -> max(selMin, h.min) < min(selMax, h.max) || (selMin == h.min && selMax == h.max) }
                                            else highlights = highlights + textState.selection
                                            isMenuShown = false
                                            textState = textState.copy(selection = TextRange.Zero)
                                        }
                                    ) {
                                        Icon(if (isHighlighted) Icons.Default.FormatClear else Icons.Default.Brush, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isHighlighted) "Зняти" else "Маркер", color = MaterialTheme.colorScheme.inverseOnSurface)
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.Gray.copy(alpha = 0.5f)))
                                    TextButton(
                                        onClick = {
                                            onAddToNotes(actuallySelectedText)
                                            Toast.makeText(context, "Збережено в нотатки!", Toast.LENGTH_SHORT).show()
                                            isMenuShown = false
                                            textState = textState.copy(selection = TextRange.Zero)
                                        }
                                    ) {
                                        Icon(Icons.Default.NoteAdd, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
                                        Spacer(Modifier.width(4.dp))
                                        Text("В нотатки", color = MaterialTheme.colorScheme.inverseOnSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}