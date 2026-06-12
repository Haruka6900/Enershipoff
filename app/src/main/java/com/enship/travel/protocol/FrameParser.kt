package com.enship.travel.protocol

/**
 * Parseur tolerant aux pannes du protocole EnerShip.
 *
 * Garanties :
 *  - ne leve JAMAIS d'exception (toute anomalie => [ParseResult.Invalid]) ;
 *  - verifie le checksum lorsqu'il est present (`...*CS`) ;
 *  - accepte les trames sans checksum (`$ACK`, `$CONF` cote firmware) ;
 *  - rejette les trames `$DATA` dont le nombre de champs est incorrect ;
 *  - remplace les valeurs numeriques illisibles par 0 plutot que d'echouer
 *    sur l'ensemble de la trame (recuperation sur champ manquant).
 *
 * Le decoupage en lignes (sur `\n`, suppression du `\r`) est realise en amont
 * par [FrameAssembler]. Ce parseur travaille sur une ligne deja isolee.
 */
object FrameParser {

    fun parse(rawLine: String): ParseResult {
        val line = rawLine.trim()
        if (line.isEmpty()) return ParseResult.Invalid(rawLine, InvalidReason.EMPTY)

        val dollar = line.indexOf('$')
        if (dollar < 0) return ParseResult.Invalid(line, InvalidReason.NO_DOLLAR)

        // On ignore tout bruit precedant le '$' (resynchronisation).
        val framed = line.substring(dollar)

        // Separation corps / checksum si un '*' est present.
        val starIdx = framed.lastIndexOf('*')
        val body: String
        val checksumOk: Boolean?
        if (starIdx >= 0) {
            body = framed.substring(0, starIdx)
            val csText = framed.substring(starIdx + 1).trim()
            checksumOk = verifyChecksum(body, csText)
            if (checksumOk == false) {
                return ParseResult.Invalid(framed, InvalidReason.CHECKSUM_MISMATCH)
            }
            if (checksumOk == null) {
                return ParseResult.Invalid(framed, InvalidReason.CHECKSUM_MISSING)
            }
        } else {
            body = framed
        }

        val fields = body.split(',')
        return when (fields[0]) {
            "\$DATA" -> parseData(framed, fields)
            "\$ALARM" -> parseAlarm(framed, fields)
            "\$ACK" -> parseAck(fields)
            "\$CONF" -> parseConf(body, fields)
            "\$PONG" -> ParseResult.Pong
            else -> ParseResult.Unknown(framed)
        }
    }

    /** @return true si valide, false si mismatch, null si checksum illisible. */
    private fun verifyChecksum(body: String, csText: String): Boolean? {
        if (csText.length < 2) return null
        val provided = csText.take(2).toIntOrNull(16) ?: return null
        return Checksum.compute(body) == provided
    }

    private fun parseData(raw: String, fields: List<String>): ParseResult {
        // fields[0] = "$DATA" ; on attend FIELD_COUNT valeurs ensuite.
        val values = fields.drop(1)
        if (values.size < TelemetrySnapshot.FIELD_COUNT) {
            return ParseResult.Invalid(raw, InvalidReason.FIELD_COUNT)
        }

        val currents = (0 until 8).map { values[it].toFloatOrZero() }
        val voltages = (8 until 16).map { values[it].toFloatOrZero() }
        val oilPressure = values[16].toFloatOrZero()
        val temp = values[17].toFloatOrZero()
        val fuel = values[18].toFloatOrZero()
        val coolant = values[19].toFloatOrZero()
        val torque = values[20].toFloatOrZero()
        val rpm = values[21].toFloatOrZero().toInt()

        val latRaw = values[22].trim()
        val lonRaw = values[23].trim()
        val gpsValid = !latRaw.equals("NA", ignoreCase = true) &&
            !lonRaw.equals("NA", ignoreCase = true)
        val lat = if (gpsValid) latRaw.toDoubleOrNull() else null
        val lon = if (gpsValid) lonRaw.toDoubleOrNull() else null

        val speed = values.getOrNull(24)?.toFloatOrZero() ?: 0f
        val heading = values.getOrNull(25)?.toFloatOrZero() ?: 0f

        val snapshot = TelemetrySnapshot(
            currents = currents,
            voltages = voltages,
            oilPressurePsi = oilPressure,
            coolantTempC = temp,
            fuelLevelPct = fuel,
            coolantLevelPct = coolant,
            torqueNm = torque,
            rpm = rpm,
            latitude = lat,
            longitude = lon,
            speedKnots = speed,
            headingDeg = heading,
            gpsValid = gpsValid && lat != null && lon != null,
        )
        return ParseResult.Data(snapshot)
    }

    private fun parseAlarm(raw: String, fields: List<String>): ParseResult {
        val code = fields.getOrNull(1)?.let { AlarmCode.fromCode(it) } ?: AlarmCode.UNKNOWN
        return ParseResult.Alarm(code, raw)
    }

    private fun parseAck(fields: List<String>): ParseResult {
        val command = fields.getOrNull(1)?.trim().orEmpty()
        val status = fields.getOrNull(2)?.trim().orEmpty().ifEmpty { "OK" }
        return ParseResult.Ack(command, status)
    }

    private fun parseConf(body: String, fields: List<String>): ParseResult {
        // Format : $CONF,TEMP_MAX=95.0,P_MIN=1.5,CARBU_MIN=10.0
        var tempMax: Float? = null
        var pMin: Float? = null
        var carbuMin: Float? = null
        for (token in fields.drop(1)) {
            val eq = token.indexOf('=')
            if (eq < 0) continue
            val key = token.substring(0, eq).trim().uppercase()
            val value = token.substring(eq + 1).trim().toFloatOrNull()
            when (key) {
                "TEMP_MAX" -> tempMax = value
                "P_MIN" -> pMin = value
                "CARBU_MIN" -> carbuMin = value
            }
        }
        return ParseResult.Conf(tempMax, pMin, carbuMin)
    }

    private fun String.toFloatOrZero(): Float = trim().toFloatOrNull() ?: 0f
}
