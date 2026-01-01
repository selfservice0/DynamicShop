package org.minecraftsmp.dynamicshop.listeners;

import org.bukkit.OfflinePlayer;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.gui.PlayerShopBrowserGUI;
import org.minecraftsmp.dynamicshop.gui.PlayerShopViewGUI;
import org.minecraftsmp.dynamicshop.managers.PlayerShopManager;
import org.minecraftsmp.dynamicshop.models.PlayerShopListing;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerShopListener implements Listener {
    private final DynamicShop plugin;

    // Track open GUIs so we can handle clicks properly
    private final Map<UUID, PlayerShopBrowserGUI> openBrowserGUIs = new HashMap<>();
    private final Map<UUID, PlayerShopViewGUI> openShopViewGUIs = new HashMap<>();

    public PlayerShopListener(DynamicShop plugin) {
        this.plugin = plugin;
    }

    /**
     * Register a browser GUI for a player
     * Called when opening the player shops browser
     */
    public void registerBrowserGUI(Player player, PlayerShopBrowserGUI gui) {
        openBrowserGUIs.put(player.getUniqueId(), gui);
    }

    /**
     * Register a shop view GUI for a player
     * Called when opening an individual player's shop
     */
    public void registerShopViewGUI(Player player, PlayerShopViewGUI gui) {
        openShopViewGUIs.put(player.getUniqueId(), gui);
    }

    /**
     * Clean up tracked GUIs when inventory is closed
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;

        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        // Only remove the specific GUI that's closing
        if (title.equals("§6§lPlayer Shops")) {
            openBrowserGUIs.remove(playerId);
        } else if (title.contains("'s Shop") || title.equals("§6§lYour Shop")) {
            openShopViewGUIs.remove(playerId);
        }
    }

    /**
     * Handle all inventory clicks in player shop GUIs
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        // Handle Player Shops Browser
        if (title.equals("§6§lPlayer Shops")) {
            event.setCancelled(true);
            handleBrowserClick(player, event.getRawSlot());
            return;
        }

        // Handle Individual Player Shop View
        if (title.contains("'s Shop") || title.equals("§6§lYour Shop")) {
            event.setCancelled(true);
            handleShopViewClick(player, event.getRawSlot());
        }
    }

    /**
     * Handle clicks in the player shops browser
     */
    private void handleBrowserClick(Player player, int slot) {
        PlayerShopBrowserGUI gui = openBrowserGUIs.get(player.getUniqueId());
        if (gui == null)
            return;

        // Navigation buttons
        if (slot == 48) { // Previous page
            gui.previousPage();
            return;
        }
        if (slot == 50) { // Next page
            gui.nextPage();
            return;
        }
        if (slot == 45) { // Back to main shop
            player.closeInventory();

            // Open category selection GUI
            org.minecraftsmp.dynamicshop.gui.CategorySelectionGUI categoryGUI = new org.minecraftsmp.dynamicshop.gui.CategorySelectionGUI(
                    plugin, player);
            plugin.getShopListener().registerCategory(player, categoryGUI);
            categoryGUI.open();
            return;
        }
        if (slot == 53) { // Info button
            return;
        }
        if (slot == 49) { // Page indicator
            return;
        }

        // Clicked on a player head - open that player's shop
        UUID shopOwnerId = gui.getShopOwnerAtSlot(slot);
        if (shopOwnerId != null) {
            PlayerShopViewGUI shopView = new PlayerShopViewGUI(plugin, player, shopOwnerId);
            registerShopViewGUI(player, shopView);
            shopView.open();
        }
    }

    /**
     * Handle clicks in an individual player's shop
     */
    private void handleShopViewClick(Player player, int slot) {
        PlayerShopViewGUI gui = openShopViewGUIs.get(player.getUniqueId());
        if (gui == null)
            return;

        // Navigation buttons
        if (slot == 48) { // Previous page
            gui.previousPage();
            return;
        }
        if (slot == 50) { // Next page
            gui.nextPage();
            return;
        }
        if (slot == 45) { // Back to browser
            PlayerShopBrowserGUI browser = new PlayerShopBrowserGUI(plugin, player);
            registerBrowserGUI(player, browser);
            browser.open();
            return;
        }
        if (slot == 49 || slot == 53) { // Info/stats buttons
            return;
        }

        // Clicked on an item
        PlayerShopListing listing = gui.getListingAtSlot(slot);
        if (listing == null)
            return;

        boolean isOwnShop = player.getUniqueId().equals(gui.getShopOwnerId());

        if (isOwnShop) {
            // Reclaim item
            reclaimItem(player, listing);

            // Refresh or close if no items left
            PlayerShopManager manager = plugin.getPlayerShopManager();
            if (manager.getListingCount(gui.getShopOwnerId()) == 0) {
                player.closeInventory();
                player.sendMessage("§7Your shop is now empty.");
            } else {
                // Recreate GUI to refresh
                PlayerShopViewGUI newGui = new PlayerShopViewGUI(plugin, player, gui.getShopOwnerId());
                registerShopViewGUI(player, newGui);
                newGui.open();
            }
        } else {
            // Purchase item
            purchaseItem(player, listing);

            // Refresh or go back if no items left
            PlayerShopManager manager = plugin.getPlayerShopManager();
            if (manager.getListingCount(gui.getShopOwnerId()) == 0) {
                player.sendMessage("§7This shop is now empty.");
                PlayerShopBrowserGUI browser = new PlayerShopBrowserGUI(plugin, player);
                registerBrowserGUI(player, browser);
                browser.open();
            } else {
                // Recreate GUI to refresh
                PlayerShopViewGUI newGui = new PlayerShopViewGUI(plugin, player, gui.getShopOwnerId());
                registerShopViewGUI(player, newGui);
                newGui.open();
            }
        }
    }

    /**
     * Player reclaims their own item from their shop
     */
    private void reclaimItem(Player player, PlayerShopListing listing) {
        PlayerShopManager manager = plugin.getPlayerShopManager();

        // Give item back
        ItemStack item = listing.getItem();
        player.getInventory().addItem(item);

        // Remove listing
        manager.removeListing(listing.getListingId());

        String itemName = item.getType().toString().toLowerCase().replace("_", " ");
        player.sendMessage("§a✓ §7You reclaimed your §f" + itemName + " §7x" + item.getAmount());
    }

    /**
     * Player purchases an item from another player's shop
     */
    private void purchaseItem(Player player, PlayerShopListing listing) {
        PlayerShopManager manager = plugin.getPlayerShopManager();
        double price = listing.getPrice();

        // ---------------------------
        // 1. Check buyer's balance
        // ---------------------------
        if (!plugin.getEconomyManager().hasEnough(player, price)) {
            player.sendMessage("§c✗ §7You need §c$" + String.format("%.2f", price) +
                    " §7to buy this item!");
            player.closeInventory();
            return;
        }

        // ---------------------------
        // 2. Check inventory space
        // ---------------------------
        ItemStack item = listing.getItem();
        if (!hasInventorySpace(player, item)) {
            player.sendMessage("§c✗ §7Not enough inventory space!");
            return;
        }

        // Charge buyer first
        plugin.getEconomyManager().charge(player, price);

        Player seller = Bukkit.getPlayer(listing.getSellerId());
        if (seller != null && seller.isOnline()) {
            String sellerItemName = item.getType().toString().toLowerCase().replace("_", " ");
            plugin.getEconomyManager().deposit(seller, price);
            seller.sendMessage("§a✓ §7Your §f" + sellerItemName + " §7x" + item.getAmount() +
                    " §7was purchased by §e" + player.getName() + " §7for §a$" +
                    String.format("%.2f", price));
        } else if (seller != null) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(listing.getSellerId());
            plugin.getEconomyManager().depositOffline(offline, price);
        }

        // Give buyer the item
        player.getInventory().addItem(item);

        // Remove listing
        manager.removeListing(listing.getListingId());

        // Buyer confirmation
        String itemName = item.getType().toString().toLowerCase().replace("_", " ");
        player.sendMessage("§a✓ §7Purchased §f" + itemName + " §7x" + item.getAmount() +
                " §7for §a$" + String.format("%.2f", price));

        // Log transaction
        plugin.getLogger().info("[PlayerShops] " + player.getName() + " bought " +
                item.getType() + " x" + item.getAmount() + " from " +
                listing.getSellerName() + " for $" + price);
    }

    // Check if player has enough inventory space for an item
    private boolean hasInventorySpace(Player player, ItemStack item) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int needed = item.getAmount();
        int maxStack = item.getMaxStackSize();

        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR) {
                needed -= maxStack;
            } else if (stack.isSimilar(item)) {
                int space = maxStack - stack.getAmount();
                needed -= space;
            }

            if (needed <= 0) {
                return true;
            }
        }

        return needed <= 0;
    }
}