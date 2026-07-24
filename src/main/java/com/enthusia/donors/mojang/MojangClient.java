package com.enthusia.donors.mojang;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Resolves real Mojang UUIDs from usernames with optional point-in-time
 * lookups. Results are cached permanently — name history never changes.
 */
public final class MojangClient {
    private final Map<String, UUID> timedCache = new ConcurrentHashMap<>();
    private final Map<String, UUID> currentCache = new ConcurrentHashMap<>();

    private final HttpClient httpClient;
    private final Logger logger;

    public MojangClient(Logger logger) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Resolve the Mojang UUID for a player name at a specific point in time.
     */
    public Optional<UUID> resolveAtTime(String username, Instant time) throws IOException, InterruptedException {
        return resolveAtTime(username, time.getEpochSecond());
    }

    /**
     * Resolve the Mojang UUID for a player name at a specific epoch second.
     */
    public Optional<UUID> resolveAtTime(String username, long epochSecond) throws IOException, InterruptedException {
        String key = username.toLowerCase() + "@" + epochSecond;
        UUID cached = timedCache.get(key);
        if (cached != null) return Optional.of(cached);

        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        URI uri = URI.create(
                "https://api.mojang.com/users/profiles/minecraft/" + encoded + "?at=" + epochSecond);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 204 || response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new IOException("Mojang API returned HTTP " + response.statusCode()
                    + " for name='" + username + "' at=" + epochSecond);
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        String id = root.get("id").getAsString();
        UUID uuid = parseUndashed(id);
        timedCache.put(key, uuid);
        return Optional.of(uuid);
    }

    /**
     * Resolve the current Mojang UUID for a player name (fallback).
     */
    public Optional<UUID> resolveCurrent(String username) throws IOException, InterruptedException {
        String key = username.toLowerCase();
        UUID cached = currentCache.get(key);
        if (cached != null) return Optional.of(cached);

        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        URI uri = URI.create("https://api.mojang.com/users/profiles/minecraft/" + encoded);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 204 || response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new IOException("Mojang API returned HTTP " + response.statusCode()
                    + " for name='" + username + "'");
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        String id = root.get("id").getAsString();
        UUID uuid = parseUndashed(id);
        currentCache.put(key, uuid);
        return Optional.of(uuid);
    }

    public static UUID parseUndashed(String undashed) {
        String dashed = undashed.substring(0, 8) + "-"
                + undashed.substring(8, 12) + "-"
                + undashed.substring(12, 16) + "-"
                + undashed.substring(16, 20) + "-"
                + undashed.substring(20);
        return UUID.fromString(dashed);
    }

    /**
     * Floodgate assigns Bedrock players Java UUIDs with the prefix
     * 00000000-0000-0000-0009-xxxxxxxxxxxx. These are valid and should
     * not be overridden by Mojang resolution (which would return 204).
     */
    public static boolean isFloodgateUuid(UUID uuid) {
        return uuid.toString().startsWith("00000000-0000-0000-0009-");
    }
}
