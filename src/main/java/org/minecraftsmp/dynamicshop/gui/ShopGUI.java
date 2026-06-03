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
import org.minecraftsmp.dynamicshop.managers.ProtocolShopManager;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import org.minecraftsmp.dynamicshop.managers.ConfigCacheManager;
import org.minecraftsmp.dynamicshop.util.ShopItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ShopGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final ItemCategory category;
    private final int size;
    private final ProtocolShopManager pm;
    private final int itemsPerPage;
    private final int[] itemSlots; // Maps item index -> inventory slot (inner grid only)
    private Inventory inventory; // Store the inventory reference

    private List<Material> allItems; // MASTER list of items in this category
    private List<Material> displayItems; // FILTERED list for rendering
    private List<SpecialShopItem> specialItems; // Special items (for PERMISSIONS/SERVER_SHOP)
    private int page = 0;
    private int maxPage = 0;
    private boolean hideOutOfStock = false;
    private final boolean commandOpened;

    public ShopGUI(DynamicShop plugin, Player player, ItemCategory category) {
        this(plugin, player, category, false);
    }

    public ShopGUI(DynamicShop plugin, Player player, ItemCategory category, boolean commandOpened) {
        this.plugin = plugin;
        this.player = player;
        this.commandOpened = commandOpened;
        this.category = category;

        this.size = plugin.getConfig().getInt("gui.shop_menu_size", 54);
        this.pm = plugin.getProtocolShopManager();

        // Build item slot list: inner columns (1-7) on rows 1 through (totalRows-2)
        // Row 0 = top border, last row = navigation, cols 0 & 8 = side borders
        int totalRows = size / 9;
        java.util.List<Integer> slots = new java.util.ArrayList<>();
        for (int row = 1; row < totalRows - 1; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        this.itemSlots = slots.stream().mapToInt(Integer::intValue).toArray();
        this.itemsPerPage = itemSlots.length;

        // Load items based on category type
        if (category == ItemCategory.PERMISSIONS || category == ItemCategory.SERVER_SHOP) {
            // Load special items from SpecialShopManager
            this.specialItems = plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                    .filter(item -> item.getCategory() == category)
                    .collect(Collectors.toList());
            this.allItems = List.of(); // Empty regular items
            this.displayItems = List.of();

            // Calculate pagination based on special items
            this.maxPage = specialItems.isEmpty() ? 0 : (specialItems.size() - 1) / itemsPerPage;
        } else {
            // Load regular items from ShopDataManager
            this.allItems = ShopDataManager.getItemsInCategory(category);
            if (allItems == null)
                allItems = List.of();

            // Also load any special items assigned to this category (e.g., enchanted variants)
            this.specialItems = plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                    .filter(item -> item.getCategory() == category)
                    .collect(Collectors.toList());

            updateDisplayItems(); // Populate displayItems based on initial state
        }
    }

    /**
     * Open the GUI for the player.
     */
    public void open() {
        // Remove any stale tracking first
        plugin.getShopListener().unregisterCategory(player);
        plugin.getShopListener().unregisterShop(player); // just in case

        // Build the title using the message key (supports Nexo glyphs in messages_nexo_example.yml)
        java.util.Map<String, String> titlePlaceholders = new java.util.HashMap<>();
        titlePlaceholders.put("category", org.minecraftsmp.dynamicshop.managers.CategoryConfigManager.getDisplayName(category));
        String title = plugin.getMessageManager().getMessage("shop-gui-title", titlePlaceholders);
        if (title == null) title = org.minecraftsmp.dynamicshop.managers.CategoryConfigManager.getDisplayName(category);

        inventory = pm.createVirtualInventory(player, size, title);
        render();
        player.openInventory(inventory);

        plugin.getShopListener().registerShop(player, this);
        plugin.getShopListener().updatePlayerInventoryLore(player, 2L);

        // Update player inventory lore AFTER the GUI is open
        player.getScheduler().runDelayed(plugin, task -> {
            plugin.getShopListener().updatePlayerInventoryLore(player);
        }, null, 3L);
    }

    /**
     * Renders the entire GUI page — all items + navigation bar.
     */
    public void render() {
        // Clear all non-navigation slots (top row + border columns + item area)
        int navRow = size - 9;
        for (int i = 0; i < navRow; i++) {
            pm.sendSlot(inventory, i, null);
        }

        // Fill border slots with filler (top row + side columns)
        ItemStack filler = org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.getFillerItem();
        // Top row (slot 4 = home button aligned with the Spectra texture house icon)
        for (int col = 0; col < 9; col++) {
            if (col == 4 && !commandOpened) {
                pm.sendSlot(inventory, col, new ItemStack(Material.AIR));
            } else {
                pm.sendSlot(inventory, col, filler);
            }
        }
        // Side columns (rows 1 through navRow-1)
        int totalRows = size / 9;
        for (int row = 1; row < totalRows - 1; row++) {
            pm.sendSlot(inventory, row * 9, filler);     // left column
            pm.sendSlot(inventory, row * 9 + 8, filler); // right column
        }

        // Render based on category type
        if (category == ItemCategory.PERMISSIONS || category == ItemCategory.SERVER_SHOP) {
            // Pure special-item categories
            int start = page * itemsPerPage;
            int end = Math.min(start + itemsPerPage, specialItems.size());

            for (int i = start; i < end; i++) {
                SpecialShopItem specialItem = specialItems.get(i);
                int slot = itemSlots[i - start];

                ItemStack displayItem = buildSpecialShopItem(specialItem);
                pm.sendSlot(inventory, slot, displayItem);
            }
        } else {
            // Regular categories — render regular items first, then any special items
            int totalCombined = displayItems.size() + specialItems.size();
            int start = page * itemsPerPage;
            int end = Math.min(start + itemsPerPage, totalCombined);

            for (int i = start; i < end; i++) {
                int slot = itemSlots[i - start];

                if (i < displayItems.size()) {
                    // Regular item
                    Material mat = displayItems.get(i);
                    ItemStack displayItem = buildShopItem(mat);
                    pm.sendSlot(inventory, slot, displayItem);
                } else {
                    // Special item (enchanted variant, etc.)
                    int specialIdx = i - displayItems.size();
                    SpecialShopItem specialItem = specialItems.get(specialIdx);
                    ItemStack displayItem = buildSpecialShopItem(specialItem);
                    pm.sendSlot(inventory, slot, displayItem);
                }
            }
        }

        // Render navigation
        renderNavigation();
    }

    private void updateDisplayItems() {
        if (allItems == null) {
            displayItems = List.of();
            maxPage = 0;
            return;
        }

        if (hideOutOfStock) {
            displayItems = allItems.stream()
                    .filter(mat -> ShopDataManager.getStock(mat) > 0)
                    .collect(Collectors.toList());
        } else {
            displayItems = new ArrayList<>(allItems);
        }

        // Recalculate max page (include special items in the total)
        int totalCombined = displayItems.size() + specialItems.size();
        this.maxPage = totalCombined <= 0 ? 0 : (totalCombined - 1) / itemsPerPage;

        // Safety check: if current page exceeds maxPage (due to filtering), reset to 0
        if (page > maxPage) {
            page = 0;
        }
    }

    public void toggleHideOutOfStock() {
        this.hideOutOfStock = !this.hideOutOfStock;
        updateDisplayItems();
        // Re-render (render() clears all non-nav slots first)
        render();
    }

    /**
     * Build display item for special shop items (permissions/server-shop)
     */
    private ItemStack buildSpecialShopItem(SpecialShopItem specialItem) {
        // Use the saved display material from the item
        ItemStack item = null;
        if ("itemsadder".equalsIgnoreCase(specialItem.getDeliveryMethod()) && specialItem.getNbt() != null) {
            item = ItemsAdderWrapper.getItem(specialItem.getNbt());
        } else if ("nexo".equalsIgnoreCase(specialItem.getDeliveryMethod()) && specialItem.getNbt() != null) {
            item = NexoWrapper.getItem(specialItem.getNbt());
        } else if ("valhallammo".equalsIgnoreCase(specialItem.getDeliveryMethod()) && specialItem.getNbt() != null) {
            item = org.minecraftsmp.dynamicshop.managers.ValhallaMMOWrapper.getItem(specialItem.getNbt());
        } else if ("component".equalsIgnoreCase(specialItem.getDeliveryMethod()) || "stored_item".equalsIgnoreCase(specialItem.getDeliveryMethod())) {
            String configPath = "special_items." + specialItem.getId() + ".stored_item";
            if (plugin.getConfig().contains(configPath)) {
                item = plugin.getConfig().getItemStack(configPath);
            }
        }

        if (item == null) {
            item = new ItemStack(specialItem.getDisplayMaterial(), 1);
        } else {
            item = item.clone();
            item.setAmount(1);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Display name: use the configured name from the special item,
            // falling back to the stored_item's existing name, then prettified material name
            if (!meta.hasDisplayName()) {
                String displayName = specialItem.getDisplayName();
                // If the display name is just the auto-generated ID, prettify the material name instead
                if (displayName == null || displayName.equals(specialItem.getId())) {
                    displayName = specialItem.getDisplayMaterial() != null
                            ? prettifyMaterialName(specialItem.getDisplayMaterial().name())
                            : specialItem.getId();
                }
                meta.displayName(MessageManager.parseComponent("§e§l" + displayName));
            }

            Material baseMat = specialItem.getDisplayMaterial();
            boolean hasDynamicPrice = baseMat != null
                    && ShopDataManager.itemConfigs.containsKey(baseMat)
                    && specialItem.getPrice() > 0;

            // stored_item variants in regular categories: render like a normal shop item
            if ("stored_item".equalsIgnoreCase(specialItem.getDeliveryMethod()) && hasDynamicPrice
                    && category != ItemCategory.PERMISSIONS && category != ItemCategory.SERVER_SHOP) {
                List<String> lore = new ArrayList<>();

                boolean buyDisabled = ShopDataManager.isBuyDisabled(baseMat);
                boolean sellDisabled = ShopDataManager.isSellDisabled(baseMat);

                String variantId = specialItem.getId();
                ShopDataManager.initializeVariantData(variantId, baseMat);
                double specialBasePrice = specialItem.getPrice();

                // Buy price
                if (!buyDisabled) {
                    double buyPrice = ShopDataManager.getTotalVariantBuyCost(variantId, baseMat, specialBasePrice, 1);
                    java.util.Map<String, String> buyPlaceholders = new java.util.HashMap<>();
                    buyPlaceholders.put("price", plugin.getEconomyManager().format(buyPrice));
                    MessageManager.addLoreIfNotEmpty(lore,
                            plugin.getMessageManager().getMessage("shop-lore-buy-price", buyPlaceholders));
                }

                // Sell price
                if (!sellDisabled) {
                    double sellPrice = ShopDataManager.getTotalVariantSellValue(variantId, baseMat, specialBasePrice, 1);
                    java.util.Map<String, String> sellPlaceholders = new java.util.HashMap<>();
                    sellPlaceholders.put("price", plugin.getEconomyManager().format(sellPrice));
                    MessageManager.addLoreIfNotEmpty(lore,
                            plugin.getMessageManager().getMessage("shop-lore-sell-price", sellPlaceholders));
                }

                // Stock info
                if (!buyDisabled) {
                    double stock = ShopDataManager.getVariantStock(variantId);
                    if (stock < 0) {
                        java.util.Map<String, String> stockPlaceholders = new java.util.HashMap<>();
                        stockPlaceholders.put("stock", String.format("%.0f", stock));
                        MessageManager.addLoreIfNotEmpty(lore,
                                plugin.getMessageManager().getMessage("lore-stock-negative", stockPlaceholders));

                        double hours = ShopDataManager.getVariantShortageHours(variantId);
                        double hourlyRate = ConfigCacheManager.hourlyIncreasePercent / 100.0;
                        double multiplier = Math.pow(1.0 + hourlyRate, hours);
                        double percentIncrease = (multiplier - 1.0) * 100.0;
                        double maxPercent = (ConfigCacheManager.maxPriceMultiplier - 1.0) * 100.0;
                        boolean capped = percentIncrease >= maxPercent;
                        if (capped) percentIncrease = maxPercent;

                        java.util.Map<String, String> percentPlaceholders = new java.util.HashMap<>();
                        percentPlaceholders.put("percent", String.format("%,.0f", percentIncrease) + (capped ? " (MAX)" : ""));
                        percentPlaceholders.put("hourly_rate", String.format("%.1f", ConfigCacheManager.hourlyIncreasePercent));
                        MessageManager.addLoreIfNotEmpty(lore,
                                plugin.getMessageManager().getMessage("shop-lore-price-increase", percentPlaceholders));
                        MessageManager.addLoreIfNotEmpty(lore,
                                plugin.getMessageManager().getMessage("shop-lore-price-increase-note", percentPlaceholders));
                    } else if (stock == 0) {
                        MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().getMessage("lore-out-of-stock"));

                        double hours = ShopDataManager.getVariantShortageHours(variantId);
                        double hourlyRate = ConfigCacheManager.hourlyIncreasePercent / 100.0;
                        double multiplier = Math.pow(1.0 + hourlyRate, hours);
                        double percentIncrease = (multiplier - 1.0) * 100.0;
                        double maxPercent = (ConfigCacheManager.maxPriceMultiplier - 1.0) * 100.0;
                        boolean capped = percentIncrease >= maxPercent;
                        if (capped) percentIncrease = maxPercent;

                        java.util.Map<String, String> percentPlaceholders = new java.util.HashMap<>();
                        percentPlaceholders.put("percent", String.format("%,.0f", percentIncrease) + (capped ? " (MAX)" : ""));
                        percentPlaceholders.put("hourly_rate", String.format("%.1f", ConfigCacheManager.hourlyIncreasePercent));
                        MessageManager.addLoreIfNotEmpty(lore,
                                plugin.getMessageManager().getMessage("shop-lore-price-increase", percentPlaceholders));
                        MessageManager.addLoreIfNotEmpty(lore,
                                plugin.getMessageManager().getMessage("shop-lore-price-increase-note", percentPlaceholders));
                    } else {
                        java.util.Map<String, String> stockPlaceholders = new java.util.HashMap<>();
                        stockPlaceholders.put("stock", String.format("%.0f", stock));
                        MessageManager.addLoreIfNotEmpty(lore,
                                plugin.getMessageManager().getMessage("lore-stock", stockPlaceholders));
                        if (stock < 10) {
                            MessageManager.addLoreIfNotEmpty(lore,
                                    plugin.getMessageManager().getMessage("shop-lore-low-stock"));
                        }
                    }
                }

                lore.add("");
                if (ConfigCacheManager.useDialogGui) {
                    MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().getMessage("dialog-lore-click-to-open"));
                } else {
                    if (!buyDisabled) {
                        MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().getMessage("shop-lore-left-click-buy"));
                        MessageManager.addLoreIfNotEmpty(lore,
                                plugin.getMessageManager().getMessage("shop-lore-shift-left-click-buy"));
                    }
                    if (!sellDisabled) {
                        MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().getMessage("shop-lore-right-click-sell"));
                        MessageManager.addLoreIfNotEmpty(lore,
                                plugin.getMessageManager().getMessage("shop-lore-shift-right-click-sell"));
                    }
                }

                meta.lore(lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
                item.setItemMeta(meta);
                return item;
            }

            // --- Original special item lore (permissions, server-shop, nexo, etc.) ---
            List<String> lore = new ArrayList<>();
            lore.add("§7───────────────────");

            if (hasDynamicPrice) {
                // Use the base material's dynamic pricing
                double buyPrice = ShopDataManager.getTotalBuyCost(baseMat, 1);
                double sellPrice = ShopDataManager.getTotalSellValue(baseMat, 1);
                String buyFormatted = plugin.getEconomyManager().format(buyPrice);
                String sellFormatted = plugin.getEconomyManager().format(sellPrice);

                boolean buyDisabled = ShopDataManager.isBuyDisabled(baseMat);
                boolean sellDisabled = ShopDataManager.isSellDisabled(baseMat);

                if (!buyDisabled) {
                    lore.add("§a§lBUY: §f" + buyFormatted);
                }
                if (!sellDisabled && sellPrice > 0) {
                    lore.add("§c§lSELL: §f" + sellFormatted);
                }
            } else {
                // Fall back to the special item's fixed price
                String priceFormatted = plugin.getEconomyManager().format(specialItem.getPrice());
                lore.add("§a§lBUY: §f" + priceFormatted);
            }

            lore.add("§7");

            // Type-specific info
            if (specialItem.isPermissionItem()) {
                lore.add("§7Type: §dPermission");
                lore.add("§7Node: §f" + specialItem.getPermission());

                // Check if player already owns it
                if (plugin.getPermissionsManager().hasPermission(player, specialItem.getPermission(), specialItem.getPermissionWorld())) {
                    lore.add("§7");
                    lore.add("§a✔ You already own this!");
                }
            } else if (specialItem.isServerShopItem()) {
                // Show stock info using the base material's stock pool
                if (baseMat != null) {
                    double stock = ShopDataManager.getStock(baseMat);
                    if (stock < 0) {
                        java.util.Map<String, String> stPh = new java.util.HashMap<>();
                        stPh.put("stock", String.format("%.0f", stock));
                        lore.add(plugin.getMessageManager().getMessage("lore-stock-negative", stPh));
                    } else if (stock == 0) {
                        lore.add(plugin.getMessageManager().getMessage("lore-out-of-stock"));
                    } else {
                        java.util.Map<String, String> stPh = new java.util.HashMap<>();
                        stPh.put("stock", String.format("%.0f", stock));
                        lore.add(plugin.getMessageManager().getMessage("lore-stock", stPh));
                        if (stock < 10) {
                            lore.add(plugin.getMessageManager().getMessage("shop-lore-low-stock"));
                        }
                    }
                }
            }

            lore.add("§7───────────────────");

            // Instructions
            if (ConfigCacheManager.useDialogGui) {
                MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().getMessage("dialog-lore-click-to-open"));
            } else {
                lore.add("§eLeft-click to BUY");
            }

            meta.lore(lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }

        return item;
    }

    private static String prettifyMaterialName(String name) {
        String[] parts = name.split("_");
        StringBuilder out = new StringBuilder();
        for (String s : parts) {
            if (s.isEmpty()) continue;
            out.append(s.substring(0, 1).toUpperCase());
            out.append(s.substring(1).toLowerCase());
            out.append(" ");
        }
        return out.toString().trim();
    }

    // build the item with lore
    private ItemStack buildShopItem(Material mat) {
        double price = ShopDataManager.getTotalBuyCost(mat, 1);
        double sellPrice = ShopDataManager.getTotalSellValue(mat, 1);
        double stock = ShopDataManager.getStock(mat);

        ItemStack item;
        try {
            // Use template if available (preserves enchantments, custom name, lore, etc.)
            ItemStack template = ShopDataManager.getTemplate(mat);
            if (template != null) {
                item = template.clone();
                item.setAmount(1);
            } else {
                item = new ItemStack(mat, 1);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid shop item material skipped: " + mat);
            // Return a placeholder item so the GUI doesn't break entirely
            item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(MessageManager.parseComponent("§c§lINVALID ITEM"));
                List<String> lore = new ArrayList<>();
                lore.add("§7Material: " + mat);
                lore.add("§cThis item is invalid");
                lore.add("§cin this version.");
                meta.lore(lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
                item.setItemMeta(meta);
            }
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Display name priority: custom_name > template's name > prettified material name
            String customName = ShopDataManager.getCustomName(mat);
            if (customName != null) {
                // Custom name always wins — set BOTH to override any template name
                net.kyori.adventure.text.Component nameComponent = MessageManager.parseComponent("§e§l" + customName);
                meta.displayName(nameComponent);
                meta.itemName(nameComponent);
            } else if (!meta.hasDisplayName()) {
                // No custom name and no template name — use prettified material name
                meta.displayName(
                        MessageManager.parseComponent("§e§l" + mat.name().replace("_", " ")));
            }

            List<String> lore = new ArrayList<>();

            boolean buyDisabled = ShopDataManager.isBuyDisabled(mat);
            boolean sellDisabled = ShopDataManager.isSellDisabled(mat);

            // Buy price (hide if buy disabled)
            if (!buyDisabled) {
                java.util.Map<String, String> buyPlaceholders = new java.util.HashMap<>();
                buyPlaceholders.put("price", plugin.getEconomyManager().format(price));
                MessageManager.addLoreIfNotEmpty(lore,
                        plugin.getMessageManager().getMessage("shop-lore-buy-price", buyPlaceholders));
            }

            // Sell price (hide if sell disabled)
            if (!sellDisabled) {
                java.util.Map<String, String> sellPlaceholders = new java.util.HashMap<>();
                sellPlaceholders.put("price", plugin.getEconomyManager().format(sellPrice));
                MessageManager.addLoreIfNotEmpty(lore,
                        plugin.getMessageManager().getMessage("shop-lore-sell-price", sellPlaceholders));
            }

            // Stock info (hide if buy disabled)
            if (!buyDisabled) {
                if (stock < 0) {
                    java.util.Map<String, String> stockPlaceholders = new java.util.HashMap<>();
                    stockPlaceholders.put("stock", String.format("%.0f", stock));
                    MessageManager.addLoreIfNotEmpty(lore,
                            plugin.getMessageManager().getMessage("lore-stock-negative", stockPlaceholders));

                    // Show price increase for negative stock too
                    double hours = ShopDataManager.getHoursInShortage(mat);
                    double hourlyRate = ConfigCacheManager.hourlyIncreasePercent / 100.0;
                    double multiplier = Math.pow(1.0 + hourlyRate, hours);
                    double percentIncrease = (multiplier - 1.0) * 100.0;
                    double maxPercent = (ConfigCacheManager.maxPriceMultiplier - 1.0) * 100.0;
                    boolean capped = percentIncrease >= maxPercent;
                    if (capped) percentIncrease = maxPercent;

                    java.util.Map<String, String> percentPlaceholders = new java.util.HashMap<>();
                    percentPlaceholders.put("percent", String.format("%,.0f", percentIncrease) + (capped ? " (MAX)" : ""));
                    percentPlaceholders.put("hourly_rate", String.format("%.1f", ConfigCacheManager.hourlyIncreasePercent));
                    MessageManager.addLoreIfNotEmpty(lore,
                            plugin.getMessageManager().getMessage("shop-lore-price-increase", percentPlaceholders));
                    MessageManager.addLoreIfNotEmpty(lore,
                            plugin.getMessageManager().getMessage("shop-lore-price-increase-note", percentPlaceholders));
                } else if (stock == 0) {
                    MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().getMessage("lore-out-of-stock"));

                    // Show price increase for zero stock
                    double hours = ShopDataManager.getHoursInShortage(mat);
                    double hourlyRate = ConfigCacheManager.hourlyIncreasePercent / 100.0;
                    double multiplier = Math.pow(1.0 + hourlyRate, hours);
                    double percentIncrease = (multiplier - 1.0) * 100.0;
                    double maxPercent = (ConfigCacheManager.maxPriceMultiplier - 1.0) * 100.0;
                    boolean capped = percentIncrease >= maxPercent;
                    if (capped) percentIncrease = maxPercent;

                    java.util.Map<String, String> percentPlaceholders = new java.util.HashMap<>();
                    percentPlaceholders.put("percent", String.format("%,.0f", percentIncrease) + (capped ? " (MAX)" : ""));
                    percentPlaceholders.put("hourly_rate", String.format("%.1f", ConfigCacheManager.hourlyIncreasePercent));
                    MessageManager.addLoreIfNotEmpty(lore,
                            plugin.getMessageManager().getMessage("shop-lore-price-increase", percentPlaceholders));
                    MessageManager.addLoreIfNotEmpty(lore,
                            plugin.getMessageManager().getMessage("shop-lore-price-increase-note", percentPlaceholders));
                } else {
                    java.util.Map<String, String> stockPlaceholders = new java.util.HashMap<>();
                    stockPlaceholders.put("stock", String.format("%.0f", stock));
                    MessageManager.addLoreIfNotEmpty(lore,
                            plugin.getMessageManager().getMessage("lore-stock", stockPlaceholders));
                    if (stock < 10) {
                        MessageManager.addLoreIfNotEmpty(lore,
                                plugin.getMessageManager().getMessage("shop-lore-low-stock"));
                    }
                }
            }

            lore.add("");
            if (ConfigCacheManager.useDialogGui) {
                // Dialog mode: show simple 'click to open' instead of buy/sell instructions
                MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().getMessage("dialog-lore-click-to-open"));
            } else {
                if (!buyDisabled) {
                    MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().getMessage("shop-lore-left-click-buy"));
                    MessageManager.addLoreIfNotEmpty(lore,
                            plugin.getMessageManager().getMessage("shop-lore-shift-left-click-buy"));
                }
                if (!sellDisabled) {
                    MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().getMessage("shop-lore-right-click-sell"));
                    MessageManager.addLoreIfNotEmpty(lore,
                            plugin.getMessageManager().getMessage("shop-lore-shift-right-click-sell"));
                }
            }

            meta.lore(lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Clears all visual items.
     */
    public void clear() {
        if (inventory != null) {
            inventory.clear();
        }
    }

    /**
     * Renders navigation area (bottom row).
     */
    private void renderNavigation() {
        int navRow = size - 9; // Bottom row starts here

        ItemStack filler = org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.getFillerItem();

        // Fill unused nav slots with filler first
        for (int i = 0; i < 9; i++) {
            pm.sendSlot(inventory, navRow + i, filler);
        }

        // Helper to get strings safely
        String prevName = plugin.getMessageManager().getMessage("gui-nav-previous");
        if (prevName == null) prevName = "§ePrevious Page";
        String prevLore = page > 0 ? plugin.getMessageManager().getMessage("gui-nav-previous-lore") : plugin.getMessageManager().getMessage("gui-nav-previous-none");
        if (prevLore == null) prevLore = page > 0 ? "§7Click to go back" : "§cNo previous page";
        
        // Left Arrow - Previous Page
        ItemStack prevPage = ShopItemBuilder.navItemNexo(prevName, "shop_back_button", Material.ARROW, prevLore);
        pm.sendSlot(inventory, navRow + 0, prevPage);

        String nextName = plugin.getMessageManager().getMessage("gui-nav-next");
        if (nextName == null) nextName = "§eNext Page";
        String nextLore = page < maxPage ? plugin.getMessageManager().getMessage("gui-nav-next-lore") : plugin.getMessageManager().getMessage("gui-nav-next-none");
        if (nextLore == null) nextLore = page < maxPage ? "§7Click to go forward" : "§cNo next page";
        
        // Right Arrow - Next Page
        ItemStack nextPage = ShopItemBuilder.navItemNexo(nextName, "shop_next_button", Material.ARROW, nextLore);
        pm.sendSlot(inventory, navRow + 8, nextPage);

        // X - Back to Categories (Red X) — hidden when opened via command
        if (commandOpened) {
            pm.sendSlot(inventory, navRow + 4, filler);
        } else {
            String backName = plugin.getMessageManager().getMessage("gui-nav-back");
            if (backName == null) backName = "§c§lBack to Categories";
            String backLore = plugin.getMessageManager().getMessage("gui-nav-back-lore");
            if (backLore == null) backLore = "§7Return to category selection";
            
            ItemStack backToCategories = ShopItemBuilder.navItemNexo(backName, "shop_categories_button", Material.BARRIER, backLore);
            pm.sendSlot(inventory, navRow + 4, backToCategories);
        }

        // Compass - Search (Anvil GUI) — hidden when opened via command
        if (!commandOpened) {
            String searchName = plugin.getMessageManager().getMessage("gui-nav-search");
            if (searchName == null) searchName = "§b§lSearch Items";
            String searchLore = plugin.getMessageManager().getMessage("gui-nav-search-lore");
            if (searchLore == null) searchLore = "§7Open search menu";
            
            ItemStack search = ShopItemBuilder.navItemNexo(searchName, "shop_search_button", Material.COMPASS, searchLore);
            pm.sendSlot(inventory, navRow + 3, search);
        }

        // Page Info
        int totalItems = (category == ItemCategory.PERMISSIONS || category == ItemCategory.SERVER_SHOP)
                ? specialItems.size()
                : displayItems.size();
                
        java.util.Map<String, String> pagePlaceholders = new java.util.HashMap<>();
        pagePlaceholders.put("page", String.valueOf(page + 1));
        pagePlaceholders.put("max", String.valueOf(maxPage + 1));
        pagePlaceholders.put("total", String.valueOf(totalItems));
        
        String pageName = plugin.getMessageManager().getMessage("gui-nav-page", pagePlaceholders);
        if (pageName == null) pageName = "§ePage §f" + (page + 1) + " §7/ §f" + (maxPage + 1);
        
        String pageLoreStr = plugin.getMessageManager().getMessage("gui-nav-page-lore", pagePlaceholders);
        if (pageLoreStr == null) pageLoreStr = "§7Total items: §e" + totalItems;
        
        ItemStack pageInfo = ShopItemBuilder.navItemNexo(pageName, "shop_page_button", Material.PAPER, pageLoreStr);
        pm.sendSlot(inventory, navRow + 5, pageInfo);

        // Filter Toggle (Hopper)
        if (category != ItemCategory.PERMISSIONS && category != ItemCategory.SERVER_SHOP) {
            String filterName = plugin.getMessageManager().getMessage("gui-nav-filter");
            if (filterName == null) filterName = "§6Filter Options";
            
            String filterState = hideOutOfStock 
                    ? plugin.getMessageManager().getMessage("gui-nav-filter-hidden") 
                    : plugin.getMessageManager().getMessage("gui-nav-filter-shown");
            if (filterState == null) filterState = hideOutOfStock ? "§aCurrently: §fHiding Out of Stock" : "§cCurrently: §fShowing All";
            
            String filterLoreStr = plugin.getMessageManager().getMessage("gui-nav-filter-lore");
            if (filterLoreStr == null) filterLoreStr = "§7Click to toggle";
            
            ItemStack filterItem = ShopItemBuilder.navItemNexo(filterName, "shop_filter_button", Material.HOPPER, filterState, filterLoreStr);
            pm.sendSlot(inventory, navRow + 2, filterItem);
        }
    }

    /**
     * Moves a page forward.
     */
    public void nextPage() {
        if (page < maxPage) {
            page++;
            render();
        }
    }

    /**
     * Moves a page backwards.
     */
    public void prevPage() {
        if (page > 0) {
            page--;
            render();
        }
    }

    public ItemCategory getCategory() {
        return category;
    }

    public boolean isCommandOpened() {
        return commandOpened;
    }

    public int getSize() {
        return size;
    }

    public int getPage() {
        return page;
    }

    public boolean isNavigationSlot(int rawSlot) {
        return rawSlot >= (size - 9);
    }

    /**
     * Check if a slot is a border/filler slot (not an item slot and not navigation)
     */
    public boolean isBorderSlot(int rawSlot) {
        if (isNavigationSlot(rawSlot)) return false;
        for (int itemSlot : itemSlots) {
            if (itemSlot == rawSlot) return false;
        }
        return true;
    }

    /**
     * Convert a raw inventory slot to an item index within the current page.
     * Returns -1 if the slot is not a valid item slot.
     */
    private int slotToItemIndex(int rawSlot) {
        for (int i = 0; i < itemSlots.length; i++) {
            if (itemSlots[i] == rawSlot) {
                return (page * itemsPerPage) + i;
            }
        }
        return -1;
    }

    /**
     * Returns the Material that corresponds to the clicked slot (for regular
     * items).
     * IMPORTANT: Because this is a virtual GUI, we compute it instead of reading
     * inventory.
     */
    public Material getItemFromSlot(int clickedSlot) {
        if (isNavigationSlot(clickedSlot))
            return null;
        if (category == ItemCategory.PERMISSIONS || category == ItemCategory.SERVER_SHOP)
            return null;

        int index = slotToItemIndex(clickedSlot);

        // Index falls in regular items range
        if (index < 0 || index >= displayItems.size())
            return null;
        return displayItems.get(index);
    }

    /**
     * Returns the SpecialShopItem that corresponds to the clicked slot.
     * Works for both pure special categories (PERMISSIONS, SERVER_SHOP) and
     * regular categories that contain special item variants (e.g., enchanted tools).
     */
    public SpecialShopItem getSpecialItemFromSlot(int clickedSlot) {
        if (isNavigationSlot(clickedSlot))
            return null;

        int index = slotToItemIndex(clickedSlot);
        if (index < 0) return null;

        if (category == ItemCategory.PERMISSIONS || category == ItemCategory.SERVER_SHOP) {
            // Pure special category
            if (index >= specialItems.size()) return null;
            return specialItems.get(index);
        } else {
            // Mixed category: special items come after regular items
            int specialIdx = index - displayItems.size();
            if (specialIdx < 0 || specialIdx >= specialItems.size()) return null;
            return specialItems.get(specialIdx);
        }
    }

    public Inventory getInventory() {
        return inventory;
    }
}
