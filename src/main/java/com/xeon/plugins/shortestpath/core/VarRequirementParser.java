/*
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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

final class VarRequirementParser
{
	private VarRequirementParser()
	{
	}

	static Set<VarRequirement> parseVarbits(String value)
	{
		return parse(value, VarRequirement.VarType.VARBIT);
	}

	static Set<VarRequirement> parseVarPlayers(String value)
	{
		return parse(value, VarRequirement.VarType.VARPLAYER);
	}

	private static Set<VarRequirement> parse(String value, VarRequirement.VarType type)
	{
		if (value == null || value.isBlank())
		{
			return Set.of();
		}

		Set<VarRequirement> requirements = new LinkedHashSet<>();
		for (String rawRequirement : value.split(";"))
		{
			String requirement = rawRequirement.trim();
			if (requirement.isEmpty())
			{
				continue;
			}
			VarRequirement parsed = parseRequirement(requirement, type);
			if (parsed != null)
			{
				requirements.add(parsed);
			}
		}
		return requirements.isEmpty() ? Set.of() : Set.copyOf(requirements);
	}

	private static VarRequirement parseRequirement(String requirement, VarRequirement.VarType type)
	{
		for (VarRequirement.CheckType checkType : VarRequirement.CheckType.values())
		{
			String[] parts = requirement.split(Pattern.quote(checkType.code()));
			if (parts.length != 2)
			{
				continue;
			}
			try
			{
				int id = Integer.parseInt(parts[0].trim());
				int value = Integer.parseInt(parts[1].trim());
				return new VarRequirement(type, id, value, checkType);
			}
			catch (NumberFormatException ignored)
			{
				return null;
			}
		}
		return null;
	}
}
