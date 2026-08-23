/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.

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

import java.util.Arrays;
import java.util.List;

record NpcMesh(
	int npcId,
	String name,
	int combatLevel,
	int sequenceId,
	boolean walkingAnimation,
	int[] frameLengths,
	int frameStep,
	AnimatedObjectMesh.Frame[] frames,
	Bounds bounds,
	List<Instance> instances
)
{
	NpcMesh
	{
		frameLengths = frameLengths == null ? new int[0] : Arrays.copyOf(frameLengths, frameLengths.length);
		frames = frames == null ? new AnimatedObjectMesh.Frame[0] : Arrays.copyOf(frames, frames.length);
		bounds = bounds == null ? Bounds.fallback() : bounds;
		instances = instances == null ? List.of() : List.copyOf(instances);
	}

	int frameCount()
	{
		return frames.length;
	}

	void releaseVertexData()
	{
		// NPC animation frames are shared across loaded regions by NpcMeshBuilder.FrameCache.
		// Region unloads must not clear them out from another visible region.
	}

	record Instance(
		int plane,
		int phaseOffset,
		float movementPhaseSeconds,
		float idleYawRadians,
		float[] x,
		float[] y,
		float[] z,
		float[] segmentStartYawRadians,
		float[] segmentEndYawRadians,
		boolean[] segmentWalking,
		float[] segmentSeconds
	)
	{
		static Instance stationary(int plane, int phaseOffset, float x, float y, float z, float yawRadians)
		{
			return new Instance(
				plane,
				phaseOffset,
				0.0f,
				yawRadians,
				new float[]{x},
				new float[]{y},
				new float[]{z},
				new float[0],
				new float[0],
				new boolean[0],
				new float[0]
			);
		}

		static Instance moving(
			int plane,
			int phaseOffset,
			float movementPhaseSeconds,
			float idleYawRadians,
			float[] x,
			float[] y,
			float[] z,
			float[] segmentStartYawRadians,
			float[] segmentEndYawRadians,
			boolean[] segmentWalking,
			float[] segmentSeconds
		)
		{
			return new Instance(
				plane,
				phaseOffset,
				movementPhaseSeconds,
				idleYawRadians,
				x,
				y,
				z,
				segmentStartYawRadians,
				segmentEndYawRadians,
				segmentWalking,
				segmentSeconds
			);
		}

		Instance
		{
			plane = Math.max(0, Math.min(3, plane));
			x = x == null ? new float[0] : Arrays.copyOf(x, x.length);
			y = y == null ? new float[0] : Arrays.copyOf(y, y.length);
			z = z == null ? new float[0] : Arrays.copyOf(z, z.length);
			segmentStartYawRadians = segmentStartYawRadians == null ? new float[0] : Arrays.copyOf(segmentStartYawRadians, segmentStartYawRadians.length);
			segmentEndYawRadians = segmentEndYawRadians == null ? new float[0] : Arrays.copyOf(segmentEndYawRadians, segmentEndYawRadians.length);
			segmentWalking = segmentWalking == null ? new boolean[0] : Arrays.copyOf(segmentWalking, segmentWalking.length);
			segmentSeconds = segmentSeconds == null ? new float[0] : Arrays.copyOf(segmentSeconds, segmentSeconds.length);
		}

		boolean moving()
		{
			return x.length > 1 && segmentSeconds.length > 0;
		}

		Transform transformAt(float timeSeconds)
		{
			if (!moving())
			{
				return new Transform(point(x, 0), point(y, 0), point(z, 0), idleYawRadians, false);
			}

			float total = totalSeconds();
			if (total <= 0.0f)
			{
				return new Transform(point(x, 0), point(y, 0), point(z, 0), idleYawRadians, false);
			}

			float cycle = Math.floorMod((long) ((timeSeconds + movementPhaseSeconds) * 1000.0f), (long) (total * 1000.0f)) / 1000.0f;
			for (int i = 0; i < segmentSeconds.length; i++)
			{
				float duration = Math.max(0.001f, segmentSeconds[i]);
				if (cycle <= duration || i == segmentSeconds.length - 1)
				{
					float t = Math.max(0.0f, Math.min(1.0f, cycle / duration));
					return new Transform(
						lerp(point(x, i), point(x, i + 1), t),
						lerp(point(y, i), point(y, i + 1), t),
						lerp(point(z, i), point(z, i + 1), t),
						lerpAngle(point(segmentStartYawRadians, i), point(segmentEndYawRadians, i), t),
						point(segmentWalking, i)
					);
				}
				cycle -= duration;
			}
			return new Transform(point(x, 0), point(y, 0), point(z, 0), idleYawRadians, false);
		}

		private float totalSeconds()
		{
			float total = 0.0f;
			for (float seconds : segmentSeconds)
			{
				total += Math.max(0.001f, seconds);
			}
			return total;
		}

		private static float point(float[] values, int index)
		{
			return values.length == 0 ? 0.0f : values[Math.min(index, values.length - 1)];
		}

		private static boolean point(boolean[] values, int index)
		{
			return values.length != 0 && values[Math.min(index, values.length - 1)];
		}

		private static float lerp(float a, float b, float t)
		{
			return a + (b - a) * t;
		}

		private static float lerpAngle(float a, float b, float t)
		{
			float delta = b - a;
			float twoPi = (float) (Math.PI * 2.0);
			while (delta > Math.PI)
			{
				delta -= twoPi;
			}
			while (delta < -Math.PI)
			{
				delta += twoPi;
			}
			return a + delta * t;
		}
	}

	record Transform(
		float x,
		float y,
		float z,
		float yawRadians,
		boolean walking
	)
	{
	}

	record Bounds(
		float minX,
		float minY,
		float minZ,
		float maxX,
		float maxY,
		float maxZ
	)
	{
		static Bounds fallback()
		{
			return new Bounds(-0.45f, 0.0f, -0.45f, 0.45f, 1.75f, 0.45f);
		}

		boolean valid()
		{
			return Float.isFinite(minX)
				&& Float.isFinite(minY)
				&& Float.isFinite(minZ)
				&& Float.isFinite(maxX)
				&& Float.isFinite(maxY)
				&& Float.isFinite(maxZ)
				&& minX <= maxX
				&& minY <= maxY
				&& minZ <= maxZ;
		}
	}
}
