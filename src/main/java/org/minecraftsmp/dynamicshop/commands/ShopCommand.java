package org.minecraftsmp.dynamicshop.commands;

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
import org.minecraftsmp.dynamicshop.managers.CategoryConfigManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.transactions.Transaction;

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
            sender.sendMessage("§cOnly players can open the shop.");
            return true;
        }

        if (!p.hasPermission("dynamicshop.use")) {
            p.sendMessage(plugin.getMessageManager().noPermission());
            return true;
        }

        // Handle /shop sellhand
        if (args.length >= 1 && args[0].equalsIgnoreCase("sellhand")) {
            if (!p.hasPermission("dynamicshop.use.sellhand")) {
                p.sendMessage(plugin.getMessageManager().noPermission());
                return true;
            }
            return handleSellHand(p);
        }

        // Handle /shop sellall
        if (args.length >= 1 && args[0].equalsIgnoreCase("sellall")) {
            if (!p.hasPermission("dynamicshop.use.sellall")) {
                p.sendMessage(plugin.getMessageManager().noPermission());
                return true;
            }
            return handleSellAll(p);
        }

        // Handle /shop sell <price> (player shop listing)
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
                p.sendMessage("  §e" + c.name().toLowerCase());
            }
            return true;
        }

        // Don't allow direct access to special categories
        if (cat == ItemCategory.PERMISSIONS || cat == ItemCategory.SERVER_SHOP || cat == ItemCategory.PLAYER_SHOPS) {
            p.sendMessage(plugin.getMessageManager().getMessage("cannot-access-special-category"));
            return true;
        }

        // Don't allow access to hidden categories
        if (CategoryConfigManager.getSlot(cat) < 0) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("category", args[0]);
            p.sendMessage(plugin.getMessageManager().getMessage("unknown-category", placeholders));
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
        boolean hasSpecialItems = plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                .anyMatch(item -> item.getCategory() == cat);
                
        if (items.isEmpty() && !hasSpecialItems) {
            p.sendMessage(plugin.getMessageManager().categoryEmpty());
            return;
        }

        ShopGUI gui = new ShopGUI(plugin, p, cat, true);
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
            // Add sell commands
            if ("sellhand".startsWith(args[0].toLowerCase())) {
                out.add("sellhand");
            }
            if ("sellall".startsWith(args[0].toLowerCase())) {
                out.add("sellall");
            }
            if ("sell".startsWith(args[0].toLowerCase())) {
                out.add("sell");
            }

            for (ItemCategory c : ItemCategory.values()) {
                // Don't suggest special or hidden categories
                if (c == ItemCategory.PERMISSIONS || c == ItemCategory.SERVER_SHOP || c == ItemCategory.PLAYER_SHOPS) {
                    continue;
                }
                // Don't suggest hidden categories
                if (CategoryConfigManager.getSlot(c) < 0) {
                    continue;
                }

                if (c.name().toLowerCase().startsWith(args[0].toLowerCase())) {
                    out.add(c.name().toLowerCase());
                }
            }
        }

        return out;
    }

    // --------------------------------------------------------------------
    // SELL HAND: sell entire held stack to the dynamic shop
    // --------------------------------------------------------------------
    private boolean handleSellHand(Player p) {
        if (!plugin.getShopListener().checkTransactionCooldown(p)) return true;
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            p.sendMessage("§c✗ §7You're not holding anything!");
            return true;
        }

        if (isDamaged(held)) {
            p.sendMessage(plugin.getMessageManager().cannotSellDamaged());
            return true;
        }

        Material mat = held.getType();
        int amount = held.getAmount();

        if (!isShopSellMatch(held, mat)) {
            p.sendMessage(plugin.getMessageManager().cannotSell());
            return true;
        }

        if (ShopDataManager.getBasePrice(mat) < 0) {
            p.sendMessage(plugin.getMessageManager().cannotSell());
            return true;
        }

        if (ShopDataManager.isSellDisabled(mat)) {
            p.sendMessage("§cSelling this item is disabled.");
            return true;
        }

        if (!ShopDataManager.canSell(mat, amount)) {
            int limit = ShopDataManager.getSellLimit(mat);
            if (limit <= 0) {
                p.sendMessage("§cShop storage is full for this item.");
                return true;
            }
            amount = Math.min(amount, limit);
        }

        double payout = ShopDataManager.getTotalSellValue(mat, amount);
        if (!Double.isFinite(payout) || payout < 0) {
            p.sendMessage(plugin.getMessageManager().cannotSell());
            return true;
        }

        // Remove items from hand
        int remaining = held.getAmount() - amount;
        if (remaining <= 0) {
            p.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(remaining);
        }

        ShopDataManager.updateStock(mat, amount);
        String currency = plugin.getEconomyManager().getCurrency(mat);
        plugin.getEconomyManager().deposit(p, payout, currency);
        plugin.getShopListener().recordTransaction(p);

        Map<String, String> ph = new HashMap<>();
        ph.put("amount", String.valueOf(amount));
        ph.put("item", org.minecraftsmp.dynamicshop.util.ShopItemNames.getDisplayName(mat));
        ph.put("price", plugin.getEconomyManager().format(payout, currency));
        p.sendMessage(plugin.getMessageManager().getMessage("sold-item-success", ph));

        plugin.getTransactionLogger().log(Transaction.now(
                p.getName(),
                Transaction.TransactionType.SELL,
                mat.name(),
                amount,
                payout,
                ShopDataManager.detectCategory(mat).name(),
                ""));

        return true;
    }

    // --------------------------------------------------------------------
    // SELL ALL: sell every sellable item in inventory
    // --------------------------------------------------------------------
    private boolean handleSellAll(Player p) {
        if (!plugin.getShopListener().checkTransactionCooldown(p)) return true;
        // Gather sellable materials and counts
        Map<Material, Integer> sellable = new java.util.LinkedHashMap<>();

        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (isDamaged(item)) continue;

            Material mat = item.getType();
            if (ShopDataManager.getBasePrice(mat) < 0) continue;
            if (ShopDataManager.isSellDisabled(mat)) continue;
            if (!isShopSellMatch(item, mat)) continue;

            sellable.merge(mat, item.getAmount(), Integer::sum);
        }

        if (sellable.isEmpty()) {
            p.sendMessage("§c✗ §7You don't have any items to sell!");
            return true;
        }

        Map<String, Double> payoutsByCurrency = new java.util.LinkedHashMap<>();
        int totalItems = 0;
        int itemTypes = 0;

        for (Map.Entry<Material, Integer> entry : sellable.entrySet()) {
            Material mat = entry.getKey();
            int amount = entry.getValue();

            // Check stock limits
            if (!ShopDataManager.canSell(mat, amount)) {
                int limit = ShopDataManager.getSellLimit(mat);
                if (limit <= 0) continue;
                amount = Math.min(amount, limit);
            }

            if (amount <= 0) continue;
            double quote = ShopDataManager.getTotalSellValue(mat, amount);
            String currency = plugin.getEconomyManager().getCurrency(mat);
            if (!Double.isFinite(quote) || quote < 0
                    || !Double.isFinite(payoutsByCurrency.getOrDefault(currency, 0.0) + quote)) continue;
            Map<Integer, ItemStack> originalSlots = new HashMap<>();
            // Remove items from inventory
            int toRemove = amount;
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                ItemStack item = p.getInventory().getItem(i);
                if (isShopSellMatch(item, mat) && !isDamaged(item) && toRemove > 0) {
                    originalSlots.put(i, item.clone());
                    int take = Math.min(item.getAmount(), toRemove);
                    int newAmt = item.getAmount() - take;
                    if (newAmt <= 0) {
                        p.getInventory().setItem(i, null);
                    } else {
                        item.setAmount(newAmt);
                    }
                    toRemove -= take;
                }
            }

            int actuallySold = amount - toRemove;
            if (actuallySold <= 0) {
                continue;
            }

            double payout = ShopDataManager.getTotalSellValue(mat, actuallySold);
            if (!Double.isFinite(payout) || payout < 0
                    || !Double.isFinite(payoutsByCurrency.getOrDefault(currency, 0.0) + payout)) {
                originalSlots.forEach((slot, original) -> p.getInventory().setItem(slot, original));
                continue;
            }

            ShopDataManager.updateStock(mat, actuallySold);
            payoutsByCurrency.merge(currency, payout, Double::sum);
            totalItems += actuallySold;
            itemTypes++;

            plugin.getTransactionLogger().log(Transaction.now(
                    p.getName(),
                    Transaction.TransactionType.SELL,
                    mat.name(),
                    actuallySold,
                    payout,
                    ShopDataManager.detectCategory(mat).name(),
                    ""));
        }

        if (totalItems == 0) {
            p.sendMessage("§c✗ §7No items could be sold (shop storage may be full).");
            return true;
        }

        StringBuilder payoutSummary = new StringBuilder();
        plugin.getShopListener().recordTransaction(p);
        for (Map.Entry<String, Double> payoutEntry : payoutsByCurrency.entrySet()) {
            plugin.getEconomyManager().deposit(p, payoutEntry.getValue(), payoutEntry.getKey());
            if (payoutSummary.length() > 0) {
                payoutSummary.append(", ");
            }
            payoutSummary.append(plugin.getEconomyManager().format(
                    payoutEntry.getValue(), payoutEntry.getKey()));
        }

        p.sendMessage("§a✓ §7Sold §f" + totalItems + " items §7(§e" + itemTypes + " types§7) for §a" +
                payoutSummary);

        return true;
    }

    private boolean handleSellCommand(Player player, String[] args) {
        if (!plugin.getPlayerShopManager().isEnabled()) {
            player.sendMessage("§cPlayer shops are currently disabled.");
            return true;
        }
        // Check permission
        if (!player.hasPermission("dynamicshop.playershop.sell")) {
            player.sendMessage(plugin.getMessageManager().getMessage("playershop-no-permission-sell"));
            return true;
        }

        // Parse price
        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessageManager().getMessage("playershop-invalid-price"));
            player.sendMessage(plugin.getMessageManager().getMessage("playershop-price-example"));
            return true;
        }

        if (!Double.isFinite(price) || price <= 0) {
            player.sendMessage(plugin.getMessageManager().getMessage("playershop-price-must-be-positive"));
            return true;
        }

        // Get held item
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (heldItem == null || heldItem.getType() == Material.AIR) {
            player.sendMessage(plugin.getMessageManager().getMessage("playershop-hold-item"));
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
            Map<String, String> ph1 = new HashMap<>();
            ph1.put("max", String.valueOf(maxListings));
            player.sendMessage(plugin.getMessageManager().getMessage("playershop-max-listings", ph1));
            player.sendMessage(plugin.getMessageManager().getMessage("playershop-remove-items-first"));
            return true;
        }

        // Clone item and remove from hand
        ItemStack itemToList = heldItem.clone();
        player.getInventory().setItemInMainHand(null);

        // Add to shop
        boolean success = plugin.getPlayerShopManager().addListing(player, itemToList, price);

        if (success) {
            String itemName = itemToList.getType().toString().toLowerCase().replace("_", " ");
            Map<String, String> ph2 = new HashMap<>();
            ph2.put("item", itemName);
            ph2.put("amount", String.valueOf(itemToList.getAmount()));
            ph2.put("price", String.format("%.2f", price));
            player.sendMessage(plugin.getMessageManager().getMessage("playershop-listed", ph2));
            player.sendMessage(plugin.getMessageManager().getMessage("playershop-listed-success"));
        } else {
            // Return item if failed
            player.getInventory().addItem(itemToList);
            player.sendMessage(plugin.getMessageManager().getMessage("playershop-listing-failed"));
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

    private boolean isShopSellMatch(ItemStack item, Material mat) {
        if (item == null || item.getType() != mat) {
            return false;
        }

        ItemStack template = ShopDataManager.getTemplate(mat);
        if (template == null) {
            template = new ItemStack(mat, 1);
        }

        ItemStack oneItem = item.clone();
        oneItem.setAmount(1);
        ItemStack oneTemplate = template.clone();
        oneTemplate.setAmount(1);
        return oneItem.isSimilar(oneTemplate);
    }
}
