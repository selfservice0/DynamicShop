package org.minecraftsmp.dynamicshop.managers;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manager for category configuration persistence.
 * Handles custom slot positions, icons, and display names for categories.
 */
public class CategoryConfigManager {

    private static DynamicShop plugin;
    private static File configFile;
    private static YamlConfiguration config;

    private static final Map<ItemCategory, Integer> categorySlots = new HashMap<>();
    private static final Map<ItemCategory, Material> categoryIcons = new HashMap<>();
    private static final Map<ItemCategory, String> categoryNames = new HashMap<>();

    // Default slot positions (same as original CategorySelectionGUI layout)
    // Custom categories default to -1 (hidden) until admin assigns a slot
    private static final Map<ItemCategory, Integer> DEFAULT_SLOTS = new HashMap<>();
    static {
        DEFAULT_SLOTS.put(ItemCategory.MISC, 11);
        DEFAULT_SLOTS.put(ItemCategory.BLOCKS, 12);
        DEFAULT_SLOTS.put(ItemCategory.REDSTONE, 13);
        DEFAULT_SLOTS.put(ItemCategory.TOOLS, 14);
        DEFAULT_SLOTS.put(ItemCategory.ARMOR, 15);
        DEFAULT_SLOTS.put(ItemCategory.FOOD, 20);
        DEFAULT_SLOTS.put(ItemCategory.FARMING, 21);
        DEFAULT_SLOTS.put(ItemCategory.WOOD, 22);
        DEFAULT_SLOTS.put(ItemCategory.PERMISSIONS, 23);
        DEFAULT_SLOTS.put(ItemCategory.SERVER_SHOP, 24);
        DEFAULT_SLOTS.put(ItemCategory.PLAYER_SHOPS, 31);
        // Custom categories hidden by default (-1)
        for (ItemCategory cat : ItemCategory.values()) {
            if (cat.isCustomCategory()) {
                DEFAULT_SLOTS.put(cat, -1);
            }
        }
    }

    /**
     * Initialize the category config manager
     */
    public static void init(DynamicShop pl) {
        plugin = pl;
        configFile = new File(plugin.getDataFolder(), "categories.yml");
        load();
    }

    /**
     * Load category settings from categories.yml
     */
    public static void load() {
        categorySlots.clear();
        categoryIcons.clear();
        categoryNames.clear();

        if (!configFile.exists()) {
            // Create default config
            saveDefaults();
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        ConfigurationSection categoriesSection = config.getConfigurationSection("categories");
        if (categoriesSection == null) {
            // Use defaults
            categorySlots.putAll(DEFAULT_SLOTS);
            return;
        }

        for (ItemCategory category : ItemCategory.values()) {
            String key = category.name();
            ConfigurationSection catSection = categoriesSection.getConfigurationSection(key);

            if (catSection != null) {
                // Load slot
                if (catSection.contains("slot")) {
                    categorySlots.put(category, catSection.getInt("slot"));
                } else {
                    categorySlots.put(category, DEFAULT_SLOTS.getOrDefault(category, -1));
                }

                // Load icon override
                if (catSection.contains("icon")) {
                    String iconStr = catSection.getString("icon");
                    if (iconStr != null) {
                        try {
                            Material icon = Material.valueOf(iconStr.toUpperCase());
                            categoryIcons.put(category, icon);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid icon material for category " + key);
                        }
                    }
                }

                // Load name override
                if (catSection.contains("name")) {
                    categoryNames.put(category, catSection.getString("name"));
                }
            } else {
                // Use default slot
                categorySlots.put(category, DEFAULT_SLOTS.getOrDefault(category, -1));
            }
        }
    }

    /**
     * Save category settings to categories.yml
     */
    public static void save() {
        if (config == null) {
            config = new YamlConfiguration();
        }

        for (ItemCategory category : ItemCategory.values()) {
            String path = "categories." + category.name();

            // Save slot
            config.set(path + ".slot", categorySlots.getOrDefault(category,
                    DEFAULT_SLOTS.getOrDefault(category, -1)));

            // Save icon if overridden
            if (categoryIcons.containsKey(category)) {
                config.set(path + ".icon", categoryIcons.get(category).name());
            }

            // Save name if overridden
            if (categoryNames.containsKey(category)) {
                config.set(path + ".name", categoryNames.get(category));
            }
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save categories.yml", e);
        }
    }

    /**
     * Save default configuration
     */
    private static void saveDefaults() {
        config = new YamlConfiguration();

        config.options().setHeader(java.util.List.of(
                "Category Configuration",
                "Customize display order, icons, and names",
                "",
                "slot: The inventory slot position (0-44 for a 54-slot GUI, -1 = hidden)",
                "icon: Override the default icon (optional, must be a valid material name)",
                "name: Override the default display name (optional)",
                "",
                "CUSTOM_1 through CUSTOM_20 are placeholder categories you can enable",
                "by setting their slot to a valid position and customizing name/icon"));

        for (ItemCategory category : ItemCategory.values()) {
            String path = "categories." + category.name();
            config.set(path + ".slot", DEFAULT_SLOTS.getOrDefault(category, -1));
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create categories.yml", e);
        }
    }

    /**
     * Get the slot position for a category
     */
    public static int getSlot(ItemCategory category) {
        return categorySlots.getOrDefault(category, DEFAULT_SLOTS.getOrDefault(category, 0));
    }

    /**
     * Get the icon for a category (returns override or default from enum)
     */
    public static Material getIcon(ItemCategory category) {
        return categoryIcons.getOrDefault(category, category.getIcon());
    }

    /**
     * Get the display name for a category (returns override or default from enum)
     */
    public static String getDisplayName(ItemCategory category) {
        return categoryNames.getOrDefault(category, category.getDisplayName());
    }

    /**
     * Set the slot position for a category
     */
    public static void setSlot(ItemCategory category, int slot) {
        categorySlots.put(category, slot);
    }

    /**
     * Set a custom icon for a category
     */
    public static void setIcon(ItemCategory category, Material icon) {
        categoryIcons.put(category, icon);
    }

    /**
     * Set a custom display name for a category
     */
    public static void setDisplayName(ItemCategory category, String name) {
        categoryNames.put(category, name);
    }

    /**
     * Remove custom icon override (revert to default)
     */
    public static void removeIcon(ItemCategory category) {
        categoryIcons.remove(category);
    }

    /**
     * Remove custom display name override (revert to default)
     */
    public static void removeDisplayName(ItemCategory category) {
        categoryNames.remove(category);
    }

    /**
     * Swap slot positions between two categories
     */
    public static void swapSlots(ItemCategory cat1, ItemCategory cat2) {
        int slot1 = getSlot(cat1);
        int slot2 = getSlot(cat2);
        categorySlots.put(cat1, slot2);
        categorySlots.put(cat2, slot1);
    }

    /**
     * Get the category at a specific slot, or null if none
     */
    public static ItemCategory getCategoryAtSlot(int slot) {
        for (Map.Entry<ItemCategory, Integer> entry : categorySlots.entrySet()) {
            if (entry.getValue() == slot) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Check if a slot is occupied by a category
     */
    public static boolean isSlotOccupied(int slot) {
        return categorySlots.containsValue(slot);
    }

    /**
     * Get all category slots
     */
    public static Map<ItemCategory, Integer> getAllSlots() {
        return new HashMap<>(categorySlots);
    }

    /**
     * Get the next available custom category (one with slot = -1, i.e. hidden).
     * Returns null if all 10 custom categories are already in use.
     */
    public static ItemCategory getNextAvailableCustomCategory() {
        for (ItemCategory cat : ItemCategory.values()) {
            if (cat.isCustomCategory() && getSlot(cat) == -1) {
                return cat;
            }
        }
        return null;
    }
}
