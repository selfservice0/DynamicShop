package org.minecraftsmp.dynamicshop.web;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class WebFileManagerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private InputStream resource(String name) {
        return new ByteArrayInputStream(
                ("// DS-WEB-VERSION: 3.0\nnew " + name).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void preservesOldFilesUntilExplicitUpdateThenBacksThemUp() throws Exception {
        Path data = temp.getRoot().toPath(), web = data.resolve("web");
        Files.createDirectories(web);
        Files.writeString(
                web.resolve("index.html"), "<!-- DS-WEB-VERSION: 2.6.7 -->\ncustom website");
        Files.writeString(data.resolve("website-appearance.json"), "saved preferences");
        var manager = new WebFileManager(data, this::resource);
        manager.installMissing();
        assertTrue(Files.readString(web.resolve("index.html")).contains("custom website"));
        assertEquals(true, manager.status().get("needsUpdate"));
        var result = manager.update();
        assertEquals(false, manager.status().get("needsUpdate"));
        assertTrue(
                Files.readString(data.resolve((String) result.get("backup")).resolve("index.html"))
                        .contains("custom website"));
        assertEquals(
                "saved preferences", Files.readString(data.resolve("website-appearance.json")));
        assertFalse(((String) result.get("backup")).startsWith("web/"));
    }

    @Test
    public void missingBundledResourceCannotPartiallyReplaceWebsite() throws Exception {
        Path data = temp.getRoot().toPath(), web = data.resolve("web");
        Files.createDirectories(web);
        Files.writeString(web.resolve("index.html"), "old website");
        var manager =
                new WebFileManager(data, name -> name.endsWith("items.js") ? null : resource(name));
        try {
            manager.update();
            fail();
        } catch (IOException expected) {
        }
        assertEquals("old website", Files.readString(web.resolve("index.html")));
        assertFalse(Files.exists(web.resolve("app.js")));
    }

    @Test
    public void handlesAllMarkerStyles() {
        for (String marker :
                new String[] {
                    "// DS-WEB-VERSION: 3.0",
                    "/* DS-WEB-VERSION: 3.0 */",
                    "<!-- DS-WEB-VERSION: 3.0 -->"
                })
            assertEquals("3.0", WebFileManager.version(marker.getBytes(StandardCharsets.UTF_8)));
    }
}
