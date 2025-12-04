package org.minecraftsmp.dynamicshop.gui;

import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.managers.PlayerShopManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerShopBrowserGUI {
    private final DynamicShop plugin;
    private final Player viewer;
    private final Inventory inventory;
    private int currentPage = 0;

    private static final int SHOPS_PER_PAGE = 45; // 5 rows of 9
    private static final int GUI_SIZE = 54; // 6 rows

    public PlayerShopBrowserGUI(DynamicShop plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(null, GUI_SIZE, "§6§lPlayer Shops");

        refreshPage();
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void nextPage() {
        List<UUID> shops = plugin.getPlayerShopManager().getActiveShopOwners();
        int maxPage = (int) Math.ceil(shops.size() / (double) SHOPS_PER_PAGE) - 1;

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
        List<UUID> shopOwners = manager.getActiveShopOwners();

        // Sort by item count (most items first)
        shopOwners.sort((a, b) ->
                Integer.compare(manager.getListingCount(b), manager.getListingCount(a))
        );

        int startIndex = currentPage * SHOPS_PER_PAGE;
        int endIndex = Math.min(startIndex + SHOPS_PER_PAGE, shopOwners.size());

        // Display shop owner heads
        for (int i = startIndex; i < endIndex; i++) {
            UUID ownerId = shopOwners.get(i);
            String ownerName = manager.getPlayerName(ownerId);
            int itemCount = manager.getListingCount(ownerId);

            ItemStack head = createPlayerHead(ownerId, ownerName, itemCount);
            inventory.setItem(i - startIndex, head);
        }

        // Navigation items at bottom row
        addNavigationItems(shopOwners.size());
    }

    private ItemStack createPlayerHead(UUID playerId, String playerName, int itemCount) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null) {
            // Try to get the player's head
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                meta.setOwningPlayer(player);
            } else {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerId));
            }

            meta.setDisplayName("§e§l" + playerName + "'s Shop");

            List<String> lore = new ArrayList<>();
            lore.add("§7Items for sale: §f" + itemCount);
            lore.add("");

            if (viewer.getUniqueId().equals(playerId)) {
                lore.add("§a§lYour Shop");
                lore.add("§7Click to view/manage");
            } else {
                lore.add("§eClick to browse");
            }

            meta.setLore(lore);
            head.setItemMeta(meta);
        }

        return head;
    }

    private void addNavigationItems(int totalShops) {
        int maxPage = (int) Math.ceil(totalShops / (double) SHOPS_PER_PAGE) - 1;

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
            lore.add("§7Total shops: §f" + totalShops);
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

        // Back to main shop
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c◀ Back to Shop");
            back.setItemMeta(backMeta);
        }
        inventory.setItem(45, back);

        // Info/Help
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§b§lHow to Sell");
            List<String> lore = new ArrayList<>();
            lore.add("§7Hold an item and type:");
            lore.add("§e/shop sell <price>");
            lore.add("");
            lore.add("§7Example:");
            lore.add("§f/shop sell 100");
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
        }
        inventory.setItem(53, info);
    }

    /**
     * Get the shop owner UUID from the clicked slot
     */
    public UUID getShopOwnerAtSlot(int slot) {
        if (slot >= SHOPS_PER_PAGE) {
            return null;
        }

        PlayerShopManager manager = plugin.getPlayerShopManager();
        List<UUID> shopOwners = manager.getActiveShopOwners();

        // Sort same way as display
        shopOwners.sort((a, b) ->
                Integer.compare(manager.getListingCount(b), manager.getListingCount(a))
        );

        int actualIndex = currentPage * SHOPS_PER_PAGE + slot;

        if (actualIndex >= 0 && actualIndex < shopOwners.size()) {
            return shopOwners.get(actualIndex);
        }

        return null;
    }
}