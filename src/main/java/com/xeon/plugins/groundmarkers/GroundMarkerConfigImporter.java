package com.xeon.plugins.groundmarkers;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class GroundMarkerConfigImporter {
    private static final String GROUND_MARKER_PREFIX = "groundMarker.";
    private static final String REGION_KEY_PREFIX = "region_";
    private static final String BOLT_DIR = "bolt-launcher";
    private static final String BOLT_FLATPAK_ID = "com.adamcake.Bolt";

    private GroundMarkerConfigImporter() {
    }

    public enum Client {
        RUNELITE("RuneLite", "RuneLite properties file"),
        HDOS("HDOS", "HDOS profile properties file");

        private final String displayName;
        private final String chooserLabel;

        Client(String displayName, String chooserLabel) {
            this.displayName = displayName;
            this.chooserLabel = chooserLabel;
        }

        public String displayName() {
            return displayName;
        }

        public String chooserLabel() {
            return chooserLabel;
        }
    }

    public static ImportResult discover(Client client) {
        ImportResult result = new ImportResult();
        for (Path file : discoverConfigFiles(client)) {
            try {
                MarkerSource source = readSource(client, file);
                if (source.markerCount() > 0) {
                    result.addSource(source);
                }
            } catch (Exception ex) {
                result.addError(file + ": " + ex.getMessage());
            }
        }
        return result;
    }

    public static MarkerSource readSource(Client client, Path propertiesFile) throws IOException {
        List<GroundMarker> markers = readMarkers(propertiesFile);
        return new MarkerSource(client, propertiesFile.toAbsolutePath().normalize(), profileName(client, propertiesFile), markers);
    }

    public static List<GroundMarker> readMarkers(Path propertiesFile) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        List<GroundMarker> markers = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            Integer keyRegion = regionIdFromKey(key);
            if (keyRegion == null) {
                continue;
            }
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            List<GroundMarker> fromValue = GroundMarkerJson.parse(JsonParser.parseString(value));
            for (GroundMarker marker : fromValue) {
                if (marker.regionId == 0) {
                    marker.regionId = keyRegion;
                }
                markers.add(marker);
            }
        }
        return markers;
    }

    public static List<Path> discoverConfigFiles(Client client) {
        Set<Path> files = new LinkedHashSet<>();
        switch (client) {
            case RUNELITE -> {
                for (Path dir : candidateRuneLiteDirs()) {
                    addRuneLiteFiles(files, dir);
                }
            }
            case HDOS -> {
                for (Path dir : candidateHdosProfileDirs()) {
                    addPropertiesIn(files, dir);
                }
            }
        }
        return files.stream()
                .filter(Files::isReadable)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
    }

    public static List<Path> candidateProfileDirectories(Client client) {
        LinkedHashSet<Path> dirs = new LinkedHashSet<>();
        switch (client) {
            case RUNELITE -> {
                for (Path dir : candidateRuneLiteDirs()) {
                    if (Files.isDirectory(dir.resolve("profiles2"))) {
                        dirs.add(dir.resolve("profiles2").toAbsolutePath().normalize());
                    }
                    if (Files.isDirectory(dir.resolve("profiles"))) {
                        dirs.add(dir.resolve("profiles").toAbsolutePath().normalize());
                    }
                    if (Files.isDirectory(dir)) {
                        dirs.add(dir.toAbsolutePath().normalize());
                    }
                }
            }
            case HDOS -> {
                for (Path dir : candidateHdosProfileDirs()) {
                    if (Files.isDirectory(dir)) {
                        dirs.add(dir.toAbsolutePath().normalize());
                    }
                }
            }
        }
        return new ArrayList<>(dirs);
    }

    private static void addRuneLiteFiles(Set<Path> files, Path dir) {
        addIfFile(files, dir.resolve("settings.properties"));
        addPropertiesIn(files, dir.resolve("profiles2"));
        addPropertiesIn(files, dir.resolve("profiles"));
    }

    private static List<Path> candidateRuneLiteDirs() {
        LinkedHashSet<Path> dirs = new LinkedHashSet<>();
        for (Path home : candidateUserHomes()) {
            dirs.add(home.resolve(".runelite"));
            dirs.add(home.resolve("Library").resolve("Application Support").resolve("RuneLite"));
            dirs.add(home.resolve(".config").resolve("runelite"));
            dirs.add(home.resolve("snap").resolve("runelite").resolve("current").resolve(".runelite"));
        }

        addEnvDir(dirs, "APPDATA", "RuneLite");
        addEnvDir(dirs, "appdata", "RuneLite");
        addEnvDir(dirs, "LOCALAPPDATA", "RuneLite");
        addEnvDir(dirs, "XDG_CONFIG_HOME", "runelite");

        for (Path boltDataDir : candidateBoltDataDirs()) {
            dirs.add(boltDataDir.resolve(".runelite"));
        }
        return new ArrayList<>(dirs);
    }

    private static List<Path> candidateHdosProfileDirs() {
        LinkedHashSet<Path> dirs = new LinkedHashSet<>();
        for (Path home : candidateUserHomes()) {
            dirs.add(home.resolve("hdos").resolve("profiles2"));
        }
        addEnvDir(dirs, "APPDATA", Path.of("hdos", "profiles2").toString());
        addEnvDir(dirs, "appdata", Path.of("hdos", "profiles2").toString());
        addEnvDir(dirs, "LOCALAPPDATA", Path.of("hdos", "profiles2").toString());

        for (Path boltDataDir : candidateBoltDataDirs()) {
            dirs.add(boltDataDir.resolve("hdos").resolve("profiles2"));
        }
        return new ArrayList<>(dirs);
    }

    private static List<Path> candidateUserHomes() {
        LinkedHashSet<Path> homes = new LinkedHashSet<>();
        addPropertyPath(homes, "user.home");
        addEnvPath(homes, "HOME");
        addEnvPath(homes, "USERPROFILE");
        return new ArrayList<>(homes);
    }

    private static List<Path> candidateBoltDataDirs() {
        LinkedHashSet<Path> dirs = new LinkedHashSet<>();
        for (Path home : candidateUserHomes()) {
            dirs.add(home.resolve(".local").resolve("share").resolve(BOLT_DIR));
            dirs.add(home.resolve(".var").resolve("app").resolve(BOLT_FLATPAK_ID).resolve("data").resolve(BOLT_DIR));
        }

        addEnvDir(dirs, "XDG_DATA_HOME", BOLT_DIR);
        addEnvDir(dirs, "APPDATA", Path.of(BOLT_DIR, "data").toString());
        addEnvDir(dirs, "appdata", Path.of(BOLT_DIR, "data").toString());
        addEnvDir(dirs, "LOCALAPPDATA", Path.of(BOLT_DIR, "data").toString());
        return new ArrayList<>(dirs);
    }

    private static void addPropertyPath(Set<Path> paths, String property) {
        String value = System.getProperty(property);
        addPath(paths, value);
    }

    private static void addEnvPath(Set<Path> paths, String env) {
        String value = System.getenv(env);
        addPath(paths, value);
    }

    private static void addEnvDir(Set<Path> dirs, String env, String child) {
        String base = System.getenv(env);
        if (base == null || base.isBlank()) {
            return;
        }
        dirs.add(Path.of(base).resolve(child));
    }

    private static void addPath(Set<Path> paths, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        paths.add(Path.of(value));
    }

    private static void addIfFile(Set<Path> files, Path file) {
        if (Files.isRegularFile(file)) {
            files.add(file.toAbsolutePath().normalize());
        }
    }

    private static void addPropertiesIn(Set<Path> files, Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.properties")) {
            for (Path file : stream) {
                addIfFile(files, file);
            }
        } catch (IOException ignored) {
        }
    }

    private static Integer regionIdFromKey(String key) {
        if (key == null || !key.startsWith(GROUND_MARKER_PREFIX)) {
            return null;
        }
        String tail = key.substring(GROUND_MARKER_PREFIX.length());
        int regionIndex = tail.lastIndexOf(REGION_KEY_PREFIX);
        if (regionIndex < 0) {
            return null;
        }
        String suffix = tail.substring(regionIndex + REGION_KEY_PREFIX.length());
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String profileName(Client client, Path propertiesFile) {
        Path fileName = propertiesFile.getFileName();
        String raw = fileName == null ? propertiesFile.toString() : fileName.toString();
        if (raw.endsWith(".properties")) {
            raw = raw.substring(0, raw.length() - ".properties".length());
        }
        if (client == Client.RUNELITE && "settings".equals(raw)) {
            return "settings";
        }

        int dash = raw.lastIndexOf('-');
        if (dash > 0 && dash < raw.length() - 1) {
            String suffix = raw.substring(dash + 1);
            if (suffix.chars().allMatch(Character::isDigit)) {
                return raw.substring(0, dash) + " (" + suffix + ")";
            }
        }
        return raw;
    }

    public record MarkerSource(Client client, Path file, String profileName, List<GroundMarker> markers) {
        public MarkerSource {
            markers = markers == null ? List.of() : List.copyOf(markers);
        }

        public int markerCount() {
            return markers.size();
        }

        public String displayTitle() {
            return client.displayName() + " - " + profileName;
        }

        @Override
        public String toString() {
            return displayTitle() + " (" + markerCount() + " markers)";
        }
    }

    public static final class ImportResult {
        private final List<MarkerSource> sources = new ArrayList<>();
        private final List<GroundMarker> markers = new ArrayList<>();
        private final Map<Path, Integer> sourceCounts = new LinkedHashMap<>();
        private final List<String> errors = new ArrayList<>();

        private void addSource(MarkerSource source) {
            sources.add(source);
            markers.addAll(source.markers());
            sourceCounts.put(source.file(), source.markerCount());
        }

        private void addError(String error) {
            errors.add(error);
        }

        public List<MarkerSource> sources() {
            return List.copyOf(sources);
        }

        public List<GroundMarker> markers() {
            return List.copyOf(markers);
        }

        public Map<Path, Integer> sourceCounts() {
            return Map.copyOf(sourceCounts);
        }

        public List<String> errors() {
            return List.copyOf(errors);
        }

        public int markerCount() {
            return markers.size();
        }

        public int sourceCount() {
            return sources.size();
        }
    }
}
