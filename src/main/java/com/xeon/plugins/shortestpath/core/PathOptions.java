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

import java.util.EnumSet;
import java.util.Set;

public record PathOptions(
	boolean includeTransports,
	boolean includeTeleports,
	boolean avoidWilderness,
	boolean includePoh,
	boolean avoidItemTeleports,
	Set<TransportType> enabledTransportTypes,
	long calculationCutoffMillis
)
{
	public PathOptions
	{
		enabledTransportTypes = copyTransportTypes(enabledTransportTypes);
	}

	public static PathOptions defaults()
	{
		return new PathOptions(true, true, false, true, false, defaultEnabledTransportTypes(), 3000L);
	}

	public static Set<TransportType> defaultEnabledTransportTypes()
	{
		EnumSet<TransportType> types = EnumSet.allOf(TransportType.class);
		types.removeIf(TransportType::isLeagueOnly);
		types.remove(TransportType.HOT_AIR_BALLOON);
		types.remove(TransportType.TELEPORTATION_MINIGAME);
		types.remove(TransportType.WILDERNESS_OBELISK);
		return types;
	}

	public static Set<TransportType> copyTransportTypes(Set<TransportType> types)
	{
		if (types == null)
		{
			return defaultEnabledTransportTypes();
		}
		if (types.isEmpty())
		{
			return EnumSet.noneOf(TransportType.class);
		}
		return EnumSet.copyOf(types);
	}
}
