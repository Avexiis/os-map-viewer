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

final class TileShapeModel
{
	static final int SIMPLE_UNDERLAY_TYPE = 0;
	static final int SIMPLE_OVERLAY_TYPE = 1;
	static final int FACE_STRIDE = 4;
	static final int FACE_UNDERLAY = 0;
	static final int FACE_OVERLAY = 1;

	private static final float TILE_COORDINATE_SCALE = 128.0f;
	private static final int[][] VERTEX_TYPES = new int[][]{
		new int[]{1, 3, 5, 7},
		new int[]{1, 3, 5, 7},
		new int[]{1, 3, 5, 7},
		new int[]{1, 3, 5, 7, 6},
		new int[]{1, 3, 5, 7, 6},
		new int[]{1, 3, 5, 7, 6},
		new int[]{1, 3, 5, 7, 6},
		new int[]{1, 3, 5, 7, 2, 6},
		new int[]{1, 3, 5, 7, 2, 8},
		new int[]{1, 3, 5, 7, 2, 8},
		new int[]{1, 3, 5, 7, 11, 12},
		new int[]{1, 3, 5, 7, 11, 12},
		new int[]{1, 3, 5, 7, 13, 14}
	};
	private static final int[][] FACE_DATA = new int[][]{
		new int[]{0, 1, 2, 3, 0, 0, 1, 3},
		new int[]{1, 1, 2, 3, 1, 0, 1, 3},
		new int[]{0, 1, 2, 3, 1, 0, 1, 3},
		new int[]{0, 0, 1, 2, 0, 0, 2, 4, 1, 0, 4, 3},
		new int[]{0, 0, 1, 4, 0, 0, 4, 3, 1, 1, 2, 4},
		new int[]{0, 0, 4, 3, 1, 0, 1, 2, 1, 0, 2, 4},
		new int[]{0, 1, 2, 4, 1, 0, 1, 4, 1, 0, 4, 3},
		new int[]{0, 4, 1, 2, 0, 4, 2, 5, 1, 0, 4, 5, 1, 0, 5, 3},
		new int[]{0, 4, 1, 2, 0, 4, 2, 3, 0, 4, 3, 5, 1, 0, 4, 5},
		new int[]{0, 0, 4, 5, 1, 4, 1, 2, 1, 4, 2, 3, 1, 4, 3, 5},
		new int[]{0, 0, 1, 5, 0, 1, 4, 5, 0, 1, 2, 4, 1, 0, 5, 3, 1, 5, 4, 3, 1, 4, 2, 3},
		new int[]{1, 0, 1, 5, 1, 1, 4, 5, 1, 1, 2, 4, 0, 0, 5, 3, 0, 5, 4, 3, 0, 4, 2, 3},
		new int[]{1, 0, 5, 4, 1, 0, 1, 5, 0, 0, 4, 3, 0, 4, 5, 3, 0, 5, 2, 3, 0, 1, 2, 5}
	};

	private TileShapeModel()
	{
	}

	static int shapeTypeFor(int overlayPath)
	{
		return overlayPath + 1;
	}

	static boolean isShaped(int shapeType)
	{
		return shapeType > SIMPLE_OVERLAY_TYPE && shapeType < VERTEX_TYPES.length;
	}

	static int[] vertexTypes(int shapeType)
	{
		return VERTEX_TYPES[shapeType];
	}

	static int[] faceData(int shapeType)
	{
		return FACE_DATA[shapeType];
	}

	static int rotateVertexType(int vertexType, int orientation)
	{
		orientation &= 3;
		if ((vertexType & 1) == 0 && vertexType <= 8)
		{
			return ((vertexType - orientation - orientation - 1) & 7) + 1;
		}
		if (vertexType > 8 && vertexType <= 12)
		{
			return ((vertexType - 9 - orientation) & 3) + 9;
		}
		if (vertexType > 12 && vertexType <= 16)
		{
			return ((vertexType - 13 - orientation) & 3) + 13;
		}
		return vertexType;
	}

	static int rotateFaceVertex(int vertexIndex, int orientation)
	{
		if (vertexIndex < 4)
		{
			return (vertexIndex - orientation) & 3;
		}
		return vertexIndex;
	}

	static float localX(int vertexType)
	{
		return switch (vertexType)
		{
			case 1, 7, 8 -> 0.0f;
			case 12, 13, 16 -> 32.0f / TILE_COORDINATE_SCALE;
			case 2, 6, 9, 11 -> 64.0f / TILE_COORDINATE_SCALE;
			case 10, 14, 15 -> 96.0f / TILE_COORDINATE_SCALE;
			case 3, 4, 5 -> 1.0f;
			default -> 0.0f;
		};
	}

	static float localY(int vertexType)
	{
		return switch (vertexType)
		{
			case 1, 2, 3 -> 0.0f;
			case 9, 13, 14 -> 32.0f / TILE_COORDINATE_SCALE;
			case 4, 8, 10, 12 -> 64.0f / TILE_COORDINATE_SCALE;
			case 11, 15, 16 -> 96.0f / TILE_COORDINATE_SCALE;
			case 5, 6, 7 -> 1.0f;
			default -> 0.0f;
		};
	}
}
