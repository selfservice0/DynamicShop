package org.minecraftsmp.dynamicshop.config;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraftsmp.dynamicshop.util.GuiTheme;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;

/** Changes just gui.theme, retaining the owner's other values, comments, and formatting. */
public final class ThemeConfigStore {
    private ThemeConfigStore() {}

    public static void save(Path file, String selected) throws Exception {
        String theme = GuiTheme.normalize(selected);
        if (!GuiTheme.names().contains(theme)) throw new IOException("Unknown theme: " + selected);
        String original = Files.readString(file, StandardCharsets.UTF_8);
        boolean bom = original.startsWith("\uFEFF");
        String source = bom ? original.substring(1) : original;
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        var yaml = new Yaml(new SafeConstructor(options));
        yaml.load(source);
        Node root = yaml.compose(new StringReader(source));
        if (root != null && !(root instanceof MappingNode)) throw new IOException("Config root must be a mapping");
        Node gui = child(root, "gui");
        if (gui != null && !(gui instanceof MappingNode)) throw new IOException("gui must be a mapping");
        if (gui != null && (gui.getAnchor() != null || child(gui, "<<") != null)) {
            throw new IOException("gui uses a YAML anchor or merge; set an explicit gui.theme in config.yml first");
        }
        Node value = child(gui, "theme");
        if (value == null) {
            var defaults = new YamlConfiguration();
            defaults.set("gui.theme", theme);
            ConfigDefaultInserter.addMissing(file, defaults);
            return;
        }
        if (!(value instanceof ScalarNode)) throw new IOException("gui.theme must be a theme name");
        if (value.getAnchor() != null) throw new IOException("gui.theme uses a YAML anchor; set an explicit theme name first");
        int start = source.offsetByCodePoints(0, value.getStartMark().getIndex());
        int end = source.offsetByCodePoints(0, value.getEndMark().getIndex());
        String updated = (bom ? "\uFEFF" : "") + source.substring(0, start) + "\"" + theme + "\"" + source.substring(end);
        new YamlConfiguration().loadFromString(updated);
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".dynamicshop-theme-config-", ".tmp");
        try {
            Files.writeString(temporary, updated, StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static Node child(Node node, String key) {
        if (node instanceof MappingNode mapping) {
            for (var tuple : mapping.getValue()) {
                if (tuple.getKeyNode() instanceof ScalarNode scalar && key.equals(scalar.getValue())) return tuple.getValueNode();
            }
        }
        return null;
    }
}
