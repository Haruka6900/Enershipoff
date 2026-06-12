package com.enship.travel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {

    @Insert
    suspend fun insert(entity: TelemetryEntity): Long

    /** Les [limit] derniers echantillons, du plus recent au plus ancien. */
    @Query("SELECT * FROM telemetry_log ORDER BY timestamp DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<TelemetryEntity>>

    /** Echantillons sur une fenetre temporelle, ordre chronologique. */
    @Query("SELECT * FROM telemetry_log WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun since(since: Long): Flow<List<TelemetryEntity>>

    @Query("SELECT * FROM telemetry_log ORDER BY timestamp ASC")
    suspend fun all(): List<TelemetryEntity>

    @Query("SELECT COUNT(*) FROM telemetry_log")
    fun count(): Flow<Int>

    /** Purge des echantillons plus vieux que [before] (retention). */
    @Query("DELETE FROM telemetry_log WHERE timestamp < :before")
    suspend fun purgeOlderThan(before: Long)

    @Query("DELETE FROM telemetry_log")
    suspend fun clear()
}
