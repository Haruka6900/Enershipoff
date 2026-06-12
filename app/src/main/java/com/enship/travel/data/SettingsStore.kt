package com.enship.travel.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "enership_settings")

/**
 * Persistance locale des reglages utilisateur et des derniers seuils connus.
 *
 * Les seuils stockes ici sont le reflet local de la configuration de l'Arduino
 * (synchronisee via `$GET_CONF` / `$SET_SEUIL_*`). Ils permettent a l'app de
 * pre-remplir l'ecran de configuration et de colorer les jauges hors-ligne.
 */
class SettingsStore(private val context: Context) {

    val lastDeviceAddress: Flow<String?> =
        context.dataStore.data.map { it[KEY_LAST_ADDRESS] }

    val lastDeviceName: Flow<String?> =
        context.dataStore.data.map { it[KEY_LAST_NAME] }

    val tempMax: Flow<Float> =
        context.dataStore.data.map { it[KEY_TEMP_MAX] ?: DEFAULT_TEMP_MAX }

    val pressionMin: Flow<Float> =
        context.dataStore.data.map { it[KEY_PRESSION_MIN] ?: DEFAULT_PRESSION_MIN }

    val carbuMin: Flow<Float> =
        context.dataStore.data.map { it[KEY_CARBU_MIN] ?: DEFAULT_CARBU_MIN }

    suspend fun setLastDevice(name: String, address: String) {
        context.dataStore.edit {
            it[KEY_LAST_NAME] = name
            it[KEY_LAST_ADDRESS] = address
        }
    }

    suspend fun setTempMax(value: Float) {
        context.dataStore.edit { it[KEY_TEMP_MAX] = value }
    }

    suspend fun setPressionMin(value: Float) {
        context.dataStore.edit { it[KEY_PRESSION_MIN] = value }
    }

    suspend fun setCarbuMin(value: Float) {
        context.dataStore.edit { it[KEY_CARBU_MIN] = value }
    }

    companion object {
        const val DEFAULT_TEMP_MAX = 95.0f
        const val DEFAULT_PRESSION_MIN = 1.5f
        const val DEFAULT_PRESSION_MAX = 5.0f
        const val DEFAULT_CARBU_MIN = 10.0f

        private val KEY_LAST_ADDRESS = stringPreferencesKey("last_address")
        private val KEY_LAST_NAME = stringPreferencesKey("last_name")
        private val KEY_TEMP_MAX = floatPreferencesKey("temp_max")
        private val KEY_PRESSION_MIN = floatPreferencesKey("pression_min")
        private val KEY_CARBU_MIN = floatPreferencesKey("carbu_min")
    }
}
