package com.example.myapplication

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ========================================================================================
// 1. ЕКЗЕМПЛЯРИ ДАНИХ (ТАБЛИЦІ БД)
// ========================================================================================

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val email: String,
    val name: String,
    val password: String
)

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ownerEmail: String, // Прив'язка до користувача
    val title: String,
    val author: String,
    val totalPages: Int,
    val currentPage: Int,
    val imageUrl: String?,
    val fileUri: String?,
    val rating: Float,
    val dateAdded: Long
)

@Entity(tableName = "reading_sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ownerEmail: String, // Прив'язка до користувача
    val bookId: Int,
    val date: String,
    val minutes: Int,
    val pagesRead: Int
)

@Entity(tableName = "study_notes")
data class StudyNoteEntity(
    @PrimaryKey val id: String,
    val ownerEmail: String, // Прив'язка до користувача
    val text: String,
    val isImportant: Boolean,
    val timestamp: Long
)

// ========================================================================================
// 2. DAO (ОПЕРАЦІЇ З ДАНИМИ)
// ========================================================================================

@Dao
interface AppDao {

    // --- Користувачі ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUser(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE name = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?
    // --- Книги ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    // ОНОВЛЕНО: Тепер беремо книги лише поточного власника
    @Query("SELECT * FROM books WHERE ownerEmail = :userEmail ORDER BY dateAdded DESC")
    fun getAllBooks(userEmail: String): Flow<List<BookEntity>>

    @Query("UPDATE books SET currentPage = :page, totalPages = :total WHERE id = :id")
    suspend fun updateBookProgress(id: Int, page: Int, total: Int)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Int)

    @Query("UPDATE books SET rating = :rating WHERE id = :id")
    suspend fun updateBookRating(id: Int, rating: Float)

    // --- Сесії читання ---
    @Insert
    suspend fun insertSession(session: SessionEntity)

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId AND ownerEmail = :userEmail ORDER BY id DESC")
    fun getSessionsForBook(bookId: Int, userEmail: String): Flow<List<SessionEntity>>

    // ОНОВЛЕНО: Фільтрація всіх сесій за власником
    @Query("SELECT * FROM reading_sessions WHERE ownerEmail = :userEmail ORDER BY id DESC")
    fun getAllSessions(userEmail: String): Flow<List<SessionEntity>>

    // --- Нотатки для вивчення (Study Notes) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyNote(note: StudyNoteEntity)

    // ОНОВЛЕНО: Нотатки лише поточного власника
    @Query("SELECT * FROM study_notes WHERE ownerEmail = :userEmail ORDER BY timestamp DESC")
    fun getAllStudyNotes(userEmail: String): Flow<List<StudyNoteEntity>>

    @Query("DELETE FROM study_notes WHERE id = :id")
    suspend fun deleteStudyNote(id: String)
}

// ========================================================================================
// 3. КОНФІГУРАЦІЯ БАЗИ ДАНИХ
// ========================================================================================

@Database(
    entities = [UserEntity::class, BookEntity::class, SessionEntity::class, StudyNoteEntity::class],
    version = 2, // ЗМІНЕНО: Потрібно оновити версію через зміну структури (додавання ownerEmail)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
}