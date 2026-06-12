package com.enship.travel.bluetooth

/**
 * Entree de journal brut pour la console de debug / packet inspector.
 */
data class RawLogEntry(
    val direction: Direction,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
) {
    enum class Direction { IN, OUT, ERROR }
}
