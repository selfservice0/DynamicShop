package org.minecraftsmp.dynamicshop.managers;

import java.lang.reflect.Method;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Optional Oraxen API bridge. No Oraxen classes are loaded unless it is enabled. */
public final class OraxenWrapper {
    private static Plugin boundPlugin;
    private static ItemApi itemApi;
    private static boolean warned;

    private OraxenWrapper() {}

    private static Plugin plugin() {
        if (Bukkit.getServer() == null) return null;
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Oraxen");
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    private static synchronized ItemApi api() throws ReflectiveOperationException {
        Plugin plugin = plugin();
        if (plugin == null) return null;
        if (plugin != boundPlugin || itemApi == null) {
            itemApi = new ItemApi(Class.forName("io.th0rgal.oraxen.api.OraxenItems", true,
                    plugin.getClass().getClassLoader()));
            boundPlugin = plugin;
            warned = false;
        }
        return itemApi;
    }

    public static ItemStack getItem(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            ItemApi api = api();
            return api == null ? null : api.getItem(id);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warn(e);
            return null;
        }
    }

    public static boolean isValid(String id) {
        if (id == null || id.isBlank()) return false;
        try {
            ItemApi api = api();
            return api != null && api.isValid(id);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warn(e);
            return false;
        }
    }

    public static String getCustomItemId(ItemStack stack) {
        if (stack == null) return null;
        try {
            ItemApi api = api();
            return api == null ? null : api.getId(stack);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warn(e);
            return null;
        }
    }

    public static Component parseMiniMessage(String text) {
        Plugin plugin = plugin();
        if (plugin == null) return null;
        try {
            ClassLoader loader = plugin.getClass().getClassLoader();
            // Use Oraxen's native resolvers, including its glyph fonts, colors and spacing.
            // Server-authored GUI titles use the non-player resolver, like the Nexo bridge.
            return MiniMessage.builder().tags(TagResolver.resolver(TagResolver.standard(),
                    resolver(loader, "GlyphTag"), resolver(loader, "ShiftTag")))
                    .build().deserialize(text);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warn(e);
            return null;
        }
    }

    private static TagResolver resolver(ClassLoader loader, String name) throws ReflectiveOperationException {
        Class<?> type;
        try {
            type = Class.forName("io.th0rgal.oraxen.glyphs." + name, true, loader);
        } catch (ClassNotFoundException oldVersion) {
            type = Class.forName("io.th0rgal.oraxen.fonts." + name, true, loader);
        }
        return (TagResolver) type.getField("RESOLVER").get(null);
    }

    private static void warn(Throwable error) {
        if (!warned && Bukkit.getServer() != null) {
            warned = true;
            Bukkit.getLogger().warning("[DynamicShop/Oraxen] Could not access the Oraxen API: "
                    + error + ". Check the Oraxen version and startup errors.");
        }
    }

    /** Bind both the current API and the names used by older Oraxen releases. */
    static final class ItemApi {
        private final Method item;
        private final Method id;
        private final Method build;

        ItemApi(Class<?> api) throws ReflectiveOperationException {
            item = method(api, "getItemById", "itemFromId", String.class);
            id = method(api, "getIdByItem", "idFromItem", ItemStack.class);
            build = item.getReturnType().getMethod("build");
        }

        private static Method method(Class<?> type, String current, String legacy, Class<?> argument)
                throws NoSuchMethodException {
            try {
                return type.getMethod(current, argument);
            } catch (NoSuchMethodException oldVersion) {
                return type.getMethod(legacy, argument);
            }
        }

        ItemStack getItem(String name) throws ReflectiveOperationException {
            Object builder = item.invoke(null, name);
            ItemStack stack = builder == null ? null : (ItemStack) build.invoke(builder);
            return stack == null ? null : stack.clone();
        }

        boolean isValid(String name) throws ReflectiveOperationException {
            return item.invoke(null, name) != null;
        }

        String getId(ItemStack stack) throws ReflectiveOperationException {
            return (String) id.invoke(null, stack);
        }
    }
}
