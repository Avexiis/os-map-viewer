package com.xeon.model;

public final class MapArea {
    private final String displayName;
    private final String rawName;
    private final String objectName;
    private final String source;
    private final int areaId;
    private final int worldX;
    private final int worldY;
    private final int plane;
    private final String searchText;

    public MapArea(String displayName, String rawName, String objectName, String source,
                   int areaId, int worldX, int worldY, int plane) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        this.displayName = displayName;
        this.rawName = rawName;
        this.objectName = objectName;
        this.source = source == null ? "" : source;
        this.areaId = areaId;
        this.worldX = worldX;
        this.worldY = worldY;
        this.plane = plane;
        this.searchText = buildSearchText(displayName, rawName, objectName, source, areaId, worldX, worldY, plane);
    }

    public String displayName() {
        return displayName;
    }

    public String rawName() {
        return rawName;
    }

    public String objectName() {
        return objectName;
    }

    public String source() {
        return source;
    }

    public int areaId() {
        return areaId;
    }

    public int worldX() {
        return worldX;
    }

    public int worldY() {
        return worldY;
    }

    public int plane() {
        return plane;
    }

    public Tile tile() {
        return new Tile(worldX, worldY, plane);
    }

    public String searchText() {
        return searchText;
    }

    public String dropdownLabel() {
        return displayName + "  (" + worldX + ", " + worldY + ", " + plane + ")";
    }

    @Override
    public String toString() {
        return displayName;
    }

    private static String buildSearchText(String displayName, String rawName, String objectName, String source,
                                          int areaId, int worldX, int worldY, int plane) {
        StringBuilder out = new StringBuilder();
        append(out, displayName);
        append(out, rawName);
        append(out, objectName);
        append(out, source);
        append(out, String.valueOf(areaId));
        append(out, worldX + "," + worldY + "," + plane);
        append(out, worldX + " " + worldY + " " + plane);
        return out.toString();
    }

    private static void append(StringBuilder out, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!out.isEmpty()) {
            out.append(' ');
        }
        out.append(value);
    }
}
