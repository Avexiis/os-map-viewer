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
import java.util.Arrays;
import java.util.List;

final class NodeGraph
{
	static final int NO_NODE = -1;

	private static final byte FLAG_BANK_VISITED = 1;
	private static final byte FLAG_ABSTRACT = 1 << 1;
	private static final byte FLAG_DELAYED_VISIT = 1 << 2;
	private static final byte FLAG_TRANSPORT = 1 << 3;
	private static final AbstractNodeKind[] ABSTRACT_KINDS = AbstractNodeKind.values();

	private int[] packedPosition;
	private int[] previous;
	private int[] cost;
	private int[] differentialCost;
	private byte[] flags;
	private byte[] abstractKind;
	private int size;

	NodeGraph(int initialCapacity)
	{
		int capacity = Math.max(1, initialCapacity);
		packedPosition = new int[capacity];
		previous = new int[capacity];
		cost = new int[capacity];
		differentialCost = new int[capacity];
		flags = new byte[capacity];
		abstractKind = new byte[capacity];
	}

	int createStart(int packedPosition)
	{
		return append(packedPosition, NO_NODE, 0, 0, (byte) 0, (byte) 0);
	}

	int createTile(int packedPosition, int previous, boolean bankVisited)
	{
		int travelTime = previous != NO_NODE && isTile(previous)
			? WorldPointUtil.distanceBetween(this.packedPosition[previous], packedPosition)
			: 0;
		byte flagBits = bankVisited ? FLAG_BANK_VISITED : 0;
		return append(packedPosition, previous, costOf(previous) + travelTime, 0, flagBits, (byte) 0);
	}

	int createTransport(int packedPosition, int previous, int travelTime, int additionalCost,
	                    boolean bankVisited, boolean delayedVisit, int differentialCost)
	{
		byte flagBits = FLAG_TRANSPORT;
		if (bankVisited)
		{
			flagBits |= FLAG_BANK_VISITED;
		}
		if (delayedVisit)
		{
			flagBits |= FLAG_DELAYED_VISIT;
		}
		return append(packedPosition, previous, costOf(previous) + travelTime + additionalCost,
			differentialCost, flagBits, (byte) 0);
	}

	int createAbstract(AbstractNodeKind abstractNodeKind, int previous, boolean bankVisited)
	{
		byte flagBits = FLAG_ABSTRACT;
		if (bankVisited)
		{
			flagBits |= FLAG_BANK_VISITED;
		}
		return append(WorldPointUtil.UNDEFINED, previous, costOf(previous), 0, flagBits,
			(byte) abstractNodeKind.ordinal());
	}

	int packedPosition(int id)
	{
		return packedPosition[id];
	}

	int cost(int id)
	{
		return cost[id];
	}

	int differentialCost(int id)
	{
		return differentialCost[id];
	}

	int compareCost(int id)
	{
		return cost[id] + differentialCost[id];
	}

	boolean bankVisited(int id)
	{
		return (flags[id] & FLAG_BANK_VISITED) != 0;
	}

	boolean isTile(int id)
	{
		return (flags[id] & FLAG_ABSTRACT) == 0;
	}

	boolean isTransport(int id)
	{
		return (flags[id] & FLAG_TRANSPORT) != 0;
	}

	boolean isDelayedVisit(int id)
	{
		return (flags[id] & FLAG_DELAYED_VISIT) != 0;
	}

	AbstractNodeKind abstractKind(int id)
	{
		return ABSTRACT_KINDS[abstractKind[id]];
	}

	List<PathStep> getPathSteps(int id)
	{
		int[] prev = previous;
		int[] packed = packedPosition;
		byte[] flg = flags;
		if (prev == null || packed == null || flg == null || id == NO_NODE)
		{
			return new ArrayList<>();
		}
		int len = prev.length;

		int node = id;
		int count = 0;
		while (node != NO_NODE && node < len)
		{
			if ((flg[node] & FLAG_ABSTRACT) == 0)
			{
				count++;
			}
			node = prev[node];
		}

		List<PathStep> pathSteps = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			pathSteps.add(null);
		}

		node = id;
		int index = count;
		while (node != NO_NODE && node < len && index > 0)
		{
			if ((flg[node] & FLAG_ABSTRACT) == 0)
			{
				pathSteps.set(--index, new PathStep(packed[node], (flg[node] & FLAG_BANK_VISITED) != 0));
			}
			node = prev[node];
		}

		return pathSteps;
	}

	int getClosestTilePosition(int id)
	{
		int[] prev = previous;
		int[] packed = packedPosition;
		byte[] flg = flags;
		if (prev == null || packed == null || flg == null)
		{
			return WorldPointUtil.UNDEFINED;
		}
		int len = prev.length;
		int node = id;
		while (node != NO_NODE && node < len && (flg[node] & FLAG_ABSTRACT) != 0)
		{
			node = prev[node];
		}
		return node != NO_NODE && node < len ? packed[node] : WorldPointUtil.UNDEFINED;
	}

	void release()
	{
		packedPosition = null;
		previous = null;
		cost = null;
		differentialCost = null;
		flags = null;
		abstractKind = null;
		size = 0;
	}

	private int costOf(int id)
	{
		return id == NO_NODE ? 0 : cost[id];
	}

	private int append(int packed, int prev, int nodeCost, int diffCost, byte flagBits, byte kind)
	{
		ensureCapacity();
		int id = size;
		packedPosition[id] = packed;
		previous[id] = prev;
		cost[id] = nodeCost;
		differentialCost[id] = diffCost;
		flags[id] = flagBits;
		abstractKind[id] = kind;
		size = id + 1;
		return id;
	}

	private void ensureCapacity()
	{
		if (size < packedPosition.length)
		{
			return;
		}
		int newCapacity = packedPosition.length + (packedPosition.length >> 1);
		packedPosition = Arrays.copyOf(packedPosition, newCapacity);
		previous = Arrays.copyOf(previous, newCapacity);
		cost = Arrays.copyOf(cost, newCapacity);
		differentialCost = Arrays.copyOf(differentialCost, newCapacity);
		flags = Arrays.copyOf(flags, newCapacity);
		abstractKind = Arrays.copyOf(abstractKind, newCapacity);
	}
}
