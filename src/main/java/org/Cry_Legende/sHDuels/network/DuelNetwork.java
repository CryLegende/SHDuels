package org.Cry_Legende.sHDuels.network;

/**
 * Ce dont les duels ont besoin du réseau. Seule {@link ShNetworkDuels} connaît SHNetwork : sans lui, aucune
 * classe de ce paquet n'est chargée.
 */
public interface DuelNetwork {

    /**
     * Serveur qui héberge le monde, ou null s'il est hébergé ici (ou inconnu du réseau).
     */
    String hostOf(String worldName);

    /**
     * Serveur d'un joueur connecté sur un autre serveur du réseau, ou null s'il est ici ou hors ligne.
     */
    String serverOf(String playerName);
}
