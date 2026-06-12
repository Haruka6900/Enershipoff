package com.enship.travel.data

import com.enship.travel.data.db.AlarmDao
import com.enship.travel.data.db.AlarmEntity
import com.enship.travel.data.db.TelemetryDao
import com.enship.travel.data.db.TelemetryEntity
import com.enship.travel.protocol.AlarmCode
import com.enship.travel.protocol.TelemetrySnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Depot unifie de persistance : ecrit la telemetrie et l'historique d'alarmes,
 * applique la retention, et fournit les flux de lecture a l'UI.
 *
 * Gestion des alarmes en fronts : le firmware reemet `$ALARM` tant que la
 * condition dure (toutes les 50 ms). On evite d'inonder la base en ne creant
 * une entree que lorsqu'une alarme passe inactive -> active.
 */
class TelemetryRepository(
    private val telemetryDao: TelemetryDao,
    private val alarmDao: AlarmDao,
) {
    fun recentTelemetry(limit: Int): Flow<List<TelemetryEntity>> =
        telemetryDao.recent(limit)

    fun telemetrySince(since: Long): Flow<List<TelemetryEntity>> =
        telemetryDao.since(since)

    fun telemetryCount(): Flow<Int> = telemetryDao.count()

    fun recentAlarms(limit: Int): Flow<List<AlarmEntity>> = alarmDao.recent(limit)

    fun activeAlarms(): Flow<List<AlarmEntity>> = alarmDao.active()

    suspend fun logTelemetry(snapshot: TelemetrySnapshot) {
        telemetryDao.insert(snapshot.toEntity())
    }

    /** Enregistre une alarme en front montant uniquement. */
    suspend fun raiseAlarm(code: AlarmCode) {
        if (alarmDao.activeCount(code.code) == 0) {
            alarmDao.insert(
                AlarmEntity(
                    code = code.code,
                    label = code.label,
                    raisedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Clot une alarme active (front descendant). */
    suspend fun clearAlarm(code: AlarmCode) {
        alarmDao.clearActive(code.code, System.currentTimeMillis())
    }

    suspend fun clearAllAlarms() {
        alarmDao.clearAll(System.currentTimeMillis())
    }

    suspend fun applyRetention(maxAgeMs: Long) {
        telemetryDao.purgeOlderThan(System.currentTimeMillis() - maxAgeMs)
    }

    suspend fun clearTelemetry() = telemetryDao.clear()

    suspend fun allTelemetry(): List<TelemetryEntity> = telemetryDao.all()

    suspend fun allAlarms(): List<AlarmEntity> = alarmDao.all()
}

private fun TelemetrySnapshot.toEntity() = TelemetryEntity(
    timestamp = receivedAt,
    c0 = currents.getOrElse(0) { 0f }, c1 = currents.getOrElse(1) { 0f },
    c2 = currents.getOrElse(2) { 0f }, c3 = currents.getOrElse(3) { 0f },
    c4 = currents.getOrElse(4) { 0f }, c5 = currents.getOrElse(5) { 0f },
    c6 = currents.getOrElse(6) { 0f }, c7 = currents.getOrElse(7) { 0f },
    v0 = voltages.getOrElse(0) { 0f }, v1 = voltages.getOrElse(1) { 0f },
    v2 = voltages.getOrElse(2) { 0f }, v3 = voltages.getOrElse(3) { 0f },
    v4 = voltages.getOrElse(4) { 0f }, v5 = voltages.getOrElse(5) { 0f },
    v6 = voltages.getOrElse(6) { 0f }, v7 = voltages.getOrElse(7) { 0f },
    oilPressure = oilPressurePsi,
    coolantTemp = coolantTempC,
    fuelLevel = fuelLevelPct,
    coolantLevel = coolantLevelPct,
    torque = torqueNm,
    rpm = rpm,
    latitude = latitude,
    longitude = longitude,
    speed = speedKnots,
    heading = headingDeg,
    gpsValid = gpsValid,
)
