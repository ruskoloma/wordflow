package com.rsln.wordflow.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "words",
    indices = [Index(value = ["normalizedWord"], unique = true)]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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
    val isLearned: Boolean = false
)
