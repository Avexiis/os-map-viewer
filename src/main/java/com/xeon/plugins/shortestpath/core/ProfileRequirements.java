package com.xeon.plugins.shortestpath.core;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ProfileRequirements {
    private final Set<String> skills;
    private final Set<String> quests;

    private ProfileRequirements(Set<String> skills, Set<String> quests) {
        this.skills = Set.copyOf(skills);
        this.quests = Set.copyOf(quests);
    }

    public static ProfileRequirements fromTransports(Transport[] transports) {
        Set<String> skills = new LinkedHashSet<>();
        Set<String> quests = new LinkedHashSet<>();
        if (transports != null) {
            for (Transport transport : transports) {
                skills.addAll(transport.getSkillRequirements().keySet());
                quests.addAll(transport.getQuestRequirements());
            }
        }
        return new ProfileRequirements(skills, quests);
    }

    public Set<String> skills() {
        return skills;
    }

    public Set<String> quests() {
        return quests;
    }

    public boolean needsTotalLevel() {
        return skills.contains(ProfileNames.TOTAL_LEVEL);
    }

    public boolean needsCombatLevel() {
        return skills.contains(ProfileNames.COMBAT_LEVEL);
    }

    public boolean needsQuestPoints() {
        return skills.contains(ProfileNames.QUEST_POINTS);
    }
}
