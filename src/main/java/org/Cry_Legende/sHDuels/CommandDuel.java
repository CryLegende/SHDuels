package org.Cry_Legende.sHDuels;

import org.Cry_Legende.sHDuels.network.DuelNetwork;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CommandDuel implements CommandExecutor {

    private final SHDuels plugin;
    private final DuelManager duelManager;
    private final Map<UUID, UUID> duelInvitations; // Clé = Joueur invitant, Valeur = Joueur invité

    public CommandDuel(SHDuels plugin, DuelManager duelManager) {
        this.plugin = plugin;
        this.duelManager = duelManager; // Utilisez l'instance passée
        this.duelInvitations = new HashMap<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessages().getString("command-player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(plugin.getMessages().getString("duel-usage"));
            return true;
        }

        if (args[0].equalsIgnoreCase("admin")) {
            handleAdminCommands(player, args);
        } else {
            handleDuelCommands(player, args);
        }

        return true;
    }

    /** Forme historique /duel admin <sous-commande> : mêmes droits et même effet que /shduels. */
    private void handleAdminCommands(Player player, String[] args) {
        plugin.admin().run(player, java.util.Arrays.copyOfRange(args, 1, args.length));
    }

    private void handleDuelCommands(Player player, String[] args) {
        DuelNetwork network = plugin.network();
        if (network != null) {
            // L'arène est dans un monde que ce serveur ne fait que copier : le duel se joue sur le serveur qui l'héberge.
            // arena.pos1 est une position enregistrée (world_key) : le nom du monde se lit sur la position chargée
            Location arena = plugin.getConfig().getLocation("arena.pos1");
            String arenaHost = network.hostOf(arena != null && arena.getWorld() != null ? arena.getWorld().getName() : null);
            if (arenaHost != null) {
                player.sendMessage(plugin.getMessages().getString("duel-arena-elsewhere", "").replace("{server}", arenaHost));
                return;
            }
        }

        Player targetPlayer = Bukkit.getPlayer(args[0]);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            if (network != null && network.serverOf(args[0]) != null) {
                player.sendMessage(plugin.getMessages().getString("duel-other-server", "").replace("{player}", args[0]));
                return;
            }
            player.sendMessage(plugin.getMessages().getString("player-not-found"));
            return;
        }

        if (targetPlayer == player) {
            player.sendMessage(plugin.getMessages().getString("duel-self"));
            return;
        }

        // Vérification si la commande est une acceptation de duel
        if (duelInvitations.containsKey(targetPlayer.getUniqueId()) && duelInvitations.get(targetPlayer.getUniqueId()).equals(player.getUniqueId())) {
            // Début du duel
            duelManager.startDuel(player, targetPlayer);
            duelInvitations.remove(targetPlayer.getUniqueId());
            return;
        }

        // Vérifier si le joueur a déjà envoyé une invitation
        if (duelInvitations.containsKey(player.getUniqueId())) {
            player.sendMessage(plugin.getMessages().getString("duel-already-sent"));
            return;
        }

        // Envoi d'une invitation de duel
        duelInvitations.put(player.getUniqueId(), targetPlayer.getUniqueId());

        player.sendMessage(plugin.getMessages().getString("duel-sent").replace("{player}", targetPlayer.getName()));
        String jsonString = plugin.getMessages().getString("duel-received").replace("{player}", player.getName());
        BaseComponent[] components = parseJsonMessage(jsonString);
        targetPlayer.spigot().sendMessage(components);

        // Expiration de l'invitation de duel après un délai défini
        new BukkitRunnable() {
            @Override
            public void run() {
                if (duelInvitations.containsKey(player.getUniqueId()) && duelInvitations.get(player.getUniqueId()).equals(targetPlayer.getUniqueId())) {
                    duelInvitations.remove(player.getUniqueId());
                    player.sendMessage(plugin.getMessages().getString("duel-expired"));
                }
            }
        }.runTaskLater(plugin, plugin.getConfig().getLong("duel-invitation-expiry") * 20L);
    }


    private BaseComponent[] parseJsonMessage(String jsonString) {
        return ComponentSerializer.parse(jsonString);
    }
}
