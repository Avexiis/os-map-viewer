package com.xeon.plugins.shortestpath.core;

import java.util.Locale;
import java.util.Set;

final class ProfileNames {
    static final String TOTAL_LEVEL = "total";
    static final String COMBAT_LEVEL = "combat";
    static final String QUEST_POINTS = "quest";

    private static final Set<String> REGULAR_SKILLS = Set.of(
            "attack",
            "strength",
            "defence",
            "ranged",
            "prayer",
            "magic",
            "runecraft",
            "hitpoints",
            "crafting",
            "mining",
            "smithing",
            "fishing",
            "cooking",
            "firemaking",
            "woodcutting",
            "agility",
            "herblore",
            "thieving",
            "fletching",
            "slayer",
            "farming",
            "construction",
            "hunter",
            "sailing"
    );

    private ProfileNames() {
    }

    static String canonicalSkill(String name) {
        String clean = canonical(name);
        return switch (clean) {
            case "defense" -> "defence";
            case "runecrafting" -> "runecraft";
            case "hp" -> "hitpoints";
            case "sail" -> "sailing";
            case "overall", "totallevel" -> TOTAL_LEVEL;
            case "combatlevel" -> COMBAT_LEVEL;
            case "questpoints", "qp" -> QUEST_POINTS;
            default -> clean;
        };
    }

    static String canonicalQuest(String name) {
        return canonical(name);
    }

    static boolean isRegularSkill(String skill) {
        return REGULAR_SKILLS.contains(skill);
    }

    static Set<String> regularSkills() {
        return REGULAR_SKILLS;
    }

    private static String canonical(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT)
                .replace("&", "and")
                .replace("'", "");
        StringBuilder out = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                out.append(ch);
            }
        }
        return out.toString();
    }
}
