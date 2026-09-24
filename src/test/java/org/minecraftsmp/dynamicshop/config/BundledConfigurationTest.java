package org.minecraftsmp.dynamicshop.config;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.junit.Assert.*;

public class BundledConfigurationTest {
    @Test
    public void bundledYamlHasNoDuplicateKeys() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        for (String file : List.of("config.yml", "messages.yml", "messages_nexo_example.yml",
                "messages_oraxen_example.yml", "plugin.yml")) {
            try (InputStream stream = getClass().getResourceAsStream("/" + file)) {
                assertNotNull(file, stream);
                assertTrue(file, yaml.load(stream) instanceof Map);
            }
        }
    }

    @Test
    public void copperKeepsThePreviouslyEffectivePrice() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/config.yml")) {
            Map<?, ?> config = new Yaml(new SafeConstructor(new LoaderOptions())).load(stream);
            Map<?, ?> items = (Map<?, ?>) config.get("items");
            Map<?, ?> copper = (Map<?, ?>) items.get("COPPER_BLOCK");
            assertEquals(54.0, ((Number) copper.get("base")).doubleValue(), 0.0);
        }
    }
}
