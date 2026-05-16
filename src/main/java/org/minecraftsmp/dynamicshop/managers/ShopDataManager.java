package org.minecraftsmp.dynamicshop.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ShopDataManager {

    // base prices (static config, from config.yml)
    // Item Configuration
    public record ShopItemConfig(
            double basePrice,
            Double maxStock, // Pricing curve
            Double minStock, // Pricing curve
            Integer maxStockStorage, // Hard limit
            Integer minStockStorage, // Hard limit
            boolean disableBuy,
            boolean disableSell,
            ItemCategory categoryOverride,
            Double stockRate) { // Per-item rate override (null = use global)
    }

    public static final Map<Material, ShopItemConfig> itemConfigs = new ConcurrentHashMap<>();

    // dynamic data
    static final Map<Material, Double> stockMap = new ConcurrentHashMap<>();
    private static final Map<Material, Double> purchasesMap = new ConcurrentHashMap<>();
    private static final Map<Material, Long> lastUpdateMap = new ConcurrentHashMap<>();
    private static final Map<Material, Double> shortageHoursMap = new ConcurrentHashMap<>();

    // cached category mapping
    private static final Map<Material, ItemCategory> categoryCache = new ConcurrentHashMap<>();

    // materials per-category (cached)
    private static final Map<ItemCategory, List<Material>> categoryItems = new EnumMap<>(ItemCategory.class);

    // Admin features: category overrides
    private static final Map<Material, ItemCategory> categoryOverrides = new ConcurrentHashMap<>();

    // Custom display names for shop items (overrides material name in GUI)
    private static final Map<Material, String> customNames = new ConcurrentHashMap<>();

    // Item templates (items with custom components like enchantments, names, lore)
    // Stored in config.yml under items.MATERIAL.template
    private static final Map<Material, ItemStack> itemTemplates = new ConcurrentHashMap<>();

    // Save queue
    // which items need to be written to YAML
    private static final Set<Material> saveQueue = ConcurrentHashMap.newKeySet();

    private static DynamicShop plugin;

    // dynamic data file
    private static File shopDataFile;
    private static YamlConfiguration shopDataConfig;

    public static ScheduledTask saveTimer;
    private static ScheduledTask shortageTicker;

    // ------------------------------------------------------------------------
    // INIT
    // ------------------------------------------------------------------------
    public static void init(DynamicShop pl) {
        plugin = pl;

        itemConfigs.clear();
        categoryCache.clear();
        categoryItems.clear();
        categoryOverrides.clear();
        customNames.clear();
        itemTemplates.clear();

        loadConfigItems();
        loadTemplates();
        buildCategoryLists();

        shopDataFile = new File(plugin.getDataFolder(), "shopdata.yml");
        shopDataConfig = YamlConfiguration.loadConfiguration(shopDataFile);
        loadDynamicData();

        if (ConfigCacheManager.crossServerEnabled) {
            if (saveTimer == null || saveTimer.isCancelled()) {
                int seconds = ConfigCacheManager.crossServerSaveInterval;
                saveTimer = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                        plugin, task -> saveQueuedItems(), seconds * 20L, seconds * 20L);
            }
        }

        // Periodic shortage tick — bakes shortage accumulation/decay every 5 minutes
        // so items at zero stock gain shortage even with no trades,
        // and items with supply lose shortage even with no trades.
        if (shortageTicker != null && !shortageTicker.isCancelled()) {
            shortageTicker.cancel();
        }
        shortageTicker = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, task -> tickAllShortage(), 72000L, 72000L); // 1 hour = 72000 ticks
    }

    public static void reload() {
        flushQueue();
        init(plugin);
    }

    /**
     * Check if the ShopDataManager was fully initialized.
     * Used to prevent NPE during early shutdown (e.g., when Vault is missing).
     */
    public static boolean isInitialized() {
        return shopDataConfig != null;
    }

    // ------------------------------------------------------------------------
    // LOAD ITEMS FROM CONFIG (base prices)
    // ------------------------------------------------------------------------
    private static void loadConfigItems() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("items");
        if (sec == null) {
            Bukkit.getLogger().warning("[DynamicShop] No 'items:' section found in config.yml!");
            return;
        }

        int loaded = 0;

        for (String key : sec.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat == null) {
                Bukkit.getLogger().warning("[DynamicShop] Invalid item in config: " + key);
                continue;
            }

            ConfigurationSection data = sec.getConfigurationSection(key);
            if (data == null)
                continue;

            double base = data.getDouble("base", -1);
            Double maxStock = data.contains("max-stock") ? data.getDouble("max-stock") : null;
            Double minStock = data.contains("min-stock") ? data.getDouble("min-stock") : null;
            Integer maxStorage = data.contains("max-stock-storage") ? data.getInt("max-stock-storage") : null;
            Integer minStorage = data.contains("min-stock-storage") ? data.getInt("min-stock-storage") : null;
            boolean disableBuy = data.getBoolean("disable-buy", false);
            boolean disableSell = data.getBoolean("disable-sell", false);
            Double stockRate = data.contains("rate") ? data.getDouble("rate") : null;

            // Load category override
            ItemCategory overrideCat = null;
            String categoryOverride = data.getString("category", null);
            if (categoryOverride != null) {
                try {
                    overrideCat = ItemCategory.valueOf(categoryOverride.toUpperCase());
                    categoryOverrides.put(mat, overrideCat);
                } catch (IllegalArgumentException ignored) {
                }
            }

            // Load custom display name
            String customName = data.getString("custom_name", null);
            if (customName != null && !customName.isEmpty()) {
                customNames.put(mat, customName);
            }

            ShopItemConfig config = new ShopItemConfig(base, maxStock, minStock, maxStorage, minStorage, disableBuy,
                    disableSell, overrideCat, stockRate);
            itemConfigs.put(mat, config);

            loaded++;
        }

        Bukkit.getLogger().info("[DynamicShop] Loaded " + loaded + " items from config.yml");
    }

    // ------------------------------------------------------------------------
    // ITEM TEMPLATES (items with custom components)
    // Stored in config.yml under items.MATERIAL.template
    // ------------------------------------------------------------------------
    private static void loadTemplates() {
        // Migrate from old item_templates.yml if it exists
        File oldTemplateFile = new File(plugin.getDataFolder(), "item_templates.yml");
        if (oldTemplateFile.exists()) {
            YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldTemplateFile);
            int migrated = 0;
            for (String key : oldConfig.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat == null) continue;
                ItemStack template = oldConfig.getItemStack(key);
                if (template != null) {
                    plugin.getConfig().set("items." + mat.name() + ".template", template);
                    migrated++;
                }
            }
            if (migrated > 0) {
                plugin.saveConfig();
                Bukkit.getLogger().info("[DynamicShop] Migrated " + migrated + " templates from item_templates.yml to config.yml");
            }
            // Rename old file so it's not loaded again
            oldTemplateFile.renameTo(new File(plugin.getDataFolder(), "item_templates.yml.old"));
        }

        // Load templates from config.yml
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("items");
        if (sec == null) return;

        int count = 0;
        for (String key : sec.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat == null) continue;
            ItemStack template = sec.getItemStack(key + ".template");
            if (template != null) {
                itemTemplates.put(mat, template);
                count++;
            }
        }

        if (count > 0) {
            Bukkit.getLogger().info("[DynamicShop] Loaded " + count + " item templates with components");
        }
    }

    /**
     * Store a template ItemStack for a material. When players buy this material,
     * they receive a clone of the template instead of a plain item.
     * The template preserves all item components (enchantments, names, lore, etc).
     */
    public static void setTemplate(Material mat, ItemStack template) {
        ItemStack clean = template.clone();
        clean.setAmount(1);
        itemTemplates.put(mat, clean);

        plugin.getConfig().set("items." + mat.name() + ".template", clean);
        plugin.saveConfig();
    }

    /**
     * Get the template ItemStack for a material, or null if no template is set.
     * Always returns a fresh clone to prevent mutation.
     */
    public static ItemStack getTemplate(Material mat) {
        ItemStack template = itemTemplates.get(mat);
        return template != null ? template.clone() : null;
    }

    /**
     * Check if a material has a stored template with custom components.
     */
    public static boolean hasTemplate(Material mat) {
        return itemTemplates.containsKey(mat);
    }

    /**
     * Remove a stored template for a material.
     */
    public static void removeTemplate(Material mat) {
        itemTemplates.remove(mat);
        plugin.getConfig().set("items." + mat.name() + ".template", null);
        plugin.saveConfig();
    }

    // ------------------------------------------------------------------------
    // CUSTOM DISPLAY NAMES
    // Stored in config.yml under items.MATERIAL.custom_name
    // ------------------------------------------------------------------------

    /**
     * Get the custom display name for a material, or null if none is set.
     */
    public static String getCustomName(Material mat) {
        return customNames.get(mat);
    }

    /**
     * Set a custom display name for a material.
     */
    public static void setCustomName(Material mat, String name) {
        if (name == null || name.isEmpty()) {
            removeCustomName(mat);
            return;
        }
        customNames.put(mat, name);
        plugin.getConfig().set("items." + mat.name() + ".custom_name", name);
        plugin.saveConfig();
    }

    /**
     * Remove a custom display name for a material.
     */
    public static void removeCustomName(Material mat) {
        customNames.remove(mat);
        plugin.getConfig().set("items." + mat.name() + ".custom_name", null);
        plugin.saveConfig();
    }

    // ------------------------------------------------------------------------
    // BASE PRICE ACCESS
    // ------------------------------------------------------------------------
    public static double getBasePrice(Material mat) {
        ShopItemConfig cfg = itemConfigs.get(mat);
        return cfg != null ? cfg.basePrice : -1.0;
    }

    // ------------------------------------------------------------------------
    // PUBLIC PRICE ACCESS
    // ------------------------------------------------------------------------

    /**
     * Public single-item buy price at current stock (before tax).
     */
    public static double getPrice(Material mat) {
        double base = getBasePrice(mat);
        if (base < 0)
            return -1.0; // untradeable
        return getTotalBuyCost(mat, 1);
    }

    /**
     * Public single-item sell price at current stock (after sell tax).
     */
    public static double getSellPrice(Material mat) {
        return getTotalSellValue(mat, 1);
    }

    /**
     * Check if an item can be purchased (respects stock restriction flag).
     */
    public static boolean canBuy(Material mat) {
        return canBuy(mat, 1);
    }

    public static boolean isBuyDisabled(Material mat) {
        ShopItemConfig cfg = itemConfigs.get(mat);
        return cfg != null && cfg.disableBuy;
    }

    public static boolean isSellDisabled(Material mat) {
        ShopItemConfig cfg = itemConfigs.get(mat);
        return cfg != null && cfg.disableSell;
    }

    public static boolean canBuy(Material mat, int amount) {
        ShopItemConfig cfg = itemConfigs.get(mat);
        if (cfg == null || cfg.basePrice < 0)
            return false;
        if (cfg.disableBuy)
            return false;

        // Check min stock storage
        double currentStock = getStock(mat);
        double minLimit = cfg.minStockStorage != null ? cfg.minStockStorage
                : (ConfigCacheManager.restrictBuyingAtZeroStock ? 0.0 : -Double.MAX_VALUE);

        return currentStock - amount >= minLimit;
    }

    public static boolean canSell(Material mat, int amount) {
        ShopItemConfig cfg = itemConfigs.get(mat);
        if (cfg == null || cfg.basePrice < 0)
            return false;
        if (cfg.disableSell)
            return false;

        // Check max stock storage
        double currentStock = getStock(mat);
        double maxLimit = cfg.maxStockStorage != null ? cfg.maxStockStorage : Double.MAX_VALUE;

        return currentStock + amount <= maxLimit;
    }

    /**
     * Get the maximum amount that can be bought given current stock limits.
     * Returns Integer.MAX_VALUE if no limit.
     */
    public static int getBuyLimit(Material mat) {
        ShopItemConfig cfg = itemConfigs.get(mat);
        if (cfg == null)
            return 0;

        double currentStock = getStock(mat);
        double minLimit = cfg.minStockStorage != null ? cfg.minStockStorage
                : (ConfigCacheManager.restrictBuyingAtZeroStock ? 0.0 : -Double.MAX_VALUE);

        if (minLimit == -Double.MAX_VALUE)
            return Integer.MAX_VALUE;

        // maxBuy = current - min
        double maxBuy = currentStock - minLimit;
        return (int) Math.max(0, maxBuy);
    }

    /**
     * Get the maximum amount that can be sold given current stock limits.
     * Returns Integer.MAX_VALUE if no limit.
     */
    public static int getSellLimit(Material mat) {
        ShopItemConfig cfg = itemConfigs.get(mat);
        if (cfg == null)
            return 0;

        double currentStock = getStock(mat);
        double maxLimit = cfg.maxStockStorage != null ? cfg.maxStockStorage : Double.MAX_VALUE;

        if (maxLimit == Double.MAX_VALUE)
            return Integer.MAX_VALUE;

        // maxSell = max - current
        double maxSell = maxLimit - currentStock;
        return (int) Math.max(0, maxSell);
    }

    /**
     * Core dynamic pricing formula.
     *
     * Uses definite integrals for O(1) computation.
     * Clamping is baked INTO the integral (not applied after), so
     * bulk pricing is mathematically identical to 1-at-a-time.
     */
    // ============================================================================
    // TOTAL BUY COST (continuous pricing with clamped integration)
    // ∫ clamp(P(s), MIN, MAX) ds from s = s0 - amount → s0
    // ============================================================================
    public static double getTotalBuyCost(Material mat, double amount) {
        ShopItemConfig cfg = itemConfigs.get(mat);
        if (cfg == null || cfg.basePrice < 0)
            return -1.0;

        double B = cfg.basePrice;

        if (!ConfigCacheManager.dynamicPricingEnabled) {
            return B * amount;
        }

        double s0 = getStock(mat);
        double L = cfg.maxStock != null ? cfg.maxStock : ConfigCacheManager.maxStock;
        double minStock = cfg.minStock != null ? cfg.minStock : 0.0;
        double k = ConfigCacheManager.curveStrength;
        double negPercent = (cfg.stockRate != null ? cfg.stockRate : ConfigCacheManager.negativeStockPercentPerItem) / 100.0;
        double q = 1.0 + negPercent;
        double hourlyIncrease = ConfigCacheManager.hourlyIncreasePercent / 100.0;
        double h = getHoursInShortage(mat);
        double t = Math.pow(1.0 + hourlyIncrease, h);

        double a = s0 - amount;
        double b = s0;

        return computeClampedIntegral(B, a, b, L, minStock, k, q, t);
    }

    // ============================================================================
    // TOTAL SELL VALUE (continuous pricing with clamped integration + tax)
    // ∫ clamp(P(s), MIN, MAX) ds from s = s0 → s0 + amount, then apply tax
    // ============================================================================
    public static double getTotalSellValue(Material mat, int amount) {
        ShopItemConfig cfg = itemConfigs.get(mat);
        if (cfg == null || cfg.basePrice < 0)
            return -1.0;

        double B = cfg.basePrice;
        double tax = ConfigCacheManager.sellTaxPercent;

        if (!ConfigCacheManager.dynamicPricingEnabled) {
            return B * amount * (1.0 - tax);
        }

        double s0 = getStock(mat);
        double L = cfg.maxStock != null ? cfg.maxStock : ConfigCacheManager.maxStock;
        double minStock = cfg.minStock != null ? cfg.minStock : 0.0;
        double k = ConfigCacheManager.curveStrength;
        double negPercent = (cfg.stockRate != null ? cfg.stockRate : ConfigCacheManager.negativeStockPercentPerItem) / 100.0;
        double q = 1.0 + negPercent;
        double hourlyIncrease = ConfigCacheManager.hourlyIncreasePercent / 100.0;
        double h = getHoursInShortage(mat);
        double t = Math.pow(1.0 + hourlyIncrease, h);

        double a = s0;
        double b = s0 + amount;

        double total = computeClampedIntegral(B, a, b, L, minStock, k, q, t);

        return Math.max(0.0, total * (1.0 - tax));
    }

    // ============================================================================
    // CLAMPED INTEGRAL: ∫ clamp(P(s), minPrice, maxPrice) ds
    // Clamping is built into each region's integral so bulk = 1-at-a-time.
    // ============================================================================
    private static double computeClampedIntegral(double B, double a, double b,
            double L, double minStock, double k, double q, double t) {
        double total = 0.0;
        double lowerBound = minStock;
        double upperBound = L;
        double maxPrice = B * ConfigCacheManager.maxPriceMultiplier;
        double minPrice = B * ConfigCacheManager.minPriceMultiplier;

        // NEGATIVE REGION (-∞ → lowerBound]
        double negA = Math.min(a, lowerBound);
        double negB = Math.min(b, lowerBound);
        if (negA < negB) {
            total += integrateNegativeRegionClamped(B, q, t, negA - lowerBound, negB - lowerBound, maxPrice);
        }

        // MID REGION [lowerBound → upperBound]
        // P(s') = B*t*(1 - 0.5*k*s'/L), linear decreasing from B*t to B*t*(1-0.5k)
        double effectiveMax = upperBound - lowerBound;
        if (effectiveMax <= 0)
            effectiveMax = 1.0;

        double midA = Math.max(a, lowerBound);
        double midB = Math.min(b, upperBound);
        if (midA < midB) {
            total += integrateMidRegionClamped(B, k, t, effectiveMax,
                    midA - lowerBound, midB - lowerBound, maxPrice, minPrice);
        }

        // HIGH REGION [upperBound → +∞) — flat price, clamp directly
        double highA = Math.max(a, upperBound);
        double highB = Math.max(b, upperBound);
        if (highA < highB) {
            double highPrice = Math.max(minPrice, Math.min(B * t * (1.0 - k), maxPrice));
            total += highPrice * (highB - highA);
        }

        return total;
    }

    // ============================================================================
    // REGION INTEGRALS
    // ============================================================================

    /**
     * Negative region: integrates min(B*t*q^(-s), maxPrice) from a to b.
     * Finds the threshold where exponential price exceeds maxPrice and splits
     * the integral into a flat-clamped portion + normal exponential portion.
     * This makes bulk identical to 1-at-a-time (no post-hoc clamping needed).
     */
    private static double integrateNegativeRegionClamped(double B, double q, double t, double a, double b, double maxPrice) {
        // Price function: P(s) = B * t * q^(-s), increasing as s decreases
        // Find threshold s_thresh where P(s) = maxPrice:
        //   B * t * q^(-s) = maxPrice  =>  s = -ln(maxPrice/(B*t)) / ln(q)

        // Guard: if q == 1 (negativeStockPercent == 0), log(q) == 0 => flat price in negative region
        double logQ = Math.log(q);
        if (Math.abs(logQ) < 1e-12) {
            // Flat price at B*t, clamped to maxPrice
            double flatPrice = Math.min(B * t, maxPrice);
            return flatPrice * (b - a);
        }

        double sThresh;
        if (B * t >= maxPrice) {
            sThresh = 0.0; // entire negative region exceeds max
        } else {
            sThresh = -Math.log(maxPrice / (B * t)) / logQ;
        }

        double total = 0.0;

        // Clamped portion: [a, min(b, sThresh)] at flat maxPrice
        double clampEnd = Math.min(b, sThresh);
        if (a < clampEnd) {
            total += maxPrice * (clampEnd - a);
        }

        // Normal (unclamped) portion: [max(a, sThresh), b]
        double normalStart = Math.max(a, sThresh);
        if (normalStart < b) {
            total += B * t * (Math.pow(q, -normalStart) - Math.pow(q, -b)) / logQ;
        }

        return total;
    }

    /**
     * Mid region: integrates clamp(B*t*(1 - 0.5*k*s'/L), minPrice, maxPrice) from a to b.
     * Price is linear decreasing: highest at s'=0 (P=B*t), lowest at s'=L (P=B*t*(1-0.5k)).
     * Finds thresholds where price crosses max/min clamps and splits accordingly.
     */
    private static double integrateMidRegionClamped(double B, double k, double t, double L,
            double a, double b, double maxPrice, double minPrice) {
        // P(s') = B*t*(1 - 0.5*k*s'/L)
        // P decreases as s' increases
        // MAX threshold: P(s') = maxPrice => s' = 2*L*(1 - maxPrice/(B*t)) / k
        // MIN threshold: P(s') = minPrice => s' = 2*L*(1 - minPrice/(B*t)) / k

        // Guard: if k == 0 (no curve), price is flat at B*t throughout the mid region
        if (Math.abs(k) < 1e-12) {
            double flatPrice = Math.max(minPrice, Math.min(B * t, maxPrice));
            return flatPrice * (b - a);
        }

        double total = 0.0;

        // Find where price crosses maxPrice (above this, clamp to max)
        double sMax = (t > 0 && B * t > maxPrice) ? 2.0 * L * (1.0 - maxPrice / (B * t)) / k : -1.0;
        // Find where price crosses minPrice (below this, clamp to min)
        double sMin = (t > 0 && B * t * (1.0 - 0.5 * k) < minPrice) ? 2.0 * L * (1.0 - minPrice / (B * t)) / k : L + 1.0;

        // Clamp thresholds to valid range
        sMax = Math.max(sMax, 0);
        sMin = Math.min(sMin, L);

        // Region splits: [a, b] may cross sMax and/or sMin
        // Above max: [a, min(b, sMax)] — flat at maxPrice
        double maxClampEnd = Math.min(b, sMax);
        if (a < maxClampEnd) {
            total += maxPrice * (maxClampEnd - a);
        }

        // Normal region: [max(a, sMax), min(b, sMin)]
        double normalA = Math.max(a, sMax);
        double normalB = Math.min(b, sMin);
        if (normalA < normalB) {
            double term1 = (normalB - normalA);
            double term2 = 0.5 * k * (normalB * normalB - normalA * normalA) / (2.0 * L);
            total += B * (term1 - term2) * t;
        }

        // Below min: [max(a, sMin), b] — flat at minPrice
        double minClampStart = Math.max(a, sMin);
        if (minClampStart < b) {
            total += minPrice * (b - minClampStart);
        }

        return total;
    }

    // ------------------------------------------------------------------------
    // CATEGORY DETECTION + CACHING
    // ------------------------------------------------------------------------
    public static ItemCategory detectCategory(Material mat) {
        // Check for admin category override first
        if (categoryOverrides.containsKey(mat)) {
            return categoryOverrides.get(mat);
        }

        if (categoryCache.containsKey(mat)) {
            return categoryCache.get(mat);
        }

        String name = mat.name();

        // === TOOLS & WEAPONS ===
        if (name.contains("PICKAXE") || name.contains("AXE") && !name.contains("WAX") ||
                name.contains("SHOVEL") || name.contains("SWORD") ||
                name.contains("TRIDENT") || name.contains("BOW") ||
                name.contains("CROSSBOW") || name.contains("FISHING_ROD") ||
                name.contains("MACE") || name.contains("SHEARS") ||
                name.contains("FLINT_AND_STEEL") || name.contains("SHIELD") ||
                name.contains("BRUSH") || name.equals("ARROW") ||
                name.contains("SPECTRAL_ARROW") || name.contains("TIPPED_ARROW")) {
            return cacheAndReturn(mat, ItemCategory.TOOLS);
        }

        // === ARMOR ===
        if (name.contains("HELMET") || name.contains("CHESTPLATE") ||
                name.contains("LEGGINGS") || name.contains("BOOTS") ||
                name.contains("ELYTRA") || name.equals("TURTLE_SHELL") ||
                name.equals("TURTLE_SCUTE") || name.contains("HORSE_ARMOR")) {
            return cacheAndReturn(mat, ItemCategory.ARMOR);
        }

        // === WOOD ===
        if ((name.contains("LOG") && !name.equals("MAGMA_BLOCK")) ||
                name.contains("WOOD") && !name.equals("PETRIFIED_OAK_SLAB") ||
                name.contains("PLANK") ||
                name.contains("FENCE") && !name.contains("NETHER") ||
                name.contains("DOOR") && !name.contains("IRON") && !name.contains("TRAP") ||
                name.contains("TRAPDOOR") && !name.contains("IRON") ||
                name.contains("STAIRS") && (name.contains("OAK") || name.contains("SPRUCE") ||
                        name.contains("BIRCH") || name.contains("JUNGLE") || name.contains("ACACIA") ||
                        name.contains("DARK_OAK") || name.contains("MANGROVE") || name.contains("CHERRY") ||
                        name.contains("BAMBOO") || name.contains("CRIMSON") || name.contains("WARPED"))
                ||
                name.contains("SLAB") && (name.contains("OAK") || name.contains("SPRUCE") ||
                        name.contains("BIRCH") || name.contains("JUNGLE") || name.contains("ACACIA") ||
                        name.contains("DARK_OAK") || name.contains("MANGROVE") || name.contains("CHERRY") ||
                        name.contains("BAMBOO") || name.contains("CRIMSON") || name.contains("WARPED"))
                ||
                name.contains("SIGN") && !name.equals("DESIGN") ||
                name.contains("BARREL") ||
                name.contains("CHEST") && (name.contains("OAK") || name.contains("SPRUCE") ||
                        name.contains("BIRCH") || name.contains("JUNGLE") || name.contains("ACACIA") ||
                        name.contains("DARK_OAK") || name.contains("MANGROVE") || name.contains("CHERRY") ||
                        name.contains("BAMBOO") || name.contains("CRIMSON") || name.contains("WARPED"))) {
            return cacheAndReturn(mat, ItemCategory.WOOD);
        }

        // === BLOCKS ===
        if (name.contains("STONE") && !name.contains("REDSTONE") && !name.contains("LODESTONE") ||
                name.contains("DIRT") || name.contains("SAND") && !name.contains("SANDSTONE") ||
                name.contains("GRAVEL") || name.contains("COBBLE") ||
                name.contains("BRICK") && !name.contains("NETHER") ||
                name.contains("TERRACOTTA") || name.contains("CONCRETE") ||
                name.contains("GLASS") && !name.contains("BOTTLE") ||
                name.contains("WOOL") || name.contains("CARPET") ||
                name.contains("CLAY") && !name.equals("CLAY_BALL") ||
                name.contains("MUD") || name.equals("MOSS_BLOCK") || name.equals("MOSS_CARPET") ||
                name.contains("GRANITE") || name.contains("DIORITE") || name.contains("ANDESITE") ||
                name.contains("DEEPSLATE") || name.contains("TUFF") || name.contains("CALCITE") ||
                name.contains("BASALT") || name.contains("BLACKSTONE") ||
                name.contains("PRISMARINE") || name.contains("PURPUR") ||
                name.equals("QUARTZ_BLOCK") || name.contains("SMOOTH_QUARTZ") ||
                name.contains("NETHERRACK") || name.contains("SOUL_SAND") || name.contains("SOUL_SOIL") ||
                name.contains("END_STONE") || name.contains("OBSIDIAN") ||
                name.contains("ICE") || name.equals("SNOW_BLOCK") || name.contains("PACKED_ICE") ||
                name.contains("SANDSTONE") || name.contains("SMOOTH_SANDSTONE") ||
                name.contains("CUT_SANDSTONE") || name.equals("GLOWSTONE") ||
                name.equals("SEA_LANTERN") || name.equals("MAGMA_BLOCK") ||
                name.contains("NETHER_BRICK") || name.equals("RED_NETHER_BRICKS") ||
                name.equals("SPONGE") || name.equals("WET_SPONGE") ||
                name.equals("SLIME_BLOCK") || name.equals("HONEY_BLOCK") ||
                name.equals("BEDROCK") || name.contains("SPAWNER")) {
            return cacheAndReturn(mat, ItemCategory.BLOCKS);
        }

        // === FOOD ===
        if (name.contains("FISH") && !name.contains("FISHING") ||
                name.contains("APPLE") ||
                name.contains("CARROT") && !name.equals("CARROT_ON_A_STICK") ||
                name.contains("POTATO") || name.contains("BEEF") ||
                name.contains("PORK") ||
                name.contains("CHICKEN") && !name.equals("CHICKEN_SPAWN_EGG") ||
                name.contains("BREAD") || name.contains("COOKIE") ||
                name.contains("MUTTON") ||
                name.contains("RABBIT") && !name.contains("FOOT") ||
                name.contains("STEW") || name.contains("SOUP") ||
                name.contains("MELON_SLICE") || name.contains("BERRIES") ||
                name.contains("CHORUS") && name.contains("FRUIT") ||
                name.contains("BEETROOT") && !name.contains("SEEDS") ||
                name.equals("DRIED_KELP") || name.equals("HONEY_BOTTLE") ||
                name.equals("MILK_BUCKET") || name.equals("CAKE") ||
                name.equals("PUMPKIN_PIE") || name.contains("SUSPICIOUS_STEW") ||
                name.equals("ENCHANTED_GOLDEN_APPLE") || name.equals("GOLDEN_APPLE") ||
                name.equals("GOLDEN_CARROT") || name.equals("POISONOUS_POTATO") ||
                name.equals("ROTTEN_FLESH") || name.equals("SPIDER_EYE") ||
                name.contains("MUSHROOM_STEW") || name.equals("EGG") ||
                name.equals("SUGAR") || name.contains("SWEET_BERRIES") ||
                name.contains("GLOW_BERRIES")) {
            return cacheAndReturn(mat, ItemCategory.FOOD);
        }

        // === REDSTONE ===
        if (name.contains("REDSTONE") && !name.contains("REDSTONE_ORE") ||
                name.contains("PISTON") || name.contains("REPEATER") ||
                name.contains("COMPARATOR") || name.contains("HOPPER") ||
                name.contains("OBSERVER") || name.contains("DISPENSER") ||
                name.contains("DROPPER") || name.contains("LEVER") ||
                name.contains("BUTTON") && !name.contains("OAK") && !name.contains("SPRUCE") &&
                        !name.contains("BIRCH") && !name.contains("JUNGLE") && !name.contains("ACACIA") &&
                        !name.contains("DARK_OAK") && !name.contains("MANGROVE") && !name.contains("CHERRY") &&
                        !name.contains("BAMBOO") && !name.contains("CRIMSON") && !name.contains("WARPED")
                ||
                name.contains("PRESSURE_PLATE") && !name.contains("STONE_PRESSURE_PLATE") &&
                        !name.contains("OAK") && !name.contains("SPRUCE") && !name.contains("BIRCH") &&
                        !name.contains("JUNGLE") && !name.contains("ACACIA") && !name.contains("DARK_OAK") &&
                        !name.contains("MANGROVE") && !name.contains("CHERRY")
                ||
                name.contains("RAIL") || name.contains("DETECTOR") && !name.equals("DETECTOR_RAIL") ||
                name.contains("REDSTONE_TORCH") || name.contains("REDSTONE_LAMP") ||
                name.contains("DAYLIGHT") || name.contains("TRIPWIRE") ||
                name.equals("NOTE_BLOCK") || name.equals("JUKEBOX") ||
                name.equals("TARGET") || name.equals("LIGHTNING_ROD") ||
                name.contains("SCULK_SENSOR") || name.equals("CRAFTER") ||
                name.equals("CHEST") && !name.contains("ENDER") ||
                name.equals("TRAPPED_CHEST") || name.contains("FURNACE") ||
                name.equals("LECTERN") || name.equals("BELL") ||
                name.contains("DOOR") && name.equals("IRON_DOOR") ||
                name.contains("TRAPDOOR") && name.equals("IRON_TRAPDOOR")) {
            return cacheAndReturn(mat, ItemCategory.REDSTONE);
        }

        // === FARMING ===
        if (name.contains("WHEAT") && !name.equals("WHEAT") ||
                name.contains("SEEDS") || name.contains("BONE_MEAL") ||
                name.equals("BONE_BLOCK") || name.equals("COMPOSTER") ||
                name.equals("HAY_BLOCK") ||
                name.equals("PUMPKIN") && !name.equals("PUMPKIN_PIE") ||
                name.equals("CARVED_PUMPKIN") || name.equals("JACK_O_LANTERN") ||
                name.equals("SUGAR_CANE") ||
                name.contains("BAMBOO") && !name.contains("BUTTON") &&
                        !name.contains("DOOR") && !name.contains("FENCE") &&
                        !name.contains("SIGN") && !name.contains("STAIRS") &&
                        !name.contains("SLAB")
                ||
                name.equals("CACTUS") || name.equals("COCOA_BEANS") ||
                name.contains("NETHER_WART") || name.contains("SAPLING") ||
                name.contains("LEAVES") && !name.contains("BOOK") ||
                name.equals("VINE") || name.contains("GLOW_LICHEN") ||
                name.equals("PITCHER_PLANT") || name.equals("TORCHFLOWER") ||
                name.contains("SPORE_BLOSSOM") || name.contains("AZALEA") ||
                name.equals("BIG_DRIPLEAF") || name.equals("SMALL_DRIPLEAF") ||
                name.contains("HOE") || name.contains("FLOWER") ||
                name.contains("TULIP") || name.equals("DANDELION") ||
                name.equals("POPPY") || name.equals("BLUE_ORCHID") ||
                name.equals("ALLIUM") || name.equals("AZURE_BLUET") ||
                name.equals("OXEYE_DAISY") || name.equals("CORNFLOWER") ||
                name.equals("LILY_OF_THE_VALLEY") || name.equals("WITHER_ROSE") ||
                name.equals("SUNFLOWER") || name.equals("LILAC") ||
                name.equals("ROSE_BUSH") || name.equals("PEONY") ||
                name.equals("TALL_GRASS") || name.equals("LARGE_FERN") ||
                name.equals("FERN") || name.equals("GRASS") ||
                name.contains("MUSHROOM") && !name.contains("STEW") ||
                name.equals("BROWN_MUSHROOM_BLOCK") || name.equals("RED_MUSHROOM_BLOCK") ||
                name.equals("MUSHROOM_STEM") || name.contains("FUNGUS") ||
                name.equals("WARPED_ROOTS") || name.equals("CRIMSON_ROOTS") ||
                name.equals("WEEPING_VINES") || name.equals("TWISTING_VINES") ||
                name.equals("MELON") || name.equals("CHORUS_PLANT") ||
                name.equals("CHORUS_FLOWER")) {
            return cacheAndReturn(mat, ItemCategory.FARMING);
        }

        return cacheAndReturn(mat, ItemCategory.MISC);
    }

    private static ItemCategory cacheAndReturn(Material mat, ItemCategory cat) {
        categoryCache.put(mat, cat);
        return cat;
    }

    // ------------------------------------------------------------------------
    // CATEGORY ITEM LISTS
    // ------------------------------------------------------------------------
    private static void buildCategoryLists() {

        for (ItemCategory cat : ItemCategory.values()) {
            if (cat == ItemCategory.PERMISSIONS || cat == ItemCategory.SERVER_SHOP) {
                continue;
            }

            categoryItems.put(cat, new ArrayList<>());
        }

        for (Material mat : itemConfigs.keySet()) {
            ItemCategory cat = detectCategory(mat);
            if (cat == ItemCategory.PERMISSIONS || cat == ItemCategory.SERVER_SHOP) {
                continue;
            }

            categoryItems.get(cat).add(mat);
        }

        for (List<Material> list : categoryItems.values()) {
            list.sort(Comparator.comparing(Enum::name));
        }
    }

    public static List<Material> getItemsForCategory(ItemCategory cat) {
        return categoryItems.getOrDefault(cat, Collections.emptyList());
    }

    // ------------------------------------------------------------------------
    // QUERY — GET ITEMS IN A CATEGORY
    // ------------------------------------------------------------------------
    public static List<Material> getItemsInCategory(ItemCategory cat) {
        List<Material> result;
        if (cat == ItemCategory.MISC) {
            result = new ArrayList<>(itemConfigs.keySet());
        } else {
            result = new ArrayList<>(categoryItems.getOrDefault(cat, Collections.emptyList()));
        }

        // Filter out disabled items (base price < 0) for regular shop display
        result.removeIf(mat -> {
            ShopItemConfig c = itemConfigs.get(mat);
            return c == null || c.basePrice < 0;
        });
        return result;
    }

    /**
     * Get items in category WITHOUT filtering disabled items (for admin GUI)
     */
    public static List<Material> getItemsInCategoryIncludeDisabled(ItemCategory cat) {
        if (cat == ItemCategory.MISC) {
            return new ArrayList<>(itemConfigs.keySet());
        }
        return new ArrayList<>(categoryItems.getOrDefault(cat, Collections.emptyList()));
    }

    // ------------------------------------------------------------------------
    // DYNAMIC DATA ACCESSORS
    // ------------------------------------------------------------------------
    public static double getStock(Material mat) {
        return stockMap.getOrDefault(mat, 0.0);
    }

    public static double getPurchases(Material mat) {
        return purchasesMap.getOrDefault(mat, 0.0);
    }

    public static long getLastUpdate(Material mat) {
        return lastUpdateMap.getOrDefault(mat, System.currentTimeMillis());
    }

    public static void setLastUpdate(Material mat, long timestamp) {
        lastUpdateMap.put(mat, timestamp);
        markDirty(mat);
    }

    // ------------------------------------------------------------------------
    // SPECIAL DIRECT SETTERS (NO P2P)
    // ------------------------------------------------------------------------
    public static void setStockDirect(Material mat, double stock) {
        // Capture current shortage before changing anything
        accumulateShortage(mat);

        // Clamp to storage limits
        ShopItemConfig cfg = itemConfigs.get(mat);
        if (cfg != null) {
            if (cfg.maxStockStorage != null && stock > cfg.maxStockStorage) {
                stock = cfg.maxStockStorage;
            }
            if (cfg.minStockStorage != null && stock < cfg.minStockStorage) {
                stock = cfg.minStockStorage;
            }
        }

        double oldStock = stockMap.getOrDefault(mat, 0.0);
        stockMap.put(mat, stock);

        // If stock becomes positive, reset shortage hours
        if (stock > 0) {
            shortageHoursMap.put(mat, 0.0);
        }

        if (stock <= 0 && oldStock > 0) {
            lastUpdateMap.put(mat, System.currentTimeMillis());
        } else if (stock > 0 && oldStock <= 0) {
            lastUpdateMap.put(mat, System.currentTimeMillis());
        } else {
            // Even if state didn't flip, we updated stock, potentially resetting "last
            // update"
            // logic implies we should reset lastUpdate on ANY significant change?
            // With cumulative logic, we usually WANT to reset lastUpdate so "live" tracking
            // starts fresh.
            lastUpdateMap.put(mat, System.currentTimeMillis());
        }

        markDirty(mat);
    }

    public static void setPurchasesDirect(Material mat, double purchases) {
        purchasesMap.put(mat, purchases);
        markDirty(mat);
    }

    // ------------------------------------------------------------------------
    // STOCK UPDATES (WITH P2P BROADCAST)
    // ------------------------------------------------------------------------
    public static void updateStock(Material mat, double delta) {
        // Capture shortage before update
        accumulateShortage(mat);

        // Note: Inflation is only reset when stock goes positive (below)

        double oldStock = getStock(mat);
        double newStock = oldStock + delta;

        // Clamp to storage limits
        ShopItemConfig cfg = itemConfigs.get(mat);
        if (cfg != null) {
            if (cfg.maxStockStorage != null && newStock > cfg.maxStockStorage) {
                newStock = cfg.maxStockStorage;
            }
            if (cfg.minStockStorage != null && newStock < cfg.minStockStorage) {
                newStock = cfg.minStockStorage;
            }
        }

        stockMap.put(mat, newStock);

        // Note: shortage hours are NOT reset when stock goes positive.
        // They only accumulate while stock <= 0 (via accumulateShortage).
        // Admins can manually reset via /shopadmin commands if needed.

        // Even if we stay negative, we reset lastUpdate because we "baked" the previous
        // time
        lastUpdateMap.put(mat, System.currentTimeMillis());

        markDirty(mat);

        if (plugin != null &&
                plugin.getP2PCrossServerManager() != null &&
                plugin.getP2PCrossServerManager().isRunning()) {
            plugin.getP2PCrossServerManager().publishStockUpdate(mat, newStock, getPurchases(mat));
        }
    }

    /**
     * Force an immediate write of the current in-memory dynamic data to
     * shopdata.yml.
     * Used on plugin shutdown and before certain cross-server sync operations.
     */
    public static void flushQueue() {
        saveQueuedItems();
    }

    /**
     * Apply a stock update received from another server via the P2P cross-server
     * manager.
     * This does NOT re-broadcast the change; it only updates local state and lets
     * the
     * normal YAML save cycle persist the data.
     */
    public static void receiveRemoteStockUpdate(Material mat, double stock, double purchases) {
        if (mat == null)
            return;

        // Update dynamic data without triggering another publish
        setStockDirect(mat, stock);
        setPurchasesDirect(mat, purchases);

        // Keep "last update" accurate for stats / PAPI
        setLastUpdate(mat, System.currentTimeMillis());

        markDirty(mat);
    }

    // ------------------------------------------------------------------------
    // DYNAMIC DATA SAVE / LOAD (YAML)
    // ------------------------------------------------------------------------
    public static void saveDynamicData() {
        ConfigurationSection itemsSec = shopDataConfig.getConfigurationSection("items");
        if (itemsSec == null) {
            itemsSec = shopDataConfig.createSection("items");
        }

        for (Material mat : itemConfigs.keySet()) {
            String key = mat.name();
            ConfigurationSection sec = itemsSec.getConfigurationSection(key);
            if (sec == null)
                sec = itemsSec.createSection(key);

            sec.set("stock", stockMap.getOrDefault(mat, 0.0));
            sec.set("purchases", purchasesMap.getOrDefault(mat, 0.0));
            sec.set("last_update", lastUpdateMap.getOrDefault(mat, System.currentTimeMillis()));
            sec.set("shortage_hours", shortageHoursMap.getOrDefault(mat, 0.0));
        }

        try {
            shopDataConfig.save(shopDataFile);
        } catch (IOException e) {
            Bukkit.getLogger().severe("[DynamicShop] Failed to save shopdata.yml: " + e.getMessage());
        }
    }

    public static void saveQueuedItems() {
        if (saveQueue.isEmpty())
            return;

        ConfigurationSection itemsSec = shopDataConfig.getConfigurationSection("items");
        if (itemsSec == null) {
            itemsSec = shopDataConfig.createSection("items");
        }

        // Copy to avoid concurrent modification if something calls markDirty while
        // saving
        Set<Material> toSave = new HashSet<>(saveQueue);

        for (Material mat : toSave) {
            String key = mat.name();
            ConfigurationSection sec = itemsSec.getConfigurationSection(key);
            if (sec == null) {
                sec = itemsSec.createSection(key);
            }

            sec.set("stock", stockMap.getOrDefault(mat, 0.0));
            sec.set("purchases", purchasesMap.getOrDefault(mat, 0.0));
            sec.set("last_update", lastUpdateMap.getOrDefault(mat, System.currentTimeMillis()));
            sec.set("shortage_hours", shortageHoursMap.getOrDefault(mat, 0.0));
        }

        // Now clear the ones we just saved
        saveQueue.removeAll(toSave);

        try {
            shopDataConfig.save(shopDataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[DynamicShop] Failed to save shopdata.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // LOAD DYNAMIC DATA
    // ------------------------------------------------------------------------
    private static void loadDynamicData() {
        stockMap.clear();
        purchasesMap.clear();
        lastUpdateMap.clear();
        shortageHoursMap.clear();

        if (!shopDataFile.exists()) {
            plugin.getLogger().info("§e[ShopData] No shopdata.yml found — starting fresh.");
            shopDataConfig = new YamlConfiguration();
            shopDataConfig.createSection("items");
            saveDynamicData();

            long now = System.currentTimeMillis();
            for (Material mat : itemConfigs.keySet()) {
                stockMap.put(mat, 0.0);
                purchasesMap.put(mat, 0.0);
                lastUpdateMap.put(mat, now);
                shortageHoursMap.put(mat, 0.0);
            }

            return;
        }

        ConfigurationSection itemsSec = shopDataConfig.getConfigurationSection("items");
        if (itemsSec == null) {
            itemsSec = shopDataConfig.createSection("items");
        }

        ConfigurationSection stockSec = shopDataConfig.getConfigurationSection("stock");
        ConfigurationSection purchasesSec = shopDataConfig.getConfigurationSection("purchases");
        ConfigurationSection lastUpdateSec = shopDataConfig.getConfigurationSection("last_update");
        ConfigurationSection shortageSec = shopDataConfig.getConfigurationSection("shortage_hours");

        long now = System.currentTimeMillis();

        for (Material mat : itemConfigs.keySet()) {
            String key = mat.name();

            ConfigurationSection sec = itemsSec.getConfigurationSection(key);
            if (sec == null)
                sec = itemsSec.createSection(key);

            // STOCK
            double stock = 0.0;
            if (sec.contains("stock")) {
                stock = sec.getDouble("stock");
            } else if (stockSec != null && stockSec.isDouble(key)) {
                stock = stockSec.getDouble(key);
            }
            sec.set("stock", stock);
            stockMap.put(mat, stock);

            // PURCHASES
            double purchases = 0.0;
            if (sec.contains("purchases")) {
                purchases = sec.getDouble("purchases");
            } else if (purchasesSec != null && purchasesSec.isDouble(key)) {
                purchases = purchasesSec.getDouble(key);
            }
            sec.set("purchases", purchases);
            purchasesMap.put(mat, purchases);

            // LAST UPDATE
            long lastUpdate = now;
            if (sec.contains("last_update")) {
                lastUpdate = sec.getLong("last_update", now);
            } else if (lastUpdateSec != null && lastUpdateSec.isLong(key)) {
                lastUpdate = lastUpdateSec.getLong(key, now);
            }
            sec.set("last_update", lastUpdate);
            lastUpdateMap.put(mat, lastUpdate);

            // SHORTAGE HOURS
            double shortageHours = 0.0;
            if (sec.contains("shortage_hours")) {
                shortageHours = sec.getDouble("shortage_hours");
            } else if (shortageSec != null && shortageSec.isDouble(key)) {
                shortageHours = shortageSec.getDouble(key);
            }
            sec.set("shortage_hours", shortageHours);
            shortageHoursMap.put(mat, shortageHours);
        }

        // Cleanup old root-level sections (migrated into items.*)
        shopDataConfig.set("stock", null);
        shopDataConfig.set("last_update", null);
        shopDataConfig.set("purchases", null);

        saveDynamicData();
    }

    // ------------------------------------------------------------------------
    // SHORTAGE TRACKING
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // SHORTAGE TRACKING
    // ------------------------------------------------------------------------

    /**
     * Calculates the total hours this item has been in a shortage (stock <= 0).
     * Returns: Stored Accumulated Hours + Live Current Duration (if stock <= 0)
     *          OR Stored Hours - Decay (if stock > 0)
     *
     * When stock is positive, shortage hours decay over time proportional to
     * how close the stock is to max-stock:
     *   decayRate = configuredRate * (stock / maxStock)
     *   effectiveHours = storedHours - (decayRate * timeSinceLastUpdate)
     */
    public static double getHoursInShortage(Material mat) {
        double stored = shortageHoursMap.getOrDefault(mat, 0.0);
        double stock = getStock(mat);

        if (stock <= 0) {
            // Currently out of stock — add live duration since last update
            long diff = System.currentTimeMillis() - getLastUpdate(mat);
            stored += (diff / 3600000.0);
        } else if (stored > 0) {
            // Stock is positive — apply decay based on how full the shop is
            double decayRate = ConfigCacheManager.shortageDecayPercentPerHour;
            if (decayRate > 0) {
                ShopItemConfig cfg = itemConfigs.get(mat);
                double L = (cfg != null && cfg.maxStock != null) ? cfg.maxStock : ConfigCacheManager.maxStock;
                double stockRatio = Math.min(stock / L, 1.0); // 0.0 → 1.0

                long diff = System.currentTimeMillis() - getLastUpdate(mat);
                double hoursSinceUpdate = diff / 3600000.0;

                // Decay: the fuller the shop, the faster shortage hours drain
                double decay = decayRate * stockRatio * hoursSinceUpdate;
                stored = Math.max(0.0, stored - decay);
            }
        }

        return stored;
    }

    /**
     * Helper to "bake" the current live shortage duration OR decay into the map.
     * Call this BEFORE resetting lastUpdate or changing stock.
     */
    private static void accumulateShortage(Material mat) {
        double stored = shortageHoursMap.getOrDefault(mat, 0.0);
        double stock = getStock(mat);
        long now = System.currentTimeMillis();
        long last = getLastUpdate(mat);
        double deltaHours = (now - last) / 3600000.0;

        if (stock <= 0) {
            // Out of stock — accumulate shortage time
            shortageHoursMap.put(mat, stored + deltaHours);
            markDirty(mat);
        } else if (stored > 0) {
            // Stock is positive and we have stored shortage hours — apply decay
            double decayRate = ConfigCacheManager.shortageDecayPercentPerHour;
            if (decayRate > 0) {
                ShopItemConfig cfg = itemConfigs.get(mat);
                double L = (cfg != null && cfg.maxStock != null) ? cfg.maxStock : ConfigCacheManager.maxStock;
                double stockRatio = Math.min(stock / L, 1.0);
                double decay = decayRate * stockRatio * deltaHours;
                double newStored = Math.max(0.0, stored - decay);
                shortageHoursMap.put(mat, newStored);
                markDirty(mat);
            }
        }
    }

    public static void setHoursInShortage(Material mat, double hours) {
        shortageHoursMap.put(mat, hours);
        markDirty(mat);
    }

    /**
     * Periodic tick — bakes shortage accumulation/decay for ALL items.
     * Called every 5 minutes so items passively gain/lose shortage
     * even without player transactions.
     */
    public static void tickAllShortage() {
        long now = System.currentTimeMillis();
        for (Material mat : itemConfigs.keySet()) {
            if (getBasePrice(mat) < 0) continue; // skip disabled
            accumulateShortage(mat);
            lastUpdateMap.put(mat, now);
        }
    }

    public static void addHoursInShortage(Material mat, double deltaHours) {
        shortageHoursMap.put(mat, getHoursInShortage(mat) + deltaHours);
        markDirty(mat);
    }

    public static double getShortageHours(Material mat) {
        return getHoursInShortage(mat);
    }

    public static void resetAllShortageData() {
        shortageHoursMap.clear();
        long now = System.currentTimeMillis();
        for (Material mat : itemConfigs.keySet()) {
            // Reset last update to now so "live" tracking doesn't jump
            lastUpdateMap.put(mat, now);
            markDirty(mat);
        }
        saveDynamicData();
    }

    // ------------------------------------------------------------------------
    // ITEM STATISTICS (for PlaceholderAPI)
    // ------------------------------------------------------------------------
    public static double getTotalStockValue() {
        double total = 0.0;
        for (Material mat : itemConfigs.keySet()) {
            double base = getBasePrice(mat);
            if (base <= 0)
                continue;
            total += getStock(mat) * base;
        }
        return total;
    }

    public static double getTotalPurchasesValue() {
        double total = 0.0;
        for (Material mat : itemConfigs.keySet()) {
            double base = getBasePrice(mat);
            if (base <= 0)
                continue;
            total += getPurchases(mat) * base;
        }
        return total;
    }

    public static int getTotalTrackedItems() {
        return itemConfigs.size();
    }

    public static Set<Material> getAllTrackedMaterials() {
        return Collections.unmodifiableSet(itemConfigs.keySet());
    }

    // queue helpers
    private static void markDirty(Material mat) {
        if (mat != null) {
            saveQueue.add(mat);
        }
    }

    // ------------------------------------------------------------------------
    // ITEM STATISTICS (for PlaceholderAPI)
    // ------------------------------------------------------------------------
    public static class ItemStats {
        public final String displayName;
        public final int timesBought;
        public final int timesSold;
        public final int quantityBought;
        public final int quantitySold;

        public ItemStats(String displayName, int timesBought, int timesSold, int quantityBought, int quantitySold) {
            this.displayName = displayName;
            this.timesBought = timesBought;
            this.timesSold = timesSold;
            this.quantityBought = quantityBought;
            this.quantitySold = quantitySold;
        }

        public int getNetFlow() {
            return quantitySold - quantityBought;
        }
    }

    public static ItemStats getStats(String materialName) {
        try {
            Material mat = Material.valueOf(materialName.toUpperCase());

            if (!itemConfigs.containsKey(mat))
                return null;

            String displayName = Arrays.stream(mat.name().toLowerCase().split("_"))
                    .map(w -> w.isEmpty() ? "" : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                    .collect(java.util.stream.Collectors.joining(" "));

            int timesBought = 0;
            int timesSold = 0;
            int quantityBought = 0;
            int quantitySold = 0;

            if (plugin != null && plugin.getTransactionLogger() != null) {
                List<org.minecraftsmp.dynamicshop.transactions.Transaction> transactions = plugin.getTransactionLogger()
                        .getRecentTransactions();

                for (org.minecraftsmp.dynamicshop.transactions.Transaction tx : transactions) {
                    if (tx.getItem().equalsIgnoreCase(mat.name())) {
                        if (tx.getType() == org.minecraftsmp.dynamicshop.transactions.Transaction.TransactionType.BUY) {
                            timesBought++;
                            quantityBought += tx.getAmount();
                        } else if (tx
                                .getType() == org.minecraftsmp.dynamicshop.transactions.Transaction.TransactionType.SELL) {
                            timesSold++;
                            quantitySold += tx.getAmount();
                        }
                    }
                }
            }

            return new ItemStats(displayName, timesBought, timesSold, quantityBought, quantitySold);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // DEBUG / RESET HELPERS
    // ------------------------------------------------------------------------
    public static void resetAllDynamicData() {
        for (Material mat : itemConfigs.keySet()) {
            stockMap.put(mat, 0.0);
            purchasesMap.put(mat, 0.0);
            lastUpdateMap.put(mat, System.currentTimeMillis());
            shortageHoursMap.put(mat, 0.0);
        }

        shopDataConfig.set("stock", null);
        shopDataConfig.set("last_update", null);
        shopDataConfig.set("purchases", null);

        saveDynamicData();
    }

    // ------------------------------------------------------------------------
    // ADMIN ITEM MANAGEMENT
    // ------------------------------------------------------------------------

    /**
     * Check if an item is disabled from the shop (base price < 0)
     */
    public static boolean isItemDisabled(Material mat) {
        ShopItemConfig cfg = itemConfigs.get(mat);
        return cfg == null || cfg.basePrice < 0;
    }

    /**
     * Enable or disable an item in the shop.
     * Uses base: -1 to disable (standard convention).
     * When enabling, restores to 1.0 as default price.
     */
    public static void setItemDisabled(Material mat, boolean disabled) {
        if (disabled) {
            // Store -1 as base price to disable
            setBasePrice(mat, -1);
        } else {
            // Restore to default price of 1.0
            setBasePrice(mat, 1.0);
        }
    }

    /**
     * Set the base price for an item
     */
    public static void setBasePrice(Material mat, double price) {
        // Since ShopItemConfig is a record, we must replace the whole entry
        ShopItemConfig old = itemConfigs.get(mat);
        ShopItemConfig newConfig;
        if (old == null) {
            // Should usually not happen if we are enabling/adding, but safe default
            newConfig = new ShopItemConfig(price, null, null, null, null, false, false, null, null);
        } else {
            newConfig = new ShopItemConfig(price, old.maxStock, old.minStock, old.maxStockStorage, old.minStockStorage,
                    old.disableBuy, old.disableSell, old.categoryOverride, old.stockRate);
        }
        itemConfigs.put(mat, newConfig);

        // Save to config
        plugin.getConfig().set("items." + mat.name() + ".base", price);
        plugin.saveConfig();
    }

    /**
     * Enable or disable buying for an item
     */
    public static void setBuyDisabled(Material mat, boolean disabled) {
        ShopItemConfig old = itemConfigs.get(mat);
        if (old == null) return;
        ShopItemConfig newConfig = new ShopItemConfig(old.basePrice, old.maxStock, old.minStock,
                old.maxStockStorage, old.minStockStorage, disabled, old.disableSell, old.categoryOverride, old.stockRate);
        itemConfigs.put(mat, newConfig);

        plugin.getConfig().set("items." + mat.name() + ".disable-buy", disabled);
        plugin.saveConfig();
    }

    /**
     * Enable or disable selling for an item
     */
    public static void setSellDisabled(Material mat, boolean disabled) {
        ShopItemConfig old = itemConfigs.get(mat);
        if (old == null) return;
        ShopItemConfig newConfig = new ShopItemConfig(old.basePrice, old.maxStock, old.minStock,
                old.maxStockStorage, old.minStockStorage, old.disableBuy, disabled, old.categoryOverride, old.stockRate);
        itemConfigs.put(mat, newConfig);

        plugin.getConfig().set("items." + mat.name() + ".disable-sell", disabled);
        plugin.saveConfig();
    }

    /**
     * Set max stock (pricing curve limit) for an item
     */
    public static void setMaxStock(Material mat, Double max) {
        ShopItemConfig old = itemConfigs.get(mat);
        if (old == null) return;
        ShopItemConfig newConfig = new ShopItemConfig(old.basePrice(), max, old.minStock(),
                old.maxStockStorage(), old.minStockStorage(), old.disableBuy(), old.disableSell(), old.categoryOverride(), old.stockRate());
        itemConfigs.put(mat, newConfig);

        plugin.getConfig().set("items." + mat.name() + ".max-stock", max);
        plugin.saveConfig();
    }

    /**
     * Set max stock storage (hard limit) for an item
     */
    public static void setMaxStockStorage(Material mat, Integer max) {
        ShopItemConfig old = itemConfigs.get(mat);
        if (old == null) return;
        ShopItemConfig newConfig = new ShopItemConfig(old.basePrice(), old.maxStock(), old.minStock(),
                max, old.minStockStorage(), old.disableBuy(), old.disableSell(), old.categoryOverride(), old.stockRate());
        itemConfigs.put(mat, newConfig);

        plugin.getConfig().set("items." + mat.name() + ".max-stock-storage", max);
        plugin.saveConfig();
    }

    /**
     * Set the stock rate for an item (per-item override)
     */
    public static void setStockRate(Material mat, Double rate) {
        ShopItemConfig old = itemConfigs.get(mat);
        if (old == null) return;
        ShopItemConfig newConfig = new ShopItemConfig(old.basePrice, old.maxStock, old.minStock,
                old.maxStockStorage, old.minStockStorage, old.disableBuy, old.disableSell, old.categoryOverride, rate);
        itemConfigs.put(mat, newConfig);

        if (rate != null) {
            plugin.getConfig().set("items." + mat.name() + ".rate", rate);
        } else {
            plugin.getConfig().set("items." + mat.name() + ".rate", null);
        }
        plugin.saveConfig();
    }

    /**
     * Get the effective stock rate for an item (per-item or global fallback)
     */
    public static double getStockRate(Material mat) {
        ShopItemConfig cfg = itemConfigs.get(mat);
        if (cfg != null && cfg.stockRate != null) {
            return cfg.stockRate;
        }
        return ConfigCacheManager.negativeStockPercentPerItem;
    }

    /**
     * Set a category override for an item
     */
    public static void setCategoryOverride(Material mat, ItemCategory category) {
        categoryOverrides.put(mat, category);
        categoryCache.put(mat, category); // Update cache too

        // Save to config
        plugin.getConfig().set("items." + mat.name() + ".category", category.name());
        plugin.saveConfig();

        // Rebuild category lists to reflect the change
        buildCategoryLists();
    }
}
