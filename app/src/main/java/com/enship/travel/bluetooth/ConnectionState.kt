package com.enship.travel.bluetooth

/**
 * Etat de la liaison Bluetooth, expose a l'UI.
 */
enum class ConnectionState {
    /** Aucune tentative en cours. */
    DISCONNECTED,
    /** Connexion initiale en cours. */
    CONNECTING,
    /** Socket RFCOMM ouvert, lecture active. */
    CONNECTED,
    /** Connexion perdue, attente avant nouvelle tentative auto. */
    RECONNECTING,
    /** Echec definitif (BT eteint, equipement absent...). */
    ERROR,
}

/**
 * Qualite de liaison derivee de la frequence/integrite des trames.
 */
enum class LinkQuality {
    EXCELLENT, // trames regulieres, peu d'erreurs
    GOOD,
    POOR,      // trames irregulieres ou taux d'erreur eleve
    STALE,     // plus de trame recente
    NONE;      // deconnecte
}

/**
 * Appareil Bluetooth jumelé presentable a l'utilisateur.
 */
data class PairedDevice(
    val name: String,
    val address: String,
)
