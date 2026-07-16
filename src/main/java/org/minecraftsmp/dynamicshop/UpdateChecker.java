package org.minecraftsmp.dynamicshop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;

/**
 * Async GitHub release checker.
 * On startup: checks the latest release tag from the GitHub API.
 * On OP join: notifies if the server is running an outdated version.
 */
public class UpdateChecker implements Listener {

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/selfservice0/DynamicShop/releases/latest";

    private final DynamicShop plugin;
    private String latestVersion = null;
    private boolean updateAvailable = false;

    public UpdateChecker(DynamicShop plugin) {
        this.plugin = plugin;
    }

    /**
     * Kick off an async check against the GitHub releases API.
     */
    public void check() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = URI.create(GITHUB_API_URL).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "DynamicShop-UpdateChecker");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    plugin.getLogger().info("[UpdateChecker] Could not check for updates (HTTP " + responseCode + ")");
                    return;
                }

                // Read response
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                // Parse tag_name from JSON (simple extraction to avoid extra dependencies)
                String json = response.toString();
                latestVersion = extractJsonValue(json, "tag_name");

                if (latestVersion == null) {
                    plugin.getLogger().info("[UpdateChecker] Could not parse latest version from GitHub.");
                    return;
                }

                // Strip leading 'v' if present (e.g., "v2.4.4" → "2.4.4")
                String cleanLatest = latestVersion.startsWith("v") ? latestVersion.substring(1) : latestVersion;
                String currentVersion = plugin.getDescription().getVersion();

                if (isNewerVersion(cleanLatest, currentVersion)) {
                    updateAvailable = true;
                    plugin.getLogger().info("§e[UpdateChecker] A new version is available: §a" + latestVersion
                            + " §e(you are running §c" + currentVersion + "§e)");
                    plugin.getLogger().info("§e[UpdateChecker] Download: https://github.com/selfservice0/DynamicShop/releases/latest");
                } else {
                    plugin.getLogger().info("[UpdateChecker] You are running the latest version (" + currentVersion + ").");
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[UpdateChecker] Failed to check for updates: " + e.getMessage());
            }
        });
    }

    /**
     * Notify OP players on join if an update is available.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!updateAvailable || latestVersion == null) return;

        Player player = event.getPlayer();
        if (!player.isOp()) return;

        // Delay the message so it doesn't get buried in join spam
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                String currentVersion = plugin.getDescription().getVersion();
                player.sendMessage("§e[DynamicShop] §fA new version is available: §a" + latestVersion
                        + " §f(you are running §c" + currentVersion + "§f)");
                player.sendMessage("§e[DynamicShop] §fDownload: §bhttps://github.com/selfservice0/DynamicShop/releases/latest");
            }
        }, 60L); // 3 second delay
    }

    /**
     * Simple JSON value extractor — finds "key":"value" and returns value.
     * Avoids pulling in a JSON library just for one field.
     */
    private String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIndex = json.indexOf(search);
        if (keyIndex < 0) return null;

        // Find the colon after the key
        int colonIndex = json.indexOf(':', keyIndex + search.length());
        if (colonIndex < 0) return null;

        // Find the opening quote of the value
        int startQuote = json.indexOf('"', colonIndex + 1);
        if (startQuote < 0) return null;

        // Find the closing quote of the value
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) return null;

        return json.substring(startQuote + 1, endQuote);
    }

    /**
     * Compares two version strings (e.g., "2.4.5" vs "2.4.4").
     * Returns true if 'latest' is newer than 'current'.
     */
    private boolean isNewerVersion(String latest, String current) {
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");

        int maxLength = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < maxLength; i++) {
            int latestNum = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
            int currentNum = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;

            if (latestNum > currentNum) return true;
            if (latestNum < currentNum) return false;
        }
        return false; // Versions are equal
    }

    /**
     * Parse a version part, stripping any non-numeric suffixes (e.g., "4-SNAPSHOT" → 4).
     */
    private int parseVersionPart(String part) {
        try {
            // Strip non-numeric suffixes
            StringBuilder digits = new StringBuilder();
            for (char c : part.toCharArray()) {
                if (Character.isDigit(c)) digits.append(c);
                else break;
            }
            return digits.length() > 0 ? Integer.parseInt(digits.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
