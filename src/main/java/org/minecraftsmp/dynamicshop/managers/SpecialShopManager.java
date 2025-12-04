package org.minecraftsmp.dynamicshop.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.category.SpecialShopItem;
import org.minecraftsmp.dynamicshop.transactions.Transaction;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles Special Shop items (Permission items & Server-Shop items)
 * VERSION FOR MINECRAFT 1.21+ (Data Components)
 */
public class SpecialShopManager {

    private final DynamicShop plugin;
    private final Map<String, SpecialShopItem> registry = new HashMap<>();

    public SpecialShopManager(DynamicShop plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------
    // INITIALIZATION
    // ------------------------------------------------------------
    public void init() {
        loadFromConfig();
    }

    // ------------------------------------------------------------
    // RELOAD
    // ------------------------------------------------------------
    public void reload() {
        registry.clear();
        loadFromConfig();
        plugin.getLogger().info("Reloaded " + registry.size() + " special shop items");
    }

    // ------------------------------------------------------------
    // ADD PERMISSION SHOP ITEM
    // ------------------------------------------------------------
    public void addPermissionItem(String permission, double price, Material displayMaterial, String requiredPermission) {
        String id = permission.toLowerCase().replace(".", "_");
        String displayName = permission.replace(".", "_");
        addPermissionItem(id, displayName, price, permission, displayMaterial, requiredPermission, true);
    }

    private void addPermissionItem(String id, String displayName, double price, String permission, Material displayMaterial, String requiredPermission, boolean save) {
        SpecialShopItem item = SpecialShopItem.forPermission(id, displayName, price, permission, displayMaterial, requiredPermission);
        registry.put(id, item);

        if (save) {
            String path = "special_items." + id;
            plugin.getConfig().set(path + ".type", "perm");
            plugin.getConfig().set(path + ".name", displayName);
            plugin.getConfig().set(path + ".permission", permission);
            plugin.getConfig().set(path + ".price", price);
            plugin.getConfig().set(path + ".display_material", displayMaterial.name());
            if (requiredPermission != null && !requiredPermission.isEmpty()) {
                plugin.getConfig().set(path + ".required_permission", requiredPermission);
            }
            plugin.saveConfig();
        }
    }

    // ------------------------------------------------------------
    // ADD SERVER-SHOP ITEM
    // ------------------------------------------------------------
    public void addServerShopItem(String identifier, double price, Material displayMaterial, String requiredPermission) {
        String id = identifier.toLowerCase().replace(" ", "_");
        String displayName = identifier;
        addServerShopItem(id, displayName, price, identifier, displayMaterial, requiredPermission, true);
    }

    private void addServerShopItem(String id, String displayName, double price, String identifier, Material displayMaterial, String requiredPermission, boolean save) {
        String path = "special_items." + id;

        SpecialShopItem item = SpecialShopItem.forServerShop(id, displayName, price, identifier, displayMaterial, requiredPermission);

        if (!save) {
            // Loading from config
            String delivery = plugin.getConfig().getString(path + ".delivery_method");
            if (delivery != null && !delivery.isEmpty()) {
                item.setDeliveryMethod(delivery);
            }

            String matName = plugin.getConfig().getString(path + ".material");
            if (matName != null && !matName.isEmpty()) {
                try {
                    item.setMaterial(Material.valueOf(matName.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[DynamicShop] Invalid material '" + matName +
                            "' for special item " + id + ", using display material instead.");
                    item.setMaterial(displayMaterial);
                }
            } else {
                item.setMaterial(displayMaterial);
            }

            int amount = plugin.getConfig().getInt(path + ".amount", 1);
            item.setAmount(amount);

            String nbt = plugin.getConfig().getString(path + ".nbt");
            if (nbt != null && !nbt.isEmpty()) {
                item.setNbt(nbt);
            }

            String component = plugin.getConfig().getString(path + ".component");
            if (component != null && !component.isEmpty()) {
                item.setNbt(component); // Reuse NBT field for component data
            }
        } else {
            // Creating new item
            item.setDeliveryMethod("item");
            item.setMaterial(displayMaterial);
            item.setAmount(1);
        }

        registry.put(id, item);

        if (save) {
            plugin.getConfig().set(path + ".type", "server-shop");
            plugin.getConfig().set(path + ".name", displayName);
            plugin.getConfig().set(path + ".identifier", identifier);
            plugin.getConfig().set(path + ".price", price);
            plugin.getConfig().set(path + ".display_material", displayMaterial.name());
            plugin.getConfig().set(path + ".delivery_method", item.getDeliveryMethod());
            plugin.getConfig().set(path + ".material", item.getMaterial().name());
            plugin.getConfig().set(path + ".amount", item.getAmount());
            if (requiredPermission != null && !requiredPermission.isEmpty()) {
                plugin.getConfig().set(path + ".required_permission", requiredPermission);
            }
            plugin.saveConfig();
        }
    }

    // ------------------------------------------------------------
    // LOAD FROM CONFIG
    // ------------------------------------------------------------
    private void loadFromConfig() {
        if (!plugin.getConfig().isConfigurationSection("special_items"))
            return;

        for (String id : plugin.getConfig().getConfigurationSection("special_items").getKeys(false)) {
            String basePath = "special_items." + id + ".";
            String type = plugin.getConfig().getString(basePath + "type");
            String name = plugin.getConfig().getString(basePath + "name");
            double price = plugin.getConfig().getDouble(basePath + "price");

            String materialName = plugin.getConfig().getString(basePath + "display_material");
            Material displayMaterial = Material.CHEST;
            if (materialName != null) {
                try {
                    displayMaterial = Material.valueOf(materialName);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid display_material '" + materialName + "' for " + id + ", using CHEST");
                }
            }

            // Load required permission (optional)
            String requiredPermission = plugin.getConfig().getString(basePath + "required_permission");

            if (type == null) continue;

            switch (type.toLowerCase()) {
                case "perm" -> {
                    String permission = plugin.getConfig().getString(basePath + "permission");
                    addPermissionItem(id, name, price, permission, displayMaterial, requiredPermission, false);
                }
                case "server-shop" -> {
                    String identifier = plugin.getConfig().getString(basePath + "identifier");
                    addServerShopItem(id, name, price, identifier, displayMaterial, requiredPermission, false);
                }
            }
        }
    }

    // ------------------------------------------------------------
    // GETTERS
    // ------------------------------------------------------------
    public Map<String, SpecialShopItem> getAllSpecialItems() {
        return Collections.unmodifiableMap(registry);
    }

    public SpecialShopItem getSpecialItem(String id) {
        return registry.get(id);
    }

    // ------------------------------------------------------------
    // PURCHASE HANDLING
    // ------------------------------------------------------------
    public void purchase(Player p, SpecialShopItem item) {
        // Check required permission first
        if (item.hasRequiredPermission()) {
            if (!p.hasPermission(item.getRequiredPermission())) {
                p.sendMessage("§c✗ §7You need permission §e" + item.getRequiredPermission() + " §7to purchase this!");
                return;
            }
        }

        ItemCategory category = item.getCategory();

        switch (category) {
            case PERMISSIONS -> {
                String perm = item.getPermission();
                if (perm == null) {
                    p.sendMessage(plugin.getMessageManager().specialPermissionFailed());
                    return;
                }

                if (plugin.getPermissionsManager().hasPermission(p, perm)) {
                    p.sendMessage(plugin.getMessageManager().specialPermissionAlreadyOwned());
                    return;
                }

                double price = item.getPrice();
                if (!plugin.getEconomyManager().charge(p, price)) {
                    String formatted = plugin.getEconomyManager().format(price);
                    p.sendMessage(plugin.getMessageManager().notEnoughMoney(formatted));
                    return;
                }

                boolean success = plugin.getPermissionsManager().grantPermission(p, perm);

                if (success) {
                    p.sendMessage(plugin.getMessageManager().specialPermissionSuccess(perm));
                    Transaction tx = Transaction.now(p.getName(), Transaction.TransactionType.BUY,
                            "PERMISSION:" + perm, 1, price, "PERMISSIONS", "permission=" + perm);
                    plugin.getTransactionLogger().log(tx);
                } else {
                    plugin.getEconomyManager().deposit(p, price);
                    p.sendMessage(plugin.getMessageManager().specialPermissionFailed());
                }
            }

            case SERVER_SHOP -> {
                String identifier = item.getItemIdentifier();
                if (identifier == null) {
                    p.sendMessage(plugin.getMessageManager().specialServerItemFailed());
                    return;
                }

                double price = item.getPrice();
                if (!plugin.getEconomyManager().charge(p, price)) {
                    String formatted = plugin.getEconomyManager().format(price);
                    p.sendMessage(plugin.getMessageManager().notEnoughMoney(formatted));
                    return;
                }

                boolean success = giveServerShopItem(p, item);

                if (success) {
                    p.sendMessage(plugin.getMessageManager().specialServerItemSuccess(identifier));
                    Transaction tx = Transaction.now(p.getName(), Transaction.TransactionType.BUY,
                            "SERVER_SHOP:" + identifier, 1, price, "SERVER_SHOP", "identifier=" + identifier);
                    plugin.getTransactionLogger().log(tx);
                } else {
                    plugin.getEconomyManager().deposit(p, price);
                    p.sendMessage(plugin.getMessageManager().specialServerItemFailed());
                }
            }

            default -> p.sendMessage(plugin.getMessageManager().getMessage("unknown-special-item"));
        }
    }

    // ------------------------------------------------------------
    // GIVE SERVER-SHOP ITEM (1.21+ Compatible)
    // ------------------------------------------------------------
    private boolean giveServerShopItem(Player player, SpecialShopItem item) {
        try {
            String deliveryMethod = item.getDeliveryMethod();
            if (deliveryMethod == null || deliveryMethod.isEmpty()) {
                deliveryMethod = "item";
            }

            // 1. NORMAL MATERIAL ITEM
            if (deliveryMethod.equalsIgnoreCase("item")) {
                Material m = item.getMaterial();
                if (m == null) {
                    plugin.getLogger().warning("[DynamicShop] Special item " + item.getId() +
                            " has no material for 'item' delivery.");
                    return false;
                }

                int amount = Math.max(1, item.getAmount());
                ItemStack stack = new ItemStack(m, amount);
                player.getInventory().addItem(stack);
                return true;
            }

            // 2. PERMISSION DELIVERY
            if (deliveryMethod.equalsIgnoreCase("permission")) {
                String perm = item.getPermission();
                if (perm != null && !perm.isEmpty()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "lp user " + player.getName() + " permission set " + perm + " true");
                    return true;
                }
                return false;
            }

            // 3. COMMAND DELIVERY
            if (deliveryMethod.equalsIgnoreCase("command")) {
                String command = item.getNbt(); // Reuse NBT field for command
                if (command == null || command.isEmpty()) {
                    plugin.getLogger().warning("[DynamicShop] Special item " + item.getId() +
                            " has no command for 'command' delivery.");
                    return false;
                }

                // Replace {player} placeholder
                command = command.replace("{player}", player.getName());

                plugin.getLogger().info("[DynamicShop] Executing command: /" + command);

                try {
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    if (!success) {
                        plugin.getLogger().warning("[DynamicShop] Command returned false: " + command);
                    }
                    return success;
                } catch (Exception e) {
                    plugin.getLogger().severe("[DynamicShop] Error executing command: " + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }

            // 4. NBT/COMPONENT DELIVERY (Minecraft 1.21+)
            if (deliveryMethod.equalsIgnoreCase("nbt") || deliveryMethod.equalsIgnoreCase("component")) {
                Material m = item.getMaterial();
                if (m == null) {
                    plugin.getLogger().warning("[DynamicShop] Special item " + item.getId() +
                            " has no material for component delivery.");
                    return false;
                }

                String componentData = item.getNbt(); // Using NBT field for component data
                if (componentData == null) componentData = "";
                componentData = componentData.trim();

                // Minecraft 1.21+ syntax: /give player item[component=value]
                String material = "minecraft:" + m.name().toLowerCase();
                String cmd;

                if (!componentData.isEmpty()) {
                    // Component data exists - use bracket syntax
                    // Remove outer braces if present (we'll add them back)
                    if (componentData.startsWith("{") && componentData.endsWith("}")) {
                        componentData = componentData.substring(1, componentData.length() - 1);
                    }

                    // For spawners, convert old NBT format to 1.21 component format
                    if (m == Material.SPAWNER && componentData.contains("BlockEntityTag")) {
                        // Old format: {BlockEntityTag:{SpawnData:{id:"minecraft:pig"}}}
                        // New format: block_entity_data={id:"minecraft:mob_spawner",SpawnData:{entity:{id:"minecraft:pig"}}}

                        // Extract the mob ID from old format
                        if (componentData.contains("\"minecraft:")) {
                            int startIdx = componentData.indexOf("\"minecraft:") + 1;
                            int endIdx = componentData.indexOf("\"", startIdx + 1);
                            String mobId = componentData.substring(startIdx, endIdx);

                            // Build new format
                            componentData = "block_entity_data={id:\"minecraft:mob_spawner\",SpawnData:{entity:{id:\"" + mobId + "\"}}}";
                        }
                    } else if (m == Material.SPAWNER && !componentData.contains("block_entity_data")) {
                        // Assume it's just the mob type like "pig"
                        // Already in correct format, just wrap it
                        if (!componentData.startsWith("block_entity_data=")) {
                            componentData = "block_entity_data={id:\"minecraft:mob_spawner\",SpawnData:{entity:{id:\"minecraft:" + componentData + "\"}}}";
                        }
                    }

                    cmd = "minecraft:give " + player.getName() + " " + material + "[" + componentData + "]";
                } else {
                    // No component data, just give regular item
                    cmd = "minecraft:give " + player.getName() + " " + material;
                }

                plugin.getLogger().info("[DynamicShop] Executing: /" + cmd);

                try {
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    if (!success) {
                        plugin.getLogger().warning("[DynamicShop] Give command returned false");
                        // Fallback: give regular item
                        ItemStack stack = new ItemStack(m, 1);
                        player.getInventory().addItem(stack);
                        player.sendMessage("§cWarning: Special item data could not be applied");
                    }
                    return true;
                } catch (Exception e) {
                    plugin.getLogger().severe("[DynamicShop] Error executing give command: " + e.getMessage());
                    e.printStackTrace();
                    // Fallback: give regular item
                    ItemStack stack = new ItemStack(m, 1);
                    player.getInventory().addItem(stack);
                    player.sendMessage("§cWarning: Special item data could not be applied");
                    return true;
                }
            }

            plugin.getLogger().warning("Unknown delivery method '" + deliveryMethod +
                    "' for special item " + item.getId());
            return false;

        } catch (Exception e) {
            plugin.getLogger().severe("Error giving server shop item to " + player.getName() +
                    " (id=" + item.getId() + ")");
            e.printStackTrace();
            return false;
        }
    }
}