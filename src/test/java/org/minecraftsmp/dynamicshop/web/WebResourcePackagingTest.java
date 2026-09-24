package org.minecraftsmp.dynamicshop.web;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.Assert.*;

/** Regression for Maven replacing JavaScript's ${id} with the artifact coordinates. */
public class WebResourcePackagingTest {
    private String resource(String name) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(name, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    @Test public void packagedWebsiteDiffersOnlyInExplicitVersionMarker() throws Exception {
        String descriptor = resource("plugin.yml");
        String version = descriptor.lines().filter(s -> s.startsWith("version:")).findFirst().orElseThrow()
                .substring("version:".length()).trim().replace("'", "");
        assertFalse(version.contains("@"));
        Path source = Path.of(System.getProperty("basedir", "."), "src/main/resources/web");
        for (String file : WebFileManager.FILES) {
            String expected = Files.readString(source.resolve(file)).replace("@project.version@", version);
            assertEquals("Resource filtering changed JavaScript/HTML in " + file, expected, resource("web/" + file));
        }
        assertTrue(resource("web/app.js").contains("id=\"${id}\""));
        assertFalse(resource("web/app.js").contains("org.minecraftsmp:DynamicShop:jar:"));
    }
}
