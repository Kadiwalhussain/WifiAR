package com.wifiar.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RssiSampleEntity::class,
        MappingSessionEntity::class,
        SpeedTestEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class WifiArDatabase : RoomDatabase() {

    abstract fun rssiSampleDao(): RssiSampleDao
    abstract fun mappingSessionDao(): MappingSessionDao
    abstract fun speedTestDao(): SpeedTestDao

    companion object {
        private const val DB_NAME = "wifiar.db"

        @Volatile
        private var instance: WifiArDatabase? = null

        fun getInstance(context: Context): WifiArDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WifiArDatabase::class.java,
                    DB_NAME,
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
