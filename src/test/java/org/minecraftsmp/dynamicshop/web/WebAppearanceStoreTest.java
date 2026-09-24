package org.minecraftsmp.dynamicshop.web;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;

public class WebAppearanceStoreTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void persistsDesignAndSharedBranding() throws Exception {
        Path file = temp.getRoot().toPath().resolve("appearance.json");
        var store = new WebAppearanceStore(file);
        var settings = store.get();
        settings.put("design", "nova");
        settings.put("title", "My Server");
        settings.put("homeEnabled", true);
        settings.put("homeUrl", "https://example.com/community");
        store.save(settings);
        assertEquals("nova", new WebAppearanceStore(file).get().get("design"));
        assertEquals("My Server", new WebAppearanceStore(file).get().get("title"));
    }

    @Test
    public void rejectsUnsafeLinksAndInvalidStylesWithoutChangingSavedSettings() throws Exception {
        var store = new WebAppearanceStore(temp.getRoot().toPath().resolve("appearance.json"));
        for (String url :
                List.of(
                        "javascript:alert(1)",
                        "data:text/html,test",
                        "/relative",
                        "https://user@example.com")) {
            var value = store.get();
            value.put("homeUrl", url);
            try {
                store.save(value);
                fail(url);
            } catch (IllegalArgumentException expected) {
            }
        }
        var value = store.get();
        value.put("accent", "red;display:none");
        try {
            store.save(value);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        assertEquals("chest", store.get().get("design"));
    }

    @Test
    public void acceptsAllDesignsAndRejectsInvalidNestedPreferences() {
        for (String design : List.of("classic", "market", "inventory", "nova", "chest", "dsx")) {
            var value = WebAppearanceStore.defaults();
            value.put("design", design);
            assertEquals(design, WebAppearanceStore.validate(value).get("design"));
        }
        var value = WebAppearanceStore.defaults();
        value.put("layoutStyles", Map.of("nova", Map.of("font", "malicious")));
        try {
            WebAppearanceStore.validate(value);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void stripsUnknownFields() {
        var value = WebAppearanceStore.defaults();
        value.put("secret", "not public");
        assertFalse(WebAppearanceStore.validate(value).containsKey("secret"));
    }

    @Test
    public void persistsDsxAndItsOwnColorsWithoutReplacingOtherDesigns() throws Exception {
        Path file = temp.getRoot().toPath().resolve("dsx-appearance.json");
        var store = new WebAppearanceStore(file);
        var value = store.get();
        value.put("design", "dsx");
        value.put("accent", "#f0b429");
        value.put(
                "layoutStyles",
                Map.of(
                        "dsx",
                        Map.of("accent", "#f0b429", "background", "#0a0d11"),
                        "chest",
                        Map.of("accent", "#85c8ff")));
        store.save(value);
        var restored = new WebAppearanceStore(file).get();
        assertEquals("dsx", restored.get("design"));
        var styles = (Map<?, ?>) restored.get("layoutStyles");
        assertEquals("#f0b429", ((Map<?, ?>) styles.get("dsx")).get("accent"));
        assertEquals("#85c8ff", ((Map<?, ?>) styles.get("chest")).get("accent"));
    }
}
