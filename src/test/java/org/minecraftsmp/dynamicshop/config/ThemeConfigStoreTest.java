package org.minecraftsmp.dynamicshop.config;

import java.nio.file.Files;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ThemeConfigStoreTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();

    @Test public void flowMappingsUnicodeBomAndInlineCommentsSurvive() throws Exception {
        for (String source : new String[] {"\uFEFF# 🪙\r\ngui: {theme: 'default', shop_menu_size: 54}\r\n",
                "gui:\n  theme: default # a comment\n  filler_material: AIR\n"}) {
            var path = directory.getRoot().toPath().resolve("config.yml");
            Files.writeString(path, source);
            ThemeConfigStore.save(path, "newyears");
            String expected = source.contains("'default'") ? source.replace("'default'", "\"newyear\"")
                    : source.replace("theme: default", "theme: \"newyear\"");
            assertEquals(expected, Files.readString(path));
        }
    }

    @Test public void missingThemeIsAddedWithoutReplacingExistingValues() throws Exception {
        for (String original : new String[] {"# owner\ngui:\n  shop_menu_size: 45\n", "# owner\neconomy:\n  sell_tax_percent: 15\n", ""}) {
            var path = directory.getRoot().toPath().resolve("config.yml");
            Files.writeString(path, original);
            ThemeConfigStore.save(path, "christmas");
            assertTrue(Files.readString(path).startsWith(original));
            var config = YamlConfiguration.loadConfiguration(path.toFile());
            assertEquals("christmas", config.getString("gui.theme"));
            if (original.contains("45")) assertEquals(45, config.getInt("gui.shop_menu_size"));
        }
    }

    @Test public void duplicateOrMalformedConfigIsNeverOverwritten() throws Exception {
        for (String source : new String[] {"gui: nope\n", "gui:\n  theme: default\n  theme: halloween\n", "gui: {theme: [broken\n",
                "other: &look default\ngui:\n  theme: *look\n",
                "defaults: &base {theme: default}\ngui: *base\n",
                "defaults: &base {theme: default}\ngui:\n  <<: *base\n"}) {
            var path = directory.getRoot().toPath().resolve("config.yml");
            Files.writeString(path, source);
            try {
                ThemeConfigStore.save(path, "christmas");
                fail("Invalid config accepted");
            }
            catch (Exception expected) { assertEquals(source, Files.readString(path)); }
        }
    }
}
