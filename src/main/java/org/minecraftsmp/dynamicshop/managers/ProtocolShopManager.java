package org.minecraftsmp.dynamicshop.managers;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import org.minecraftsmp.dynamicshop.DynamicShop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modern implementation using Bukkit inventories with ProtocolLib packet
 * interception
 * for click security.
 * 
 * In Minecraft 1.14+, we can't create fully virtual inventories via packets
 * alone.
 * Instead, we:
 * 1. Create real Bukkit inventories (but with a NULL holder for security)
 * 2. Use event listeners to intercept clicks and prevent item duplication
 * 3. Items are still "secure" - they exist in the GUI but clicks are cancelled
 */
public class ProtocolShopManager {

    @SuppressWarnings("unused")
    private final DynamicShop plugin;
    @SuppressWarnings("unused")
    private final ProtocolManager pm;

    // Track which inventories are "shop" inventories
    private final Map<Inventory, Boolean> shopInventories = new HashMap<>();

    public ProtocolShopManager(DynamicShop plugin) {
        this.plugin = plugin;
        this.pm = ProtocolLibrary.getProtocolManager();
    }

    // -----------------------------------------------------------
    // Open a shop inventory (real Bukkit inventory but with null holder)
    // -----------------------------------------------------------
    public Inventory createVirtualInventory(Player p, int size, String title) {
        // Create inventory with NULL holder - this makes it "virtual"
        // Size must be multiple of 9
        int rows = Math.max(3, Math.min(6, (size + 8) / 9));
        Inventory inv = Bukkit.createInventory(null, rows * 9,
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize(title));

        // Register as shop inventory
        shopInventories.put(inv, true);

        return inv;
    }

    // -----------------------------------------------------------
    // Check if inventory is a shop inventory
    // -----------------------------------------------------------
    public boolean isShopInventory(Inventory inv) {
        return shopInventories.containsKey(inv);
    }

    // -----------------------------------------------------------
    // Clean up when inventory is closed
    // -----------------------------------------------------------
    public void unregisterInventory(Inventory inv) {
        shopInventories.remove(inv);
    }

    // -----------------------------------------------------------
    // Update a single slot
    // -----------------------------------------------------------
    public void sendSlot(Inventory inv, int slot, ItemStack item) {
        if (slot < 0 || slot >= inv.getSize())
            return;
        inv.setItem(slot, item);
    }

    // -----------------------------------------------------------
    // Send full inventory contents
    // -----------------------------------------------------------
    public void sendContents(Inventory inv, List<ItemStack> items) {
        inv.clear();
        for (int i = 0; i < Math.min(items.size(), inv.getSize()); i++) {
            inv.setItem(i, items.get(i));
        }
    }

    // -----------------------------------------------------------
    // Send empty contents
    // -----------------------------------------------------------
    public void sendEmptyContents(Inventory inv, int size) {
        inv.clear();
    }

    // -----------------------------------------------------------
    // Clear window
    // -----------------------------------------------------------
    public void clearWindow(Inventory inv, int size) {
        inv.clear();
    }

    // -----------------------------------------------------------
    // Get total registered shop inventories (for debugging)
    // -----------------------------------------------------------
    public int getActiveShopCount() {
        return shopInventories.size();
    }
}
