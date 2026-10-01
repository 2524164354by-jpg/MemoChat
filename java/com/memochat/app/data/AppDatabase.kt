package com.memochat.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Entry::class, Message::class, DayMeta::class, TimeCapsule::class],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun messageDao(): MessageDao
    abstract fun dayMetaDao(): DayMetaDao
    abstract fun capsuleDao(): CapsuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "memochat.db"
                ).fallbackToDestructiveMigration()
                  .build().also { INSTANCE = it }
            }
        }
    }
}
