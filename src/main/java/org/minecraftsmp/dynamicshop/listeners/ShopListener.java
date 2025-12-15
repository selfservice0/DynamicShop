package org.minecraftsmp.dynamicshop.listeners;

import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.category.SpecialShopItem;
import org.minecraftsmp.dynamicshop.gui.AdminItemEditGUI;
import org.minecraftsmp.dynamicshop.gui.AdminShopBrowseGUI;
import org.minecraftsmp.dynamicshop.gui.CategorySelectionGUI;
import org.minecraftsmp.dynamicshop.gui.SearchResultsGUI;
import org.minecraftsmp.dynamicshop.gui.ShopGUI;
import org.minecraftsmp.dynamicshop.managers.ConfigCacheManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.transactions.Transaction;

import java.util.*;

public class ShopListener implements Listener {

    private final DynamicShop plugin;

    private final Map<Player, ShopGUI> openShop = new HashMap<>();
    private final Map<Player, CategorySelectionGUI> openCategory = new HashMap<>();
    private final Map<Player, SearchResultsGUI> openSearch = new HashMap<>();
    private final Map<Player, AdminShopBrowseGUI> openAdminBrowse = new HashMap<>();
    private final Map<Player, AdminItemEditGUI> openAdminEdit = new HashMap<>();

    public ShopListener(DynamicShop plugin) {
        this.plugin = plugin;
    }

    public void unregisterCategory(Player p) {
        openCategory.remove(p);
    }

    public void unregisterShop(Player p) {
        openShop.remove(p);
    }

    public void registerShop(Player p, ShopGUI gui) {
        openShop.put(p, gui);
    }

    public void registerCategory(Player p, CategorySelectionGUI gui) {
        openCategory.put(p, gui);
    }

    public void registerSearch(Player p, SearchResultsGUI gui) {
        openSearch.put(p, gui);
    }

    public void unregisterSearch(Player p) {
        openSearch.remove(p);
    }

    public void clear(Player p) {
        openShop.remove(p);
        openCategory.remove(p);
        openSearch.remove(p);
        openAdminBrowse.remove(p);
        openAdminEdit.remove(p);
    }

    // Admin GUI registration
    public void registerAdminBrowse(Player p, AdminShopBrowseGUI gui) {
        openAdminBrowse.put(p, gui);
    }

    public void unregisterAdminBrowse(Player p) {
        openAdminBrowse.remove(p);
    }

    public void registerAdminEdit(Player p, AdminItemEditGUI gui) {
        openAdminEdit.put(p, gui);
    }

    public void unregisterAdminEdit(Player p) {
        openAdminEdit.remove(p);
    }

    // ------------------------------------------------------------------
    // CLICK LISTENER
    // ------------------------------------------------------------------
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p))
            return;
        if (e.getClickedInventory() == null)
            return;

        // -------------------------
        // ADMIN EDIT GUI
        // -------------------------
        if (openAdminEdit.containsKey(p)) {
            e.setCancelled(true);
            openAdminEdit.get(p).handleClick(e.getRawSlot());
            return;
        }

        // -------------------------
        // ADMIN BROWSE GUI
        // -------------------------
        if (openAdminBrowse.containsKey(p)) {
            e.setCancelled(true);
            openAdminBrowse.get(p).handleClick(e.getRawSlot(), e.isRightClick());
            return;
        }

        // -------------------------
        // SEARCH GUI
        // -------------------------
        if (openSearch.containsKey(p)) {
            e.setCancelled(true);

            SearchResultsGUI gui = openSearch.get(p);

            // Click inside search GUI (top inventory)
            if (e.getRawSlot() < 54) {
                gui.handleClick(e.getRawSlot(), e.isRightClick(), e.isShiftClick());
                return;
            }

            // Player inventory → sell
            ItemStack clicked = e.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
                handlePlayerInventoryClick(p, clicked, e.isRightClick(), e.isShiftClick());
            }
            return;
        }

        // -------------------------
        // SHOP GUI
        // -------------------------
        if (openShop.containsKey(p)) {
            e.setCancelled(true);
            ShopGUI gui = openShop.get(p);

            if (e.getRawSlot() < gui.getSize()) {
                handleShopClick(p, gui, e.getRawSlot(), e.isRightClick(), e.isShiftClick());
                return;
            }

            handlePlayerInventoryClick(p, e.getCurrentItem(), e.isRightClick(), e.isShiftClick());
            return;
        }

        // -------------------------
        // CATEGORY GUI
        // -------------------------
        if (openCategory.containsKey(p)) {
            e.setCancelled(true);
            openCategory.get(p).handleClick(p, e.getRawSlot());
        }
    }

    // ------------------------------------------------------------------
    // SELLING FROM PLAYER INVENTORY (Right-click)
    // ------------------------------------------------------------------
    private void handlePlayerInventoryClick(Player p, ItemStack clicked, boolean isRightClick, boolean isShift) {
        if (clicked == null || clicked.getType() == Material.AIR)
            return;

        // Only sell on RIGHT-click
        if (!isRightClick)
            return;

        if (isDamaged(clicked)) {
            p.sendMessage(plugin.getMessageManager().cannotSellDamaged());
            return;
        }

        Material mat = clicked.getType();

        // use continuous pricing for single-unit sell
        double sellPrice = ShopDataManager.getTotalSellValue(mat, 1);

        if (sellPrice <= 0) {
            p.sendMessage(plugin.getMessageManager().cannotSell());
            return;
        }

        // Determine amount to sell
        int amount = isShift ? Math.min(clicked.getAmount(), 64) : 1;

        if (amount <= 0) {
            String itemName = mat.name().replace("_", " ").toLowerCase();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("item", itemName);
            p.sendMessage(plugin.getMessageManager().getMessage("not-enough-items", placeholders));
            return;
        }

        // Sell the items
        if (openShop.containsKey(p)) {
            sellItem(p, mat, amount, openShop.get(p));
        } else if (openSearch.containsKey(p)) {
            sellItem(p, mat, amount, openSearch.get(p));
        }
    }

    // ------------------------------------------------------------------
    // CLICK INSIDE SHOP GUI
    // ------------------------------------------------------------------
    private void handleShopClick(Player p, ShopGUI gui, int slot, boolean right, boolean shift) {
        if (gui.isNavigationSlot(slot)) {
            handleNavigationClick(p, gui, slot);
            return;
        }

        ItemCategory cat = gui.getCategory();

        // SPECIAL SHOP ITEMS
        if (cat == ItemCategory.PERMISSIONS || cat == ItemCategory.SERVER_SHOP) {
            SpecialShopItem sItem = gui.getSpecialItemFromSlot(slot);
            if (sItem == null)
                return;

            if (right) {
                p.sendMessage(plugin.getMessageManager().getMessage("cannot-sell-special-item"));
                return;
            }

            plugin.getSpecialShopManager().purchase(p, sItem);
            return;
        }

        // REGULAR ITEM
        Material mat = gui.getItemFromSlot(slot);
        if (mat == null)
            return;

        int amount = shift ? 64 : 1;

        if (right) {
            // SELL
            int has = 0;
            if (shift) {
                for (ItemStack item : p.getInventory().getContents()) {
                    if (item != null && item.getType() == mat && !isDamaged(item)) {
                        has += item.getAmount();
                    }
                }
                amount = Math.min(has, 64);
            }

            if (amount <= 0) {
                Map<String, String> ph = new HashMap<>();
                ph.put("item", mat.name().replace("_", " ").toLowerCase());
                p.sendMessage(plugin.getMessageManager().getMessage("not-enough-items", ph));
                return;
            }

            sellItem(p, mat, amount, gui);

        } else {
            // BUY
            buyItem(p, mat, amount, gui);
        }
    }

    // ------------------------------------------------------------------
    // CATEGORY NAVIGATION
    // ------------------------------------------------------------------
    private void handleNavigationClick(Player p, ShopGUI gui, int raw) {
        int navStart = gui.getSize() - 9;
        int local = raw - navStart;

        if (local == 0) {
            gui.prevPage();
            return;
        }
        if (local == 8) {
            gui.nextPage();
            return;
        }

        if (local == 4) {
            unregisterShop(p);
            CategorySelectionGUI cg = new CategorySelectionGUI(plugin, p);
            cg.open();
            registerCategory(p, cg);
            return;
        }

        if (local == 3) {
            p.closeInventory();
            unregisterShop(p);

            new AnvilGUI.Builder()
                    .title("§8Search Items")
                    .text("diamond")
                    .itemLeft(new ItemStack(Material.PAPER))
                    .onClick((slot, state) -> {
                        String text = state.getText().trim();
                        if (text.isEmpty()) {
                            p.sendMessage(plugin.getMessageManager().getMessage("search-enter-term"));
                            return Arrays.asList(AnvilGUI.ResponseAction.close());
                        }

                        SearchResultsGUI s = new SearchResultsGUI(plugin, p, text);
                        registerSearch(p, s);
                        return Arrays.asList(AnvilGUI.ResponseAction.close());
                    })
                    .plugin(plugin)
                    .open(p);
        }
    }

    // ------------------------------------------------------------------
    // BUY ACTION (CONTINUOUS PRICING)
    // ------------------------------------------------------------------
    public void buyItem(Player p, Material mat, int amount, Object gui) {

        if (!ShopDataManager.canBuy(mat)) {
            p.sendMessage(plugin.getMessageManager().getMessage("out-of-stock"));
            return;
        }

        double s0 = ShopDataManager.getStock(mat);

        // Optional restriction
        if (ConfigCacheManager.restrictBuyingAtZeroStock) {
            if (s0 <= 0) {
                p.sendMessage(plugin.getMessageManager().getMessage("out-of-stock"));
                return;
            }
            if (s0 < amount) {
                amount = (int) Math.max(0, s0);
                if (amount <= 0) {
                    p.sendMessage(plugin.getMessageManager().getMessage("out-of-stock"));
                    return;
                }
            }
        }

        double totalCost = ShopDataManager.getTotalBuyCost(mat, amount);

        if (!plugin.getEconomyManager().hasEnough(p, totalCost)) {
            Map<String, String> ph = new HashMap<>();
            ph.put("price", plugin.getEconomyManager().format(totalCost));
            p.sendMessage(plugin.getMessageManager().getMessage("not-enough-money-need", ph));
            return;
        }

        if (p.getInventory().firstEmpty() == -1) {
            p.sendMessage(plugin.getMessageManager().inventoryFull());
            return;
        }

        plugin.getEconomyManager().charge(p, totalCost);
        p.getInventory().addItem(new ItemStack(mat, amount));

        ShopDataManager.updateStock(mat, -amount);

        Map<String, String> ph = new HashMap<>();
        ph.put("amount", String.valueOf(amount));
        ph.put("item", mat.name().replace("_", " ").toLowerCase());
        ph.put("price", plugin.getEconomyManager().format(totalCost));
        p.sendMessage(plugin.getMessageManager().getMessage("bought-item", ph));

        plugin.getTransactionLogger().log(Transaction.now(
                p.getName(),
                Transaction.TransactionType.BUY,
                mat.name(),
                amount,
                totalCost,
                ShopDataManager.detectCategory(mat).name(),
                ""));

        if (gui instanceof ShopGUI)
            ((ShopGUI) gui).render();
        if (gui instanceof SearchResultsGUI)
            ((SearchResultsGUI) gui).render();

        Bukkit.getScheduler().runTaskLater(plugin, () -> updateSingleItemLore(p, mat), 3L);
    }

    // ------------------------------------------------------------------
    // SELL ACTION (CONTINUOUS PRICING)
    // ------------------------------------------------------------------
    public void sellItem(Player p, Material mat, int amount, Object gui) {

        int removed = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == mat && removed < amount && !isDamaged(item)) {
                removed += Math.min(item.getAmount(), amount - removed);
            }
        }

        if (removed == 0) {
            Map<String, String> ph = new HashMap<>();
            ph.put("item", mat.name().replace("_", " ").toLowerCase());
            p.sendMessage(plugin.getMessageManager().getMessage("not-enough-items", ph));
            return;
        }

        double totalPayout = ShopDataManager.getTotalSellValue(mat, removed);

        int actuallyRemoved = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == mat && actuallyRemoved < removed && !isDamaged(item)) {
                int take = Math.min(item.getAmount(), removed - actuallyRemoved);
                item.setAmount(item.getAmount() - take);
                actuallyRemoved += take;
            }
        }

        ShopDataManager.updateStock(mat, actuallyRemoved);

        plugin.getEconomyManager().deposit(p, totalPayout);

        Map<String, String> ph = new HashMap<>();
        ph.put("amount", String.valueOf(actuallyRemoved));
        ph.put("item", mat.name().replace("_", " ").toLowerCase());
        ph.put("price", plugin.getEconomyManager().format(totalPayout));
        p.sendMessage(plugin.getMessageManager().getMessage("sold-item-success", ph));

        plugin.getTransactionLogger().log(Transaction.now(
                p.getName(),
                Transaction.TransactionType.SELL,
                mat.name(),
                actuallyRemoved,
                totalPayout,
                ShopDataManager.detectCategory(mat).name(),
                ""));

        if (gui instanceof ShopGUI)
            ((ShopGUI) gui).render();
        if (gui instanceof SearchResultsGUI)
            ((SearchResultsGUI) gui).render();

        Bukkit.getScheduler().runTaskLater(plugin, () -> updateSingleItemLore(p, mat), 3L);
    }

    // ------------------------------------------------------------------
    // UPDATE PLAYER INVENTORY LORE (ALL SLOTS)
    // ------------------------------------------------------------------
    public void updatePlayerInventoryLore(Player player) {
        updatePlayerInventoryLore(player, 2L);
    }

    public void updatePlayerInventoryLore(Player player, long delay) {
        if (!openShop.containsKey(player) && !openSearch.containsKey(player))
            return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                com.comphenix.protocol.ProtocolManager pm = com.comphenix.protocol.ProtocolLibrary.getProtocolManager();

                Map<Material, List<String>> loreCache = new HashMap<>();

                // main inv (0–35)
                for (int i = 0; i < 36; i++) {
                    ItemStack item = player.getInventory().getItem(i);
                    if (item == null || item.getType() == Material.AIR)
                        continue;

                    Material mat = item.getType();
                    double buyPrice = ShopDataManager.getPrice(mat);
                    if (buyPrice < 0)
                        continue; // not in shop

                    sendFakeItemWithLore(pm, player, i, item, mat, loreCache);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to send fake inventory lore: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, delay);
    }

    // ------------------------------------------------------------------
    // UPDATE LORE FOR ONE MATERIAL
    // ------------------------------------------------------------------
    private void updateSingleItemLore(Player player, Material targetMat) {
        if (!openShop.containsKey(player) && !openSearch.containsKey(player))
            return;

        try {
            com.comphenix.protocol.ProtocolManager pm = com.comphenix.protocol.ProtocolLibrary.getProtocolManager();
            Map<Material, List<String>> loreCache = new HashMap<>();

            for (int i = 0; i < 36; i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item == null || item.getType() != targetMat)
                    continue;

                sendFakeItemWithLore(pm, player, i, item, targetMat, loreCache);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to update lore for " + targetMat + ": " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // PROTOCOLLIB: SEND FAKE ITEM WITH DYNAMIC LORE
    // ------------------------------------------------------------------
    private void sendFakeItemWithLore(com.comphenix.protocol.ProtocolManager pm,
            Player player,
            int slot,
            ItemStack item,
            Material mat,
            Map<Material, List<String>> loreCache) throws Exception {

        List<String> lore = loreCache.get(mat);
        if (lore == null) {
            double buyPrice = ShopDataManager.getTotalBuyCost(mat, 1);
            double sellPrice = ShopDataManager.getTotalSellValue(mat, 1);
            double stock = ShopDataManager.getStock(mat);

            lore = new ArrayList<>();

            // buy price
            Map<String, String> buyPh = new HashMap<>();
            buyPh.put("price", plugin.getEconomyManager().format(buyPrice));
            lore.add(plugin.getMessageManager().getMessage("lore-buy-price", buyPh));

            // sell price
            if (sellPrice > 0) {
                Map<String, String> sellPh = new HashMap<>();
                sellPh.put("price", plugin.getEconomyManager().format(sellPrice));
                lore.add(plugin.getMessageManager().getMessage("lore-sell-price", sellPh));
            }

            // stock info
            if (stock < 0) {
                Map<String, String> stPh = new HashMap<>();
                stPh.put("stock", String.format("%.0f", stock));
                lore.add(plugin.getMessageManager().getMessage("lore-stock-negative", stPh));
            } else if (stock == 0) {
                lore.add(plugin.getMessageManager().getMessage("lore-out-of-stock"));
            } else {
                Map<String, String> stPh = new HashMap<>();
                stPh.put("stock", String.format("%.0f", stock));
                lore.add(plugin.getMessageManager().getMessage("lore-stock", stPh));
            }

            // instructions
            lore.add("");
            lore.add(plugin.getMessageManager().getMessage("lore-click-to-sell-1"));
            lore.add(plugin.getMessageManager().getMessage("lore-shift-click-to-sell-64"));

            loreCache.put(mat, lore);
        }

        ItemStack fake = item.clone();
        ItemMeta meta = fake.getItemMeta();
        if (meta == null)
            return;
        meta.setLore(lore);
        fake.setItemMeta(meta);

        com.comphenix.protocol.events.PacketContainer packet = pm
                .createPacket(com.comphenix.protocol.PacketType.Play.Server.SET_SLOT);

        int containerSize = 0;
        if (openShop.containsKey(player)) {
            containerSize = openShop.get(player).getSize();
        } else if (openSearch.containsKey(player)) {
            containerSize = 54;
        }

        int packetSlot;
        if (slot < 9) {
            // hotbar
            packetSlot = containerSize + 27 + slot;
        } else {
            // main inv
            packetSlot = containerSize + (slot - 9);
        }

        packet.getIntegers()
                .write(0, getOpenWindowId(player))
                .write(1, 0)
                .write(2, packetSlot);

        packet.getItemModifier().write(0, fake);

        pm.sendServerPacket(player, packet);
    }

    // ------------------------------------------------------------------
    // NMS: GET WINDOW ID
    // ------------------------------------------------------------------
    private int getOpenWindowId(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object container = handle.getClass().getField("containerMenu").get(handle);
            return (int) container.getClass().getField("containerId").get(container);
        } catch (Exception e) {
            return 1; // fallback
        }
    }

    // ------------------------------------------------------------------
    // PREVENT DRAGGING IN GUIS
    // ------------------------------------------------------------------
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (!openShop.containsKey(p) && !openCategory.containsKey(p) && !openSearch.containsKey(p)
                && !openAdminBrowse.containsKey(p) && !openAdminEdit.containsKey(p))
            return;
        e.setCancelled(true);
    }

    // ------------------------------------------------------------------
    // CLEANUP ON INVENTORY CLOSE
    // ------------------------------------------------------------------
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Player p = (Player) e.getPlayer();
        clear(p);
    }

    private boolean isDamaged(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            return damageable.hasDamage();
        }
        return false;
    }
}
