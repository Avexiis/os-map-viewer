package com.xeon.atlas;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RuneLiteCacheLocator {
    private static final String CACHE_DATA_FILE = "main_file_cache.dat2";
    private static final String BOLT_DIR = "bolt-launcher";
    private static final String BOLT_FLATPAK_ID = "com.adamcake.Bolt";
    private static final String RUNELITE_FLATPAK_ID = "net.runelite.RuneLite";

    private RuneLiteCacheLocator() {
    }

    public static Path locateCacheDirectory() {
        for (Path candidate : candidateCacheDirectories()) {
            if (isCacheDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean isCacheDirectory(Path directory) {
        return directory != null
                && Files.isDirectory(directory)
                && Files.isRegularFile(directory.resolve(CACHE_DATA_FILE));
    }

    public static List<Path> candidateCacheDirectories() {
        LinkedHashSet<Path> dirs = new LinkedHashSet<>();
        for (Path runeLiteDir : candidateRuneLiteDirs()) {
            addCacheCandidates(dirs, runeLiteDir);
        }
        for (Path boltDataDir : candidateBoltDataDirs()) {
            addCacheCandidates(dirs, boltDataDir);
            addCacheCandidates(dirs, boltDataDir.resolve(".runelite"));
        }
        return new ArrayList<>(dirs);
    }

    private static void addCacheCandidates(Set<Path> dirs, Path root) {
        if (root == null) {
            return;
        }
        addPath(dirs, root.resolve("jagexcache").resolve("oldschool").resolve("LIVE"));
    }

    private static List<Path> candidateRuneLiteDirs() {
        LinkedHashSet<Path> dirs = new LinkedHashSet<>();
        for (Path home : candidateUserHomes()) {
            addPath(dirs, home.resolve(".runelite"));
            addPath(dirs, home.resolve("Library").resolve("Application Support").resolve("RuneLite"));
            addPath(dirs, home.resolve(".config").resolve("runelite"));
            addPath(dirs, home.resolve("snap").resolve("runelite").resolve("current").resolve(".runelite"));
            addPath(dirs, home.resolve(".var").resolve("app").resolve(RUNELITE_FLATPAK_ID).resolve("config").resolve("RuneLite"));
            addPath(dirs, home.resolve(".var").resolve("app").resolve(RUNELITE_FLATPAK_ID).resolve("data").resolve("RuneLite"));
            addPath(dirs, home.resolve(".var").resolve("app").resolve(RUNELITE_FLATPAK_ID).resolve("data"));
        }

        addEnvDir(dirs, "APPDATA", "RuneLite");
        addEnvDir(dirs, "appdata", "RuneLite");
        addEnvDir(dirs, "LOCALAPPDATA", "RuneLite");
        addEnvDir(dirs, "XDG_CONFIG_HOME", "runelite");
        addEnvDir(dirs, "XDG_DATA_HOME", "RuneLite");
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
            addPath(dirs, home.resolve(".local").resolve("share").resolve(BOLT_DIR));
            addPath(dirs, home.resolve(".var").resolve("app").resolve(BOLT_FLATPAK_ID).resolve("data").resolve(BOLT_DIR));
        }

        addEnvDir(dirs, "XDG_DATA_HOME", BOLT_DIR);
        addEnvDir(dirs, "APPDATA", Path.of(BOLT_DIR, "data").toString());
        addEnvDir(dirs, "appdata", Path.of(BOLT_DIR, "data").toString());
        addEnvDir(dirs, "LOCALAPPDATA", Path.of(BOLT_DIR, "data").toString());
        return new ArrayList<>(dirs);
    }

    private static void addPropertyPath(Set<Path> paths, String property) {
        addPath(paths, System.getProperty(property));
    }

    private static void addEnvPath(Set<Path> paths, String env) {
        addPath(paths, System.getenv(env));
    }

    private static void addEnvDir(Set<Path> dirs, String env, String child) {
        String base = System.getenv(env);
        if (base == null || base.isBlank()) {
            return;
        }
        addPath(dirs, Path.of(base).resolve(child));
    }

    private static void addPath(Set<Path> paths, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        addPath(paths, Path.of(value));
    }

    private static void addPath(Set<Path> paths, Path path) {
        if (path == null) {
            return;
        }
        paths.add(path.toAbsolutePath().normalize());
    }
}
