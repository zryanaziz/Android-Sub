package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import android.util.Log

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "temperature") val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: GeminiApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApi::class.java)
    }
}

class GeminiService(private val customApiKey: String? = null) {
    private val apiKey: String
        get() = if (!customApiKey.isNullOrBlank()) {
            customApiKey
        } else {
            com.example.BuildConfig.GEMINI_API_KEY
        }

    suspend fun translateToKurdishSorani(originalText: String): Result<String> {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return Result.failure(Exception("API Key is missing or default placeholder value is detected. Please configure GEMINI_API_KEY in the Secrets panel in the AI Studio UI."))
        }

        val prompt = "Translate the following subtitle text to natural and fluent Kurdish Sorani (سۆرانی, written in elegant Arabic-Kurdish script). Do not output any notes, punctuation-only changes, or English context unless requested. Translate meaning-for-meaning so it fits subtitle durations:\n\n\"$originalText\""
        
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.3),
            systemInstruction = GeminiContent(
                parts = listOf(
                    GeminiPart(
                        text = "You are an expert Kurdish translator and subtitle adapter. You specialize in the Sorani (سۆرانی) dialect. You write in Kurdish Arabic alphabet (ألفبای کوردی) with precise and modern spelling conventions (e.g. using ڕ, ڵ, وو, ێ, ۆ correctly). Avoid literal translation; provide idiomatic, concise subtitle lines that look natural to Kurdish speakers."
                    )
                )
            )
        )

        return try {
            val response = GeminiClient.api.generateContent(apiKey, request)
            val translated = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!translated.isNullOrBlank()) {
                Result.success(translated.trim().removeSurrounding("\""))
            } else {
                Result.failure(Exception("Failed to generate translation. Empty response from Gemini."))
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Translation error", e)
            Result.failure(e)
        }
    }

    suspend fun refineKurdishSorani(existingKurdish: String, originalContext: String): Result<String> {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return Result.failure(Exception("API Key is missing or default placeholder value is detected. Please configure GEMINI_API_KEY in the Secrets panel in the AI Studio UI."))
        }

        val prompt = "The original text is: \"$originalContext\"\nOur current Kurdish Sorani draft translation is: \"$existingKurdish\"\n\nPlease refine and polish this Kurdish draft into elite, cinematic Kurdish Sorani. Make sure the spelling is 100% correct according to formal Central Kurdish conventions. Keep it precise, engaging, and styled as high-quality movie/show subtitles. Output ONLY the refined Kurdish text without explanations:\n"
        
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.2),
            systemInstruction = GeminiContent(
                parts = listOf(
                    GeminiPart(
                        text = "You are a professional editor for Kurdish Sorani television/film localization. Correct common spelling mistakes (like confounding ی/ێ or و/ۆ, or missing proper markers for ڕ and ڵ where required). Ensure the text is written in standard script, elegant, and ready for broadcast. Output ONLY the refined Kurdish text and nothing else."
                    )
                )
            )
        )

        return try {
            val response = GeminiClient.api.generateContent(apiKey, request)
            val refined = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!refined.isNullOrBlank()) {
                Result.success(refined.trim().removeSurrounding("\""))
            } else {
                Result.failure(Exception("Failed to refine. Empty response from Gemini."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
