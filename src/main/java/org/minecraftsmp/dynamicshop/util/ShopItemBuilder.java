package org.minecraftsmp.dynamicshop.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import org.minecraftsmp.dynamicshop.managers.MessageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility to build standardized ItemStack displays for the
 * ProtocolLib virtual GUIs.
 *
 * - Category icons
 * - Shop items (with buy/sell price)
 * - Navigation buttons
 * - Placeholder filler panes
 */
public class ShopItemBuilder {

    // ---------------------------------------------------------
    // BUILD AN ITEM FOR THE SHOP ITEM LIST (with sell price)
    // ---------------------------------------------------------
    public static ItemStack buildShopDisplayItem(Material mat, String formattedBuyPrice, String formattedSellPrice) {
        ItemStack item = new ItemStack(mat);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {

            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, component("§e§l" + prettify(mat.name())));

            List<Component> lore = new ArrayList<>();
            lore.add(component("§7────────────────────"));
            lore.add(component("§a§lBUY: §f" + formattedBuyPrice));

            // Only show sell price if item can be sold
            if (formattedSellPrice != null && !formattedSellPrice.equals("N/A")) {
                lore.add(component("§c§lSELL: §f" + formattedSellPrice));
            }

            lore.add(component("§7────────────────────"));
            lore.add(component("§7Left-click to §aBUY"));

            if (formattedSellPrice != null && !formattedSellPrice.equals("N/A")) {
                lore.add(component("§7Right-click to §cSELL"));
            }

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    // Backward compatibility - defaults to no sell price
    public static ItemStack buildShopDisplayItem(Material mat, String formattedPrice) {
        return buildShopDisplayItem(mat, formattedPrice, null);
    }

    // ---------------------------------------------------------
    // CATEGORY / NAV ITEMS
    // ---------------------------------------------------------
    public static ItemStack navItem(String name, Material icon, String... loreLines) {
        ItemStack item = new ItemStack(icon);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {

            // Handle both & and § in name if present
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent(name));

            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(MessageManager.parseComponent(line));
            }

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Create a nav item using the configured custom-item provider, falling back to vanilla Material.
     * @param name Display name
     * @param nexoId Nexo item ID (e.g. "shop_back_button")
     * @param fallbackIcon Vanilla material to use if Nexo isn't available
     * @param loreLines Lore text lines
     */
    public static ItemStack navItemNexo(String name, String nexoId, Material fallbackIcon, String... loreLines) {
        return navItemNexo(null, name, nexoId, fallbackIcon, loreLines);
    }

    /** Uses vanilla controls for Bedrock viewers without the Java GUI resource pack. */
    public static ItemStack navItemNexo(org.bukkit.entity.Player viewer, String name, String nexoId,
                                      Material fallbackIcon, String... loreLines) {
        ItemStack item = null;

        // Method name is retained for compatibility with existing callers.
        if (nexoId != null) {
            item = org.minecraftsmp.dynamicshop.managers.CustomItemSupport.getMenuItem(nexoId, viewer);
            if (item != null) {
                item = item.clone();
            }
        }

        // Fall back to vanilla material
        if (item == null) {
            item = new ItemStack(fallbackIcon);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent(name, viewer));

            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(MessageManager.parseComponent(line, viewer));
            }

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    // ---------------------------------------------------------
    // FILLER PANE (DECORATION)
    // ---------------------------------------------------------
    public static ItemStack filler() {
        return org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.getFillerItem();
    }

    // ---------------------------------------------------------
    // UTILITY – PRETTIFY MATERIAL NAMES
    // STONE_BRICKS => Stone Bricks
    // ---------------------------------------------------------
    private static String prettify(String input) {
        String[] parts = input.split("_");
        StringBuilder out = new StringBuilder();

        for (String s : parts) {
            if (s.isEmpty())
                continue;

            out.append(s.substring(0, 1).toUpperCase());
            out.append(s.substring(1).toLowerCase());
            out.append(" ");
        }

        return out.toString().trim();
    }

    private static Component component(String text) {
        return MessageManager.parseComponent(text);
    }
}
