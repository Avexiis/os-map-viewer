package com.xeon.plugins.shortestpath.core;

import java.util.Map;

final class TransportRecord {
    static final String ORIGIN = "Origin";
    static final String DESTINATION = "Destination";
    static final String SKILLS = "Skills";
    static final String QUESTS = "Quests";
    static final String VARBITS = "Varbits";
    static final String VAR_PLAYERS = "VarPlayers";
    static final String DURATION = "Duration";
    static final String DISPLAY_INFO = "Display info";
    static final String CONSUMABLE = "Consumable";
    static final String WILDERNESS_LEVEL = "Wilderness level";
    static final String OBJECT_INFO = "menuOption menuTarget objectID";

    private final Map<String, String> fields;

    TransportRecord(Map<String, String> fields) {
        this.fields = Map.copyOf(fields);
    }

    String get(String fieldName) {
        return fields.get(fieldName);
    }

    boolean hasKey(String fieldName) {
        return fields.containsKey(fieldName);
    }
}
