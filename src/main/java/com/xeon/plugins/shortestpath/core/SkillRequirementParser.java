package com.xeon.plugins.shortestpath.core;

import java.util.LinkedHashMap;
import java.util.Map;

final class SkillRequirementParser {
    private SkillRequirementParser() {
    }

    static Map<String, Integer> parse(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }

        Map<String, Integer> requirements = new LinkedHashMap<>();
        for (String rawRequirement : value.split(";")) {
            String requirement = rawRequirement.trim();
            if (requirement.isEmpty()) {
                continue;
            }
            int split = firstWhitespace(requirement);
            if (split <= 0 || split >= requirement.length() - 1) {
                continue;
            }
            try {
                int level = Integer.parseInt(requirement.substring(0, split).trim());
                String skill = ProfileNames.canonicalSkill(requirement.substring(split + 1).trim());
                if (!skill.isBlank()) {
                    requirements.merge(skill, level, Math::max);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed third-party data rows instead of failing the entire transport load.
            }
        }
        return requirements.isEmpty() ? Map.of() : Map.copyOf(requirements);
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
