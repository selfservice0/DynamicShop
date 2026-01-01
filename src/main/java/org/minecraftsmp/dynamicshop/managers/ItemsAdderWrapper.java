package org.minecraftsmp.dynamicshop.managers;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.inventory.ItemStack;

public class ItemsAdderWrapper {

    public static boolean isValid(String id) {
        try {
            return CustomStack.isInRegistry(id);
        } catch (Throwable e) {
            return false;
        }
    }

    public static ItemStack getItem(String id) {
        try {
            CustomStack stack = CustomStack.getInstance(id);
            if (stack != null) {
                return stack.getItemStack();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getCustomItemId(ItemStack stack) {
        try {
            CustomStack customStack = CustomStack.byItemStack(stack);
            if (customStack != null) {
                return customStack.getNamespacedID();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }
}
