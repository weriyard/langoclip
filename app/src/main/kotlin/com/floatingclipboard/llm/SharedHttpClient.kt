package com.floatingclipboard.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Współdzielony klient HTTP dla wszystkich providerów. ContentNegotiation tolerancyjne na nieznane pola. */
internal val llmHttpClient: HttpClient by lazy {
    HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        expectSuccess = false
    }
}
