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

import java.util.Arrays;

final class SceneMeshBuffer
{
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	private float[] values;
	private int size;

	SceneMeshBuffer(int initialCapacity)
	{
		values = new float[Math.max(128, initialCapacity)];
	}

	void addVertex(float x, float y, float z, float normalX, float normalY, float normalZ, int rgb)
	{
		addVertex(x, y, z, normalX, normalY, normalZ, rgb, 1.0f);
	}

	void addVertex(float x, float y, float z, float normalX, float normalY, float normalZ, int rgb, float alpha)
	{
		addVertex(x, y, z, normalX, normalY, normalZ, rgb, alpha, 0.0f);
	}

	void addVertex(
		float x,
		float y,
		float z,
		float normalX,
		float normalY,
		float normalZ,
		int rgb,
		float alpha,
		float depthBias
	)
	{
		addVertex(x, y, z, normalX, normalY, normalZ, rgb, alpha, depthBias, 0.0f, 0.0f, 0, 0.0f, 0.0f, 0.0f);
	}

	void addVertex(
		float x,
		float y,
		float z,
		float normalX,
		float normalY,
		float normalZ,
		int rgb,
		float alpha,
		float depthBias,
		float textureU,
		float textureV,
		int textureLayer,
		float animationU,
		float animationV,
		float textureAlphaCutoff
	)
	{
		add(x);
		add(y);
		add(z);
		add(normalX);
		add(normalY);
		add(normalZ);
		add(((rgb >> 16) & 0xFF) / 255.0f);
		add(((rgb >> 8) & 0xFF) / 255.0f);
		add((rgb & 0xFF) / 255.0f);
		add(alpha);
		add(depthBias);
		add(textureU);
		add(textureV);
		add(textureLayer);
		add(animationU);
		add(animationV);
		add(textureAlphaCutoff);
	}

	int size()
	{
		return size;
	}

	float[] toArray()
	{
		return Arrays.copyOf(values, size);
	}

	private void add(float value)
	{
		if (size == values.length)
		{
			if (values.length >= MAX_ARRAY_SIZE)
			{
				throw new OutOfMemoryError("3D mesh buffer exceeded maximum Java array size");
			}
			long doubled = (long) values.length * 2L;
			int nextLength = (int) Math.min(MAX_ARRAY_SIZE, Math.max(doubled, (long) size + 1L));
			values = Arrays.copyOf(values, nextLength);
		}
		values[size++] = value;
	}
}
