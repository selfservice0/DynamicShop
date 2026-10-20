package org.minecraftsmp.dynamicshop.managers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import org.minecraftsmp.dynamicshop.util.GuiTheme;
import static org.junit.Assert.*;

public class NexoThemeInstallerTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private static final String PREFIX = "pack/external_packs/dynamicshop_halloween/assets/dynamicshop_halloween/textures/";
    private static final String PNG = PREFIX + "dynamicshop_halloween_shop_54.png";
    private static final String GLYPHS = "glyphs/dynamicshop_halloween.yml";

    @Test public void allRealNexoBundlesInstallAndResolveEveryGlyphAndItemTexture() throws Exception {
        for (String theme : GuiTheme.names()) {
            if (theme.equals("default")) continue;
            var root = directory.getRoot().toPath().resolve(theme);
            var state = directory.getRoot().toPath().resolve(theme + ".properties");
            byte[] bytes;
            try (var resource = getClass().getResourceAsStream("/gui-themes/nexo/" + theme + ".zip")) {
                assertNotNull(resource);
                bytes = resource.readAllBytes();
            }
            var installed = ThemeInstaller.install("nexo", theme, new ByteArrayInputStream(bytes), root, state);
            assertEquals("nexo", installed.provider());
            assertTrue(installed.installed() > 40);
            int count = 0;
            try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    assertArrayEquals(entry.getName(), zip.readAllBytes(), Files.readAllBytes(root.resolve(entry.getName())));
                    count++;
                }
            }
            assertEquals(count, installed.installed());
            var repeat = ThemeInstaller.install("nexo", theme, new ByteArrayInputStream(bytes), root, state);
            assertEquals(0, repeat.installed());
            assertEquals(0, repeat.updated());
            assertTrue(repeat.preserved().isEmpty());
            var glyphs = YamlConfiguration.loadConfiguration(root.resolve("glyphs/dynamicshop_" + theme + ".yml").toFile());
            assertEquals(13, glyphs.getKeys(false).size());
            var textures = root.resolve("pack/external_packs/dynamicshop_" + theme + "/assets/dynamicshop_" + theme + "/textures");
            for (String id : glyphs.getKeys(false)) {
                assertTrue(Files.exists(textures.resolve(glyphs.getString(id + ".texture").split(":")[1])));
                assertEquals("dynamicshop:themes", glyphs.getString(id + ".font"));
            }
            var items = YamlConfiguration.loadConfiguration(root.resolve("items/dynamicshop_" + theme + "_theme.yml").toFile());
            assertEquals(22, items.getKeys(false).size());
            for (String id : items.getKeys(false)) {
                assertTrue(Files.exists(textures.resolve(items.getString(id + ".Pack.texture").split(":")[1] + ".png")));
            }
            assertFalse(Files.exists(textures.resolve("dynamicshop_gui_dialog_2.png")));
            assertFalse(Files.exists(root.resolve("pack/assets")));
        }
    }

    @Test public void managedUpdatesRetainAssignedCharactersAndPreserveCustomArtwork() throws Exception {
        var root = directory.getRoot().toPath().resolve("Nexo");
        var state = directory.getRoot().toPath().resolve("state");
        String original = "glyph:\n  texture: dynamicshop_halloween:old.png\n  font: dynamicshop:themes\n";
        ThemeInstaller.install("nexo", "halloween", bundle(Map.of(PNG, "old", GLYPHS, original)), root, state);
        var assigned = YamlConfiguration.loadConfiguration(root.resolve(GLYPHS).toFile());
        assigned.set("glyph.char", "\ue502");
        assigned.save(root.resolve(GLYPHS).toFile());
        Files.writeString(root.resolve(PNG), "owner artwork");
        var report = ThemeInstaller.install("nexo", "halloween", bundle(Map.of(PNG, "new", GLYPHS,
                original.replace("old.png", "new.png"))), root, state);
        assertEquals(1, report.updated());
        assertEquals(java.util.List.of(PNG), report.preserved());
        assertEquals("owner artwork", Files.readString(root.resolve(PNG)));
        var updated = YamlConfiguration.loadConfiguration(root.resolve(GLYPHS).toFile());
        assertEquals("\ue502", updated.getString("glyph.char"));
        assertEquals("dynamicshop_halloween:new.png", updated.getString("glyph.texture"));
        assertEquals("dynamicshop:themes", updated.getString("glyph.font"));
    }

    @Test public void forceBacksUpSelectedNexoFilesBeforeReplacingAndKeepsOtherThemes() throws Exception {
        var root = directory.getRoot().toPath().resolve("Nexo");
        var state = directory.getRoot().toPath().resolve("state");
        Files.createDirectories(root.resolve(PNG).getParent());
        Files.createDirectories(root.resolve(GLYPHS).getParent());
        Files.writeString(root.resolve(PNG), "owner texture");
        Files.writeString(root.resolve(GLYPHS), "glyph:\n  texture: owner:art.png\n  char: '\ue503'\n");
        Files.writeString(root.resolve("glyphs/dynamicshop_christmas.yml"), "other theme");
        Files.writeString(root.resolve("settings.yml"), "owner settings");
        var incoming = Map.of(PNG, "new texture", GLYPHS, "glyph:\n  texture: dynamicshop_halloween:new.png\n");
        var report = ThemeInstaller.install("nexo", "halloween", bundle(incoming), root, state, true,
                directory.getRoot().toPath().resolve("backups"));
        assertEquals(2, report.updated());
        assertTrue(report.backupFolder().getFileName().toString().startsWith("nexo-halloween-"));
        assertEquals("owner texture", Files.readString(report.backupFolder().resolve(PNG)));
        assertTrue(Files.readString(report.backupFolder().resolve(GLYPHS)).contains("owner:art.png"));
        assertEquals("\ue503", YamlConfiguration.loadConfiguration(root.resolve(GLYPHS).toFile()).getString("glyph.char"));
        assertEquals("new texture", Files.readString(root.resolve(PNG)));
        assertEquals("other theme", Files.readString(root.resolve("glyphs/dynamicshop_christmas.yml")));
        assertEquals("owner settings", Files.readString(root.resolve("settings.yml")));
        var repeat = ThemeInstaller.install("nexo", "halloween", bundle(incoming), root, state);
        assertEquals(0, repeat.updated());
        assertTrue(repeat.preserved().isEmpty());
    }

    @Test public void nexoArchiveCannotWriteOraxenPathsOtherThemesOrOutsideTheNamespace() throws Exception {
        for (boolean force : new boolean[] {false, true}) {
            for (String name : new String[] {"../outside.png", "/absolute.png", "settings.yml", PREFIX + "../../other.png",
                    "pack/assets/dynamicshop/textures/test.png", "items/dynamicshop_halloween.yml",
                    "glyphs/dynamicshop_christmas.yml", "items/dynamicshop_christmas_theme.yml",
                    PREFIX.replace("dynamicshop_halloween", "dynamicshop_christmas") + "test.png"}) {
                var entries = new LinkedHashMap<String, String>();
                entries.put(PNG, "valid");
                entries.put(name, "invalid");
                var root = directory.getRoot().toPath().resolve("Nexo");
                try {
                    ThemeInstaller.install("nexo", "halloween", bundle(entries), root,
                            directory.getRoot().toPath().resolve("state"), force, directory.getRoot().toPath().resolve("backups"));
                    fail("Invalid archive path accepted: " + name);
                } catch (java.io.IOException expected) {
                    assertFalse(Files.exists(root.resolve(PNG)));
                }
            }
        }
    }

    @Test public void selectionRoutesToActualProviderFoldersAndHonorsIndependentOptOuts() throws Exception {
        var shop = PluginTestFixture.plugin(directory.newFolder("DynamicShop"));
        PluginTestFixture.set(shop, JavaPlugin.class, "classLoader", getClass().getClassLoader());
        var nexoFolder = directory.getRoot().toPath().resolve("custom-nexo-data");
        var oraxenFolder = directory.getRoot().toPath().resolve("custom-oraxen-data");
        Plugin nexo = provider(nexoFolder);
        Plugin oraxen = provider(oraxenFolder);
        PluginManager manager = PluginTestFixture.proxy(PluginManager.class, (object, method, args) -> switch (method.getName()) {
            case "isPluginEnabled" -> true;
            case "getPlugin" -> args[0].equals("Nexo") ? nexo : oraxen;
            default -> throw new AssertionError(method.getName());
        });
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        var oldServer = serverField.get(null);
        var oldShop = DynamicShop.getInstance();
        try {
            serverField.set(null, PluginTestFixture.proxy(Server.class, (object, method, args) -> switch (method.getName()) {
                case "getPluginManager" -> manager;
                default -> throw new AssertionError(method.getName());
            }));
            PluginTestFixture.set(null, DynamicShop.class, "instance", shop);
            shop.getConfig().set("gui.theme", "halloween");
            shop.getConfig().set("gui.auto_install_oraxen_theme", false);
            ThemeInstaller.installSelected(shop); // auto selects Nexo when both are enabled.
            assertTrue(Files.exists(nexoFolder.resolve(GLYPHS)));
            assertFalse(Files.exists(oraxenFolder));
            assertTrue(Files.exists(shop.getDataFolder().toPath().resolve("theme-installation/nexo-halloween.properties")));
            shop.getConfig().set("gui.theme", "christmas");
            shop.getConfig().set("gui.auto_install_nexo_theme", false);
            ThemeInstaller.installSelected(shop);
            assertFalse(Files.exists(nexoFolder.resolve("glyphs/dynamicshop_christmas.yml")));
            var manual = ThemeInstaller.installSelectedManually(shop, false);
            assertEquals("nexo", manual.provider());
            assertTrue(Files.exists(nexoFolder.resolve("glyphs/dynamicshop_christmas.yml")));
            shop.getConfig().set("gui.custom_item_provider", "oraxen");
            ThemeInstaller.installSelected(shop);
            assertFalse(Files.exists(oraxenFolder));
            shop.getConfig().set("gui.auto_install_oraxen_theme", true);
            ThemeInstaller.installSelected(shop);
            assertTrue(Files.exists(oraxenFolder.resolve("glyphs/dynamicshop_christmas.yml")));
            assertTrue(Files.exists(shop.getDataFolder().toPath().resolve("theme-installation/oraxen-christmas.properties")));
            shop.getConfig().set("gui.custom_item_provider", "none");
            shop.getConfig().set("gui.theme", "summer");
            ThemeInstaller.installSelected(shop);
            assertFalse(Files.exists(nexoFolder.resolve("glyphs/dynamicshop_summer.yml")));
            try {
                ThemeInstaller.installSelectedManually(shop, true);
                fail("Disabled provider must not install into either plugin");
            } catch (java.io.IOException expected) {
                assertTrue(expected.getMessage().contains("enabled Nexo or Oraxen"));
            }
        } finally {
            serverField.set(null, oldServer);
            PluginTestFixture.set(null, DynamicShop.class, "instance", oldShop);
        }
    }

    private Plugin provider(Path folder) {
        return PluginTestFixture.proxy(Plugin.class, (object, method, args) -> switch (method.getName()) {
            case "isEnabled" -> true;
            case "getDataFolder" -> folder.toFile();
            default -> throw new AssertionError(method.getName());
        });
    }

    private static InputStream bundle(Map<String, String> files) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new ByteArrayInputStream(output.toByteArray());
    }
}
