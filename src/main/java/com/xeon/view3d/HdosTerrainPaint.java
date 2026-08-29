/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * Copyright (c) 2022-2023, dennisdev
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

record HdosTerrainPaint(
	int underlayHslSw,
	int underlayHslSe,
	int underlayHslNe,
	int underlayHslNw,
	int overlayHslSw,
	int overlayHslSe,
	int overlayHslNe,
	int overlayHslNw,
	int overlayPath,
	int overlayRotation,
	int overlayTextureLayer,
	boolean hasUnderlay,
	boolean hasOverlay
)
{
	int underlayRgbForVertex(int vertexType)
	{
		return HdosColorUtil.rgbForHsl(underlayHslForVertex(vertexType));
	}

	int overlayRgbForVertex(int vertexType)
	{
		return HdosColorUtil.rgbForHsl(overlayHslForVertex(vertexType));
	}

	private int underlayHslForVertex(int vertexType)
	{
		return switch (vertexType)
		{
			case 1, 13 -> underlayHslSw;
			case 2, 9 -> HdosColorUtil.mixHsl(underlayHslSe, underlayHslSw);
			case 3, 14 -> underlayHslSe;
			case 4, 10 -> HdosColorUtil.mixHsl(underlayHslSe, underlayHslNe);
			case 5, 15 -> underlayHslNe;
			case 6, 11 -> HdosColorUtil.mixHsl(underlayHslNw, underlayHslNe);
			case 7, 16 -> underlayHslNw;
			case 8, 12 -> HdosColorUtil.mixHsl(underlayHslNw, underlayHslSw);
			default -> HdosColorUtil.INVALID_HSL_COLOR;
		};
	}

	private int overlayHslForVertex(int vertexType)
	{
		return switch (vertexType)
		{
			case 1, 13 -> overlayHslSw;
			case 2, 9 -> averageOverlayHsl(overlayHslSe, overlayHslSw);
			case 3, 14 -> overlayHslSe;
			case 4, 10 -> averageOverlayHsl(overlayHslSe, overlayHslNe);
			case 5, 15 -> overlayHslNe;
			case 6, 11 -> averageOverlayHsl(overlayHslNw, overlayHslNe);
			case 7, 16 -> overlayHslNw;
			case 8, 12 -> averageOverlayHsl(overlayHslNw, overlayHslSw);
			default -> HdosColorUtil.INVALID_HSL_COLOR;
		};
	}

	private static int averageOverlayHsl(int a, int b)
	{
		if (a == HdosColorUtil.INVALID_HSL_COLOR || b == HdosColorUtil.INVALID_HSL_COLOR)
		{
			return HdosColorUtil.INVALID_HSL_COLOR;
		}
		if (a < 0)
		{
			return b;
		}
		if (b < 0)
		{
			return a;
		}
		return (a + b) >> 1;
	}
}
