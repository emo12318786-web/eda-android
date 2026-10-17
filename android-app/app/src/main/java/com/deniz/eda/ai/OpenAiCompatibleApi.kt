package com.deniz.eda.ai

import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible endpoint used by local Ollama and online providers.
 * Explicit timeouts avoid hanging on weak mobile connections; OkHttp retries
 * transient connection failures where safe.
 */
interface OpenAiCompatibleApi {
    @POST("chat/completions")
    suspend fun sohbetTamamla(
        @Header("Authorization") yetki: String,
        @Body istek: ChatCompletionRequest
    ): Response<ChatCompletionResponse>

    companion object {
        fun olustur(baseUrl: String): OpenAiCompatibleApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenAiCompatibleApi::class.java)
        }
    }
}
