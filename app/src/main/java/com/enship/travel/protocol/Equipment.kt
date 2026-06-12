package com.enship.travel.protocol

/**
 * Catalogue des 8 equipements electriques surveilles.
 * L'ordre correspond exactement aux canaux C0..C7 / V0..V7 du firmware Arduino
 * (voir tableau `nomEquipement` dans le firmware).
 */
enum class Equipment(val index: Int, val label: String) {
    BATTERIE_1(0, "Batterie 1"),
    BATTERIE_2(1, "Batterie 2"),
    BATTERIE_3(2, "Batterie 3"),
    PROPULSEUR(3, "Propulseur"),
    GUINDEAU(4, "Guindeau"),
    VENTILATEUR(5, "Ventilateur"),
    ALTERNATEUR_140A(6, "Alternateur 140A"),
    ALTERNATEUR_80A(7, "Alternateur 80A");

    companion object {
        val ordered: List<Equipment> = entries.sortedBy { it.index }
    }
}
