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
package com.xeon.view3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class NpcRouteFinder
{
	private static final int SEARCH_SIZE = 128;
	private static final int QUEUE_SIZE = SEARCH_SIZE * SEARCH_SIZE;
	private static final int UNVISITED = -1;
	private static final int START = -2;
	private static final int[] DX = new int[]{-1, 1, 0, 0};
	private static final int[] DY = new int[]{0, 0, -1, 1};

	private NpcRouteFinder()
	{
	}

	static List<Step> findCardinalPath(
		NpcWanderCollisionMap collisionMap,
		int startX,
		int startY,
		int endX,
		int endY,
		int plane,
		int size
	)
	{
		return findCardinalPath(collisionMap, startX, startY, endX, endY, plane, size, Bounds.unbounded());
	}

	static List<Step> findCardinalPath(
		NpcWanderCollisionMap collisionMap,
		int startX,
		int startY,
		int endX,
		int endY,
		int plane,
		int size,
		Bounds bounds
	)
	{
		Bounds searchBounds = bounds == null ? Bounds.unbounded() : bounds;
		int footprint = Math.max(1, size);
		if (!searchBounds.contains(startX, startY) || !searchBounds.contains(endX, endY))
		{
			return List.of();
		}
		if (startX == endX && startY == endY)
		{
			return List.of(new Step(startX, startY));
		}
		if (!canStand(collisionMap, startX, startY, plane, footprint)
			|| !canStand(collisionMap, endX, endY, plane, footprint))
		{
			return List.of();
		}

		int baseX = startX - SEARCH_SIZE / 2;
		int baseY = startY - SEARCH_SIZE / 2;
		int startLocalX = startX - baseX;
		int startLocalY = startY - baseY;
		int endLocalX = endX - baseX;
		int endLocalY = endY - baseY;
		if (!inSearchBounds(startLocalX, startLocalY) || !inSearchBounds(endLocalX, endLocalY))
		{
			return List.of();
		}

		int[] previous = new int[SEARCH_SIZE * SEARCH_SIZE];
		Arrays.fill(previous, UNVISITED);
		int[] queue = new int[QUEUE_SIZE];
		int read = 0;
		int write = 0;
		int startIndex = index(startLocalX, startLocalY);
		int endIndex = index(endLocalX, endLocalY);
		previous[startIndex] = START;
		queue[write++] = startIndex;

		while (read != write)
		{
			int currentIndex = queue[read++];
			int currentLocalX = currentIndex % SEARCH_SIZE;
			int currentLocalY = currentIndex / SEARCH_SIZE;
			int currentX = baseX + currentLocalX;
			int currentY = baseY + currentLocalY;
			int[] order = directionOrder(currentX, currentY, endX, endY);
			for (int direction : order)
			{
				int nextLocalX = currentLocalX + DX[direction];
				int nextLocalY = currentLocalY + DY[direction];
				if (!inSearchBounds(nextLocalX, nextLocalY))
				{
					continue;
				}

				int nextIndex = index(nextLocalX, nextLocalY);
				if (previous[nextIndex] != UNVISITED)
				{
					continue;
				}

				int dx = DX[direction];
				int dy = DY[direction];
				int nextX = currentX + dx;
				int nextY = currentY + dy;
				if (!searchBounds.contains(nextX, nextY)
					|| !canStep(collisionMap, currentX, currentY, plane, footprint, dx, dy))
				{
					continue;
				}

				previous[nextIndex] = currentIndex;
				if (nextIndex == endIndex)
				{
					return buildPath(previous, endIndex, baseX, baseY);
				}
				queue[write++] = nextIndex;
			}
		}
		return List.of();
	}

	private static boolean canStand(
		NpcWanderCollisionMap collisionMap,
		int x,
		int y,
		int plane,
		int size
	)
	{
		return collisionMap == null || collisionMap.canStand(x, y, plane, size);
	}

	private static boolean canStep(
		NpcWanderCollisionMap collisionMap,
		int x,
		int y,
		int plane,
		int size,
		int dx,
		int dy
	)
	{
		return collisionMap == null || collisionMap.canStep(x, y, plane, size, dx, dy);
	}

	private static List<Step> buildPath(int[] previous, int endIndex, int baseX, int baseY)
	{
		List<Step> path = new ArrayList<>();
		for (int index = endIndex; index >= 0; index = previous[index])
		{
			path.add(new Step(baseX + index % SEARCH_SIZE, baseY + index / SEARCH_SIZE));
			if (previous[index] == START)
			{
				break;
			}
		}
		Collections.reverse(path);
		return List.copyOf(path);
	}

	private static int[] directionOrder(int x, int y, int endX, int endY)
	{
		int xDir = Integer.compare(endX, x);
		int yDir = Integer.compare(endY, y);
		boolean preferX = Math.abs(endX - x) >= Math.abs(endY - y);
		int primaryX = xDir < 0 ? 0 : 1;
		int primaryY = yDir < 0 ? 2 : 3;
		int secondaryX = xDir < 0 ? 1 : 0;
		int secondaryY = yDir < 0 ? 3 : 2;
		if (xDir == 0)
		{
			return new int[]{primaryY, secondaryX, secondaryY, primaryX};
		}
		if (yDir == 0)
		{
			return new int[]{primaryX, secondaryY, secondaryX, primaryY};
		}
		return preferX
			? new int[]{primaryX, primaryY, secondaryY, secondaryX}
			: new int[]{primaryY, primaryX, secondaryX, secondaryY};
	}

	private static boolean inSearchBounds(int localX, int localY)
	{
		return localX >= 0 && localY >= 0 && localX < SEARCH_SIZE && localY < SEARCH_SIZE;
	}

	private static int index(int localX, int localY)
	{
		return localX + localY * SEARCH_SIZE;
	}

	record Step(int x, int y)
	{
	}

	record Bounds(int minX, int minY, int maxX, int maxY)
	{
		private static Bounds unbounded()
		{
			return new Bounds(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
		}

		boolean contains(int x, int y)
		{
			return x >= minX && y >= minY && x <= maxX && y <= maxY;
		}
	}
}
