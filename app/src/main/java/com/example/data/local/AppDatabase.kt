package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NotificationEntity::class,
        CallLogEntity::class,
        PinnedAppEntity::class,
        NotificationSettingEntity::class,
        SenderToneRuleEntity::class,
        PinnedThreadEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun callLogDao(): CallLogDao
    abstract fun pinnedAppDao(): PinnedAppDao
    abstract fun notificationSettingDao(): NotificationSettingDao
    abstract fun senderToneRuleDao(): SenderToneRuleDao
    abstract fun pinnedThreadDao(): PinnedThreadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omnihub_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
