package org.minecraftsmp.dynamicshop.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ShopDataManager {

    // base prices (static config, from config.yml)
    static final Map<Material, Double> basePrices = new ConcurrentHashMap<>();

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

    // Save queue
    // which items need to be written to YAML
    private static final Set<Material> saveQueue = ConcurrentHashMap.newKeySet();

    private static DynamicShop plugin;

    // dynamic data file
    private static File shopDataFile;
    private static YamlConfiguration shopDataConfig;

    public static BukkitRunnable saveTimer;

    // ------------------------------------------------------------------------
    // INIT
    // ------------------------------------------------------------------------
    public static void init(DynamicShop pl) {
        plugin = pl;

        basePrices.clear();
        categoryCache.clear();
        categoryItems.clear();
        categoryOverrides.clear();

        loadConfigItems();
        buildCategoryLists();

        shopDataFile = new File(plugin.getDataFolder(), "shopdata.yml");
        shopDataConfig = YamlConfiguration.loadConfiguration(shopDataFile);
        loadDynamicData();

        if (ConfigCacheManager.crossServerEnabled) {
            if (saveTimer == null || saveTimer.isCancelled()) {
                saveTimer = new BukkitRunnable() {
                    @Override
                    public void run() {
                        saveQueuedItems();
                    }
                };
                int seconds = ConfigCacheManager.crossServerSaveInterval;
                saveTimer.runTaskTimer(plugin, seconds * 20L, seconds * 20L);
            }
        }
    }

    public static void reload() {
        flushQueue();
        init(plugin);
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

            basePrices.put(mat, base);

            // Load category override
            String categoryOverride = data.getString("category", null);
            if (categoryOverride != null) {
                try {
                    ItemCategory overrideCat = ItemCategory.valueOf(categoryOverride.toUpperCase());
                    categoryOverrides.put(mat, overrideCat);
                } catch (IllegalArgumentException ignored) {
                    // Invalid category, ignore
                }
            }

            loaded++;
        }

        Bukkit.getLogger().info("[DynamicShop] Loaded " + loaded + " items from config.yml");
    }

    // ------------------------------------------------------------------------
    // BASE PRICE ACCESS
    // ------------------------------------------------------------------------
    public static double getBasePrice(Material mat) {
        return basePrices.getOrDefault(mat, -1.0);
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
        double base = basePrices.getOrDefault(mat, -1.0);
        if (base < 0)
            return false; // untradeable

        // Use cached config value instead of config lookups
        if (ConfigCacheManager.restrictBuyingAtZeroStock) {
            double stock = getStock(mat);

            if (stock <= 0) {
                plugin.getLogger()
                        .info("[Stock Restriction] Blocked purchase of " + mat.name() + " (stock: " + stock + ")");
                return false;
            }
        }

        return true;
    }

    /**
     * Core dynamic pricing formula.
     *
     * Takes a base price and adjusts it based on current stock.
     * - Low stock => price goes up toward maxMultiplier
     * - High stock => price goes down toward minMultiplier
     */
    // ============================================================================
    // TOTAL BUY COST (continuous pricing)
    // ∫ P(s) ds from s = s0 - amount → s0
    // ============================================================================
    public static double getTotalBuyCost(Material mat, double amount) {
        double B = getBasePrice(mat);
        if (B < 0)
            return -1.0;

        // Dynamic pricing disabled → simple linear pricing
        if (!ConfigCacheManager.dynamicPricingEnabled) {
            return B * amount;
        }

        double s0 = getStock(mat);
        double L = ConfigCacheManager.maxStock;
        double k = ConfigCacheManager.curveStrength;

        // Negative stock multiplier per item
        double negPercent = ConfigCacheManager.negativeStockPercentPerItem / 100.0;
        double q = 1.0 + negPercent;

        // Shortage inflation multiplier
        double hourlyIncrease = ConfigCacheManager.hourlyIncreasePercent / 100.0;
        double h = getHoursInShortage(mat);
        double t = Math.pow(1.0 + hourlyIncrease, h);

        double a = s0 - amount; // lower bound
        double b = s0; // upper bound

        double total = 0.0;

        // NEGATIVE REGION (-∞ → 0]
        double negA = Math.min(a, 0);
        double negB = Math.min(b, 0);
        if (negA < negB) {
            total += integrateNegativeRegion(B, q, t, negA, negB);
        }

        // MID REGION [0 → L]
        double midA = Math.max(a, 0);
        double midB = Math.min(b, L);
        if (midA < midB) {
            total += integrateMidPositiveRegion(B, k, L, midA, midB);
        }

        // HIGH REGION [L → +∞)
        double highA = Math.max(a, L);
        double highB = Math.max(b, L);
        if (highA < highB) {
            total += integrateHighPositiveRegion(B, k, highA, highB);
        }

        // Clamp to min/max price multiplier limits
        double maxTotal = B * amount * ConfigCacheManager.maxPriceMultiplier;
        double minTotal = B * amount * ConfigCacheManager.minPriceMultiplier;
        return Math.max(minTotal, Math.min(total, maxTotal));
    }

    // ============================================================================
    // TOTAL SELL VALUE (continuous pricing)
    // ∫ P(s) ds from s = s0 → s0 + amount then apply tax
    // ============================================================================
    public static double getTotalSellValue(Material mat, int amount) {
        double B = getBasePrice(mat);
        if (B < 0)
            return -1.0;

        // Dynamic pricing disabled
        if (!ConfigCacheManager.dynamicPricingEnabled) {
            return B * amount * (1.0 - ConfigCacheManager.sellTaxPercent);
        }

        double s0 = getStock(mat);
        double L = ConfigCacheManager.maxStock;
        double k = ConfigCacheManager.curveStrength;

        // Negative-stock multiplier
        double negPercent = ConfigCacheManager.negativeStockPercentPerItem / 100.0;
        double q = 1.0 + negPercent;

        // Shortage inflation
        double hourlyIncrease = ConfigCacheManager.hourlyIncreasePercent / 100.0;
        double h = getHoursInShortage(mat);
        double t = Math.pow(1.0 + hourlyIncrease, h);

        double a = s0; // lower bound
        double b = s0 + amount; // upper bound

        double total = 0.0;

        // NEGATIVE REGION
        double negA = Math.min(a, 0);
        double negB = Math.min(b, 0);
        if (negA < negB) {
            total += integrateNegativeRegion(B, q, t, negA, negB);
        }

        // MID REGION
        double midA = Math.max(a, 0);
        double midB = Math.min(b, L);
        if (midA < midB) {
            total += integrateMidPositiveRegion(B, k, L, midA, midB);
        }

        // HIGH REGION
        double highA = Math.max(a, L);
        double highB = Math.max(b, L);
        if (highA < highB) {
            total += integrateHighPositiveRegion(B, k, highA, highB);
        }

        // Clamp to min/max price multiplier limits (before tax)
        double maxTotal = B * amount * ConfigCacheManager.maxPriceMultiplier;
        double minTotal = B * amount * ConfigCacheManager.minPriceMultiplier;
        double clamped = Math.max(minTotal, Math.min(total, maxTotal));

        // FINAL SELL TAX
        double tax = ConfigCacheManager.sellTaxPercent;

        return Math.max(0.0, clamped * (1.0 - tax));
    }

    // ============================================================================
    // REGION INTEGRALS
    // ============================================================================

    // NEGATIVE REGION: price = B * t * q^(-s)
    private static double integrateNegativeRegion(double B, double q, double t, double a, double b) {
        // ∫ B*t * q^(-s) ds = B*t * ( q^(-a) - q^(-b) ) / ln(q)
        return B * t * (Math.pow(q, -a) - Math.pow(q, -b)) / Math.log(q);
    }

    // MID REGION: price = B * (1 - 0.5*k*(s/L))
    private static double integrateMidPositiveRegion(double B, double k, double L, double a, double b) {
        // ∫ B(1 - 0.5k s/L) ds = B[(b-a) - 0.5k(b² - a²)/(2L)]
        double term1 = (b - a);
        double term2 = 0.5 * k * (b * b - a * a) / (2 * L);
        return B * (term1 - term2);
    }

    // HIGH REGION: price = B * (1 - k) (flat)
    private static double integrateHighPositiveRegion(double B, double k, double a, double b) {
        return B * (1.0 - k) * (b - a);
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

        for (Material mat : basePrices.keySet()) {
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
            result = new ArrayList<>(basePrices.keySet());
        } else {
            result = new ArrayList<>(categoryItems.getOrDefault(cat, Collections.emptyList()));
        }

        // Filter out disabled items (base price < 0) for regular shop display
        result.removeIf(mat -> basePrices.getOrDefault(mat, -1.0) < 0);
        return result;
    }

    /**
     * Get items in category WITHOUT filtering disabled items (for admin GUI)
     */
    public static List<Material> getItemsInCategoryIncludeDisabled(ItemCategory cat) {
        if (cat == ItemCategory.MISC) {
            return new ArrayList<>(basePrices.keySet());
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
        double oldStock = stockMap.getOrDefault(mat, 0.0);
        stockMap.put(mat, stock);

        if (stock <= 0 && oldStock > 0) {
            lastUpdateMap.put(mat, System.currentTimeMillis());
        } else if (stock > 0 && oldStock <= 0) {
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
        double oldStock = getStock(mat);
        double newStock = oldStock + delta;
        stockMap.put(mat, newStock);

        if (newStock <= 0 && oldStock > 0) {
            lastUpdateMap.put(mat, System.currentTimeMillis());
        } else if (newStock > 0 && oldStock <= 0) {
            lastUpdateMap.put(mat, System.currentTimeMillis());
        }

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

        for (Material mat : basePrices.keySet()) {
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
            for (Material mat : basePrices.keySet()) {
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

        for (Material mat : basePrices.keySet()) {
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
    public static double getHoursInShortage(Material mat) {
        return shortageHoursMap.getOrDefault(mat, 0.0);
    }

    public static void setHoursInShortage(Material mat, double hours) {
        shortageHoursMap.put(mat, hours);
        markDirty(mat);
    }

    public static void addHoursInShortage(Material mat, double deltaHours) {
        shortageHoursMap.put(mat, getHoursInShortage(mat) + deltaHours);
        markDirty(mat);
    }

    public static double getShortageHours(Material mat) {
        return getHoursInShortage(mat);
    }

    // ------------------------------------------------------------------------
    // ITEM STATISTICS (for PlaceholderAPI)
    // ------------------------------------------------------------------------
    public static double getTotalStockValue() {
        double total = 0.0;
        for (Material mat : basePrices.keySet()) {
            double base = getBasePrice(mat);
            if (base <= 0)
                continue;
            total += getStock(mat) * base;
        }
        return total;
    }

    public static double getTotalPurchasesValue() {
        double total = 0.0;
        for (Material mat : basePrices.keySet()) {
            double base = getBasePrice(mat);
            if (base <= 0)
                continue;
            total += getPurchases(mat) * base;
        }
        return total;
    }

    public static int getTotalTrackedItems() {
        return basePrices.size();
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

            if (!basePrices.containsKey(mat))
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
        for (Material mat : basePrices.keySet()) {
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
        return basePrices.getOrDefault(mat, -1.0) < 0;
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
        basePrices.put(mat, price);

        // Save to config
        plugin.getConfig().set("items." + mat.name() + ".base", price);
        plugin.saveConfig();
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
