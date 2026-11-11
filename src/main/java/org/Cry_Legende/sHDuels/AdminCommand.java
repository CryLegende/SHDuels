package org.Cry_Legende.sHDuels;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * /shduels pos1 | pos2 | end | reload | status. La forme historique /duel admin &lt;sous-commande&gt; renvoie ici.
 * Console autorisée pour end, reload et status ; pos1 et pos2 prennent la position du joueur.
 */
final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_POS1 = "shduels.admin.pos1";
    private static final String PERMISSION_POS2 = "shduels.admin.pos2";
    private static final String PERMISSION_END = "shduels.admin.end";
    private static final String PERMISSION_RELOAD = "shduels.admin.reload";
    private static final String PERMISSION_STATUS = "shduels.admin.status";

    private final SHDuels plugin;
    private final DuelManager duelManager;

    AdminCommand(SHDuels plugin, DuelManager duelManager) {
        this.plugin = plugin;
        this.duelManager = duelManager;
    }

    /** Sous-commandes que l'expéditeur a le droit d'utiliser. */
    private List<String> allowed(CommandSender sender) {
        List<String> subs = new ArrayList<>();
        if (sender.hasPermission(PERMISSION_POS1)) subs.add("pos1");
        if (sender.hasPermission(PERMISSION_POS2)) subs.add("pos2");
        if (sender.hasPermission(PERMISSION_END)) subs.add("end");
        if (sender.hasPermission(PERMISSION_RELOAD)) subs.add("reload");
        if (sender.hasPermission(PERMISSION_STATUS)) subs.add("status");
        return subs;
    }

    private String text(String key) {
        return plugin.getMessages().getString(key, key);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        run(sender, args);
        return true;
    }

    /** Exécute une sous-commande d'administration ; args commence à la sous-commande. */
    void run(CommandSender sender, String[] args) {
        List<String> subs = allowed(sender);
        if (subs.isEmpty()) {
            sender.sendMessage(text("no-permission"));
            return;
        }
        String sub = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (!subs.contains(sub)) {
            sender.sendMessage(text("usage-liste").replace("{sous-commandes}", String.join("|", subs)));
            return;
        }
        switch (sub) {
            case "pos1" -> setPosition(sender, "arena.pos1", "pos1-set");
            case "pos2" -> setPosition(sender, "arena.pos2", "pos2-set");
            case "end" -> {
                if (duelManager.isIdle()) {
                    sender.sendMessage(text("end-aucun"));
                } else {
                    // Mettre fin en avance à un duel en cours
                    duelManager.endDuel(null, null);
                    sender.sendMessage(text("duel-ended"));
                }
            }
            case "reload" -> reload(sender);
            case "status" -> status(sender);
            default -> sender.sendMessage(text("usage-liste").replace("{sous-commandes}", String.join("|", subs)));
        }
    }

    private void setPosition(CommandSender sender, String path, String messageKey) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(text("command-player-only"));
            return;
        }
        plugin.getConfig().set(path, player.getLocation());
        plugin.saveConfig();
        player.sendMessage(text(messageKey));
    }

    private void reload(CommandSender sender) {
        String error = plugin.reloadAll();
        if (error == null) {
            sender.sendMessage(text("reload-ok"));
            if (plugin.networkSettingChanged()) {
                sender.sendMessage(text("reload-reseau"));
            }
        } else {
            String[] parts = error.split(" : ", 2);
            sender.sendMessage(text("reload-erreur").replace("{fichier}", parts[0]).replace("{erreur}", parts.length > 1 ? parts[1] : ""));
        }
    }

    private void status(CommandSender sender) {
        sender.sendMessage(text("status-titre"));
        sender.sendMessage(text("status-etat").replace("{etat}", text("etat-" + duelManager.stateKey())));
        List<Player> duelists = duelManager.duelists();
        if (!duelists.isEmpty()) {
            sender.sendMessage(text("status-joueurs").replace("{joueurs}",
                duelists.stream().map(Player::getName).collect(Collectors.joining(", "))));
        }
        sender.sendMessage(text("status-arene").replace("{point}", "pos1").replace("{position}", describe(plugin.getConfig().getLocation("arena.pos1"))));
        sender.sendMessage(text("status-arene").replace("{point}", "pos2").replace("{position}", describe(plugin.getConfig().getLocation("arena.pos2"))));
    }

    private String describe(Location location) {
        if (location == null || location.getWorld() == null) {
            return text("arene-invalide");
        }
        return location.getWorld().getName() + " " + Math.round(location.getX()) + " " + Math.round(location.getY()) + " " + Math.round(location.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String start = args[0].toLowerCase(Locale.ROOT);
            for (String sub : allowed(sender)) {
                if (sub.startsWith(start)) {
                    result.add(sub);
                }
            }
        }
        return result;
    }
}
