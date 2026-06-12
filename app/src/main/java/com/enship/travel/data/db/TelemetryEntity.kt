package com.enship.travel.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Echantillon de telemetrie historise (une trame `$DATA` complete aplatie).
 * Stockage local uniquement (offline) — aucune synchronisation reseau.
 */
@Entity(tableName = "telemetry_log")
data class TelemetryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,

    val c0: Float, val c1: Float, val c2: Float, val c3: Float,
    val c4: Float, val c5: Float, val c6: Float, val c7: Float,

    val v0: Float, val v1: Float, val v2: Float, val v3: Float,
    val v4: Float, val v5: Float, val v6: Float, val v7: Float,

    val oilPressure: Float,
    val coolantTemp: Float,
    val fuelLevel: Float,
    val coolantLevel: Float,
    val torque: Float,
    val rpm: Int,
    val latitude: Double?,
    val longitude: Double?,
    val speed: Float,
    val heading: Float,
    val gpsValid: Boolean,
)
