package com.rsln.wordflow.data.remote

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.rsln.wordflow.BuildConfig
import com.rsln.wordflow.data.auth.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Typed HTTP client for the WordFlow Go backend.
 *
 * Every call is a suspend function that either returns a DTO or
 * throws a BackendException subclass. ViewModels / repositories
 * catch the exception and map it to UI state (toast, error state,
 * etc.).
 *
 * Base URL comes from BuildConfig.BACKEND_URL. Production builds default
 * to the hosted backend. Override via WORDFLOW_BACKEND_URL=... in
 * android/local.properties for local development.
 */
class BackendClient(private val authManager: AuthManager) {

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(authManager))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = BuildConfig.BACKEND_URL.trimEnd('/')

    val currentBaseUrl: String get() = baseUrl

    // ---------- Health -------------------------------------------

    suspend fun health(): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/health").get().build()
        execute(req) { it }
    }

    // ---------- Auth (passwordless email-code flow) --------------

    /** Kick off a sign-in by email. Server emails a 6-digit code. */
    suspend fun emailAuthStart(email: String): EmailStartResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/v1/auth/email/start")
            .post(jsonBody(EmailStartRequest(email)))
            .build()
        execute(req) { gson.fromJson(it, EmailStartResponse::class.java) }
    }

    /** Complete the sign-in by redeeming the emailed code. */
    suspend fun emailAuthVerify(stateId: String, code: String): AuthResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/v1/auth/email/verify")
            .post(jsonBody(EmailVerifyRequest(stateId, code)))
            .build()
        execute(req) { gson.fromJson(it, AuthResponse::class.java) }
    }

    // ---------- Words --------------------------------------------

    suspend fun createWord(request: CreateWordRequest): WordDto = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/v1/words")
            .post(jsonBody(request))
            .build()
        execute(req) { gson.fromJson(it, WordDto::class.java) }
    }

    suspend fun updateWord(remoteId: String, request: UpdateWordRequest): WordDto = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/v1/words/$remoteId")
            .patch(jsonBody(request))
            .build()
        execute(req) { gson.fromJson(it, WordDto::class.java) }
    }

    suspend fun deleteWord(remoteId: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/v1/words/$remoteId")
            .delete()
            .build()
        execute<Unit>(req) { }
    }

    suspend fun batchProgress(updates: List<ProgressUpdate>): ProgressBatchResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/v1/words/progress/batch")
            .patch(jsonBody(ProgressBatchRequest(updates)))
            .build()
        execute(req) { gson.fromJson(it, ProgressBatchResponse::class.java) }
    }

    // ---------- Collections --------------------------------------

    suspend fun createCollection(request: CreateCollectionRequest): CollectionDto = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/v1/collections")
            .post(jsonBody(request))
            .build()
        execute(req) { gson.fromJson(it, CollectionDto::class.java) }
    }

    suspend fun updateCollection(remoteId: String, request: UpdateCollectionRequest): CollectionDto = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/v1/collections/$remoteId")
            .patch(jsonBody(request))
            .build()
        execute(req) { gson.fromJson(it, CollectionDto::class.java) }
    }

    suspend fun deleteCollection(remoteId: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/v1/collections/$remoteId")
            .delete()
            .build()
        execute<Unit>(req) { }
    }

    // ---------- Links --------------------------------------------

    suspend fun linkWord(collectionRemoteId: String, wordRemoteId: String): WordCollectionDto =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$baseUrl/v1/collections/$collectionRemoteId/words/$wordRemoteId")
                .post(EMPTY_JSON)
                .build()
            execute(req) { gson.fromJson(it, WordCollectionDto::class.java) }
        }

    suspend fun unlinkWord(collectionRemoteId: String, wordRemoteId: String) =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$baseUrl/v1/collections/$collectionRemoteId/words/$wordRemoteId")
                .delete()
                .build()
            execute<Unit>(req) { }
        }

    // ---------- Sync pull ----------------------------------------

    suspend fun syncPull(sinceIso: String? = null): SyncPullResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/v1/sync/pull".toHttpUrl().newBuilder().apply {
            if (!sinceIso.isNullOrBlank()) addQueryParameter("since", sinceIso)
        }.build()
        val req = Request.Builder().url(url).get().build()
        execute(req) { gson.fromJson(it, SyncPullResponse::class.java) }
    }

    // ---------- AI proxy (Phase 15) -------------------------------

    /**
     * Generic POST for the AI endpoints. OpenRouterService owns the
     * request shapes and JSON schemas — this method just handles
     * transport. Returns the raw JSON string of the response body so
     * the caller can parse the model-specific fields itself.
     */
    suspend fun postAi(path: String, body: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl$path")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        execute(req) { it }
    }

    // ---------- Internal -----------------------------------------

    private fun jsonBody(obj: Any): RequestBody =
        gson.toJson(obj).toRequestBody(JSON_MEDIA_TYPE)

    /**
     * Executes a request, maps common status codes to typed
     * exceptions, and passes the response body string to [parse] on
     * 2xx. 204 is handled by passing "" to parse (so Unit-returning
     * callers don't crash on an empty body).
     */
    private inline fun <T> execute(req: Request, parse: (String) -> T): T {
        val response: Response = try {
            client.newCall(req).execute()
        } catch (e: IOException) {
            throw BackendException.Network(e.message ?: "network error", e)
        }

        response.use { resp ->
            val body = resp.body?.string().orEmpty()
            val status = resp.code
            if (status in 200..299) {
                return try {
                    parse(body)
                } catch (e: JsonSyntaxException) {
                    throw BackendException.Parse("invalid JSON from server: ${e.message}", e)
                }
            }

            val errorBody = try {
                if (body.isNotBlank()) gson.fromJson(body, ErrorResponse::class.java) else ErrorResponse()
            } catch (_: Exception) { ErrorResponse(error = body.take(200)) }

            throw when (status) {
                401 -> {
                    authManager.signOut()
                    BackendException.Unauthorized(errorBody.messageOr("not signed in or token expired"))
                }
                404 -> BackendException.NotFound(errorBody.messageOr("not found"))
                409 -> BackendException.Conflict(errorBody.codeOr("conflict"), errorBody.existingId)
                429 -> BackendException.RateLimited(errorBody.messageOr("rate limit exceeded"))
                in 500..599 -> BackendException.Server(status, errorBody.codeOr(body.take(200)))
                else -> BackendException.Http(status, errorBody.messageOr(errorBody.codeOr(body.take(200))))
            }
        }
    }

    private fun ErrorResponse.messageOr(fallback: String): String =
        message?.takeIf { it.isNotBlank() }
            ?: error?.takeIf { it.isNotBlank() }
            ?: fallback.takeIf { it.isNotBlank() }
            ?: "request failed"

    private fun ErrorResponse.codeOr(fallback: String): String =
        error?.takeIf { it.isNotBlank() }
            ?: message?.takeIf { it.isNotBlank() }
            ?: fallback.takeIf { it.isNotBlank() }
            ?: "request_failed"

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val EMPTY_JSON = "{}".toRequestBody("application/json".toMediaType())
    }
}

/**
 * Typed exceptions the BackendClient throws. Repositories catch
 * these and surface them to the UI. Unauthorized bubbles up to a
 * "please sign in" state, Conflict becomes a 409 UI message, etc.
 */
sealed class BackendException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(msg: String, cause: Throwable) : BackendException(msg, cause)
    class Unauthorized(msg: String) : BackendException(msg)
    class NotFound(msg: String) : BackendException(msg)
    class Conflict(val code: String, val existingId: String?) :
        BackendException("conflict: $code (existing_id=$existingId)")
    class RateLimited(msg: String) : BackendException(msg)
    class Server(val status: Int, msg: String) : BackendException("server $status: $msg")
    class Http(val status: Int, msg: String) : BackendException("http $status: $msg")
    class Parse(msg: String, cause: Throwable) : BackendException(msg, cause)
}
