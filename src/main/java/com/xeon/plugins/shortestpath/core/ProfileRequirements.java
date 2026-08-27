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

import com.xeon.util.wikisync.ProfileNames;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ProfileRequirements
{
	private final Set<String> skills;
	private final Set<String> quests;

	private ProfileRequirements(Set<String> skills, Set<String> quests)
	{
		this.skills = Set.copyOf(skills);
		this.quests = Set.copyOf(quests);
	}

	public static ProfileRequirements fromTransports(Transport[] transports)
	{
		Set<String> skills = new LinkedHashSet<>();
		Set<String> quests = new LinkedHashSet<>();
		if (transports != null)
		{
			for (Transport transport : transports)
			{
				skills.addAll(transport.getSkillRequirements().keySet());
				quests.addAll(transport.getQuestRequirements());
			}
		}
		return new ProfileRequirements(skills, quests);
	}

	public Set<String> skills()
	{
		return skills;
	}

	public Set<String> quests()
	{
		return quests;
	}

	public boolean needsTotalLevel()
	{
		return skills.contains(ProfileNames.TOTAL_LEVEL);
	}

	public boolean needsCombatLevel()
	{
		return skills.contains(ProfileNames.COMBAT_LEVEL);
	}

	public boolean needsQuestPoints()
	{
		return skills.contains(ProfileNames.QUEST_POINTS);
	}
}
