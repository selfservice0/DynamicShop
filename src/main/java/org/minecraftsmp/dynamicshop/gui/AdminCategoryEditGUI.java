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
import org.minecraftsmp.dynamicshop.managers.RestockManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.List;

/**
 * GUI for editing a single category's properties.
 * Allows changing the icon, display name, and restock settings.
 * 
 * Uses InputManager for Paper 1.21+ compatibility (Dialogs) or Chat Fallback.
 */
public class AdminCategoryEditGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final Inventory inventory;
    private final ItemCategory category;
    private final AdminCategoryGUI parentGUI;

    private static final int SIZE = 36;

    // Slots — Row 2  (icon / name / reset)
    private static final int ICON_SLOT = 11;
    private static final int NAME_SLOT = 13;
    private static final int RESET_SLOT = 15;

    // Slots — Row 3 (restock controls)
    private static final int RESTOCK_TOGGLE_SLOT = 20;
    private static final int RESTOCK_STOCK_SLOT = 22;
    private static final int RESTOCK_INTERVAL_SLOT = 24;

    // Slots — Row 4
    private static final int BACK_SLOT = 31;

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

        // ── Icon editor ─────────────────────────────────────────────
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

        // ── Name Button ─────────────────────────────────────────────
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

        // ── Reset Button ────────────────────────────────────────────
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

        // ── Restock Controls ────────────────────────────────────────
        RestockManager restock = plugin.getRestockManager();
        int[] rule = restock != null ? restock.getRuleForCategory(category) : null;
        boolean hasRule = rule != null;

        // Toggle button
        ItemStack toggleItem = new ItemStack(hasRule ? Material.LIME_DYE : Material.GRAY_DYE);
        meta = toggleItem.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection()
                    .deserialize(hasRule ? "§a§lRestock: ON" : "§7§lRestock: OFF"));
            meta.lore(List.of(
                    text(hasRule ? "§aThis category auto-restocks." : "§7No restock rule for this category."),
                    text(""),
                    text(hasRule ? "§eClick to §cdisable§e restock" : "§eClick to §aenable§e restock")));
            toggleItem.setItemMeta(meta);
        }
        inventory.setItem(RESTOCK_TOGGLE_SLOT, toggleItem);

        // Stock amount button
        ItemStack stockItem = new ItemStack(Material.CHEST);
        meta = stockItem.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§6§lTarget Stock"));
            meta.lore(List.of(
                    text("§7Current: §f" + (hasRule ? rule[0] : "N/A")),
                    text(""),
                    text("§eClick to set target stock level")));
            stockItem.setItemMeta(meta);
        }
        inventory.setItem(RESTOCK_STOCK_SLOT, stockItem);

        // Interval button
        ItemStack intervalItem = new ItemStack(Material.CLOCK);
        meta = intervalItem.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§d§lRestock Interval"));
            meta.lore(List.of(
                    text("§7Current: §f" + (hasRule ? rule[1] + " min" : "N/A")),
                    text(""),
                    text("§eClick to set interval (minutes)")));
            intervalItem.setItemMeta(meta);
        }
        inventory.setItem(RESTOCK_INTERVAL_SLOT, intervalItem);

        // ── Back Button ─────────────────────────────────────────────
        ItemStack backItem = new ItemStack(Material.ARROW);
        meta = backItem.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§c§l◀ Back"));
            backItem.setItemMeta(meta);
        }
        inventory.setItem(BACK_SLOT, backItem);
    }

    private ItemStack createFiller() {
        return org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.getFillerItem();
    }

    /**
     * Handle click events
     */
    public void handleClick(int slot, boolean isRightClick) {
        switch (slot) {
            case ICON_SLOT -> handleIconClick(isRightClick);
            case NAME_SLOT -> handleNameClick();
            case RESET_SLOT -> handleReset();
            case RESTOCK_TOGGLE_SLOT -> handleRestockToggle();
            case RESTOCK_STOCK_SLOT -> handleRestockStock();
            case RESTOCK_INTERVAL_SLOT -> handleRestockInterval();
            case BACK_SLOT -> goBack();
        }
    }

    private void handleIconClick(boolean isRightClick) {
        // Check if player is holding an item (left-click with item)
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (!isRightClick && heldItem != null && heldItem.getType() != Material.AIR) {
            // Use the held item's material
            CategoryConfigManager.setIcon(category, heldItem.getType().name());
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
                            CategoryConfigManager.setIcon(category, mat.name());
                            player.sendMessage("§a[DynamicShop] §fIcon changed to §e" + mat.name());
                        } else {
                            player.sendMessage("§c[DynamicShop] §fInvalid material: " + input);
                        }
                    }

                    // Reopen GUI with a NEW instance to avoid stale onClose handler
                    
                    // [Folia/Paper API] Commented out the old Bukkit scheduler to prevent Folia crashes
                    // Bukkit.getScheduler().runTask(plugin, () -> {
                    //     AdminCategoryEditGUI newGUI = new AdminCategoryEditGUI(plugin, player, category, parentGUI);
                    //     plugin.getShopListener().registerAdminCategoryEdit(player, newGUI);
                    //     newGUI.open();
                    // });

                    // [Folia/Paper API] Use Paper's EntityScheduler which is fully compatible with Folia for GUI tasks
                    player.getScheduler().run(plugin, task -> {
                        AdminCategoryEditGUI newGUI = new AdminCategoryEditGUI(plugin, player, category, parentGUI);
                        plugin.getShopListener().registerAdminCategoryEdit(player, newGUI);
                        newGUI.open();
                    }, null);
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

    // ─── RESTOCK HANDLERS ───────────────────────────────────────────

    private void handleRestockToggle() {
        RestockManager restock = plugin.getRestockManager();
        if (restock == null) return;

        int[] rule = restock.getRuleForCategory(category);
        if (rule != null) {
            // Remove the rule
            restock.removeRuleForCategory(category);
            player.sendMessage("§a[DynamicShop] §fRestock disabled for §e" + CategoryConfigManager.getDisplayName(category));
        } else {
            // Add a default rule and ensure restock is globally enabled
            if (!restock.isRestockEnabled()) {
                restock.setRestockEnabled(true);
            }
            restock.setRuleForCategory(category, 200, 30);
            player.sendMessage("§a[DynamicShop] §fRestock enabled for §e" + CategoryConfigManager.getDisplayName(category)
                    + " §f(200 stock, every 30 min)");
        }
        render();
    }

    private void handleRestockStock() {
        RestockManager restock = plugin.getRestockManager();
        if (restock == null) return;

        int[] rule = restock.getRuleForCategory(category);
        if (rule == null) {
            player.sendMessage("§c[DynamicShop] §fEnable restock first!");
            return;
        }

        plugin.getShopListener().unregisterAdminCategoryEdit(player);

        plugin.getInputManager().requestText(player,
                "Enter Target Stock Amount",
                String.valueOf(rule[0]),
                input -> {
                    if (input != null && !input.trim().isEmpty()) {
                        try {
                            int stock = Integer.parseInt(input.trim());
                            if (stock > 0) {
                                int[] current = plugin.getRestockManager().getRuleForCategory(category);
                                int interval = current != null ? current[1] : 30;
                                plugin.getRestockManager().setRuleForCategory(category, stock, interval);
                                player.sendMessage("§a[DynamicShop] §fRestock target set to §e" + stock);
                            } else {
                                player.sendMessage("§c[DynamicShop] §fStock must be positive!");
                            }
                        } catch (NumberFormatException e) {
                            player.sendMessage("§c[DynamicShop] §fInvalid number: " + input);
                        }
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        AdminCategoryEditGUI newGUI = new AdminCategoryEditGUI(plugin, player, category, parentGUI);
                        plugin.getShopListener().registerAdminCategoryEdit(player, newGUI);
                        newGUI.open();
                    });
                });
    }

    private void handleRestockInterval() {
        RestockManager restock = plugin.getRestockManager();
        if (restock == null) return;

        int[] rule = restock.getRuleForCategory(category);
        if (rule == null) {
            player.sendMessage("§c[DynamicShop] §fEnable restock first!");
            return;
        }

        plugin.getShopListener().unregisterAdminCategoryEdit(player);

        plugin.getInputManager().requestText(player,
                "Enter Restock Interval (minutes)",
                String.valueOf(rule[1]),
                input -> {
                    if (input != null && !input.trim().isEmpty()) {
                        try {
                            int minutes = Integer.parseInt(input.trim());
                            if (minutes > 0) {
                                int[] current = plugin.getRestockManager().getRuleForCategory(category);
                                int stock = current != null ? current[0] : 200;
                                plugin.getRestockManager().setRuleForCategory(category, stock, minutes);
                                player.sendMessage("§a[DynamicShop] §fRestock interval set to §e" + minutes + " min");
                            } else {
                                player.sendMessage("§c[DynamicShop] §fInterval must be positive!");
                            }
                        } catch (NumberFormatException e) {
                            player.sendMessage("§c[DynamicShop] §fInvalid number: " + input);
                        }
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        AdminCategoryEditGUI newGUI = new AdminCategoryEditGUI(plugin, player, category, parentGUI);
                        plugin.getShopListener().registerAdminCategoryEdit(player, newGUI);
                        newGUI.open();
                    });
                });
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
