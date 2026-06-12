package com.enship.travel.protocol

/**
 * Trame `$DATA` decodee — instantane complet des 24 valeurs transmises
 * par l'Arduino toutes les 500 ms.
 *
 * Format firmware :
 * `$DATA,C0..C7,V0..V7,P,T,NC,NR,CPL,RPM,LAT,LON,SPD,HDG*CS\r\n`
 *
 * Les champs GPS LAT/LON valent `NA` lorsque le fix n'est pas valide :
 * dans ce cas [gpsValid] est false et [latitude]/[longitude] sont null.
 */
data class TelemetrySnapshot(
    /** 8 courants (A), index 0..7 = Equipment.index */
    val currents: List<Float>,
    /** 8 tensions (V), index 0..7 = Equipment.index */
    val voltages: List<Float>,
    val oilPressurePsi: Float,
    val coolantTempC: Float,
    val fuelLevelPct: Float,
    val coolantLevelPct: Float,
    val torqueNm: Float,
    val rpm: Int,
    val latitude: Double?,
    val longitude: Double?,
    val speedKnots: Float,
    val headingDeg: Float,
    val gpsValid: Boolean,
    /** Horodatage de reception local (epoch ms). */
    val receivedAt: Long = System.currentTimeMillis(),
) {
    /** Puissance instantanee approximative par equipement (W). */
    fun powerWatts(index: Int): Float =
        if (index in currents.indices && index in voltages.indices)
            currents[index] * voltages[index] else 0f

    /** Puissance totale du bord (W). */
    val totalPowerWatts: Float
        get() = Equipment.ordered.sumOf { powerWatts(it.index).toDouble() }.toFloat()

    companion object {
        const val CURRENT_COUNT = 8
        const val VOLTAGE_COUNT = 8
        /** Nombre total de valeurs apres le tag `$DATA`. */
        const val FIELD_COUNT = 24
    }
}
