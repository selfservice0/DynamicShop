package org.minecraftsmp.dynamicshop.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraftsmp.dynamicshop.DynamicShop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

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

        createMessageFiles();

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        mergeMessageDefaults();

        // Load prefix
        prefix = messagesConfig.getString("messages.prefix", "&6&lDynamicShop &7» ");
    }

    // ------------------------------------------------------------
    // RELOAD
    // ------------------------------------------------------------
    public void reload() {
        loadMessages();
    }

    /** Configuration signal only; it cannot verify whether clients loaded a resource pack. */
    public boolean hasCustomGuiTitles() {
        for (String key :
                new String[] {
                    "gui-category-title",
                    "shop-gui-title",
                    "admin-shop-gui-title",
                    "item-action-title",
                    "player-shop-browser-title",
                    "player-shop-view-title",
                    "dialog-title"
                }) {
            String title = messagesConfig.getString("messages." + key, "");
            if (title.matches("(?is).*<(?:glyph|g|shift|s|font):[^>]+>.*")
                    || title.codePoints()
                            .anyMatch(
                                    code ->
                                            Character.getType(code) == Character.PRIVATE_USE
                                                    || (code >= 0xA413 && code <= 0xA418)))
                return true;
        }
        return false;
    }

    // ------------------------------------------------------------
    // GET MESSAGE (with optional placeholders)
    // ------------------------------------------------------------
    public String getMessage(String key) {
        return getMessage(key, new HashMap<>());
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String message = messagesConfig.getString("messages." + key, "&cMessage not found: " + key);

        // If message is empty, return null to indicate it should be skipped
        if (message.isEmpty()) {
            return null;
        }

        // Replace placeholders
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        // Apply color codes
        message = message.replace('&', '§');

        return message;
    }

    /**
     * Parses a string into a Component.
     * Glyph and shift tags use the selected Nexo or Oraxen provider's native resolvers.
     * Standard MiniMessage tags work without a custom item provider.
     * Legacy-only strings retain the legacy serializer's formatting behavior.
     */
    public static Component parseComponent(String text) {
        return parseComponent(text, null);
    }

    /**
     * Parses a string into a Component using the configured GUI provider.
     */
    public static Component parseComponent(String text, org.bukkit.entity.Player player) {
        if (text == null) return Component.empty();
        if (org.minecraftsmp.dynamicshop.util.BedrockUtil.isBedrock(player)) {
            // Strip pack-only markup before a provider turns it into Unicode glyphs.
            text = text.replaceAll("(?i)</?(?:glyph|g|shift|s|font)(?::[^>]*)?>", "");
            // Older message packs used raw characters instead of named glyph tags.
            StringBuilder readable = new StringBuilder();
            text.codePoints()
                    .filter(
                            code ->
                                    Character.getType(code) != Character.PRIVATE_USE
                                            && (code < 0xA413 || code > 0xA418))
                    .forEach(readable::appendCodePoint);
            text = readable.toString();
        }

        // Check if the text contains custom font tags.
        if (text.contains("<glyph:")
                || text.contains("<shift:")
                || text.contains("<g:")
                || text.contains("<s:")) {
            // Convert legacy color codes (§ and &) to MiniMessage format for compatibility
            String mmText = text.replace('§', '&');
            // Convert &X color codes to MiniMessage <color> tags
            mmText = convertLegacyToMiniMessage(mmText);
            // Providers remain optional; unavailable APIs fall back to a plain title.
            try {
                Component result = CustomItemSupport.parseMiniMessage(mmText, player);
                if (result != null) return result;
            } catch (Throwable ignored) {
            }
            // Strip custom font tags when no provider can resolve them.
            String stripped = mmText.replaceAll("<(?:glyph|g|shift|s):[^>]*>", "");
            org.bukkit.Bukkit.getLogger()
                    .warning(
                            "[DynamicShop] GUI glyph tags could not be resolved. Check gui.custom_item_provider and the selected plugin's glyph files. Text: "
                                    + text);
            return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(stripped);
        }

        // Dialog labels and other messages can contain standard tags without any glyphs.
        // Let MiniMessage recognize its own tags; unknown command arguments such as
        // <price> alone should continue through the legacy path unchanged.
        MiniMessage miniMessage = MiniMessage.miniMessage();
        if (text.indexOf('<') >= 0 && !miniMessage.stripTags(text).equals(text)) {
            return miniMessage.deserialize(convertLegacyToMiniMessage(text.replace('§', '&')));
        }

        // Keep existing legacy messages and raw Unicode glyphs compatible.
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text.replace('§', '&'));
    }

    /**
     * Convert legacy & color codes to MiniMessage format.
     * e.g. "&f" -> "<white>", "&0&l" -> "<black><bold>"
     */
    private static String convertLegacyToMiniMessage(String text) {
        // Replace formatting codes first (order matters - do these before colors)
        text = text.replace("&l", "<bold>").replace("&L", "<bold>");
        text = text.replace("&o", "<italic>").replace("&O", "<italic>");
        text = text.replace("&n", "<underlined>").replace("&N", "<underlined>");
        text = text.replace("&m", "<strikethrough>").replace("&M", "<strikethrough>");
        text = text.replace("&k", "<obfuscated>").replace("&K", "<obfuscated>");
        text = text.replace("&r", "<reset>").replace("&R", "<reset>");

        // Replace color codes
        text = text.replace("&0", "<black>").replace("&1", "<dark_blue>");
        text = text.replace("&2", "<dark_green>").replace("&3", "<dark_aqua>");
        text = text.replace("&4", "<dark_red>").replace("&5", "<dark_purple>");
        text = text.replace("&6", "<gold>").replace("&7", "<gray>");
        text = text.replace("&8", "<dark_gray>").replace("&9", "<blue>");
        text = text.replace("&a", "<green>").replace("&A", "<green>");
        text = text.replace("&b", "<aqua>").replace("&B", "<aqua>");
        text = text.replace("&c", "<red>").replace("&C", "<red>");
        text = text.replace("&d", "<light_purple>").replace("&D", "<light_purple>");
        text = text.replace("&e", "<yellow>").replace("&E", "<yellow>");
        text = text.replace("&f", "<white>").replace("&F", "<white>");

        return text;
    }

    /**
     * Helper method to add a lore line only if the message is not disabled (null).
     *
     * @param lore    The lore list to add to
     * @param message The message (can be null if disabled)
     */
    public static void addLoreIfNotEmpty(java.util.List<String> lore, String message) {
        if (message != null) {
            lore.add(message);
        }
    }

    // ------------------------------------------------------------
    // GET MESSAGE WITH PREFIX
    // ------------------------------------------------------------
    public String getMessageWithPrefix(String key) {
        return getMessageWithPrefix(key, new HashMap<>());
    }

    public String getMessageWithPrefix(String key, Map<String, String> placeholders) {
        String message = getMessage(key, placeholders);
        if (message == null) {
            return null; // Message is disabled
        }
        String prefixColored = prefix.replace('&', '§');
        return prefixColored + message;
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

    public String cannotSellDamaged() {
        return getMessageWithPrefix("cannot-sell-damaged");
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

    public String categoryLoreItems(int count) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("count", String.valueOf(count));
        return getMessage("category-lore-items", placeholders);
    }

    public String categoryLoreClickToBrowse() {
        return getMessage("category-lore-click-to-browse");
    }

    public String categoryLoreNoItems() {
        return getMessage("category-lore-no-items");
    }

    public String inventoryFull() {
        return getMessageWithPrefix("inventory-full");
    }

    private void createMessageFiles() {
        // Create messages.yml if it doesn't exist
        if (!messagesFile.exists()) {
            // Existing messages are preserved; only fresh installs use a provider template.
            String provider = CustomItemSupport.guiProvider();
            if (!provider.equals("none")) {
                try {
                    // Save the selected provider's example as messages.yml.
                    InputStream nexoStream =
                            plugin.getResource("messages_" + provider + "_example.yml");
                    if (nexoStream != null) {
                        java.nio.file.Files.copy(nexoStream, messagesFile.toPath());
                        nexoStream.close();
                        plugin.getLogger()
                                .info(
                                        provider
                                                + " detected! Generated messages.yml with glyph support.");
                    } else {
                        plugin.saveResource("messages.yml", false);
                    }
                } catch (Exception e) {
                    plugin.getLogger()
                            .warning(
                                    "Failed to generate custom GUI messages template, using default.");
                    plugin.saveResource("messages.yml", false);
                }
            } else {
                plugin.saveResource("messages.yml", false);
            }
        }

        // Also generate messages_nexo_example.yml template as a reference if it doesn't exist
        File nexoFile = new File(plugin.getDataFolder(), "messages_nexo_example.yml");
        if (!nexoFile.exists()) {
            try {
                plugin.saveResource("messages_nexo_example.yml", false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger()
                        .warning("Could not generate messages_nexo_example.yml template.");
            }
        }

        if (!new File(plugin.getDataFolder(), "messages_oraxen_example.yml").exists()) {
            plugin.saveResource("messages_oraxen_example.yml", false);
        }
    }

    private void mergeMessageDefaults() {
        // Load defaults from jar and merge missing keys
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig =
                    YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            messagesConfig.setDefaults(defaultConfig);

            // Collect missing keys and their default values
            java.util.List<String[]> missing = new java.util.ArrayList<>();
            for (String key : defaultConfig.getKeys(true)) {
                if (!messagesConfig.isSet(key)) {
                    Object val = defaultConfig.get(key);
                    if (val instanceof String) {
                        messagesConfig.set(key, val); // Add to in-memory config
                        missing.add(new String[] {key, (String) val});
                        plugin.getLogger().info("[Messages] Added missing key: " + key);
                    }
                }
            }

            // Append missing keys to the file WITHOUT re-serializing the whole thing.
            // This prevents Bukkit's SnakeYAML from corrupting MiniMessage tags like
            // <glyph:...> and <shift:...> which get mangled when the file is re-saved.
            if (!missing.isEmpty()) {
                try (java.io.FileWriter fw = new java.io.FileWriter(messagesFile, true)) {
                    fw.write("\n  # --- Auto-added missing keys ---\n");
                    for (String[] entry : missing) {
                        // entry[0] = "messages.key-name", entry[1] = "value"
                        String shortKey =
                                entry[0].startsWith("messages.")
                                        ? entry[0].substring("messages.".length())
                                        : entry[0];
                        // Escape the value for YAML (wrap in double quotes)
                        String escaped = entry[1].replace("\\", "\\\\").replace("\"", "\\\"");
                        fw.write("  " + shortKey + ": \"" + escaped + "\"\n");
                    }
                    plugin.getLogger()
                            .info(
                                    "[Messages] Appended "
                                            + missing.size()
                                            + " missing keys to messages.yml (safe append, no reformatting).");
                } catch (java.io.IOException e) {
                    plugin.getLogger()
                            .warning(
                                    "[Messages] Could not append missing keys to messages.yml: "
                                            + e.getMessage());
                }
            }
        }
    }
}
