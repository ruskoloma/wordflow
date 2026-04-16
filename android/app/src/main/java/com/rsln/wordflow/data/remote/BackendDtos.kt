package com.rsln.wordflow.data.remote

import com.google.gson.annotations.SerializedName

/*
 * Wire DTOs for the Go backend. Field names are @SerializedName-tagged
 * to use snake_case on the wire while keeping the Kotlin side in
 * idiomatic camelCase. Gson handles the mapping.
 *
 * Dates come in as ISO 8601 strings; the repository layer converts
 * them to epoch millis when mapping to Room entities.
 */

// ----- Word -----

data class WordDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("original_word") val originalWord: String,
    @SerializedName("normalized_word") val normalizedWord: String,
    @SerializedName("translation") val translation: String,
    @SerializedName("example_usage") val exampleUsage: String = "",
    @SerializedName("explanation") val explanation: String = "",
    @SerializedName("pronunciation") val pronunciation: String = "",
    @SerializedName("added_date") val addedDate: String,
    @SerializedName("last_shown_date") val lastShownDate: String? = null,
    @SerializedName("show_count") val showCount: Int = 0,
    @SerializedName("difficulty") val difficulty: Int = 5,
    @SerializedName("is_learned") val isLearned: Boolean = false,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class CreateWordRequest(
    @SerializedName("original_word") val originalWord: String,
    @SerializedName("normalized_word") val normalizedWord: String,
    @SerializedName("translation") val translation: String,
    @SerializedName("example_usage") val exampleUsage: String = "",
    @SerializedName("explanation") val explanation: String = "",
    @SerializedName("pronunciation") val pronunciation: String = "",
    @SerializedName("difficulty") val difficulty: Int? = null,
    @SerializedName("is_learned") val isLearned: Boolean? = null
)

data class UpdateWordRequest(
    @SerializedName("original_word") val originalWord: String? = null,
    @SerializedName("translation") val translation: String? = null,
    @SerializedName("example_usage") val exampleUsage: String? = null,
    @SerializedName("explanation") val explanation: String? = null,
    @SerializedName("pronunciation") val pronunciation: String? = null,
    @SerializedName("difficulty") val difficulty: Int? = null,
    @SerializedName("is_learned") val isLearned: Boolean? = null
)

data class ProgressUpdate(
    @SerializedName("id") val id: String,
    @SerializedName("show_count") val showCount: Int,
    @SerializedName("last_shown_date") val lastShownDate: String?,
    @SerializedName("is_learned") val isLearned: Boolean
)

data class ProgressBatchRequest(
    @SerializedName("updates") val updates: List<ProgressUpdate>
)

data class ProgressBatchResponse(
    @SerializedName("applied") val applied: Int,
    @SerializedName("server_time") val serverTime: String
)

// ----- Collection -----

data class CollectionDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("name") val name: String,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class CreateCollectionRequest(
    @SerializedName("name") val name: String,
    @SerializedName("is_active") val isActive: Boolean? = null
)

data class UpdateCollectionRequest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("is_active") val isActive: Boolean? = null
)

// ----- WordCollection (M2M link) -----

data class WordCollectionDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("word_id") val wordId: String,
    @SerializedName("collection_id") val collectionId: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

// ----- Sync pull -----

data class SyncPullResponse(
    @SerializedName("server_time") val serverTime: String,
    @SerializedName("words") val words: List<WordDto> = emptyList(),
    @SerializedName("collections") val collections: List<CollectionDto> = emptyList(),
    @SerializedName("word_collections") val wordCollections: List<WordCollectionDto> = emptyList()
)

// ----- Auth (passwordless email-code flow) -----

/**
 * Body for POST /v1/auth/email/start. The server creates the Clerk
 * user if needed, kicks off a sign-in attempt, and asks Clerk to
 * email a 6-digit code to this address.
 */
data class EmailStartRequest(
    @SerializedName("email") val email: String
)

/**
 * Body returned by /v1/auth/email/start. `state_id` is the opaque
 * handle the client must pass back to /v1/auth/email/verify.
 */
data class EmailStartResponse(
    @SerializedName("state_id") val stateId: String,
    @SerializedName("email") val email: String
)

/**
 * Body for POST /v1/auth/email/verify. Swap the 6-digit code for
 * an app JWT + user_id.
 */
data class EmailVerifyRequest(
    @SerializedName("state_id") val stateId: String,
    @SerializedName("code") val code: String
)

/**
 * Success body from /v1/auth/email/verify. Email is echoed back so
 * the client can show "signed in as ..." without decoding the JWT.
 */
data class AuthResponse(
    @SerializedName("jwt") val jwt: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("email") val email: String = ""
)

// ----- Error bodies -----

data class ErrorResponse(
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("existing_id") val existingId: String? = null
)
