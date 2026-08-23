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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TransportLoader
{
	private static final TsvParser TSV_PARSER = new TsvParser();

	private TransportLoader()
	{
	}

	public static HashMap<Integer, Set<Transport>> loadAllFromResources()
	{
		HashMap<Integer, Set<Transport>> transports = new HashMap<>();
		for (TransportType type : TransportType.values())
		{
			if (type.hasResourcePath() && !type.isLeagueOnly())
			{
				addTransports(transports, type.getResourcePath(), type, type.getRadiusThreshold());
			}
		}
		return transports;
	}

	private static void addTransports(Map<Integer, Set<Transport>> transports, String path,
	                                  TransportType transportType, int radiusThreshold)
	{
		try
		{
			byte[] bytes = Util.readAllBytes(Objects.requireNonNull(
				TransportLoader.class.getResourceAsStream(path),
				"Missing resource " + path
			));
			addTransportsFromContents(transports, new String(bytes, StandardCharsets.UTF_8), transportType, radiusThreshold);
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Unable to load " + path, e);
		}
	}

	static void addTransportsFromContents(Map<Integer, Set<Transport>> transports, String contents,
	                                      TransportType transportType, int radiusThreshold)
	{
		Set<Transport> newTransports = new HashSet<>();
		for (TransportRecord record : TSV_PARSER.parse(contents))
		{
			if (transportType.isLeagueOnly()
				|| Transport.isLeagueSpecificDisplayInfo(record.get(TransportRecord.DISPLAY_INFO)))
			{
				continue;
			}
			newTransports.add(Transport.fromRecord(record, transportType));
		}

		Set<Transport> transportOrigins = new HashSet<>();
		Set<Transport> transportDestinations = new HashSet<>();
		for (Transport transport : newTransports)
		{
			int origin = transport.getOrigin();
			int destination = transport.getDestination();
			if ((origin == Transport.UNDEFINED_ORIGIN && destination == Transport.UNDEFINED_DESTINATION)
				|| (origin == Transport.LOCATION_PERMUTATION && destination == Transport.LOCATION_PERMUTATION))
			{
				continue;
			}
			else if (origin != Transport.LOCATION_PERMUTATION
				&& origin != Transport.UNDEFINED_ORIGIN
				&& destination == Transport.LOCATION_PERMUTATION)
			{
				transportOrigins.add(transport);
			}
			else if (origin == Transport.LOCATION_PERMUTATION
				&& destination != Transport.UNDEFINED_DESTINATION)
			{
				transportDestinations.add(transport);
			}

			if (origin != Transport.LOCATION_PERMUTATION
				&& destination != Transport.UNDEFINED_DESTINATION
				&& destination != Transport.LOCATION_PERMUTATION
				&& (origin == Transport.UNDEFINED_ORIGIN || origin != destination))
			{
				transports.computeIfAbsent(origin, ignored -> new HashSet<>()).add(transport);
			}
		}

		for (Transport origin : transportOrigins)
		{
			for (Transport destination : transportDestinations)
			{
				if (WorldPointUtil.distanceBetween2D(origin.getOrigin(), destination.getDestination()) > radiusThreshold)
				{
					Transport combined = new Transport(origin, destination);
					transports.computeIfAbsent(origin.getOrigin(), ignored -> new HashSet<>()).add(combined);
				}
			}
		}
	}
}
