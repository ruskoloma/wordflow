package com.rsln.wordflow.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.rsln.wordflow.data.auth.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Client-side façade for the AI endpoints on our Go backend.
 *
 * Historically this class talked to OpenRouter directly with a
 * user-supplied API key. Phase 15 moved that responsibility to the
 * backend (/v1/ai/translate and /v1/ai/generate-collection), so the
 * Android app just sends typed requests and parses the typed
 * responses. The prompts live server-side now — one place to
 * update, no app release needed when tuning them.
 *
 * Kept the class name and method signatures the same to minimize
 * churn in AddWordViewModel and the Generate Collection flow.
 */
class OpenRouterService(
    private val backendClient: BackendClient,
    private val authManager: AuthManager
) {
    private val gson = Gson()

    /**
     * Quick "can we talk to the backend" check. Hits the public
     * /health endpoint so it works even if the user hasn't pasted
     * a token yet — useful feedback on the Settings screen.
     */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = backendClient.health()
            val json = JsonParser.parseString(body).asJsonObject
            val status = json.get("status")?.asString ?: "ok"
            val dbMs = json.get("db_ms")?.asInt ?: -1
            val signed = if (authManager.isSignedIn()) " (signed in)" else " (not signed in)"
            Result.success("Backend $status, db ${dbMs}ms$signed")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Translate one English word/phrase. Posts to
     * POST /v1/ai/translate and parses the typed response into
     * the AiTranslationResult domain type. No raw chat/completions
     * bodies any more.
     */
    suspend fun translateWord(
        word: String,
        existingCollections: List<String>,
        model: String
    ): Result<AiTranslationResult> = withContext(Dispatchers.IO) {
        try {
            if (!authManager.isSignedIn()) {
                return@withContext Result.failure(Exception("Sign in before using AI features"))
            }

            val requestBody = gson.toJson(
                mapOf(
                    "word" to word,
                    "existing_collections" to existingCollections,
                    "model" to AiModel.normalize(model)
                )
            )
            val responseBody = backendClient.postAi("/v1/ai/translate", requestBody)
            val json = JsonParser.parseString(responseBody).asJsonObject

            val translation = json.get("translation")?.asString ?: ""
            if (translation.isBlank()) {
                val suggestion = json.get("suggestion")?.asString ?: ""
                if (suggestion.isNotBlank()) {
                    return@withContext Result.failure(
                        Exception("No translation found. Did you mean: \"$suggestion\"?")
                    )
                }
                return@withContext Result.failure(Exception("No translation found for \"$word\""))
            }

            val examples = json.getAsJsonArray("examples")?.map { it.asString } ?: emptyList()
            val matched = json.getAsJsonArray("matched_collections")?.map { it.asString } ?: emptyList()
            val suggested = json.getAsJsonArray("suggested_collections")?.map { it.asString } ?: emptyList()
            val difficulty = json.get("difficulty")?.asInt?.coerceIn(1, 10) ?: 5

            Result.success(
                AiTranslationResult(
                    translation = translation,
                    examples = examples,
                    pronunciation = json.get("pronunciation")?.asString ?: "",
                    reason = json.get("reason")?.asString ?: "",
                    suggestion = json.get("suggestion")?.asString ?: "",
                    suggestionTranslation = json.get("suggestion_translation")?.asString ?: "",
                    matchedCollections = matched,
                    suggestedCollections = suggested,
                    difficulty = difficulty
                )
            )
        } catch (e: BackendException.Unauthorized) {
            Result.failure(Exception("Sign in before using AI features"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate a batch of themed words. Posts to
     * POST /v1/ai/generate-collection. The server-side prompt
     * handles the "order from easiest to hardest" bit.
     *
     * The wire format here returns each generated word in its own
     * `word` field. We stash it in AiTranslationResult.suggestion so
     * the existing UI (which re-uses AiTranslationResult for
     * generated drafts) doesn't need a new type.
     */
    suspend fun generateCollectionWords(
        collectionName: String,
        description: String,
        wordCount: Int,
        targetDifficulty: Int = 5,
        model: String
    ): Result<List<AiTranslationResult>> = withContext(Dispatchers.IO) {
        try {
            if (!authManager.isSignedIn()) {
                return@withContext Result.failure(Exception("Sign in before using AI features"))
            }

            val requestBody = gson.toJson(
                mapOf(
                    "collection_name" to collectionName,
                    "description" to description,
                    "word_count" to wordCount,
                    "target_difficulty" to targetDifficulty,
                    "model" to AiModel.normalize(model)
                )
            )
            val responseBody = backendClient.postAi("/v1/ai/generate-collection", requestBody)
            val json = JsonParser.parseString(responseBody).asJsonObject
            val wordsArray = json.getAsJsonArray("words")
                ?: return@withContext Result.failure(Exception("No 'words' array in response"))

            val results = wordsArray.map { element ->
                val obj = element.asJsonObject
                val examples = try {
                    obj.getAsJsonArray("examples")?.map { it.asString } ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
                AiTranslationResult(
                    translation = obj.get("translation")?.asString ?: "",
                    examples = examples,
                    pronunciation = obj.get("pronunciation")?.asString ?: "",
                    reason = obj.get("reason")?.asString ?: "",
                    difficulty = try {
                        obj.get("difficulty")?.asInt?.coerceIn(1, 10) ?: 5
                    } catch (_: Exception) { 5 },
                    suggestion = obj.get("word")?.asString ?: ""
                )
            }

            Result.success(results)
        } catch (e: BackendException.Unauthorized) {
            Result.failure(Exception("Sign in before using AI features"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
