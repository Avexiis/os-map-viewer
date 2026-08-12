package com.xeon.plugins.shortestpath.core;

import java.util.LinkedHashSet;
import java.util.Set;

final class QuestRequirementParser {
    private QuestRequirementParser() {
    }

    static Set<String> parse(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }

        Set<String> quests = new LinkedHashSet<>();
        for (String rawQuest : value.split(";")) {
            String quest = ProfileNames.canonicalQuest(rawQuest);
            if (!quest.isBlank()) {
                quests.add(quest);
            }
        }
        return quests.isEmpty() ? Set.of() : Set.copyOf(quests);
    }
}
