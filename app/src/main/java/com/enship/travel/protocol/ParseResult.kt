package com.enship.travel.protocol

/**
 * Resultat du decodage d'une ligne brute recue de l'Arduino.
 *
 * Le parseur est tolerant aux pannes : toute ligne invalide produit un
 * [ParseResult.Invalid] (jamais d'exception) afin que la couche superieure
 * puisse comptabiliser l'erreur sans interrompre le flux.
 */
sealed interface ParseResult {

    /** Trame `$DATA` valide et complete. */
    data class Data(val snapshot: TelemetrySnapshot) : ParseResult

    /** Trame `$ALARM,<code>`. */
    data class Alarm(val code: AlarmCode, val raw: String) : ParseResult

    /** Reponse `$ACK,<command>,OK`. */
    data class Ack(val command: String, val status: String) : ParseResult

    /** Reponse `$CONF,TEMP_MAX=..,P_MIN=..,CARBU_MIN=..`. */
    data class Conf(
        val tempMax: Float?,
        val pressionMin: Float?,
        val carbuMin: Float?,
    ) : ParseResult

    /** Reponse `$PONG` a un `$PING`. */
    data object Pong : ParseResult

    /** Trame reconnue mais non geree (extension future) — conservee telle quelle. */
    data class Unknown(val raw: String) : ParseResult

    /** Trame rejetee : checksum invalide, champs manquants, corruption... */
    data class Invalid(val raw: String, val reason: InvalidReason) : ParseResult
}

enum class InvalidReason {
    EMPTY,
    NO_DOLLAR,
    CHECKSUM_MISSING,
    CHECKSUM_MISMATCH,
    FIELD_COUNT,
    NUMBER_FORMAT,
    TRUNCATED,
}
