package org.minecraftsmp.dynamicshop.managers;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.minecraftsmp.dynamicshop.DynamicShop;

/** Routes explicit item IDs and GUI rendering without making either provider mandatory. */
public final class CustomItemSupport {
    private CustomItemSupport() {}

    public static boolean isCustomItem(String value) {
        if (value == null) return false;
        int colon = value.indexOf(':');
        if (colon < 1 || value.substring(colon + 1).isBlank()) return false;
        String provider = value.substring(0, colon).toLowerCase(Locale.ROOT);
        return provider.equals("nexo") || provider.equals("oraxen");
    }

    public static ItemStack getItem(String value) {
        if (!isCustomItem(value)) return null;
        int colon = value.indexOf(':');
        String provider = value.substring(0, colon).toLowerCase(Locale.ROOT);
        String id = value.substring(colon + 1);
        if (provider.equals("oraxen")) return OraxenWrapper.getItem(id);
        return enabled("Nexo") ? NexoWrapper.getItem(id) : null;
    }

    public static boolean enabled(String name) {
        return Bukkit.getServer() != null && Bukkit.getPluginManager().isPluginEnabled(name);
    }

    public static String getItemIdentifier(ItemStack stack) {
        if (stack == null) return null;
        if (enabled("Nexo")) {
            String id = NexoWrapper.getCustomItemId(stack);
            if (id != null) return "nexo:" + id;
        }
        String id = OraxenWrapper.getCustomItemId(stack);
        return id == null ? null : "oraxen:" + id;
    }

    public static String guiProvider() {
        DynamicShop plugin = DynamicShop.getInstance();
        String configured = plugin == null ? "auto" : plugin.getConfig().getString("gui.custom_item_provider", "auto");
        return selectProvider(configured, enabled("Nexo"), enabled("Oraxen"));
    }

    static String selectProvider(String configured, boolean nexo, boolean oraxen) {
        String provider = configured == null ? "auto" : configured.toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "nexo" -> nexo ? "nexo" : "none";
            case "oraxen" -> oraxen ? "oraxen" : "none";
            case "none" -> "none";
            default -> nexo ? "nexo" : oraxen ? "oraxen" : "none";
        };
    }

    public static ItemStack getMenuItem(String id) {
        return getMenuItem(id, null);
    }

    /** Registry lookup only: metrics must not build or clone item stacks. */
    public static boolean hasMenuItem(String id) {
        if (id == null) return false;
        String value = isCustomItem(id) ? id : guiProvider() + ":" + id;
        if (!isCustomItem(value)) return false;
        int colon = value.indexOf(':');
        String name = value.substring(colon + 1);
        return value.substring(0, colon).equalsIgnoreCase("oraxen")
                ? OraxenWrapper.isValid(name) : enabled("Nexo") && NexoWrapper.isValid(name);
    }

    public static ItemStack getMenuItem(String id, Player viewer) {
        if (org.minecraftsmp.dynamicshop.util.BedrockUtil.isBedrock(viewer)) return null;
        return id == null ? null : getItem(isCustomItem(id) ? id : guiProvider() + ":" + id);
    }

    public static Component parseMiniMessage(String text, Player player) {
        return switch (guiProvider()) {
            case "oraxen" -> OraxenWrapper.parseMiniMessage(text);
            case "nexo" -> NexoWrapper.parseMiniMessage(text, player);
            default -> null;
        };
    }
}
