package org.minecraftsmp.dynamicshop.managers;

import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.zip.ZipInputStream;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.util.GuiTheme;

/** Installs bundled artwork for the selected provider while retaining owner edits. */
public final class ThemeInstaller {
    private ThemeInstaller() {}

    public static boolean autoInstallEnabled(DynamicShop plugin, String provider) {
        return (provider.equals("oraxen") || provider.equals("nexo"))
                && plugin.getConfig().getBoolean("gui.auto_install_" + provider + "_theme", true);
    }

    public static String providerName(String provider) {
        return switch (provider) {
            case "oraxen" -> "Oraxen";
            case "nexo" -> "Nexo";
            default -> throw new IllegalArgumentException("Unsupported theme provider: " + provider);
        };
    }

    public static String reloadInstructions(String provider) {
        return provider.equals("nexo")
                ? "Run /nexo reload, wait for pack generation/upload, then /nexo pack <player>. Reopen menus."
                : "Run /oraxen reload all, wait for pack generation/upload, then /oraxen pack send. Reopen menus.";
    }

    public static void installSelected(DynamicShop plugin) {
        String theme = GuiTheme.selected(plugin);
        String provider = CustomItemSupport.guiProvider();
        if (!shouldInstall(theme, autoInstallEnabled(plugin, provider), provider)) return;
        var target = Bukkit.getPluginManager().getPlugin(providerName(provider));
        if (target == null || !target.isEnabled()) return;
        try {
            Report report = installBundle(plugin, provider, theme, target.getDataFolder().toPath(), false);
            if (report.installed() + report.updated() > 0) {
                plugin.getLogger().info("[Theme] Installed " + report.installed() + " and updated " + report.updated()
                        + " " + theme + " theme file(s) in " + target.getDataFolder() + ". " + reloadInstructions(provider));
            }
            if (!report.preserved().isEmpty()) {
                plugin.getLogger().warning("[Theme] Preserved existing/custom " + providerName(provider) + " files: "
                        + String.join(", ", report.preserved())
                        + ". Use /shopadmin theme install force to back up and replace the selected theme's bundled files,"
                        + " or merge theme updates manually.");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[Theme] Could not finish installing the " + providerName(provider)
                    + " " + theme + " theme: " + e.getMessage());
        }
    }

    /** Explicit installation honors the GUI provider but ignores its automatic-install setting. */
    public static Report installSelectedManually(DynamicShop plugin, boolean force) throws IOException {
        String theme = GuiTheme.selected(plugin);
        if (!GuiTheme.builtIn(theme)) throw new IOException("Select a bundled theme first with /shopadmin theme <name>.");
        String provider = CustomItemSupport.guiProvider();
        if (!shouldInstall(theme, true, provider)) {
            throw new IOException("Select an enabled Nexo or Oraxen GUI provider in gui.custom_item_provider.");
        }
        var target = Bukkit.getPluginManager().getPlugin(providerName(provider));
        if (target == null || !target.isEnabled()) throw new IOException(providerName(provider) + " is not enabled on this server.");
        return installBundle(plugin, provider, theme, target.getDataFolder().toPath(), force);
    }

    private static Report installBundle(DynamicShop plugin, String provider, String theme, Path folder, boolean force) throws IOException {
        Path installation = plugin.getDataFolder().toPath().resolve("theme-installation");
        try (InputStream bundle = plugin.getResource("gui-themes/" + provider + "/" + theme + ".zip")) {
            if (bundle == null) throw new IOException("Bundled " + providerName(provider) + " " + theme + " theme is missing from the JAR");
            return install(provider, theme, bundle, folder, installation.resolve(provider + "-" + theme + ".properties"),
                    force, installation.resolve("backups"));
        }
    }

    static boolean shouldInstall(String theme, boolean enabled, String provider) {
        return enabled && ("oraxen".equals(provider) || "nexo".equals(provider)) && GuiTheme.builtIn(theme);
    }

    public record Report(String provider, int installed, int updated, List<String> preserved, Path backupFolder) {}

    static Report install(String provider, String theme, InputStream bundle, Path folder, Path stateFile) throws IOException {
        return install(provider, theme, bundle, folder, stateFile, false, null);
    }

    static Report install(String provider, String theme, InputStream bundle, Path folder, Path stateFile,
                          boolean force, Path backupRoot) throws IOException {
        theme = GuiTheme.normalize(theme);
        if (!shouldInstall(theme, true, provider)) throw new IOException("Unknown theme: " + theme);
        if (force && backupRoot == null) throw new IOException("Force installation requires a backup folder");
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(bundle)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!allowed(provider, theme, name) || files.put(name, zip.readAllBytes()) != null) {
                    throw new IOException("Invalid theme archive entry: " + name);
                }
            }
        }
        if (files.isEmpty()) throw new IOException("Theme archive is empty");
        Files.createDirectories(folder);
        Path root = folder.toRealPath();
        // Validate every target before writing any files; never follow an inner symlink.
        for (String name : files.keySet()) safeTarget(root, name);
        Properties previous = new Properties();
        if (!force && Files.exists(stateFile)) {
            try (InputStream input = Files.newInputStream(stateFile)) { previous.load(input); }
        }
        Properties next = new Properties();
        int installed = 0;
        int updated = 0;
        List<String> preserved = new ArrayList<>();
        Map<String, byte[]> writes = new LinkedHashMap<>();
        Map<String, byte[]> backups = new LinkedHashMap<>();
        for (var entry : files.entrySet()) {
            String name = entry.getKey();
            byte[] incoming = entry.getValue();
            String fingerprint = fingerprint(name, incoming);
            Path target = safeTarget(root, name);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                writes.put(name, incoming);
                installed++;
            } else {
                byte[] existing = Files.readAllBytes(target);
                String current;
                try { current = fingerprint(name, existing); }
                catch (IOException invalidCustomYaml) { current = "invalid-custom-yaml"; }
                if (!current.equals(fingerprint)) {
                    if (force || current.equals(previous.getProperty(name))) {
                        byte[] replacement;
                        try { replacement = preserveGlyphCharacters(name, incoming, existing); }
                        catch (IOException invalidOldGlyphs) {
                            if (!force) throw invalidOldGlyphs;
                            // The unparseable file is backed up before replacing it with valid bundled YAML.
                            replacement = incoming;
                        }
                        writes.put(name, replacement);
                        if (force) backups.put(name, existing);
                        updated++;
                    } else {
                        preserved.add(name);
                        // Keep the previous ownership digest: a later manual restoration is recognizable.
                        if (previous.containsKey(name)) next.setProperty(name, previous.getProperty(name));
                        continue;
                    }
                }
            }
            next.setProperty(name, fingerprint);
        }
        // Finish every backup before installing even a missing file. A failed backup
        // must never leave custom files overwritten or a partially started forced update.
        Path backupFolder = null;
        if (!backups.isEmpty()) {
            Files.createDirectories(backupRoot);
            backupFolder = Files.createTempDirectory(backupRoot, provider + "-" + theme + "-" + System.currentTimeMillis() + "-");
            for (var entry : backups.entrySet()) writeAtomic(safeTarget(backupFolder, entry.getKey()), entry.getValue());
        }
        for (var entry : writes.entrySet()) writeAtomic(safeTarget(root, entry.getKey()), entry.getValue());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        next.store(output, "DynamicShop installed theme fingerprints; custom files are preserved");
        writeAtomic(stateFile, output.toByteArray());
        return new Report(provider, installed, updated, List.copyOf(preserved), backupFolder);
    }

    private static boolean allowed(String provider, String theme, String name) {
        if (name.equals("glyphs/dynamicshop_" + theme + ".yml")) return true;
        if (provider.equals("nexo")) {
            return name.equals("items/dynamicshop_" + theme + "_theme.yml")
                    || name.matches("pack/external_packs/dynamicshop_" + theme + "/assets/dynamicshop_" + theme
                            + "/textures/[a-zA-Z0-9_]+\\.png(?:\\.mcmeta)?");
        }
        return name.matches("items/dynamicshop_" + theme + "(?:_categories)?\\.yml")
                || name.matches("pack/assets/dynamicshop/textures/[a-zA-Z0-9_]+\\.png(?:\\.mcmeta)?");
    }

    private static Path safeTarget(Path root, String name) throws IOException {
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) throw new IOException("Theme path leaves the provider folder: " + name);
        Path current = root;
        for (Path part : root.relativize(target)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Theme target is a symbolic link: " + name);
        }
        return target;
    }

    private static String fingerprint(String name, byte[] bytes) throws IOException {
        if (name.endsWith(".yml")) {
            var yaml = yaml(bytes);
            if (name.startsWith("glyphs/")) {
                // Providers may assign characters and reformat YAML on reload; neither is a custom edit.
                for (String id : yaml.getKeys(false)) yaml.set(id + ".char", null);
            }
            bytes = new Gson().toJson(canonical(yaml)).getBytes(StandardCharsets.UTF_8);
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static Object canonical(Object value) {
        if (value instanceof ConfigurationSection section) return canonical(section.getValues(false));
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            map.forEach((key, child) -> result.put(key.toString(), canonical(child)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(ThemeInstaller::canonical).toList();
        return value;
    }

    private static YamlConfiguration yaml(byte[] bytes) throws IOException {
        var yaml = new YamlConfiguration();
        try { yaml.loadFromString(new String(bytes, StandardCharsets.UTF_8)); }
        catch (InvalidConfigurationException e) { throw new IOException("Invalid theme YAML", e); }
        return yaml;
    }

    private static byte[] preserveGlyphCharacters(String name, byte[] incoming, byte[] existing) throws IOException {
        if (!name.startsWith("glyphs/")) return incoming;
        var updated = yaml(incoming);
        var original = yaml(existing);
        for (String id : updated.getKeys(false)) {
            if (original.contains(id + ".char")) updated.set(id + ".char", original.get(id + ".char"));
        }
        return updated.saveToString().getBytes(StandardCharsets.UTF_8);
    }

    private static void writeAtomic(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), ".dynamicshop-theme-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
