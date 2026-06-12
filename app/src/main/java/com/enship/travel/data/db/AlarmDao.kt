package com.enship.travel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Insert
    suspend fun insert(entity: AlarmEntity): Long

    @Query("SELECT * FROM alarm_log ORDER BY raisedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarm_log WHERE clearedAt IS NULL ORDER BY raisedAt DESC")
    fun active(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarm_log ORDER BY raisedAt ASC")
    suspend fun all(): List<AlarmEntity>

    /** Marque comme resolues toutes les alarmes actives d'un code donne. */
    @Query("UPDATE alarm_log SET clearedAt = :clearedAt WHERE code = :code AND clearedAt IS NULL")
    suspend fun clearActive(code: String, clearedAt: Long)

    /** Marque toutes les alarmes actives comme resolues (reset global). */
    @Query("UPDATE alarm_log SET clearedAt = :clearedAt WHERE clearedAt IS NULL")
    suspend fun clearAll(clearedAt: Long)

    @Query("SELECT COUNT(*) FROM alarm_log WHERE code = :code AND clearedAt IS NULL")
    suspend fun activeCount(code: String): Int

    @Query("DELETE FROM alarm_log")
    suspend fun clear()
}
