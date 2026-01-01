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
import org.minecraftsmp.dynamicshop.gui.AdminCategoryGUI;
import org.minecraftsmp.dynamicshop.gui.ShopGUI;
import org.minecraftsmp.dynamicshop.managers.CategoryConfigManager;
import org.minecraftsmp.dynamicshop.managers.ItemsAdderWrapper;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;

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
            // /shopadmin reload
            // --------------------------------------------------------------
            case "reload" -> {
                plugin.reload(); // Use centralized reload method
                sender.sendMessage(plugin.getMessageManager().getMessage("reloaded"));
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
                ShopGUI shopGUI = new ShopGUI(plugin, target, category);
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
                // /shopadmin add item <price>
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

                    String iaId = null;
                    if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
                        iaId = ItemsAdderWrapper.getCustomItemId(held);
                    }

                    if (iaId != null) {
                        // Redirect to server-shop logic if it's an IA item
                        // Because 'item' mode requires Enum Material, we can't save IA item as normal
                        // shop item easily
                        // unless we map it to server-shop item.
                        // Suggest current command isn't best for custom items, OR automatically switch
                        // to server-shop mode?
                        // Let's print a message for now that they should use 'add server-shop' or just
                        // auto-create it as server-shop.

                        // Better approach for UX: Auto-redirect to server-shop creation
                        String id = iaId.replace(":", "_");

                        // Delegate to addServerShop logic
                        plugin.getSpecialShopManager().addServerShopItem(id, iaId, price, id, mat, null, true);

                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("item", id);
                        placeholders.put("price", String.valueOf(price));
                        placeholders.put("category", "Server items"); // IA items are virtually server items

                        sender.sendMessage(plugin.getMessageManager().getMessageWithPrefix(
                                "admin-server-shop-added", placeholders));
                        return true;
                    }

                    // Write to config
                    plugin.getConfig().set("items." + mat.name() + ".base", price);
                    plugin.saveConfig();

                    ShopDataManager.reload();

                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("item", mat.name().replace("_", " ").toLowerCase());
                    placeholders.put("price", String.valueOf(price));
                    placeholders.put("category", category.name());

                    sender.sendMessage(plugin.getMessageManager().getMessageWithPrefix(
                            "admin-item-added", placeholders));
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

                    plugin.getSpecialShopManager().addPermissionItem(perm, price, displayMaterial, requiredPerm);

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
                    // Auto-configure as ItemsAdder item
                    plugin.getSpecialShopManager().addServerShopItem(id, id, price, id, displayMaterial, requiredPerm,
                            true); // Temporary step

                    // Check for special modes
                    if (args.length >= modeStartIndex + 2) {
                        String mode = args[modeStartIndex].toLowerCase();
                        String itemId = id.toLowerCase().replace(" ", "_");
                        String basePath = "special_items." + itemId;

                        // SPAWNER MODE (shortcut for 1.21 spawners)
                        if (mode.equals("spawner")) {
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

                        // COMPONENT MODE (generic 1.21 components)
                        if (mode.equals("component")) {
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

                sender.sendMessage("§cUnknown remove type. Use: /shopadmin remove perm <slot>");
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
            out.add("resetshortage");
            out.add("categories");
            out.add("open");
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
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("remove") && args[1].equalsIgnoreCase("perm")) {
            int count = plugin.getSpecialShopManager().getPermissionItemCount();
            for (int i = 1; i <= count; i++) {
                out.add(String.valueOf(i));
            }
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            out.add("item");
            out.add("perm");
            out.add("server-shop");
            return out;
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("item")) {
            out.add("<price>");
        } else if (args.length == 3 && args[1].equalsIgnoreCase("perm")) {
            out.add("<price>");
        } else if (args.length == 4 && args[1].equalsIgnoreCase("perm")) {
            out.add("<permission.node>");
        } else if (args.length == 3 && args[1].equalsIgnoreCase("server-shop")) {
            out.add("<price>");
        } else if (args.length == 4 && args[1].equalsIgnoreCase("server-shop")) {
            out.add("<id>");
        }

        return out;
    }
}