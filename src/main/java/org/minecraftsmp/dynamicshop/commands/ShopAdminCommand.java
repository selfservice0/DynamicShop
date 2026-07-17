package org.minecraftsmp.dynamicshop.commands;

import org.bukkit.Bukkit;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.managers.ConfigCacheManager;
import org.minecraftsmp.dynamicshop.gui.AdminCategoryGUI;
import org.minecraftsmp.dynamicshop.gui.ItemActionGUI;
import org.minecraftsmp.dynamicshop.gui.ShopGUI;
import org.minecraftsmp.dynamicshop.managers.CategoryConfigManager;
import org.minecraftsmp.dynamicshop.managers.ItemsAdderWrapper;
import org.minecraftsmp.dynamicshop.managers.NexoWrapper;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.util.BedrockUtil;
import org.minecraftsmp.dynamicshop.util.PaperCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /shopadmin command
 *
 * Supported:
 * /shopadmin reload
 * /shopadmin add item <price>
 * /shopadmin add perm <price> <permission.node>
 * /shopadmin add server-shop <price> <identifier>
 *
 * Notes:
 * - For server shop NBT items:
 * /shopadmin add server-shop <price> <id> nbt <material> <nbt string...>
 */
public class ShopAdminCommand implements CommandExecutor, TabCompleter {

    private final DynamicShop plugin;

    public ShopAdminCommand(DynamicShop plugin) {
        this.plugin = plugin;
    }

    // --------------------------------------------------------------------
    // COMMAND EXECUTION
    // --------------------------------------------------------------------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("dynamicshop.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            // Open admin category GUI if sender is a player
            if (sender instanceof Player p) {
                AdminCategoryGUI gui = new AdminCategoryGUI(plugin, p);
                plugin.getShopListener().registerAdminCategory(p, gui);
                gui.open();
                return true;
            }
            // Console can't open GUI, show help instead
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            // --------------------------------------------------------------
            // /shopadmin resetshortage
            // --------------------------------------------------------------
            case "resetshortage" -> {
                ShopDataManager.resetAllShortageData();
                sender.sendMessage("§a[DynamicShop] §fAll shortage data has been reset.");
                return true;
            }

            // --------------------------------------------------------------
            // /shopadmin setstock <item|all> <amount>
            // --------------------------------------------------------------
            case "setstock" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /shopadmin setstock <item|all> <amount>");
                    return true;
                }

                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid number: " + args[2]);
                    return true;
                }

                if (args[1].equalsIgnoreCase("all")) {
                    int count = 0;
                    for (Material m : ShopDataManager.getAllTrackedMaterials()) {
                        ShopDataManager.setStockDirect(m, amount);
                        count++;
                    }
                    ShopDataManager.saveDynamicData();
                    sender.sendMessage("§a[DynamicShop] §fSet stock to §e" + amount + " §ffor §e" + count + " §fitems.");
                } else {
                    Material mat = Material.matchMaterial(args[1]);
                    if (mat == null) {
                        sender.sendMessage("§cUnknown item: " + args[1]);
                        return true;
                    }
                    if (ShopDataManager.getBasePrice(mat) < 0) {
                        sender.sendMessage("§cItem is not in the shop: " + args[1]);
                        return true;
                    }
                    ShopDataManager.setStockDirect(mat, amount);
                    ShopDataManager.saveDynamicData();
                    sender.sendMessage("§a[DynamicShop] §fStock for §e" + mat.name() + " §fset to §e" + amount + "§f.");
                }
                return true;
            }

            // --------------------------------------------------------------
            // /shopadmin setname <item> <name...>
            // Sets a custom display name for an item in the shop.
            // Use "clear" or "reset" as name to remove the custom name.
            // --------------------------------------------------------------
            case "setname" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /shopadmin setname <item> <name...>");
                    sender.sendMessage("§7Use §f/shopadmin setname <item> clear §7to remove.");
                    return true;
                }

                String itemArg = args[1];
                String newName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

                // Check if it's a special item
                if (itemArg.startsWith("special:")) {
                    String specialId = itemArg.substring("special:".length());
                    var specialItem = plugin.getSpecialShopManager().getAllSpecialItems().get(specialId);
                    if (specialItem == null) {
                        sender.sendMessage("§cSpecial item not found: §e" + specialId);
                        return true;
                    }
                    if (newName.equalsIgnoreCase("clear") || newName.equalsIgnoreCase("reset")) {
                        plugin.getConfig().set("special_items." + specialId + ".name", specialId);
                        plugin.saveConfig();
                        plugin.getSpecialShopManager().reload();

                        sender.sendMessage("§a[DynamicShop] §fDisplay name for §e" + specialId + " §fhas been reset.");
                    } else {
                        plugin.getConfig().set("special_items." + specialId + ".name", newName);
                        plugin.saveConfig();
                        plugin.getSpecialShopManager().reload();
                        sender.sendMessage("§a[DynamicShop] §fDisplay name for §e" + specialId + " §fset to: §e" + newName);
                    }
                    if (plugin.getWebServer() != null) plugin.getWebServer().invalidateShopItemsCache();
                    return true;
                }

                // Regular material item
                Material mat = Material.matchMaterial(itemArg);
                if (mat == null || ShopDataManager.getBasePrice(mat) < 0) {
                    sender.sendMessage("§cItem not found in shop: §e" + itemArg);
                    return true;
                }

                if (newName.equalsIgnoreCase("clear") || newName.equalsIgnoreCase("reset")) {
                    ShopDataManager.removeCustomName(mat);
                    sender.sendMessage("§a[DynamicShop] §fCustom name for §e" + mat.name() + " §fhas been removed.");
                } else {
                    ShopDataManager.setCustomName(mat, newName);
                    sender.sendMessage("§a[DynamicShop] §fDisplay name for §e" + mat.name() + " §fset to: §e" + newName);
                }
                if (plugin.getWebServer() != null) plugin.getWebServer().invalidateShopItemsCache();
                return true;
            }

            // --------------------------------------------------------------
            // /shopadmin setinflation <percent>
            // --------------------------------------------------------------
            case "setinflation" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /shopadmin setinflation <percent>");
                    sender.sendMessage("§7Current: §e" + ConfigCacheManager.hourlyIncreasePercent + "% §7per hour");
                    return true;
                }

                double percent;
                try {
                    percent = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid number: " + args[1]);
                    return true;
                }

                if (percent < 0 || percent > 100) {
                    sender.sendMessage("§cPercent must be between 0 and 100.");
                    return true;
                }

                // Update config and cache
                plugin.getConfig().set("dynamic-pricing.hourly-increase-percent", percent);
                plugin.saveConfig();
                ConfigCacheManager.hourlyIncreasePercent = percent;

                sender.sendMessage("§a[DynamicShop] §fHourly price inflation set to §e" + percent + "% §fper hour.");
                return true;
            }

            // --------------------------------------------------------------
            // /shopadmin setshortagerate <item> <percent>
            // --------------------------------------------------------------
            case "setshortagerate" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /shopadmin setshortagerate <item> <percent>");
                    sender.sendMessage("§7Sets how steeply price rises per unit of negative stock depth.");
                    sender.sendMessage("§7Global default: §e" + ConfigCacheManager.negativeStockPercentPerItem + "%");
                    return true;
                }

                Material mat = Material.matchMaterial(args[1]);
                if (mat == null) {
                    sender.sendMessage("§cUnknown item: " + args[1]);
                    return true;
                }

                double percent;
                try {
                    percent = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid number: " + args[2]);
                    return true;
                }

                if (percent < 0 || percent > 100) {
                    sender.sendMessage("§cPercent must be between 0 and 100.");
                    return true;
                }

                ShopDataManager.setStockRate(mat, percent);

                sender.sendMessage("§a[DynamicShop] §fShortage rate for §e" + mat.name() + " §fset to §e" + percent + "%§f.");
                return true;
            }

            // --------------------------------------------------------------
            // /shopadmin changeshortagerate <item> <+/-percent>
            // --------------------------------------------------------------
            case "changeshortagerate" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /shopadmin changeshortagerate <item> <amount>");
                    sender.sendMessage("§7Adds or subtracts from the item's current shortage rate.");
                    return true;
                }

                Material mat = Material.matchMaterial(args[1]);
                if (mat == null) {
                    sender.sendMessage("§cUnknown item: " + args[1]);
                    return true;
                }

                double delta;
                try {
                    delta = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid number: " + args[2]);
                    return true;
                }

                double currentRate = ShopDataManager.getStockRate(mat);
                double newRate = Math.max(0, Math.min(100, currentRate + delta));

                ShopDataManager.setStockRate(mat, newRate);

                String sign = delta >= 0 ? "+" : "";
                sender.sendMessage("§a[DynamicShop] §fShortage rate for §e" + mat.name() + " §f" + sign + delta
                        + "% → §e" + String.format("%.1f", newRate) + "%§f.");
                return true;
            }

            // --------------------------------------------------------------
            // /shopadmin testbedrock
            // --------------------------------------------------------------
            case "testbedrock" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cOnly players can use this command.");
                    return true;
                }

                boolean wasForced = BedrockUtil.isForcedBedrock(p);
                BedrockUtil.setForceBedrock(p, !wasForced);

                if (!wasForced) {
                    sender.sendMessage("§a[DynamicShop] §fBedrock mode §aENABLED§f. Opening shop...");
                    p.performCommand("shop");
                } else {
                    sender.sendMessage("§a[DynamicShop] §fBedrock mode §cDISABLED§f.");
                }
                return true;
            }

            // --------------------------------------------------------------
            // /shopadmin reload
            // --------------------------------------------------------------
            case "reload" -> {
                plugin.reload(); // Use centralized reload method
                sender.sendMessage(plugin.getMessageManager().getMessage("reloaded"));
                return true;
            }

            // --------------------------------------------------------------
            // /shopadmin webadmin
            // --------------------------------------------------------------
            case "webadmin" -> {
                if (!sender.isOp()) {
                    sender.sendMessage("§cOnly server operators can use this command.");
                    return true;
                }

                var webServer = plugin.getWebServer();
                if (webServer == null) {
                    sender.sendMessage("§c[DynamicShop] Web server is not running. Enable it in config.yml.");
                    return true;
                }

                if (!plugin.getConfig().getBoolean("webserver.admin-enabled", true)) {
                    sender.sendMessage("§c[DynamicShop] Web admin panel is disabled in config.yml (webserver.admin-enabled: false).");
                    return true;
                }

                // Use in-game name for the admin account
                String playerName = (sender instanceof Player p) ? p.getName() : "Console";
                String token = webServer.getTokenManager().generateToken(playerName);
                int port = plugin.getConfig().getInt("webserver.port", 7713);

                // Use configured hostname, or detect from the player's connection address
                String hostname = plugin.getConfig().getString("webserver.hostname", "");
                if (hostname == null || hostname.isEmpty()) {
                    // Try the IP/domain the player used to connect (best for external access)
                    if (sender instanceof Player p) {
                        hostname = PaperCompat.getVirtualHost(p);
                    }
                    // Fall back to server-ip from server.properties
                    if (hostname == null || hostname.isEmpty() || "0.0.0.0".equals(hostname)) {
                        String serverIp = Bukkit.getIp();
                        if (serverIp != null && !serverIp.isEmpty() && !"0.0.0.0".equals(serverIp)) {
                            hostname = serverIp;
                        } else {
                            hostname = "localhost";
                        }
                    }
                }
                String url = "http://" + hostname + ":" + port + "/admin.html?token=" + token;

                sender.sendMessage("§a[DynamicShop] §fWeb Admin registration link for §e" + playerName + "§f!");
                sender.sendMessage("§e§n" + url);
                sender.sendMessage("§7This link expires in §f30 minutes§7.");
                sender.sendMessage("§7Do not share this link — it grants full admin access.");

                // If player, send clickable link
                if (sender instanceof Player p) {
                    net.kyori.adventure.text.Component clickable = net.kyori.adventure.text.Component.text("§a§l[Click here to open Admin Panel]")
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                    net.kyori.adventure.text.Component.text("Click to open the web admin panel")));
                    PaperCompat.sendMessage(p, clickable);
                }
                return true;
            }

            // --------------------------------------------------------------
            // /shopadmin categories
            // --------------------------------------------------------------
            case "categories" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cOnly players can use this command.");
                    return true;
                }

                AdminCategoryGUI gui = new AdminCategoryGUI(plugin, p);
                plugin.getShopListener().registerAdminCategory(p, gui);
                gui.open();
                return true;
            }

            // --------------------------------------------------------------
            // /shopadmin open <player> <category>
            // Opens a shop category for a specific player (can be run from console)
            // --------------------------------------------------------------
            case "open" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /shopadmin open <player> <category>");
                    return true;
                }

                String playerName = args[1];
                String categoryName = args[2].toUpperCase();

                Player target = Bukkit.getPlayer(playerName);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage("§cPlayer not found or offline: " + playerName);
                    return true;
                }

                // Match category
                ItemCategory category = null;
                for (ItemCategory c : ItemCategory.values()) {
                    if (c.name().equalsIgnoreCase(categoryName)) {
                        category = c;
                        break;
                    }
                }

                if (category == null) {
                    sender.sendMessage("§cUnknown category: " + categoryName);
                    sender.sendMessage("§7Available: " + String.join(", ",
                            Arrays.stream(ItemCategory.values())
                                    .filter(c -> CategoryConfigManager.getSlot(c) >= 0)
                                    .map(c -> c.name().toLowerCase())
                                    .toList()));
                    return true;
                }

                // Don't allow opening hidden categories
                if (CategoryConfigManager.getSlot(category) < 0) {
                    sender.sendMessage("§cCategory '" + categoryName + "' is hidden.");
                    return true;
                }

                // Open the shop for the target player
                ShopGUI shopGUI = new ShopGUI(plugin, target, category, true);
                plugin.getShopListener().registerShop(target, shopGUI);
                shopGUI.open();

                sender.sendMessage("§aOpened " + category.getDisplayName() + " shop for " + target.getName());
                return true;
            }

            // --------------------------------------------------------------
            // /shopadmin add ...
            // --------------------------------------------------------------
            case "add" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cOnly players can use this command.");
                    return true;
                }

                if (args.length < 2) {
                    sendAddHelp(sender);
                    return true;
                }

                String type = args[1].toLowerCase();

                // ----------------------------------------------------------
                // ADD NORMAL ITEM
                // /shopadmin add item <price> [category]
                // ----------------------------------------------------------
                if (type.equalsIgnoreCase("item")) {

                    if (args.length < 3) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin-usage-add-item"));
                        return true;
                    }

                    double price = parsePrice(sender, args[2]);
                    if (price < 0)
                        return true;

                    ItemStack held = p.getInventory().getItemInMainHand();
                    if (held == null || held.getType() == Material.AIR) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin-must-hold-item"));
                        return true;
                    }

                    Material mat = held.getType();
                    ItemCategory category = ShopDataManager.detectCategory(mat);
                    String customName = null;

                    // Optional category override: /shopadmin add item <price> <category> [custom name...]
                    if (args.length >= 4) {
                        // Try to parse args[3] as a category
                        try {
                            category = ItemCategory.valueOf(args[3].toUpperCase());
                            // If category parsed, remaining args are the custom name
                            if (args.length >= 5) {
                                customName = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                            }
                        } catch (IllegalArgumentException e) {
                            // args[3] is not a category — treat it and everything after as the custom name
                            customName = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                        }
                    }

                    // Apply custom name to the base material IMMEDIATELY, before any plugin-specific
                    // paths (ItemsAdder, Nexo, ValhallaMMO) that may return early.
                    if (customName != null && ShopDataManager.itemConfigs.containsKey(mat)) {
                        ShopDataManager.setCustomName(mat, customName);
                        sender.sendMessage("§a✓ §7Display name for §e" + mat.name().replace("_", " ") + " §7set to: §e" + customName);
                    }

                    String iaId = null;
                    if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
                        iaId = ItemsAdderWrapper.getCustomItemId(held);
                    }
                    String nexoId = null;
                    if (Bukkit.getPluginManager().getPlugin("Nexo") != null) {
                        nexoId = NexoWrapper.getCustomItemId(held);
                    }
                    String valhallaId = null;
                    if (Bukkit.getPluginManager().getPlugin("ValhallaMMO") != null) {
                        valhallaId = org.minecraftsmp.dynamicshop.managers.ValhallaMMOWrapper.getCustomItemId(held);
                    }

                    if (iaId != null || nexoId != null || valhallaId != null) {
                        String id = iaId != null ? iaId : (nexoId != null ? nexoId : valhallaId);
                        id = id.replace(":", "_");
                        String customId = iaId != null ? iaId : (nexoId != null ? nexoId : valhallaId);

                        // Delegate to addServerShop logic
                        plugin.getSpecialShopManager().addServerShopItem(id, customId, price, id, mat, null, true);
                        
                        // Update the config delivery method correctly
                        String basePath = "special_items." + id;
                        String deliveryMethod = iaId != null ? "itemsadder" : (nexoId != null ? "nexo" : "valhallammo");
                        plugin.getConfig().set(basePath + ".delivery_method", deliveryMethod);
                        plugin.getConfig().set(basePath + ".nbt", customId);
                        plugin.saveConfig();
                        plugin.getSpecialShopManager().reload(); // Reload to apply delivery method

                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("item", id);
                        placeholders.put("price", String.valueOf(price));
                        placeholders.put("category", "Server items");

                        sender.sendMessage(plugin.getMessageManager().getMessageWithPrefix(
                                "admin-server-shop-added", placeholders));
                        return true;
                    }
                    // Check if the held item has custom components (enchantments, name, lore, etc.)
                    ItemStack plainCheck = new ItemStack(mat);
                    boolean hasCustomComponents = !held.isSimilar(plainCheck);

                    // If item has custom components AND the material already exists in the shop,
                    // add it as a server-shop item (stored_item) instead of overwriting the regular entry.
                    // This prevents an enchanted diamond sword from replacing a plain diamond sword.
                    if (hasCustomComponents && ShopDataManager.itemConfigs.containsKey(mat)) {
                        // Generate a unique ID based on material + timestamp
                        String id = mat.name().toLowerCase() + "_custom_" + System.currentTimeMillis() % 100000;
                        String displayName = customName != null ? customName : id;

                        // Store as a special server-shop item with stored_item delivery
                        plugin.getSpecialShopManager().addServerShopItem(id, displayName, price, id, mat, null, true);

                        // Place it in the same category as the base material (e.g., TOOLS, COMBAT)
                        String basePath = "special_items." + id;
                        plugin.getConfig().set(basePath + ".delivery_method", "stored_item");
                        plugin.getConfig().set(basePath + ".stored_item", held.clone());
                        plugin.getConfig().set(basePath + ".category", category.name());
                        if (customName != null) {
                            plugin.getConfig().set(basePath + ".name", customName);
                        }
                        plugin.saveConfig();
                        plugin.getSpecialShopManager().reload();

                        sender.sendMessage("§a✓ §7Added as custom variant in §e" + category.getDisplayName() + "§7.");
                        sender.sendMessage("§7ID: §f" + id);
                        if (customName != null) {
                            sender.sendMessage("§7Display name: §e" + customName);
                        }
                        sender.sendMessage("§7The regular §e" + mat.name().replace("_", " ") + " §7in the shop is unchanged.");
                        return true;
                    }

                    // Write to config as a regular shop item
                    plugin.getConfig().set("items." + mat.name() + ".base", price);

                    // Save the category override if explicitly specified
                    if (category != ShopDataManager.detectCategory(mat)) {
                        plugin.getConfig().set("items." + mat.name() + ".category", category.name());
                    }

                    plugin.saveConfig();

                    // If item has custom components and material is NOT yet in the shop,
                    // store it as a template so buyers receive the full item
                    if (hasCustomComponents) {
                        ShopDataManager.setTemplate(mat, held);
                        sender.sendMessage("§a✓ §7Item has custom components — template stored! Buyers will receive the exact item.");
                    } else {
                        // Remove any old template if re-adding as plain
                        ShopDataManager.removeTemplate(mat);
                    }

                    ShopDataManager.reload();

                    // Set custom display name AFTER reload so it persists in both config and memory
                    if (customName != null) {
                        ShopDataManager.setCustomName(mat, customName);
                    }

                    // Initialize stock so the item is immediately purchasable at ~base price.
                    // Half of maxStock is the pricing midpoint where price ≈ base price.
                    if (ShopDataManager.getStock(mat) == 0) {
                        double initialStock = ConfigCacheManager.maxStock / 2.0;
                        ShopDataManager.ShopItemConfig cfg = ShopDataManager.itemConfigs.get(mat);
                        if (cfg != null && cfg.maxStock() != null) {
                            initialStock = cfg.maxStock() / 2.0;
                        }
                        ShopDataManager.setStockDirect(mat, initialStock);
                        ShopDataManager.setHoursInShortage(mat, 0.0);
                        ShopDataManager.saveDynamicData();
                        sender.sendMessage("§7Initial stock set to §e" + (int) initialStock + "§7. Adjust with §f/shopadmin setstock " + mat.name() + " <amount>");
                    }

                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("item", mat.name().replace("_", " ").toLowerCase());
                    placeholders.put("price", String.valueOf(price));
                    placeholders.put("category", category.name());

                    sender.sendMessage(plugin.getMessageManager().getMessageWithPrefix(
                            "admin-item-added", placeholders));
                    if (customName != null) {
                        sender.sendMessage("§7Display name: §e" + customName);
                    }
                    return true;
                }

                // ----------------------------------------------------------
                // ADD PERMISSION ITEM
                // /shopadmin add perm <price> <permission.node> [requiresperm
                // <required.permission>]
                // ----------------------------------------------------------
                if (type.equalsIgnoreCase("perm")) {

                    if (args.length < 4) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin-usage-add-perm"));
                        return true;
                    }

                    double price = parsePrice(sender, args[2]);
                    if (price < 0)
                        return true;

                    String perm = args[3];

                    // Check for requiresperm parameter
                    String requiredPerm = null;
                    if (args.length >= 6 && args[4].equalsIgnoreCase("requiresperm")) {
                        requiredPerm = args[5];
                    }

                    // Display material = held item or default EMERALD
                    ItemStack heldItem = p.getInventory().getItemInMainHand();
                    Material displayMaterial = (heldItem != null && heldItem.getType() != Material.AIR)
                            ? heldItem.getType()
                            : Material.EMERALD;

                    plugin.getSpecialShopManager().addPermissionItem(perm, null, price, displayMaterial, requiredPerm);

                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("permission", perm);
                    placeholders.put("price", String.valueOf(price));

                    sender.sendMessage(plugin.getMessageManager().getMessageWithPrefix(
                            "admin-permission-added", placeholders));

                    if (requiredPerm != null) {
                        sender.sendMessage("§7Requires permission: §e" + requiredPerm);
                    }

                    return true;
                }

                // ----------------------------------------------------------
                // ADD GROUP ITEM
                // /shopadmin add group <price> <groupname> [requiresperm <required.permission>]
                // ----------------------------------------------------------
                if (type.equalsIgnoreCase("group")) {

                    if (args.length < 4) {
                        sender.sendMessage("§cUsage: /shopadmin add group <price> <groupname> [requiresperm <perm>]");
                        return true;
                    }

                    double price = parsePrice(sender, args[2]);
                    if (price < 0)
                        return true;

                    String groupName = args[3];

                    String requiredPerm = null;
                    if (args.length >= 6 && args[4].equalsIgnoreCase("requiresperm")) {
                        requiredPerm = args[5];
                    }

                    // Display material = held item or default NETHER_STAR
                    ItemStack heldItem = p.getInventory().getItemInMainHand();
                    Material displayMaterial = (heldItem != null && heldItem.getType() != Material.AIR)
                            ? heldItem.getType()
                            : Material.NETHER_STAR;

                    plugin.getSpecialShopManager().addGroupItem(groupName, null, price, displayMaterial, requiredPerm);

                    sender.sendMessage("§a✓ §7Added group item: §e" + groupName + " §7for §e"
                            + plugin.getEconomyManager().format(price));
                    if (requiredPerm != null) {
                        sender.sendMessage("§7Requires permission: §e" + requiredPerm);
                    }

                    return true;
                }

                // --------------------------------------------------------------
                // ADD CUSTOM SERVER-SHOP ITEM
                // /shopadmin add server-shop <price> <identifier> [requiresperm
                // <required.permission>]
                //
                // Spawner shortcut:
                // /shopadmin add server-shop <price> <id> spawner <mob_type>
                // Example: /shopadmin add server-shop 5000 pig_spawner spawner pig
                //
                // Command mode:
                // /shopadmin add server-shop <price> <id> command <command...>
                // Example: /shopadmin add server-shop 1000 kit_vip command essentials:kit vip
                // {player}
                //
                // Component mode (1.21+):
                // /shopadmin add server-shop <price> <id> component <material> <component_data>
                // --------------------------------------------------------------
                if (type.equalsIgnoreCase("server-shop")) {

                    if (args.length < 4) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin-usage-add-server-shop"));
                        return true;
                    }

                    double price = parsePrice(sender, args[2]);
                    if (price < 0)
                        return true;

                    String id = args[3];

                    // Get held item material (defaults to CHEST if not holding anything)
                    ItemStack heldItem = p.getInventory().getItemInMainHand();
                    Material displayMaterial = (heldItem != null && heldItem.getType() != Material.AIR)
                            ? heldItem.getType()
                            : Material.CHEST;

                    // Check for requiresperm before special modes
                    String requiredPerm = null;
                    int modeStartIndex = 4;

                    if (args.length >= 6 && args[4].equalsIgnoreCase("requiresperm")) {
                        requiredPerm = args[5];
                        modeStartIndex = 6; // Special modes start after requiresperm
                    }

                    // Register base server-shop item
                    // Auto-configure as ItemsAdder/Nexo item if applicable
                    plugin.getSpecialShopManager().addServerShopItem(id, id, price, id, displayMaterial, requiredPerm,
                            true); // Temporary step

                    // Check for special modes
                    if (args.length >= modeStartIndex + 1) {
                        String mode = args[modeStartIndex].toLowerCase();
                        String itemId = id.toLowerCase().replace(" ", "_");
                        String basePath = "special_items." + itemId;

                        // SPAWNER MODE (shortcut for 1.21 spawners)
                        if (mode.equals("spawner")) {
                            if (args.length < modeStartIndex + 2) {
                                sender.sendMessage(
                                        "§cUsage: /shopadmin add server-shop <price> <id> spawner <mob_type>");
                                return true;
                            }
                            String mobType = args[modeStartIndex + 1].toLowerCase();

                            // Build 1.21 component data
                            String componentData = "block_entity_data={id:\"minecraft:mob_spawner\",SpawnData:{entity:{id:\"minecraft:"
                                    + mobType + "\"}}}";

                            plugin.getConfig().set(basePath + ".delivery_method", "component");
                            plugin.getConfig().set(basePath + ".material", "SPAWNER");
                            plugin.getConfig().set(basePath + ".nbt", componentData);
                            plugin.getConfig().set(basePath + ".display_material", "SPAWNER");
                            plugin.saveConfig();

                            sender.sendMessage("§aAdded spawner: §e" + id + " §7(mob: " + mobType + ")");

                            // Reload to apply changes
                            plugin.getSpecialShopManager().reload();
                            return true;
                        }

                        // COMMAND MODE (run any command when purchased)
                        if (mode.equals("command")) {
                            if (args.length < modeStartIndex + 2) {
                                sender.sendMessage(
                                        "§cUsage: /shopadmin add server-shop <price> <id> command <command_string>");
                                return true;
                            }
                            String command = String.join(" ",
                                    Arrays.copyOfRange(args, modeStartIndex + 1, args.length));

                            plugin.getConfig().set(basePath + ".delivery_method", "command");
                            plugin.getConfig().set(basePath + ".nbt", command); // Store command in NBT field
                            plugin.saveConfig();

                            sender.sendMessage("§aAdded command item: §e" + id);
                            sender.sendMessage("§7Command: §f/" + command);
                            sender.sendMessage("§7Use {player} as placeholder for player name");

                            // Reload to apply changes
                            plugin.getSpecialShopManager().reload();
                            return true;
                        }

                        // VALHALLAMMO MODE
                        if (mode.equals("valhallammo")) {
                            if (args.length < modeStartIndex + 2) {
                                sender.sendMessage(
                                        "§cUsage: /shopadmin add server-shop <price> <id> valhallammo <valhallammo_id>");
                                return true;
                            }
                            String valhallaId = args[modeStartIndex + 1].toLowerCase();

                            plugin.getConfig().set(basePath + ".delivery_method", "valhallammo");
                            plugin.getConfig().set(basePath + ".nbt", valhallaId);
                            plugin.saveConfig();

                            sender.sendMessage("§aAdded ValhallaMMO item: §e" + id);
                            sender.sendMessage("§7ValhallaMMO ID: §f" + valhallaId);

                            // Reload to apply changes
                            plugin.getSpecialShopManager().reload();
                            return true;
                        }

                        // COMPONENT MODE (generic 1.21 components)
                        // Usage 1: /shopadmin add server-shop <price> <id> component
                        //   → Captures held item as stored_item (preserves all components)
                        // Usage 2: /shopadmin add server-shop <price> <id> component <material> <component_data>
                        //   → Manual component string (for spawner shortcuts, etc.)
                        if (mode.equals("component")) {
                            if (args.length < modeStartIndex + 2) {
                                // No extra args — capture the held item as stored_item
                                ItemStack heldCustom = p.getInventory().getItemInMainHand();
                                if (heldCustom == null || heldCustom.getType() == Material.AIR) {
                                    sender.sendMessage("§cHold the item you want to add, or provide material + component data.");
                                    sender.sendMessage("§7Usage: /shopadmin add server-shop <price> <id> component [material] [component_data]");
                                    return true;
                                }

                                ItemCategory detectedCategory = ShopDataManager.detectCategory(heldCustom.getType());

                                plugin.getConfig().set(basePath + ".delivery_method", "stored_item");
                                plugin.getConfig().set(basePath + ".stored_item", heldCustom.clone());
                                plugin.getConfig().set(basePath + ".display_material", heldCustom.getType().name());
                                plugin.getConfig().set(basePath + ".category", detectedCategory.name());

                                plugin.saveConfig();
                                plugin.getSpecialShopManager().reload();

                                sender.sendMessage("§a✓ §7Stored custom item as §e" + id + " §7(stored_item).");
                                sender.sendMessage("§7All item components preserved. Category: §e" + detectedCategory.name());
                                return true;
                            }

                            // Extra args provided — manual component string mode
                            if (args.length < modeStartIndex + 3) {
                                sender.sendMessage(
                                        "§cUsage: /shopadmin add server-shop <price> <id> component <material> <component_data>");
                                return true;
                            }

                            String materialName = args[modeStartIndex + 1];
                            String componentData = String.join(" ",
                                    Arrays.copyOfRange(args, modeStartIndex + 2, args.length));

                            plugin.getConfig().set(basePath + ".delivery_method", "component");
                            plugin.getConfig().set(basePath + ".material", materialName.toUpperCase());
                            plugin.getConfig().set(basePath + ".nbt", componentData);
                            plugin.saveConfig();

                            // Reload to apply changes
                            plugin.getSpecialShopManager().reload();
                        }

                        // OLD NBT MODE (kept for backwards compatibility, will auto-convert)
                        if (mode.equals("nbt")) {
                            if (args.length < 7) {
                                sender.sendMessage(
                                        "§cUsage: /shopadmin add server-shop <price> <id> nbt <material> <nbt_string>");
                                return true;
                            }

                            String materialName = args[5];
                            String nbtString = String.join(" ", Arrays.copyOfRange(args, 6, args.length));

                            plugin.getConfig().set(basePath + ".delivery_method", "nbt");
                            plugin.getConfig().set(basePath + ".material", materialName.toUpperCase());
                            plugin.getConfig().set(basePath + ".nbt", nbtString);
                            plugin.saveConfig();

                            // Reload to apply changes
                            plugin.getSpecialShopManager().reload();
                        }
                    }

                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("identifier", id);
                    placeholders.put("price", String.valueOf(price));

                    sender.sendMessage(plugin.getMessageManager().getMessageWithPrefix(
                            "admin-server-shop-added", placeholders));
                    return true;
                }

                // If type not recognized
                sendAddHelp(sender);
                return true;
            }

            // --------------------------------------------------------------
            // /shopadmin remove ...
            // --------------------------------------------------------------
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /shopadmin remove perm <slot>");
                    return true;
                }

                String removeType = args[1].toLowerCase();

                // /shopadmin remove perm <slot>
                if (removeType.equals("perm")) {
                    if (args.length < 3) {
                        sender.sendMessage("§cUsage: /shopadmin remove perm <slot>");
                        sender.sendMessage("§7Slot numbers start at 1");
                        return true;
                    }

                    int slot;
                    try {
                        slot = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cInvalid slot number: " + args[2]);
                        return true;
                    }

                    int count = plugin.getSpecialShopManager().getPermissionItemCount();
                    if (slot < 1 || slot > count) {
                        sender.sendMessage("§cInvalid slot. There are only §e" + count + " §cpermission items.");
                        return true;
                    }

                    // Convert 1-based slot to 0-based index
                    var item = plugin.getSpecialShopManager().getPermissionItemByIndex(slot - 1);
                    if (item == null) {
                        sender.sendMessage("§cCould not find permission item at slot " + slot);
                        return true;
                    }

                    String itemId = item.getId();
                    String permission = item.getPermission();
                    boolean removed = plugin.getSpecialShopManager().removeSpecialItem(itemId);

                    if (removed) {
                        sender.sendMessage("§a✓ §7Removed permission item: §e" + permission);
                    } else {
                        sender.sendMessage("§cFailed to remove permission item.");
                    }
                    return true;
                }

                // /shopadmin remove group <slot>
                if (removeType.equals("group")) {
                    if (args.length < 3) {
                        sender.sendMessage("§cUsage: /shopadmin remove group <slot>");
                        sender.sendMessage("§7Slot numbers start at 1");
                        return true;
                    }

                    int slot;
                    try {
                        slot = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cInvalid slot number: " + args[2]);
                        return true;
                    }

                    int count = plugin.getSpecialShopManager().getGroupItemCount();
                    if (slot < 1 || slot > count) {
                        sender.sendMessage("§cInvalid slot. There are only §e" + count + " §cgroup items.");
                        return true;
                    }

                    var gItem = plugin.getSpecialShopManager().getGroupItemByIndex(slot - 1);
                    if (gItem == null) {
                        sender.sendMessage("§cCould not find group item at slot " + slot);
                        return true;
                    }

                    boolean gRemoved = plugin.getSpecialShopManager().removeSpecialItem(gItem.getId());
                    if (gRemoved) {
                        sender.sendMessage("§a✓ §7Removed group item: §e" + gItem.getGroupName());
                    } else {
                        sender.sendMessage("§cFailed to remove group item.");
                    }
                    return true;
                }

                sender.sendMessage("§cUnknown remove type. Use: /shopadmin remove perm <slot> | remove group <slot>");
                return true;
            }

            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    // --------------------------------------------------------------------
    // UTILITY
    // --------------------------------------------------------------------
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("admin-help-header"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin-help-reload"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin-help-add-item"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin-help-add-perm"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin-help-add-server-shop"));
        sender.sendMessage("§7/shopadmin remove perm <slot>");
        sender.sendMessage("§7/shopadmin open <player> <category>");
        sender.sendMessage("§7/shopadmin setinflation <percent>");
        sender.sendMessage("§7/shopadmin setstock <item|all> <amount>");
        sender.sendMessage("§7/shopadmin setshortagerate <item> <percent>");
        sender.sendMessage("§7/shopadmin changeshortagerate <item> <+/-amount>");
    }

    private void sendAddHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("admin-add-usage-header"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin-usage-add-item"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin-usage-add-perm"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin-usage-add-server-shop"));
    }

    private double parsePrice(CommandSender sender, String raw) {
        try {
            double d = Double.parseDouble(raw);
            if (d < 0) {
                sender.sendMessage(plugin.getMessageManager().getMessage("admin-price-negative"));
                return -1;
            }
            return d;
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessageManager().getMessage("invalid-number"));
            return -1;
        }
    }

    // --------------------------------------------------------------------
    // TAB COMPLETION
    // --------------------------------------------------------------------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {

        List<String> out = new ArrayList<>();

        if (!sender.hasPermission("dynamicshop.admin")) {
            return out;
        }

        if (args.length == 1) {
            out.add("reload");
            out.add("add");
            out.add("remove");
            out.add("setstock");
            out.add("setname");
            out.add("resetshortage");
            out.add("setinflation");
            out.add("setshortagerate");
            out.add("changeshortagerate");
            out.add("testbedrock");
            out.add("categories");
            out.add("open");
            out.add("webadmin");
            return out;
        }

        // /shopadmin open <player> <category>
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("open")) {
            for (ItemCategory c : ItemCategory.values()) {
                if (CategoryConfigManager.getSlot(c) >= 0) {
                    out.add(c.name().toLowerCase());
                }
            }
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            out.add("perm");
            out.add("group");
            return out;
        }

        // /shopadmin setstock <item|all>
        if (args.length == 2 && args[0].equalsIgnoreCase("setstock")) {
            out.add("all");
            String partial = args[1].toUpperCase();
            for (Material m : Material.values()) {
                if (ShopDataManager.getBasePrice(m) >= 0 && m.name().startsWith(partial)) {
                    out.add(m.name());
                }
            }
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setstock")) {
            out.add("<amount>");
            return out;
        }

        // /shopadmin setname <item>
        if (args.length == 2 && args[0].equalsIgnoreCase("setname")) {
            String partial = args[1].toUpperCase();
            for (Material m : Material.values()) {
                if (ShopDataManager.getBasePrice(m) >= 0 && m.name().startsWith(partial)) {
                    out.add(m.name());
                }
            }
            return out;
        }

        // Tab complete item names for setshortagerate/changeshortagerate
        if (args.length == 2 && (args[0].equalsIgnoreCase("setshortagerate") || args[0].equalsIgnoreCase("changeshortagerate"))) {
            String partial = args[1].toUpperCase();
            for (Material m : Material.values()) {
                if (ShopDataManager.getBasePrice(m) >= 0 && m.name().startsWith(partial)) {
                    out.add(m.name());
                }
            }
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setshortagerate")) {
            out.add("<percent>");
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("changeshortagerate")) {
            out.add("<+/-amount>");
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("remove") && args[1].equalsIgnoreCase("perm")) {
            int count = plugin.getSpecialShopManager().getPermissionItemCount();
            for (int i = 1; i <= count; i++) {
                out.add(String.valueOf(i));
            }
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("remove") && args[1].equalsIgnoreCase("group")) {
            int count = plugin.getSpecialShopManager().getGroupItemCount();
            for (int i = 1; i <= count; i++) {
                out.add(String.valueOf(i));
            }
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            out.add("item");
            out.add("perm");
            out.add("group");
            out.add("server-shop");
            return out;
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("item")) {
            out.add("<price>");
        } else if (args.length == 4 && args[1].equalsIgnoreCase("item")) {
            // Suggest categories for /shopadmin add item <price> [category]
            String partial = args[3].toUpperCase();
            for (ItemCategory c : ItemCategory.values()) {
                if (c != ItemCategory.PLAYER_SHOPS && c.name().startsWith(partial)) {
                    out.add(c.name().toLowerCase());
                }
            }
        } else if (args.length == 3 && args[1].equalsIgnoreCase("perm")) {
            out.add("<price>");
        } else if (args.length == 4 && args[1].equalsIgnoreCase("perm")) {
            out.add("<permission.node>");
        } else if (args.length == 3 && args[1].equalsIgnoreCase("group")) {
            out.add("<price>");
        } else if (args.length == 4 && args[1].equalsIgnoreCase("group")) {
            out.add("<groupname>");
        } else if (args.length == 3 && args[1].equalsIgnoreCase("server-shop")) {
            out.add("<price>");
        } else if (args.length == 4 && args[1].equalsIgnoreCase("server-shop")) {
            out.add("<id>");
        } else if (args.length == 5 && args[1].equalsIgnoreCase("server-shop")) {
            out.add("requiresperm");
            out.add("spawner");
            out.add("command");
            out.add("component");
            if (Bukkit.getPluginManager().getPlugin("ValhallaMMO") != null) out.add("valhallammo");
        } else if (args.length == 6 && args[1].equalsIgnoreCase("server-shop") && args[4].equalsIgnoreCase("valhallammo")) {
            if (Bukkit.getPluginManager().getPlugin("ValhallaMMO") != null) {
                out.addAll(org.minecraftsmp.dynamicshop.managers.ValhallaMMOWrapper.getAllItemIds());
            }
        } else if (args.length == 7 && args[1].equalsIgnoreCase("server-shop") && args[4].equalsIgnoreCase("requiresperm")) {
            out.add("spawner");
            out.add("command");
            out.add("component");
            if (Bukkit.getPluginManager().getPlugin("ValhallaMMO") != null) out.add("valhallammo");
        } else if (args.length == 8 && args[1].equalsIgnoreCase("server-shop") && args[6].equalsIgnoreCase("valhallammo")) {
            if (Bukkit.getPluginManager().getPlugin("ValhallaMMO") != null) {
                out.addAll(org.minecraftsmp.dynamicshop.managers.ValhallaMMOWrapper.getAllItemIds());
            }
        }

        return out;
    }
}
