package com.xeon.plugins.shortestpath.core;

enum AbstractNodeKind {
    GLOBAL_TELEPORTS_OVER_30,
    GLOBAL_TELEPORTS_OVER_20,
    GLOBAL_TELEPORTS_OVER_0,
    GLOBAL_TELEPORTS_NORMAL;

    static AbstractNodeKind fromWildernessLevel(int wildernessLevel) {
        if (wildernessLevel > 30) {
            return GLOBAL_TELEPORTS_OVER_30;
        }
        if (wildernessLevel > 20) {
            return GLOBAL_TELEPORTS_OVER_20;
        }
        if (wildernessLevel > 0) {
            return GLOBAL_TELEPORTS_OVER_0;
        }
        return GLOBAL_TELEPORTS_NORMAL;
    }

    int maxWildernessLevel() {
        return switch (this) {
            case GLOBAL_TELEPORTS_OVER_30 -> 31;
            case GLOBAL_TELEPORTS_OVER_20 -> 30;
            case GLOBAL_TELEPORTS_OVER_0 -> 20;
            case GLOBAL_TELEPORTS_NORMAL -> 0;
        };
    }
}
