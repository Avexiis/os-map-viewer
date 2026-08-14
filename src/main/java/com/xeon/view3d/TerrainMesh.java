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

public final class TerrainMesh
{
	public static final int FLOATS_PER_VERTEX = 9;

	private final int regionId;
	private final int regionX;
	private final int regionY;
	private final int plane;
	private final float[] vertexData;
	private final int vertexCount;
	private final float initialCameraX;
	private final float initialCameraY;
	private final float initialCameraZ;

	public TerrainMesh(
		int regionId,
		int regionX,
		int regionY,
		int plane,
		float[] vertexData,
		int vertexCount,
		float initialCameraX,
		float initialCameraY,
		float initialCameraZ
	)
	{
		this.regionId = regionId;
		this.regionX = regionX;
		this.regionY = regionY;
		this.plane = plane;
		this.vertexData = Arrays.copyOf(vertexData, vertexData.length);
		this.vertexCount = vertexCount;
		this.initialCameraX = initialCameraX;
		this.initialCameraY = initialCameraY;
		this.initialCameraZ = initialCameraZ;
	}

	public int regionId()
	{
		return regionId;
	}

	public int regionX()
	{
		return regionX;
	}

	public int regionY()
	{
		return regionY;
	}

	public int plane()
	{
		return plane;
	}

	public float[] vertexData()
	{
		return Arrays.copyOf(vertexData, vertexData.length);
	}

	public int vertexCount()
	{
		return vertexCount;
	}

	public float initialCameraX()
	{
		return initialCameraX;
	}

	public float initialCameraY()
	{
		return initialCameraY;
	}

	public float initialCameraZ()
	{
		return initialCameraZ;
	}
}
