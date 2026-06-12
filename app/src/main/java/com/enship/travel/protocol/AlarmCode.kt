package com.enship.travel.protocol

/**
 * Codes d'alarme emis par le firmware (`$ALARM,<code>`).
 */
enum class AlarmCode(val code: String, val label: String) {
    P_LOW("P_LOW", "Pression d'huile basse"),
    P_HIGH("P_HIGH", "Pression d'huile haute"),
    T_HIGH("T_HIGH", "Temperature refroidissement haute"),
    NC_LOW("NC_LOW", "Niveau carburant bas"),
    NR_LOW("NR_LOW", "Niveau refroidissement bas"),
    UNKNOWN("UNKNOWN", "Alarme inconnue");

    companion object {
        fun fromCode(raw: String): AlarmCode =
            entries.firstOrNull { it.code == raw.trim().uppercase() } ?: UNKNOWN
    }
}
