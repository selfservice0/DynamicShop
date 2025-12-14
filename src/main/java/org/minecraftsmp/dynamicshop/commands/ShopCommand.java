package org.minecraftsmp.dynamicshop.commands;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import org.bukkit.inventory.ItemStack;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.gui.CategorySelectionGUI;
import org.minecraftsmp.dynamicshop.gui.ShopGUI;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /shop command
 *
 * Supported:
 * /shop
 * /shop <category>
 *
 * If no category is provided, opens the CategorySelectionGUI.
 * If category is provided, skip category GUI and go straight to ShopGUI.
 */
public class ShopCommand implements CommandExecutor, TabCompleter {

    private final DynamicShop plugin;

    public ShopCommand(DynamicShop plugin) {
        this.plugin = plugin;
    }

    // --------------------------------------------------------------------
    // COMMAND EXECUTION
    // --------------------------------------------------------------------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cOnly players can open the shop."));
            return true;
        }

        if (!p.hasPermission("dynamicshop.use")) {
            p.sendMessage(plugin.getMessageManager().noPermission());
            return true;
        }

        // Handle /shop sell <price>
        if (args.length >= 2 && args[0].equalsIgnoreCase("sell")) {
            return handleSellCommand(p, args);
        }

        // /shop
        if (args.length == 0) {
            openCategoryGUI(p);
            return true;
        }

        // /shop <category>
        ItemCategory cat = matchCategory(args[0]);
        if (cat == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("category", args[0]);
            p.sendMessage(plugin.getMessageManager().getMessage("unknown-category", placeholders));
            p.sendMessage(plugin.getMessageManager().getMessage("list-categories"));
            for (ItemCategory c : ItemCategory.values()) {
                // Don't show special categories
                if (c == ItemCategory.PERMISSIONS || c == ItemCategory.SERVER_SHOP || c == ItemCategory.PLAYER_SHOPS) {
                    continue;
                }
                p.sendMessage("  " + ChatColor.YELLOW + c.name().toLowerCase());
            }
            return true;
        }

        // Don't allow direct access to special categories
        if (cat == ItemCategory.PERMISSIONS || cat == ItemCategory.SERVER_SHOP || cat == ItemCategory.PLAYER_SHOPS) {
            p.sendMessage(plugin.getMessageManager().getMessage("cannot-access-special-category"));
            return true;
        }

        openCategoryDirect(p, cat);
        return true;
    }

    // --------------------------------------------------------------------
    // GUI OPENING HELPERS
    // --------------------------------------------------------------------
    private void openCategoryGUI(Player p) {
        CategorySelectionGUI gui = new CategorySelectionGUI(plugin, p);
        plugin.getShopListener().registerCategory(p, gui);
        gui.open();
    }

    private void openCategoryDirect(Player p, ItemCategory cat) {
        // Check if category has items
        List<org.bukkit.Material> items = ShopDataManager.getItemsInCategory(cat);
        if (items.isEmpty()) {
            p.sendMessage(plugin.getMessageManager().categoryEmpty());
            return;
        }

        ShopGUI gui = new ShopGUI(plugin, p, cat);
        plugin.getShopListener().registerShop(p, gui);
        gui.open();
    }

    // --------------------------------------------------------------------
    // CATEGORY MATCHER
    // --------------------------------------------------------------------
    private ItemCategory matchCategory(String raw) {
        raw = raw.trim().toUpperCase();
        for (ItemCategory c : ItemCategory.values()) {
            if (c.name().equalsIgnoreCase(raw))
                return c;
        }
        return null;
    }

    // --------------------------------------------------------------------
    // TAB COMPLETION
    // --------------------------------------------------------------------
    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        // /shop <tab>
        if (args.length == 1) {
            for (ItemCategory c : ItemCategory.values()) {
                // Don't suggest special categories
                if (c == ItemCategory.PERMISSIONS || c == ItemCategory.SERVER_SHOP || c == ItemCategory.PLAYER_SHOPS) {
                    continue;
                }

                if (c.name().toLowerCase().startsWith(args[0].toLowerCase())) {
                    out.add(c.name().toLowerCase());
                }
            }
        }

        return out;
    }

    private boolean handleSellCommand(Player player, String[] args) {
        // Check permission
        if (!player.hasPermission("dynamicshop.playershop.sell")) {
            player.sendMessage("§c✗ §7You don't have permission to sell items!");
            return true;
        }

        // Parse price
        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c✗ §7Invalid price! Usage: §e/shop sell <price>");
            player.sendMessage("§7Example: §e/shop sell 100");
            return true;
        }

        if (price <= 0) {
            player.sendMessage("§c✗ §7Price must be greater than 0!");
            return true;
        }

        // Get held item
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (heldItem == null || heldItem.getType() == Material.AIR) {
            player.sendMessage("§c✗ §7Hold an item in your hand to list it!");
            return true;
        }

        if (isDamaged(heldItem)) {
            player.sendMessage(plugin.getMessageManager().cannotSellDamaged());
            return true;
        }

        // Check max listings
        int maxListings = plugin.getConfig().getInt("player-shops.max-listings-per-player", 27);
        int currentListings = plugin.getPlayerShopManager().getListingCount(player.getUniqueId());

        if (currentListings >= maxListings) {
            player.sendMessage("§c✗ §7You've reached the maximum of §c" + maxListings + " §7listings!");
            player.sendMessage("§7Remove some items from your shop first.");
            return true;
        }

        // Clone item and remove from hand
        ItemStack itemToList = heldItem.clone();
        player.getInventory().setItemInMainHand(null);

        // Add to shop
        boolean success = plugin.getPlayerShopManager().addListing(player, itemToList, price);

        if (success) {
            String itemName = itemToList.getType().toString().toLowerCase().replace("_", " ");
            player.sendMessage("§a✓ §7Listed §f" + itemName + " §7x" + itemToList.getAmount() +
                    " §7for §a$" + String.format("%.2f", price));
            player.sendMessage("§7Players can now buy this from your shop!");
        } else {
            // Return item if failed
            player.getInventory().addItem(itemToList);
            player.sendMessage("§c✗ §7Failed to list item!");
        }

        return true;
    }

    private boolean isDamaged(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            return damageable.hasDamage();
        }
        return false;
    }
}
