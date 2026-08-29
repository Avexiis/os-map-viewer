package com.xeon.view3d;

import com.xeon.tools.HdosCacheDiagnostics;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HdosTerrainRegionLoaderTest
{
	@Test
	void opensRendererSessionWithHdosCacheThroughDispleeStorage() throws Exception
	{
		Path cacheDirectory = configuredHdosCache();
		Assumptions.assumeTrue(Files.isDirectory(cacheDirectory), "HDOS cache not found: " + cacheDirectory);

		try (TerrainRegionLoader.Session session = new TerrainRegionLoader().open(cacheDirectory, true))
		{
			assertTrue(session.hdosCache());
			TerrainMesh mesh = session.loadRegion(HdosCacheDiagnostics.DEFAULT_REGION_ID);

			assertEquals(HdosCacheDiagnostics.DEFAULT_REGION_ID, mesh.regionId());
			assertTrue(mesh.vertexCount() > 0, "HDOS terrain mesh did not contain renderable vertices");
			assertTrue(mesh.textureSet().layerCount() > 1, "HDOS terrain textures were not decoded");
		}
	}

	@Test
	void resolvesHdosRegionArchivesThroughRuneliteStoreFacade() throws Exception
	{
		Path cacheDirectory = configuredHdosCache();
		Assumptions.assumeTrue(Files.isDirectory(cacheDirectory), "HDOS cache not found: " + cacheDirectory);

		int regionX = HdosCacheDiagnostics.DEFAULT_REGION_ID >> 8;
		int regionY = HdosCacheDiagnostics.DEFAULT_REGION_ID & 0xFF;
		try (Store store = new Store(new DispleeCacheStorage(cacheDirectory)))
		{
			store.load();
			Index maps = store.getIndex(IndexType.MAPS);
			assertNotNull(maps, "HDOS maps index was not exposed to the renderer store");

			Archive terrain = maps.findArchiveByName("m" + regionX + "_" + regionY);
			Archive locations = maps.findArchiveByName("l" + regionX + "_" + regionY);
			assertNotNull(terrain, "Sample HDOS terrain archive was not resolved by RuneLite map name");
			assertNotNull(locations, "Sample HDOS location archive was not resolved by RuneLite map name");

			byte[] terrainData = store.getStorage().loadArchive(terrain);
			byte[] locationData = store.getStorage().loadArchive(locations);
			assertNotNull(terrainData, "Sample HDOS terrain archive did not load");
			assertNotNull(locationData, "Sample HDOS location archive did not load");
			assertTrue(terrainData.length > 0, "Sample HDOS terrain archive was empty");
			assertTrue(locationData.length > 0, "Sample HDOS location archive was empty");
			assertTrue(terrain.decompress(terrainData).length > 0, "Sample HDOS terrain archive did not decompress");
			assertTrue(locations.decompress(locationData).length > 0, "Sample HDOS location archive did not decompress");
		}
	}

	private static Path configuredHdosCache()
	{
		String property = System.getProperty("osmapviewer.hdosCache");
		if (property != null && !property.isBlank())
		{
			return Path.of(property);
		}

		String environment = System.getenv("OSMAPVIEWER_HDOS_CACHE");
		if (environment != null && !environment.isBlank())
		{
			return Path.of(environment);
		}

		return HdosCacheDiagnostics.DEFAULT_HDOS_CACHE;
	}
}
