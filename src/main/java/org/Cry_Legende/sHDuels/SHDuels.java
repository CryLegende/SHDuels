package org.Cry_Legende.sHDuels;

import org.Cry_Legende.sHDuels.network.DuelNetwork;
import org.Cry_Legende.sHDuels.network.ShNetworkDuels;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
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

    private volatile FileConfiguration messages;
    private FileConfiguration tempData;
    private File tempDataFile;
    private DuelManager duelManager;
    private DuelNetwork network;
    private AdminCommand admin;
    // Valeur de reseau.actif au démarrage : le mode réseau ne se change pas à chaud
    private boolean networkActiveAtStart;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        networkActiveAtStart = getConfig().getBoolean("reseau.actif", true);
        enableNetwork();
        reloadMessages(); // Charge et traduit les messages
        // temp_data.yml (et config.yml) sont lus à la première utilisation : les mondes chargés après onEnable (PhantomWorlds)
        // sont alors connus, sinon leurs positions seraient lues nulles puis effacées à la prochaine écriture.

        duelManager = new DuelManager(this);
        // Enregistre DuelManager UNE SEULE FOIS
        getServer().getPluginManager().registerEvents(duelManager, this);

        getCommand("duel").setExecutor(new CommandDuel(this, duelManager));
        admin = new AdminCommand(this, duelManager);
        PluginCommand adminCommand = getCommand("shduels");
        if (adminCommand != null) {
            adminCommand.setExecutor(admin);
            adminCommand.setTabCompleter(admin);
        }
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

    /** Commande d'administration, aussi appelée par la forme historique /duel admin. */
    AdminCommand admin() {
        return admin;
    }

    /** Vrai quand reseau.actif n'a plus la valeur du démarrage : il faut redémarrer pour qu'elle compte. */
    boolean networkSettingChanged() {
        return getConfig().getBoolean("reseau.actif", true) != networkActiveAtStart;
    }

    /**
     * Relit config.yml et messages.yml. Renvoie null si tout est lu, sinon « fichier : erreur » : l'ancienne
     * configuration reste alors en mémoire. temp_data.yml (données) n'est pas relu. Les positions de l'arène sont
     * des données écrites par les commandes : une position qui ne se relit plus (monde non chargé) garde sa valeur en mémoire.
     */
    String reloadAll() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        }
        try {
            new YamlConfiguration().load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            return "config.yml : " + describe(e);
        }
        FileConfiguration loadedMessages;
        try {
            loadedMessages = readMessages(true);
        } catch (IOException | InvalidConfigurationException e) {
            return "messages.yml : " + describe(e);
        }
        Location pos1 = getConfig().getLocation("arena.pos1");
        Location pos2 = getConfig().getLocation("arena.pos2");
        reloadConfig();
        if (pos1 != null && getConfig().getLocation("arena.pos1") == null) {
            getConfig().set("arena.pos1", pos1);
        }
        if (pos2 != null && getConfig().getLocation("arena.pos2") == null) {
            getConfig().set("arena.pos2", pos2);
        }
        messages = loadedMessages;
        return null;
    }

    private static String describe(Exception e) {
        String text = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        text = text.replaceAll("\\s*\\R\\s*", " ").trim();
        return text.length() > 240 ? text.substring(0, 240) + "..." : text;
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
        try {
            messages = readMessages(false);
        } catch (IOException | InvalidConfigurationException e) {
            // pas atteint : la lecture souple ne lève rien
            getLogger().log(Level.SEVERE, "messages.yml illisible : " + e.getMessage());
        }
    }

    /**
     * Lit messages.yml (strict : une erreur de syntaxe est levée) avec les clés absentes complétées par celles du jar,
     * puis traduit les couleurs une fois pour toutes pour ne pas avoir à le faire à chaque get.
     */
    private FileConfiguration readMessages(boolean strict) throws IOException, InvalidConfigurationException {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        YamlConfiguration loaded;
        if (strict) {
            loaded = new YamlConfiguration();
            loaded.load(messagesFile);
        } else {
            loaded = YamlConfiguration.loadConfiguration(messagesFile);
        }
        // Un messages.yml d'une version précédente n'a pas les clés ajoutées depuis : elles prennent le texte du jar
        // (en mémoire seulement, le fichier n'est pas réécrit)
        InputStream bundled = getResource("messages.yml");
        if (bundled != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8));
            for (String key : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(key) && !loaded.contains(key, true)) {
                    loaded.set(key, defaults.get(key));
                }
            }
        }
        for (String key : loaded.getKeys(false)) { // false pour ne pas aller en profondeur
            if (loaded.isString(key)) {
                loaded.set(key, ChatColor.translateAlternateColorCodes('&', loaded.getString(key)));
            } else if (loaded.isConfigurationSection(key)) { // Pour les sections comme "countdown"
                ConfigurationSection section = loaded.getConfigurationSection(key);
                if (section != null) {
                    for (String subKey : section.getKeys(false)) {
                        if (section.isString(subKey)) {
                            section.set(subKey, ChatColor.translateAlternateColorCodes('&', section.getString(subKey)));
                        }
                    }
                }
            }
        }
        return loaded;
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