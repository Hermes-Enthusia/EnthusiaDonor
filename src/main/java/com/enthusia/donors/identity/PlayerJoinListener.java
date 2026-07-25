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
        // Floodgate UUIDs have variant bits 0x0009 at bits 48-63 of the least
        // significant half (clock_seq_and_variant field): 00000000-0000-0000-0009-xxxxxxxxxxxx
        return (uuid.getLeastSignificantBits() >> 48) == 0x0009L;
    }
}
