package org.minecraftsmp.dynamicshop.commands;

import java.lang.reflect.Field;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import static org.junit.Assert.*;

public class ShopAdminCompletionTest {
    private final ShopAdminCommand command = new ShopAdminCommand(null);
    private Field serverField;
    private Object previousServer;

    @Before
    public void setUp() throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        PluginManager manager =
                PluginTestFixture.proxy(
                        PluginManager.class,
                        (o, method, args) -> {
                            if (method.getName().equals("getPlugin")) return null;
                            throw new AssertionError(method.getName());
                        });
        serverField.set(
                null,
                PluginTestFixture.proxy(
                        Server.class,
                        (o, method, args) -> {
                            if (method.getName().equals("getPluginManager")) return manager;
                            throw new AssertionError(method.getName());
                        }));
    }

    @After
    public void tearDown() throws Exception {
        serverField.set(null, previousServer);
    }

    private CommandSender sender(boolean authorized) {
        return PluginTestFixture.proxy(
                CommandSender.class,
                (o, method, args) -> {
                    if (method.getName().equals("hasPermission")) {
                        assertEquals("dynamicshop.admin", args[0]);
                        return authorized;
                    }
                    throw new AssertionError(method.getName());
                });
    }

    private List<String> complete(String... args) {
        return command.onTabComplete(sender(true), null, "shopadmin", args);
    }

    @Test
    public void unauthorizedUsersNeverReceiveAdminCompletions() {
        assertTrue(
                command.onTabComplete(sender(false), null, "shopadmin", new String[] {""})
                        .isEmpty());
        assertTrue(
                command.onTabComplete(sender(false), null, "shopadmin", new String[] {"open", ""})
                        .isEmpty());
    }

    @Test
    public void completionPreservesPriceAndValueHints() {
        assertEquals(List.of("<price>"), complete("add", "item", ""));
        assertEquals(List.of("<permission.node>"), complete("add", "perm", "1", ""));
        assertEquals(List.of("<groupname>"), complete("add", "group", "1", ""));
        assertEquals(List.of("<id>"), complete("add", "server-shop", "1", ""));
        assertEquals(List.of("<amount>"), complete("setstock", "STONE", ""));
        assertEquals(List.of("check"), complete("webupdate", "ch"));
        assertTrue(complete("webupdate", "other").isEmpty());
    }

    @Test
    public void deliverySuggestionsRespectOptionalPermissionArguments() {
        List<String> modes = List.of("requiresperm", "spawner", "command", "component");
        assertEquals(modes, complete("add", "server-shop", "1", "sample", ""));
        assertEquals(modes, complete("add", "server-shop", "1", "sample", "requiresperm"));
        assertEquals(
                List.of("spawner", "command", "component"),
                complete("add", "server-shop", "1", "sample", "requiresperm", "shop.vip", ""));
        assertTrue(complete("add", "server-shop", "1", "sample", "valhallammo", "").isEmpty());
    }
}
