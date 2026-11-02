package com.ai.mobileagent.gemini

import com.ai.mobileagent.data.ActionType
import com.ai.mobileagent.data.AgentDecision
import com.ai.mobileagent.data.ScreenSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun validateApiKey(apiKey: String, model: String = "gemini-3.5-flash-lite"): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isBlank()) {
                return@withContext Result.failure(Exception("API Key cannot be blank"))
            }

            try {
                // High-speed endpoint: GET /v1beta/models verifies key validity in ~100ms without LLM generation overhead
                val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$key"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseCode = response.code
                val responseBody = response.body?.string().orEmpty()
                response.close()

                if (responseCode == 200) {
                    Result.success(true)
                } else {
                    val errorMsg = try {
                        val parsed = json.parseToJsonElement(responseBody)
                        parsed.jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                            ?: "HTTP $responseCode"
                    } catch (e: Exception) {
                        "HTTP $responseCode: $responseBody"
                    }
                    Result.failure(Exception("Validation Error: $errorMsg"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Network error: ${e.message ?: "Connection timed out"}"))
            }
        }

    suspend fun getNextAction(
        apiKey: String,
        model: String,
        goal: String,
        history: List<String>,
        snapshot: ScreenSnapshot
    ): Result<AgentDecision> = withContext(Dispatchers.IO) {
        val candidateModels = listOf(
            model,
            "gemini-3.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-flash-latest"
        ).distinct()
        var lastError: Exception? = null

        val promptText = AgentPrompts.buildUserTurnPrompt(goal, history, snapshot.toGeminiContext())
        val parts = mutableListOf<Part>()
        parts.add(Part(text = promptText))

        if (!snapshot.screenshotBase64.isNullOrBlank()) {
            parts.add(
                Part(
                    inlineData = InlineData(
                        mimeType = "image/jpeg",
                        data = snapshot.screenshotBase64
                    )
                )
            )
        }

        val requestPayload = GeminiGenerateRequest(
            systemInstruction = Content(parts = listOf(Part(text = AgentPrompts.SYSTEM_INSTRUCTION))),
            contents = listOf(Content(parts = parts)),
            generationConfig = GenerationConfig(
                temperature = 0.2f,
                responseMimeType = "application/json"
            )
        )
        val requestBody = json.encodeToString(GeminiGenerateRequest.serializer(), requestPayload)
            .toRequestBody(jsonMediaType)

        for (m in candidateModels) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseCode = response.code
                val responseStr = response.body?.string().orEmpty()
                response.close()

                if (responseCode == 200) {
                    val apiResponse = json.decodeFromString(GeminiGenerateResponse.serializer(), responseStr)
                    val rawText = apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: continue

                    val cleanedJson = cleanJsonString(rawText)
                    val decision = json.decodeFromString(AgentDecision.serializer(), cleanedJson)
                    return@withContext Result.success(decision)
                } else {
                    lastError = IOException("Gemini API Error (HTTP $responseCode): $responseStr")
                    if (responseCode == 503) {
                        continue
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        Result.failure(lastError ?: Exception("Failed to get next action from Gemini"))
    }

    private fun cleanJsonString(raw: String): String {
        var str = raw.trim()
        if (str.startsWith("```json")) {
            str = str.removePrefix("```json")
        } else if (str.startsWith("```")) {
            str = str.removePrefix("```")
        }
        if (str.endsWith("```")) {
            str = str.removeSuffix("```")
        }
        return str.trim()
    }
}
