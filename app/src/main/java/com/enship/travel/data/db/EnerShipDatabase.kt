package com.enship.travel.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TelemetryEntity::class, AlarmEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class EnerShipDatabase : RoomDatabase() {

    abstract fun telemetryDao(): TelemetryDao
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile
        private var INSTANCE: EnerShipDatabase? = null

        fun get(context: Context): EnerShipDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EnerShipDatabase::class.java,
                    "enership.db",
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
