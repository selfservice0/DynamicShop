package org.minecraftsmp.dynamicshop.managers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import static org.junit.Assert.*;

public class MessageDefaultsTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();

    @Test public void existingMessagesGainTooltipDefaultsWithoutChangingCustomizations() throws Exception {
        var plugin = PluginTestFixture.plugin(directory.getRoot());
        PluginTestFixture.set(plugin, JavaPlugin.class, "classLoader", getClass().getClassLoader());
        var file = directory.getRoot().toPath().resolve("messages.yml");
        String original = "messages:\r\n  # Keep these colors and glyphs\r\n"
                + "  shop-gui-title: '<white><shift:-48><glyph:dynamicshop_gui_menu>'\r\n"
                + "  special-lore-type-permission: ''\r\n"
                + "  special-lore-permission-node: '&6Nodo: {permission}'\r\n";
        Files.writeString(file, original, StandardCharsets.UTF_8);
        var messages = plugin.getMessageManager();
        messages.init();
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).startsWith(original));
        var stored = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals("", stored.getString("messages.special-lore-type-permission"));
        assertEquals("&6Nodo: {permission}", stored.getString("messages.special-lore-permission-node"));
        assertEquals("&7Type: &dGroup", stored.getString("messages.special-lore-type-group"));
        assertEquals("&7Group: &f{group}", stored.getString("messages.special-lore-group-name"));
        assertNull(messages.getMessage("special-lore-type-permission"));
        assertEquals("§6Nodo: group.endupgrade1", messages.getMessage(
                "special-lore-permission-node", Map.of("permission", "group.endupgrade1")));
        String merged = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, merged.replace("&6Nodo: {permission}", "&bAccess: {permission}"), StandardCharsets.UTF_8);
        messages.reload();
        assertEquals("§bAccess: group.endupgrade1", messages.getMessage(
                "special-lore-permission-node", Map.of("permission", "group.endupgrade1")));
        assertEquals("Reload must not append duplicate defaults",
                merged.replace("&6Nodo: {permission}", "&bAccess: {permission}"),
                Files.readString(file, StandardCharsets.UTF_8));
    }
}
