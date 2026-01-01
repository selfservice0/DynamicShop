package org.minecraftsmp.dynamicshop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.managers.CategoryConfigManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Admin GUI for managing shop categories.
 * 
 * Features:
 * - Left-click: Enter category (opens AdminShopBrowseGUI)
 * - Right-click: Pick up category for drag-drop OR edit if shift-right-click
 * - Shift+Right-click: Edit category properties (icon, name)
 */
public class AdminCategoryGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final Inventory inventory;

    private static final int SIZE = 54;

    // Drag-drop state
    private ItemCategory heldCategory = null;
    private int heldFromSlot = -1;

    // Navigation slots (bottom row of 54-slot inventory)
    private static final int SAVE_SLOT = 49;
    private static final int CONFIG_SLOT = 50;
    private static final int CLOSE_SLOT = 53;
    private static final int CANCEL_SLOT = 45; // SIZE - 9
    private static final int HIDE_SLOT = 46; // SIZE - 8

    public AdminCategoryGUI(DynamicShop plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, SIZE,
                LegacyComponentSerializer.legacySection().deserialize("§4§lCategory Editor"));
    }

    public void open() {
        render();
        player.openInventory(inventory);
    }

    public void render() {
        inventory.clear();

        // Fill with glass panes
        ItemStack filler = createFiller();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        // Place categories at their configured slots
        for (ItemCategory category : ItemCategory.values()) {
            int slot = CategoryConfigManager.getSlot(category);
            if (slot >= 0 && slot < SIZE - 9) { // Don't place in navigation row
                inventory.setItem(slot, createCategoryItem(category));
            }
        }

        // Navigation row
        renderNavigation();

        // Show held item indicator
        if (heldCategory != null) {
            player.sendActionBar(text("§e§lHolding: §f" + CategoryConfigManager.getDisplayName(heldCategory) +
                    " §7(Right-click a slot to place)"));
        }
    }

    private ItemStack createCategoryItem(ItemCategory category) {
        Material icon = CategoryConfigManager.getIcon(category);
        String displayName = CategoryConfigManager.getDisplayName(category);

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Apply colors if present
            String formattedName = displayName.contains("&")
                    ? displayName.replace('&', '§')
                    : "§e§l" + displayName;

            // Highlight if this is the held category
            if (category == heldCategory) {
                String stripped = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(LegacyComponentSerializer.legacySection().deserialize(formattedName));
                meta.displayName(LegacyComponentSerializer.legacySection()
                        .deserialize("§b§l▶ " + stripped + " §7(HELD)"));
            } else {
                meta.displayName(LegacyComponentSerializer.legacySection().deserialize(formattedName));
            }

            List<Component> lore = new ArrayList<>();
            lore.add(text("§7───────────────────"));
            lore.add(text("§7Slot: §f" + CategoryConfigManager.getSlot(category)));
            lore.add(text("§7Icon: §f" + icon.name()));

            // Item count for this category
            int itemCount = getItemCount(category);
            lore.add(text("§7Items: §a" + itemCount));

            lore.add(text("§7───────────────────"));
            lore.add(text(""));
            lore.add(text("§a§l◀ Left-click §7to enter"));
            lore.add(text("§e§l◀ Right-click §7to pick up/move"));
            lore.add(text("§b§l◀ Shift+Right-click §7to edit"));

            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private int getItemCount(ItemCategory category) {
        if (category == ItemCategory.PERMISSIONS) {
            return (int) plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                    .filter(item -> item.getCategory() == ItemCategory.PERMISSIONS)
                    .count();
        } else if (category == ItemCategory.SERVER_SHOP) {
            return (int) plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                    .filter(item -> item.getCategory() == ItemCategory.SERVER_SHOP)
                    .count();
        } else {
            return ShopDataManager.getItemsInCategory(category).size();
        }
    }

    private void renderNavigation() {
        int navStart = SIZE - 9;

        // Fill navigation row
        ItemStack navFiller = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta navMeta = navFiller.getItemMeta();
        if (navMeta != null) {
            navMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(" "));
            navFiller.setItemMeta(navMeta);
        }
        for (int i = navStart; i < SIZE; i++) {
            inventory.setItem(i, navFiller);
        }

        // Save button
        ItemStack saveItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta saveMeta = saveItem.getItemMeta();
        if (saveMeta != null) {
            saveMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§a§lSave Changes"));
            saveMeta.lore(List.of(
                    text("§7Save category positions and"),
                    text("§7customizations to config"),
                    text(""),
                    text("§eClick to save")));
            saveItem.setItemMeta(saveMeta);
        }
        inventory.setItem(SAVE_SLOT, saveItem);

        // Config Editor button
        ItemStack configItem = new ItemStack(Material.PAPER);
        ItemMeta configMeta = configItem.getItemMeta();
        if (configMeta != null) {
            configMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e§lConfig Editor"));
            configMeta.lore(List.of(
                    text("§7Edit plugin settings"),
                    text(""),
                    text("§eClick to open")));
            configItem.setItemMeta(configMeta);
        }
        inventory.setItem(CONFIG_SLOT, configItem);

        // Close button
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§c§lClose"));
            closeMeta.lore(List.of(text("§7Close without saving")));
            closeItem.setItemMeta(closeMeta);
        }
        inventory.setItem(CLOSE_SLOT, closeItem);

        // Cancel drag button (if holding)
        if (heldCategory != null) {
            ItemStack cancelItem = new ItemStack(Material.RED_WOOL);
            ItemMeta cancelMeta = cancelItem.getItemMeta();
            if (cancelMeta != null) {
                cancelMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§c§lCancel Move"));
                cancelMeta.lore(List.of(
                        text("§7Cancel the current drag operation"),
                        text("§7and return " + CategoryConfigManager.getDisplayName(heldCategory)),
                        text("§7to its original position")));
                cancelItem.setItemMeta(cancelMeta);
            }
            inventory.setItem(CANCEL_SLOT, cancelItem);

            // Hide category button
            ItemStack hideItem = new ItemStack(Material.BARRIER);
            ItemMeta hideMeta = hideItem.getItemMeta();
            if (hideMeta != null) {
                hideMeta.displayName(LegacyComponentSerializer.legacySection().deserialize("§4§lHide Category"));
                hideMeta.lore(List.of(
                        text("§7Remove " + CategoryConfigManager.getDisplayName(heldCategory)),
                        text("§7from the shop so players"),
                        text("§7cannot see it in /shop"),
                        text(""),
                        text("§cClick to hide")));
                hideItem.setItemMeta(hideMeta);
            }
            inventory.setItem(HIDE_SLOT, hideItem);
        }
    }

    private ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            if (heldCategory != null) {
                meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§7§oEmpty Slot"));
                meta.lore(List.of(text("§eRight-click to place category here")));
            } else {
                meta.displayName(LegacyComponentSerializer.legacySection().deserialize(" "));
                meta.lore(List.of(text("§eLeft-click to create")));
            }
            filler.setItemMeta(meta);
        }
        return filler;
    }

    /**
     * Handle click events in this GUI
     */
    public void handleClick(int slot, boolean isRightClick, boolean isShiftClick) {
        // Navigation row
        if (slot >= SIZE - 9) {
            if (slot == SAVE_SLOT) {
                saveChanges();
                player.sendMessage("§a[DynamicShop] §fCategory changes saved!");
                return;
            }
            if (slot == CONFIG_SLOT) {
                openConfigEditor();
                return;
            }
            if (slot == CLOSE_SLOT) {
                player.closeInventory();
                return;
            }
            // Cancel drag
            if (slot == CANCEL_SLOT && heldCategory != null) {
                cancelDrag();
                return;
            }
            // Hide category
            if (slot == HIDE_SLOT && heldCategory != null) {
                hideCategory();
                return;
            }
            return;
        }

        // Find category at this slot
        ItemCategory categoryAtSlot = CategoryConfigManager.getCategoryAtSlot(slot);

        // Handle based on click type and drag state
        if (heldCategory != null) {
            // We're in drag mode
            if (isRightClick) {
                // Place the held category
                if (categoryAtSlot != null) {
                    // Swap with existing category
                    CategoryConfigManager.swapSlots(heldCategory, categoryAtSlot);
                } else {
                    // Place in empty slot
                    CategoryConfigManager.setSlot(heldCategory, slot);
                }
                heldCategory = null;
                heldFromSlot = -1;
                render();
                player.sendActionBar(text("§a§lCategory placed!"));
            }
        } else {
            // Normal mode
            if (categoryAtSlot == null) {
                // Clicked empty slot - try to add a custom category here
                if (!isRightClick) {
                    // Left-click on empty slot: enable next available custom category
                    ItemCategory nextCustom = CategoryConfigManager.getNextAvailableCustomCategory();
                    if (nextCustom != null) {
                        CategoryConfigManager.setSlot(nextCustom, slot);
                        render();
                        player.sendActionBar(text("§a§lAdded: §f" + CategoryConfigManager.getDisplayName(nextCustom) +
                                " §7(Shift+Right-click to edit)"));
                    } else {
                        player.sendActionBar(text("§c§lAll 10 custom categories are already in use!"));
                    }
                }
                return;
            }

            if (isRightClick && isShiftClick) {
                // Shift+Right-click: Edit category
                openEditMenu(categoryAtSlot);
            } else if (isRightClick) {
                // Right-click: Pick up for drag
                heldCategory = categoryAtSlot;
                heldFromSlot = slot;
                render();
                player.sendActionBar(text("§e§lPicked up: §f" + CategoryConfigManager.getDisplayName(categoryAtSlot)));
            } else {
                // Left-click: Enter category
                enterCategory(categoryAtSlot);
            }
        }
    }

    private void enterCategory(ItemCategory category) {
        plugin.getShopListener().unregisterAdminCategory(player);

        // Create admin browse GUI set to this category
        AdminShopBrowseGUI browseGUI = new AdminShopBrowseGUI(plugin, player, category);
        plugin.getShopListener().registerAdminBrowse(player, browseGUI);
        browseGUI.open();
    }

    private void openConfigEditor() {
        // Create a dummy parent using the first available category
        AdminShopBrowseGUI dummyParent = new AdminShopBrowseGUI(plugin, player, ItemCategory.values()[0]);

        AdminConfigGUI configGUI = new AdminConfigGUI(plugin, player, dummyParent) {
            @Override
            public void handleClick(int slot) {
                if (slot == 49) { // Back button
                    // Custom back behavior: return to Category Editor
                    plugin.getShopListener().unregisterAdminConfig(player);
                    AdminCategoryGUI catGUI = new AdminCategoryGUI(plugin, player);
                    plugin.getShopListener().registerAdminCategory(player, catGUI);
                    catGUI.open();
                    return;
                }
                super.handleClick(slot);
            }
        };

        plugin.getShopListener().registerAdminConfig(player, configGUI);
        configGUI.open();
    }

    private void openEditMenu(ItemCategory category) {
        // Close current inventory and open submenu
        plugin.getShopListener().unregisterAdminCategory(player);

        AdminCategoryEditGUI editGUI = new AdminCategoryEditGUI(plugin, player, category, this);
        plugin.getShopListener().registerAdminCategoryEdit(player, editGUI);
        editGUI.open();
    }

    private void cancelDrag() {
        if (heldCategory != null && heldFromSlot >= 0) {
            // Restore to original position
            CategoryConfigManager.setSlot(heldCategory, heldFromSlot);
        }
        heldCategory = null;
        heldFromSlot = -1;
        render();
        player.sendActionBar(text("§c§lDrag cancelled"));
    }

    private void hideCategory() {
        if (heldCategory != null) {
            String name = CategoryConfigManager.getDisplayName(heldCategory);
            // Set slot to -1 to hide from /shop
            CategoryConfigManager.setSlot(heldCategory, -1);
            heldCategory = null;
            heldFromSlot = -1;
            render();
            player.sendActionBar(text("§4§lHidden: §f" + name + " §7(Save to persist)"));
        }
    }

    private void saveChanges() {
        CategoryConfigManager.save();
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * Helper method to convert legacy-formatted strings to Adventure Components.
     */
    private Component text(String legacyText) {
        return LegacyComponentSerializer.legacySection().deserialize(legacyText);
    }
}
