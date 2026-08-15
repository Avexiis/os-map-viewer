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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;

final class TerrainFloorTextures
{
	private static final int NO_TEXTURE = -1;

	private final Map<Integer, Integer> underlayTextures;
	private final Map<Integer, Integer> overlayTextures;

	private TerrainFloorTextures(Map<Integer, Integer> underlayTextures, Map<Integer, Integer> overlayTextures)
	{
		this.underlayTextures = Map.copyOf(underlayTextures);
		this.overlayTextures = Map.copyOf(overlayTextures);
	}

	static TerrainFloorTextures empty()
	{
		return new TerrainFloorTextures(Map.of(), Map.of());
	}

	static TerrainFloorTextures load(Store store)
	{
		try
		{
			return new TerrainFloorTextures(
				loadUnderlayTextures(store),
				loadOverlayTextures(store)
			);
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load floor texture metadata for 3D terrain: " + ex.getMessage());
			return empty();
		}
	}

	int underlayTexture(int underlayId)
	{
		return underlayTextures.getOrDefault(underlayId, NO_TEXTURE);
	}

	int overlayTexture(int overlayId)
	{
		return overlayTextures.getOrDefault(overlayId, NO_TEXTURE);
	}

	private static Map<Integer, Integer> loadUnderlayTextures(Store store) throws IOException
	{
		Map<Integer, Integer> textures = new HashMap<>();
		for (FSFile file : filesFor(store, ConfigType.UNDERLAY))
		{
			int texture = decodeUnderlayTexture(file.getContents());
			if (texture >= 0)
			{
				textures.put(file.getFileId(), texture);
			}
		}
		return textures;
	}

	private static Map<Integer, Integer> loadOverlayTextures(Store store) throws IOException
	{
		Map<Integer, Integer> textures = new HashMap<>();
		for (FSFile file : filesFor(store, ConfigType.OVERLAY))
		{
			int texture = decodeOverlayTexture(file.getContents());
			if (texture >= 0)
			{
				textures.put(file.getFileId(), texture);
			}
		}
		return textures;
	}

	private static Iterable<FSFile> filesFor(Store store, ConfigType configType) throws IOException
	{
		Index index = store.getIndex(IndexType.CONFIGS);
		Archive archive = index.getArchive(configType.getId());
		byte[] archiveData = store.getStorage().loadArchive(archive);
		ArchiveFiles files = archive.getFiles(archiveData);
		return files.getFiles();
	}

	private static int decodeUnderlayTexture(byte[] bytes)
	{
		InputStream input = new InputStream(bytes);
		int texture = NO_TEXTURE;
		while (input.remaining() > 0)
		{
			int opcode = input.readUnsignedByte();
			if (opcode == 0)
			{
				break;
			}
			switch (opcode)
			{
				case 1 -> input.read24BitInt();
				case 2 -> texture = normalizeTexture(input.readUnsignedShort());
				case 3 -> input.readUnsignedShort();
				case 4, 5 ->
				{
				}
				default -> throw new IllegalArgumentException("Unsupported underlay opcode " + opcode);
			}
		}
		return texture;
	}

	private static int decodeOverlayTexture(byte[] bytes)
	{
		InputStream input = new InputStream(bytes);
		int texture = NO_TEXTURE;
		while (input.remaining() > 0)
		{
			int opcode = input.readUnsignedByte();
			if (opcode == 0)
			{
				break;
			}
			switch (opcode)
			{
				case 1, 7, 13 -> input.read24BitInt();
				case 2 -> texture = input.readUnsignedByte();
				case 3 -> texture = normalizeTexture(input.readUnsignedShort());
				case 5, 8, 10, 12 ->
				{
				}
				case 6 -> input.readString();
				case 9, 15 -> input.readUnsignedShort();
				case 11, 14, 16 -> input.readUnsignedByte();
				default -> throw new IllegalArgumentException("Unsupported overlay opcode " + opcode);
			}
		}
		return texture;
	}

	private static int normalizeTexture(int texture)
	{
		return texture == 0xFFFF ? NO_TEXTURE : texture;
	}
}
