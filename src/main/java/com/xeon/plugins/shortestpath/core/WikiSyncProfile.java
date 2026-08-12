package com.xeon.plugins.shortestpath.core;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class WikiSyncProfile {
    private String username;
    private String profileType;
    private String fetchedAt;
    private Map<String, Integer> levels = new LinkedHashMap<>();
    private Set<String> completedQuests = new LinkedHashSet<>();

    public WikiSyncProfile() {
        this("", "STANDARD", Instant.EPOCH, Map.of(), Set.of());
    }

    public WikiSyncProfile(String username, String profileType, Instant fetchedAt,
                           Map<String, Integer> levels, Set<String> completedQuests) {
        this.username = username == null ? "" : username;
        this.profileType = profileType == null || profileType.isBlank() ? "STANDARD" : profileType;
        this.fetchedAt = (fetchedAt == null ? Instant.now() : fetchedAt).toString();
        this.levels = normalizeLevels(levels);
        this.completedQuests = normalizeQuests(completedQuests);
    }

    public String username() {
        return username == null ? "" : username;
    }

    public String profileType() {
        return profileType == null || profileType.isBlank() ? "STANDARD" : profileType;
    }

    public Instant fetchedAt() {
        try {
            return Instant.parse(fetchedAt);
        } catch (RuntimeException ex) {
            return Instant.EPOCH;
        }
    }

    public Map<String, Integer> levels() {
        return Map.copyOf(levels == null ? Map.of() : levels);
    }

    public Set<String> completedQuests() {
        return Set.copyOf(completedQuests == null ? Set.of() : completedQuests);
    }

    public boolean canUse(Transport transport) {
        if (transport == null) {
            return true;
        }
        for (Map.Entry<String, Integer> requirement : transport.getSkillRequirements().entrySet()) {
            if (!hasLevel(requirement.getKey(), requirement.getValue())) {
                return false;
            }
        }
        for (String quest : transport.getQuestRequirements()) {
            if (!hasCompletedQuest(quest)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasLevel(String skill, int requiredLevel) {
        Integer level = levels == null ? null : levels.get(ProfileNames.canonicalSkill(skill));
        return level != null && level >= requiredLevel;
    }

    public boolean hasCompletedQuest(String quest) {
        return completedQuests != null && completedQuests.contains(ProfileNames.canonicalQuest(quest));
    }

    public boolean hasData() {
        return (levels != null && !levels.isEmpty()) || (completedQuests != null && !completedQuests.isEmpty());
    }

    public String summary() {
        int levelCount = skillLevelCount();
        int questCount = completedQuests == null ? 0 : completedQuests.size();
        return username() + ": " + levelCount + " skill" + (levelCount == 1 ? "" : "s")
                + ", " + questCount + " completed quest" + (questCount == 1 ? "" : "s");
    }

    private int skillLevelCount() {
        if (levels == null || levels.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String skill : levels.keySet()) {
            if (ProfileNames.isRegularSkill(skill)) {
                count++;
            }
        }
        return count;
    }

    private static Map<String, Integer> normalizeLevels(Map<String, Integer> input) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        if (input != null) {
            input.forEach((skill, level) -> {
                if (level != null) {
                    String key = ProfileNames.canonicalSkill(skill);
                    if (!key.isBlank()) {
                        normalized.merge(key, level, Math::max);
                    }
                }
            });
        }
        return normalized;
    }

    private static Set<String> normalizeQuests(Set<String> input) {
        Set<String> normalized = new LinkedHashSet<>();
        if (input != null) {
            for (String quest : input) {
                String key = ProfileNames.canonicalQuest(quest);
                if (!key.isBlank()) {
                    normalized.add(key);
                }
            }
        }
        return normalized;
    }
}
