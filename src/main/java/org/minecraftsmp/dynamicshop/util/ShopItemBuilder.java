package org.minecraftsmp.dynamicshop.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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

            meta.setDisplayName("§e§l" + prettify(mat.name()));

            List<String> lore = new ArrayList<>();
            lore.add("§7────────────────────");
            lore.add("§a§lBUY: §f" + formattedBuyPrice);
            
            // Only show sell price if item can be sold
            if (formattedSellPrice != null && !formattedSellPrice.equals("N/A")) {
                lore.add("§c§lSELL: §f" + formattedSellPrice);
            }
            
            lore.add("§7────────────────────");
            lore.add("§7Left-click to §aBUY");
            
            if (formattedSellPrice != null && !formattedSellPrice.equals("N/A")) {
                lore.add("§7Right-click to §cSELL");
            }

            meta.setLore(lore);
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

            meta.setDisplayName(name);

            List<String> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(line);
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    // ---------------------------------------------------------
    // FILLER PANE (DECORATION)
    // ---------------------------------------------------------
    public static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }

        return item;
    }

    // ---------------------------------------------------------
    // UTILITY – PRETTIFY MATERIAL NAMES
    // STONE_BRICKS => Stone Bricks
    // ---------------------------------------------------------
    private static String prettify(String input) {
        String[] parts = input.split("_");
        StringBuilder out = new StringBuilder();

        for (String s : parts) {
            if (s.isEmpty()) continue;

            out.append(s.substring(0, 1).toUpperCase());
            out.append(s.substring(1).toLowerCase());
            out.append(" ");
        }

        return out.toString().trim();
    }
}
