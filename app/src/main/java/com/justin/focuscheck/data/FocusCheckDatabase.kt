package com.justin.focuscheck.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        CheckInEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class FocusCheckDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun checkInDao(): CheckInDao

    companion object {

        @Volatile
        private var instance: FocusCheckDatabase? = null

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {
                    database.execSQL(
                        """
                        ALTER TABLE tasks
                        ADD COLUMN pauseReason TEXT
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE tasks
                        ADD COLUMN pausedAt INTEGER
                        """.trimIndent()
                    )
                }
            }

        private val MIGRATION_2_3 =
            object : Migration(2, 3) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS check_ins (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            taskId INTEGER,
                            taskTitle TEXT NOT NULL,
                            reportedStatus TEXT NOT NULL,
                            note TEXT NOT NULL,
                            nextTaskId INTEGER,
                            nextTaskTitle TEXT
                        )
                        """.trimIndent()
                    )
                }
            }
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {
                    database.execSQL(
                        """
                ALTER TABLE tasks
                ADD COLUMN dueDateEpochDay INTEGER
                """.trimIndent()
                    )
                }
            }

        fun getDatabase(
            context: Context
        ): FocusCheckDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    FocusCheckDatabase::class.java,
                    "focus_check_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4
                    )
                    .build()
                    .also { database ->
                        instance = database
                    }
            }
        }
    }
}