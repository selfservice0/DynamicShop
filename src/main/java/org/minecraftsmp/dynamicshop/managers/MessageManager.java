package org.minecraftsmp.dynamicshop.managers;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraftsmp.dynamicshop.DynamicShop;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles loading and formatting messages from messages.yml
 * Supports color codes and placeholders
 */
public class MessageManager {

    private final DynamicShop plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private String prefix;

    public MessageManager(DynamicShop plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------
    // INITIALIZATION
    // ------------------------------------------------------------
    public void init() {
        loadMessages();
    }

    private void loadMessages() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        // Create messages.yml if it doesn't exist
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Load defaults from jar
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream)
            );
            messagesConfig.setDefaults(defaultConfig);
        }

        // Load prefix
        prefix = messagesConfig.getString("messages.prefix", "&6&lDynamicShop &7» ");
    }

    // ------------------------------------------------------------
    // RELOAD
    // ------------------------------------------------------------
    public void reload() {
        loadMessages();
    }

    // ------------------------------------------------------------
    // GET MESSAGE (with optional placeholders)
    // ------------------------------------------------------------
    public String getMessage(String key) {
        return getMessage(key, new HashMap<>());
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String message = messagesConfig.getString("messages." + key, "&cMessage not found: " + key);

        // Replace placeholders
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        // Apply color codes
        message = ChatColor.translateAlternateColorCodes('&', message);

        return message;
    }

    // ------------------------------------------------------------
    // GET MESSAGE WITH PREFIX
    // ------------------------------------------------------------
    public String getMessageWithPrefix(String key) {
        return getMessageWithPrefix(key, new HashMap<>());
    }

    public String getMessageWithPrefix(String key, Map<String, String> placeholders) {
        String prefixColored = ChatColor.translateAlternateColorCodes('&', prefix);
        return prefixColored + getMessage(key, placeholders);
    }

    // ------------------------------------------------------------
    // CONVENIENCE METHODS FOR COMMON MESSAGES
    // ------------------------------------------------------------

    public String noPermission() {
        return getMessageWithPrefix("no-permission");
    }

    public String notEnoughMoney(String price) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("price", price);
        return getMessageWithPrefix("not-enough-money", placeholders);
    }

    public String cannotSell() {
        return getMessageWithPrefix("cannot-sell");
    }

    public String categoryEmpty() {
        return getMessageWithPrefix("category-empty");
    }

    public String specialPermissionSuccess(String permission) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("permission", permission);
        return getMessageWithPrefix("special-permission-success", placeholders);
    }

    public String specialPermissionFailed() {
        return getMessageWithPrefix("special-permission-failed");
    }

    public String specialPermissionAlreadyOwned() {
        return getMessageWithPrefix("special-permission-already-owned");
    }

    public String specialServerItemSuccess(String identifier) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("identifier", identifier);
        return getMessageWithPrefix("special-server-item-success", placeholders);
    }

    public String specialServerItemFailed() {
        return getMessageWithPrefix("special-server-item-failed");
    }

    public String inventoryFull() {
        return getMessageWithPrefix("inventory-full");
    }
}