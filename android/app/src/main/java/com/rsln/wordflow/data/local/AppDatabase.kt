package com.rsln.wordflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rsln.wordflow.data.local.dao.CollectionDao
import com.rsln.wordflow.data.local.dao.WordDao
import com.rsln.wordflow.data.local.entity.CollectionEntity
import com.rsln.wordflow.data.local.entity.WordCollectionCrossRef
import com.rsln.wordflow.data.local.entity.WordEntity

@Database(
    entities = [
        WordEntity::class,
        CollectionEntity::class,
        WordCollectionCrossRef::class
    ],
    // v2 adds: WordEntity.remoteId/updatedAt/pendingProgressPush,
    // CollectionEntity.remoteId/updatedAt, plus unique indexes on
    // remoteId for both tables. fallbackToDestructiveMigration is
    // still on (see below) so dev installs wipe data on upgrade.
    // A proper Migration(1, 2) with UUID backfill lands in Phase 14.
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun wordDao(): WordDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wordflow.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
