/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.LocationsDefinition;
import net.runelite.cache.definitions.MapDefinition;
import net.runelite.cache.definitions.MapDefinition.Tile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Position;
import net.runelite.cache.region.Region;
import net.runelite.cache.util.KeyProvider;

final class HdosRegionLoader
{
	private final Store store;
	private final Index mapsIndex;
	private final KeyProvider keyProvider;
	private final Map<Integer, Region> regions = new HashMap<>();
	private HdosMapsCache mapsCache;

	HdosRegionLoader(Store store, KeyProvider keyProvider)
	{
		this.store = store;
		this.mapsIndex = store.getIndex(IndexType.MAPS);
		this.keyProvider = keyProvider;
	}

	Region loadRegionFromArchive(int regionId) throws IOException
	{
		Region cached = regions.get(regionId);
		if (cached != null)
		{
			return cached;
		}

		MapDefinition map = loadMapDef(regionId);
		if (map == null)
		{
			return null;
		}

		LocationsDefinition locs = null;
		try
		{
			locs = loadLocDef(regionId);
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load HDOS locations for region " + regionId + ": " + ex.getMessage());
		}
		Region region = new Region(regionId);
		region.loadTerrain(map);
		if (locs != null)
		{
			region.loadLocations(locs);
		}
		regions.put(regionId, region);
		return region;
	}

	private MapDefinition loadMapDef(int regionId) throws IOException
	{
		int regionX = regionId >> 8;
		int regionY = regionId & 0xFF;
		byte[] data = loadArchive(regionId, "m", 0, false);
		return data == null ? null : loadMap(regionX, regionY, data);
	}

	private LocationsDefinition loadLocDef(int regionId) throws IOException
	{
		int regionX = regionId >> 8;
		int regionY = regionId & 0xFF;
		String archiveName = "l" + regionX + "_" + regionY;
		String missingXteaReason = null;
		try
		{
			LocationArchiveData archiveData = loadLocationArchive(regionId, archiveName);
			if (archiveData.data() != null)
			{
				try
				{
					return loadLocations(regionX, regionY, archiveData.data());
				}
				catch (IOException ex)
				{
					if (!archiveData.loadedWithoutUsableKeys())
					{
						throw ex;
					}
					missingXteaReason = "unkeyed location data did not parse: " + ex.getMessage();
				}
			}
			else
			{
				missingXteaReason = archiveData.missingReason();
			}
		}
		catch (IOException | RuntimeException ex)
		{
			missingXteaReason = ex.getMessage();
		}

		LocationsDefinition sidecarLocations = loadMapsCacheLocDef(regionId, regionX, regionY);
		if (sidecarLocations != null)
		{
			return sidecarLocations;
		}

		if (missingXteaReason != null)
		{
			logMissingXtea(regionId, regionX, regionY, archiveName, missingXteaReason);
		}
		return null;
	}

	private byte[] loadArchive(int regionId, String prefix, int fileId, boolean xtea) throws IOException
	{
		if (mapsIndex == null)
		{
			return null;
		}

		DispleeCacheStorage storage = hdosStorage();
		if (mapsIndex.isNamed())
		{
			int regionX = regionId >> 8;
			int regionY = regionId & 0xFF;
			String archiveName = prefix + regionX + "_" + regionY;
			if (!xtea)
			{
				return storage.loadArchiveFile(IndexType.MAPS.getNumber(), archiveName, 0, null);
			}

			int[] keys = keyProvider == null ? null : keyProvider.getKey(regionId);
			if (keys != null)
			{
				try
				{
					return storage.loadArchiveFile(IndexType.MAPS.getNumber(), archiveName, 0, keys);
				}
				catch (IOException | RuntimeException ex)
				{
					return storage.loadArchiveFile(IndexType.MAPS.getNumber(), archiveName, 0, null);
				}
			}
			return storage.loadArchiveFile(IndexType.MAPS.getNumber(), archiveName, 0, null);
		}

		return storage.loadArchiveFile(IndexType.MAPS.getNumber(), regionId, fileId, null);
	}

	private LocationArchiveData loadLocationArchive(int regionId, String archiveName) throws IOException
	{
		if (mapsIndex == null)
		{
			return LocationArchiveData.absent();
		}

		DispleeCacheStorage storage = hdosStorage();
		if (!mapsIndex.isNamed())
		{
			byte[] data = storage.loadArchiveFile(IndexType.MAPS.getNumber(), regionId, 1, null);
			return new LocationArchiveData(data, true,
				data == null ? "location archive produced no file data" : null);
		}
		if (!storage.hasArchive(IndexType.MAPS.getNumber(), archiveName))
		{
			return LocationArchiveData.absent();
		}

		int regionX = regionId >> 8;
		int regionY = regionId & 0xFF;
		int[] keys = keyProvider == null ? null : keyProvider.getKey(regionId);
		if (hasUsableXteaKeys(keys))
		{
			try
			{
				byte[] data = storage.loadArchiveFile(IndexType.MAPS.getNumber(), archiveName, 0, keys);
				if (data == null)
				{
					throw new IOException("supplied XTEA keys produced no file data");
				}
				return new LocationArchiveData(data, false, null);
			}
			catch (IOException | RuntimeException keyedFailure)
			{
				try
				{
					byte[] data = storage.loadArchiveFile(IndexType.MAPS.getNumber(), archiveName, 0, null);
					if (data == null)
					{
						throw new IOException("unkeyed fallback produced no file data");
					}
					else
					{
						System.err.println("HDOS XTEA keys failed for region " + regionId
							+ " (" + regionX + "," + regionY + ") archive " + archiveName
							+ "; loaded locations without keys instead: " + keyedFailure.getMessage());
					}
					return new LocationArchiveData(data, false, null);
				}
				catch (IOException | RuntimeException unkeyedFailure)
				{
					throw new IOException("Failed to load HDOS locations for region " + regionId
						+ " (" + regionX + "," + regionY + ") archive " + archiveName
						+ " with supplied XTEA keys or without keys; keyed failure: "
						+ keyedFailure.getMessage() + "; unkeyed failure: " + unkeyedFailure.getMessage(),
						unkeyedFailure);
				}
			}
		}

		try
		{
			byte[] data = storage.loadArchiveFile(IndexType.MAPS.getNumber(), archiveName, 0, null);
			return new LocationArchiveData(data, true,
				data == null ? "location archive exists but produced no file data without keys" : null);
		}
		catch (IOException | RuntimeException ex)
		{
			return new LocationArchiveData(null, false,
				"no non-zero XTEA keys are available and unkeyed load failed: " + ex.getMessage());
		}
	}

	private LocationsDefinition loadMapsCacheLocDef(int regionId, int regionX, int regionY) throws IOException
	{
		HdosMapsCache sidecar = hdosMapsCache();
		if (sidecar == null || !sidecar.available())
		{
			return null;
		}

		List<HdosMapsCache.Payload> payloads = sidecar.locationPayloads(regionX, regionY);
		if (payloads.isEmpty())
		{
			return null;
		}

		IOException lastFailure = null;
		for (HdosMapsCache.Payload payload : payloads)
		{
			if (payload.failure() != null)
			{
				lastFailure = payload.failure();
				continue;
			}

			try
			{
				LocationsDefinition locations = loadLocations(regionX, regionY, payload.data());
				System.err.println("Loaded HDOS maps-cache locations for region " + regionId
					+ " (" + regionX + "," + regionY + ") from " + payload.path().getFileName());
				return locations;
			}
			catch (IOException ex)
			{
				lastFailure = ex;
			}
		}

		Path directory = sidecar.directory();
		System.err.println("Failed to load HDOS maps-cache locations for region " + regionId
			+ " (" + regionX + "," + regionY + ") from " + directory
			+ "; tried " + payloads.size() + " file(s); last failure: "
			+ (lastFailure == null ? "unknown" : lastFailure.getMessage()));
		return null;
	}

	static boolean hasUsableXteaKeys(int[] keys)
	{
		return keys != null
			&& keys.length >= 4
			&& (keys[0] != 0 || keys[1] != 0 || keys[2] != 0 || keys[3] != 0);
	}

	private static void logMissingXtea(
		int regionId,
		int regionX,
		int regionY,
		String archiveName,
		String reason
	)
	{
		System.err.println("Missing HDOS XTEA keys for region " + regionId
			+ " (" + regionX + "," + regionY + ") archive " + archiveName + ": " + reason);
	}

	private DispleeCacheStorage hdosStorage() throws IOException
	{
		if (store.getStorage() instanceof DispleeCacheStorage storage)
		{
			return storage;
		}
		throw new IOException("HDOS region loader requires Displee cache storage.");
	}

	private HdosMapsCache hdosMapsCache() throws IOException
	{
		if (mapsCache == null)
		{
			mapsCache = new HdosMapsCache(hdosStorage().cacheDirectory());
		}
		return mapsCache;
	}

	private static MapDefinition loadMap(int regionX, int regionY, byte[] data) throws IOException
	{
		MapDefinition map = new MapDefinition();
		map.setRegionX(regionX);
		map.setRegionY(regionY);
		Tile[][][] tiles = map.getTiles();
		CacheInput in = new CacheInput(data);

		for (int z = 0; z < Region.Z; z++)
		{
			for (int x = 0; x < Region.X; x++)
			{
				for (int y = 0; y < Region.Y; y++)
				{
					Tile tile = tiles[z][x][y] = new Tile();
					while (true)
					{
						int attribute = in.readUnsignedByte("terrain attribute");
						if (attribute == 0)
						{
							break;
						}
						if (attribute == 1)
						{
							tile.height = in.readUnsignedByte("terrain height");
							break;
						}
						if (attribute <= 49)
						{
							tile.attrOpcode = attribute;
							tile.overlayId = (short) in.readUnsignedByte("terrain overlay");
							tile.overlayPath = (byte) ((attribute - 2) / 4);
							tile.overlayRotation = (byte) (attribute - 2 & 3);
						}
						else if (attribute <= 81)
						{
							tile.settings = (byte) (attribute - 49);
						}
						else
						{
							tile.underlayId = (short) (attribute - 81);
						}
					}
				}
			}
		}
		return map;
	}

	static LocationsDefinition loadLocations(int regionX, int regionY, byte[] data) throws IOException
	{
		LocationsDefinition locs = new LocationsDefinition();
		locs.setRegionX(regionX);
		locs.setRegionY(regionY);
		CacheInput in = new CacheInput(data);
		int id = -1;

		while (true)
		{
			int idOffset = in.readUnsignedIntSmartShortCompat("location id delta");
			if (idOffset == 0)
			{
				break;
			}
			id += idOffset;

			int position = 0;
			while (true)
			{
				int positionOffset = in.readUnsignedShortSmart("location position delta");
				if (positionOffset == 0)
				{
					break;
				}
				position += positionOffset - 1;

				int localY = position & 0x3F;
				int localX = position >> 6 & 0x3F;
				int height = position >> 12 & 0x3;
				int attributes = in.readUnsignedByte("location attributes");
				int type = attributes >> 2;
				int orientation = attributes & 0x3;
				locs.getLocations().add(new Location(id, type, orientation, new Position(localX, localY, height)));
			}
		}
		return locs;
	}

	private record LocationArchiveData(byte[] data, boolean loadedWithoutUsableKeys, String missingReason)
	{
		private static LocationArchiveData absent()
		{
			return new LocationArchiveData(null, false, null);
		}
	}

	private static final class CacheInput
	{
		private final byte[] data;
		private int offset;

		private CacheInput(byte[] data)
		{
			this.data = data == null ? new byte[0] : data;
		}

		private int readUnsignedByte(String field) throws IOException
		{
			require(1, field);
			return data[offset++] & 0xFF;
		}

		private int readUnsignedShort(String field) throws IOException
		{
			require(2, field);
			int value = (data[offset] & 0xFF) << 8 | (data[offset + 1] & 0xFF);
			offset += 2;
			return value;
		}

		private int readUnsignedShortSmart(String field) throws IOException
		{
			int peek = peekUnsignedByte(field);
			if (peek < 128)
			{
				return readUnsignedByte(field);
			}
			return readUnsignedShort(field) - 0x8000;
		}

		private int readUnsignedIntSmartShortCompat(String field) throws IOException
		{
			int total = 0;
			int value;
			while ((value = readUnsignedShortSmart(field)) == 32767)
			{
				total += 32767;
			}
			return total + value;
		}

		private int peekUnsignedByte(String field) throws IOException
		{
			require(1, field);
			return data[offset] & 0xFF;
		}

		private void require(int length, String field) throws IOException
		{
			if (offset + length > data.length)
			{
				throw new IOException("HDOS cache data ended while reading " + field
					+ " at offset " + offset + " of " + data.length);
			}
		}
	}
}
