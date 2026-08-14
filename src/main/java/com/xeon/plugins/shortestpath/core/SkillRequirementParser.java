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

import java.util.LinkedHashMap;
import java.util.Map;

final class SkillRequirementParser
{
	private SkillRequirementParser()
	{
	}

	static Map<String, Integer> parse(String value)
	{
		if (value == null || value.isBlank())
		{
			return Map.of();
		}

		Map<String, Integer> requirements = new LinkedHashMap<>();
		for (String rawRequirement : value.split(";"))
		{
			String requirement = rawRequirement.trim();
			if (requirement.isEmpty())
			{
				continue;
			}
			int split = firstWhitespace(requirement);
			if (split <= 0 || split >= requirement.length() - 1)
			{
				continue;
			}
			try
			{
				int level = Integer.parseInt(requirement.substring(0, split).trim());
				String skill = ProfileNames.canonicalSkill(requirement.substring(split + 1).trim());
				if (!skill.isBlank())
				{
					requirements.merge(skill, level, Math::max);
				}
			}
			catch (NumberFormatException ignored)
			{
				// Ignore malformed third-party data rows instead of failing the entire transport load.
			}
		}
		return requirements.isEmpty() ? Map.of() : Map.copyOf(requirements);
	}

	private static int firstWhitespace(String value)
	{
		for (int i = 0; i < value.length(); i++)
		{
			if (Character.isWhitespace(value.charAt(i)))
			{
				return i;
			}
		}
		return -1;
	}
}
