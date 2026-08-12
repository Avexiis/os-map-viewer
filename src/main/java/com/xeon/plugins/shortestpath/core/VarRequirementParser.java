package com.xeon.plugins.shortestpath.core;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

final class VarRequirementParser {
    private VarRequirementParser() {
    }

    static Set<VarRequirement> parseVarbits(String value) {
        return parse(value, VarRequirement.VarType.VARBIT);
    }

    static Set<VarRequirement> parseVarPlayers(String value) {
        return parse(value, VarRequirement.VarType.VARPLAYER);
    }

    private static Set<VarRequirement> parse(String value, VarRequirement.VarType type) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }

        Set<VarRequirement> requirements = new LinkedHashSet<>();
        for (String rawRequirement : value.split(";")) {
            String requirement = rawRequirement.trim();
            if (requirement.isEmpty()) {
                continue;
            }
            VarRequirement parsed = parseRequirement(requirement, type);
            if (parsed != null) {
                requirements.add(parsed);
            }
        }
        return requirements.isEmpty() ? Set.of() : Set.copyOf(requirements);
    }

    private static VarRequirement parseRequirement(String requirement, VarRequirement.VarType type) {
        for (VarRequirement.CheckType checkType : VarRequirement.CheckType.values()) {
            String[] parts = requirement.split(Pattern.quote(checkType.code()));
            if (parts.length != 2) {
                continue;
            }
            try {
                int id = Integer.parseInt(parts[0].trim());
                int value = Integer.parseInt(parts[1].trim());
                return new VarRequirement(type, id, value, checkType);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
