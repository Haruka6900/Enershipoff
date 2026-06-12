package com.enship.travel.bluetooth

import com.enship.travel.protocol.InvalidReason

/**
 * Statistiques de communication accumulees pendant une session de liaison.
 * Sert au diagnostic et a l'indicateur de qualite de liaison.
 */
data class CommStats(
    val framesReceived: Long = 0,
    val dataFrames: Long = 0,
    val alarmFrames: Long = 0,
    val ackFrames: Long = 0,
    val confFrames: Long = 0,
    val pongFrames: Long = 0,
    val unknownFrames: Long = 0,
    val invalidFrames: Long = 0,
    val checksumErrors: Long = 0,
    val fieldCountErrors: Long = 0,
    val bytesReceived: Long = 0,
    val bytesSent: Long = 0,
    val commandsSent: Long = 0,
    val reconnects: Long = 0,
    /** Epoch ms de la derniere trame $DATA valide. */
    val lastDataAt: Long = 0,
    /** Intervalle moyen entre 2 trames $DATA (ms), lisse. */
    val avgIntervalMs: Double = 0.0,
    /** Debut de la session courante (epoch ms). */
    val sessionStart: Long = 0,
) {
    /** Taux d'erreur global (0..1). */
    val errorRate: Double
        get() = if (framesReceived == 0L) 0.0
        else invalidFrames.toDouble() / framesReceived.toDouble()

    fun withInvalid(reason: InvalidReason): CommStats {
        var checksum = checksumErrors
        var fieldCount = fieldCountErrors
        when (reason) {
            InvalidReason.CHECKSUM_MISMATCH, InvalidReason.CHECKSUM_MISSING -> checksum++
            InvalidReason.FIELD_COUNT -> fieldCount++
            else -> {}
        }
        return copy(
            framesReceived = framesReceived + 1,
            invalidFrames = invalidFrames + 1,
            checksumErrors = checksum,
            fieldCountErrors = fieldCount,
        )
    }
}
