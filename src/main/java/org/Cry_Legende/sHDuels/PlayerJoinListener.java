package org.Cry_Legende.sHDuels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;

import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final SHDuels plugin;
    // private final DuelManager duelManager; // Pas directement nécessaire si on restaure juste la position

    public PlayerJoinListener(SHDuels plugin, DuelManager duelManager) {
        this.plugin = plugin;
        // this.duelManager = duelManager; // Non utilisé directement ici après refactor
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String playerDataPath = playerUUID.toString();

        // Vérifier si les données du joueur sont stockées dans temp_data.yml
        if (plugin.getTempData().isConfigurationSection(playerDataPath)) {
            ConfigurationSection playerDataSection = plugin.getTempData().getConfigurationSection(playerDataPath);
            if (playerDataSection != null && playerDataSection.isLocation("originalLocation")) {
                Location originalLocation = playerDataSection.getLocation("originalLocation");

                if (originalLocation != null) {
                    plugin.getLogger().info("Données de duel trouvées pour " + player.getName() + " à la reconnexion. Restauration en cours...");
                    // Différer la téléportation pour s'assurer que le joueur est pleinement chargé
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) { // Double-vérifier que le joueur est toujours en ligne
                            player.teleport(originalLocation);
                            // Restaurer d'autres états si nécessaire (vie, faim, effets)
                            player.setHealth(player.getMaxHealth());
                            player.setFoodLevel(20);
                            player.setFireTicks(0);
                            for (PotionEffect effect : player.getActivePotionEffects()) {
                                player.removePotionEffect(effect.getType()); // Nettoyer tous les effets
                            }
                            player.setWalkSpeed(0.2f); // Vitesse normale
                            player.setInvulnerable(false); // S'assurer qu'il n'est pas invulnérable

                            plugin.getLogger().info(player.getName() + " restauré à sa position d'avant-duel.");
                        }
                        // Supprimer les données du joueur de temp_data.yml après restauration
                        plugin.getTempData().set(playerDataPath, null);
                        plugin.saveTempData();
                    }, 20L); // Délai de 1 seconde (20 ticks)
                } else {
                    // Si originalLocation est null pour une raison quelconque, nettoyer les données
                    plugin.getTempData().set(playerDataPath, null);
                    plugin.saveTempData();
                }
            }
        }
    }
    // parseLocation n'est plus nécessaire car on utilise la sérialisation de Location de Bukkit
}