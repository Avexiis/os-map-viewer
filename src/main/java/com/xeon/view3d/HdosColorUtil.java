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

final class HdosColorUtil
{
	static final int INVALID_HSL_COLOR = 12_345_678;
	private static final int[] HSL_RGB_MAP = buildPalette(0.8D, 0, 512);

	private HdosColorUtil()
	{
	}

	static int packHsl(int hue, int saturation, int lightness)
	{
		if (lightness > 179)
		{
			saturation /= 2;
		}
		if (lightness > 192)
		{
			saturation /= 2;
		}
		if (lightness > 217)
		{
			saturation /= 2;
		}
		if (lightness > 243)
		{
			saturation /= 2;
		}
		return ((saturation / 32) << 7) + ((hue / 4) << 10) + (lightness / 2);
	}

	static int mixHsl(int hslA, int hslB)
	{
		if (hslA == INVALID_HSL_COLOR || hslB == INVALID_HSL_COLOR)
		{
			return INVALID_HSL_COLOR;
		}
		if (hslA == -1)
		{
			return hslB;
		}
		if (hslB == -1)
		{
			return hslA;
		}

		int hue = (hslA >> 10) & 0x3F;
		int saturation = (hslA >> 7) & 0x7;
		int lightness = hslA & 0x7F;
		hue += (hslB >> 10) & 0x3F;
		saturation += (hslB >> 7) & 0x7;
		lightness += hslB & 0x7F;
		return (hue >> 1) << 10 | (saturation >> 1) << 7 | (lightness >> 1);
	}

	static int adjustUnderlayLight(int hsl, int light)
	{
		if (hsl == -1)
		{
			return INVALID_HSL_COLOR;
		}
		return adjustLight(hsl, light);
	}

	static int adjustOverlayLight(int hsl, int light)
	{
		if (hsl == -2)
		{
			return INVALID_HSL_COLOR;
		}
		if (hsl == -1)
		{
			return clamp(light, 2, 126);
		}
		return adjustLight(hsl, light);
	}

	static int rgbForHsl(int hsl)
	{
		if (hsl == INVALID_HSL_COLOR || hsl < 0)
		{
			return 0;
		}
		return HSL_RGB_MAP[hsl & 0xFFFF];
	}

	private static int adjustLight(int hsl, int light)
	{
		int adjustedLight = (hsl & 0x7F) * light >> 7;
		return (hsl & 0xFF80) + clamp(adjustedLight, 2, 126);
	}

	private static int[] buildPalette(double brightness, int start, int end)
	{
		int[] palette = new int[0x10000];
		int paletteIndex = start * 128;
		for (int h = start; h < end; h++)
		{
			double hue = (h >> 3) / 64.0D + 0.0078125D;
			double saturation = (h & 7) / 8.0D + 0.0625D;
			for (int l = 0; l < 128; l++)
			{
				double light = l / 128.0D;
				double r = light;
				double g = light;
				double b = light;
				if (saturation != 0.0D)
				{
					double value = light < 0.5D
						? light * (1.0D + saturation)
						: light + saturation - light * saturation;
					double low = 2.0D * light - value;
					r = hueToRgb(low, value, hue + 0.3333333333333333D);
					g = hueToRgb(low, value, hue);
					b = hueToRgb(low, value, hue - 0.3333333333333333D);
				}

				int rgb = (int) (r * 256.0D) << 16 | (int) (g * 256.0D) << 8 | (int) (b * 256.0D);
				palette[paletteIndex++] = brightenRgb(rgb, brightness);
			}
		}
		return palette;
	}

	private static double hueToRgb(double low, double value, double hue)
	{
		if (hue > 1.0D)
		{
			hue -= 1.0D;
		}
		if (hue < 0.0D)
		{
			hue += 1.0D;
		}
		if (6.0D * hue < 1.0D)
		{
			return low + (value - low) * 6.0D * hue;
		}
		if (2.0D * hue < 1.0D)
		{
			return value;
		}
		if (3.0D * hue < 2.0D)
		{
			return low + (value - low) * (0.6666666666666666D - hue) * 6.0D;
		}
		return low;
	}

	private static int brightenRgb(int rgb, double brightness)
	{
		double r = (double) (rgb >> 16 & 0xFF) / 256.0D;
		double g = (double) (rgb >> 8 & 0xFF) / 256.0D;
		double b = (double) (rgb & 0xFF) / 256.0D;
		r = Math.pow(r, brightness);
		g = Math.pow(g, brightness);
		b = Math.pow(b, brightness);
		return (int) (r * 256.0D) << 16 | (int) (g * 256.0D) << 8 | (int) (b * 256.0D);
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
