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
import net.runelite.cache.definitions.OverlayDefinition;
import net.runelite.cache.definitions.UnderlayDefinition;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;

final class HdosFloorDefinitions
{
	private static final int NO_TEXTURE = -1;

	private HdosFloorDefinitions()
	{
	}

	static Map<Integer, UnderlayDefinition> loadUnderlays(Store store) throws IOException
	{
		Map<Integer, UnderlayDefinition> underlays = new HashMap<>();
		for (DispleeCacheStorage.DecodedFile file : filesFor(store, ConfigType.UNDERLAY))
		{
			UnderlayDefinition definition = decodeUnderlay(file.id(), file.data());
			underlays.put(definition.getId(), definition);
		}
		return underlays;
	}

	static Map<Integer, OverlayDefinition> loadOverlays(Store store) throws IOException
	{
		Map<Integer, OverlayDefinition> overlays = new HashMap<>();
		for (DispleeCacheStorage.DecodedFile file : filesFor(store, ConfigType.OVERLAY))
		{
			OverlayDefinition definition = decodeOverlay(file.id(), file.data());
			overlays.put(definition.getId(), definition);
		}
		return overlays;
	}

	private static Iterable<DispleeCacheStorage.DecodedFile> filesFor(Store store, ConfigType configType)
		throws IOException
	{
		Index index = store.getIndex(IndexType.CONFIGS);
		if (index == null || index.getArchive(configType.getId()) == null)
		{
			return java.util.List.of();
		}
		if (store.getStorage() instanceof DispleeCacheStorage storage)
		{
			return storage.loadArchiveFiles(IndexType.CONFIGS.getNumber(), configType.getId());
		}
		throw new IOException("HDOS floor decoder requires Displee cache storage.");
	}

	private static UnderlayDefinition decodeUnderlay(int id, byte[] bytes)
	{
		UnderlayDefinition definition = new UnderlayDefinition();
		definition.setId(id);
		InputStream input = new InputStream(bytes);
		while (input.remaining() > 0)
		{
			int opcode = input.readUnsignedByte();
			if (opcode == 0)
			{
				break;
			}
			switch (opcode)
			{
				case 1 -> definition.setColor(input.read24BitInt());
				case 2, 3 -> input.readUnsignedShort();
				case 4, 5 ->
				{
				}
				default -> throw new IllegalArgumentException("Unsupported HDOS underlay opcode " + opcode);
			}
		}
		definition.calculateHsl();
		return definition;
	}

	private static OverlayDefinition decodeOverlay(int id, byte[] bytes)
	{
		OverlayDefinition definition = new OverlayDefinition();
		definition.setId(id);
		InputStream input = new InputStream(bytes);
		while (input.remaining() > 0)
		{
			int opcode = input.readUnsignedByte();
			if (opcode == 0)
			{
				break;
			}
			switch (opcode)
			{
				case 1 -> definition.setRgbColor(input.read24BitInt());
				case 2 -> definition.setTexture(input.readUnsignedByte());
				case 3 -> definition.setTexture(normalizeTexture(input.readUnsignedShort()));
				case 5 -> definition.setHideUnderlay(false);
				case 6 -> input.readString();
				case 7 -> definition.setSecondaryRgbColor(input.read24BitInt());
				case 8, 10, 12 ->
				{
				}
				case 9, 15 -> input.readUnsignedShort();
				case 11, 14, 16 -> input.readUnsignedByte();
				case 13 -> input.read24BitInt();
				default -> throw new IllegalArgumentException("Unsupported HDOS overlay opcode " + opcode);
			}
		}
		definition.calculateHsl();
		return definition;
	}

	private static int normalizeTexture(int texture)
	{
		return texture == 0xFFFF ? NO_TEXTURE : texture;
	}
}
