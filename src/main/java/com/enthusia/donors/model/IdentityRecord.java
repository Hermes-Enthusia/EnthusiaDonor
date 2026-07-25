package com.enthusia.donors.model;

import java.util.UUID;

public record IdentityRecord(
        String username,
        UUID serverUuid,
        boolean isBedrock,
        String floodgateXuid,
        long lastSeenAt
) {}
