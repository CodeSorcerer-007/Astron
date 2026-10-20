package com.astron.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TransferSessionEntity::class,
        TransferFileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AstronDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao

    companion object {
        const val DATABASE_NAME = "astron_database.db"
    }
}
