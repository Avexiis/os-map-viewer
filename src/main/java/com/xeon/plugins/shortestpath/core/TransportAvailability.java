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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TransportAvailability
{
	public static final Transport[] EMPTY_TRANSPORTS = new Transport[0];

	private final PrimitiveIntHashMap<Transport[]> transportsPacked;
	private final PrimitiveIntHashMap<Transport[]> displayTransports;
	private final Transport[] usableTeleports;

	private TransportAvailability(PrimitiveIntHashMap<Transport[]> transportsPacked,
	                              PrimitiveIntHashMap<Transport[]> displayTransports,
	                              Transport[] usableTeleports)
	{
		this.transportsPacked = transportsPacked;
		this.displayTransports = displayTransports;
		this.usableTeleports = usableTeleports;
	}

	public PrimitiveIntHashMap<Transport[]> getTransportsPacked()
	{
		return transportsPacked;
	}

	public PrimitiveIntHashMap<Transport[]> getDisplayTransports()
	{
		return displayTransports;
	}

	public Transport[] getUsableTeleports()
	{
		return usableTeleports;
	}

	public Transport[] getTransportsAt(int origin)
	{
		return displayTransports.getOrDefault(origin, EMPTY_TRANSPORTS);
	}

	static final class Builder
	{
		private final Map<Integer, Set<Transport>> transportsByOrigin;
		private final Set<Transport> usableTeleports;
		private final Set<Integer> pohOrigins = new HashSet<>();

		Builder(int expectedTransportCount)
		{
			transportsByOrigin = new HashMap<>(Math.max(1, expectedTransportCount / 2));
			usableTeleports = new HashSet<>(Math.max(1, expectedTransportCount / 20));
		}

		void add(Transport transport)
		{
			if (transport.getOrigin() == WorldPointUtil.UNDEFINED)
			{
				usableTeleports.add(transport);
				return;
			}
			transportsByOrigin.computeIfAbsent(transport.getOrigin(), ignored -> new HashSet<>()).add(transport);
		}

		void remapPohTransports()
		{
			int pohLanding = WorldPointUtil.packWorldPoint(1923, 5709, 0);
			Set<Transport> pohTransports = new HashSet<>();
			for (Map.Entry<Integer, Set<Transport>> entry : transportsByOrigin.entrySet())
			{
				int origin = entry.getKey();
				if (PathfinderConfig.isInsidePoh(WorldPointUtil.unpackWorldX(origin), WorldPointUtil.unpackWorldY(origin)))
				{
					pohTransports.addAll(entry.getValue());
					pohOrigins.add(origin);
				}
			}
			if (!pohTransports.isEmpty())
			{
				transportsByOrigin.computeIfAbsent(pohLanding, ignored -> new HashSet<>()).addAll(pohTransports);
			}
		}

		TransportAvailability build()
		{
			int expected = Math.max(1, transportsByOrigin.size());
			PrimitiveIntHashMap<Transport[]> packed = new PrimitiveIntHashMap<>(expected);
			PrimitiveIntHashMap<Transport[]> display = new PrimitiveIntHashMap<>(expected);
			for (Map.Entry<Integer, Set<Transport>> entry : transportsByOrigin.entrySet())
			{
				int origin = entry.getKey();
				Transport[] transports = entry.getValue().toArray(EMPTY_TRANSPORTS);
				packed.put(origin, transports);
				if (!pohOrigins.contains(origin))
				{
					display.put(origin, transports);
				}
			}
			return new TransportAvailability(packed, display, usableTeleports.toArray(EMPTY_TRANSPORTS));
		}
	}
}
