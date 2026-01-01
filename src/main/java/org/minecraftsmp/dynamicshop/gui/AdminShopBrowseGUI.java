package org.minecraftsmp.dynamicshop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.category.SpecialShopItem;
import org.minecraftsmp.dynamicshop.managers.ItemsAdderWrapper;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.util.ShopItemBuilder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
    private List<SpecialShopItem> specialItems; // For PERMISSIONS/SERVER_SHOP
    private int page = 0;
    private int maxPage = 0;

    public AdminShopBrowseGUI(DynamicShop plugin, Player player) {
        this(plugin, player, ItemCategory.BLOCKS);
    }

    public AdminShopBrowseGUI(DynamicShop plugin, Player player, ItemCategory startCategory) {
        this.plugin = plugin;
        this.player = player;
        this.currentCategory = startCategory;
        this.inventory = Bukkit.createInventory(null, SIZE,
                LegacyComponentSerializer.legacySection().deserialize("§4§lAdmin Shop"));

        loadItemsForCategory();
    }

    public void loadItemsForCategory() {
        // Special categories use special items
        if (currentCategory == ItemCategory.PERMISSIONS ||
                currentCategory == ItemCategory.SERVER_SHOP) {
            this.items = List.of();
            this.specialItems = plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                    .filter(item -> item.getCategory() == currentCategory)
                    .toList();
            this.page = 0;
            this.maxPage = specialItems.isEmpty() ? 0 : (specialItems.size() - 1) / ITEMS_PER_PAGE;
        } else if (currentCategory == ItemCategory.PLAYER_SHOPS) {
            // Skip player shops entirely
            this.items = List.of();
            this.specialItems = List.of();
            this.page = 0;
            this.maxPage = 0;
        } else {
            this.specialItems = List.of();
            this.items = ShopDataManager.getItemsInCategoryIncludeDisabled(currentCategory);
            if (items == null)
                items = List.of();
            this.page = 0;
            this.maxPage = items.isEmpty() ? 0 : (items.size() - 1) / ITEMS_PER_PAGE;
        }
    }

    public void open() {
        render();
        player.openInventory(inventory);
    }

    public void render() {
        inventory.clear();

        // Check if we're in a special category
        if (currentCategory == ItemCategory.PERMISSIONS || currentCategory == ItemCategory.SERVER_SHOP) {
            // Render special items
            int start = page * ITEMS_PER_PAGE;
            int end = Math.min(start + ITEMS_PER_PAGE, specialItems.size());

            for (int i = start; i < end; i++) {
                SpecialShopItem sItem = specialItems.get(i);
                int slot = i - start;
                inventory.setItem(slot, buildSpecialItem(sItem));
            }
        } else {
            // Render regular items for current page
            int start = page * ITEMS_PER_PAGE;
            int end = Math.min(start + ITEMS_PER_PAGE, items.size());

            for (int i = start; i < end; i++) {
                Material mat = items.get(i);
                int slot = i - start;
                inventory.setItem(slot, buildAdminItem(mat));
            }
        }

        // Render navigation bar
        renderNavigation();
    }

    private ItemStack buildSpecialItem(SpecialShopItem sItem) {
        ItemStack item = null;
        if ("itemsadder".equalsIgnoreCase(sItem.getDeliveryMethod()) && sItem.getNbt() != null) {
            item = ItemsAdderWrapper.getItem(sItem.getNbt());
        }

        if (item == null) {
            item = new ItemStack(sItem.getDisplayMaterial());
        } else {
            item = item.clone();
            item.setAmount(1);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e§l" + sItem.getName()));

            List<String> lore = new ArrayList<>();
            lore.add("§7───────────────────");
            lore.add("§7ID: §f" + sItem.getId());
            lore.add("§7Type: §b" + sItem.getCategory().getDisplayName());

            if (sItem.isPermissionItem()) {
                lore.add("§7Permission: §a" + sItem.getPermission());
            } else {
                lore.add("§7Identifier: §a" + sItem.getItemIdentifier());
            }

            lore.add("§7Price: §e$" + String.format("%.2f", sItem.getPrice()));

            if (sItem.hasRequiredPermission()) {
                lore.add("§7Requires: §c" + sItem.getRequiredPermission());
            }

            lore.add("§7───────────────────");
            lore.add("");
            lore.add("§e§lRight-click to EDIT");

            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
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
                meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§c§lINVALID: " + mat.name()));
                item.setItemMeta(meta);
            }
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Show disabled status in name
            String displayName = mat.name().replace("_", " ");
            if (disabled) {
                meta.displayName(
                        LegacyComponentSerializer.legacySection().deserialize("§c§m" + displayName + " §7(DISABLED)"));
            } else {
                meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§a§l" + displayName));
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

            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
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

        // Page info / Config edit (slot 4)
        ItemStack pageItem = new ItemStack(Material.PAPER);
        ItemMeta pageMeta = pageItem.getItemMeta();
        if (pageMeta != null) {
            pageMeta.displayName(LegacyComponentSerializer.legacySection()
                    .deserialize("§7Page §f" + (page + 1) + " §7/ §f" + (maxPage + 1)));
            List<String> pageLore = new ArrayList<>();
            pageLore.add("§7Items: §e" + items.size());
            pageLore.add("");
            pageLore.add("§e§lClick to edit Config");
            pageMeta.lore(
                    pageLore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
            pageItem.setItemMeta(pageMeta);
        }
        inventory.setItem(navRow + 4, pageItem);

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
            meta.displayName(LegacyComponentSerializer.legacySection()
                    .deserialize("§b§lCategory: " + currentCategory.getDisplayName()));

            List<String> lore = new ArrayList<>();
            lore.add("§7───────────────────");

            // List all categories, highlight current (only skip PLAYER_SHOPS)
            for (ItemCategory cat : ItemCategory.values()) {
                if (cat == ItemCategory.PLAYER_SHOPS) {
                    continue; // Only skip PLAYER_SHOPS
                }

                if (cat == currentCategory) {
                    lore.add("§a▶ " + cat.getDisplayName());
                } else {
                    lore.add("§7  " + cat.getDisplayName());
                }
            }

            lore.add("§7───────────────────");
            lore.add("§eClick to cycle category");

            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(" "));
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
                case 4 -> openConfigEditor(); // Config editor
                case 5 -> player.closeInventory(); // Close
            }
            return;
        }

        // Item slot - only handle right-click
        if (!isRightClick)
            return;

        // Check if we're in a special category
        if (currentCategory == ItemCategory.PERMISSIONS || currentCategory == ItemCategory.SERVER_SHOP) {
            SpecialShopItem sItem = getSpecialItemFromSlot(slot);
            if (sItem == null)
                return;

            // Open the special item editor GUI
            plugin.getShopListener().unregisterAdminBrowse(player);
            AdminSpecialItemEditGUI editGUI = new AdminSpecialItemEditGUI(plugin, player, sItem, this);
            plugin.getShopListener().registerAdminSpecialEdit(player, editGUI);
            editGUI.open();
            return;
        }

        // Regular item
        Material mat = getItemFromSlot(slot);
        if (mat == null)
            return;

        // Open the item editor GUI
        plugin.getShopListener().unregisterAdminBrowse(player);
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

    private void openConfigEditor() {
        plugin.getShopListener().unregisterAdminBrowse(player);
        AdminConfigGUI configGUI = new AdminConfigGUI(plugin, player, this);
        plugin.getShopListener().registerAdminConfig(player, configGUI);
        configGUI.open();
    }

    private void cycleCategory() {
        ItemCategory[] categories = ItemCategory.values();
        int currentIndex = currentCategory.ordinal();

        // Find next valid category (only skip PLAYER_SHOPS)
        do {
            currentIndex = (currentIndex + 1) % categories.length;
        } while (categories[currentIndex] == ItemCategory.PLAYER_SHOPS);

        currentCategory = categories[currentIndex];
        loadItemsForCategory();
        render();
    }

    public SpecialShopItem getSpecialItemFromSlot(int slot) {
        if (slot >= ITEMS_PER_PAGE)
            return null;

        int index = (page * ITEMS_PER_PAGE) + slot;
        if (index < 0 || index >= specialItems.size())
            return null;

        return specialItems.get(index);
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
