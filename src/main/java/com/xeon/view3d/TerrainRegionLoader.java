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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.OverlayManager;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.TextureManager;
import net.runelite.cache.UnderlayManager;
import net.runelite.cache.fs.Store;
import net.runelite.cache.item.RSTextureProvider;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.KeyProvider;
import net.runelite.cache.util.XteaKeyManager;

public final class TerrainRegionLoader
{
	private static final String XTEA_RESOURCE = "/com/xeon/xteas.json";

	public TerrainMesh loadFullScene(Path cacheDirectory, int regionId) throws IOException
	{
		if (cacheDirectory == null)
		{
			throw new IOException("Cache directory is not set.");
		}
		if (regionId < 0)
		{
			throw new IOException("Region ID is not valid.");
		}

		try (Store store = new Store(cacheDirectory.toFile()))
		{
			store.load();

			UnderlayManager underlays = new UnderlayManager(store);
			underlays.load();
			OverlayManager overlays = new OverlayManager(store);
			overlays.load();
			ObjectManager objects = new ObjectManager(store);
			objects.load();
			RSTextureProvider textureProvider = loadTextureProvider(store);

			RegionLoader regionLoader = new RegionLoader(store, loadKeyProvider());
			Region region = regionLoader.loadRegionFromArchive(regionId);
			if (region == null)
			{
				throw new IOException("Region " + regionId + " was not found in the cache.");
			}

			return TerrainMeshBuilder.build(
				region,
				underlays,
				overlays,
				objects,
				new ObjectModelProvider(store),
				textureProvider
			);
		}
	}

	private static RSTextureProvider loadTextureProvider(Store store)
	{
		try
		{
			TextureManager textures = new TextureManager(store);
			textures.load();
			return new RSTextureProvider(textures, new SpriteManager(store));
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load cache texture metadata for 3D terrain: " + ex.getMessage());
			return null;
		}
	}

	private static KeyProvider loadKeyProvider() throws IOException
	{
		XteaKeyManager keyManager = new XteaKeyManager();
		byte[] bytes;
		try (InputStream in = TerrainRegionLoader.class.getResourceAsStream(XTEA_RESOURCE))
		{
			bytes = in == null ? new byte[0] : in.readAllBytes();
		}

		String contents = new String(bytes, StandardCharsets.UTF_8).trim();
		if (contents.isEmpty() || "[]".equals(contents) || "[ ]".equals(contents))
		{
			return keyManager;
		}

		try (ByteArrayInputStream in = new ByteArrayInputStream(bytes))
		{
			keyManager.loadKeys(in);
		}
		return keyManager;
	}
}
