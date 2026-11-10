package org.Cry_Legende.sHDuels;

import org.Cry_Legende.sHDuels.network.DuelNetwork;
import org.Cry_Legende.sHDuels.network.ShNetworkDuels;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class SHDuels extends JavaPlugin {

    private FileConfiguration messages;
    private FileConfiguration tempData;
    private File tempDataFile;
    private DuelManager duelManager;
    private DuelNetwork network;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        enableNetwork();
        reloadMessages(); // Charge et traduit les messages
        // temp_data.yml (et config.yml) sont lus à la première utilisation : les mondes chargés après onEnable (PhantomWorlds)
        // sont alors connus, sinon leurs positions seraient lues nulles puis effacées à la prochaine écriture.

        duelManager = new DuelManager(this);
        // Enregistre DuelManager UNE SEULE FOIS
        getServer().getPluginManager().registerEvents(duelManager, this);

        getCommand("duel").setExecutor(new CommandDuel(this, duelManager));
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, duelManager), this);
        // La ligne redondante getServer().getPluginManager().registerEvents(duelManager, this); a été supprimée.

        getLogger().info("SHDuels a été activé !");
    }

    /**
     * Passe en mode réseau si SHNetwork est installé et 'reseau.actif' est vrai. Les classes qui touchent à SHNetwork
     * ne sont résolues qu'ici : sans lui, rien n'est chargé et les duels gardent leur fonctionnement d'origine.
     */
    private void enableNetwork() {
        if (!getConfig().getBoolean("reseau.actif", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("SHNetwork")) return;
        try {
            network = ShNetworkDuels.open();
            getLogger().info("SHNetwork détecté : les duels ne se jouent que sur le serveur qui héberge l'arène.");
        } catch (RuntimeException | LinkageError e) {
            network = null;
            getLogger().log(Level.SEVERE, "SHNetwork présent mais inutilisable, duels locaux à ce serveur : " + e);
        }
    }

    /** Pont vers SHNetwork, ou null quand le mode réseau est dormant. */
    public DuelNetwork network() {
        return network;
    }

    @Override
    public void onDisable() {
        // Logique pour terminer proprement les duels en cours si nécessaire
        if (duelManager != null) {
            duelManager.forceEndAllDuels(); // Ajoute cette méthode à DuelManager
        }
        // Ni saveConfig() ni saveTempData() ici : chaque modification est déjà écrite sur le moment, et réécrire à l'arrêt
        // effacerait les positions dont le monde n'est pas chargé.
        getLogger().info("SHDuels a été désactivé !");
    }

    public FileConfiguration getMessages() {
        if (messages == null) {
            reloadMessages();
        }
        return messages;
    }

    // Pas besoin de cette méthode statique si tu traduis à la volée ou au chargement
    // public static String translateColors(String message) {
    // return ChatColor.translateAlternateColorCodes('&', message);
    // }

    public void reloadMessages() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        // Un messages.yml d'une version précédente n'a pas les clés ajoutées depuis : elles prennent le texte du jar
        // (en mémoire seulement, le fichier n'est pas réécrit)
        InputStream bundled = getResource("messages.yml");
        if (bundled != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8));
            for (String key : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(key) && !messages.contains(key, true)) {
                    messages.set(key, defaults.get(key));
                }
            }
        }
        // Traduire les couleurs pour tous les messages une fois chargés
        // pour ne pas avoir à le faire à chaque get.
        for (String key : messages.getKeys(false)) { // false pour ne pas aller en profondeur
            if (messages.isString(key)) {
                messages.set(key, ChatColor.translateAlternateColorCodes('&', messages.getString(key)));
            } else if (messages.isConfigurationSection(key)) { // Pour les sections comme "countdown"
                ConfigurationSection section = messages.getConfigurationSection(key);
                if (section != null) {
                    for (String subKey : section.getKeys(false)) {
                        if (section.isString(subKey)) {
                            section.set(subKey, ChatColor.translateAlternateColorCodes('&', section.getString(subKey)));
                        }
                    }
                }
            }
        }
    }

    // loadMessages() n'est plus nécessaire si reloadMessages fait le travail.

    public FileConfiguration getTempData() {
        if (tempData == null) {
            reloadTempData();
        }
        return tempData;
    }

    public void reloadTempData() {
        if (tempDataFile == null) {
            tempDataFile = new File(getDataFolder(), "temp_data.yml");
        }
        if (!tempDataFile.exists()) {
            try {
                tempDataFile.createNewFile();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not create temp_data.yml", e);
            }
        }
        tempData = YamlConfiguration.loadConfiguration(tempDataFile);
    }

    public void saveTempData() {
        if (tempData == null || tempDataFile == null) {
            return;
        }
        try {
            getTempData().save(tempDataFile);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Could not save temp_data.yml to " + tempDataFile, ex);
        }
    }
}