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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
		try (Session session = open(cacheDirectory))
		{
			return session.loadRegion(regionId);
		}
	}

	public Session open(Path cacheDirectory) throws IOException
	{
		if (cacheDirectory == null)
		{
			throw new IOException("Cache directory is not set.");
		}

		Store store = new Store(cacheDirectory.toFile());
		try
		{
			store.load();
			UnderlayManager underlays = new UnderlayManager(store);
			underlays.load();
			OverlayManager overlays = new OverlayManager(store);
			overlays.load();
			ObjectManager objects = new ObjectManager(store);
			objects.load();
			TextureResources textures = loadTextureResources(store);
			TerrainFloorTextures floorTextures = TerrainFloorTextures.load(store);
			RegionLoader regionLoader = new RegionLoader(store, loadKeyProvider());
			return new Session(
				store,
				regionLoader,
				underlays,
				overlays,
				objects,
				NpcSpawnIndex.loadDefault(),
				new NpcDefinitionProvider(store),
				NpcWanderCollisionMap.loadDefault(),
				new ObjectModelProvider(store),
				new ObjectAnimationProvider(store),
				textures.textureProvider(),
				floorTextures,
				textures.textureSet()
			);
		}
		catch (IOException | RuntimeException ex)
		{
			try
			{
				store.close();
			}
			catch (IOException closeEx)
			{
				ex.addSuppressed(closeEx);
			}
			throw ex;
		}
	}

	private static TextureResources loadTextureResources(Store store)
	{
		try
		{
			TextureManager textures = new TextureManager(store);
			textures.load();
			SpriteManager sprites = new SpriteManager(store);
			sprites.load();
			return new TextureResources(
				new RSTextureProvider(textures, sprites),
				SceneTextureSet.build(textures, sprites)
			);
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load cache texture metadata for 3D terrain: " + ex.getMessage());
			return new TextureResources(null, SceneTextureSet.empty());
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

	private record TextureResources(
		RSTextureProvider textureProvider,
		SceneTextureSet textureSet
	)
	{
	}

	public static final class Session implements AutoCloseable
	{
		private final Store store;
		private final RegionLoader regionLoader;
		private final UnderlayManager underlays;
		private final OverlayManager overlays;
		private final ObjectManager objects;
		private volatile NpcSpawnIndex npcSpawnIndex;
		private final NpcDefinitionProvider npcDefinitionProvider;
		private final NpcWanderCollisionMap npcCollisionMap;
		private final NpcMeshBuilder.FrameCache npcFrameCache = new NpcMeshBuilder.FrameCache();
		private final ObjectModelProvider modelProvider;
		private final ObjectAnimationProvider animationProvider;
		private final RSTextureProvider textureProvider;
		private final TerrainFloorTextures floorTextures;
		private final SceneTextureSet textureSet;

		private Session(
			Store store,
			RegionLoader regionLoader,
			UnderlayManager underlays,
			OverlayManager overlays,
			ObjectManager objects,
			NpcSpawnIndex npcSpawnIndex,
			NpcDefinitionProvider npcDefinitionProvider,
			NpcWanderCollisionMap npcCollisionMap,
			ObjectModelProvider modelProvider,
			ObjectAnimationProvider animationProvider,
			RSTextureProvider textureProvider,
			TerrainFloorTextures floorTextures,
			SceneTextureSet textureSet
		)
		{
			this.store = store;
			this.regionLoader = regionLoader;
			this.underlays = underlays;
			this.overlays = overlays;
			this.objects = objects;
			this.npcSpawnIndex = npcSpawnIndex;
			this.npcDefinitionProvider = npcDefinitionProvider;
			this.npcCollisionMap = npcCollisionMap;
			this.modelProvider = modelProvider;
			this.animationProvider = animationProvider;
			this.textureProvider = textureProvider;
			this.floorTextures = floorTextures;
			this.textureSet = textureSet;
		}

		public synchronized TerrainMesh loadRegion(int regionId) throws IOException
		{
			if (regionId < 0)
			{
				throw new IOException("Region ID is not valid.");
			}

			Region region = regionLoader.loadRegionFromArchive(regionId);
			if (region == null)
			{
				throw new IOException("Region " + regionId + " was not found in the cache.");
			}

			Map<Integer, Region> regions = new HashMap<>();
			regions.put(region.getRegionID(), region);
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					if (dx == 0 && dy == 0)
					{
						continue;
					}
					int neighborRegionId = TerrainScene.regionId(region.getRegionX() + dx, region.getRegionY() + dy);
					Region neighbor = loadNeighborRegion(neighborRegionId);
					if (neighbor != null)
					{
						regions.put(neighborRegionId, neighbor);
					}
				}
			}

			try
			{
				return TerrainMeshBuilder.build(
					new TerrainRegionContext(region, regions),
					underlays,
					overlays,
					objects,
					npcSpawnIndex,
					npcDefinitionProvider,
					npcCollisionMap,
					modelProvider,
					animationProvider,
					textureProvider,
					floorTextures,
					textureSet,
					npcFrameCache
				);
			}
			finally
			{
				modelProvider.clearCache();
				animationProvider.clearCache();
			}
		}

		void reloadNpcSpawnIndex()
		{
			npcSpawnIndex = NpcSpawnIndex.loadDefault();
			npcFrameCache.clear();
		}

		synchronized List<NpcDefinitionProvider.NpcSummary> npcCatalog()
		{
			return npcDefinitionProvider.catalog();
		}

		synchronized NpcPreviewModel npcPreviewModel(int npcId)
		{
			try
			{
				return NpcMeshBuilder.previewModel(
					npcId,
					npcDefinitionProvider,
					modelProvider,
					animationProvider,
					textureProvider,
					textureSet
				);
			}
			finally
			{
				modelProvider.clearCache();
				animationProvider.clearCache();
			}
		}

		synchronized String npcName(int npcId)
		{
			NpcDefinition3D definition = npcDefinitionProvider.definition(npcId);
			if (definition == null
				|| definition.name == null
				|| definition.name.isBlank()
				|| "null".equalsIgnoreCase(definition.name))
			{
				return "";
			}
			return definition.name;
		}

		private Region loadNeighborRegion(int regionId)
		{
			try
			{
				return regionLoader.loadRegionFromArchive(regionId);
			}
			catch (IOException | RuntimeException ex)
			{
				return null;
			}
		}

		@Override
		public void close() throws IOException
		{
			npcFrameCache.clear();
			store.close();
		}
	}
}
