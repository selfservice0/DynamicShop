package org.minecraftsmp.dynamicshop.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Keeps Paper's Adventure conveniences optional at runtime while retaining
 * Bukkit/Spigot fallbacks for the same user-facing behavior.
 */
public final class PaperCompat {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final Method CREATE_COMPONENT_INVENTORY = findMethod(
            Bukkit.class, "createInventory", InventoryHolder.class, int.class, Component.class);
    private static final Method SET_DISPLAY_NAME = findMethod(ItemMeta.class, "displayName", Component.class);
    private static final Method GET_DISPLAY_NAME = findMethod(ItemMeta.class, "displayName");
    private static final Method SET_ITEM_NAME = findMethod(ItemMeta.class, "itemName", Component.class);
    private static final Method SET_LORE = findMethod(ItemMeta.class, "lore", List.class);
    private static final Method GET_LORE = findMethod(ItemMeta.class, "lore");
    private static final Method SEND_COMPONENT = findMethod(CommandSender.class, "sendMessage", Component.class);
    private static final Method SEND_ACTION_BAR = findMethod(Player.class, "sendActionBar", Component.class);
    private static final Method GET_VIRTUAL_HOST = findMethod(Player.class, "getVirtualHost");

    private PaperCompat() {
    }

    public static Inventory createInventory(InventoryHolder holder, int size, Component title) {
        if (CREATE_COMPONENT_INVENTORY != null) {
            try {
                return (Inventory) CREATE_COMPONENT_INVENTORY.invoke(null, holder, size, title);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Fall through to the Spigot title overload.
            }
        }
        return Bukkit.createInventory(holder, size, toLegacy(title));
    }

    public static void setDisplayName(ItemMeta meta, Component name) {
        if (SET_DISPLAY_NAME != null) {
            try {
                SET_DISPLAY_NAME.invoke(meta, name);
                return;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Fall through to the Spigot metadata API.
            }
        }
        meta.setDisplayName(toLegacy(name));
    }

    public static Component getDisplayName(ItemMeta meta) {
        if (GET_DISPLAY_NAME != null) {
            try {
                Object value = GET_DISPLAY_NAME.invoke(meta);
                if (value instanceof Component component) {
                    return component;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Fall through to the Spigot metadata API.
            }
        }
        return LEGACY.deserialize(meta.hasDisplayName() ? meta.getDisplayName() : "");
    }

    public static String getPlainDisplayName(ItemMeta meta) {
        return PLAIN.serialize(getDisplayName(meta));
    }

    public static void setItemName(ItemMeta meta, Component name) {
        if (SET_ITEM_NAME != null) {
            try {
                SET_ITEM_NAME.invoke(meta, name);
                return;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Fall through to the Spigot metadata API.
            }
        }
        meta.setItemName(toLegacy(name));
    }

    public static void setLore(ItemMeta meta, List<Component> lore) {
        if (SET_LORE != null) {
            try {
                SET_LORE.invoke(meta, lore);
                return;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Fall through to the Spigot metadata API.
            }
        }
        meta.setLore(lore == null ? null : lore.stream().map(PaperCompat::toLegacy).toList());
    }

    @SuppressWarnings("unchecked")
    public static List<Component> getLore(ItemMeta meta) {
        if (!meta.hasLore()) {
            return Collections.emptyList();
        }
        if (GET_LORE != null) {
            try {
                Object value = GET_LORE.invoke(meta);
                if (value instanceof List<?>) {
                    return new ArrayList<>((List<Component>) value);
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Fall through to the Spigot metadata API.
            }
        }
        List<String> lore = meta.getLore();
        if (lore == null) {
            return Collections.emptyList();
        }
        return lore.stream().map(value -> (Component) LEGACY.deserialize(value)).toList();
    }

    public static void sendMessage(CommandSender sender, Component message) {
        if (SEND_COMPONENT != null) {
            try {
                SEND_COMPONENT.invoke(sender, message);
                return;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Fall through to the Spigot message API.
            }
        }
        sender.sendMessage(toLegacy(message));
    }

    public static void sendActionBar(Player player, Component message) {
        if (SEND_ACTION_BAR != null) {
            try {
                SEND_ACTION_BAR.invoke(player, message);
                return;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Fall through to Spigot's Bungee chat bridge.
            }
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(toLegacy(message)));
    }

    public static String getVirtualHost(Player player) {
        if (player == null || GET_VIRTUAL_HOST == null) {
            return null;
        }
        try {
            Object value = GET_VIRTUAL_HOST.invoke(player);
            if (value instanceof InetSocketAddress address) {
                return address.getHostString();
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Fall through to caller's configured/server-ip fallback.
        }
        return null;
    }

    private static String toLegacy(Component component) {
        return LEGACY.serialize(component == null ? Component.empty() : component);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException | LinkageError ignored) {
            return null;
        }
    }
}
