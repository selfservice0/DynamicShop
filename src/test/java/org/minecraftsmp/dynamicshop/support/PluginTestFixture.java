package org.minecraftsmp.dynamicshop.support;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import org.objenesis.ObjenesisStd;

/** Provides only the Bukkit-owned plugin fields needed by isolated transaction tests. */
public final class PluginTestFixture {
    private PluginTestFixture() {}

    public static DynamicShop plugin(File directory) throws Exception {
        DynamicShop plugin = new ObjenesisStd().newInstance(DynamicShop.class);
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        set(plugin, JavaPlugin.class, "logger", logger);
        set(plugin, JavaPlugin.class, "newConfig", new YamlConfiguration());
        set(plugin, JavaPlugin.class, "dataFolder", directory);
        set(plugin, JavaPlugin.class, "configFile", new File(directory, "config.yml"));
        MessageManager messages = new MessageManager(plugin);
        set(messages, MessageManager.class, "messagesConfig", new YamlConfiguration());
        set(messages, MessageManager.class, "prefix", "");
        set(plugin, DynamicShop.class, "messageManager", messages);
        return plugin;
    }

    public static void set(Object object, Class<?> owner, String name, Object value)
            throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    public static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (object, method, args) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                return switch (method.getName()) {
                                    case "hashCode" -> System.identityHashCode(object);
                                    case "equals" -> object == args[0];
                                    default -> "Test " + type.getSimpleName();
                                };
                            }
                            return handler.invoke(object, method, args);
                        }));
    }
}
