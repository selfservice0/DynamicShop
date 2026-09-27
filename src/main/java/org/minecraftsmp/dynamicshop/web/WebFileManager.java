package org.minecraftsmp.dynamicshop.web;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

/** Installs only the known bundled web assets. Backups live outside the served web directory. */
public final class WebFileManager {
    public static final List<String> FILES =
            List.of(
                    "index.html",
                    "style.css",
                    "app.js",
                    "dashboard.js",
                    "items.html",
                    "items.js",
                    "admin.html",
                    "web-update.html");
    private final Path data;
    private final Function<String, InputStream> resources;

    public WebFileManager(Path data, Function<String, InputStream> resources) {
        this.data = data;
        this.resources = resources;
    }

    public synchronized Map<String, Object> status() throws IOException {
        List<String> outdated = new ArrayList<>();
        String version = "unknown";
        for (String name : FILES) {
            try (InputStream in = resources.apply("web/" + name)) {
                if (in == null) throw new IOException("Bundled web file missing: " + name);
                String expected = version(in.readAllBytes());
                if (name.equals("index.html")) version = expected;
                Path target = data.resolve("web").resolve(name);
                if (!Files.isRegularFile(target)
                        || expected.equals("unknown")
                        || !expected.equals(version(Files.readAllBytes(target))))
                    outdated.add(name);
            }
        }
        return Map.of(
                "version", version, "needsUpdate", !outdated.isEmpty(), "outdatedFiles", outdated);
    }

    public synchronized void installMissing() throws IOException {
        Path web = data.resolve("web");
        Files.createDirectories(web);
        for (String name : FILES)
            if (!Files.exists(web.resolve(name)))
                try (InputStream in = resources.apply("web/" + name)) {
                    if (in == null) throw new IOException("Bundled web file missing: " + name);
                    Files.copy(in, web.resolve(name));
                }
    }

    public synchronized Map<String, Object> update() throws IOException {
        Path web = data.resolve("web");
        Files.createDirectories(web);
        // Load every resource before touching any installed file.
        Map<String, byte[]> bundled = new LinkedHashMap<>(), previous = new LinkedHashMap<>();
        for (String name : FILES) {
            try (InputStream in = resources.apply("web/" + name)) {
                if (in == null) throw new IOException("Bundled web file missing: " + name);
                bundled.put(name, in.readAllBytes());
            }
            Path target = web.resolve(name);
            if (Files.exists(target)) previous.put(name, Files.readAllBytes(target));
        }
        Path backup =
                data.resolve("web-backups")
                        .resolve(
                                LocalDateTime.now()
                                                .format(
                                                        DateTimeFormatter.ofPattern(
                                                                "yyyyMMdd-HHmmss"))
                                        + "-"
                                        + UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectories(backup);
        for (var entry : previous.entrySet())
            Files.write(backup.resolve(entry.getKey()), entry.getValue());
        List<String> changed = new ArrayList<>();
        try {
            for (var entry : bundled.entrySet()) {
                replace(web.resolve(entry.getKey()), entry.getValue());
                changed.add(entry.getKey());
            }
        } catch (IOException error) {
            for (String name : changed)
                try {
                    if (previous.containsKey(name)) replace(web.resolve(name), previous.get(name));
                    else Files.deleteIfExists(web.resolve(name));
                } catch (IOException rollback) {
                    error.addSuppressed(rollback);
                }
            throw error;
        }
        return Map.of(
                "success",
                true,
                "backup",
                data.relativize(backup).toString(),
                "files",
                FILES.size(),
                "version",
                version(bundled.get("index.html")));
    }

    private void replace(Path target, byte[] bytes) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), "web-update-", ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static String version(byte[] bytes) {
        String line = new String(bytes, StandardCharsets.UTF_8).split("\\R", 2)[0];
        int start = line.indexOf("DS-WEB-VERSION:");
        return start < 0
                ? "unknown"
                : line.substring(start + 15).replace("-->", "").replace("*/", "").trim();
    }
}
