package org.minecraftsmp.dynamicshop.web;

import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraftsmp.dynamicshop.DynamicShop;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Tracks all admin changes made through the web dashboard.
 * Entries are persisted to admin-audit.yml and kept in memory for fast retrieval.
 * Maximum 500 entries are retained (oldest are pruned).
 */
public class WebAdminAuditLog {

    private static final int MAX_ENTRIES = 500;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final DynamicShop plugin;
    private final File logFile;
    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    public record AuditEntry(
            long timestamp,
            String user,
            String action,   // e.g. "item_update", "config_update", "shortage_reset"
            String target,   // e.g. "DIAMOND", "hourlyIncreasePercent", "ALL"
            String details   // e.g. "basePrice: 10.0 → 15.0, stock: 100 → 50"
    ) {}

    public WebAdminAuditLog(DynamicShop plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "admin-audit.yml");
        load();
    }

    /**
     * Log an admin action.
     */
    public void log(String user, String action, String target, String details) {
        AuditEntry entry = new AuditEntry(System.currentTimeMillis(), user, action, target, details);
        entries.add(0, entry); // newest first

        // Prune if over limit
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }

        // Save async
        // [Folia/Paper API] Replaced Bukkit Scheduler with AsyncScheduler
        // plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> this.save());

        plugin.getLogger().info("[WebAdmin Audit] " + user + " | " + action + " | " + target + " | " + details);
    }

    /**
     * Get all audit entries (newest first).
     */
    public List<AuditEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Get entries as a list of maps for JSON serialization.
     */
    public List<Map<String, Object>> getEntriesAsJson() {
        return getEntriesAsJson(100); // default limit
    }

    /**
     * Get entries as JSON with a limit.
     */
    public List<Map<String, Object>> getEntriesAsJson(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        int count = 0;
        for (AuditEntry entry : entries) {
            if (count >= limit) break;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("timestamp", entry.timestamp());
            map.put("time", FORMATTER.format(Instant.ofEpochMilli(entry.timestamp())));
            map.put("user", entry.user());
            map.put("action", entry.action());
            map.put("target", entry.target());
            map.put("details", entry.details());
            result.add(map);
            count++;
        }
        return result;
    }

    /**
     * Load entries from file.
     */
    private void load() {
        if (!logFile.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(logFile);
            List<Map<?, ?>> list = config.getMapList("entries");
            for (Map<?, ?> map : list) {
                try {
                    long ts = ((Number) map.get("timestamp")).longValue();
                    String user = (String) map.get("user");
                    String action = (String) map.get("action");
                    String target = (String) map.get("target");
                    String details = (String) map.get("details");
                    entries.add(new AuditEntry(ts, user, action, target, details));
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load admin-audit.yml", e);
        }
    }

    /**
     * Save entries to file.
     */
    private synchronized void save() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            List<Map<String, Object>> list = new ArrayList<>();
            for (AuditEntry entry : entries) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("timestamp", entry.timestamp());
                map.put("user", entry.user());
                map.put("action", entry.action());
                map.put("target", entry.target());
                map.put("details", entry.details());
                list.add(map);
            }
            config.set("entries", list);
            config.save(logFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save admin-audit.yml", e);
        }
    }
}