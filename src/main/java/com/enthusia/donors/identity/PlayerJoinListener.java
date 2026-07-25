package com.enthusia.donors.identity;

import com.enthusia.donors.storage.DonorRepository;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;
import java.util.logging.Logger;

public final class PlayerJoinListener implements Listener {
    private final DonorRepository repository;
    private final Logger logger;

    public PlayerJoinListener(DonorRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        String username = player.getName();
        UUID uuid = player.getUniqueId();
        boolean isBedrock = isFloodgateUuid(uuid);
        String xuid = null; // populated later via Floodgate API if needed

        try {
            repository.upsertIdentity(username, uuid, isBedrock, xuid);
        } catch (Exception ex) {
            logger.warning("Failed to record identity for " + username + ": " + ex.getMessage());
        }
    }

    static boolean isFloodgateUuid(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        return (msb & 0xFFFFFFFF00000000L) == 0x0000000000000000L
                && ((msb >> 32) & 0xFFFFFFFFL) == 0x00000009L;
    }
}
