package com.enship.travel.protocol

/**
 * Checksum NMEA-like utilise par le firmware EnerShip.
 *
 * Algorithme (identique cote Arduino `calculerChecksum`) :
 *   XOR de tous les caracteres SITUES APRES le `$` initial,
 *   jusqu'au `*` exclu (le `*` lui-meme et le `$` ne sont pas inclus).
 *
 * Cote Arduino, le calcul part de l'index 1 de la chaine `"$..."` (saute `$`)
 * et s'arrete a la fin de la chaine — la chaine fournie n'inclut PAS encore
 * le `*CS`. On reproduit exactement ce comportement.
 */
object Checksum {

    /**
     * Calcule le checksum sur le corps d'une trame.
     * @param body trame SANS le `*CS`, en incluant ou non le `$` de tete.
     *             Le `$` eventuel est ignore (comme le firmware).
     */
    fun compute(body: String): Int {
        var cs = 0
        val start = if (body.startsWith("$")) 1 else 0
        for (i in start until body.length) {
            cs = cs xor body[i].code
        }
        return cs and 0xFF
    }

    /** Formate un checksum sur 2 caracteres hexadecimaux majuscules. */
    fun format(cs: Int): String = "%02X".format(cs and 0xFF)

    /** Construit une trame complete `$...*CS` prete a envoyer (sans CRLF). */
    fun frame(body: String): String {
        val normalized = if (body.startsWith("$")) body else "$$body"
        return "$normalized*${format(compute(normalized))}"
    }
}
