/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis> (OS Map Viewer)
 * Copyright (c) 2017, Adam <Adam@sigterm.info> (RuneLite)
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

import net.runelite.cache.definitions.OverlayDefinition;
import net.runelite.cache.definitions.UnderlayDefinition;
import net.runelite.cache.definitions.providers.OverlayProvider;
import net.runelite.cache.definitions.providers.UnderlayProvider;
import net.runelite.cache.item.RSTextureProvider;
import net.runelite.cache.models.JagexColor;
import net.runelite.cache.region.Region;

final class TerrainColorizer
{
	private static final int BLEND = 5;
	private static final int TRANSPARENT_MAGENTA = 0xFF_00FF;
	private static final int DEFAULT_TERRAIN_RGB = 0x2F3430;

	private final TerrainRegionContext regionContext;
	private final UnderlayProvider underlays;
	private final OverlayProvider overlays;
	private final RSTextureProvider textureProvider;
	private final TerrainFloorTextures floorTextures;
	private final SceneTextureSet textureSet;
	private final TerrainTilePaint[][] paints = new TerrainTilePaint[Region.X][Region.Y];

	TerrainColorizer(
		TerrainRegionContext regionContext,
		UnderlayProvider underlays,
		OverlayProvider overlays,
		RSTextureProvider textureProvider,
		TerrainFloorTextures floorTextures,
		SceneTextureSet textureSet,
		int plane
	)
	{
		this.regionContext = regionContext;
		this.underlays = underlays;
		this.overlays = overlays;
		this.textureProvider = textureProvider;
		this.floorTextures = floorTextures;
		this.textureSet = textureSet;
		build(plane);
	}

	TerrainTilePaint paintFor(int tileX, int tileY)
	{
		return paints[tileX][tileY];
	}

	int underlayColorFor(TerrainTilePaint paint)
	{
		return paint != null && paint.hasUnderlay() ? paint.underlayRgb() : DEFAULT_TERRAIN_RGB;
	}

	int overlayColorFor(TerrainTilePaint paint)
	{
		if (paint == null)
		{
			return DEFAULT_TERRAIN_RGB;
		}
		if (paint.hasOverlay())
		{
			return paint.overlayRgb();
		}
		return underlayColorFor(paint);
	}

	private void build(int plane)
	{
		for (int x = 0; x < Region.X; x++)
		{
			for (int y = 0; y < Region.Y; y++)
			{
				int underlayId = regionContext.underlayId(plane, x, y);
				int overlayId = regionContext.overlayId(plane, x, y);
				int underlayRgb = underlayId > 0 ? blendedUnderlayRgb(plane, x, y) : 0;
				int overlayRgb = overlayId > 0 ? overlayRgb(overlayId - 1) : 0;
				int underlayTextureLayer = underlayId > 0 ? textureLayerForUnderlay(underlayId - 1) : 0;
				int overlayTextureLayer = overlayId > 0 ? textureLayerForOverlay(overlayId - 1) : 0;
				boolean hasUnderlay = underlayRgb != 0 || underlayTextureLayer > 0;
				boolean hasOverlay = overlayRgb != 0 || overlayTextureLayer > 0;
				paints[x][y] = new TerrainTilePaint(
					underlayRgb,
					overlayRgb,
					regionContext.overlayPath(plane, x, y),
					regionContext.overlayRotation(plane, x, y),
					underlayTextureLayer,
					overlayTextureLayer,
					hasUnderlay,
					hasOverlay
				);
			}
		}
	}

	private int blendedUnderlayRgb(int plane, int tileX, int tileY)
	{
		int hues = 0;
		int saturations = 0;
		int lights = 0;
		int count = 0;

		for (int dx = -BLEND; dx <= BLEND; dx++)
		{
			for (int dy = -BLEND; dy <= BLEND; dy++)
			{
				int x = tileX + dx;
				int y = tileY + dy;
				int underlayId = regionContext.underlayId(plane, x, y);
				if (underlayId <= 0)
				{
					continue;
				}
				UnderlayDefinition underlay = underlays.provide(underlayId - 1);
				if (underlay == null)
				{
					continue;
				}

				int multiplier = Math.max(1, underlay.getHueMultiplier());
				hues += underlay.getHue() * 256 / multiplier;
				saturations += underlay.getSaturation();
				lights += underlay.getLightness();
				count++;
			}
		}

		if (count == 0)
		{
			return 0;
		}

		int hue = hues / count;
		int saturation = saturations / count;
		int light = Math.max(0, Math.min(255, lights / count));
		return JagexColor.getRGBFull(JagexColor.packHSLFull(hue, saturation, light));
	}

	private int overlayRgb(int overlayId)
	{
		OverlayDefinition overlay = overlays.provide(overlayId);
		if (overlay == null)
		{
			return 0;
		}

		int hsl;
		if (overlay.getTexture() >= 0)
		{
			hsl = textureHsl(overlay.getTexture());
			if (hsl == -2)
			{
				hsl = overlayHsl(overlay);
			}
		}
		else
		{
			hsl = overlayHsl(overlay);
		}

		if (hsl == -2)
		{
			return 0;
		}

		hsl = adjustHslLightness(hsl);
		int rgb = JagexColor.getRGBFull(hsl);
		if (overlay.getSecondaryRgbColor() != -1)
		{
			int secondaryHsl = JagexColor.packHSLFull(
				overlay.getOtherHue(),
				overlay.getOtherSaturation(),
				overlay.getOtherLightness()
			);
			rgb = JagexColor.getRGBFull(secondaryHsl);
		}
		return rgb;
	}

	private int textureLayerForUnderlay(int underlayId)
	{
		int texture = floorTextures.underlayTexture(underlayId);
		return texture < 0 ? 0 : textureSet.layerForTexture(texture);
	}

	private int textureLayerForOverlay(int overlayId)
	{
		int texture = floorTextures.overlayTexture(overlayId);
		if (texture < 0)
		{
			OverlayDefinition overlay = overlays.provide(overlayId);
			texture = overlay == null ? -1 : overlay.getTexture();
		}
		return texture < 0 ? 0 : textureSet.layerForTexture(texture);
	}

	private int overlayHsl(OverlayDefinition overlay)
	{
		if (overlay.getRgbColor() == TRANSPARENT_MAGENTA)
		{
			return -2;
		}
		return JagexColor.packHSLFull(overlay.getHue(), overlay.getSaturation(), overlay.getLightness());
	}

	private int textureHsl(int texture)
	{
		if (textureProvider == null)
		{
			return -2;
		}
		try
		{
			int hsl = textureProvider.getAverageTextureRGB(texture);
			return JagexColor.packHSLFull(
				JagexColor.unpackHue((short) hsl) * 4,
				JagexColor.unpackSaturation((short) hsl) * 32,
				JagexColor.unpackLuminance((short) hsl) * 2
			);
		}
		catch (RuntimeException ex)
		{
			return -2;
		}
	}

	private static int adjustHslLightness(int hsl)
	{
		double multiplier = 0.898D;
		int hue = JagexColor.unpackHueFull(hsl);
		int saturation = JagexColor.unpackSaturationFull(hsl);
		int light = JagexColor.unpackLuminanceFull(hsl);
		int constant = (int) (light * multiplier);
		if (constant < 2)
		{
			constant = 2;
		}
		else if (constant > 255)
		{
			constant = 255;
		}
		return JagexColor.packHSLFull(hue, saturation, constant);
	}
}
