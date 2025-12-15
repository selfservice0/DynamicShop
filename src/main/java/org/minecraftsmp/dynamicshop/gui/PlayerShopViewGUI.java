package org.minecraftsmp.dynamicshop.gui;

import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.managers.PlayerShopManager;
import org.minecraftsmp.dynamicshop.models.PlayerShopListing;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class PlayerShopViewGUI {
    private final DynamicShop plugin;
    private final Player viewer;
    private final UUID shopOwnerId;
    private final String shopOwnerName;
    private final Inventory inventory;
    private int currentPage = 0;

    private static final int ITEMS_PER_PAGE = 45; // 5 rows of 9
    private static final int GUI_SIZE = 54; // 6 rows

    public PlayerShopViewGUI(DynamicShop plugin, Player viewer, UUID shopOwnerId) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.shopOwnerId = shopOwnerId;
        this.shopOwnerName = plugin.getPlayerShopManager().getPlayerName(shopOwnerId);

        String title = viewer.getUniqueId().equals(shopOwnerId)
                ? "§6§lYour Shop"
                : "§6§l" + shopOwnerName + "'s Shop";

        this.inventory = Bukkit.createInventory(null, GUI_SIZE, title);

        refreshPage();
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void nextPage() {
        List<PlayerShopListing> listings = plugin.getPlayerShopManager().getListings(shopOwnerId);
        int maxPage = (int) Math.ceil(listings.size() / (double) ITEMS_PER_PAGE) - 1;

        if (currentPage < maxPage) {
            currentPage++;
            refreshPage();
        }
    }

    public void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            refreshPage();
        }
    }

    private void refreshPage() {
        inventory.clear();

        PlayerShopManager manager = plugin.getPlayerShopManager();
        List<PlayerShopListing> listings = manager.getListings(shopOwnerId);

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, listings.size());

        boolean isOwnShop = viewer.getUniqueId().equals(shopOwnerId);

        // Display items
        for (int i = startIndex; i < endIndex; i++) {
            PlayerShopListing listing = listings.get(i);
            ItemStack displayItem = createDisplayItem(listing, isOwnShop);
            inventory.setItem(i - startIndex, displayItem);
        }

        // Navigation items at bottom row
        addNavigationItems(listings.size(), isOwnShop);
    }

    private ItemStack createDisplayItem(PlayerShopListing listing, boolean isOwnShop) {
        // Clone the item - this preserves ALL data:
        // - Enchantments (all levels)
        // - Custom names
        // - Lore
        // - Durability
        // - Attributes
        // - Everything!
        ItemStack displayItem = listing.getItem().clone();
        ItemMeta meta = displayItem.getItemMeta();

        if (meta != null) {
            // Preserve original custom name if it exists
            // Otherwise use material name
            if (!meta.hasDisplayName()) {
                String materialName = displayItem.getType().toString().replace("_", " ");
                materialName = capitalizeWords(materialName);
                meta.setDisplayName("§f" + materialName);
            }
            // If it has a custom name, keep it exactly as-is!

            // Get existing lore or create new list
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

            // Add shop info AFTER existing lore (preserve original lore)
            lore.add("");
            lore.add("§7Price: §e$" + String.format("%.2f", listing.getPrice()));
            lore.add("§7Amount: §f" + displayItem.getAmount());

            // Show durability if applicable (using modern Damageable API)
            if (displayItem.getType().getMaxDurability() > 0 && meta instanceof org.bukkit.inventory.meta.Damageable) {
                int maxDurability = displayItem.getType().getMaxDurability();
                int damage = ((org.bukkit.inventory.meta.Damageable) meta).getDamage();
                int remaining = maxDurability - damage;
                int percent = (int) ((remaining / (double) maxDurability) * 100);
                lore.add("§7Durability: §f" + remaining + "/" + maxDurability + " §7(" + percent + "%)");
            }

            // Show enchantments count if present
            if (meta.hasEnchants()) {
                lore.add("§7Enchantments: §b" + meta.getEnchants().size());
            }

            // Show listing time
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm");
            lore.add("§7Listed: §f" + sdf.format(new Date(listing.getListedTime())));

            lore.add("");
            if (isOwnShop) {
                lore.add("§a§lYour Listing");
                lore.add("§eClick to reclaim");
            } else {
                lore.add("§eClick to purchase");
            }

            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }

        return displayItem;
    }

    private String capitalizeWords(String str) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : str.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }

    private void addNavigationItems(int totalItems, boolean isOwnShop) {
        int maxPage = (int) Math.ceil(totalItems / (double) ITEMS_PER_PAGE) - 1;

        // Previous page
        if (currentPage > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName("§e◀ Previous Page");
                prevPage.setItemMeta(prevMeta);
            }
            inventory.setItem(48, prevPage);
        }

        // Page indicator
        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        if (pageInfoMeta != null) {
            pageInfoMeta.setDisplayName("§7Page " + (currentPage + 1) + " / " + (maxPage + 1));
            List<String> lore = new ArrayList<>();
            lore.add("§7Total listings: §f" + totalItems);
            pageInfoMeta.setLore(lore);
            pageInfo.setItemMeta(pageInfoMeta);
        }
        inventory.setItem(49, pageInfo);

        // Next page
        if (currentPage < maxPage) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName("§eNext Page ▶");
                nextPage.setItemMeta(nextMeta);
            }
            inventory.setItem(50, nextPage);
        }

        // Back button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c◀ Back to Shops");
            back.setItemMeta(backMeta);
        }
        inventory.setItem(45, back);

        // Shop stats
        ItemStack stats = new ItemStack(isOwnShop ? Material.EMERALD : Material.GOLD_INGOT);
        ItemMeta statsMeta = stats.getItemMeta();
        if (statsMeta != null) {
            statsMeta.setDisplayName(isOwnShop ? "§a§lYour Shop" : "§e§l" + shopOwnerName);
            List<String> lore = new ArrayList<>();
            lore.add("§7Total items: §f" + totalItems);
            if (isOwnShop) {
                lore.add("");
                lore.add("§7Click items to reclaim them");
            }
            statsMeta.setLore(lore);
            stats.setItemMeta(statsMeta);
        }
        inventory.setItem(53, stats);
    }

    /**
     * Get the listing at the clicked slot
     */
    public PlayerShopListing getListingAtSlot(int slot) {
        if (slot >= ITEMS_PER_PAGE) {
            return null;
        }

        List<PlayerShopListing> listings = plugin.getPlayerShopManager().getListings(shopOwnerId);
        int actualIndex = currentPage * ITEMS_PER_PAGE + slot;

        if (actualIndex >= 0 && actualIndex < listings.size()) {
            return listings.get(actualIndex);
        }

        return null;
    }

    public UUID getShopOwnerId() {
        return shopOwnerId;
    }
}