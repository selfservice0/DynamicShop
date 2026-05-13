package org.minecraftsmp.dynamicshop.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
// [Folia/Paper API] Commented out old BukkitRunnable import
// import org.bukkit.scheduler.BukkitRunnable;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Periodically restocks all items in a configured category to a target stock level.
 *
 * Config format (config.yml):
 * <pre>
 * restock:
 * enabled: true
 * rules:
 * - category: FOOD
 * stock: 200
 * interval-minutes: 30
 * </pre>
 */
public class RestockManager {

    private record RestockRule(ItemCategory category, double targetStock, long intervalTicks) {}

    private final DynamicShop plugin;
    // [Folia/Paper API] Replaced BukkitRunnable with ScheduledTask
    // private final List<BukkitRunnable> timers = new ArrayList<>();
    private final List<ScheduledTask> timers = new ArrayList<>();

    public RestockManager(DynamicShop plugin) {
        this.plugin = plugin;
    }

    public void init() {
        shutdown(); // cancel any existing timers

        boolean enabled = plugin.getConfig().getBoolean("restock.enabled", false);
        if (!enabled) return;

        ConfigurationSection restockSec = plugin.getConfig().getConfigurationSection("restock");
        if (restockSec == null) return;

        List<?> rulesList = restockSec.getList("rules");
        if (rulesList == null || rulesList.isEmpty()) return;

        // Parse rules from the list of maps
        for (Object obj : rulesList) {
            if (!(obj instanceof java.util.Map<?, ?> map)) continue;

            String categoryName = String.valueOf(map.get("category"));
            ItemCategory category;
            try {
                category = ItemCategory.valueOf(categoryName.toUpperCase());
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().warning("[DynamicShop] Restock: unknown category '" + categoryName + "', skipping.");
                continue;
            }

            double targetStock = map.containsKey("stock") ? ((Number) map.get("stock")).doubleValue() : 100.0;
            int intervalMinutes = map.containsKey("interval-minutes") ? ((Number) map.get("interval-minutes")).intValue() : 60;

            // Guard: enforce minimum 1-minute interval to prevent runaway timers
            if (intervalMinutes < 1) {
                Bukkit.getLogger().warning("[DynamicShop] Restock: interval-minutes for " + categoryName
                        + " was " + intervalMinutes + ", clamping to 1 minute minimum.");
                intervalMinutes = 1;
            }

            long intervalTicks = intervalMinutes * 60L * 20L; // minutes -> ticks

            RestockRule rule = new RestockRule(category, targetStock, intervalTicks);
            scheduleRule(rule);

            Bukkit.getLogger().info("[DynamicShop] Restock: " + category.getDisplayName()
                    + " → " + (int) targetStock + " stock every " + intervalMinutes + " min"
                    + " (" + intervalTicks + " ticks)");
        }
    }

    private void scheduleRule(RestockRule rule) {
        // [Folia/Paper API] Replaced BukkitRunnable with GlobalRegionScheduler for Folia compatibility
        // BukkitRunnable task = new BukkitRunnable() {
        //     @Override
        //     public void run() {
        //         List<Material> items = ShopDataManager.getItemsInCategory(rule.category());
        //         int count = 0;
        //         for (Material mat : items) {
        //             ShopDataManager.setStockDirect(mat, rule.targetStock());
        //             count++;
        //         }
        //         Bukkit.getLogger().info("[DynamicShop] Restocked " + count + " items in "
        //                 + rule.category().getDisplayName() + " to " + (int) rule.targetStock());
        //     }
        // };
        // task.runTaskTimer(plugin, rule.intervalTicks(), rule.intervalTicks());
        // timers.add(task);

        ScheduledTask task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> {
            List<Material> items = ShopDataManager.getItemsInCategory(rule.category());
            int count = 0;
            for (Material mat : items) {
                ShopDataManager.setStockDirect(mat, rule.targetStock());
                count++;
            }
            Bukkit.getLogger().info("[DynamicShop] Restocked " + count + " items in "
                    + rule.category().getDisplayName() + " to " + (int) rule.targetStock());
        }, rule.intervalTicks(), rule.intervalTicks());
        timers.add(task);
    }

    public void shutdown() {
        // [Folia/Paper API] Replaced BukkitRunnable with ScheduledTask
        // for (BukkitRunnable timer : timers) {
        //     if (!timer.isCancelled()) {
        //         timer.cancel();
        //     }
        // }
        for (ScheduledTask timer : timers) {
            if (!timer.isCancelled()) {
                timer.cancel();
            }
        }
        timers.clear();
    }

    public void reload() {
        init(); // shutdown + re-init
    }

    // ────────────────────────────────────────────────────────────────
    // CONFIG HELPERS (for Admin GUI)
    // ────────────────────────────────────────────────────────────────

    /** Check if restocking is globally enabled. */
    public boolean isRestockEnabled() {
        return plugin.getConfig().getBoolean("restock.enabled", false);
    }

    /** Toggle global restock on/off, save, and reload timers. */
    public void setRestockEnabled(boolean enabled) {
        plugin.getConfig().set("restock.enabled", enabled);
        plugin.saveConfig();
        reload();
    }

    /** Get the restock rule for a specific category, or null if none exists. */
    public int[] getRuleForCategory(ItemCategory category) {
        List<?> rulesList = plugin.getConfig().getList("restock.rules");
        if (rulesList == null) return null;

        for (Object obj : rulesList) {
            if (!(obj instanceof java.util.Map<?, ?> map)) continue;
            String catName = String.valueOf(map.get("category"));
            if (catName.equalsIgnoreCase(category.name())) {
                int stock = map.containsKey("stock") ? ((Number) map.get("stock")).intValue() : 100;
                int interval = map.containsKey("interval-minutes") ? ((Number) map.get("interval-minutes")).intValue() : 60;
                return new int[]{stock, interval};
            }
        }
        return null;
    }

    /** Set or update the restock rule for a category. Saves config and reloads timers. */
    public void setRuleForCategory(ItemCategory category, int stock, int intervalMinutes) {
        List<java.util.Map<String, Object>> rulesList = getRulesList();

        // Remove existing rule for this category if present
        rulesList.removeIf(map -> String.valueOf(map.get("category")).equalsIgnoreCase(category.name()));

        // Add new rule
        java.util.Map<String, Object> newRule = new java.util.LinkedHashMap<>();
        newRule.put("category", category.name());
        newRule.put("stock", stock);
        newRule.put("interval-minutes", intervalMinutes);
        rulesList.add(newRule);

        plugin.getConfig().set("restock.rules", rulesList);
        plugin.saveConfig();
        reload();
    }

    /** Remove the restock rule for a category. Saves config and reloads timers. */
    public void removeRuleForCategory(ItemCategory category) {
        List<java.util.Map<String, Object>> rulesList = getRulesList();
        rulesList.removeIf(map -> String.valueOf(map.get("category")).equalsIgnoreCase(category.name()));
        plugin.getConfig().set("restock.rules", rulesList);
        plugin.saveConfig();
        reload();
    }

    @SuppressWarnings("unchecked")
    private List<java.util.Map<String, Object>> getRulesList() {
        List<?> raw = plugin.getConfig().getList("restock.rules");
        List<java.util.Map<String, Object>> result = new ArrayList<>();
        if (raw != null) {
            for (Object obj : raw) {
                if (obj instanceof java.util.Map<?, ?> map) {
                    java.util.Map<String, Object> copy = new java.util.LinkedHashMap<>();
                    map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    result.add(copy);
                }
            }
        }
        return result;
    }
}