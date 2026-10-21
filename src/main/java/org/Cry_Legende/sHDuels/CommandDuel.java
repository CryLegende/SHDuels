package org.Cry_Legende.sHDuels;

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

    private void handleAdminCommands(Player player, String[] args) {
        if (!player.hasPermission("shduels.admin")) {
            player.sendMessage(plugin.getMessages().getString("no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getMessages().getString("admin-usage"));
            return;
        }

        if (args[1].equalsIgnoreCase("pos1")) {
            setArenaPosition(player.getLocation(), "arena.pos1");
            player.sendMessage(plugin.getMessages().getString("pos1-set"));
        } else if (args[1].equalsIgnoreCase("pos2")) {
            setArenaPosition(player.getLocation(), "arena.pos2");
            player.sendMessage(plugin.getMessages().getString("pos2-set"));
        } else if (args[1].equalsIgnoreCase("end")) {
            // Mettre fin en avance à un duel en cours
            duelManager.endDuel(null, null); // À définir comment gérer la fin forcée
            player.sendMessage(plugin.getMessages().getString("duel-ended"));
        } else {
            player.sendMessage(plugin.getMessages().getString("admin-usage"));
        }
    }

    private void handleDuelCommands(Player player, String[] args) {
        Player targetPlayer = Bukkit.getPlayer(args[0]);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
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

    private void setArenaPosition(Location location, String path) {
        plugin.getConfig().set(path, location);
        plugin.saveConfig();
    }
}
