package com.xeon.plugins.shortestpath.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class Transport {
    public static final int UNDEFINED_ORIGIN = WorldPointUtil.UNDEFINED;
    public static final int UNDEFINED_DESTINATION = WorldPointUtil.UNDEFINED;
    public static final int LOCATION_PERMUTATION = WorldPointUtil.packWorldPoint(-1, -1, 1);

    private int origin = UNDEFINED_ORIGIN;
    private int destination = UNDEFINED_DESTINATION;
    private TransportType type;
    private int duration;
    private String displayInfo;
    private boolean consumable;
    private int maxWildernessLevel = -1;
    private String objectInfo;
    private Map<String, Integer> skillRequirements = Map.of();
    private Set<String> questRequirements = Set.of();
    private Set<VarRequirement> varRequirements = Set.of();
    private TeleportItem teleportItem;

    private Transport() {
    }

    Transport(Transport origin, Transport destination) {
        this.origin = origin.origin;
        this.destination = destination.destination;
        this.type = origin.type;
        this.duration = Math.max(origin.duration, destination.duration);
        this.displayInfo = destination.displayInfo;
        this.consumable = origin.consumable || destination.consumable;
        this.maxWildernessLevel = Math.max(origin.maxWildernessLevel, destination.maxWildernessLevel);
        this.objectInfo = origin.objectInfo;
        this.skillRequirements = mergeSkillRequirements(origin.skillRequirements, destination.skillRequirements);
        this.questRequirements = mergeSets(origin.questRequirements, destination.questRequirements);
        this.varRequirements = mergeSets(origin.varRequirements, destination.varRequirements);
        this.teleportItem = origin.teleportItem != null ? origin.teleportItem : destination.teleportItem;
    }

    static Transport fromRecord(TransportRecord record, TransportType type) {
        Transport transport = new Transport();
        transport.type = type;
        if (record.hasKey(TransportRecord.ORIGIN)) {
            transport.origin = parseWorldPoint(record.get(TransportRecord.ORIGIN));
        }
        if (record.hasKey(TransportRecord.DESTINATION)) {
            transport.destination = parseWorldPoint(record.get(TransportRecord.DESTINATION));
        }
        transport.duration = parseInt(record.get(TransportRecord.DURATION), 0);
        transport.displayInfo = blankToNull(record.get(TransportRecord.DISPLAY_INFO));
        transport.consumable = parseBoolean(record.get(TransportRecord.CONSUMABLE));
        transport.maxWildernessLevel = parseInt(record.get(TransportRecord.WILDERNESS_LEVEL), -1);
        transport.objectInfo = blankToNull(record.get(TransportRecord.OBJECT_INFO));
        transport.skillRequirements = SkillRequirementParser.parse(record.get(TransportRecord.SKILLS));
        transport.questRequirements = QuestRequirementParser.parse(record.get(TransportRecord.QUESTS));
        transport.varRequirements = mergeSets(
                VarRequirementParser.parseVarbits(record.get(TransportRecord.VARBITS)),
                VarRequirementParser.parseVarPlayers(record.get(TransportRecord.VAR_PLAYERS))
        );
        if (type == TransportType.TELEPORTATION_ITEM) {
            transport.teleportItem = TeleportItem.fromDisplayInfo(transport.displayInfo);
        }
        if (transport.type != null && transport.type.isTeleport()) {
            transport.duration = Math.max(transport.duration, 1);
        }
        return transport;
    }

    public int getOrigin() {
        return origin;
    }

    public int getDestination() {
        return destination;
    }

    void setDestination(int destination) {
        this.destination = destination;
    }

    public TransportType getType() {
        return type;
    }

    public int getDuration() {
        return duration;
    }

    public String getDisplayInfo() {
        return displayInfo;
    }

    public boolean isConsumable() {
        return consumable;
    }

    public String getObjectInfo() {
        return objectInfo;
    }

    public Map<String, Integer> getSkillRequirements() {
        return skillRequirements;
    }

    public Set<String> getQuestRequirements() {
        return questRequirements;
    }

    public Set<VarRequirement> getVarRequirements() {
        return varRequirements;
    }

    public boolean hasProfileRequirements() {
        return !skillRequirements.isEmpty() || !questRequirements.isEmpty();
    }

    public TeleportItem getTeleportItem() {
        return teleportItem;
    }

    public boolean isUsableAtWildernessLevel(int wildernessLevel) {
        return !type.isTeleport() || wildernessLevel <= maxWildernessLevel;
    }

    public boolean isGlobal() {
        return origin == UNDEFINED_ORIGIN;
    }

    public boolean isLeagueSpecific() {
        return (type != null && type.isLeagueOnly()) || isLeagueSpecificDisplayInfo(displayInfo);
    }

    static boolean isLeagueSpecificDisplayInfo(String displayInfo) {
        if (displayInfo == null || displayInfo.isBlank()) {
            return false;
        }
        return displayInfo.regionMatches(true, 0, "Banker's Briefcase", 0, "Banker's Briefcase".length());
    }

    private static int parseWorldPoint(String value) {
        if (value == null || value.isEmpty()) {
            return LOCATION_PERMUTATION;
        }
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 3) {
            return LOCATION_PERMUTATION;
        }
        return WorldPointUtil.packWorldPoint(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        );
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value) {
        return "T".equals(value) || "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Map<String, Integer> mergeSkillRequirements(Map<String, Integer> first, Map<String, Integer> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return Map.of();
        }
        Map<String, Integer> merged = new LinkedHashMap<>();
        if (first != null) {
            merged.putAll(first);
        }
        if (second != null) {
            second.forEach((skill, level) -> merged.merge(skill, level, Math::max));
        }
        return Map.copyOf(merged);
    }

    private static <T> Set<T> mergeSets(Set<T> first, Set<T> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return Set.of();
        }
        Set<T> merged = new LinkedHashSet<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        return Set.copyOf(merged);
    }

    @Override
    public String toString() {
        return type + " " + label() + " "
                + WorldPointUtil.unpackWorldX(origin) + "," + WorldPointUtil.unpackWorldY(origin)
                + " -> " + WorldPointUtil.unpackWorldX(destination) + "," + WorldPointUtil.unpackWorldY(destination);
    }

    public String label() {
        if (displayInfo != null && !displayInfo.isBlank()) {
            return displayInfo;
        }
        if (objectInfo != null && !objectInfo.isBlank()) {
            return objectInfo;
        }
        return type == null ? "Transport" : transportTypeLabel(type);
    }

    private static String transportTypeLabel(TransportType type) {
        String text = type.name().toLowerCase().replace('_', ' ');
        StringBuilder out = new StringBuilder(text.length());
        boolean cap = true;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                cap = true;
                out.append(ch);
            } else if (cap) {
                out.append(Character.toUpperCase(ch));
                cap = false;
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }
}
