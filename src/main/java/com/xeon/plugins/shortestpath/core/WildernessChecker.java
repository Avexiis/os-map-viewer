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

import java.util.Set;

import com.xeon.plugins.shortestpath.core.WorldPointUtil.WorldArea;

public final class WildernessChecker
{
	private static final WorldArea WILDERNESS_ABOVE_GROUND = new WorldArea(2944, 3525, 448, 448);
	private static final WorldArea WILDERNESS_UNDERGROUND = new WorldArea(2944, 9918, 518, 458);

	private static final WorldArea FEROX_ENCLAVE_1 = new WorldArea(3123, 3622, 2, 10);
	private static final WorldArea FEROX_ENCLAVE_2 = new WorldArea(3125, 3617, 16, 23);
	private static final WorldArea FEROX_ENCLAVE_3 = new WorldArea(3138, 3636, 18, 10);
	private static final WorldArea FEROX_ENCLAVE_4 = new WorldArea(3141, 3625, 14, 11);
	private static final WorldArea FEROX_ENCLAVE_5 = new WorldArea(3141, 3619, 7, 6);

	private static final WorldArea NOT_WILDERNESS_1 = new WorldArea(2997, 3525, 34, 9);
	private static final WorldArea NOT_WILDERNESS_2 = new WorldArea(3005, 3534, 21, 10);
	private static final WorldArea NOT_WILDERNESS_3 = new WorldArea(3000, 3534, 5, 5);
	private static final WorldArea NOT_WILDERNESS_4 = new WorldArea(3031, 3525, 2, 2);

	private static final WorldArea WILDERNESS_ABOVE_GROUND_LEVEL_20 = new WorldArea(2944, 3680, 448, 448);
	private static final WorldArea WILDERNESS_ABOVE_GROUND_LEVEL_30 = new WorldArea(2944, 3760, 448, 448);
	private static final WorldArea WILDERNESS_UNDERGROUND_LEVEL_20 = new WorldArea(2944, 10075, 518, 301);
	private static final WorldArea WILDERNESS_UNDERGROUND_LEVEL_30 = new WorldArea(2944, 10155, 518, 221);

	private WildernessChecker()
	{
	}

	public static boolean isInWilderness(int packedPoint)
	{
		return WorldPointUtil.distanceToArea2D(packedPoint, WILDERNESS_ABOVE_GROUND) == 0
			&& WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_1) != 0
			&& WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_2) != 0
			&& WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_3) != 0
			&& WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_4) != 0
			&& WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_5) != 0
			&& WorldPointUtil.distanceToArea2D(packedPoint, NOT_WILDERNESS_1) != 0
			&& WorldPointUtil.distanceToArea2D(packedPoint, NOT_WILDERNESS_2) != 0
			&& WorldPointUtil.distanceToArea2D(packedPoint, NOT_WILDERNESS_3) != 0
			&& WorldPointUtil.distanceToArea2D(packedPoint, NOT_WILDERNESS_4) != 0
			|| WorldPointUtil.distanceToArea2D(packedPoint, WILDERNESS_UNDERGROUND) == 0;
	}

	public static boolean isInWilderness(Set<Integer> packedPoints)
	{
		for (int packedPoint : packedPoints)
		{
			if (isInWilderness(packedPoint))
			{
				return true;
			}
		}
		return false;
	}

	static boolean isInLevel20Wilderness(int packedPoint)
	{
		return WorldPointUtil.distanceToArea2D(packedPoint, WILDERNESS_ABOVE_GROUND_LEVEL_20) == 0
			|| WorldPointUtil.distanceToArea2D(packedPoint, WILDERNESS_UNDERGROUND_LEVEL_20) == 0;
	}

	static boolean isInLevel30Wilderness(int packedPoint)
	{
		return WorldPointUtil.distanceToArea2D(packedPoint, WILDERNESS_ABOVE_GROUND_LEVEL_30) == 0
			|| WorldPointUtil.distanceToArea2D(packedPoint, WILDERNESS_UNDERGROUND_LEVEL_30) == 0;
	}
}
