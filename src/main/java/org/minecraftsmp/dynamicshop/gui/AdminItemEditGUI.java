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
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin GUI for editing individual shop items.
 * Allows adjusting stock, base price, category, and enabled/disabled status.
 *
 * Uses InputManager for text input (Paper Dialog API or chat fallback).
 */
public class AdminItemEditGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final Material material;
    private final String variantId;
    private final AdminShopBrowseGUI parentGUI;
    private final Inventory inventory;

    private static final int SIZE = 45; // 5 rows

    // Slot positions
    private static final int ITEM_DISPLAY_SLOT = 13; // Center of row 2

    private static final int STOCK_MINUS_64 = 10;
    private static final int STOCK_MINUS_10 = 11;
    private static final int STOCK_MINUS_1 = 12;
    private static final int STOCK_PLUS_1 = 14;
    private static final int STOCK_PLUS_10 = 15;
    private static final int STOCK_PLUS_64 = 16;

    private static final int PRICE_EDIT_SLOT = 19; // Row 3
    private static final int PRICE_INCREASE_SLOT = 20; // Row 3 - price increase %
    private static final int CATEGORY_EDIT_SLOT = 21; // Row 3
    private static final int BUY_TOGGLE_SLOT = 23; // Row 3 - toggle buy
    private static final int TOGGLE_ENABLED_SLOT = 24; // Row 3
    private static final int SELL_TOGGLE_SLOT = 25; // Row 3 - toggle sell

    private static final int MAX_STOCK_SLOT = 30; // Row 4
    private static final int MAX_STOCK_STORAGE_SLOT = 32; // Row 4

    private static final int BACK_BUTTON_SLOT = 40; // Bottom center
    private static final int DISPLAY_MATERIAL_SLOT = 28;
    private static final int REQUIRED_PERMISSION_SLOT = 31;
    private static final int DELETE_SLOT = 34;

    public AdminItemEditGUI(DynamicShop plugin, Player player, Material material, AdminShopBrowseGUI parentGUI) {
        this(plugin, player, material, null, parentGUI);
    }

    public AdminItemEditGUI(DynamicShop plugin, Player player, SpecialShopItem item, AdminShopBrowseGUI parentGUI) {
        this(plugin, player, item.getDisplayMaterial(), item.getId(), parentGUI);
        if (!item.isTradeItem()) throw new IllegalArgumentException("Only physical custom items have stock controls");
    }

    private AdminItemEditGUI(DynamicShop plugin, Player player, Material material, String variantId,
            AdminShopBrowseGUI parentGUI) {
        this.plugin = plugin;
        this.player = player;
        this.material = material;
        this.variantId = variantId;
        this.parentGUI = parentGUI;

        String itemName = variantId == null ? material.name().replace("_", " ")
                : plugin.getSpecialShopManager().getDisplayName(variant());
        this.inventory = org.minecraftsmp.dynamicshop.util.PaperCompat.createInventory(null, SIZE,
                MessageManager.parseComponent(org.minecraftsmp.dynamicshop.util.GuiTheme.title(plugin, player,
                        "admin-item-edit-title", "&4&lEdit: {item}", SIZE, java.util.Map.of("item", itemName)), player));
    }

    public void open() {
        render();
        player.openInventory(inventory);
    }

    public void render() {
        if (!canEdit()) return;
        inventory.clear();

        // Fill with glass panes
        ItemStack filler = createFiller();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        // Item display in center
        inventory.setItem(ITEM_DISPLAY_SLOT, createItemDisplay());

        // Stock adjustment buttons
        inventory.setItem(STOCK_MINUS_64, createStockButton(-64));
        inventory.setItem(STOCK_MINUS_10, createStockButton(-10));
        inventory.setItem(STOCK_MINUS_1, createStockButton(-1));
        inventory.setItem(STOCK_PLUS_1, createStockButton(1));
        inventory.setItem(STOCK_PLUS_10, createStockButton(10));
        inventory.setItem(STOCK_PLUS_64, createStockButton(64));

        // Action buttons
        inventory.setItem(PRICE_EDIT_SLOT, createPriceButton());
        inventory.setItem(PRICE_INCREASE_SLOT, createPriceIncreaseButton());
        inventory.setItem(CATEGORY_EDIT_SLOT, createCategoryButton());
        inventory.setItem(BUY_TOGGLE_SLOT, createBuyToggleButton());
        inventory.setItem(TOGGLE_ENABLED_SLOT, createToggleButton());
        inventory.setItem(SELL_TOGGLE_SLOT, createSellToggleButton());

        inventory.setItem(MAX_STOCK_SLOT, createMaxStockButton());
        inventory.setItem(MAX_STOCK_STORAGE_SLOT, createMaxStockStorageButton());

        if (variantId != null) {
            var entry = variant();
            inventory.setItem(DISPLAY_MATERIAL_SLOT, org.minecraftsmp.dynamicshop.util.ShopItemBuilder.navItem(
                    "§b§lEdit Display Material", entry.getDisplayMaterial(),
                    "§7Current: §f" + entry.getDisplayMaterial().name(), "§eHold an item and click to use"));
            inventory.setItem(REQUIRED_PERMISSION_SLOT, org.minecraftsmp.dynamicshop.util.ShopItemBuilder.navItem(
                    "§c§lEdit Required Permission", Material.BARRIER,
                    "§7Current: §e" + (entry.hasRequiredPermission() ? entry.getRequiredPermission() : "None"),
                    "§eClick to change", "§7Type 'none' to remove requirement"));
        }
        inventory.setItem(DELETE_SLOT, org.minecraftsmp.dynamicshop.util.ShopItemBuilder.navItem(
                "§c§l⚠ DELETE ITEM", Material.TNT,
                "§7Permanently remove this shop entry", "§c§lSHIFT+CLICK to confirm"));

        // Back button
        inventory.setItem(BACK_BUTTON_SLOT, org.minecraftsmp.dynamicshop.util.GuiTheme.control(
                plugin, player, createBackButton(), "shop_back_button"));
    }

    private ItemStack createItemDisplay() {
        double basePrice = basePrice();
        double stock = stock();
        boolean disabled = disabled("disabled");
        ItemCategory category = category();

        ItemStack item = variantId == null ? null : plugin.getSpecialShopManager().getTradeTemplate(variant());
        if (item == null) item = new ItemStack(variantId == null ? material : variant().getDisplayMaterial());
        else {
            item = item.clone();
            item.setAmount(1);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta,
                    MessageManager.parseComponent("§e§l" + (variantId == null
                            ? material.name().replace("_", " ") : plugin.getSpecialShopManager().getDisplayName(variant()))));

            List<String> lore = new ArrayList<>();
            lore.add("§7═══════════════════════");
            lore.add("§7Base Price: §e" + plugin.getEconomyManager().format(basePrice));
            lore.add("§7Current Stock: §f" + String.format("%.0f", stock));
            lore.add("§7Category: §b" + category.getDisplayName());
            if (variantId != null) {
                lore.add("§7ID: §f" + variantId);
                lore.add("§7Delivery: §f" + variant().getDeliveryMethod());
            }
            lore.add("§7Status: " + (disabled ? "§c✗ DISABLED" : "§a✓ ENABLED"));
            lore.add("§7═══════════════════════");

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createStockButton(int delta) {
        Material mat = delta > 0 ? Material.LIME_WOOL : Material.RED_WOOL;
        String prefix = delta > 0 ? "§a+" : "§c";

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent(prefix + delta + " Stock"));

            List<String> lore = new ArrayList<>();
            double currentStock = stock();
            lore.add("§7Current: §f" + String.format("%.0f", currentStock));
            lore.add("§7After: §f" + String.format("%.0f", clampStock(currentStock + delta)));
            lore.add("");
            lore.add("§eClick to adjust");

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPriceButton() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent("§e§lChange Base Price"));

            List<String> lore = new ArrayList<>();
            double currentPrice = basePrice();
            lore.add("§7Current: §e" + plugin.getEconomyManager().format(currentPrice));
            lore.add("");
            lore.add("§eClick to change");

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPriceIncreaseButton() {
        double hours = shortageHours();
        double percentIncrease = ShopDataManager.getInflationIncreasePercent(hours);
        double maxPercent = (org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.maxPriceMultiplier - 1.0) * 100.0;

        boolean capped = percentIncrease >= maxPercent;
        if (capped) percentIncrease = maxPercent;

        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent("§6§lPrice Increase %"));

            List<String> lore = new ArrayList<>();
            lore.add("§7Hours since update: §f" + String.format("%.1f", hours));
            lore.add("§7Current increase: §c+" + String.format("%,.0f", percentIncrease) + "%" + (capped ? " (MAX)" : ""));
            lore.add("");
            lore.add("§7This increases buy price when");
            lore.add("§7stock is at 0 or negative.");
            lore.add("");
            lore.add("§eClick to set % (resets timer)");

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCategoryButton() {
        ItemCategory current = category();

        ItemStack item = new ItemStack(org.minecraftsmp.dynamicshop.managers.CategoryConfigManager.getIcon(current));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent("§b§lChange Category"));

            List<String> lore = new ArrayList<>();
            lore.add("§7Current: §b"
                    + org.minecraftsmp.dynamicshop.managers.CategoryConfigManager.getDisplayName(current));
            lore.add("");
            lore.add("§eClick to cycle");

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMaxStockButton() {
        var itemConfig = itemConfig();
        Double maxStock = itemConfig != null ? itemConfig.maxStock() : null;

        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent("§6§lPricing Max Stock"));

            List<String> lore = new ArrayList<>();
            lore.add("§7Limits price drop due to overstock");
            lore.add("§7Current: §e" + (maxStock != null ? maxStock : "(Global Default)"));
            lore.add("");
            lore.add("§eClick to edit");
            lore.add("§cRight-click to use global default");

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMaxStockStorageButton() {
        var itemConfig = itemConfig();
        Integer maxStockStorage = itemConfig != null ? itemConfig.maxStockStorage() : null;

        ItemStack item = new ItemStack(Material.TRAPPED_CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent("§6§lStorage Hard Limit"));

            List<String> lore = new ArrayList<>();
            lore.add("§7Prevents selling past this stock");
            lore.add("§7Current: §e" + (maxStockStorage != null ? maxStockStorage : "Unlimited"));
            lore.add("");
            lore.add("§eClick to edit");
            lore.add("§cRight-click to set unlimited");

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createToggleButton() {
        boolean disabled = disabled("disabled");

        Material mat = disabled ? Material.RED_CONCRETE : Material.LIME_CONCRETE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta,
                    MessageManager.parseComponent(disabled ? "§c§lDISABLED" : "§a§lENABLED"));

            List<String> lore = new ArrayList<>();
            if (disabled) {
                lore.add("§7This item is §chidden§7 from the shop");
                lore.add("");
                lore.add("§aClick to ENABLE");
            } else {
                lore.add("§7This item is §avisible§7 in the shop");
                lore.add("");
                lore.add("§cClick to DISABLE");
            }

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBuyToggleButton() {
        boolean buyDisabled = disabled("disable-buy");

        Material mat = buyDisabled ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta,
                    MessageManager.parseComponent(
                            buyDisabled ? "§c§lBUY DISABLED" : "§a§lBUY ENABLED"));

            List<String> lore = new ArrayList<>();
            if (buyDisabled) {
                lore.add("§7Players §ccannot buy§7 this item");
                lore.add("");
                lore.add("§aClick to ENABLE buying");
            } else {
                lore.add("§7Players §acan buy§7 this item");
                lore.add("");
                lore.add("§cClick to DISABLE buying");
            }

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSellToggleButton() {
        boolean sellDisabled = disabled("disable-sell");

        Material mat = sellDisabled ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta,
                    MessageManager.parseComponent(
                            sellDisabled ? "§c§lSELL DISABLED" : "§a§lSELL ENABLED"));

            List<String> lore = new ArrayList<>();
            if (sellDisabled) {
                lore.add("§7Players §ccannot sell§7 this item");
                lore.add("");
                lore.add("§aClick to ENABLE selling");
            } else {
                lore.add("§7Players §acan sell§7 this item");
                lore.add("");
                lore.add("§cClick to DISABLE selling");
            }

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent("§c§l◀ Back"));
            List<String> lore = new ArrayList<>();
            lore.add("§7Return to item browser");
            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFiller() {
        return org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.getFillerItem(player);
    }

    /**
     * Handle click events in this GUI
     */
    public void handleClick(int slot, boolean rightClick) {
        handleClick(slot, rightClick, false);
    }

    public void handleClick(int slot, boolean rightClick, boolean shiftClick) {
        if (!canEdit()) return;
        switch (slot) {
            // Stock adjustments
            case STOCK_MINUS_64 -> adjustStock(-64);
            case STOCK_MINUS_10 -> adjustStock(-10);
            case STOCK_MINUS_1 -> adjustStock(-1);
            case STOCK_PLUS_1 -> adjustStock(1);
            case STOCK_PLUS_10 -> adjustStock(10);
            case STOCK_PLUS_64 -> adjustStock(64);

            // Actions
            case PRICE_EDIT_SLOT -> openPriceEditor();
            case PRICE_INCREASE_SLOT -> openPriceIncreaseEditor();
            case CATEGORY_EDIT_SLOT -> cycleCategory();
            case BUY_TOGGLE_SLOT -> toggleBuy();
            case TOGGLE_ENABLED_SLOT -> toggleEnabled();
            case SELL_TOGGLE_SLOT -> toggleSell();
            case MAX_STOCK_SLOT -> openMaxStockEditor(rightClick);
            case MAX_STOCK_STORAGE_SLOT -> openMaxStockStorageEditor(rightClick);
            case DISPLAY_MATERIAL_SLOT -> { if (variantId != null) editDisplayMaterial(); }
            case REQUIRED_PERMISSION_SLOT -> { if (variantId != null) editRequiredPermission(); }
            case DELETE_SLOT -> {
                if (shiftClick) {
                    boolean removed = variantId == null ? ShopDataManager.removeItem(material)
                            : plugin.getSpecialShopManager().removeSpecialItem(variantId);
                    if (removed) {
                        player.sendMessage("§a✓ §7Shop entry permanently deleted.");
                        goBack();
                    } else player.sendMessage("§cThis shop entry could not be deleted.");
                } else player.sendMessage("§c⚠ Shift+click to confirm deletion!");
            }
            case BACK_BUTTON_SLOT -> goBack();
        }
    }

    private void adjustStock(int delta) {
        if (variantId == null) ShopDataManager.updateStock(material, delta);
        else ShopDataManager.setVariantStock(variantId, clampStock(stock() + delta));
        player.sendMessage("§a✓ §7Stock adjusted by §e" + (delta > 0 ? "+" : "") + delta);
        render();
    }

    private void openPriceEditor() {
        double currentPrice = basePrice();

        plugin.getInputManager().requestNumber(player,
                "Enter new base price for " + (variantId == null ? material.name() : variant().getName()),
                currentPrice,
                newPrice -> {
                    if (!canEdit()) return;
                    if (newPrice != null) {
                        if (!Double.isFinite(newPrice) || newPrice < 0) {
                            player.sendMessage("§c✗ §7Price must be a finite, nonnegative number!");
                        } else {
                            setOption("price", newPrice);
                            player.sendMessage("§a✓ §7Base price set to §e" + plugin.getEconomyManager().format(newPrice));
                        }
                    }
                    // Reopen this GUI with a NEW instance to avoid stale onClose handler
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!canEdit()) return;
                        AdminItemEditGUI newGUI = newEditor();
                        plugin.getShopListener().registerAdminEdit(player, newGUI);
                        newGUI.open();
                    });
                });
    }

    private void openPriceIncreaseEditor() {
        double hours = shortageHours();
        double hourlyRate = org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.hourlyIncreasePercent / 100.0;
        double currentPercent = ShopDataManager.getInflationIncreasePercent(hours);
        double maxPercent = (org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.maxPriceMultiplier - 1.0) * 100.0;
        if (currentPercent > maxPercent) currentPercent = maxPercent;

        plugin.getInputManager().requestInt(player,
                "Enter price increase % for " + (variantId == null ? material.name() : variant().getName()),
                (int) currentPercent,
                percent -> {
                    if (!canEdit()) return;
                    if (percent != null) {
                        if (percent < 0) {
                            player.sendMessage("§c✗ §7Percentage cannot be negative!");
                        } else {
                            // Reverse the formula to get the necessary hours
                            // (percent/100) + 1 = (1 + hourlyRate)^hours
                            double targetMultiplier = (percent / 100.0) + 1.0;
                            double newHours = 0.0;
                            if (hourlyRate > 0) {
                                newHours = Math.log(targetMultiplier) / Math.log(1.0 + hourlyRate);
                            }

                            if (variantId == null) {
                                ShopDataManager.setLastUpdate(material, System.currentTimeMillis());
                                ShopDataManager.setHoursInShortage(material, newHours);
                            } else {
                                ShopDataManager.setVariantLastUpdate(variantId, System.currentTimeMillis());
                                ShopDataManager.setVariantShortageHours(variantId, newHours);
                            }

                            player.sendMessage("§a✓ §7Price increase set to §c+" + percent + "%");
                        }
                    }
                    // Reopen this GUI with a NEW instance to avoid stale onClose handler
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!canEdit()) return;
                        AdminItemEditGUI newGUI = newEditor();
                        plugin.getShopListener().registerAdminEdit(player, newGUI);
                        newGUI.open();
                    });
                });
    }

    private void openMaxStockEditor(boolean rightClick) {
        if (rightClick) {
            setOption("max-stock", null);
            player.sendMessage("§a✓ §7Max stock (pricing limit) reset to global default.");
            render();
            return;
        }

        double current = org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.maxStock;
        var config = itemConfig();
        if (config != null && config.maxStock() != null) {
            current = config.maxStock();
        }

        plugin.getInputManager().requestNumber(player,
                "Enter max stock pricing curve limit",
                current,
                newVal -> {
                    if (!canEdit()) return;
                    if (newVal != null) {
                        if (!Double.isFinite(newVal) || newVal == 0) {
                            player.sendMessage("§cPricing max stock must be positive, or negative to reset.");
                        } else {
                            setOption("max-stock", newVal < 0 ? null : newVal);
                            player.sendMessage("§a✓ §7Max stock (pricing limit) updated.");
                        }
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!canEdit()) return;
                        AdminItemEditGUI newGUI = newEditor();
                        plugin.getShopListener().registerAdminEdit(player, newGUI);
                        newGUI.open();
                    });
                });
    }

    private void openMaxStockStorageEditor(boolean rightClick) {
        if (rightClick) {
            setOption("max-stock-storage", null);
            player.sendMessage("§a✓ §7Max stock storage (hard limit) reset to unlimited.");
            render();
            return;
        }

        int current = 1000000; // placeholder for unlimited
        var config = itemConfig();
        if (config != null && config.maxStockStorage() != null) {
            current = config.maxStockStorage();
        }

        plugin.getInputManager().requestInt(player,
                "Enter hard storage limit (negative for unlimited)",
                current,
                newVal -> {
                    if (!canEdit()) return;
                    if (newVal != null) {
                        setOption("max-stock-storage", newVal < 0 ? null : newVal);
                        player.sendMessage("§a✓ §7Max stock storage (hard limit) updated.");
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!canEdit()) return;
                        AdminItemEditGUI newGUI = newEditor();
                        plugin.getShopListener().registerAdminEdit(player, newGUI);
                        newGUI.open();
                    });
                });
    }

    private void cycleCategory() {
        ItemCategory current = category();
        ItemCategory[] categories = ItemCategory.values();
        int currentIndex = current.ordinal();

        // Find next valid category (skip special categories, but include visible custom
        // categories)
        for (int attempts = 0; attempts < categories.length; attempts++) {
            currentIndex = (currentIndex + 1) % categories.length;
            ItemCategory candidate = categories[currentIndex];
            if (candidate != ItemCategory.PERMISSIONS && candidate != ItemCategory.PLAYER_SHOPS
                    && (variantId != null || candidate != ItemCategory.SERVER_SHOP)
                    && org.minecraftsmp.dynamicshop.managers.CategoryConfigManager.getSlot(candidate) >= 0) break;
            if (attempts == categories.length - 1) {
                player.sendMessage("§cNo visible shop category is available.");
                return;
            }
        }

        ItemCategory newCategory = categories[currentIndex];
        setOption("category", newCategory.name());
        player.sendMessage("§a✓ §7Category changed to §b"
                + org.minecraftsmp.dynamicshop.managers.CategoryConfigManager.getDisplayName(newCategory));
        render();
    }

    private void toggleEnabled() {
        boolean currentlyDisabled = disabled("disabled");
        setOption("disabled", !currentlyDisabled);

        if (currentlyDisabled) {
            player.sendMessage("§a✓ §7Item §aenabled§7 in the shop");
        } else {
            player.sendMessage("§a✓ §7Item §cdisabled§7 from the shop");
        }
        render();
    }

    private void toggleBuy() {
        boolean currentlyDisabled = disabled("disable-buy");
        setOption("disable-buy", !currentlyDisabled);

        if (currentlyDisabled) {
            player.sendMessage("§a✓ §7Buying §aenabled§7 for this item");
        } else {
            player.sendMessage("§a✓ §7Buying §cdisabled§7 for this item");
        }
        render();
    }

    private void toggleSell() {
        boolean currentlyDisabled = disabled("disable-sell");
        setOption("disable-sell", !currentlyDisabled);

        if (currentlyDisabled) {
            player.sendMessage("§a✓ §7Selling §aenabled§7 for this item");
        } else {
            player.sendMessage("§a✓ §7Selling §cdisabled§7 for this item");
        }
        render();
    }

    private void goBack() {
        // Return to parent browse GUI
        plugin.getShopListener().unregisterAdminEdit(player);
        plugin.getShopListener().registerAdminBrowse(player, parentGUI);
        parentGUI.loadItemsForCategory();
        parentGUI.render();
        player.openInventory(parentGUI.getInventory());
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Material getMaterial() {
        return material;
    }

    public String getVariantId() { return variantId; }

    private SpecialShopItem variant() {
        return plugin.getSpecialShopManager().getSpecialItem(variantId);
    }

    private boolean canEdit() {
        if (variantId == null ? ShopDataManager.itemConfigs.containsKey(material)
                : variant() != null && variant().isTradeItem()) return true;
        player.sendMessage("§cThis shop item was removed or changed. Reopen the shop editor.");
        return false;
    }

    private double basePrice() {
        return variantId == null ? ShopDataManager.getBasePrice(material) : variant().getPrice();
    }

    private double stock() {
        return variantId == null ? ShopDataManager.getStock(material) : ShopDataManager.getVariantStock(variantId);
    }

    private double shortageHours() {
        return variantId == null ? ShopDataManager.getHoursInShortage(material)
                : ShopDataManager.getVariantShortageHours(variantId);
    }

    private ItemCategory category() {
        return variantId == null ? ShopDataManager.detectCategory(material) : variant().getCategory();
    }

    private ShopDataManager.ShopItemConfig itemConfig() {
        return variantId == null ? ShopDataManager.itemConfigs.get(material)
                : ShopDataManager.getVariantConfig(variantId, material);
    }

    private boolean disabled(String option) {
        if (variantId != null) return plugin.getSpecialShopManager().isTradeDisabled(variantId, option);
        return switch (option) {
            case "disabled" -> ShopDataManager.isItemDisabled(material);
            case "disable-buy" -> ShopDataManager.isBuyDisabled(material);
            case "disable-sell" -> ShopDataManager.isSellDisabled(material);
            default -> throw new IllegalArgumentException(option);
        };
    }

    private double clampStock(double stock) {
        var config = itemConfig();
        if (config != null) {
            if (config.maxStockStorage() != null) stock = Math.min(stock, config.maxStockStorage());
            if (config.minStockStorage() != null) stock = Math.max(stock, config.minStockStorage());
        }
        return stock;
    }

    private void setOption(String option, Object value) {
        if (variantId != null) {
            if (!canEdit()) return;
            plugin.getConfig().set("special_items." + variantId + "." + option, value);
            plugin.saveConfig();
            plugin.getSpecialShopManager().reload();
            return;
        }
        switch (option) {
            case "price" -> ShopDataManager.setBasePrice(material, (Double) value);
            case "max-stock" -> ShopDataManager.setMaxStock(material, (Double) value);
            case "max-stock-storage" -> ShopDataManager.setMaxStockStorage(material, (Integer) value);
            case "category" -> ShopDataManager.setCategoryOverride(material, ItemCategory.valueOf((String) value));
            case "disabled" -> ShopDataManager.setItemDisabled(material, (Boolean) value);
            case "disable-buy" -> ShopDataManager.setBuyDisabled(material, (Boolean) value);
            case "disable-sell" -> ShopDataManager.setSellDisabled(material, (Boolean) value);
            default -> throw new IllegalArgumentException(option);
        }
    }

    private AdminItemEditGUI newEditor() {
        return variantId == null ? new AdminItemEditGUI(plugin, player, material, parentGUI)
                : new AdminItemEditGUI(plugin, player, variant(), parentGUI);
    }

    private void reopenEditor() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!canEdit()) return;
            AdminItemEditGUI next = newEditor();
            plugin.getShopListener().registerAdminEdit(player, next);
            next.open();
        });
    }

    private void editDisplayMaterial() {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            player.sendMessage("§cHold an item to set as the display material!");
            return;
        }
        setOption("display_material", held.getType().name());
        render();
    }

    private void editRequiredPermission() {
        var entry = variant();
        plugin.getInputManager().requestText(player, "Required Permission",
                entry.hasRequiredPermission() ? entry.getRequiredPermission() : "none", permission -> {
                    if (!canEdit()) return;
                    if (permission != null) setOption("required_permission",
                            permission.isBlank() || permission.equalsIgnoreCase("none") ? null : permission);
                    reopenEditor();
                });
    }
}
