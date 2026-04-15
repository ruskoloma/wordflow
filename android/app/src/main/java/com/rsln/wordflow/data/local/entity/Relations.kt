package com.rsln.wordflow.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class WordWithCollections(
    @Embedded val word: WordEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            WordCollectionCrossRef::class,
            parentColumn = "wordId",
            entityColumn = "collectionId"
        )
    )
    val collections: List<CollectionEntity>
)

data class CollectionWithWords(
    @Embedded val collection: CollectionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            WordCollectionCrossRef::class,
            parentColumn = "collectionId",
            entityColumn = "wordId"
        )
    )
    val words: List<WordEntity>
)
