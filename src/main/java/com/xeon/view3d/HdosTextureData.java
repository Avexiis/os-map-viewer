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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.definitions.loaders.TextureLoader;
import net.runelite.cache.definitions.providers.SpriteProvider;
import net.runelite.cache.definitions.providers.TextureProvider;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

final class HdosTextureData implements TextureProvider, SpriteProvider
{
	private static final int MAX_TEXTURE_FILES = 4;

	private final DispleeCacheStorage storage;
	private final List<TextureDefinition> textures;
	private final Map<Integer, List<SpriteDefinition>> sprites = new HashMap<>();

	private HdosTextureData(DispleeCacheStorage storage, List<TextureDefinition> textures)
	{
		this.storage = storage;
		this.textures = List.copyOf(textures);
	}

	static HdosTextureData load(Store store) throws IOException
	{
		DispleeCacheStorage storage = hdosStorage(store);
		Index index = store.getIndex(IndexType.TEXTURES);
		if (index == null)
		{
			return new HdosTextureData(storage, List.of());
		}

		List<TextureDefinition> textures = new ArrayList<>();
		TextureLoader loader = new TextureLoader().setRev233(false);
		for (Archive archive : index.getArchives())
		{
			byte[] data = storage.loadArchiveFile(IndexType.TEXTURES.getNumber(), archive.getArchiveId(), 0, null);
			if (data == null || data.length == 0)
			{
				continue;
			}
			try
			{
				TextureDefinition definition = loader.load(archive.getArchiveId(), data);
				if (hasUsableSpriteFiles(definition))
				{
					textures.add(definition);
				}
			}
			catch (RuntimeException ex)
			{
				// HDOS has extra texture archives that are not useful for OS Map Viewer terrain textures.
			}
		}
		return new HdosTextureData(storage, textures);
	}

	List<TextureDefinition> textures()
	{
		return textures;
	}

	@Override
	public TextureDefinition[] provide()
	{
		return textures.toArray(TextureDefinition[]::new);
	}

	@Override
	public synchronized SpriteDefinition provide(int spriteId, int frameId)
	{
		List<SpriteDefinition> definitions = sprites.computeIfAbsent(spriteId, this::loadSprites);
		for (SpriteDefinition definition : definitions)
		{
			if (definition.getFrame() == frameId)
			{
				return definition;
			}
		}
		return null;
	}

	private List<SpriteDefinition> loadSprites(int spriteId)
	{
		try
		{
			byte[] data = storage.loadArchiveFile(IndexType.SPRITES.getNumber(), spriteId, 0, null);
			if (data == null || data.length == 0)
			{
				return List.of();
			}
			return List.copyOf(Arrays.asList(new SpriteLoader().load(spriteId, data)));
		}
		catch (IOException | RuntimeException ex)
		{
			return List.of();
		}
	}

	private static DispleeCacheStorage hdosStorage(Store store) throws IOException
	{
		if (store.getStorage() instanceof DispleeCacheStorage storage)
		{
			return storage;
		}
		throw new IOException("HDOS texture decoder requires Displee cache storage.");
	}

	private static boolean hasUsableSpriteFiles(TextureDefinition definition)
	{
		int[] fileIds = definition.getFileIds();
		if (fileIds == null || fileIds.length == 0 || fileIds.length > MAX_TEXTURE_FILES)
		{
			return false;
		}
		for (int fileId : fileIds)
		{
			if (fileId < 0)
			{
				return false;
			}
		}
		return true;
	}
}
