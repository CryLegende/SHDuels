package org.Cry_Legende.sHDuels.network;

import org.Cry_Legende.sHNetwork.api.NetPlayer;
import org.Cry_Legende.sHNetwork.api.SHNetworkAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Optional;

/** Pont vers SHNetwork. Cette classe n'est chargée que si SHNetwork est installé. */
public final class ShNetworkDuels implements DuelNetwork {

    private final SHNetworkAPI api;

    private ShNetworkDuels() {
        this.api = SHNetworkAPI.get();
    }

    /** Point d'entrée : le plugin ne résout cette classe qu'à cet appel, donc jamais sans SHNetwork. */
    public static DuelNetwork open() {
        return new ShNetworkDuels();
    }

    /**
     * L'arène est dans un monde que ce serveur ne fait que copier quand SHNetwork le liste dans
     * {@code mondes-distants} : toute arrivée dans ce monde renvoie le joueur sur le serveur qui l'héberge.
     */
    @Override
    public String hostOf(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        Plugin shnetwork = Bukkit.getPluginManager().getPlugin("SHNetwork");
        if (shnetwork == null) {
            return null;
        }
        ConfigurationSection remote = shnetwork.getConfig().getConfigurationSection("mondes-distants");
        if (remote == null) {
            return null;
        }
        for (String world : remote.getKeys(false)) {
            if (!world.toLowerCase(Locale.ROOT).equals(worldName.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String server = remote.getString(world, "");
            if (!server.isBlank() && !server.equalsIgnoreCase(api.serverName())) {
                return server;
            }
        }
        return null;
    }

    @Override
    public String serverOf(String playerName) {
        Optional<NetPlayer> found = api.find(playerName);
        if (found.isEmpty() || found.get().server().equalsIgnoreCase(api.serverName())) {
            return null;
        }
        return found.get().server();
    }
}
