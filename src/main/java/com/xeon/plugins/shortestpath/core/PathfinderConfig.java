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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PathfinderConfig
{
	private static final int POH_MIN_X = 1856;
	private static final int POH_MAX_X = 2047;
	private static final int POH_MIN_Y = 5696;
	private static final int POH_MAX_Y = 5767;

	private final SplitFlagMap mapData;
	private final ThreadLocal<CollisionMap> map;
	private final Transport[] allTransports;
	private final ProfileRequirements profileRequirements;
	private PathOptions options;
	private Set<TeleportItem> enabledTeleportItems = EnumSet.allOf(TeleportItem.class);
	private volatile WikiSyncProfile profile;
	private volatile TransportAvailability availability;

	public PathfinderConfig(PathOptions options)
	{
		this.options = options == null ? PathOptions.defaults() : options;
		mapData = SplitFlagMap.fromResources();
		map = ThreadLocal.withInitial(() -> new CollisionMap(mapData));

		Map<Integer, Set<Transport>> loadedTransports = TransportLoader.loadAllFromResources();
		remapPohDestinations(loadedTransports);
		allTransports = flatten(loadedTransports);
		profileRequirements = ProfileRequirements.fromTransports(allTransports);
		refresh(this.options);
	}

	public synchronized void refresh(PathOptions options)
	{
		this.options = options == null ? PathOptions.defaults() : options;
		TransportAvailability.Builder builder = new TransportAvailability.Builder(allTransports.length);
		for (Transport transport : allTransports)
		{
			if (useTransport(transport))
			{
				builder.add(transport);
			}
		}
		builder.remapPohTransports();
		availability = builder.build();
	}

	public void setProfile(WikiSyncProfile profile)
	{
		this.profile = profile;
		refresh(options);
	}

	public void setEnabledTeleportItems(Set<TeleportItem> enabledTeleportItems)
	{
		this.enabledTeleportItems = copyTeleportItems(enabledTeleportItems);
		refresh(options);
	}

	public WikiSyncProfile getProfile()
	{
		return profile;
	}

	public ProfileRequirements profileRequirements()
	{
		return profileRequirements;
	}

	public CollisionMap getMap()
	{
		return map.get();
	}

	public SplitFlagMap getMapData()
	{
		return mapData;
	}

	public long getCalculationCutoffMillis()
	{
		return options.calculationCutoffMillis();
	}

	public boolean isBankPathEnabled()
	{
		return false;
	}

	public boolean bankAccessible(int packedPosition)
	{
		return false;
	}

	public PrimitiveIntHashMap<Transport[]> getTransportsPacked(boolean bankVisited)
	{
		return availability.getTransportsPacked();
	}

	public Transport[] getUsableTeleports(boolean bankVisited)
	{
		return availability.getUsableTeleports();
	}

	public TransportAvailability getTransportAvailability()
	{
		return availability;
	}

	public int getAdditionalTransportCost(Transport transport)
	{
		return transport.getType().getAdditionalCost();
	}

	public int getDifferentialCost(Transport transport)
	{
		return transport.getType().differentialCost();
	}

	public boolean avoidWilderness(int packedPosition, int packedNeighborPosition, boolean targetInWilderness)
	{
		return options.avoidWilderness()
			&& !targetInWilderness
			&& !WildernessChecker.isInWilderness(packedPosition)
			&& WildernessChecker.isInWilderness(packedNeighborPosition);
	}

	public boolean avoidBlockedRegion(int packedPosition, int packedNeighborPosition, boolean targetInBlockedRegion)
	{
		return false;
	}

	public Transport findTransport(int source, int destination, boolean bankVisited)
	{
		Transport direct = firstMatching(availability.getTransportsPacked()
			.getOrDefault(source, TransportAvailability.EMPTY_TRANSPORTS), destination);
		if (direct != null)
		{
			return direct;
		}
		return firstMatching(availability.getUsableTeleports(), destination);
	}

	public List<Transport> visibleTransports()
	{
		List<Transport> out = new ArrayList<>();
		for (int origin : availability.getDisplayTransports().keys())
		{
			Collections.addAll(out, availability.getDisplayTransports()
				.getOrDefault(origin, TransportAvailability.EMPTY_TRANSPORTS));
		}
		Collections.addAll(out, availability.getUsableTeleports());
		return out;
	}

	public static boolean isInsidePoh(int x, int y)
	{
		return x >= POH_MIN_X && x <= POH_MAX_X && y >= POH_MIN_Y && y <= POH_MAX_Y;
	}

	private boolean useTransport(Transport transport)
	{
		boolean globalTeleport = transport.isGlobal();
		if (!options.enabledTransportTypes().contains(transport.getType()))
		{
			return false;
		}
		if (!options.includeTeleports() && (globalTeleport || transport.getType().isTeleport()))
		{
			return false;
		}
		if (!options.includeTransports() && !globalTeleport && !transport.getType().isTeleport())
		{
			return false;
		}
		if (!options.includePoh() && touchesPoh(transport))
		{
			return false;
		}
		if (transport.getType() == TransportType.TELEPORTATION_ITEM)
		{
			if (options.avoidItemTeleports())
			{
				return false;
			}
			TeleportItem item = transport.getTeleportItem();
			if (!enabledTeleportItems.contains(item))
			{
				return false;
			}
		}
		WikiSyncProfile currentProfile = profile;
		if (currentProfile != null && !currentProfile.canUse(transport))
		{
			return false;
		}
		return true;
	}

	private static Set<TeleportItem> copyTeleportItems(Set<TeleportItem> items)
	{
		if (items == null)
		{
			return EnumSet.allOf(TeleportItem.class);
		}
		if (items.isEmpty())
		{
			return EnumSet.noneOf(TeleportItem.class);
		}
		return EnumSet.copyOf(items);
	}

	private static Transport firstMatching(Transport[] transports, int destination)
	{
		for (Transport transport : transports)
		{
			if (transport.getDestination() == destination)
			{
				return transport;
			}
		}
		return null;
	}

	private static boolean touchesPoh(Transport transport)
	{
		int origin = transport.getOrigin();
		int destination = transport.getDestination();
		return (origin != WorldPointUtil.UNDEFINED
			&& isInsidePoh(WorldPointUtil.unpackWorldX(origin), WorldPointUtil.unpackWorldY(origin)))
			|| (destination != WorldPointUtil.UNDEFINED
			&& isInsidePoh(WorldPointUtil.unpackWorldX(destination), WorldPointUtil.unpackWorldY(destination)));
	}

	private static Transport[] flatten(Map<Integer, Set<Transport>> transports)
	{
		Set<Transport> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		List<Transport> all = new ArrayList<>();
		for (Set<Transport> set : transports.values())
		{
			for (Transport transport : set)
			{
				if (seen.add(transport))
				{
					all.add(transport);
				}
			}
		}
		return all.toArray(TransportAvailability.EMPTY_TRANSPORTS);
	}

	private static void remapPohDestinations(Map<Integer, Set<Transport>> transports)
	{
		int pohLanding = WorldPointUtil.packWorldPoint(1923, 5709, 0);
		for (Set<Transport> transportSet : transports.values())
		{
			for (Transport transport : transportSet)
			{
				int destination = transport.getDestination();
				int destX = WorldPointUtil.unpackWorldX(destination);
				int destY = WorldPointUtil.unpackWorldY(destination);
				if (destination != pohLanding && isInsidePoh(destX, destY))
				{
					transport.setDestination(pohLanding);
				}
			}
		}
	}
}
