package com.rsln.wordflow.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A named collection/bucket for grouping words. Same local-vs-remote
 * id split as WordEntity — Long id for in-app navigation, String
 * remoteId populated after the server confirms the POST.
 */
@Entity(
    tableName = "collections",
    indices = [Index(value = ["remoteId"], unique = true)]
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val remoteId: String? = null,
    val name: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
