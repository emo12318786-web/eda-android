package com.deniz.eda.ai

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Groq'un ucu OpenAI-uyumlu
 * /chat/completions). Tek bir Retrofit arayuzu uc saglayici icin de yeniden
 * kullanilir, sadece base URL ve API anahtari degisir.
 */
interface OpenAiCompatibleApi {
    @POST("chat/completions")
    suspend fun sohbetTamamla(
        @Header("Authorization") yetki: String,
        @Body istek: ChatCompletionRequest
    ): Response<ChatCompletionResponse>

    companion object {
        fun olustur(baseUrl: String): OpenAiCompatibleApi {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenAiCompatibleApi::class.java)
        }
    }
}
