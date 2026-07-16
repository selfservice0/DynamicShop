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
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerShopViewGUI {
    private final DynamicShop plugin;
    private final Player viewer;
    private final UUID shopOwnerId;
    private final String shopOwnerName;
    private final Inventory inventory;
    private final Map<Integer, String> slotListingIds = new HashMap<>();
    private int currentPage = 0;

    // Item slots: rows 1-4, columns 1-7 (7 items per row, 4 rows = 28 items)
    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,  // row 1
        19, 20, 21, 22, 23, 24, 25,  // row 2
        28, 29, 30, 31, 32, 33, 34,  // row 3
        37, 38, 39, 40, 41, 42, 43   // row 4
    };
    private static final int ITEMS_PER_PAGE = ITEM_SLOTS.length; // 28
    private static final int GUI_SIZE = 54; // 6 rows

    public PlayerShopViewGUI(DynamicShop plugin, Player viewer, UUID shopOwnerId) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.shopOwnerId = shopOwnerId;
        this.shopOwnerName = plugin.getPlayerShopManager().getPlayerName(shopOwnerId);

        java.util.Map<String, String> titlePlaceholders = new java.util.HashMap<>();
        titlePlaceholders.put("player", viewer.getUniqueId().equals(shopOwnerId) ? "Your" : shopOwnerName);
        String title = plugin.getMessageManager().getMessage("player-shop-view-title", titlePlaceholders);
        if (title == null) title = "§6§l" + shopOwnerName + "'s Shop";

        this.inventory = org.minecraftsmp.dynamicshop.util.PaperCompat.createInventory(null, GUI_SIZE,
                MessageManager.parseComponent(title, viewer));

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
        slotListingIds.clear();

        // Fill borders: top row + side columns
        ItemStack filler = org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.getFillerItem();
        // Top row
        for (int col = 0; col < 9; col++) {
            inventory.setItem(col, filler);
        }
        // Side columns (rows 1-4)
        for (int row = 1; row < 5; row++) {
            inventory.setItem(row * 9, filler);     // left
            inventory.setItem(row * 9 + 8, filler); // right
        }

        PlayerShopManager manager = plugin.getPlayerShopManager();
        List<PlayerShopListing> listings = manager.getListings(shopOwnerId);

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, listings.size());

        boolean isOwnShop = viewer.getUniqueId().equals(shopOwnerId);

        // Display items in bordered slots
        for (int i = startIndex; i < endIndex; i++) {
            PlayerShopListing listing = listings.get(i);
            ItemStack displayItem = createDisplayItem(listing, isOwnShop);
            int slot = ITEM_SLOTS[i - startIndex];
            inventory.setItem(slot, displayItem);
            slotListingIds.put(slot, listing.getListingId());
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
        // - Everything
        ItemStack displayItem = listing.getItem().clone();
        ItemMeta meta = displayItem.getItemMeta();

        if (meta != null) {
            // Preserve original custom name if it exists
            // Otherwise use material name
            if (!meta.hasDisplayName()) {
                String materialName = displayItem.getType().toString().replace("_", " ");
                materialName = capitalizeWords(materialName);
                org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent("§f" + materialName));
            }
            // If it has a custom name, keep it exactly as-is!

            // Get existing lore or create new list (using modern lore() which returns
            // List<Component>)
            List<net.kyori.adventure.text.Component> lore = meta.hasLore() ? new ArrayList<>(org.minecraftsmp.dynamicshop.util.PaperCompat.getLore(meta))
                    : new ArrayList<>();

            // Add shop info AFTER existing lore (preserve original lore)
            lore.add(MessageManager.parseComponent(""));
            lore.add(MessageManager.parseComponent("§7Price: §e$" + String.format("%.2f", listing.getPrice())));
            lore.add(MessageManager.parseComponent("§7Amount: §f" + displayItem.getAmount()));

            // Show durability if applicable (using modern Damageable API)
            if (displayItem.getType().getMaxDurability() > 0 && meta instanceof org.bukkit.inventory.meta.Damageable) {
                int maxDurability = displayItem.getType().getMaxDurability();
                int damage = ((org.bukkit.inventory.meta.Damageable) meta).getDamage();
                int remaining = maxDurability - damage;
                int percent = (int) ((remaining / (double) maxDurability) * 100);
                lore.add(MessageManager.parseComponent(
                        "§7Durability: §f" + remaining + "/" + maxDurability + " §7(" + percent + "%)"));
            }

            // Show enchantments count if present
            if (meta.hasEnchants()) {
                lore.add(MessageManager.parseComponent(
                        "§7Enchantments: §b" + meta.getEnchants().size()));
            }

            // Show listing time
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm");
            lore.add(MessageManager.parseComponent("§7Listed: §f" + sdf.format(new Date(listing.getListedTime()))));

            lore.add(MessageManager.parseComponent(""));
            if (isOwnShop) {
                lore.add(MessageManager.parseComponent("§a§lYour Listing"));
                lore.add(MessageManager.parseComponent("§eClick to reclaim"));
            } else {
                lore.add(MessageManager.parseComponent("§eClick to purchase"));
            }

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore);
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
                org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(prevMeta, MessageManager.parseComponent("§e◀ Previous Page"));
                prevPage.setItemMeta(prevMeta);
            }
            inventory.setItem(48, prevPage);
        }

        // Page indicator
        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        if (pageInfoMeta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(pageInfoMeta, MessageManager.parseComponent("§7Page " + (currentPage + 1) + " / " + (maxPage + 1)));
            List<String> lore = new ArrayList<>();
            lore.add("§7Total listings: §f" + totalItems);
            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(pageInfoMeta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            pageInfo.setItemMeta(pageInfoMeta);
        }
        inventory.setItem(49, pageInfo);

        // Next page
        if (currentPage < maxPage) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            if (nextMeta != null) {
                org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(nextMeta, MessageManager.parseComponent("§eNext Page ▶"));
                nextPage.setItemMeta(nextMeta);
            }
            inventory.setItem(50, nextPage);
        }

        // Back button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(backMeta, MessageManager.parseComponent("§c◀ Back to Shops"));
            back.setItemMeta(backMeta);
        }
        inventory.setItem(45, back);

        // Shop stats
        ItemStack stats = new ItemStack(isOwnShop ? Material.EMERALD : Material.GOLD_INGOT);
        ItemMeta statsMeta = stats.getItemMeta();
        if (statsMeta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(statsMeta, MessageManager.parseComponent(isOwnShop ? "§a§lYour Shop" : "§e§l" + shopOwnerName));
            List<String> lore = new ArrayList<>();
            lore.add("§7Total items: §f" + totalItems);
            if (isOwnShop) {
                lore.add("");
                lore.add("§7Click items to reclaim them");
            }
            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(statsMeta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            stats.setItemMeta(statsMeta);
        }
        inventory.setItem(53, stats);
    }

    /**
     * Get the listing at the clicked slot
     */
    public PlayerShopListing getListingAtSlot(int slot) {
        String listingId = slotListingIds.get(slot);
        return listingId != null ? plugin.getPlayerShopManager().getListing(listingId) : null;
    }

    public UUID getShopOwnerId() {
        return shopOwnerId;
    }
}
