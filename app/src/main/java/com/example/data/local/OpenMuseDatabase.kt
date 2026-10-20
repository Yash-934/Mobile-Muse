package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectWorkspaceEntity::class,
        ConversationThreadEntity::class,
        ChatMessageEntity::class,
        AutonomousTaskEntity::class,
        TaskStepEntity::class,
        CommandReceiptEntity::class,
        BrowserTabEntity::class,
        BookmarkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class OpenMuseDatabase : RoomDatabase() {
    abstract fun dao(): OpenMuseDao

    companion object {
        @Volatile
        private var INSTANCE: OpenMuseDatabase? = null

        fun getInstance(context: Context): OpenMuseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OpenMuseDatabase::class.java,
                    "openmuse_local.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun createInMemory(context: Context): OpenMuseDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                OpenMuseDatabase::class.java
            ).allowMainThreadQueries().build()
        }
    }
}
