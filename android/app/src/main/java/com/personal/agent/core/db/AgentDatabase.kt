package com.personal.agent.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DeviceEntity::class,
        JobEntity::class,
        AutomationEntity::class,
        AutomationRunEntity::class,
        NotificationEntity::class,
        ScreenEntity::class,
        OcrEntity::class,
        VisionEntity::class,
        LogEntity::class,
        PermissionEntity::class,
        ConfigEntity::class,
        CommandEntity::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class AgentDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao
    abstract fun jobDao(): JobDao
    abstract fun automationDao(): AutomationDao
    abstract fun automationRunDao(): AutomationRunDao
    abstract fun notificationDao(): NotificationDao
    abstract fun screenDao(): ScreenDao
    abstract fun ocrDao(): OcrDao
    abstract fun visionDao(): VisionDao
    abstract fun logDao(): LogDao
    abstract fun permissionDao(): PermissionDao
    abstract fun configDao(): ConfigDao
    abstract fun commandDao(): CommandDao

    companion object {
        private const val DATABASE_NAME = "agent.db"

        @Volatile
        private var INSTANCE: AgentDatabase? = null

        /**
         * Returns the singleton [AgentDatabase] instance, creating it if necessary.
         * Thread-safe via double-checked locking with [synchronized].
         */
        fun getInstance(context: Context): AgentDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): AgentDatabase {
            return Room.databaseBuilder(
                appContext,
                AgentDatabase::class.java,
                DATABASE_NAME
            )
                // Keep WAL mode for better concurrent read performance
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }
    }
}
