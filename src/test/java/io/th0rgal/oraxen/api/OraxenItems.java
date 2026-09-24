package io.th0rgal.oraxen.api;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.minecraftsmp.dynamicshop.support.PlainTestStack;

/** Test-only stand-in for the optional Oraxen API; never included in the plugin JAR. */
public final class OraxenItems {
    public static final ItemStack TEMPLATE = new PlainTestStack(Material.BRICK, 1);
    public static Builder getItemById(String id) {
        return id.equals("shop_food") ? new Builder() : null;
    }
    public static String getIdByItem(ItemStack stack) {
        return stack.getType() == Material.BRICK ? "shop_food" : null;
    }
    public static final class Builder {
        public ItemStack build() { return TEMPLATE; }
    }
}
