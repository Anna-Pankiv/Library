package com.example.myapplication

import android.app.Application
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

// --- 1. МОДЕЛІ ДАНИХ ---
enum class SortType { TITLE, AUTHOR, DATE_ADDED, PAGES, RATING }
enum class AppThemeMode { LIGHT, DARK, PURPLE }
enum class AppLanguage { UK, EN }

data class CustomCollection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val bookIds: Set<Int> = emptySet()
)

data class StudyNote(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isImportant: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class TimerData(val bookId: Int, val startPage: Int, val startTimeMs: Long)

data class AppState(
    val isLoggedIn: Boolean = false,
    val currentUserName: String = "",
    val currentUserEmail: String = "",
    val books: List<Book> = emptyList(),
    val studyNotes: List<StudyNote> = emptyList(),
    val collections: List<CustomCollection> = emptyList(),
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val language: AppLanguage = AppLanguage.UK,
    val sortType: SortType = SortType.DATE_ADDED,
    val isAiLoading: Boolean = false,
    val aiExplanation: String? = null,
    val isSearching: Boolean = false,
    val searchResults: List<Book> = emptyList(), // ЗМІНЕНО: список результатів замість одного
    val searchError: String? = null,
    val sessions: List<ReadingSession> = emptyList(),
    val notes: List<Note> = emptyList(),
    val loginError: String? = null
)

// --- 2. VIEWMODEL ---
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "bkr_database"
    ).fallbackToDestructiveMigration().build()

    private val dao = db.dao()

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _activeTimer = MutableStateFlow<TimerData?>(null)
    val activeTimer: StateFlow<TimerData?> = _activeTimer.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            _userEmail.filterNotNull().collect { email ->
                dao.getAllBooks(email).onEach { entities ->
                    _state.update { it.copy(books = entities.map { e -> e.toBook() }) }
                }.launchIn(viewModelScope)

                dao.getAllStudyNotes(email).onEach { entities ->
                    _state.update { it.copy(studyNotes = entities.map { e -> e.toStudyNote() }) }
                }.launchIn(viewModelScope)

                dao.getAllSessions(email).onEach { entities ->
                    _state.update { it.copy(sessions = entities.map { it.toSession() }) }
                }.launchIn(viewModelScope)
            }
        }
    }

    // --- АВТОРИЗАЦІЯ ---
    fun login(identifier: String, pass: String) {
        viewModelScope.launch {
            var user = dao.getUser(identifier)
            if (user == null) {
                user = dao.getUserByUsername(identifier)
            }
            if (user != null && user.password == pass) {
                _userEmail.value = user.email
                _state.update {
                    it.copy(
                        isLoggedIn = true,
                        currentUserEmail = user.email,
                        currentUserName = user.name,
                        loginError = null,
                        collections = listOf(
                            CustomCollection(id = "fav_${user.email}", name = "Улюблене")
                        )
                    )
                }
            } else {
                _state.update {
                    it.copy(loginError = "Невірний нікнейм/email або пароль")
                }
            }
        }
    }

    fun clearLoginError() {
        _state.update { it.copy(loginError = null) }
    }

    fun register(name: String, email: String, pass: String): Boolean {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) return false
        viewModelScope.launch {
            dao.insertUser(UserEntity(email, name, pass))
            _userEmail.value = email
            _state.update {
                it.copy(
                    isLoggedIn = true,
                    currentUserName = name,
                    currentUserEmail = email,
                    collections = listOf(
                        CustomCollection(id = "fav_$email", name = "Улюблене")
                    )
                )
            }
        }
        return true
    }

    fun logout() {
        _userEmail.value = null
        _state.update {
            it.copy(
                isLoggedIn = false,
                currentUserName = "",
                currentUserEmail = "",
                books = emptyList(),
                studyNotes = emptyList(),
                sessions = emptyList()
            )
        }
    }

    // --- РОБОТА З КНИГАМИ ---
    fun addBook(book: Book) {
        val email = _state.value.currentUserEmail
        if (email.isBlank()) return
        viewModelScope.launch {
            dao.insertBook(book.toEntity(email))
        }
    }

    fun stopReadingBook(bookId: Int) {
        viewModelScope.launch { dao.updateBookProgress(bookId, 0, 100) }
    }

    fun deleteBook(bookId: Int) {
        viewModelScope.launch { dao.deleteBookById(bookId) }
    }

    fun setBookRating(bookId: Int, rating: Float) {
        viewModelScope.launch { dao.updateBookRating(bookId, rating) }
    }

    fun getOrCreateLocalBook(uriString: String, fileName: String): Int {
        val email = _state.value.currentUserEmail
        if (email.isBlank()) return 0

        val existing = _state.value.books.find { it.fileUri == uriString }
        if (existing != null) return existing.id

        val newBook = Book(
            0, fileName.substringBeforeLast("."), "Локальний файл",
            BookFormat.EBOOK, 1, 0, "📁", Color(0xFF607D8B), 0, fileUri = uriString
        )
        viewModelScope.launch { dao.insertBook(newBook.toEntity(email)) }
        return 0
    }

    fun saveLocalBookProgress(bookId: Int, page: Int, totalPages: Int, minutesRead: Int, previousPage: Int = 0) {
        val email = _state.value.currentUserEmail
        if (email.isBlank()) return
        val pagesRead = max(0, page - previousPage)
        viewModelScope.launch {
            dao.updateBookProgress(bookId, page, totalPages)
            dao.insertSession(SessionEntity(0, email, bookId, todayDate(), max(1, minutesRead), pagesRead))
        }
    }

    // --- НАЛАШТУВАННЯ ТА СПИСКИ ---
    fun setTheme(mode: AppThemeMode) { _state.update { it.copy(themeMode = mode) } }
    fun setLanguage(lang: AppLanguage) { _state.update { it.copy(language = lang) } }
    fun updateSortType(type: SortType) { _state.update { it.copy(sortType = type) } }

    fun createCollection(name: String) {
        if (name.isBlank()) return
        val newCol = CustomCollection(name = name)
        _state.update { it.copy(collections = it.collections + newCol) }
    }

    fun toggleBookInCollection(bookId: Int, collectionId: String) {
        _state.update { current ->
            current.copy(collections = current.collections.map { col ->
                if (col.id == collectionId) {
                    val newIds =
                        if (col.bookIds.contains(bookId)) col.bookIds - bookId else col.bookIds + bookId
                    col.copy(bookIds = newIds)
                } else col
            })
        }
    }

    // --- ТАЙМЕР ЧИТАННЯ ---
    fun startReadingTimer(bookId: Int, startPage: Int) {
        _activeTimer.value = TimerData(bookId, startPage, System.currentTimeMillis())
    }

    fun stopReadingTimer(bookId: Int, endPage: Int): Boolean {
        val timer = _activeTimer.value ?: return false
        val email = _state.value.currentUserEmail
        if (email.isBlank()) return false

        val minutes = ((System.currentTimeMillis() - timer.startTimeMs) / 60000).toInt()
        val pagesRead = max(0, endPage - timer.startPage)

        val book = _state.value.books.find { it.id == bookId }
        val totalPages = book?.totalPages?.takeIf { it > 0 } ?: 100

        viewModelScope.launch {
            dao.updateBookProgress(bookId, endPage, totalPages)
            dao.insertSession(
                SessionEntity(0, email, bookId, todayDate(), max(1, minutes), pagesRead)
            )
        }

        _activeTimer.value = null
        return true
    }

    // --- НОТАТКИ ТА ШІ ---
    fun addNote(note: Note) {
        _state.update { it.copy(notes = listOf(note) + it.notes) }
    }

    fun getNextNoteId() = (0..100000).random()

    fun addStudyNote(text: String, isImportant: Boolean) {
        if (text.isBlank()) return
        val email = _state.value.currentUserEmail
        if (email.isBlank()) return
        viewModelScope.launch {
            dao.insertStudyNote(
                StudyNoteEntity(
                    UUID.randomUUID().toString(), email, text, isImportant,
                    System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteStudyNote(noteId: String) {
        viewModelScope.launch { dao.deleteStudyNote(noteId) }
    }

    fun toggleStudyNoteImportance(noteId: String) {
        val note = _state.value.studyNotes.find { it.id == noteId } ?: return
        val email = _state.value.currentUserEmail
        if (email.isBlank()) return
        viewModelScope.launch {
            dao.insertStudyNote(
                StudyNoteEntity(note.id, email, note.text, !note.isImportant, note.timestamp)
            )
        }
    }

    /**
     * ШІ-помічник: виправлено модель з "gemini-pro" (застаріла) на "gemini-1.5-flash".
     * Відповідає українською мовою.
     */
    fun explainNoteWithAi(noteText: String) {
        _state.update { it.copy(isAiLoading = true, aiExplanation = null) }
        viewModelScope.launch {
            try {
                val groqKey = "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"
                val url = "https://api.groq.com/openai/v1/chat/completions"

                val gson = com.google.gson.Gson()
                val requestBody = gson.toJson(mapOf(
                    "model" to "llama-3.3-70b-versatile",
                    "messages" to listOf(
                        mapOf(
                            "role" to "system",
                            "content" to "Ти — досвідчений викладач. Відповідай ВИКЛЮЧНО українською мовою. Пояснюй детально але простою мовою, наводь конкретні приклади."
                        ),
                        mapOf(
                            "role" to "user",
                            "content" to "Поясни: $noteText"
                        )
                    ),
                    "max_tokens" to 1024,
                    "temperature" to 0.7
                ))

                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val mediaType = "application/json".toMediaType()
                val body = requestBody.toRequestBody(mediaType)
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $groqKey")
                    .post(body)
                    .build()

                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                val responseText = response.body?.string() ?: ""

                if (!response.isSuccessful || responseText.isBlank()) {
                    _state.update {
                        it.copy(isAiLoading = false, aiExplanation = "Помилка API (${response.code}): $responseText")
                    }
                    return@launch
                }

                val json = com.google.gson.JsonParser.parseString(responseText).asJsonObject
                val text = json
                    .getAsJsonArray("choices")
                    ?.get(0)?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")?.asString
                    ?.trim()
                    ?: "Відповідь не отримано"

                _state.update {
                    it.copy(isAiLoading = false, aiExplanation = text)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isAiLoading = false,
                        aiExplanation = "Помилка ШІ: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearAiExplanation() { _state.update { it.copy(aiExplanation = null) } }

    // ========================================================================================
    // ПОШУК КНИГ (ВИПРАВЛЕНО)
    // ========================================================================================
    /**
     * Універсальний пошук:
     *  - ISBN (лише цифри/дефіс) → isbn:XXXXX
     *  - Все інше → пошук без лапок і без обмеження мови,
     *    щоб Google Books повертав результати і укр, і англ мовами.
     *
     * Повертає список до 10 книг (searchResults) замість одного результату.
     */
    fun searchBookApi(query: String) {
        if (query.isBlank()) return
        _state.update { it.copy(isSearching = true, searchResults = emptyList(), searchError = null) }

        viewModelScope.launch {
            try {
                val trimmed = query.trim()
                val isIsbn = trimmed.replace("-", "").let { it.all { c -> c.isDigit() } && it.length >= 10 }

                val books: List<Book> = if (isIsbn) {
                    // ISBN: пробуємо точний пошук, при помилці — fallback на загальний запит
                    val isbnResult = try {
                        RetrofitClient.instance.searchBooks(
                            query = "isbn:$trimmed",
                            maxResults = 1,
                            langRestrict = null
                        ).items?.mapNotNull { it.volumeInfo.toBook() } ?: emptyList()
                    } catch (_: Exception) { emptyList() }

                    // Якщо ISBN не дав результату — шукаємо як звичайний текст
                    if (isbnResult.isNotEmpty()) isbnResult
                    else {
                        try {
                            RetrofitClient.instance.searchBooks(
                                query = trimmed,
                                maxResults = 5,
                                langRestrict = null
                            ).items?.mapNotNull { it.volumeInfo.toBook() } ?: emptyList()
                        } catch (_: Exception) { emptyList() }
                    }

                } else {
                    // Текстовий пошук: два паралельних запити
                    // Запит 1: загальний — назва / автор / ключові слова
                    val resp1 = try {
                        RetrofitClient.instance.searchBooks(
                            query = trimmed,
                            maxResults = 10,
                            langRestrict = null
                        ).items?.mapNotNull { it.volumeInfo.toBook() } ?: emptyList()
                    } catch (_: Exception) { emptyList() }

                    // Запит 2: intitle — пошук точно по назві
                    val resp2 = try {
                        RetrofitClient.instance.searchBooks(
                            query = "intitle:$trimmed",
                            maxResults = 10,
                            langRestrict = null
                        ).items?.mapNotNull { it.volumeInfo.toBook() } ?: emptyList()
                    } catch (_: Exception) { emptyList() }

                    // Об'єднуємо: спочатку intitle (точніші), потім загальні, дедуплікація по назві+автору
                    val seen = mutableSetOf<String>()
                    (resp2 + resp1).filter { book ->
                        val key = "${book.title.lowercase()}|${book.author.lowercase()}"
                        seen.add(key)
                    }.take(10)
                }

                _state.update {
                    it.copy(
                        isSearching = false,
                        searchResults = books,
                        searchError = if (books.isEmpty()) "Книги не знайдено. Спробуйте інший запит." else null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSearching = false,
                        searchResults = emptyList(),
                        searchError = "Помилка мережі: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /** Перетворює VolumeInfo у Book, повертає null якщо немає назви */
    private fun VolumeInfo.toBook(): Book? {
        val bookTitle = title?.takeIf { it.isNotBlank() } ?: return null
        return Book(
            id = 0,
            title = bookTitle,
            author = authors?.joinToString(", ") ?: "Невідомий автор",
            format = BookFormat.EBOOK,
            totalPages = pageCount ?: 0,
            currentPage = 0,
            coverEmoji = "📚",
            accent = Color.Blue,
            estimatedMinutesLeft = 0,
            imageUrl = imageLinks?.thumbnail?.replace("http:", "https:"),
            dateAdded = System.currentTimeMillis(),
            rating = 0f
        )
    }

    /** Зворотна сумісність: перший результат зі списку (якщо десь використовується searchResult) */
    val searchResult: Book? get() = _state.value.searchResults.firstOrNull()

    fun clearSearch() {
        _state.update { it.copy(searchResults = emptyList(), searchError = null) }
    }

    // --- ДОПОМІЖНІ ---
    private fun todayDate() = SimpleDateFormat("dd MMMM yyyy", Locale("uk")).format(Date())

    private fun BookEntity.toBook() = Book(
        id, title, author, BookFormat.EBOOK, totalPages, currentPage,
        "📚", Color.Gray, 0,
        imageUrl = imageUrl, fileUri = fileUri, dateAdded = dateAdded, rating = rating
    )

    private fun Book.toEntity(userEmail: String) = BookEntity(
        id, userEmail, title, author, totalPages, currentPage, imageUrl, fileUri, rating, dateAdded
    )

    private fun StudyNoteEntity.toStudyNote() = StudyNote(id, text, isImportant, timestamp)
    private fun SessionEntity.toSession() = ReadingSession(id, bookId, date, minutes, pagesRead)
}