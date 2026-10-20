package org.minecraftsmp.dynamicshop.managers;

import com.nexomc.nexo.api.NexoItems;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class NexoWrapper {

    // Lazy-cached MiniMessage instance with Nexo resolvers.
    // Built on first successful access, invalidated if Nexo isn't ready yet.
    private static volatile MiniMessage nexoMiniMessage;
    private static volatile boolean resolverInitFailed = false;

    public static boolean isValid(String id) {
        try {
            return NexoItems.itemFromId(id) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    public static ItemStack getItem(String id) {
        try {
            var builder = NexoItems.itemFromId(id);
            if (builder != null) {
                ItemStack item = builder.build();
                return item == null ? null : item.clone();
            }
        } catch (Throwable e) {
            org.bukkit.Bukkit.getLogger().warning("[DynamicShop/Nexo] Failed to get Nexo item '" + id + "': " + e.getMessage());
        }
        return null;
    }

    public static String getCustomItemId(ItemStack stack) {
        try {
            return NexoItems.idFromItem(stack);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Build (or return cached) MiniMessage instance with Nexo's GlyphTag and ShiftTag resolvers.
     * Returns null if Nexo's resolvers aren't available yet.
     */
    private static MiniMessage getNexoMiniMessage() {
        // Fast path — already cached
        if (nexoMiniMessage != null) return nexoMiniMessage;

        // Don't keep retrying every call if it failed once this tick;
        // the flag is cleared on successful build or after a reload.
        // Actually, we DO want to retry because Nexo may load late.
        try {
            TagResolver glyphResolver = com.nexomc.nexo.glyphs.GlyphTag.INSTANCE.getRESOLVER();
            TagResolver shiftResolver = com.nexomc.nexo.glyphs.ShiftTag.INSTANCE.getRESOLVER();

            if (glyphResolver == null || shiftResolver == null) {
                org.bukkit.Bukkit.getLogger().warning("[DynamicShop/Nexo] GlyphTag/ShiftTag resolver returned null — Nexo may still be loading.");
                return null;
            }

            nexoMiniMessage = MiniMessage.builder()
                .tags(TagResolver.resolver(
                    TagResolver.standard(),
                    glyphResolver,
                    shiftResolver
                ))
                .build();

            resolverInitFailed = false;
            return nexoMiniMessage;
        } catch (Throwable e) {
            if (!resolverInitFailed) {
                org.bukkit.Bukkit.getLogger().warning("[DynamicShop/Nexo] Cannot initialize Nexo GlyphTag/ShiftTag resolvers: " + e.getMessage());
                org.bukkit.Bukkit.getLogger().warning("[DynamicShop/Nexo] Glyph-based GUI titles may not render correctly until Nexo finishes loading.");
                resolverInitFailed = true;
            }
            return null;
        }
    }

    /**
     * Force re-initialization of the cached MiniMessage instance.
     * Call this after Nexo finishes loading (e.g. on a delayed task).
     */
    public static void invalidateCache() {
        nexoMiniMessage = null;
        resolverInitFailed = false;
    }

    /**
     * Parse a MiniMessage string using Nexo's GlyphTag and ShiftTag resolvers directly.
     * Bypasses all permission checks — glyphs always render.
     */
    public static Component parseMiniMessage(String text) {
        try {
            MiniMessage mm = getNexoMiniMessage();
            if (mm != null) {
                return mm.deserialize(text);
            }
        } catch (Throwable e) {
            org.bukkit.Bukkit.getLogger().warning("[DynamicShop/Nexo] Failed to parse MiniMessage with Nexo resolvers: " + e.getMessage());
        }
        // Fallback: strip glyph/shift tags and use vanilla MiniMessage
        // This at least renders the text portion correctly
        return null;
    }

    // Overload kept for API compat — player param is ignored (no permission checks)
    public static Component parseMiniMessage(String text, org.bukkit.entity.Player player) {
        return parseMiniMessage(text);
    }

    /**
     * Get a Glyph Component by glyph ID.
     */
    public static Component getGlyphComponent(String glyphId) {
        try {
            com.nexomc.nexo.NexoPlugin plugin = com.nexomc.nexo.NexoPlugin.instance();
            com.nexomc.nexo.glyphs.Glyph glyph = plugin.fontManager().glyphFromID(glyphId);
            if (glyph != null) {
                return glyph.glyphComponent();
            }
        } catch (Throwable e) {
            // Nexo not loaded or glyph not found
        }
        return null;
    }
}
