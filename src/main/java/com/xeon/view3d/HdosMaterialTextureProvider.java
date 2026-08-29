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

import java.io.IOException;
import java.util.Arrays;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.definitions.providers.SpriteProvider;
import net.runelite.cache.definitions.providers.TextureProvider;
import net.runelite.cache.fs.Store;
import net.runelite.cache.item.RSTextureProvider;

final class HdosMaterialTextureProvider extends RSTextureProvider
{
	private static final int MATERIALS_INDEX = 26;
	private static final int MATERIALS_ARCHIVE = 0;
	private static final int MATERIALS_FILE = 0;
	private static final TextureProvider EMPTY_TEXTURES = () -> new TextureDefinition[0];
	private static final SpriteProvider EMPTY_SPRITES = (spriteId, frameId) -> null;

	private final Material[] materials;

	private HdosMaterialTextureProvider(Material[] materials)
	{
		super(EMPTY_TEXTURES, EMPTY_SPRITES);
		this.materials = Arrays.copyOf(materials, materials.length);
	}

	static HdosMaterialTextureProvider load(Store store) throws IOException
	{
		if (!(store.getStorage() instanceof DispleeCacheStorage storage))
		{
			throw new IOException("HDOS material decoder requires Displee cache storage.");
		}

		byte[] data = storage.loadArchiveFile(MATERIALS_INDEX, MATERIALS_ARCHIVE, MATERIALS_FILE, null);
		if (data == null || data.length == 0)
		{
			return new HdosMaterialTextureProvider(new Material[0]);
		}
		return new HdosMaterialTextureProvider(decodeMaterials(data));
	}

	@Override
	public int[] load(int textureId)
	{
		return null;
	}

	@Override
	public int getAverageTextureRGB(int textureId)
	{
		Material material = material(textureId);
		return material == null || !material.valid() ? -2 : material.averageHsl();
	}

	@Override
	public boolean vmethod3057(int textureId)
	{
		Material material = material(textureId);
		return material != null && material.valid();
	}

	@Override
	public boolean vmethod3066(int textureId)
	{
		Material material = material(textureId);
		return material != null && material.small();
	}

	boolean isValid(int textureId)
	{
		Material material = material(textureId);
		return material != null && material.valid();
	}

	SceneTextureSet.Material sceneMaterial(int textureId)
	{
		Material material = material(textureId);
		if (material == null)
		{
			return new SceneTextureSet.Material(0.0f, 0.0f, 0.0f);
		}
		float alphaCutoff = material.animU() != 0 || material.animV() != 0 || material.alphaMode() == 2
			? 0.01f
			: 0.9f;
		return new SceneTextureSet.Material(material.animU(), material.animV(), alphaCutoff);
	}

	private Material material(int textureId)
	{
		return textureId < 0 || textureId >= materials.length ? null : materials[textureId];
	}

	private static Material[] decodeMaterials(byte[] data)
	{
		HdosByteBuffer input = new HdosByteBuffer(data);
		int count = input.readUnsignedShort();
		Material.Mutable[] mutable = new Material.Mutable[count];
		for (int i = 0; i < count; i++)
		{
			if (input.readUnsignedByte() == 1)
			{
				mutable[i] = new Material.Mutable();
			}
		}
		forEachMaterial(mutable, material -> material.valid = input.readUnsignedByte() == 1);
		forEachMaterial(mutable, material -> material.small = input.readUnsignedByte() == 1);
		forEachMaterial(mutable, material -> material.disabled = input.readUnsignedByte() == 1);
		forEachMaterial(mutable, material -> material.brightness = input.readByte());
		forEachMaterial(mutable, material -> material.blanch = input.readByte());
		forEachMaterial(mutable, material -> material.shaderId = input.readByte());
		forEachMaterial(mutable, material -> material.shaderParam = input.readByte());
		forEachMaterial(mutable, material -> material.averageHsl = input.readUnsignedShort());

		if (input.remaining() > 0)
		{
			forEachMaterial(mutable, material -> material.animU = input.readByte());
			forEachMaterial(mutable, material -> material.animV = input.readByte());
			forEachMaterial(mutable, material -> input.readByte());
			forEachMaterial(mutable, material -> material.flipV = input.readUnsignedByte() == 1);
			forEachMaterial(mutable, material -> material.mipmap = input.readByte());
			forEachMaterial(mutable, material -> material.repeatS = input.readUnsignedByte() == 1);
			forEachMaterial(mutable, material -> material.repeatT = input.readUnsignedByte() == 1);
			forEachMaterial(mutable, material -> material.floatTexture = input.readUnsignedByte() == 1);
			if (input.remaining() >= count)
			{
				forEachMaterial(mutable, material -> material.combineMode = input.readUnsignedByte());
			}
			if (input.remaining() >= count * Integer.BYTES)
			{
				forEachMaterial(mutable, material -> material.shaderParam2 = input.readInt());
			}
			if (input.remaining() >= count)
			{
				forEachMaterial(mutable, material -> material.alphaMode = input.readUnsignedByte());
			}
		}

		Material[] materials = new Material[count];
		for (int i = 0; i < count; i++)
		{
			Material.Mutable material = mutable[i];
			materials[i] = material == null ? null : material.toMaterial();
		}
		return materials;
	}

	private static void forEachMaterial(Material.Mutable[] materials, MaterialReader reader)
	{
		for (Material.Mutable material : materials)
		{
			if (material != null)
			{
				reader.read(material);
			}
		}
	}

	private interface MaterialReader
	{
		void read(Material.Mutable material);
	}

	record Material(
		boolean valid,
		boolean small,
		boolean disabled,
		byte brightness,
		byte blanch,
		byte shaderId,
		byte shaderParam,
		int averageHsl,
		byte animU,
		byte animV,
		boolean flipV,
		byte mipmap,
		boolean repeatS,
		boolean repeatT,
		boolean floatTexture,
		int combineMode,
		int shaderParam2,
		int alphaMode
	)
	{
		private static final class Mutable
		{
			private boolean valid;
			private boolean small;
			private boolean disabled;
			private byte brightness;
			private byte blanch;
			private byte shaderId;
			private byte shaderParam;
			private int averageHsl;
			private byte animU;
			private byte animV;
			private boolean flipV;
			private byte mipmap;
			private boolean repeatS;
			private boolean repeatT;
			private boolean floatTexture;
			private int combineMode;
			private int shaderParam2;
			private int alphaMode;

			private Material toMaterial()
			{
				return new Material(
					valid,
					small,
					disabled,
					brightness,
					blanch,
					shaderId,
					shaderParam,
					averageHsl,
					animU,
					animV,
					flipV,
					mipmap,
					repeatS,
					repeatT,
					floatTexture,
					combineMode,
					shaderParam2,
					alphaMode
				);
			}
		}
	}
}
