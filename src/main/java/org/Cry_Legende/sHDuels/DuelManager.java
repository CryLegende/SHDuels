package org.Cry_Legende.sHDuels;

import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DuelManager implements Listener {

    private final SHDuels plugin;
    private Map<UUID, PlayerData> playerDataMap; // Stocke les données des joueurs avant le duel
    private DuelState duelState;
    private Player player1; // Participant 1
    private Player player2; // Participant 2
    private BukkitTask duelTimeoutTask; // Tâche pour la durée maximale du duel
    private BukkitTask actionbarTask; // Tâche pour l'actionbar

    public DuelManager(SHDuels plugin) {
        this.plugin = plugin;
        this.playerDataMap = new HashMap<>();
        this.duelState = DuelState.NONE;
    }

    public void startDuel(Player p1, Player p2) {
        if (duelState != DuelState.NONE) {
            String alreadyInProgressMsg = plugin.getMessages().getString("duel-already-in-progress");
            if (p1 != null && p1.isOnline()) p1.sendMessage(alreadyInProgressMsg);
            if (p2 != null && p2.isOnline()) p2.sendMessage(alreadyInProgressMsg);
            return;
        }

        Location arenaPos1 = plugin.getConfig().getLocation("arena.pos1");
        Location arenaPos2 = plugin.getConfig().getLocation("arena.pos2");

        if (arenaPos1 == null || arenaPos1.getWorld() == null) {
            plugin.getLogger().severe("Position 1 de l'arène non configurée ou son monde n'est pas chargé. Impossible de démarrer le duel.");
            String errorMsg = plugin.getMessages().getString("error.arena-pos1-invalid", ChatColor.RED + "L'arène de duel (pos1) n'est pas correctement configurée. Contactez un administrateur.");
            if (p1 != null && p1.isOnline()) p1.sendMessage(errorMsg);
            if (p2 != null && p2.isOnline()) p2.sendMessage(errorMsg);
            return;
        }
        if (arenaPos2 == null || arenaPos2.getWorld() == null) {
            plugin.getLogger().severe("Position 2 de l'arène non configurée ou son monde n'est pas chargé. Impossible de démarrer le duel.");
            String errorMsg = plugin.getMessages().getString("error.arena-pos2-invalid", ChatColor.RED + "L'arène de duel (pos2) n'est pas correctement configurée. Contactez un administrateur.");
            if (p1 != null && p1.isOnline()) p1.sendMessage(errorMsg);
            if (p2 != null && p2.isOnline()) p2.sendMessage(errorMsg);
            return;
        }

        player1 = p1;
        player2 = p2;

        savePlayerData(player1);
        savePlayerData(player2);

        preparePlayerForDuel(player1);
        preparePlayerForDuel(player2);

        duelState = DuelState.PREPARATION;
        teleportToArena(player1, player2);
        prepareCountdown();

        plugin.getLogger().info("[Duel] Duel démarré entre " + p1.getName() + " et " + p2.getName() + ". État: PREPARATION.");
    }

    private void preparePlayerForDuel(Player player) {
        if (player == null || !player.isOnline()) return;
        player.setFoodLevel(20);
        player.setHealth(player.getMaxHealth());
        player.setFireTicks(0);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        setInvincibility(player, true);
        setMovementAndJump(player, 0f, false);
    }

    private void setMovementAndJump(Player player, float walkSpeed, boolean canJump) {
        if (player == null || !player.isOnline()) return;
        player.setWalkSpeed(walkSpeed);
        if (!canJump) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 128, false, false, false), true);
        } else {
            player.removePotionEffect(PotionEffectType.JUMP);
        }
    }

    public void setInvincibility(Player player, boolean invincible) {
        if (player != null && player.isOnline()) {
            player.setInvulnerable(invincible);
        }
    }

    private void savePlayerData(Player player) {
        if (player == null || !player.isOnline()) return;
        playerDataMap.put(player.getUniqueId(), new PlayerData(player.getLocation().clone()));
        plugin.getTempData().set(player.getUniqueId().toString() + ".originalLocation", player.getLocation().clone());
        plugin.saveTempData();
    }

    private void clearTempPlayerData(UUID playerUUID) {
        if (playerUUID == null) return;
        String path = playerUUID.toString();
        if (plugin.getTempData().contains(path)) {
            plugin.getTempData().set(path, null);
            plugin.saveTempData();
        }
    }

    private void teleportToArena(Player p1, Player p2) {
        Location pos1 = plugin.getConfig().getLocation("arena.pos1");
        Location pos2 = plugin.getConfig().getLocation("arena.pos2");

        plugin.getLogger().info("[Duel] Téléportation de " + (p1 != null ? p1.getName() : "N/A") + " vers pos1 (" + (pos1 != null && pos1.getWorld() != null ? pos1.getWorld().getName() : "Monde Invalide") + ") et "
            + (p2 != null ? p2.getName() : "N/A") + " vers pos2 (" + (pos2 != null && pos2.getWorld() != null ? pos2.getWorld().getName() : "Monde Invalide") + ").");

        if (p1 != null && p1.isOnline() && pos1 != null) p1.teleport(pos1);
        if (p2 != null && p2.isOnline() && pos2 != null) p2.teleport(pos2);
    }

    private void startActionbarTask() {
        if (actionbarTask != null) {
            actionbarTask.cancel();
        }
        actionbarTask = new BukkitRunnable() {
            int timeLeft = plugin.getConfig().getInt("duel-duration-seconds", 300);

            @Override
            public void run() {
                if (duelState != DuelState.DUELING || player1 == null || !player1.isOnline() || player2 == null || !player2.isOnline()) {
                    this.cancel();
                    actionbarTask = null;
                    return;
                }

                String actionbarMessage = plugin.getMessages().getString("duel-actionbar-message", "&eTemps restant: &f{time} &esecondes")
                    .replace("{time}", String.valueOf(timeLeft));

                if (player1.isOnline())
                    player1.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbarMessage));
                if (player2.isOnline())
                    player2.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbarMessage));

                if (--timeLeft < 0) {
                    plugin.getLogger().info("[Duel] Temps écoulé pour le duel.");
                    if (player1.isOnline())
                        player1.sendTitle("", plugin.getMessages().getString("time-out"), 10, 20, 10);
                    if (player2.isOnline())
                        player2.sendTitle("", plugin.getMessages().getString("time-out"), 10, 20, 10);
                    this.cancel();
                    actionbarTask = null;
                    endDuel(null, null);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player damagedPlayer = (Player) event.getEntity();
        boolean isPlayer1 = damagedPlayer.equals(player1);
        boolean isPlayer2 = damagedPlayer.equals(player2);

        if (isPlayer1 || isPlayer2) {
            if (duelState == DuelState.PREPARATION || duelState == DuelState.END) {
                event.setCancelled(true);
                return;
            }

            if (duelState == DuelState.DUELING) {
                if (damagedPlayer.isInvulnerable()) {
                    event.setCancelled(true);
                    return;
                }

                double newHealth = damagedPlayer.getHealth() - event.getFinalDamage();
                if (newHealth <= 0) {
                    event.setCancelled(true);
                    Player winner = isPlayer1 ? player2 : player1;
                    Player loser = damagedPlayer;
                    plugin.getLogger().info("[Duel] " + loser.getName() + " est censé mourir. Gagnant: " + (winner != null ? winner.getName() : "N/A"));
                    endDuel(winner, loser);
                }
            }
        }
    }

    private void prepareCountdown() {
        new BukkitRunnable() {
            int countdown = plugin.getConfig().getInt("countdown-duration", 5);

            @Override
            public void run() {
                if (player1 == null || !player1.isOnline() || player2 == null || !player2.isOnline() || duelState != DuelState.PREPARATION) {
                    plugin.getLogger().warning("[Duel] Countdown interrompu (joueur déconnecté ou état incorrect: " + duelState + ").");
                    if (duelState == DuelState.PREPARATION) {
                        endDuel(null, null);
                    }
                    this.cancel();
                    return;
                }

                if (countdown == 0) {
                    duelState = DuelState.DUELING;
                    plugin.getLogger().info("[Duel] Countdown terminé. État: DUELING.");
                    String duelStartMsg = plugin.getMessages().getString("duel-start");
                    if (player1.isOnline()) player1.sendMessage(duelStartMsg);
                    if (player2.isOnline()) player2.sendMessage(duelStartMsg);

                    setInvincibility(player1, false);
                    setInvincibility(player2, false);

                    setMovementAndJump(player1, 0.2f, true);
                    setMovementAndJump(player2, 0.2f, true);

                    try {
                        String soundName = plugin.getConfig().getString("start-sound", "BLOCK_NOTE_BLOCK_PLING");
                        Sound startSound = Sound.valueOf(soundName.toUpperCase());
                        if (player1.isOnline())
                            player1.playSound(player1.getLocation(), startSound, SoundCategory.MASTER, 1.0f, 1.0f);
                        if (player2.isOnline())
                            player2.playSound(player2.getLocation(), startSound, SoundCategory.MASTER, 1.0f, 1.0f);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Son de démarrage du duel invalide dans la config: " + plugin.getConfig().getString("start-sound"));
                    }

                    startActionbarTask();

                    if (duelTimeoutTask != null) duelTimeoutTask.cancel();
                    duelTimeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (duelState == DuelState.DUELING) {
                            plugin.getLogger().info("[Duel] Duel timeout reached (programmé).");
                            if (player1 != null && player1.isOnline())
                                player1.sendTitle("", plugin.getMessages().getString("time-out"), 10, 20, 10);
                            if (player2 != null && player2.isOnline())
                                player2.sendTitle("", plugin.getMessages().getString("time-out"), 10, 20, 10);
                            endDuel(null, null);
                        }
                    }, plugin.getConfig().getLong("duel-duration-seconds", 300) * 20L);

                    this.cancel();
                    return;
                } else if (countdown <= 3) {
                    String countdownMessageKey = "countdown." + countdown;
                    String countdownMessage = plugin.getMessages().getString(countdownMessageKey, "&e" + countdown);

                    if (player1.isOnline()) player1.sendTitle("", countdownMessage, 5, 10, 5);
                    if (player2.isOnline()) player2.sendTitle("", countdownMessage, 5, 10, 5);

                    try {
                        String soundName = plugin.getConfig().getString("countdown-sound", "BLOCK_NOTE_BLOCK_PLING");
                        Sound countdownSound = Sound.valueOf(soundName.toUpperCase());
                        if (player1.isOnline())
                            player1.playSound(player1.getLocation(), countdownSound, SoundCategory.MASTER, 0.8f, 1.0f + (0.2f * (3 - countdown)));
                        if (player2.isOnline())
                            player2.playSound(player2.getLocation(), countdownSound, SoundCategory.MASTER, 0.8f, 1.0f + (0.2f * (3 - countdown)));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Son de countdown du duel invalide dans la config: " + plugin.getConfig().getString("countdown-sound"));
                    }
                }
                countdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if ((player.equals(player1) || player.equals(player2)) && duelState != DuelState.NONE) {

            String message = event.getMessage();
            if (!message.startsWith("/")) return;

            String commandBase = message.substring(1).split(" ")[0].toLowerCase();
            List<String> allowedCommands = plugin.getConfig().getStringList("allowed-commands");
            boolean commandIsAllowed = false;
            for (String allowedCmdConfig : allowedCommands) {
                String cleanedAllowedCmd = (allowedCmdConfig.startsWith("/") ? allowedCmdConfig.substring(1) : allowedCmdConfig).toLowerCase();
                if (commandBase.equals(cleanedAllowedCmd)) {
                    commandIsAllowed = true;
                    break;
                }
            }

            if (!commandIsAllowed) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessages().getString("command-not-allowed-in-duel", ChatColor.RED + "Vous ne pouvez pas utiliser cette commande pendant un duel."));
            }
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        boolean isParticipant = player.equals(player1) || player.equals(player2);
        boolean isActiveDuelState = duelState == DuelState.PREPARATION || duelState == DuelState.DUELING;

        if (!isParticipant || !isActiveDuelState) {
            return;
        }

        plugin.getLogger().info("[Duel] PlayerChangedWorldEvent: " + player.getName() + ", État: " + duelState + ", De: " + event.getFrom().getName() + ", Vers: " + player.getWorld().getName());

        Location arenaPos1Location = plugin.getConfig().getLocation("arena.pos1");
        World arenaWorld = null;
        if (arenaPos1Location != null && arenaPos1Location.getWorld() != null) {
            arenaWorld = arenaPos1Location.getWorld();
        } else {
            plugin.getLogger().severe("[Duel] Impossible de déterminer le monde de l'arène (arena.pos1 non configurée ou monde non chargé) lors de PlayerChangedWorldEvent pour " + player.getName());
            if (duelState == DuelState.DUELING) {
                Player otherPlayer = player.equals(player1) ? player2 : player1;
                String changeWorldMsg = plugin.getMessages().getString("duel-world-change");
                if (player1 != null && player1.isOnline()) player1.sendMessage(changeWorldMsg);
                if (player2 != null && player2.isOnline()) player2.sendMessage(changeWorldMsg);
                endDuel(otherPlayer, player);
            }
            return;
        }

        String changeWorldMsg = plugin.getMessages().getString("duel-world-change");
        Player otherPlayer = player.equals(player1) ? player2 : player1;

        if (duelState == DuelState.PREPARATION) {
            if (player.getWorld().equals(arenaWorld)) {
                plugin.getLogger().info("[Duel] Joueur " + player.getName() + " téléporté dans le monde de l'arène (" + arenaWorld.getName() + ") pendant la PREPARATION. OK.");
            } else {
                plugin.getLogger().warning("[Duel] Joueur " + player.getName() + " a changé vers un monde INATTENDU (" + player.getWorld().getName() + ") pendant la PREPARATION. Le monde de l'arène est " + arenaWorld.getName() + ". Annulation du duel.");
                if (player1 != null && player1.isOnline()) player1.sendMessage(changeWorldMsg);
                if (player2 != null && player2.isOnline()) player2.sendMessage(changeWorldMsg);
                endDuel(otherPlayer, player);
            }
        } else if (duelState == DuelState.DUELING) {
            plugin.getLogger().info("[Duel] Joueur " + player.getName() + " a changé de monde (" + player.getWorld().getName() + ") pendant la phase DUELING. Annulation du duel.");
            if (player1 != null && player1.isOnline()) player1.sendMessage(changeWorldMsg);
            if (player2 != null && player2.isOnline()) player2.sendMessage(changeWorldMsg);
            endDuel(otherPlayer, player);
        }
    }

    public void endDuel(Player winner, Player loser) {
        if (duelState == DuelState.NONE) {
            return;
        }
        DuelState previousState = duelState;
        duelState = DuelState.END;
        plugin.getLogger().info("[Duel] endDuel appelé. Précédent état: " + previousState + ". Gagnant: " + (winner != null ? winner.getName() : "Aucun") + ", Perdant: " + (loser != null ? loser.getName() : "Aucun"));

        if (duelTimeoutTask != null) {
            duelTimeoutTask.cancel();
            duelTimeoutTask = null;
        }
        if (actionbarTask != null) {
            actionbarTask.cancel();
            actionbarTask = null;
        }

        if (winner != null && winner.isOnline()) {
            winner.sendTitle("", plugin.getMessages().getString("you-won"), 10, 40, 10);
            setInvincibility(winner, true);
            setMovementAndJump(winner, 0f, false);

            try {
                Firework firework = winner.getWorld().spawn(winner.getLocation().add(0, 1, 0), Firework.class);
                FireworkMeta meta = firework.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.GREEN, Color.LIME)
                    .withFade(Color.WHITE)
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .trail(true)
                    .build());
                meta.setPower(0);
                firework.setFireworkMeta(meta);
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la création du feu d'artifice: " + e.getMessage());
            }
        }

        if (loser != null && loser.isOnline()) {
            loser.sendTitle("", plugin.getMessages().getString("you-lost"), 10, 40, 10);
            setInvincibility(loser, true);
            setMovementAndJump(loser, 0f, false);

            try {
                String soundName = plugin.getConfig().getString("lose-sound", "ENTITY_ZOMBIE_BREAK_WOODEN_DOOR");
                Sound loseSound = Sound.valueOf(soundName.toUpperCase());
                loser.playSound(loser.getLocation(), loseSound, SoundCategory.MASTER, 1.0f, 0.8f);
                loser.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0, false, false, false));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Son de défaite invalide dans la config: " + plugin.getConfig().getString("lose-sound"));
            }
        }

        if (winner != null && loser != null && winner.isOnline() && loser.isOnline()) {
            String broadcastMessage = plugin.getMessages().getString("duel-win-broadcast")
                .replace("{winner}", winner.getName())
                .replace("{loser}", loser.getName());

            Location arenaLocationForBroadcast = winner.getLocation();
            double broadcastRadius = plugin.getConfig().getDouble("duel-win-broadcast-radius", 50.0);
            int sentMessages = 0;

            for (Player onlinePlayer : arenaLocationForBroadcast.getWorld().getPlayers()) {
                if (onlinePlayer.getLocation().distanceSquared(arenaLocationForBroadcast) <= broadcastRadius * broadcastRadius) {
                    onlinePlayer.sendMessage(broadcastMessage);
                    sentMessages++;
                }
            }
            plugin.getLogger().info("[Duel] Annonce de victoire locale envoyée à " + sentMessages + " joueur(s) autour de " + winner.getName() + " (rayon: " + broadcastRadius + ").");

        } else if (winner != null && winner.isOnline() && (loser == null || !loser.isOnline())) {
            String forfeitMessage = plugin.getMessages().getString("duel-win-forfeit-broadcast", "&8&l« &e&lSH &8&l» &e{winner} &7a gagné son duel par forfait!")
                .replace("{winner}", winner.getName())
                .replace("{loser}", (loser != null ? loser.getName() : "adversaire déconnecté"));
            Bukkit.broadcastMessage(forfeitMessage); // Forfait reste global pour l'instant
            plugin.getLogger().info("[Duel] Annonce de victoire par forfait (globale). Message: " + forfeitMessage);
        }

        long teleportDelayTicks = plugin.getConfig().getLong("duel-end-teleport-delay-ticks", 60L);
        new BukkitRunnable() {
            @Override
            public void run() {
                Player localP1 = player1;
                Player localP2 = player2;

                if (localP1 != null && localP1.isOnline()) {
                    restorePlayerData(localP1);
                    setInvincibility(localP1, false);
                    setMovementAndJump(localP1, 0.2f, true);
                }
                if (localP2 != null && localP2.isOnline()) {
                    restorePlayerData(localP2);
                    setInvincibility(localP2, false);
                    setMovementAndJump(localP2, 0.2f, true);
                }

                if ((localP1 != null && localP1.isOnline()) || (localP2 != null && localP2.isOnline())) {
                    plugin.getLogger().info("[Duel] Joueurs restaurés après le duel.");
                }

                if (DuelManager.this.player1 == localP1) DuelManager.this.player1 = null;
                if (DuelManager.this.player2 == localP2) DuelManager.this.player2 = null;

                playerDataMap.clear();

                if (duelState == DuelState.END && DuelManager.this.player1 == null && DuelManager.this.player2 == null) {
                    duelState = DuelState.NONE;
                    plugin.getLogger().info("[Duel] État du duel réinitialisé à NONE.");
                } else {
                    plugin.getLogger().info("[Duel] État du duel non réinitialisé à NONE car un autre duel pourrait être en cours ou les joueurs ne correspondent pas (p1: " + DuelManager.this.player1 + ", p2: " + DuelManager.this.player2 + "). État actuel: " + duelState);
                }
            }
        }.runTaskLater(plugin, teleportDelayTicks);
    }

    public void restorePlayerData(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerUUID = player.getUniqueId();
        PlayerData data = playerDataMap.get(playerUUID);
        Location restoreLocation = null;

        if (data != null && data.getOriginalLocation() != null) {
            restoreLocation = data.getOriginalLocation();
        } else {
            Location locFromTemp = plugin.getTempData().getLocation(playerUUID.toString() + ".originalLocation");
            if (locFromTemp != null) {
                restoreLocation = locFromTemp;
                plugin.getLogger().info("[Duel] Position restaurée depuis temp_data.yml pour " + player.getName());
            }
        }

        if (restoreLocation != null) {
            player.teleport(restoreLocation);
        } else {
            World mainWorld = Bukkit.getWorlds().get(0);
            if (mainWorld != null) {
                player.teleport(mainWorld.getSpawnLocation());
                plugin.getLogger().warning("[Duel] Impossible de restaurer la position de " + player.getName() + ", téléporté au spawn du monde principal.");
            } else {
                plugin.getLogger().severe("[Duel] IMPOSSIBLE de restaurer " + player.getName() + ", aucun monde principal trouvé pour le spawn.");
            }
        }

        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setFireTicks(0);
        if (player.hasPotionEffect(PotionEffectType.JUMP)) {
            PotionEffect jumpEffect = player.getPotionEffect(PotionEffectType.JUMP);
            if (jumpEffect != null && jumpEffect.getAmplifier() == 128) {
                player.removePotionEffect(PotionEffectType.JUMP);
            }
        }
        if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
            PotionEffect blindEffect = player.getPotionEffect(PotionEffectType.BLINDNESS);
            if (blindEffect != null && blindEffect.getDuration() <= 80 * 20 + 20) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
            }
        }
        player.setWalkSpeed(0.2f);

        // Les données temporaires ne doivent plus impacter les reconnexions
        // après une restauration effective (fin de duel, arrêt plugin, etc.).
        clearTempPlayerData(playerUUID);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player disconnectedPlayer = event.getPlayer();

        if (duelState != DuelState.NONE && (disconnectedPlayer.equals(player1) || disconnectedPlayer.equals(player2))) {
            plugin.getLogger().info("[Duel] Joueur " + disconnectedPlayer.getName() + " s'est déconnecté pendant un duel (état: " + duelState + ").");

            Player winner = null;
            if (disconnectedPlayer.equals(player1) && player2 != null && player2.isOnline()) {
                winner = player2;
            } else if (disconnectedPlayer.equals(player2) && player1 != null && player1.isOnline()) {
                winner = player1;
            }

            Player actualLoser = disconnectedPlayer;

            if (winner != null) {
                endDuel(winner, actualLoser);
            } else { // Si l'autre joueur aussi s'est déconnecté (très rare) ou si le duel est dans un état où on ne peut pas déterminer de gagnant.
                endDuel(null, null);
            }

            if (disconnectedPlayer.equals(player1)) {
                player1 = null;
            } else if (disconnectedPlayer.equals(player2)) {
                player2 = null;
            }
        }
    }

    public void forceEndAllDuels() {
        if (duelState != DuelState.NONE) {
            plugin.getLogger().info("[Duel] Forcing end of active duel (état: " + duelState + ") due to plugin disable.");

            Player p1Ref = player1;
            Player p2Ref = player2;

            if (p1Ref != null && p1Ref.isOnline()) {
                p1Ref.sendMessage(ChatColor.YELLOW + "Le duel a été interrompu par un redémarrage/arrêt du serveur.");
                restorePlayerData(p1Ref);
                setInvincibility(p1Ref, false);
                setMovementAndJump(p1Ref, 0.2f, true);
            }
            if (p2Ref != null && p2Ref.isOnline()) {
                p2Ref.sendMessage(ChatColor.YELLOW + "Le duel a été interrompu par un redémarrage/arrêt du serveur.");
                restorePlayerData(p2Ref);
                setInvincibility(p2Ref, false);
                setMovementAndJump(p2Ref, 0.2f, true);
            }

            if (duelTimeoutTask != null) duelTimeoutTask.cancel();
            if (actionbarTask != null) actionbarTask.cancel();

            player1 = null;
            player2 = null;
            playerDataMap.clear();
            duelState = DuelState.NONE;
            plugin.getLogger().info("[Duel] Duel forcé terminé et ressources nettoyées.");
        }
    }


    private enum DuelState {
        NONE,
        PREPARATION,
        DUELING,
        END
    }

    private static class PlayerData {
        private final Location originalLocation;

        public PlayerData(Location originalLocation) {
            this.originalLocation = originalLocation;
        }

        public Location getOriginalLocation() {
            return originalLocation;
        }
    }
}
