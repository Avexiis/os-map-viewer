package com.xeon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ConfigManager {
    public static final String CORE_NAMESPACE = "core";

    private static final String DIR_NAME = "os-map-viewer";
    private static final String FILE_NAME = "config.json";

    private final Path path;
    private final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .create();
    private final Gson prettyGson = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();
    private JsonObject root = new JsonObject();

    private ConfigManager(Path path) {
        this.path = path;
    }

    public static ConfigManager loadDefault() {
        return load(defaultFile());
    }

    public static ConfigManager load(Path path) {
        ConfigManager manager = new ConfigManager(path);
        manager.reload();
        return manager;
    }

    public static Path defaultDirectory() {
        return Path.of(System.getProperty("user.home"), DIR_NAME);
    }

    public static Path defaultFile() {
        return defaultDirectory().resolve(FILE_NAME);
    }

    public synchronized void reload() {
        root = new JsonObject();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonObject()) {
                root = parsed.getAsJsonObject();
            }
        } catch (IOException | JsonParseException ignored) {
            root = new JsonObject();
        }
    }

    public synchronized Path directory() {
        Path parent = path.getParent();
        return parent == null ? Path.of("") : parent;
    }

    public synchronized Path file() {
        return path;
    }

    public Gson gson() {
        return gson;
    }

    public PluginConfig scope(String namespace) {
        return new PluginConfig(this, requireName(namespace, "namespace"));
    }

    public synchronized Set<String> namespaces() {
        return new LinkedHashSet<>(root.keySet());
    }

    public synchronized Set<String> keys(String namespace) {
        JsonObject group = namespaceObject(namespace, false);
        return group == null ? Set.of() : new LinkedHashSet<>(group.keySet());
    }

    public synchronized boolean contains(String namespace, String key) {
        JsonObject group = namespaceObject(namespace, false);
        return group != null && group.has(requireName(key, "key"));
    }

    public synchronized JsonElement getElement(String namespace, String key) {
        JsonObject group = namespaceObject(namespace, false);
        if (group == null) {
            return null;
        }
        JsonElement element = group.get(requireName(key, "key"));
        return element == null ? null : element.deepCopy();
    }

    public synchronized void setElement(String namespace, String key, JsonElement value) {
        JsonObject group = namespaceObject(namespace, true);
        group.add(requireName(key, "key"), value == null ? null : value.deepCopy());
        save();
    }

    public synchronized void remove(String namespace, String key) {
        JsonObject group = namespaceObject(namespace, false);
        if (group != null && group.remove(requireName(key, "key")) != null) {
            if (group.size() == 0) {
                root.remove(requireName(namespace, "namespace"));
            }
            save();
        }
    }

    public synchronized <T> T getObject(String namespace, String key, Class<T> type, T defaultValue) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        JsonElement element = getElement(namespace, key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        try {
            T value = gson.fromJson(element, type);
            return value == null ? defaultValue : value;
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    public synchronized void setObject(String namespace, String key, Object value) {
        if (value == null) {
            remove(namespace, key);
            return;
        }
        setElement(namespace, key, gson.toJsonTree(value));
    }

    public synchronized String getString(String namespace, String key, String defaultValue) {
        JsonElement element = getElement(namespace, key);
        if (element == null || !element.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return element.getAsString();
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    public synchronized void setString(String namespace, String key, String value) {
        setObject(namespace, key, value);
    }

    public synchronized boolean getBoolean(String namespace, String key, boolean defaultValue) {
        JsonElement element = getElement(namespace, key);
        if (element == null || !element.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    public synchronized void setBoolean(String namespace, String key, boolean value) {
        setObject(namespace, key, value);
    }

    public synchronized int getInt(String namespace, String key, int defaultValue) {
        JsonElement element = getElement(namespace, key);
        if (element == null || !element.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    public synchronized void setInt(String namespace, String key, int value) {
        setObject(namespace, key, value);
    }

    public synchronized double getDouble(String namespace, String key, double defaultValue) {
        JsonElement element = getElement(namespace, key);
        if (element == null || !element.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return element.getAsDouble();
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    public synchronized void setDouble(String namespace, String key, double value) {
        setObject(namespace, key, value);
    }

    private void save() {
        try {
            Path target = path.toAbsolutePath();
            Path parent = target.getParent();
            if (parent == null) {
                return;
            }
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, "config-", ".json.tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                prettyGson.toJson(root, writer);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }

    private JsonObject namespaceObject(String namespace, boolean create) {
        String cleanNamespace = requireName(namespace, "namespace");
        JsonElement existing = root.get(cleanNamespace);
        if (existing != null && existing.isJsonObject()) {
            return existing.getAsJsonObject();
        }
        if (!create) {
            return null;
        }
        JsonObject group = new JsonObject();
        root.add(cleanNamespace, group);
        return group;
    }

    private static String requireName(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
