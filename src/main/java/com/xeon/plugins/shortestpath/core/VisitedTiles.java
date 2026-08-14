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

import static com.xeon.plugins.shortestpath.core.SplitFlagMap.REGION_SIZE;

final class VisitedTiles
{
	private final SplitFlagMap.RegionExtent regionExtents;
	private final int widthInclusive;
	private final VisitedRegion[] visitedRegionsWithoutBank;
	private final VisitedRegion[] visitedRegionsWithBank;
	private final CollisionMap map;
	private final boolean[] abstractVisitedWithoutBank = new boolean[AbstractNodeKind.values().length];
	private final boolean[] abstractVisitedWithBank = new boolean[AbstractNodeKind.values().length];

	VisitedTiles(CollisionMap map)
	{
		this.map = map;
		regionExtents = SplitFlagMap.getRegionExtents();
		widthInclusive = regionExtents.width() + 1;
		int heightInclusive = regionExtents.height() + 1;
		visitedRegionsWithoutBank = new VisitedRegion[widthInclusive * heightInclusive];
		visitedRegionsWithBank = new VisitedRegion[widthInclusive * heightInclusive];
	}

	boolean get(int packedPoint, boolean bankVisited)
	{
		return get(
			WorldPointUtil.unpackWorldX(packedPoint),
			WorldPointUtil.unpackWorldY(packedPoint),
			WorldPointUtil.unpackWorldPlane(packedPoint),
			bankVisited
		);
	}

	boolean get(int x, int y, int plane, boolean bankVisited)
	{
		VisitedRegion[] visitedRegions = bankVisited ? visitedRegionsWithBank : visitedRegionsWithoutBank;
		int regionIndex = getRegionIndex(x / REGION_SIZE, y / REGION_SIZE);
		if (regionIndex < 0 || regionIndex >= visitedRegions.length)
		{
			return true;
		}
		VisitedRegion region = visitedRegions[regionIndex];
		return region != null && region.get(Math.floorMod(x, REGION_SIZE), Math.floorMod(y, REGION_SIZE), plane);
	}

	boolean getAbstract(AbstractNodeKind abstractNodeKind, boolean bankVisited)
	{
		return bankVisited
			? abstractVisitedWithBank[abstractNodeKind.ordinal()]
			: abstractVisitedWithoutBank[abstractNodeKind.ordinal()];
	}

	boolean set(int id, NodeGraph graph)
	{
		if (graph.isTile(id))
		{
			return set(graph.packedPosition(id), graph.bankVisited(id));
		}

		AbstractNodeKind kind = graph.abstractKind(id);
		boolean visited = getAbstract(kind, graph.bankVisited(id));
		if (graph.bankVisited(id))
		{
			abstractVisitedWithBank[kind.ordinal()] = true;
		}
		abstractVisitedWithoutBank[kind.ordinal()] = true;
		return !visited;
	}

	private boolean set(int packedPoint, boolean bankVisited)
	{
		return set(
			WorldPointUtil.unpackWorldX(packedPoint),
			WorldPointUtil.unpackWorldY(packedPoint),
			WorldPointUtil.unpackWorldPlane(packedPoint),
			bankVisited
		);
	}

	private boolean set(int x, int y, int plane, boolean bankVisited)
	{
		int regionIndex = getRegionIndex(x / REGION_SIZE, y / REGION_SIZE);
		if (regionIndex < 0 || regionIndex >= visitedRegionsWithoutBank.length)
		{
			return false;
		}

		if (bankVisited)
		{
			boolean unique = setInRegion(visitedRegionsWithBank, regionIndex, x, y, plane);
			setInRegion(visitedRegionsWithoutBank, regionIndex, x, y, plane);
			return unique;
		}

		return setInRegion(visitedRegionsWithoutBank, regionIndex, x, y, plane);
	}

	void clear()
	{
		for (int i = 0; i < visitedRegionsWithoutBank.length; i++)
		{
			visitedRegionsWithoutBank[i] = null;
			visitedRegionsWithBank[i] = null;
		}
		for (int i = 0; i < abstractVisitedWithoutBank.length; i++)
		{
			abstractVisitedWithoutBank[i] = false;
			abstractVisitedWithBank[i] = false;
		}
	}

	private boolean setInRegion(VisitedRegion[] visitedRegions, int regionIndex, int x, int y, int plane)
	{
		VisitedRegion region = visitedRegions[regionIndex];
		if (region == null)
		{
			region = new VisitedRegion(map.getRegionPlaneCounts(regionIndex));
			visitedRegions[regionIndex] = region;
		}
		return region.set(Math.floorMod(x, REGION_SIZE), Math.floorMod(y, REGION_SIZE), plane);
	}

	private int getRegionIndex(int regionX, int regionY)
	{
		return (regionX - regionExtents.minX()) + (regionY - regionExtents.minY()) * widthInclusive;
	}

	private static final class VisitedRegion
	{
		private final long[] planes;
		private final byte planeCount;

		VisitedRegion(byte planeCount)
		{
			this.planeCount = planeCount;
			this.planes = new long[Math.max(0, planeCount) * REGION_SIZE];
		}

		boolean set(int x, int y, int plane)
		{
			if (plane < 0 || plane >= planeCount)
			{
				return false;
			}
			int index = y + plane * REGION_SIZE;
			boolean unique = (planes[index] & (1L << x)) == 0;
			planes[index] |= 1L << x;
			return unique;
		}

		boolean get(int x, int y, int plane)
		{
			if (plane < 0 || plane >= planeCount)
			{
				return true;
			}
			return (planes[y + plane * REGION_SIZE] & (1L << x)) != 0;
		}
	}
}
