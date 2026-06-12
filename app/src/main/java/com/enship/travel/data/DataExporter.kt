package com.enship.travel.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.enship.travel.data.db.AlarmEntity
import com.enship.travel.data.db.TelemetryEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exporte la telemetrie et l'historique d'alarmes au format CSV ou JSON,
 * puis fournit un Intent de partage (offline, fichier local).
 */
object DataExporter {

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)

    fun telemetryToCsv(rows: List<TelemetryEntity>): String = buildString {
        appendLine(
            "timestamp,iso," +
                "c0,c1,c2,c3,c4,c5,c6,c7," +
                "v0,v1,v2,v3,v4,v5,v6,v7," +
                "oil_pressure_psi,coolant_temp_c,fuel_pct,coolant_pct," +
                "torque_nm,rpm,latitude,longitude,speed_kn,heading_deg,gps_valid",
        )
        for (r in rows) {
            appendLine(
                "${r.timestamp},${iso.format(Date(r.timestamp))}," +
                    "${r.c0},${r.c1},${r.c2},${r.c3},${r.c4},${r.c5},${r.c6},${r.c7}," +
                    "${r.v0},${r.v1},${r.v2},${r.v3},${r.v4},${r.v5},${r.v6},${r.v7}," +
                    "${r.oilPressure},${r.coolantTemp},${r.fuelLevel},${r.coolantLevel}," +
                    "${r.torque},${r.rpm},${r.latitude ?: ""},${r.longitude ?: ""}," +
                    "${r.speed},${r.heading},${r.gpsValid}",
            )
        }
    }

    fun telemetryToJson(rows: List<TelemetryEntity>): String = buildString {
        append("[")
        rows.forEachIndexed { i, r ->
            if (i > 0) append(",")
            append(
                """{"timestamp":${r.timestamp},"iso":"${iso.format(Date(r.timestamp))}",""" +
                    """"currents":[${r.c0},${r.c1},${r.c2},${r.c3},${r.c4},${r.c5},${r.c6},${r.c7}],""" +
                    """"voltages":[${r.v0},${r.v1},${r.v2},${r.v3},${r.v4},${r.v5},${r.v6},${r.v7}],""" +
                    """"oilPressurePsi":${r.oilPressure},"coolantTempC":${r.coolantTemp},""" +
                    """"fuelPct":${r.fuelLevel},"coolantPct":${r.coolantLevel},""" +
                    """"torqueNm":${r.torque},"rpm":${r.rpm},""" +
                    """"latitude":${r.latitude ?: "null"},"longitude":${r.longitude ?: "null"},""" +
                    """"speedKnots":${r.speed},"headingDeg":${r.heading},"gpsValid":${r.gpsValid}}""",
            )
        }
        append("]")
    }

    fun alarmsToCsv(rows: List<AlarmEntity>): String = buildString {
        appendLine("code,label,raised_at,raised_iso,cleared_at,cleared_iso,active")
        for (r in rows) {
            val clearedIso = r.clearedAt?.let { iso.format(Date(it)) } ?: ""
            appendLine(
                "${r.code},${r.label},${r.raisedAt},${iso.format(Date(r.raisedAt))}," +
                    "${r.clearedAt ?: ""},$clearedIso,${r.isActive}",
            )
        }
    }

    /** Ecrit un fichier dans le cache et retourne un Intent de partage. */
    fun shareIntent(context: Context, fileName: String, content: String, mime: String): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun timestampedName(prefix: String, ext: String): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_$ts.$ext"
    }
}
