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
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /shopadmin command
 *
 *  Supported:
 *   /shopadmin reload
 *   /shopadmin add item <price>
 *   /shopadmin add perm <price> <permission.node>
 *   /shopadmin add server-shop <price> <identifier>
 *
 *   Notes:
 *    - For server shop NBT items:
 *      /shopadmin add server-shop <price> <id> nbt <material> <nbt string...>
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
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            // --------------------------------------------------------------
            // /shopadmin reload
            // --------------------------------------------------------------
            case "reload" -> {
                plugin.reloadConfig();
                ShopDataManager.reload();
                plugin.getSpecialShopManager().reload(); // Reload special items too
                sender.sendMessage(plugin.getMessageManager().getMessage("reloaded"));
                return true;
            }

            // --------------------------------------------------------------
            // /shopadmin add ...
            // --------------------------------------------------------------
            case "add" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command.");
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
                    if (price < 0) return true;

                    ItemStack held = p.getInventory().getItemInMainHand();
                    if (held == null || held.getType() == Material.AIR) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin-must-hold-item"));
                        return true;
                    }

                    Material mat = held.getType();
                    ItemCategory category = ShopDataManager.detectCategory(mat);

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
                // /shopadmin add perm <price> <permission.node> [requiresperm <required.permission>]
                // ----------------------------------------------------------
                if (type.equalsIgnoreCase("perm")) {

                    if (args.length < 4) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin-usage-add-perm"));
                        return true;
                    }

                    double price = parsePrice(sender, args[2]);
                    if (price < 0) return true;

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
                // /shopadmin add server-shop <price> <identifier> [requiresperm <required.permission>]
                //
                // Spawner shortcut:
                // /shopadmin add server-shop <price> <id> spawner <mob_type>
                // Example: /shopadmin add server-shop 5000 pig_spawner spawner pig
                //
                // Command mode:
                // /shopadmin add server-shop <price> <id> command <command...>
                // Example: /shopadmin add server-shop 1000 kit_vip command essentials:kit vip {player}
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
                    if (price < 0) return true;

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
                    plugin.getSpecialShopManager().addServerShopItem(id, price, displayMaterial, requiredPerm);

                    // Check for special modes
                    if (args.length >= modeStartIndex + 2) {
                        String mode = args[modeStartIndex].toLowerCase();
                        String itemId = id.toLowerCase().replace(" ", "_");
                        String basePath = "special_items." + itemId;

                        // SPAWNER MODE (shortcut for 1.21 spawners)
                        if (mode.equals("spawner")) {
                            String mobType = args[modeStartIndex + 1].toLowerCase();

                            // Build 1.21 component data
                            String componentData = "block_entity_data={id:\"minecraft:mob_spawner\",SpawnData:{entity:{id:\"minecraft:" + mobType + "\"}}}";

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
                            String command = String.join(" ", Arrays.copyOfRange(args, modeStartIndex + 1, args.length));

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
                                sender.sendMessage("§cUsage: /shopadmin add server-shop <price> <id> component <material> <component_data>");
                                return true;
                            }

                            String materialName = args[modeStartIndex + 1];
                            String componentData = String.join(" ", Arrays.copyOfRange(args, modeStartIndex + 2, args.length));

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
                                sender.sendMessage("§cUsage: /shopadmin add server-shop <price> <id> nbt <material> <nbt_string>");
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