package com.xeon.view;

final class MapIconArea {
    final String displayName;
    final int areaId;
    final int spriteId;
    final int worldX;
    final int worldY;
    final int plane;

    MapIconArea(String displayName, int areaId, int spriteId, int worldX, int worldY, int plane) {
        this.displayName = displayName;
        this.areaId = areaId;
        this.spriteId = spriteId;
        this.worldX = worldX;
        this.worldY = worldY;
        this.plane = plane;
    }
}
