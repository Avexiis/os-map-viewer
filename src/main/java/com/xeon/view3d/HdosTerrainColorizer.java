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

import net.runelite.cache.definitions.OverlayDefinition;
import net.runelite.cache.definitions.UnderlayDefinition;
import net.runelite.cache.definitions.providers.OverlayProvider;
import net.runelite.cache.definitions.providers.UnderlayProvider;
import net.runelite.cache.item.RSTextureProvider;
import net.runelite.cache.region.Region;

final class HdosTerrainColorizer
{
	private static final int BLEND_RADIUS = 5;
	private static final int TRANSPARENT_MAGENTA = 0xFF_00FF;

	private final TerrainRegionContext regionContext;
	private final OverlayProvider overlays;
	private final RSTextureProvider textureProvider;
	private final TerrainFloorTextures floorTextures;
	private final SceneTextureSet textureSet;
	private final HdosTerrainPaint[][] paints = new HdosTerrainPaint[Region.X][Region.Y];

	HdosTerrainColorizer(
		TerrainRegionContext regionContext,
		UnderlayProvider underlays,
		OverlayProvider overlays,
		RSTextureProvider textureProvider,
		TerrainFloorTextures floorTextures,
		SceneTextureSet textureSet,
		TerrainHeightMap heightMap,
		int[][] lightOcclusions,
		int plane
	)
	{
		this.regionContext = regionContext;
		this.overlays = overlays;
		this.textureProvider = textureProvider;
		this.floorTextures = floorTextures;
		this.textureSet = textureSet;
		build(underlays, heightMap, lightOcclusions, plane);
	}

	HdosTerrainPaint paintFor(int tileX, int tileY)
	{
		return paints[tileX][tileY];
	}

	private void build(UnderlayProvider underlays, TerrainHeightMap heightMap, int[][] lightOcclusions, int plane)
	{
		int[][] blendedUnderlays = blendUnderlays(underlays, plane);
		int[][] tileLights = tileLights(heightMap, lightOcclusions);
		for (int x = 0; x < Region.X; x++)
		{
			for (int y = 0; y < Region.Y; y++)
			{
				int underlayId = regionContext.underlayId(plane, x, y);
				int overlayId = regionContext.overlayId(plane, x, y);
				int underlaySw = -1;
				int underlaySe = -1;
				int underlayNe = -1;
				int underlayNw = -1;
				if (underlayId > 0)
				{
					underlaySw = blendedUnderlays[x][y];
					underlaySe = fallbackUnderlay(blendedUnderlays[x + 1][y], underlaySw);
					underlayNe = fallbackUnderlay(blendedUnderlays[x + 1][y + 1], underlaySw);
					underlayNw = fallbackUnderlay(blendedUnderlays[x][y + 1], underlaySw);
				}

				OverlayHsl overlay = overlayId > 0 ? overlayHsl(overlayId - 1) : OverlayHsl.NONE;
				paints[x][y] = new HdosTerrainPaint(
					HdosColorUtil.adjustUnderlayLight(underlaySw, tileLights[x][y]),
					HdosColorUtil.adjustUnderlayLight(underlaySe, tileLights[x + 1][y]),
					HdosColorUtil.adjustUnderlayLight(underlayNe, tileLights[x + 1][y + 1]),
					HdosColorUtil.adjustUnderlayLight(underlayNw, tileLights[x][y + 1]),
					HdosColorUtil.adjustOverlayLight(overlay.hsl(), tileLights[x][y]),
					HdosColorUtil.adjustOverlayLight(overlay.hsl(), tileLights[x + 1][y]),
					HdosColorUtil.adjustOverlayLight(overlay.hsl(), tileLights[x + 1][y + 1]),
					HdosColorUtil.adjustOverlayLight(overlay.hsl(), tileLights[x][y + 1]),
					regionContext.overlayPath(plane, x, y),
					regionContext.overlayRotation(plane, x, y),
					overlay.textureLayer(),
					underlaySw != -1,
					overlay.hasOverlay()
				);
			}
		}
	}

	private int[][] blendUnderlays(UnderlayProvider underlays, int plane)
	{
		int[][] colors = new int[Region.X + 1][Region.Y + 1];
		for (int x = 0; x <= Region.X; x++)
		{
			for (int y = 0; y <= Region.Y; y++)
			{
				colors[x][y] = -1;
				int hues = 0;
				int saturations = 0;
				int lights = 0;
				int multipliers = 0;
				int count = 0;
				for (int dx = -BLEND_RADIUS; dx <= BLEND_RADIUS; dx++)
				{
					for (int dy = -BLEND_RADIUS; dy <= BLEND_RADIUS; dy++)
					{
						int underlayId = regionContext.underlayId(plane, x + dx, y + dy);
						if (underlayId <= 0)
						{
							continue;
						}
						UnderlayDefinition underlay = underlays.provide(underlayId - 1);
						if (underlay == null)
						{
							continue;
						}
						hues += underlay.getHue();
						saturations += underlay.getSaturation();
						lights += underlay.getLightness();
						multipliers += underlay.getHueMultiplier();
						count++;
					}
				}
				if (count > 0 && multipliers > 0)
				{
					int hue = hues * 256 / multipliers;
					colors[x][y] = HdosColorUtil.packHsl(hue, saturations / count, lights / count);
				}
			}
		}
		return colors;
	}

	private int[][] tileLights(TerrainHeightMap heightMap, int[][] lightOcclusions)
	{
		int[][] lights = new int[Region.X + 1][Region.Y + 1];
		for (int x = 0; x <= Region.X; x++)
		{
			for (int y = 0; y <= Region.Y; y++)
			{
				lights[x][y] = HdosSceneLighting.tileLight(heightMap, lightOcclusions, x, y);
			}
		}
		return lights;
	}

	private OverlayHsl overlayHsl(int overlayId)
	{
		OverlayDefinition overlay = overlays.provide(overlayId);
		if (overlay == null)
		{
			return OverlayHsl.NONE;
		}

		int texture = floorTextures.overlayTexture(overlayId);
		if (texture < 0)
		{
			texture = overlay.getTexture();
		}
		int textureLayer = texture < 0 ? 0 : textureSet.layerForTexture(texture);
		if (texture >= 0 && textureProvider != null)
		{
			int textureHsl = textureProvider.getAverageTextureRGB(texture);
			if (textureHsl > 0)
			{
				return new OverlayHsl(textureHsl, textureLayer, true);
			}
		}

		if (overlay.getRgbColor() == TRANSPARENT_MAGENTA)
		{
			return OverlayHsl.NONE;
		}

		int hsl = HdosColorUtil.packHsl(overlay.getHue(), overlay.getSaturation(), overlay.getLightness());
		if (overlay.getSecondaryRgbColor() != -1)
		{
			hsl = HdosColorUtil.packHsl(
				overlay.getOtherHue(),
				overlay.getOtherSaturation(),
				overlay.getOtherLightness()
			);
		}
		return new OverlayHsl(hsl, textureLayer, true);
	}

	private static int fallbackUnderlay(int hsl, int fallback)
	{
		return hsl == -1 ? fallback : hsl;
	}

	private record OverlayHsl(int hsl, int textureLayer, boolean hasOverlay)
	{
		private static final OverlayHsl NONE = new OverlayHsl(-2, 0, false);
	}
}
