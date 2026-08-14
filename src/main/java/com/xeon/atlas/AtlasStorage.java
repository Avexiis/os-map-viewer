package com.xeon.atlas;

import com.xeon.AppVersion;
import com.xeon.config.ConfigManager;
import com.xeon.io.Paths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

public final class AtlasStorage {
    public static final String ATLAS_FILE_NAME = "FullWorldMap.atlas";

    private static final String ATLAS_DIR = "atlas";
    private static final String TEMP_DIR = "atlas-temp";
    private static final String METADATA_FILE_NAME = "atlas.properties";
    private static final String KEY_APP_VERSION = "appVersion";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_INSTALLED_AT = "installedAt";
    private static final String SOURCE_BUNDLED = "bundled";
    private static final String SOURCE_GENERATED = "generated";

    private AtlasStorage() {
    }

    public static Path atlasDirectory() {
        return ConfigManager.defaultDirectory().resolve(ATLAS_DIR);
    }

    public static Path installedAtlasPath() {
        return atlasDirectory().resolve(ATLAS_FILE_NAME);
    }

    public static Path ensureInstalledAtlas() throws IOException {
        ensureDirectories();
        String appVersion = AppVersion.current();
        Path installedAtlas = installedAtlasPath();
        if (shouldInstallBundledAtlas(installedAtlas, appVersion)) {
            installBundledAtlas(appVersion);
        }
        return installedAtlas;
    }

    public static void recordGeneratedAtlasInstalled() throws IOException {
        ensureDirectories();
        writeMetadata(SOURCE_GENERATED, AppVersion.current());
    }

    public static Path tempRoot() {
        return ConfigManager.defaultDirectory().resolve(TEMP_DIR);
    }

    public static void ensureDirectories() throws IOException {
        Files.createDirectories(atlasDirectory());
        Files.createDirectories(tempRoot());
    }

    private static boolean shouldInstallBundledAtlas(Path installedAtlas, String appVersion) {
        if (!Files.isRegularFile(installedAtlas)) {
            return true;
        }

        String installedVersion = readMetadata().getProperty(KEY_APP_VERSION);
        if (installedVersion == null || installedVersion.isBlank()) {
            return true;
        }
        return AppVersion.isNewerThan(appVersion, installedVersion);
    }

    private static void installBundledAtlas(String appVersion) throws IOException {
        Path temp = Files.createTempFile(atlasDirectory(), ATLAS_FILE_NAME, ".tmp");
        try (InputStream in = AtlasStorage.class.getResourceAsStream(Paths.MAP_ATLAS_RESOURCE)) {
            if (in == null) {
                throw new IOException("Bundled atlas resource not found: " + Paths.MAP_ATLAS_RESOURCE);
            }
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            moveReplacing(temp, installedAtlasPath());
            writeMetadata(SOURCE_BUNDLED, appVersion);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Path metadataPath() {
        return atlasDirectory().resolve(METADATA_FILE_NAME);
    }

    private static Properties readMetadata() {
        Properties properties = new Properties();
        Path path = metadataPath();
        if (!Files.isRegularFile(path)) {
            return properties;
        }
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException ignored) {
        }
        return properties;
    }

    private static void writeMetadata(String source, String appVersion) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(KEY_APP_VERSION, appVersion == null || appVersion.isBlank() ? AppVersion.UNKNOWN : appVersion);
        properties.setProperty(KEY_SOURCE, source == null || source.isBlank() ? SOURCE_BUNDLED : source);
        properties.setProperty(KEY_INSTALLED_AT, Instant.now().toString());
        Path path = metadataPath();
        Files.createDirectories(path.getParent());
        try (OutputStream out = Files.newOutputStream(path)) {
            properties.store(out, "OS Map Viewer atlas install metadata");
        }
    }

    public static void moveReplacing(Path source, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
