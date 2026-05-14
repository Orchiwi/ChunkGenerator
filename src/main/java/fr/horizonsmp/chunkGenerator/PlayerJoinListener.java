package fr.horizonsmp.chunkGenerator;

import fr.horizonsmp.chunkGenerator.monitoring.StatsDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerJoinListener implements Listener {

    private final StatsDisplay statsDisplay;

    public PlayerJoinListener(StatsDisplay statsDisplay) {
        this.statsDisplay = statsDisplay;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        statsDisplay.onPlayerJoin(event.getPlayer());
    }
}
