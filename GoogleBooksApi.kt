package com.example.myapplication

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// --- Моделі даних ---
data class BookSearchResponse(val items: List<BookItem>?)
data class BookItem(val volumeInfo: VolumeInfo)
data class VolumeInfo(
    val title: String?,
    val authors: List<String>?,
    val description: String?,
    val pageCount: Int?,
    val imageLinks: ImageLinks?
)
data class ImageLinks(val thumbnail: String?)

// --- Інтерфейс API ---
interface GoogleBooksApiService {
    @GET("volumes")
    suspend fun searchBooks(
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 10,
        @Query("langRestrict") langRestrict: String? = null,
        // API ключ — обов'язковий, щоб уникнути 503/429 помилок від Google
        @Query("key") apiKey: String = RetrofitClient.GOOGLE_BOOKS_API_KEY
    ): BookSearchResponse
}

// --- Клієнт Retrofit ---
object RetrofitClient {
    private const val BASE_URL = "https://www.googleapis.com/books/v1/"

    // ВАЖЛИВО: Отримай безкоштовний ключ на https://console.cloud.google.com/
    // APIs & Services → Credentials → Create Credentials → API Key
    // Потім увімкни "Books API" в бібліотеці API
    const val GOOGLE_BOOKS_API_KEY = "AIzaSyCu--nCMwOjlA2XMbCxOpdddHlFwccReAo"

    val instance: GoogleBooksApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleBooksApiService::class.java)
    }
}