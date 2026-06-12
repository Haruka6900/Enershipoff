package com.enship.travel.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Evenement d'alarme historise.
 *
 * Le firmware reemet une trame `$ALARM` tant que la condition persiste : on
 * ne cree donc une nouvelle entree que lorsqu'une alarme passe a l'etat actif
 * (front montant). [clearedAt] est renseigne lorsque l'alarme disparait.
 */
@Entity(tableName = "alarm_log")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Code brut (P_LOW, T_HIGH...). */
    val code: String,
    /** Libelle lisible. */
    val label: String,
    /** Apparition (epoch ms). */
    val raisedAt: Long,
    /** Disparition (epoch ms) ou null si encore active. */
    val clearedAt: Long? = null,
) {
    val isActive: Boolean get() = clearedAt == null
}
