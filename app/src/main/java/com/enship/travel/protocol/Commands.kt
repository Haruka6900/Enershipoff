package com.enship.travel.protocol

/**
 * Construit les commandes sortantes vers l'Arduino.
 *
 * Le firmware lit les commandes via `Serial1.readStringUntil('\n')` puis
 * `cmd.trim()`. Il compare ensuite avec des chaines EXACTES (`$PING`,
 * `$GET_CONF`, `$RESET_ALARME`) ou des prefixes (`$SET_SEUIL_TEMP,`).
 *
 * Important : le firmware ne verifie PAS le checksum des commandes entrantes.
 * On envoie donc des commandes simples terminees par `\r\n`. Chaque commande
 * doit se terminer par `\n` pour que `readStringUntil('\n')` la delimite.
 */
object Commands {

    private const val TERMINATOR = "\r\n"

    val PING = "\$PING$TERMINATOR"
    val GET_CONF = "\$GET_CONF$TERMINATOR"
    val RESET_ALARME = "\$RESET_ALARME$TERMINATOR"

    fun setSeuilTemp(value: Float): String =
        "\$SET_SEUIL_TEMP,${format(value)}$TERMINATOR"

    fun setSeuilPression(value: Float): String =
        "\$SET_SEUIL_PRESSION,${format(value)}$TERMINATOR"

    fun setSeuilCarbu(value: Float): String =
        "\$SET_SEUIL_CARBU,${format(value)}$TERMINATOR"

    /** Identifiant de commande renvoye dans les `$ACK,<cmd>,OK`. */
    object AckId {
        const val TEMP = "SET_SEUIL_TEMP"
        const val PRESSION = "SET_SEUIL_PRESSION"
        const val CARBU = "SET_SEUIL_CARBU"
        const val RESET = "RESET_ALARME"
    }

    private fun format(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString()
        else "%.1f".format(value)
}
