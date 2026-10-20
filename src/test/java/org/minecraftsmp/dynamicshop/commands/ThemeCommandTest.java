package org.minecraftsmp.dynamicshop.commands;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import static org.junit.Assert.*;

public class ThemeCommandTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private DynamicShop plugin;
    private DynamicShop oldPlugin;
    private Object oldServer;
    private Field serverField;
    private final List<String> messages = new ArrayList<>();
    private ShopAdminCommand command;
    private static final String ORIGINAL = "# Owner config 🪙\r\ngui:\r\n  # Theme choices\r\n"
            + "  theme: 'default' # keep this comment\r\n  shop_menu_size: 45\r\n"
            + "items:\r\n  STONE:\r\n    base_price: 42.0\r\n";

    @Before public void setUp() throws Exception {
        plugin = PluginTestFixture.plugin(directory.getRoot());
        plugin.getConfig().set("gui.theme", "default");
        Files.writeString(directory.getRoot().toPath().resolve("config.yml"), ORIGINAL);
        oldPlugin = DynamicShop.getInstance();
        PluginTestFixture.set(null, DynamicShop.class, "instance", plugin);
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        oldServer = serverField.get(null);
        PluginManager manager = PluginTestFixture.proxy(PluginManager.class, (object, method, args) -> switch (method.getName()) {
            case "isPluginEnabled" -> false;
            default -> throw new AssertionError(method.getName());
        });
        serverField.set(null, PluginTestFixture.proxy(Server.class, (object, method, args) -> switch (method.getName()) {
            case "getPluginManager" -> manager;
            default -> throw new AssertionError(method.getName());
        }));
        command = new ShopAdminCommand(plugin);
    }

    @After public void tearDown() throws Exception {
        serverField.set(null, oldServer);
        PluginTestFixture.set(null, DynamicShop.class, "instance", oldPlugin);
    }

    private CommandSender sender(boolean allowed) {
        return PluginTestFixture.proxy(CommandSender.class, (object, method, args) -> switch (method.getName()) {
            case "hasPermission" -> allowed;
            case "sendMessage" -> {
                messages.add((String) args[0]);
                yield null;
            }
            default -> throw new AssertionError(method.getName());
        });
    }

    @Test public void switchingThemesPersistsOnlyTheThemeAndSupportsAliases() throws Exception {
        for (String requested : List.of("christmas", "newyears", "new-year", "moonlit", "halloween", "valentines",
                "Valentine's Day", "easter", "St. Patrick's Day", "summer-festival", "default")) {
            assertTrue(command.onCommand(sender(true), null, "shopadmin", new String[] {"theme", requested}));
            String expected = org.minecraftsmp.dynamicshop.util.GuiTheme.normalize(requested);
            assertEquals(expected, plugin.getConfig().getString("gui.theme"));
            String actual = Files.readString(directory.getRoot().toPath().resolve("config.yml"));
            assertEquals(ORIGINAL.replace("'default'", "\"" + expected + "\""), actual);
            assertEquals(expected, YamlConfiguration.loadConfiguration(
                    directory.getRoot().toPath().resolve("config.yml").toFile()).getString("gui.theme"));
        }
    }

    @Test public void permissionInvalidArgumentsAndListingNeverChangeConfig() throws Exception {
        command.onCommand(sender(false), null, "shopadmin", new String[] {"theme", "christmas"});
        for (String[] args : new String[][] {{"theme"}, {"theme", "list"}, {"theme", "unknown"},
                {"theme", "christmas", "extra"}, {"theme", "../christmas"}}) {
            command.onCommand(sender(true), null, "shopadmin", args);
        }
        assertEquals(ORIGINAL, Files.readString(directory.getRoot().toPath().resolve("config.yml")));
        assertEquals("default", plugin.getConfig().getString("gui.theme"));
        assertTrue(messages.stream().anyMatch(message -> message.contains("newyear")));
    }

    @Test public void failedSaveDoesNotChangeTheActiveTheme() throws Exception {
        var path = directory.getRoot().toPath().resolve("config.yml");
        Files.writeString(path, "gui: [broken YAML");
        command.onCommand(sender(true), null, "shopadmin", new String[] {"theme", "christmas"});
        assertEquals("default", plugin.getConfig().getString("gui.theme"));
        assertEquals("gui: [broken YAML", Files.readString(path));
        assertTrue(messages.stream().anyMatch(message -> message.contains("Could not save")));
    }

    @Test public void completionListsAndFiltersAllAvailableThemesForAdmins() {
        assertEquals(List.of("default", "moonstone", "halloween", "christmas", "newyear", "valentines", "easter", "stpatricks", "summer", "list", "install"),
                command.onTabComplete(sender(true), null, "shopadmin", new String[] {"theme", ""}));
        assertEquals(List.of("newyear"), command.onTabComplete(sender(true), null, "shopadmin", new String[] {"theme", "ne"}));
        assertEquals(List.of("valentines"), command.onTabComplete(sender(true), null, "shopadmin", new String[] {"theme", "val"}));
        assertTrue(command.onTabComplete(sender(false), null, "shopadmin", new String[] {"theme", ""}).isEmpty());
        assertTrue(command.onTabComplete(sender(true), null, "shopadmin", new String[] {"theme", "newyear", ""}).isEmpty());
        assertEquals(List.of("force"), command.onTabComplete(sender(true), null, "shopadmin", new String[] {"theme", "install", "f"}));
        assertTrue(command.onTabComplete(sender(true), null, "shopadmin", new String[] {"theme", "install", "wrong"}).isEmpty());
        assertTrue(command.onTabComplete(sender(false), null, "shopadmin", new String[] {"theme", "install", ""}).isEmpty());
    }

    private java.nio.file.Path enableProvider(String provider) throws Exception {
        PluginTestFixture.set(plugin, JavaPlugin.class, "classLoader", getClass().getClassLoader());
        var folder = directory.getRoot().toPath().resolve(provider);
        Plugin oraxen = PluginTestFixture.proxy(Plugin.class, (object, method, args) -> switch (method.getName()) {
            case "isEnabled" -> true;
            case "getDataFolder" -> folder.toFile();
            default -> throw new AssertionError(method.getName());
        });
        PluginManager manager = PluginTestFixture.proxy(PluginManager.class, (object, method, args) -> switch (method.getName()) {
            case "getPlugin" -> args[0].equals(provider) ? oraxen : null;
            case "isPluginEnabled" -> args[0].equals(provider);
            default -> throw new AssertionError(method.getName());
        });
        serverField.set(null, PluginTestFixture.proxy(Server.class, (object, method, args) -> switch (method.getName()) {
            case "getPluginManager" -> manager;
            default -> throw new AssertionError(method.getName());
        }));
        return folder;
    }

    @Test public void installAndForceWorkWithAutoInstallationOffWithoutChangingConfiguration() throws Exception {
        var root = enableProvider("Oraxen");
        plugin.getConfig().set("gui.theme", "halloween");
        plugin.getConfig().set("gui.auto_install_oraxen_theme", false);
        plugin.getConfig().set("gui.custom_item_provider", "oraxen");
        var texture = root.resolve("pack/assets/dynamicshop/textures/dynamicshop_halloween_categories_54.png");
        Files.createDirectories(texture.getParent());
        Files.writeString(texture, "custom pixels");
        assertTrue(command.onCommand(sender(true), null, "shopadmin", new String[] {"theme", "install"}));
        assertEquals("custom pixels", Files.readString(texture));
        assertTrue(messages.stream().anyMatch(message -> message.contains("Preserved")));
        assertTrue(command.onCommand(sender(true), null, "shopadmin", new String[] {"theme", "install", "force"}));
        try (var bundle = getClass().getResourceAsStream("/gui-themes/oraxen/halloween.zip");
             var zip = new java.util.zip.ZipInputStream(bundle)) {
            boolean found = false;
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("pack/assets/dynamicshop/textures/dynamicshop_halloween_categories_54.png")) {
                    assertArrayEquals(zip.readAllBytes(), Files.readAllBytes(texture));
                    found = true;
                }
            }
            assertTrue(found);
        }
        try (var folders = Files.list(directory.getRoot().toPath().resolve("theme-installation/backups"))) {
            var backup = folders.findFirst().orElseThrow();
            assertEquals("custom pixels", Files.readString(backup.resolve(root.relativize(texture))));
        }
        assertEquals(ORIGINAL, Files.readString(directory.getRoot().toPath().resolve("config.yml")));
        assertEquals("halloween", plugin.getConfig().getString("gui.theme"));
        assertFalse(plugin.getConfig().getBoolean("gui.auto_install_oraxen_theme"));
        assertEquals("oraxen", plugin.getConfig().getString("gui.custom_item_provider"));
        assertTrue(messages.stream().anyMatch(message -> message.contains("backed up")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("/oraxen reload all")));
    }

    @Test public void installRejectsPermissionsBadArgumentsDefaultThemeAndUnavailableOraxen() throws Exception {
        var root = enableProvider("Oraxen");
        plugin.getConfig().set("gui.theme", "halloween");
        command.onCommand(sender(false), null, "shopadmin", new String[] {"theme", "install", "force"});
        for (String[] args : new String[][] {{"theme", "install", "wrong"}, {"theme", "install", "force", "extra"}}) {
            command.onCommand(sender(true), null, "shopadmin", args);
        }
        assertFalse(Files.exists(root));
        plugin.getConfig().set("gui.theme", "default");
        command.onCommand(sender(true), null, "shopadmin", new String[] {"theme", "install", "force"});
        assertFalse(Files.exists(root));
        assertTrue(messages.stream().anyMatch(message -> message.contains("Select a bundled theme first")));
        plugin.getConfig().set("gui.theme", "halloween");
        PluginManager disabled = PluginTestFixture.proxy(PluginManager.class, (object, method, args) -> switch (method.getName()) {
            case "getPlugin" -> null;
            case "isPluginEnabled" -> false;
            default -> throw new AssertionError(method.getName());
        });
        serverField.set(null, PluginTestFixture.proxy(Server.class, (object, method, args) -> switch (method.getName()) {
            case "getPluginManager" -> disabled;
            default -> throw new AssertionError(method.getName());
        }));
        command.onCommand(sender(true), null, "shopadmin", new String[] {"theme", "install", "force"});
        assertTrue(messages.stream().anyMatch(message -> message.contains("Select an enabled Nexo or Oraxen")));
        assertEquals(ORIGINAL, Files.readString(directory.getRoot().toPath().resolve("config.yml")));
    }

    @Test public void selectingNexoInstallsItsThemeAndManualForceUsesTheSameProvider() throws Exception {
        var root = enableProvider("Nexo");
        plugin.getConfig().set("gui.custom_item_provider", "nexo");
        plugin.getConfig().set("gui.auto_install_oraxen_theme", false);
        assertTrue(command.onCommand(sender(true), null, "shopadmin", new String[] {"theme", "halloween"}));
        var glyph = root.resolve("glyphs/dynamicshop_halloween.yml");
        var texture = root.resolve("pack/external_packs/dynamicshop_halloween/assets/dynamicshop_halloween/textures/"
                + "dynamicshop_halloween_categories_54.png");
        assertTrue(Files.exists(glyph));
        assertTrue(Files.exists(texture));
        assertFalse(Files.exists(directory.getRoot().toPath().resolve("Oraxen")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("/nexo reload")));
        assertFalse(messages.stream().anyMatch(message -> message.contains("/oraxen reload")));
        plugin.getConfig().set("gui.auto_install_nexo_theme", false);
        Files.writeString(texture, "owner artwork");
        messages.clear();
        command.onCommand(sender(true), null, "shopadmin", new String[] {"theme", "install"});
        assertEquals("owner artwork", Files.readString(texture));
        assertTrue(messages.stream().anyMatch(message -> message.contains("Preserved")));
        command.onCommand(sender(true), null, "shopadmin", new String[] {"theme", "install", "force"});
        assertTrue(Files.size(texture) > 1000);
        try (var backups = Files.list(directory.getRoot().toPath().resolve("theme-installation/backups"))) {
            var backup = backups.findFirst().orElseThrow();
            assertTrue(backup.getFileName().toString().startsWith("nexo-halloween-"));
            assertEquals("owner artwork", Files.readString(backup.resolve(root.relativize(texture))));
        }
        assertTrue(messages.stream().anyMatch(message -> message.contains("Nexo theme halloween")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("/nexo pack <player>")));
        assertEquals("nexo", plugin.getConfig().getString("gui.custom_item_provider"));
        assertFalse(plugin.getConfig().getBoolean("gui.auto_install_nexo_theme"));
        assertEquals(ORIGINAL.replace("'default'", "\"halloween\""),
                Files.readString(directory.getRoot().toPath().resolve("config.yml")));
    }
}
