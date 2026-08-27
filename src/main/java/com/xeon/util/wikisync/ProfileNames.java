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
package com.xeon.util.wikisync;

import java.util.Locale;
import java.util.Set;

public final class ProfileNames
{
	public static final String TOTAL_LEVEL = "total";
	public static final String COMBAT_LEVEL = "combat";
	public static final String QUEST_POINTS = "quest";

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

	private ProfileNames()
	{
	}

	public static String canonicalSkill(String name)
	{
		String clean = canonical(name);
		return switch (clean)
		{
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

	public static String canonicalQuest(String name)
	{
		return canonical(name);
	}

	public static boolean isRegularSkill(String skill)
	{
		return REGULAR_SKILLS.contains(skill);
	}

	public static Set<String> regularSkills()
	{
		return REGULAR_SKILLS;
	}

	private static String canonical(String name)
	{
		if (name == null)
		{
			return "";
		}
		String normalized = name.trim().toLowerCase(Locale.ROOT)
			.replace("&", "and")
			.replace("'", "");
		StringBuilder out = new StringBuilder(normalized.length());
		for (int i = 0; i < normalized.length(); i++)
		{
			char ch = normalized.charAt(i);
			if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9'))
			{
				out.append(ch);
			}
		}
		return out.toString();
	}
}
