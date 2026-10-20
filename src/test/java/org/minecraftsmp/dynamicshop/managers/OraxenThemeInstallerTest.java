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
import static org.junit.Assert.*;

public class OraxenThemeInstallerTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private static final String PNG = "pack/assets/dynamicshop/textures/dynamicshop_halloween_shop_54.png";
    private static final String GLYPHS = "glyphs/dynamicshop_halloween.yml";

    @Test public void bothRealBundlesInstallEveryResourceAndRepeatWithoutChanges() throws Exception {
        for (String theme : org.minecraftsmp.dynamicshop.util.GuiTheme.names()) {
            if (theme.equals("default")) continue;
            Path root = directory.getRoot().toPath().resolve(theme);
            Path state = directory.getRoot().toPath().resolve(theme + ".properties");
            byte[] bytes;
            try (InputStream resource = getClass().getResourceAsStream("/gui-themes/oraxen/" + theme + ".zip")) {
                assertNotNull(resource);
                bytes = resource.readAllBytes();
            }
            var first = ThemeInstaller.install("oraxen", theme, new ByteArrayInputStream(bytes), root, state);
            assertTrue(first.installed() > 40);
            assertEquals(0, first.updated());
            assertTrue(first.preserved().isEmpty());
            int count = 0;
            try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    assertArrayEquals(entry.getName(), zip.readAllBytes(), Files.readAllBytes(root.resolve(entry.getName())));
                    count++;
                }
            }
            assertEquals(count, first.installed());
            assertFalse(Files.exists(root.resolve("pack/assets/dynamicshop/textures/dynamicshop_gui_dialog_2.png")));
            var second = ThemeInstaller.install("oraxen", theme, new ByteArrayInputStream(bytes), root, state);
            assertEquals(0, second.installed());
            assertEquals(0, second.updated());
            assertTrue(second.preserved().isEmpty());
            // Native dialog IDs must resolve without replacing a server's legacy dialog texture.
            var glyphs = YamlConfiguration.loadConfiguration(root.resolve("glyphs/dynamicshop_" + theme + ".yml").toFile());
            assertEquals("dynamicshop:dynamicshop_" + theme + "_dialog.png",
                    glyphs.getString("dynamicshop_" + theme + "_dialog.texture"));
            for (String id : glyphs.getKeys(false)) {
                assertTrue(Files.exists(root.resolve("pack/assets/dynamicshop/textures/"
                        + glyphs.getString(id + ".texture").split(":")[1])));
            }
            try (var itemFiles = Files.list(root.resolve("items"))) {
                for (Path itemFile : itemFiles.toList()) {
                    var items = YamlConfiguration.loadConfiguration(itemFile.toFile());
                    for (String id : items.getKeys(false)) {
                        for (String texture : items.getStringList(id + ".Pack.textures")) {
                            assertTrue(Files.exists(root.resolve("pack/assets/dynamicshop/textures/"
                                    + texture.split(":")[1] + ".png")));
                        }
                    }
                }
            }
        }
    }

    @Test public void managedUpdatesRetainOraxenAssignedCharactersAndAddNewGlyphs() throws Exception {
        Path root = directory.getRoot().toPath().resolve("Oraxen");
        Path state = directory.getRoot().toPath().resolve("state.properties");
        String original = "dynamicshop_halloween_dialog:\n  texture: dynamicshop:old.png\n  height: 256\n";
        ThemeInstaller.install("oraxen", "halloween", bundle(Map.of(PNG, "old pixels", GLYPHS, original)), root, state);
        var assigned = YamlConfiguration.loadConfiguration(root.resolve(GLYPHS).toFile());
        assigned.set("dynamicshop_halloween_dialog.char", "\ue501");
        assigned.save(root.resolve(GLYPHS).toFile());
        String update = original.replace("old.png", "new.png") + "dynamicshop_halloween_new:\n  texture: dynamicshop:new.png\n";
        var report = ThemeInstaller.install("oraxen", "halloween", bundle(Map.of(PNG, "new pixels", GLYPHS, update)), root, state);
        assertEquals(2, report.updated());
        assertTrue(report.preserved().isEmpty());
        assertEquals("new pixels", Files.readString(root.resolve(PNG)));
        var glyphs = YamlConfiguration.loadConfiguration(root.resolve(GLYPHS).toFile());
        assertEquals("\ue501", glyphs.getString("dynamicshop_halloween_dialog.char"));
        assertEquals("dynamicshop:new.png", glyphs.getString("dynamicshop_halloween_dialog.texture"));
        assertTrue(glyphs.contains("dynamicshop_halloween_new"));
        var repeat = ThemeInstaller.install("oraxen", "halloween", bundle(Map.of(PNG, "new pixels", GLYPHS, update)), root, state);
        assertEquals(0, repeat.updated());
        assertTrue(repeat.preserved().isEmpty());
    }

    @Test public void customizedAndUnmanagedFilesArePreservedWhileNewFilesInstall() throws Exception {
        Path root = directory.getRoot().toPath().resolve("Oraxen");
        Path state = directory.getRoot().toPath().resolve("state.properties");
        ThemeInstaller.install("oraxen", "halloween", bundle(Map.of(PNG, "original", GLYPHS, "glyph:\n  height: 256\n")), root, state);
        Files.writeString(root.resolve(PNG), "custom artwork");
        Files.writeString(root.resolve(GLYPHS), "glyph:\n  height: 200\n  chat:\n    permission: my.custom.permission\n");
        Files.createDirectories(root.resolve("items"));
        Path existing = root.resolve("items/dynamicshop_halloween.yml");
        Files.writeString(existing, "custom_item:\n  material: STONE\n");
        Path config = root.resolve("settings.yml");
        Files.writeString(config, "keep this configuration");
        String added = "pack/assets/dynamicshop/textures/new_button.png";
        var report = ThemeInstaller.install("oraxen", "halloween", bundle(Map.of(PNG, "updated", GLYPHS, "glyph:\n  height: 256\n",
                "items/dynamicshop_halloween.yml", "new_item:\n  material: PAPER\n", added, "new button")), root, state);
        assertEquals(1, report.installed());
        assertEquals(0, report.updated());
        assertEquals(3, report.preserved().size());
        assertEquals("custom artwork", Files.readString(root.resolve(PNG)));
        assertTrue(Files.readString(root.resolve(GLYPHS)).contains("my.custom.permission"));
        assertTrue(Files.readString(existing).contains("custom_item"));
        assertEquals("keep this configuration", Files.readString(config));
    }

    @Test public void identicalManualInstallIsAdoptedButInvalidCustomYamlIsUntouched() throws Exception {
        Path root = directory.getRoot().toPath().resolve("Oraxen");
        Path state = directory.getRoot().toPath().resolve("state.properties");
        Files.createDirectories(root.resolve(PNG).getParent());
        Files.createDirectories(root.resolve(GLYPHS).getParent());
        Files.writeString(root.resolve(PNG), "same pixels");
        Files.writeString(root.resolve(GLYPHS), "broken: [invalid yaml");
        var initial = ThemeInstaller.install("oraxen", "halloween", bundle(Map.of(PNG, "same pixels", GLYPHS, "glyph:\n  height: 256\n")), root, state);
        assertEquals(java.util.List.of(GLYPHS), initial.preserved());
        var next = ThemeInstaller.install("oraxen", "halloween", bundle(Map.of(PNG, "new pixels")), root, state);
        assertEquals(1, next.updated());
        assertEquals("broken: [invalid yaml", Files.readString(root.resolve(GLYPHS)));
    }

    @Test public void invalidArchivePathsAreRejectedBeforeAnyFilesAreWritten() throws Exception {
        for (boolean force : new boolean[] {false, true}) {
            for (String path : new String[] {"../outside.png", "/absolute.png", "pack/assets/dynamicshop/textures/../../outside.png",
                    "settings.yml", "items/dynamicshop_moonstone.yml"}) {
                Path root = directory.getRoot().toPath().resolve("Oraxen");
                var entries = new LinkedHashMap<String, String>();
                entries.put(PNG, "valid");
                entries.put(path, "invalid");
                try {
                    ThemeInstaller.install("oraxen", "halloween", bundle(entries), root, directory.getRoot().toPath().resolve("state"),
                            force, directory.getRoot().toPath().resolve("backups"));
                    fail("Invalid archive path accepted: " + path);
                } catch (java.io.IOException expected) {
                    assertFalse(Files.exists(root.resolve(PNG)));
                }
            }
        }
    }

    @Test public void forcedUpdateBacksUpUnmanagedFilesRetainsCharactersAndAdoptsOwnership() throws Exception {
        Path root = directory.getRoot().toPath().resolve("Oraxen");
        Path state = directory.getRoot().toPath().resolve("state.properties");
        String items = "items/dynamicshop_halloween.yml";
        String added = "pack/assets/dynamicshop/textures/halloween_extra.png";
        var originals = Map.of(PNG, "custom pixels", GLYPHS,
                "glyph:\n  char: '\ue509'\n  texture: dynamicshop:custom.png\n  height: 123\n",
                items, "custom_item:\n  material: STONE\n");
        for (var entry : originals.entrySet()) {
            Files.createDirectories(root.resolve(entry.getKey()).getParent());
            Files.writeString(root.resolve(entry.getKey()), entry.getValue());
        }
        Path otherTheme = root.resolve("glyphs/dynamicshop_moonstone.yml");
        Files.writeString(otherTheme, "other theme");
        Path unrelated = root.resolve("settings.yml");
        Files.writeString(unrelated, "owner config");
        var incoming = Map.of(PNG, "new pixels", GLYPHS,
                "glyph:\n  texture: dynamicshop:new.png\n  height: 256\n", items,
                "halloween_icon:\n  material: BRICK\n", added, "extra pixels");
        var report = ThemeInstaller.install("oraxen", "halloween", bundle(incoming), root, state, true,
                directory.getRoot().toPath().resolve("backups"));
        assertEquals(1, report.installed());
        assertEquals(3, report.updated());
        assertTrue(report.preserved().isEmpty());
        assertNotNull(report.backupFolder());
        for (var entry : originals.entrySet()) {
            assertEquals(entry.getValue(), Files.readString(report.backupFolder().resolve(entry.getKey())));
        }
        assertFalse(Files.exists(report.backupFolder().resolve(added)));
        assertEquals("new pixels", Files.readString(root.resolve(PNG)));
        var glyphs = YamlConfiguration.loadConfiguration(root.resolve(GLYPHS).toFile());
        assertEquals("\ue509", glyphs.getString("glyph.char"));
        assertEquals(256, glyphs.getInt("glyph.height"));
        assertEquals("owner config", Files.readString(unrelated));
        assertEquals("other theme", Files.readString(otherTheme));
        var update = ThemeInstaller.install("oraxen", "halloween", bundle(Map.of(PNG, "later pixels")), root, state);
        assertEquals(1, update.updated());
        assertEquals("later pixels", Files.readString(root.resolve(PNG)));
    }

    @Test public void forceRepairsInvalidGlyphsAndStateAndDoesNotCreateEmptyBackups() throws Exception {
        Path root = directory.getRoot().toPath().resolve("Oraxen");
        Path state = directory.getRoot().toPath().resolve("state.properties");
        Path backups = directory.getRoot().toPath().resolve("backups");
        Files.createDirectories(root.resolve(GLYPHS).getParent());
        Files.writeString(root.resolve(GLYPHS), "broken: [invalid yaml");
        Files.writeString(state, "broken=\\uBADZ");
        var incoming = Map.of(GLYPHS, "glyph:\n  texture: dynamicshop:valid.png\n");
        var report = ThemeInstaller.install("oraxen", "halloween", bundle(incoming), root, state, true, backups);
        assertEquals(1, report.updated());
        assertEquals("broken: [invalid yaml", Files.readString(report.backupFolder().resolve(GLYPHS)));
        assertEquals(incoming.get(GLYPHS), Files.readString(root.resolve(GLYPHS)));
        var repeat = ThemeInstaller.install("oraxen", "halloween", bundle(incoming), root, state, true, backups);
        assertEquals(0, repeat.updated());
        assertNull(repeat.backupFolder());
        try (var folders = Files.list(backups)) { assertEquals(1, folders.count()); }
    }

    @Test public void backupFailurePreventsEveryInstallationWrite() throws Exception {
        Path root = directory.getRoot().toPath().resolve("Oraxen");
        Path state = directory.getRoot().toPath().resolve("state.properties");
        Path blockedBackup = directory.getRoot().toPath().resolve("backups");
        Files.writeString(blockedBackup, "not a directory");
        Files.createDirectories(root.resolve(PNG).getParent());
        Files.writeString(root.resolve(PNG), "custom pixels");
        var files = new LinkedHashMap<String, String>();
        files.put(GLYPHS, "glyph:\n  height: 256\n");
        files.put(PNG, "new pixels");
        try {
            ThemeInstaller.install("oraxen", "halloween", bundle(files), root, state, true, blockedBackup);
            fail("Install should fail when a backup cannot be made");
        } catch (java.io.IOException expected) {
            assertEquals("custom pixels", Files.readString(root.resolve(PNG)));
            assertFalse(Files.exists(root.resolve(GLYPHS)));
            assertFalse(Files.exists(state));
        }
    }

    @Test public void providerThemeAndOptOutGateInstallation() {
        for (String theme : new String[] {"moonstone", "halloween", "HALLOWEEN", "christmas", "newyear", "newyears",
                "valentines", "easter", "stpatricks", "summer"}) {
            assertTrue(ThemeInstaller.shouldInstall(theme, true, "oraxen"));
            assertFalse(ThemeInstaller.shouldInstall(theme, false, "oraxen"));
            for (String provider : new String[] {"none", "unknown"}) {
                assertFalse(ThemeInstaller.shouldInstall(theme, true, provider));
            }
        }
        for (String theme : new String[] {"default", "unknown", "../halloween"}) {
            assertFalse(ThemeInstaller.shouldInstall(theme, true, "oraxen"));
        }
    }

    @Test public void selectedThemeUsesOraxenDataFolderAndHonorsAutoProviderPriority() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object oldServer = serverField.get(null);
        DynamicShop oldPlugin = DynamicShop.getInstance();
        var shop = PluginTestFixture.plugin(directory.newFolder("DynamicShop"));
        PluginTestFixture.set(shop, JavaPlugin.class, "classLoader", getClass().getClassLoader());
        Path actualFolder = directory.getRoot().toPath().resolve("custom-provider-data");
        Plugin oraxen = PluginTestFixture.proxy(Plugin.class, (object, method, args) -> switch (method.getName()) {
            case "isEnabled" -> true;
            case "getDataFolder" -> actualFolder.toFile();
            default -> throw new AssertionError(method.getName());
        });
        PluginManager manager = PluginTestFixture.proxy(PluginManager.class, (object, method, args) -> switch (method.getName()) {
            case "isPluginEnabled" -> true; // Both providers installed: auto must retain Nexo priority.
            case "getPlugin" -> args[0].equals("Oraxen") ? oraxen : null;
            default -> throw new AssertionError(method.getName());
        });
        Server server = PluginTestFixture.proxy(Server.class, (object, method, args) -> switch (method.getName()) {
            case "getPluginManager" -> manager;
            default -> throw new AssertionError(method.getName());
        });
        try {
            serverField.set(null, server);
            PluginTestFixture.set(null, DynamicShop.class, "instance", shop);
            shop.getConfig().set("gui.theme", "halloween");
            ThemeInstaller.installSelected(shop);
            assertFalse(Files.exists(actualFolder));
            shop.getConfig().set("gui.custom_item_provider", "oraxen");
            ThemeInstaller.installSelected(shop);
            assertTrue(Files.exists(actualFolder.resolve(GLYPHS)));
            assertFalse(Files.exists(actualFolder.resolve("glyphs/dynamicshop_moonstone.yml")));
            shop.getConfig().set("gui.auto_install_oraxen_theme", false);
            shop.getConfig().set("gui.theme", "moonstone");
            ThemeInstaller.installSelected(shop);
            assertFalse(Files.exists(actualFolder.resolve("glyphs/dynamicshop_moonstone.yml")));
            shop.getConfig().set("gui.auto_install_oraxen_theme", true);
            ThemeInstaller.installSelected(shop);
            assertTrue(Files.exists(actualFolder.resolve("glyphs/dynamicshop_moonstone.yml")));
            assertTrue(Files.exists(actualFolder.resolve(GLYPHS)));
        } finally {
            PluginTestFixture.set(null, DynamicShop.class, "instance", oldPlugin);
            serverField.set(null, oldServer);
        }
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
