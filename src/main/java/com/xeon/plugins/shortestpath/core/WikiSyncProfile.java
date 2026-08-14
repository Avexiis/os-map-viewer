/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.

 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.xeon.plugins.shortestpath.core;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class WikiSyncProfile
{
	private String username;
	private String profileType;
	private String fetchedAt;
	private Map<String, Integer> levels = new LinkedHashMap<>();
	private Set<String> completedQuests = new LinkedHashSet<>();

	public WikiSyncProfile()
	{
		this("", "STANDARD", Instant.EPOCH, Map.of(), Set.of());
	}

	public WikiSyncProfile(String username, String profileType, Instant fetchedAt,
	                       Map<String, Integer> levels, Set<String> completedQuests)
	{
		this.username = username == null ? "" : username;
		this.profileType = profileType == null || profileType.isBlank() ? "STANDARD" : profileType;
		this.fetchedAt = (fetchedAt == null ? Instant.now() : fetchedAt).toString();
		this.levels = normalizeLevels(levels);
		this.completedQuests = normalizeQuests(completedQuests);
	}

	public String username()
	{
		return username == null ? "" : username;
	}

	public String profileType()
	{
		return profileType == null || profileType.isBlank() ? "STANDARD" : profileType;
	}

	public Instant fetchedAt()
	{
		try
		{
			return Instant.parse(fetchedAt);
		}
		catch (RuntimeException ex)
		{
			return Instant.EPOCH;
		}
	}

	public Map<String, Integer> levels()
	{
		return Map.copyOf(levels == null ? Map.of() : levels);
	}

	public Set<String> completedQuests()
	{
		return Set.copyOf(completedQuests == null ? Set.of() : completedQuests);
	}

	public boolean canUse(Transport transport)
	{
		if (transport == null)
		{
			return true;
		}
		for (Map.Entry<String, Integer> requirement : transport.getSkillRequirements().entrySet())
		{
			if (!hasLevel(requirement.getKey(), requirement.getValue()))
			{
				return false;
			}
		}
		for (String quest : transport.getQuestRequirements())
		{
			if (!hasCompletedQuest(quest))
			{
				return false;
			}
		}
		return true;
	}

	public boolean hasLevel(String skill, int requiredLevel)
	{
		Integer level = levels == null ? null : levels.get(ProfileNames.canonicalSkill(skill));
		return level != null && level >= requiredLevel;
	}

	public boolean hasCompletedQuest(String quest)
	{
		return completedQuests != null && completedQuests.contains(ProfileNames.canonicalQuest(quest));
	}

	public boolean hasData()
	{
		return (levels != null && !levels.isEmpty()) || (completedQuests != null && !completedQuests.isEmpty());
	}

	public String summary()
	{
		int levelCount = skillLevelCount();
		int questCount = completedQuests == null ? 0 : completedQuests.size();
		return username() + ": " + levelCount + " skill" + (levelCount == 1 ? "" : "s")
			+ ", " + questCount + " completed quest" + (questCount == 1 ? "" : "s");
	}

	private int skillLevelCount()
	{
		if (levels == null || levels.isEmpty())
		{
			return 0;
		}
		int count = 0;
		for (String skill : levels.keySet())
		{
			if (ProfileNames.isRegularSkill(skill))
			{
				count++;
			}
		}
		return count;
	}

	private static Map<String, Integer> normalizeLevels(Map<String, Integer> input)
	{
		Map<String, Integer> normalized = new LinkedHashMap<>();
		if (input != null)
		{
			input.forEach((skill, level) -> {
				if (level != null)
				{
					String key = ProfileNames.canonicalSkill(skill);
					if (!key.isBlank())
					{
						normalized.merge(key, level, Math::max);
					}
				}
			});
		}
		return normalized;
	}

	private static Set<String> normalizeQuests(Set<String> input)
	{
		Set<String> normalized = new LinkedHashSet<>();
		if (input != null)
		{
			for (String quest : input)
			{
				String key = ProfileNames.canonicalQuest(quest);
				if (!key.isBlank())
				{
					normalized.add(key);
				}
			}
		}
		return normalized;
	}
}
