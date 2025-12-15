package org.minecraftsmp.dynamicshop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.util.ShopItemBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin-only GUI for browsing shop items.
 * Similar to ShopGUI but with admin controls instead of buy/sell.
 * Right-click on any item opens the AdminItemEditGUI.
 */
public class AdminShopBrowseGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final Inventory inventory;

    private static final int SIZE = 54;
    private static final int ITEMS_PER_PAGE = SIZE - 9; // 45 items, bottom row for navigation

    private ItemCategory currentCategory;
    private List<Material> items;
    private int page = 0;
    private int maxPage = 0;

    public AdminShopBrowseGUI(DynamicShop plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.currentCategory = ItemCategory.BLOCKS; // Default category
        this.inventory = Bukkit.createInventory(null, SIZE, "§4§lAdmin Shop");

        loadItemsForCategory();
    }

    private void loadItemsForCategory() {
        // Skip special categories - they have their own management
        if (currentCategory == ItemCategory.PERMISSIONS ||
                currentCategory == ItemCategory.SERVER_SHOP ||
                currentCategory == ItemCategory.PLAYER_SHOPS) {
            this.items = List.of();
        } else {
            this.items = ShopDataManager.getItemsInCategoryIncludeDisabled(currentCategory);
            if (items == null)
                items = List.of();
        }

        this.page = 0;
        this.maxPage = items.isEmpty() ? 0 : (items.size() - 1) / ITEMS_PER_PAGE;
    }

    public void open() {
        render();
        player.openInventory(inventory);
    }

    public void render() {
        inventory.clear();

        // Render items for current page
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());

        for (int i = start; i < end; i++) {
            Material mat = items.get(i);
            int slot = i - start;
            inventory.setItem(slot, buildAdminItem(mat));
        }

        // Render navigation bar
        renderNavigation();
    }

    private ItemStack buildAdminItem(Material mat) {
        double basePrice = ShopDataManager.getBasePrice(mat);
        double stock = ShopDataManager.getStock(mat);
        boolean disabled = ShopDataManager.isItemDisabled(mat);
        ItemCategory category = ShopDataManager.detectCategory(mat);

        ItemStack item;
        try {
            item = new ItemStack(mat, 1);
        } catch (IllegalArgumentException e) {
            item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§c§lINVALID: " + mat.name());
                item.setItemMeta(meta);
            }
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Show disabled status in name
            String displayName = mat.name().replace("_", " ");
            if (disabled) {
                meta.setDisplayName("§c§m" + displayName + " §7(DISABLED)");
            } else {
                meta.setDisplayName("§a§l" + displayName);
            }

            List<String> lore = new ArrayList<>();
            lore.add("§7───────────────────");
            lore.add("§7Base Price: §e$" + String.format("%.2f", basePrice));
            lore.add("§7Stock: §f" + String.format("%.0f", stock));
            lore.add("§7Category: §b" + category.getDisplayName());
            lore.add("§7Status: " + (disabled ? "§c✗ Disabled" : "§a✓ Enabled"));
            lore.add("§7───────────────────");
            lore.add("");
            lore.add("§e§lRight-click to EDIT");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private void renderNavigation() {
        int navRow = SIZE - 9;

        // Fill navigation row with glass panes first
        ItemStack filler = createFiller();
        for (int i = 0; i < 9; i++) {
            inventory.setItem(navRow + i, filler);
        }

        // Previous Page (slot 0)
        if (page > 0) {
            inventory.setItem(navRow, ShopItemBuilder.navItem(
                    "§e◀ Previous Page",
                    Material.ARROW,
                    "§7Page " + page + " of " + (maxPage + 1)));
        }

        // Next Page (slot 8)
        if (page < maxPage) {
            inventory.setItem(navRow + 8, ShopItemBuilder.navItem(
                    "§eNext Page ▶",
                    Material.ARROW,
                    "§7Page " + (page + 2) + " of " + (maxPage + 1)));
        }

        // Category selector (slot 3)
        inventory.setItem(navRow + 3, createCategorySelector());

        // Page info (slot 4)
        inventory.setItem(navRow + 4, ShopItemBuilder.navItem(
                "§7Page §f" + (page + 1) + " §7/ §f" + (maxPage + 1),
                Material.PAPER,
                "§7Items: §e" + items.size()));

        // Close button (slot 5)
        inventory.setItem(navRow + 5, ShopItemBuilder.navItem(
                "§c§lClose",
                Material.BARRIER,
                "§7Close this menu"));
    }

    private ItemStack createCategorySelector() {
        ItemStack item = new ItemStack(currentCategory.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lCategory: " + currentCategory.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add("§7───────────────────");

            // List all categories, highlight current
            for (ItemCategory cat : ItemCategory.values()) {
                if (cat == ItemCategory.PERMISSIONS ||
                        cat == ItemCategory.SERVER_SHOP ||
                        cat == ItemCategory.PLAYER_SHOPS) {
                    continue; // Skip special categories
                }

                if (cat == currentCategory) {
                    lore.add("§a▶ " + cat.getDisplayName());
                } else {
                    lore.add("§7  " + cat.getDisplayName());
                }
            }

            lore.add("§7───────────────────");
            lore.add("§eClick to cycle category");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        return filler;
    }

    /**
     * Handle click events in this GUI
     */
    public void handleClick(int slot, boolean isRightClick) {
        // Navigation row
        if (slot >= SIZE - 9) {
            int navSlot = slot - (SIZE - 9);

            switch (navSlot) {
                case 0 -> prevPage(); // Previous page
                case 8 -> nextPage(); // Next page
                case 3 -> cycleCategory(); // Category selector
                case 5 -> player.closeInventory(); // Close
            }
            return;
        }

        // Item slot - only handle right-click
        if (!isRightClick)
            return;

        Material mat = getItemFromSlot(slot);
        if (mat == null)
            return;

        // Open the item editor GUI
        plugin.getShopListener().unregisterAdminBrowse(player); // Unregister browse first
        AdminItemEditGUI editGUI = new AdminItemEditGUI(plugin, player, mat, this);
        plugin.getShopListener().registerAdminEdit(player, editGUI);
        editGUI.open();
    }

    private void nextPage() {
        if (page < maxPage) {
            page++;
            render();
        }
    }

    private void prevPage() {
        if (page > 0) {
            page--;
            render();
        }
    }

    private void cycleCategory() {
        ItemCategory[] categories = ItemCategory.values();
        int currentIndex = currentCategory.ordinal();

        // Find next valid category (skip special ones)
        do {
            currentIndex = (currentIndex + 1) % categories.length;
        } while (categories[currentIndex] == ItemCategory.PERMISSIONS ||
                categories[currentIndex] == ItemCategory.SERVER_SHOP ||
                categories[currentIndex] == ItemCategory.PLAYER_SHOPS);

        currentCategory = categories[currentIndex];
        loadItemsForCategory();
        render();
    }

    public Material getItemFromSlot(int slot) {
        if (slot >= ITEMS_PER_PAGE)
            return null;

        int index = (page * ITEMS_PER_PAGE) + slot;
        if (index < 0 || index >= items.size())
            return null;

        return items.get(index);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }
}
