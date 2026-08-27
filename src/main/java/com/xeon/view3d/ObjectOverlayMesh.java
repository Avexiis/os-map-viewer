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

import com.xeon.model.Tile;

final class ObjectOverlayMesh
{
	private static final int POSITION_FLOATS = 3;

	private final Tile tile;
	private final int objectId;
	private final int renderedObjectId;
	private final float centerX;
	private final float centerZ;
	private final float maxY;
	private float[] vertexData;
	private byte[] compressedVertexData;
	private final int vertexCount;

	ObjectOverlayMesh(
		Tile tile,
		int objectId,
		int renderedObjectId,
		float[] vertexData
	)
	{
		this.tile = tile == null ? null : new Tile(tile.x, tile.y, tile.z);
		this.objectId = objectId;
		this.renderedObjectId = renderedObjectId;
		this.vertexData = normalizeVertexData(vertexData);
		this.vertexCount = this.vertexData.length / POSITION_FLOATS;
		Bounds bounds = bounds(this.vertexData);
		centerX = bounds.centerX();
		centerZ = bounds.centerZ();
		maxY = bounds.maxY();
	}

	Tile tile()
	{
		return tile == null ? null : new Tile(tile.x, tile.y, tile.z);
	}

	boolean matches(Map3DObjectOverlay overlay)
	{
		if (overlay == null || overlay.tile() == null || tile == null)
		{
			return false;
		}
		Tile overlayTile = overlay.tile();
		return tile.x == overlayTile.x
			&& tile.y == overlayTile.y
			&& tile.z == overlayTile.z
			&& (overlay.objectId() <= 0
			|| objectId == overlay.objectId()
			|| renderedObjectId == overlay.objectId());
	}

	int vertexCount()
	{
		return vertexCount;
	}

	float centerX()
	{
		return centerX;
	}

	float centerZ()
	{
		return centerZ;
	}

	float maxY()
	{
		return maxY;
	}

	synchronized float[] rawVertexData()
	{
		if ((vertexData == null || vertexData.length == 0)
			&& compressedVertexData != null && compressedVertexData.length > 0)
		{
			vertexData = FloatDataCodec.inflate(compressedVertexData, vertexCount * POSITION_FLOATS);
		}
		return vertexData == null ? new float[0] : vertexData;
	}

	void compactVertexData()
	{
		compactVertexData(null);
	}

	void compactVertexData(Runnable pause)
	{
		float[] source;
		synchronized (this)
		{
			boolean hasVertexData = vertexData != null && vertexData.length > 0;
			if (!hasVertexData)
			{
				return;
			}
			if (compressedVertexData != null && hasVertexData)
			{
				vertexData = new float[0];
			}
			source = compressedVertexData == null ? vertexData : null;
		}

		byte[] compressed = source == null || source.length == 0 ? null : FloatDataCodec.deflate(source, pause);
		synchronized (this)
		{
			if (source != null && vertexData == source && compressedVertexData == null)
			{
				compressedVertexData = compressed;
				vertexData = new float[0];
			}
		}
	}

	void releaseVertexData()
	{
		synchronized (this)
		{
			vertexData = new float[0];
			compressedVertexData = null;
		}
	}

	synchronized long retainedVertexBytes()
	{
		long bytes = 0L;
		if (vertexData != null && vertexData.length > 0)
		{
			bytes += (long) vertexData.length * Float.BYTES;
		}
		else if (compressedVertexData != null)
		{
			bytes += compressedVertexData.length;
		}
		return bytes;
	}

	private static float[] normalizeVertexData(float[] source)
	{
		if (source == null || source.length < POSITION_FLOATS)
		{
			return new float[0];
		}
		int length = source.length - source.length % POSITION_FLOATS;
		if (length == source.length)
		{
			return source.clone();
		}
		float[] out = new float[length];
		System.arraycopy(source, 0, out, 0, length);
		return out;
	}

	private static Bounds bounds(float[] vertices)
	{
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		for (int i = 0; i + 2 < vertices.length; i += POSITION_FLOATS)
		{
			float x = vertices[i];
			float y = vertices[i + 1];
			float z = vertices[i + 2];
			if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
			{
				continue;
			}
			minX = Math.min(minX, x);
			minY = Math.min(minY, y);
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, y);
			maxZ = Math.max(maxZ, z);
		}
		if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)
			|| !Float.isFinite(maxX) || !Float.isFinite(maxY) || !Float.isFinite(maxZ))
		{
			return new Bounds(0.0f, 0.0f, 0.0f);
		}
		return new Bounds((minX + maxX) * 0.5f, maxY, (minZ + maxZ) * 0.5f);
	}

	private record Bounds(
		float centerX,
		float maxY,
		float centerZ
	)
	{
	}
}
