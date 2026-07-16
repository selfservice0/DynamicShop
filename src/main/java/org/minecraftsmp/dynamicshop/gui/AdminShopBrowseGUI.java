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
import org.minecraftsmp.dynamicshop.managers.NexoWrapper;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.util.ShopItemBuilder;
import org.minecraftsmp.dynamicshop.managers.MessageManager;

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
        String title = plugin.getMessageManager().getMessage("admin-shop-gui-title");
        if (title == null) title = "§4§lAdmin Shop";
        this.inventory = org.minecraftsmp.dynamicshop.util.PaperCompat.createInventory(null, SIZE,
                MessageManager.parseComponent(title));

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
            // Regular category — also load any special items assigned here
            this.items = ShopDataManager.getItemsInCategoryIncludeDisabled(currentCategory);
            if (items == null)
                items = List.of();
            this.specialItems = plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                    .filter(item -> item.getCategory() == currentCategory)
                    .toList();
            this.page = 0;
            int totalCombined = items.size() + specialItems.size();
            this.maxPage = totalCombined <= 0 ? 0 : (totalCombined - 1) / ITEMS_PER_PAGE;
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
            // Render regular items + special items assigned to this category
            int totalCombined = items.size() + specialItems.size();
            int start = page * ITEMS_PER_PAGE;
            int end = Math.min(start + ITEMS_PER_PAGE, totalCombined);

            for (int i = start; i < end; i++) {
                int slot = i - start;
                if (i < items.size()) {
                    Material mat = items.get(i);
                    inventory.setItem(slot, buildAdminItem(mat));
                } else {
                    int specialIdx = i - items.size();
                    SpecialShopItem sItem = specialItems.get(specialIdx);
                    inventory.setItem(slot, buildSpecialItem(sItem));
                }
            }
        }

        // Render navigation bar
        renderNavigation();
    }

    private ItemStack buildSpecialItem(SpecialShopItem sItem) {
        ItemStack item = null;
        if ("itemsadder".equalsIgnoreCase(sItem.getDeliveryMethod()) && sItem.getNbt() != null) {
            item = ItemsAdderWrapper.getItem(sItem.getNbt());
        } else if ("nexo".equalsIgnoreCase(sItem.getDeliveryMethod()) && sItem.getNbt() != null) {
            item = NexoWrapper.getItem(sItem.getNbt());
        } else if ("valhallammo".equalsIgnoreCase(sItem.getDeliveryMethod()) && sItem.getNbt() != null) {
            item = org.minecraftsmp.dynamicshop.managers.ValhallaMMOWrapper.getItem(sItem.getNbt());
        } else if ("component".equalsIgnoreCase(sItem.getDeliveryMethod()) || "stored_item".equalsIgnoreCase(sItem.getDeliveryMethod())) {
            String configPath = "special_items." + sItem.getId() + ".stored_item";
            if (plugin.getConfig().contains(configPath)) {
                item = plugin.getConfig().getItemStack(configPath);
            }
        }

        if (item == null) {
            item = new ItemStack(sItem.getDisplayMaterial());
        } else {
            item = item.clone();
            item.setAmount(1);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent("§e§l" + sItem.getName()));

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

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
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
            // Use template if available (shows enchant glow, etc.)
            ItemStack template = ShopDataManager.getTemplate(mat);
            if (template != null) {
                item = template.clone();
                item.setAmount(1);
            } else {
                item = new ItemStack(mat, 1);
            }
        } catch (IllegalArgumentException e) {
            item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent("§c§lINVALID: " + mat.name()));
                item.setItemMeta(meta);
            }
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Show disabled status in name
            String displayName = mat.name().replace("_", " ");
            if (disabled) {
                org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta,
                        MessageManager.parseComponent("§c§m" + displayName + " §7(DISABLED)"));
            } else {
                org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent("§a§l" + displayName));
            }

            List<String> lore = new ArrayList<>();
            lore.add("§7───────────────────");
            lore.add("§7Base Price: §e$" + String.format("%.2f", basePrice));
            lore.add("§7Stock: §f" + String.format("%.0f", stock));
            lore.add("§7Category: §b" + category.getDisplayName());
            lore.add("§7Status: " + (disabled ? "§c✗ Disabled" : "§a✓ Enabled"));
            if (ShopDataManager.hasTemplate(mat)) {
                lore.add("§7Template: §d✓ Has components");
            }
            lore.add("§7───────────────────");
            lore.add("");
            lore.add("§e§lRight-click to EDIT");

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
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
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(pageMeta, MessageManager.parseComponent(
                    "§7Page §f" + (page + 1) + " §7/ §f" + (maxPage + 1)));
            List<String> pageLore = new ArrayList<>();
            pageLore.add("§7Items: §e" + items.size());
            pageLore.add("");
            pageLore.add("§e§lClick to edit Config");
            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(pageMeta,
                    pageLore.stream().map(s -> MessageManager.parseComponent(s)).toList());
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
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent(
                    "§b§lCategory: " + currentCategory.getDisplayName()));

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

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFiller() {
        return org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.getFillerItem();
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

        // Try special item first (works for both pure-special and mixed categories)
        SpecialShopItem sItem = getSpecialItemFromSlot(slot);
        if (sItem != null) {
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
        if (index < 0) return null;

        if (currentCategory == ItemCategory.PERMISSIONS || currentCategory == ItemCategory.SERVER_SHOP) {
            // Pure special category
            if (index >= specialItems.size()) return null;
            return specialItems.get(index);
        } else {
            // Mixed category: special items come after regular items
            int specialIdx = index - items.size();
            if (specialIdx < 0 || specialIdx >= specialItems.size()) return null;
            return specialItems.get(specialIdx);
        }
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
