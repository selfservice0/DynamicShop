package org.minecraftsmp.dynamicshop.managers;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.models.PlayerShopListing;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlayerShopManager {
    private final DynamicShop plugin;
    private final File dataFile;
    private final Map<UUID, List<PlayerShopListing>> playerListings; // sellerId -> listings

    public PlayerShopManager(DynamicShop plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "player_shops.yml");
        this.playerListings = new HashMap<>();
        loadListings();
    }

    /**
     * Add a listing to a player's shop
     */
    public boolean addListing(Player seller, ItemStack item, double price) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        if (price <= 0) {
            return false;
        }

        UUID sellerId = seller.getUniqueId();
        PlayerShopListing listing = new PlayerShopListing(
                sellerId,
                seller.getName(),
                item,
                price);

        playerListings.computeIfAbsent(sellerId, k -> new ArrayList<>()).add(listing);
        saveListings();

        plugin.getLogger().info("[PlayerShops] " + seller.getName() + " listed " +
                item.getType() + " x" + item.getAmount() + " for $" + price);

        return true;
    }

    /**
     * Remove a listing (when bought or reclaimed)
     */
    public boolean removeListing(String listingId) {
        for (List<PlayerShopListing> listings : playerListings.values()) {
            PlayerShopListing toRemove = null;
            for (PlayerShopListing listing : listings) {
                if (listing.getListingId().equals(listingId)) {
                    toRemove = listing;
                    break;
                }
            }
            if (toRemove != null) {
                listings.remove(toRemove);
                saveListings();

                // Remove empty shops
                cleanupEmptyShops();
                return true;
            }
        }
        return false;
    }

    /**
     * Get all listings for a specific seller
     */
    public List<PlayerShopListing> getListings(UUID sellerId) {
        return new ArrayList<>(playerListings.getOrDefault(sellerId, new ArrayList<>()));
    }

    /**
     * Get a specific listing by ID
     */
    public PlayerShopListing getListing(String listingId) {
        for (List<PlayerShopListing> listings : playerListings.values()) {
            for (PlayerShopListing listing : listings) {
                if (listing.getListingId().equals(listingId)) {
                    return listing;
                }
            }
        }
        return null;
    }

    /**
     * Get all players who have active shops
     */
    public List<UUID> getActiveShopOwners() {
        return new ArrayList<>(playerListings.keySet());
    }

    /**
     * Get total number of listings for a player
     */
    public int getListingCount(UUID sellerId) {
        List<PlayerShopListing> listings = playerListings.get(sellerId);
        return listings == null ? 0 : listings.size();
    }

    /**
     * Get player name from UUID (checks online players first, then saved data)
     */
    public String getPlayerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }

        // Check saved listings for name
        List<PlayerShopListing> listings = playerListings.get(playerId);
        if (listings != null && !listings.isEmpty()) {
            return listings.get(0).getSellerName();
        }

        return "Unknown";
    }

    /**
     * Remove shops with no items
     */
    private void cleanupEmptyShops() {
        playerListings.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Save all listings to file
     * Uses ItemStack serialization which preserves:
     * - Enchantments
     * - Custom names and lore
     * - Durability/damage
     * - All NBT/component data
     * - Attributes
     * - Everything!
     */
    private void saveListings() {
        YamlConfiguration config = new YamlConfiguration();

        int index = 0;
        for (Map.Entry<UUID, List<PlayerShopListing>> entry : playerListings.entrySet()) {
            UUID sellerId = entry.getKey();
            List<PlayerShopListing> listings = entry.getValue();

            for (PlayerShopListing listing : listings) {
                String path = "listings." + index;
                config.set(path + ".seller_uuid", sellerId.toString());
                config.set(path + ".seller_name", listing.getSellerName());

                // Bukkit's YAML serialization automatically preserves:
                // - All enchantments (including levels)
                // - Custom display names
                // - Lore (all lines)
                // - Durability/damage values
                // - Unbreakable flag
                // - Custom model data
                // - All attribute modifiers
                // - Potion effects
                // - Book contents
                // - Leather armor colors
                // - Skull owners
                // - Item flags
                // - Everything stored in ItemMeta
                config.set(path + ".item", listing.getItem());

                config.set(path + ".price", listing.getPrice());
                config.set(path + ".listed_time", listing.getListedTime());
                config.set(path + ".listing_id", listing.getListingId());
                index++;
            }
        }

        try {
            config.save(dataFile);
            plugin.getLogger().fine("[PlayerShops] Saved " + index + " listings with full item data");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player shops: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load all listings from file
     * Bukkit's ItemStack deserialization automatically restores:
     * - All enchantments with correct levels
     * - Custom names and lore exactly as they were
     * - Durability/damage values
     * - All NBT/component data
     * - Everything!
     */
    private void loadListings() {
        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection listingsSection = config.getConfigurationSection("listings");

        if (listingsSection == null) {
            return;
        }

        int loaded = 0;
        int failed = 0;

        for (String key : listingsSection.getKeys(false)) {
            try {
                String path = "listings." + key;
                UUID sellerId = UUID.fromString(config.getString(path + ".seller_uuid"));
                String sellerName = config.getString(path + ".seller_name");

                // Load ItemStack - this preserves EVERYTHING
                ItemStack item = config.getItemStack(path + ".item");

                double price = config.getDouble(path + ".price");
                long listedTime = config.getLong(path + ".listed_time");
                String listingId = config.getString(path + ".listing_id");

                if (item != null && item.getType() != Material.AIR) {
                    // Verify item data integrity
                    if (item.hasItemMeta()) {
                        ItemMeta meta = item.getItemMeta();
                        plugin.getLogger().fine("[PlayerShops] Loaded item with meta: " +
                                (meta.hasDisplayName()
                                        ? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                                        : item.getType())
                                +
                                (meta.hasEnchants() ? " with " + meta.getEnchants().size() + " enchants" : ""));
                    }

                    PlayerShopListing listing = new PlayerShopListing(
                            sellerId, sellerName, item, price, listedTime, listingId);

                    playerListings.computeIfAbsent(sellerId, k -> new ArrayList<>()).add(listing);
                    loaded++;
                } else {
                    plugin.getLogger().warning("[PlayerShops] Skipped invalid item in listing " + key);
                    failed++;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load listing " + key + ": " + e.getMessage());
                failed++;
            }
        }

        plugin.getLogger().info("[PlayerShops] Loaded " + loaded + " listings from " +
                playerListings.size() + " shops" +
                (failed > 0 ? " (" + failed + " failed)" : ""));
    }

    /**
     * Get all listings (for admin purposes)
     */
    public List<PlayerShopListing> getAllListings() {
        List<PlayerShopListing> all = new ArrayList<>();
        for (List<PlayerShopListing> listings : playerListings.values()) {
            all.addAll(listings);
        }
        return all;
    }
}