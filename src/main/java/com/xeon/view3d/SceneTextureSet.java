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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.TextureManager;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.TextureDefinition;

final class SceneTextureSet
{
	static final int TEXTURE_SIZE = 128;
	static final int FALLBACK_LAYER = 0;
	private static final int PIXELS_PER_TEXTURE = TEXTURE_SIZE * TEXTURE_SIZE;
	private static final int FALLBACK_ARGB = 0xFFFF_FFFF;
	private static final float DEFAULT_ALPHA_CUTOFF = 0.5f;
	private static final float ANIMATED_ALPHA_CUTOFF = 0.1f;
	private static final float[][] ANIMATION_DIRECTIONS = {
		{0.0f, 0.0f},
		{0.0f, -1.0f},
		{-1.0f, 0.0f},
		{0.0f, 1.0f},
		{1.0f, 0.0f}
	};

	private final int[] pixelsArgb;
	private final Material[] materials;
	private final Map<Integer, Integer> textureLayers;

	private SceneTextureSet(int[] pixelsArgb, Material[] materials, Map<Integer, Integer> textureLayers)
	{
		this.pixelsArgb = Arrays.copyOf(pixelsArgb, pixelsArgb.length);
		this.materials = Arrays.copyOf(materials, materials.length);
		this.textureLayers = Map.copyOf(textureLayers);
	}

	static SceneTextureSet empty()
	{
		int[] pixelsArgb = new int[PIXELS_PER_TEXTURE];
		Arrays.fill(pixelsArgb, FALLBACK_ARGB);
		return new SceneTextureSet(pixelsArgb, new Material[]{Material.NONE}, Map.of());
	}

	static SceneTextureSet build(TextureManager textureManager, SpriteManager spriteManager)
	{
		List<int[]> layerPixels = new ArrayList<>();
		List<Material> layerMaterials = new ArrayList<>();
		Map<Integer, Integer> textureLayers = new HashMap<>();
		int[] fallback = new int[PIXELS_PER_TEXTURE];
		Arrays.fill(fallback, FALLBACK_ARGB);
		layerPixels.add(fallback);
		layerMaterials.add(Material.NONE);

		for (TextureDefinition definition : textureManager.getTextures())
		{
			try
			{
				int[] pixels = pixelsFor(definition, spriteManager);
				if (pixels == null)
				{
					continue;
				}
				int layer = layerPixels.size();
				layerPixels.add(pixels);
				layerMaterials.add(materialFor(definition));
				textureLayers.put(definition.getId(), layer);
			}
			catch (RuntimeException ex)
			{
				System.err.println("Failed to load scene texture " + definition.getId() + ": " + ex.getMessage());
			}
		}

		int[] pixelsArgb = new int[layerPixels.size() * PIXELS_PER_TEXTURE];
		for (int layer = 0; layer < layerPixels.size(); layer++)
		{
			System.arraycopy(layerPixels.get(layer), 0, pixelsArgb, layer * PIXELS_PER_TEXTURE, PIXELS_PER_TEXTURE);
		}
		return new SceneTextureSet(pixelsArgb, layerMaterials.toArray(Material[]::new), textureLayers);
	}

	int layerForTexture(int textureId)
	{
		return textureLayers.getOrDefault(textureId, FALLBACK_LAYER);
	}

	Material materialForLayer(int layer)
	{
		if (layer <= FALLBACK_LAYER || layer >= materials.length)
		{
			return Material.NONE;
		}
		return materials[layer];
	}

	int[] pixelsArgb()
	{
		return Arrays.copyOf(pixelsArgb, pixelsArgb.length);
	}

	int layerCount()
	{
		return materials.length;
	}

	private static int[] pixelsFor(TextureDefinition definition, SpriteManager spriteManager)
	{
		int[] fileIds = definition.getFileIds();
		if (fileIds == null || fileIds.length == 0)
		{
			return null;
		}

		int[] pixels = new int[PIXELS_PER_TEXTURE];
		for (int fileIndex = 0; fileIndex < fileIds.length; fileIndex++)
		{
			SpriteDefinition sprite = spriteManager.provide(fileIds[fileIndex], 0);
			if (sprite == null)
			{
				return null;
			}
			sprite.normalize();
			byte[] palettePixels = sprite.getPixelIdx();
			int[] palette = Arrays.copyOf(sprite.getPalette(), sprite.getPalette().length);
			applyPaletteTransform(definition, fileIndex, palette);
			for (int i = 0; i < palette.length; i++)
			{
				int rgb = brightenRgb(palette[i], 1.0D);
				int alpha = rgb == 0 ? 0 : 0xFF;
				palette[i] = alpha << 24 | rgb;
			}

			int spriteType = fileIndex == 0 || definition.getField1780() == null ? 0 : definition.getField1780()[fileIndex - 1];
			if (spriteType == 0)
			{
				copyTexturePixels(sprite, palettePixels, palette, pixels);
			}
		}
		return pixels;
	}

	private static void copyTexturePixels(
		SpriteDefinition sprite,
		byte[] palettePixels,
		int[] palette,
		int[] pixels
	)
	{
		int spriteWidth = sprite.getMaxWidth();
		if (TEXTURE_SIZE == spriteWidth)
		{
			for (int i = 0; i < pixels.length; i++)
			{
				pixels[i] = palette[palettePixels[i] & 0xFF];
			}
			return;
		}

		if (spriteWidth == 64)
		{
			int out = 0;
			for (int y = 0; y < TEXTURE_SIZE; y++)
			{
				for (int x = 0; x < TEXTURE_SIZE; x++)
				{
					pixels[out++] = palette[palettePixels[(y >> 1 << 6) + (x >> 1)] & 0xFF];
				}
			}
			return;
		}

		if (spriteWidth != 128)
		{
			throw new IllegalArgumentException("Unsupported texture sprite size: " + spriteWidth);
		}
	}

	private static void applyPaletteTransform(TextureDefinition definition, int fileIndex, int[] palette)
	{
		int[] transforms = definition.getField1786();
		if (transforms == null || fileIndex >= transforms.length)
		{
			return;
		}

		int transform = transforms[fileIndex];
		if ((transform & 0xFF00_0000) != 0x0300_0000)
		{
			return;
		}

		int redBlue = transform & 0x00FF_00FF;
		int green = transform >> 8 & 0xFF;
		for (int i = 0; i < palette.length; i++)
		{
			int color = palette[i];
			if (color >> 8 == (color & 0xFFFF))
			{
				int blue = color & 0xFF;
				palette[i] = redBlue * blue >> 8 & 0x00FF_00FF | green * blue & 0x00FF00;
			}
		}
	}

	private static Material materialFor(TextureDefinition definition)
	{
		int direction = definition.getAnimationDirection();
		int speed = definition.getAnimationSpeed();
		float animationU = 0.0f;
		float animationV = 0.0f;
		if (direction > 0 && direction < ANIMATION_DIRECTIONS.length)
		{
			animationU = ANIMATION_DIRECTIONS[direction][0] * speed;
			animationV = ANIMATION_DIRECTIONS[direction][1] * speed;
		}
		float alphaCutoff = animationU != 0.0f || animationV != 0.0f ? ANIMATED_ALPHA_CUTOFF : DEFAULT_ALPHA_CUTOFF;
		return new Material(animationU, animationV, alphaCutoff);
	}

	private static int brightenRgb(int rgb, double brightness)
	{
		double red = (double) (rgb >> 16 & 0xFF) / 256.0D;
		double green = (double) (rgb >> 8 & 0xFF) / 256.0D;
		double blue = (double) (rgb & 0xFF) / 256.0D;
		red = Math.pow(red, brightness);
		green = Math.pow(green, brightness);
		blue = Math.pow(blue, brightness);
		return (int) (red * 256.0D) << 16 | (int) (green * 256.0D) << 8 | (int) (blue * 256.0D);
	}

	record Material(
		float animationU,
		float animationV,
		float alphaCutoff
	)
	{
		private static final Material NONE = new Material(0.0f, 0.0f, 0.0f);
	}
}
