package com.rsln.wordflow.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A vocabulary word in the local Room cache.
 *
 * Local `id` is Long auto-increment (unchanged from v1) — Android code
 * navigates by Long IDs everywhere. The server's UUID for this row
 * lives in `remoteId`, populated once a successful POST /v1/words
 * round-trips. Words created offline (which, per the design, now error
 * at the repository layer) never end up in Room, so a local row that
 * exists here has always been confirmed by the server first.
 *
 * `updatedAt` mirrors the server's updated_at field and is the cursor
 * for sync pulls. `pendingProgressPush` is the local-only dirty flag
 * that flags rows where show_count / last_shown_date / is_learned have
 * been bumped offline and still need to be pushed to the backend.
 */
@Entity(
    tableName = "words",
    indices = [
        Index(value = ["normalizedWord"], unique = true),
        Index(value = ["remoteId"], unique = true)
    ]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val remoteId: String? = null,
    val originalWord: String,
    val normalizedWord: String,
    val translation: String,
    val exampleUsage: String = "",
    val explanation: String = "",
    val pronunciation: String = "",
    val addedDate: Long = System.currentTimeMillis(),
    val lastShownDate: Long = 0,
    val showCount: Int = 0,
    val difficulty: Int = 5,
    val isLearned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val pendingProgressPush: Boolean = false
)
