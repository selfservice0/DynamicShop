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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.List;

/**
 * GUI for editing a single category's properties.
 * Allows changing the icon and display name.
 * 
 * Uses InputManager for Paper 1.21+ compatibility (Dialogs) or Chat Fallback.
 */
public class AdminCategoryEditGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final Inventory inventory;
    private final ItemCategory category;
    private final AdminCategoryGUI parentGUI;

    private static final int SIZE = 27;

    // Slots
    private static final int ICON_SLOT = 11;
    private static final int NAME_SLOT = 13;
    private static final int RESET_SLOT = 15;
    private static final int BACK_SLOT = 22;

    public AdminCategoryEditGUI(DynamicShop plugin, Player player, ItemCategory category, AdminCategoryGUI parentGUI) {
        this.plugin = plugin;
        this.player = player;
        this.category = category;
        this.parentGUI = parentGUI;
        this.inventory = Bukkit.createInventory(null, SIZE,
                LegacyComponentSerializer.legacySection()
                        .deserialize("§4§lEdit: " + CategoryConfigManager.getDisplayName(category)));
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

        // Icon editor
        ItemStack iconItem = new ItemStack(category.getIcon());
        ItemMeta meta = iconItem.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§b§lChange Category Icon"));
            meta.lore(List.of(
                    text("§7Current: §f" + category.getIcon().name()),
                    text(""),
                    text("§eHold new icon item and click"),
                    text("§eto change the category icon.")));
            iconItem.setItemMeta(meta);
        }
        inventory.setItem(ICON_SLOT, iconItem);

        // Name Button
        ItemStack nameItem = new ItemStack(Material.NAME_TAG);
        meta = nameItem.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e§lChange Display Name"));
            meta.lore(List.of(
                    text("§7Current: " + CategoryConfigManager.getDisplayName(category)),
                    text(""),
                    text("§eClick to rename")));
            nameItem.setItemMeta(meta);
        }
        inventory.setItem(NAME_SLOT, nameItem);

        // Reset Button
        ItemStack resetItem = new ItemStack(Material.TNT);
        meta = resetItem.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§c§lReset to Default Icon"));
            meta.lore(List.of(
                    text("§7Reset icon to default:"),
                    text("§f" + category.getIcon().name())));
            resetItem.setItemMeta(meta);
        }
        inventory.setItem(RESET_SLOT, resetItem);

        // Back Button
        ItemStack backItem = new ItemStack(Material.ARROW);
        meta = backItem.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§c§l◀ Back"));
            backItem.setItemMeta(meta);
        }
        inventory.setItem(BACK_SLOT, backItem);
    }

    private ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(" "));
            filler.setItemMeta(meta);
        }
        return filler;
    }

    /**
     * Handle click events
     */
    public void handleClick(int slot, boolean isRightClick) {
        switch (slot) {
            case ICON_SLOT -> handleIconClick(isRightClick);
            case NAME_SLOT -> handleNameClick();
            case RESET_SLOT -> handleReset();
            case BACK_SLOT -> goBack();
        }
    }

    private void handleIconClick(boolean isRightClick) {
        // Check if player is holding an item (left-click with item)
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (!isRightClick && heldItem != null && heldItem.getType() != Material.AIR) {
            // Use the held item's material
            CategoryConfigManager.setIcon(category, heldItem.getType());
            player.sendMessage("§a[DynamicShop] §fIcon changed to §e" + heldItem.getType().name());
            render();
            return;
        }

        // Right-click or no item held: use input manager
        plugin.getShopListener().unregisterAdminCategoryEdit(player);

        plugin.getInputManager().requestText(player,
                "Enter Material Name (e.g. STONE)",
                CategoryConfigManager.getIcon(category).name(),
                input -> {
                    if (input != null && !input.trim().isEmpty()) {
                        Material mat = Material.matchMaterial(input.trim());
                        if (mat != null) {
                            CategoryConfigManager.setIcon(category, mat);
                            player.sendMessage("§a[DynamicShop] §fIcon changed to §e" + mat.name());
                        } else {
                            player.sendMessage("§c[DynamicShop] §fInvalid material: " + input);
                        }
                    }

                    // Reopen GUI with a NEW instance to avoid stale onClose handler
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        AdminCategoryEditGUI newGUI = new AdminCategoryEditGUI(plugin, player, category, parentGUI);
                        plugin.getShopListener().registerAdminCategoryEdit(player, newGUI);
                        newGUI.open();
                    });
                });
    }

    private void handleNameClick() {
        plugin.getShopListener().unregisterAdminCategoryEdit(player);

        plugin.getInputManager().requestText(player,
                "Enter Category Name",
                CategoryConfigManager.getDisplayName(category),
                input -> {
                    if (input != null && !input.trim().isEmpty()) {
                        CategoryConfigManager.setDisplayName(category, input.trim());
                        player.sendMessage("§a[DynamicShop] §fCategory name updated!");
                    }

                    // Reopen GUI with a NEW instance to avoid stale onClose handler
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        AdminCategoryEditGUI newGUI = new AdminCategoryEditGUI(plugin, player, category, parentGUI);
                        plugin.getShopListener().registerAdminCategoryEdit(player, newGUI);
                        newGUI.open();
                    });
                });
    }

    private void handleReset() {
        // Reset to defaults by removing overrides (not just setting to default values)
        CategoryConfigManager.removeIcon(category);
        CategoryConfigManager.removeDisplayName(category);
        player.sendMessage("§a[DynamicShop] §fCategory reset to defaults");
        render();
    }

    private void goBack() {
        plugin.getShopListener().unregisterAdminCategoryEdit(player);
        plugin.getShopListener().registerAdminCategory(player, parentGUI);
        parentGUI.open();
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
