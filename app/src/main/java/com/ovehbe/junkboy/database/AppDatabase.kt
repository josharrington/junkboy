package com.ovehbe.junkboy.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [FilteredMessage::class, AllowedSender::class, ChatMessage::class, AutoReplyLog::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun filteredMessageDao(): FilteredMessageDao
    abstract fun allowedSenderDao(): AllowedSenderDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun autoReplyLogDao(): AutoReplyLogDao
    
    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `auto_reply_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`senderNumber` TEXT NOT NULL, " +
                        "`repliedAt` INTEGER NOT NULL, " +
                        "`replyText` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_auto_reply_log_senderNumber` ON `auto_reply_log` (`senderNumber`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `auto_reply_log` ADD COLUMN `reason` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `filtered_messages` ADD COLUMN `isTestMode` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `filtered_messages` ADD COLUMN `decisionSummary` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `filtered_messages` ADD COLUMN `decisionTrace` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `filtered_messages` ADD COLUMN `decisionStep` TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "junkboy_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 