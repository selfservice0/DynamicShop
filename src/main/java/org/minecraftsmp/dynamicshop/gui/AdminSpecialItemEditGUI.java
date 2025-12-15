package org.minecraftsmp.dynamicshop.gui;

import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.SpecialShopItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Admin GUI for editing special shop items (permissions and server-shop items).
 * Allows editing price, display material, required permission, and deletion.
 */
public class AdminSpecialItemEditGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final SpecialShopItem item;
    private final AdminShopBrowseGUI parentGUI;
    private final Inventory inventory;

    private static final int SIZE = 54;

    // Layout slots
    private static final int ITEM_DISPLAY = 13;
    private static final int EDIT_PRICE = 29;
    private static final int EDIT_MATERIAL = 31;
    private static final int EDIT_REQUIRED_PERM = 33;
    private static final int DELETE_BUTTON = 49;
    private static final int BACK_BUTTON = 45;

    public AdminSpecialItemEditGUI(DynamicShop plugin, Player player, SpecialShopItem item,
            AdminShopBrowseGUI parentGUI) {
        this.plugin = plugin;
        this.player = player;
        this.item = item;
        this.parentGUI = parentGUI;
        this.inventory = Bukkit.createInventory(null, SIZE, "§4§lEdit: " + item.getName());
    }

    public void open() {
        render();
        player.openInventory(inventory);
    }

    public void render() {
        inventory.clear();

        // Fill with glass
        ItemStack filler = createFiller();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        // Item display
        inventory.setItem(ITEM_DISPLAY, createItemDisplay());

        // Edit buttons
        inventory.setItem(EDIT_PRICE, createPriceButton());
        inventory.setItem(EDIT_MATERIAL, createMaterialButton());
        inventory.setItem(EDIT_REQUIRED_PERM, createRequiredPermButton());

        // Delete button
        inventory.setItem(DELETE_BUTTON, createDeleteButton());

        // Back button
        inventory.setItem(BACK_BUTTON, createBackButton());
    }

    private ItemStack createItemDisplay() {
        ItemStack display = new ItemStack(item.getDisplayMaterial());
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l" + item.getName());
            List<String> lore = new ArrayList<>();
            lore.add("§7───────────────────");
            lore.add("§7ID: §f" + item.getId());
            lore.add("§7Type: §b" + item.getCategory().getDisplayName());

            if (item.isPermissionItem()) {
                lore.add("§7Permission: §a" + item.getPermission());
            } else {
                lore.add("§7Identifier: §a" + item.getItemIdentifier());
                lore.add("§7Delivery: §f" + (item.getDeliveryMethod() != null ? item.getDeliveryMethod() : "item"));
            }

            lore.add("§7Price: §e$" + String.format("%.2f", item.getPrice()));

            if (item.hasRequiredPermission()) {
                lore.add("§7Required Perm: §c" + item.getRequiredPermission());
            }

            lore.add("§7───────────────────");
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private ItemStack createPriceButton() {
        ItemStack btn = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = btn.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lEdit Price");
            List<String> lore = new ArrayList<>();
            lore.add("§7Current: §e$" + String.format("%.2f", item.getPrice()));
            lore.add("");
            lore.add("§eClick to change");
            meta.setLore(lore);
            btn.setItemMeta(meta);
        }
        return btn;
    }

    private ItemStack createMaterialButton() {
        ItemStack btn = new ItemStack(item.getDisplayMaterial());
        ItemMeta meta = btn.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lEdit Display Material");
            List<String> lore = new ArrayList<>();
            lore.add("§7Current: §f" + item.getDisplayMaterial().name());
            lore.add("");
            lore.add("§eHold item and click to use");
            meta.setLore(lore);
            btn.setItemMeta(meta);
        }
        return btn;
    }

    private ItemStack createRequiredPermButton() {
        ItemStack btn = new ItemStack(Material.BARRIER);
        ItemMeta meta = btn.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lEdit Required Permission");
            List<String> lore = new ArrayList<>();
            if (item.hasRequiredPermission()) {
                lore.add("§7Current: §e" + item.getRequiredPermission());
            } else {
                lore.add("§7Current: §aNone (anyone can buy)");
            }
            lore.add("");
            lore.add("§eClick to change");
            lore.add("§7Type 'none' to remove requirement");
            meta.setLore(lore);
            btn.setItemMeta(meta);
        }
        return btn;
    }

    private ItemStack createDeleteButton() {
        ItemStack btn = new ItemStack(Material.TNT);
        ItemMeta meta = btn.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§l⚠ DELETE ITEM");
            List<String> lore = new ArrayList<>();
            lore.add("§7This will permanently remove");
            lore.add("§7this item from the shop.");
            lore.add("");
            lore.add("§c§lSHIFT+CLICK to confirm");
            meta.setLore(lore);
            btn.setItemMeta(meta);
        }
        return btn;
    }

    private ItemStack createBackButton() {
        ItemStack btn = new ItemStack(Material.ARROW);
        ItemMeta meta = btn.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§l◀ Back");
            List<String> lore = new ArrayList<>();
            lore.add("§7Return to shop browser");
            meta.setLore(lore);
            btn.setItemMeta(meta);
        }
        return btn;
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

    public void handleClick(int slot, boolean isShiftClick) {
        switch (slot) {
            case EDIT_PRICE -> openPriceEditor();
            case EDIT_MATERIAL -> editMaterial();
            case EDIT_REQUIRED_PERM -> openRequiredPermEditor();
            case DELETE_BUTTON -> {
                if (isShiftClick) {
                    deleteItem();
                } else {
                    player.sendMessage("§c⚠ Shift+click to confirm deletion!");
                }
            }
            case BACK_BUTTON -> goBack();
        }
    }

    private void openPriceEditor() {
        player.closeInventory();

        new AnvilGUI.Builder()
                .title("§8Set Price")
                .text(String.format("%.2f", item.getPrice()))
                .itemLeft(new ItemStack(Material.GOLD_INGOT))
                .onClick((clickSlot, state) -> {
                    try {
                        double newPrice = Double.parseDouble(state.getText().trim());
                        if (newPrice < 0) {
                            player.sendMessage("§cPrice cannot be negative!");
                            return Arrays.asList(AnvilGUI.ResponseAction.close());
                        }

                        plugin.getSpecialShopManager().updateItemPrice(item.getId(), newPrice);
                        player.sendMessage("§a✓ §7Price updated to §e$" + String.format("%.2f", newPrice));

                        // Reopen this GUI with fresh data
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            SpecialShopItem updated = plugin.getSpecialShopManager().getSpecialItem(item.getId());
                            if (updated != null) {
                                AdminSpecialItemEditGUI newGui = new AdminSpecialItemEditGUI(plugin, player, updated,
                                        parentGUI);
                                plugin.getShopListener().registerAdminSpecialEdit(player, newGui);
                                newGui.open();
                            }
                        }, 2L);

                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid number!");
                    }
                    return Arrays.asList(AnvilGUI.ResponseAction.close());
                })
                .plugin(plugin)
                .open(player);
    }

    private void editMaterial() {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            player.sendMessage("§cHold an item to set as display material!");
            return;
        }

        plugin.getSpecialShopManager().updateItemDisplayMaterial(item.getId(), held.getType());
        player.sendMessage("§a✓ §7Display material set to §e" + held.getType().name());
        render();
    }

    private void openRequiredPermEditor() {
        player.closeInventory();

        String currentPerm = item.hasRequiredPermission() ? item.getRequiredPermission() : "none";

        new AnvilGUI.Builder()
                .title("§8Required Permission")
                .text(currentPerm)
                .itemLeft(new ItemStack(Material.PAPER))
                .onClick((clickSlot, state) -> {
                    String newPerm = state.getText().trim();

                    if (newPerm.equalsIgnoreCase("none") || newPerm.isEmpty()) {
                        plugin.getSpecialShopManager().updateItemRequiredPermission(item.getId(), null);
                        player.sendMessage("§a✓ §7Required permission removed");
                    } else {
                        plugin.getSpecialShopManager().updateItemRequiredPermission(item.getId(), newPerm);
                        player.sendMessage("§a✓ §7Required permission set to §e" + newPerm);
                    }

                    // Reopen this GUI with fresh data
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        SpecialShopItem updated = plugin.getSpecialShopManager().getSpecialItem(item.getId());
                        if (updated != null) {
                            AdminSpecialItemEditGUI newGui = new AdminSpecialItemEditGUI(plugin, player, updated,
                                    parentGUI);
                            plugin.getShopListener().registerAdminSpecialEdit(player, newGui);
                            newGui.open();
                        }
                    }, 2L);

                    return Arrays.asList(AnvilGUI.ResponseAction.close());
                })
                .plugin(plugin)
                .open(player);
    }

    private void deleteItem() {
        String itemId = item.getId();
        String itemName = item.getName();

        boolean removed = plugin.getSpecialShopManager().removeSpecialItem(itemId);

        if (removed) {
            player.sendMessage("§a✓ §7Deleted: §e" + itemName);
            goBack();
        } else {
            player.sendMessage("§cFailed to delete item!");
        }
    }

    private void goBack() {
        plugin.getShopListener().unregisterAdminSpecialEdit(player);
        plugin.getShopListener().registerAdminBrowse(player, parentGUI);
        parentGUI.loadItemsForCategory();
        parentGUI.render();
        player.openInventory(parentGUI.getInventory());
    }

    public Inventory getInventory() {
        return inventory;
    }

    public SpecialShopItem getItem() {
        return item;
    }
}
