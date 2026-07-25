package com.enthusia.donors.identity;

import com.enthusia.donors.storage.DonorRepository;
import org.bukkit.Bukkit;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Resolves a Tebex username to a server UUID using:
 * 1. Manual payment links (admin overrides)
 * 2. Identities table (players who joined the server)
 * 3. Bukkit OfflinePlayer lookup as last resort
 */
public final class IdentityResolver {
    private final DonorRepository repository;
    private final Logger logger;

    public IdentityResolver(DonorRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    /**
     * Resolve a Tebex payer name to the best server UUID.
     * Returns empty if the name cannot be resolved to any known player.
     */
    public Optional<UUID> resolve(String tebexName) {
        if (tebexName == null || tebexName.isBlank()) {
            return Optional.empty();
        }

        String name = tebexName.trim();

        // 1. Manual payment link (admin override — highest priority)
        try {
            Optional<UUID> linked = repository.getLinkedUuid(name);
            if (linked.isPresent()) {
                return linked;
            }
        } catch (Exception ex) {
            logger.warning("Failed to check payment link for '" + name + "': " + ex.getMessage());
        }

        // 2. Identities table (players who have joined the server)
        try {
            Optional<UUID> identity = repository.resolveIdentity(name);
            if (identity.isPresent()) {
                // Auto-link for future lookups
                try {
                    repository.upsertPaymentLink(name, identity.get(), false);
                } catch (Exception ignored) {
                    // Best-effort auto-link
                }
                return identity;
            }
        } catch (Exception ex) {
            logger.warning("Failed to resolve identity for '" + name + "': " + ex.getMessage());
        }

        // 3. Bukkit OfflinePlayer (last resort — may return wrong UUID for cracked names)
        try {
            @SuppressWarnings("deprecation")
            var offline = Bukkit.getOfflinePlayer(name);
            if (offline.hasPlayedBefore()) {
                return Optional.of(offline.getUniqueId());
            }
        } catch (Exception ex) {
            logger.warning("Failed to lookup OfflinePlayer for '" + name + "': " + ex.getMessage());
        }

        return Optional.empty();
    }
}
