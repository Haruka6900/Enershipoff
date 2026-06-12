package com.enship.travel.protocol

/**
 * Reassemble un flux d'octets BT fragmente en lignes completes.
 *
 * Le HC-06 livre les donnees par paquets arbitraires : une trame peut etre
 * coupee en plusieurs `read()`, ou plusieurs trames peuvent arriver d'un coup.
 * Cet assembleur accumule les octets et n'emet que des lignes terminees par
 * `\n`, en retirant le `\r` residuel (terminateur firmware `\r\n`).
 *
 * Protection memoire : si aucune fin de ligne n'arrive avant
 * [maxBufferLength] caracteres, le tampon est purge (trame corrompue / bruit).
 */
class FrameAssembler(
    private val maxBufferLength: Int = 4096,
) {
    private val buffer = StringBuilder()

    /**
     * Ajoute un fragment recu et retourne les lignes completes extraites.
     * Les lignes vides sont ignorees.
     */
    fun append(chunk: String): List<String> {
        buffer.append(chunk)

        if (buffer.length > maxBufferLength) {
            // On tente de conserver la derniere portion potentiellement utile.
            val lastDollar = buffer.lastIndexOf("$")
            if (lastDollar > 0) {
                buffer.delete(0, lastDollar)
            } else {
                buffer.setLength(0)
            }
        }

        val lines = mutableListOf<String>()
        var newline = buffer.indexOf("\n")
        while (newline >= 0) {
            val line = buffer.substring(0, newline).trim()
            buffer.delete(0, newline + 1)
            if (line.isNotEmpty()) lines.add(line)
            newline = buffer.indexOf("\n")
        }
        return lines
    }

    fun reset() {
        buffer.setLength(0)
    }
}
