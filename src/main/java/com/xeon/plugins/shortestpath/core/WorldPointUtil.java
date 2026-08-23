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

public final class WorldPointUtil
{
	public static final int CHEBYSHEV_DISTANCE_METRIC = 1;
	public static final int EUCLIDEAN_SQUARED_DISTANCE_METRIC = 1414213562;
	public static final int MANHATTAN_DISTANCE_METRIC = 2;
	public static final int UNDEFINED = -1;

	private WorldPointUtil()
	{
	}

	public static int packWorldPoint(int x, int y, int plane)
	{
		return (x & 0x7FFF) | ((y & 0x7FFF) << 15) | ((plane & 0x3) << 30);
	}

	public static int unpackWorldX(int packedPoint)
	{
		return packedPoint & 0x7FFF;
	}

	public static int unpackWorldY(int packedPoint)
	{
		return (packedPoint >> 15) & 0x7FFF;
	}

	public static int unpackWorldPlane(int packedPoint)
	{
		return (packedPoint >> 30) & 0x3;
	}

	public static int distanceBetween(int previousPacked, int currentPacked)
	{
		return distanceBetween(previousPacked, currentPacked, CHEBYSHEV_DISTANCE_METRIC);
	}

	public static int distanceBetween2D(int previousPacked, int currentPacked)
	{
		return distanceBetween2D(previousPacked, currentPacked, CHEBYSHEV_DISTANCE_METRIC);
	}

	public static int distanceBetween(int previousPacked, int currentPacked, int diagonal)
	{
		int previousX = unpackWorldX(previousPacked);
		int previousY = unpackWorldY(previousPacked);
		int previousZ = unpackWorldPlane(previousPacked);
		int currentX = unpackWorldX(currentPacked);
		int currentY = unpackWorldY(currentPacked);
		int currentZ = unpackWorldPlane(currentPacked);
		if (previousZ != currentZ)
		{
			return Integer.MAX_VALUE;
		}
		return distanceBetween2D(previousX, previousY, currentX, currentY, diagonal);
	}

	public static int distanceBetween2D(int previousPacked, int currentPacked, int diagonal)
	{
		return distanceBetween2D(
			unpackWorldX(previousPacked),
			unpackWorldY(previousPacked),
			unpackWorldX(currentPacked),
			unpackWorldY(currentPacked),
			diagonal
		);
	}

	public static int distanceBetween2D(int previousX, int previousY, int currentX, int currentY, int diagonal)
	{
		int dx = previousX - currentX;
		int dy = previousY - currentY;
		if (diagonal == CHEBYSHEV_DISTANCE_METRIC)
		{
			return Math.max(Math.abs(dx), Math.abs(dy));
		}
		if (diagonal == MANHATTAN_DISTANCE_METRIC)
		{
			return Math.abs(dx) + Math.abs(dy);
		}
		return dx * dx + dy * dy;
	}

	public static int distanceToArea2D(int packedPoint, WorldArea area)
	{
		int y = unpackWorldY(packedPoint);
		int x = unpackWorldX(packedPoint);
		int areaMaxX = area.x() + area.width() - 1;
		int areaMaxY = area.y() + area.height() - 1;
		int dx = Math.max(Math.max(area.x() - x, 0), x - areaMaxX);
		int dy = Math.max(Math.max(area.y() - y, 0), y - areaMaxY);
		return Math.max(dx, dy);
	}

	public record WorldArea(int x, int y, int width, int height)
	{
	}
}
