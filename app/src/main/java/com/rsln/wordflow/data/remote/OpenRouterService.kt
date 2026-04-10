package com.rsln.wordflow.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.rsln.wordflow.BuildConfig
import com.rsln.wordflow.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OpenRouterService(
    private val settingsDataStore: SettingsDataStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun translateWord(
        word: String,
        existingCollections: List<String>
    ): Result<AiTranslationResult> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("No API key configured. Set it in Settings."))
            }

            val model = settingsDataStore.aiModel.first()
            val collectionsStr = if (existingCollections.isNotEmpty())
                existingCollections.joinToString(", ") else "none yet"

            val systemPrompt = """You are a vocabulary assistant. Translate English words/phrases to Russian.
Return ONLY valid JSON with these fields:
- "translation": Russian translation (string, required)
- "examples": 1-2 English usage examples (array of strings)
- "pronunciation": approximate Russian pronunciation guide using English letters (string)
- "reason": brief explanation of the word's meaning or origin (string)
- "suggestion": if the input looks like a typo, suggest the correct word (string, empty if no typo)
- "suggestionTranslation": Russian translation of the suggestion (string, empty if no suggestion)
- "matchedCollections": which of the user's existing collections this word fits (array of strings)
- "suggestedCollections": suggest 1-2 NEW collection names this word could belong to (array of strings)
- "difficulty": difficulty score 1-10, where 1=very common, 10=very rare (integer)

Important: Return ONLY the JSON object, no markdown, no explanation."""

            val userPrompt = """Translate this English word/phrase to Russian: "$word"

User's existing collections: $collectionsStr"""

            val requestBody = gson.toJson(mapOf(
                "model" to model,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to systemPrompt),
                    mapOf("role" to "user", "content" to userPrompt)
                ),
                "temperature" to 0.3,
                "max_tokens" to 500,
                "response_format" to mapOf("type" to "json_object")
            ))

            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "com.rsln.wordflow")
                .addHeader("X-Title", "WordFlow")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errorJson = JsonParser.parseString(body).asJsonObject
                    errorJson.getAsJsonObject("error")?.get("message")?.asString ?: body
                } catch (e: Exception) { body }
                throw Exception("API error (${response.code}): $errorMsg")
            }

            val json = JsonParser.parseString(body).asJsonObject
            val content = json.getAsJsonArray("choices")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString
                ?: throw Exception("No content in response")

            val cleanContent = content.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val resultJson = JsonParser.parseString(cleanContent).asJsonObject

            val translation = resultJson.get("translation")?.asString ?: ""
            if (translation.isBlank()) {
                val suggestion = resultJson.get("suggestion")?.asString ?: ""
                if (suggestion.isNotBlank()) {
                    return@withContext Result.failure(
                        Exception("No translation found. Did you mean: \"$suggestion\"?")
                    )
                }
                return@withContext Result.failure(Exception("No translation found for \"$word\""))
            }

            val examples = try {
                resultJson.getAsJsonArray("examples")?.map { it.asString } ?: emptyList()
            } catch (e: Exception) { emptyList() }

            val matched = try {
                resultJson.getAsJsonArray("matchedCollections")?.map { it.asString } ?: emptyList()
            } catch (e: Exception) { emptyList() }

            val suggested = try {
                resultJson.getAsJsonArray("suggestedCollections")?.map { it.asString } ?: emptyList()
            } catch (e: Exception) { emptyList() }

            val difficulty = try {
                resultJson.get("difficulty")?.asInt?.coerceIn(1, 10) ?: 5
            } catch (e: Exception) { 5 }

            Result.success(AiTranslationResult(
                translation = translation,
                examples = examples,
                pronunciation = resultJson.get("pronunciation")?.asString ?: "",
                reason = resultJson.get("reason")?.asString ?: "",
                suggestion = resultJson.get("suggestion")?.asString ?: "",
                suggestionTranslation = resultJson.get("suggestionTranslation")?.asString ?: "",
                matchedCollections = matched,
                suggestedCollections = suggested,
                difficulty = difficulty
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("No API key configured"))
            }

            val model = settingsDataStore.aiModel.first()
            val requestBody = gson.toJson(mapOf(
                "model" to model,
                "messages" to listOf(
                    mapOf("role" to "user", "content" to "Say 'OK' in one word")
                ),
                "max_tokens" to 10
            ))

            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success("Connection successful! Model: $model")
            } else {
                val body = response.body?.string() ?: "Unknown error"
                Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateCollectionWords(
        collectionName: String,
        description: String,
        wordCount: Int,
        targetDifficulty: Int = 5
    ): Result<List<AiTranslationResult>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("No API key configured. Set it in Settings."))
            }
            val model = settingsDataStore.aiModel.first()

            val systemPrompt = """You are a vocabulary assistant that generates English word lists for learning.
Return ONLY a valid JSON object with a single field "words" containing an array.
Each item in the array must have these fields:
- "word": the English word or short phrase (string)
- "translation": Russian translation (string)
- "examples": 1-2 English usage examples (array of strings)
- "pronunciation": approximate Russian pronunciation using English letters (string)
- "reason": brief explanation of meaning (string)
- "difficulty": difficulty score 1-10 (integer)

Return ONLY the JSON object, no markdown."""

            val difficultyLabel = when (targetDifficulty) {
                in 1..3 -> "easy, common everyday words"
                in 4..6 -> "intermediate, moderately challenging words"
                in 7..8 -> "advanced, less common words"
                else -> "expert-level, rare and sophisticated words"
            }
            val userPrompt = """Generate exactly $wordCount English words/phrases for a vocabulary collection called "$collectionName".
${if (description.isNotBlank()) "Description: $description" else ""}
Target difficulty: approximately $targetDifficulty out of 10 ($difficultyLabel).
Pick diverse, useful words that fit this topic and match the target difficulty level.
Order from easiest to hardest."""

            val requestBody = gson.toJson(mapOf(
                "model" to model,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to systemPrompt),
                    mapOf("role" to "user", "content" to userPrompt)
                ),
                "temperature" to 0.7,
                "max_tokens" to 4000,
                "response_format" to mapOf("type" to "json_object")
            ))

            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "com.rsln.wordflow")
                .addHeader("X-Title", "WordFlow")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errorJson = JsonParser.parseString(body).asJsonObject
                    errorJson.getAsJsonObject("error")?.get("message")?.asString ?: body
                } catch (e: Exception) { body }
                throw Exception("API error (${response.code}): $errorMsg")
            }

            val json = JsonParser.parseString(body).asJsonObject
            val content = json.getAsJsonArray("choices")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString
                ?: throw Exception("No content in response")

            val cleanContent = content.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val resultJson = JsonParser.parseString(cleanContent).asJsonObject
            val wordsArray = resultJson.getAsJsonArray("words")
                ?: throw Exception("No 'words' array in response")

            val results = wordsArray.map { element ->
                val obj = element.asJsonObject
                val examples = try {
                    obj.getAsJsonArray("examples")?.map { it.asString } ?: emptyList()
                } catch (_: Exception) { emptyList() }

                AiTranslationResult(
                    translation = obj.get("translation")?.asString ?: "",
                    examples = examples,
                    pronunciation = obj.get("pronunciation")?.asString ?: "",
                    reason = obj.get("reason")?.asString ?: "",
                    difficulty = try { obj.get("difficulty")?.asInt?.coerceIn(1, 10) ?: 5 } catch (_: Exception) { 5 }
                ).let { result ->
                    // Attach the word as a pseudo-field via copy; we'll use the word from the obj
                    result.copy(suggestion = obj.get("word")?.asString ?: "")
                }
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getApiKey(): String {
        val stored = settingsDataStore.apiKey.first()
        return stored.ifBlank { BuildConfig.OPENROUTER_API_KEY }
    }
}
