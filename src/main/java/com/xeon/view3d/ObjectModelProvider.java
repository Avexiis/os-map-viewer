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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

final class ObjectModelProvider
{
	private static final int MODEL_CACHE_LIMIT = 512;
	private static final int OBJECT_SOUND_DATA_REVISION = 1673;
	private static final int TYPE_GAME_OBJECT = 10;
	private static final int TYPE_ROOF_SLOPED = 12;
	private static final int TYPE_ROOF_SLOPED_OVERHANG_HARD_OUTER_CORNER = 21;

	private final Store store;
	private final ModelLoader loader = new ModelLoader();
	private final Map<Integer, byte[]> objectConfigFiles = new HashMap<>();
	private final Map<Integer, RawObjectModelTables> rawObjectModelTables = new HashMap<>();
	private final Map<Long, int[]> preferredRoofModelIds = new HashMap<>();
	private final Set<Long> missingPreferredRoofModelIds = new HashSet<>();
	private boolean objectConfigFilesLoaded;
	private boolean objectConfigRev220SoundData = true;
	private final Map<Integer, ModelDefinition> models = new LinkedHashMap<>(MODEL_CACHE_LIMIT, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, ModelDefinition> eldest)
		{
			return size() > MODEL_CACHE_LIMIT;
		}
	};

	ObjectModelProvider(Store store)
	{
		this.store = store;
	}

	ModelDefinition load(int modelId)
	{
		return models.computeIfAbsent(modelId, this::loadUncached);
	}

	int[] modelIds(ObjectDefinition definition, int modelType)
	{
		int[] decodedModelIds = decodedModelIds(definition, modelType);
		int[] preferredModelIds = preferredRoofModelIds(definition, modelType, decodedModelIds);
		return preferredModelIds == null ? decodedModelIds : preferredModelIds;
	}

	void clearCache()
	{
		models.clear();
	}

	private int[] preferredRoofModelIds(
		ObjectDefinition definition,
		int modelType,
		int[] decodedModelIds
	)
	{
		if (definition == null
			|| !isRoofType(modelType)
			|| !"null".equalsIgnoreCase(definition.getName())
			|| decodedModelIds == null
			|| !isPlaceholderModelSet(decodedModelIds))
		{
			return null;
		}

		long key = modelKey(definition.getId(), modelType);
		int[] cached = preferredRoofModelIds.get(key);
		if (cached != null)
		{
			return cached;
		}
		if (missingPreferredRoofModelIds.contains(key))
		{
			return null;
		}

		List<int[]> candidates = rawTypedModelIds(definition.getId(), modelType);
		for (int[] candidate : candidates)
		{
			if (!Arrays.equals(candidate, decodedModelIds) && isRenderableModelSet(candidate))
			{
				preferredRoofModelIds.put(key, candidate);
				return candidate;
			}
		}

		missingPreferredRoofModelIds.add(key);
		return null;
	}

	private static int[] decodedModelIds(ObjectDefinition definition, int modelType)
	{
		if (definition == null)
		{
			return null;
		}

		int[] objectModels = definition.getObjectModels();
		if (objectModels == null)
		{
			return null;
		}

		int[] objectTypes = definition.getObjectTypes();
		if (objectTypes == null)
		{
			return modelType == TYPE_GAME_OBJECT ? objectModels : null;
		}

		for (int i = 0; i < objectTypes.length && i < objectModels.length; i++)
		{
			if (objectTypes[i] == modelType)
			{
				return new int[]{objectModels[i]};
			}
		}
		return null;
	}

	private List<int[]> rawTypedModelIds(int objectId, int modelType)
	{
		RawObjectModelTables tables = rawObjectModelTables.computeIfAbsent(objectId, this::loadRawObjectModelTables);
		return tables.typedModelIds(modelType);
	}

	private RawObjectModelTables loadRawObjectModelTables(int objectId)
	{
		byte[] contents = objectConfigContents(objectId);
		if (contents == null || contents.length == 0)
		{
			return RawObjectModelTables.EMPTY;
		}

		try
		{
			Map<Integer, List<int[]>> typedModelIds = new HashMap<>();
			int[] offset = new int[]{0};
			while (offset[0] < contents.length)
			{
				int opcode = readUnsignedByte(contents, offset);
				if (opcode == 0)
				{
					break;
				}

				if (opcode == 1)
				{
					readTypedShortModelIds(contents, offset, typedModelIds);
				}
				else if (opcode == 5)
				{
					skipShortModelIds(contents, offset);
				}
				else if (opcode == 6)
				{
					readTypedIntModelIds(contents, offset, typedModelIds);
				}
				else if (opcode == 7)
				{
					skipIntModelIds(contents, offset);
				}
				else
				{
					skipObjectOpcode(contents, offset, opcode, objectConfigRev220SoundData);
				}
			}
			return typedModelIds.isEmpty() ? RawObjectModelTables.EMPTY : new RawObjectModelTables(typedModelIds);
		}
		catch (RuntimeException ex)
		{
			return RawObjectModelTables.EMPTY;
		}
	}

	private synchronized byte[] objectConfigContents(int objectId)
	{
		if (!objectConfigFilesLoaded)
		{
			loadObjectConfigFiles();
		}
		return objectConfigFiles.get(objectId);
	}

	private void loadObjectConfigFiles()
	{
		try
		{
			Index index = store.getIndex(IndexType.CONFIGS);
			Archive archive = index == null ? null : index.getArchive(ConfigType.OBJECT.getId());
			if (archive == null)
			{
				return;
			}

			objectConfigRev220SoundData = archive.getRevision() >= OBJECT_SOUND_DATA_REVISION;
			byte[] archiveData = store.getStorage().loadArchive(archive);
			if (archiveData == null || archiveData.length == 0)
			{
				return;
			}

			ArchiveFiles files = archive.getFiles(archiveData);
			for (FSFile file : files.getFiles())
			{
				byte[] contents = file.getContents();
				if (contents != null && contents.length > 0)
				{
					objectConfigFiles.put(file.getFileId(), contents);
				}
			}
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load raw object configs for roof model selection: " + ex.getMessage());
		}
		finally
		{
			objectConfigFilesLoaded = true;
		}
	}

	private boolean isPlaceholderModelSet(int[] modelIds)
	{
		if (modelIds == null || modelIds.length == 0)
		{
			return false;
		}
		for (int modelId : modelIds)
		{
			ModelDefinition model = load(modelId);
			if (!isPlaceholderRoofModel(model))
			{
				return false;
			}
		}
		return true;
	}

	private boolean isRenderableModelSet(int[] modelIds)
	{
		if (modelIds == null || modelIds.length == 0)
		{
			return false;
		}
		boolean hasRealGeometry = false;
		for (int modelId : modelIds)
		{
			ModelDefinition model = load(modelId);
			if (model == null || model.vertexCount == 0 || model.faceCount == 0)
			{
				return false;
			}
			hasRealGeometry |= !isPlaceholderRoofModel(model);
		}
		return hasRealGeometry;
	}

	private static boolean isPlaceholderRoofModel(ModelDefinition model)
	{
		if (model == null
			|| model.vertexCount != 4
			|| model.faceCount != 2
			|| model.faceTextures != null
			|| model.faceRenderTypes != null
			|| model.faceTransparencies != null
			|| model.faceColors == null
			|| model.faceColors.length < model.faceCount)
		{
			return false;
		}

		for (int i = 0; i < model.vertexCount; i++)
		{
			if (model.vertexY[i] != 0)
			{
				return false;
			}
		}
		for (int face = 0; face < model.faceCount; face++)
		{
			if (model.faceColors[face] != 0)
			{
				return false;
			}
		}
		return true;
	}

	private ModelDefinition loadUncached(int modelId)
	{
		try
		{
			Index index = store.getIndex(IndexType.MODELS);
			Archive archive = index == null ? null : index.getArchive(modelId);
			if (archive == null)
			{
				return null;
			}

			byte[] archiveData = store.getStorage().loadArchive(archive);
			if (archiveData == null || archiveData.length == 0)
			{
				return null;
			}

			ArchiveFiles files = archive.getFiles(archiveData);
			for (FSFile file : files.getFiles())
			{
				if (file.getContents() != null && file.getContents().length > 0)
				{
					return loader.load(modelId, file.getContents());
				}
			}
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load object model " + modelId + ": " + ex.getMessage());
		}
		return null;
	}

	private static void readTypedShortModelIds(
		byte[] data,
		int[] offset,
		Map<Integer, List<int[]>> typedModelIds
	)
	{
		int count = readUnsignedByte(data, offset);
		for (int i = 0; i < count; i++)
		{
			int modelId = readUnsignedShort(data, offset);
			int modelType = readUnsignedByte(data, offset);
			addTypedModelIds(typedModelIds, modelType, new int[]{modelId});
		}
	}

	private static void skipShortModelIds(byte[] data, int[] offset)
	{
		int count = readUnsignedByte(data, offset);
		offset[0] += count * 2;
	}

	private static void readTypedIntModelIds(
		byte[] data,
		int[] offset,
		Map<Integer, List<int[]>> typedModelIds
	)
	{
		int count = readUnsignedByte(data, offset);
		for (int i = 0; i < count; i++)
		{
			int modelId = readInt(data, offset);
			int modelType = readUnsignedByte(data, offset);
			addTypedModelIds(typedModelIds, modelType, new int[]{modelId});
		}
	}

	private static void skipIntModelIds(byte[] data, int[] offset)
	{
		int count = readUnsignedByte(data, offset);
		offset[0] += count * 4;
	}

	private static void addTypedModelIds(Map<Integer, List<int[]>> typedModelIds, int modelType, int[] modelIds)
	{
		typedModelIds.computeIfAbsent(modelType, ignored -> new ArrayList<>()).add(modelIds);
	}

	private static void skipObjectOpcode(byte[] data, int[] offset, int opcode, boolean rev220SoundData)
	{
		if (opcode == 2 || (opcode >= 30 && opcode < 35))
		{
			skipString(data, offset);
		}
		else if (opcode == 14 || opcode == 15 || opcode == 19 || opcode == 28 || opcode == 75
			|| opcode == 81 || opcode == 91 || opcode == 95 || opcode == 96)
		{
			offset[0]++;
		}
		else if (opcode == 17 || opcode == 18 || opcode == 21 || opcode == 22 || opcode == 23
			|| opcode == 62 || opcode == 64 || opcode == 73 || opcode == 74 || opcode == 89
			|| opcode == 90 || opcode == 94)
		{
			return;
		}
		else if (opcode == 24 || opcode == 61 || opcode == 65 || opcode == 66 || opcode == 67
			|| opcode == 68 || opcode == 70 || opcode == 71 || opcode == 72 || opcode == 82)
		{
			offset[0] += 2;
		}
		else if (opcode == 29 || opcode == 39 || opcode == 69)
		{
			offset[0]++;
		}
		else if (opcode == 40 || opcode == 41)
		{
			int count = readUnsignedByte(data, offset);
			offset[0] += count * 4;
		}
		else if (opcode == 77)
		{
			offset[0] += 4;
			int count = readUnsignedByte(data, offset);
			offset[0] += (count + 1) * 2;
		}
		else if (opcode == 78)
		{
			offset[0] += rev220SoundData ? 4 : 3;
		}
		else if (opcode == 79)
		{
			offset[0] += rev220SoundData ? 6 : 5;
			int count = readUnsignedByte(data, offset);
			offset[0] += count * 2;
		}
		else if (opcode == 92)
		{
			offset[0] += 6;
			int count = readUnsignedByte(data, offset);
			offset[0] += (count + 1) * 2;
		}
		else if (opcode == 93)
		{
			offset[0] += 6;
		}
		else if (opcode == 100)
		{
			offset[0] += 2;
			skipString(data, offset);
		}
		else if (opcode == 101)
		{
			offset[0] += 13;
			skipString(data, offset);
		}
		else if (opcode == 102)
		{
			offset[0] += 15;
			skipString(data, offset);
		}
		else if (opcode == 249)
		{
			skipParams(data, offset);
		}
		else
		{
			throw new IllegalArgumentException("Unrecognized object opcode " + opcode);
		}
	}

	private static void skipParams(byte[] data, int[] offset)
	{
		int count = readUnsignedByte(data, offset);
		for (int i = 0; i < count; i++)
		{
			boolean stringValue = readUnsignedByte(data, offset) == 1;
			offset[0] += 3;
			if (stringValue)
			{
				skipString(data, offset);
			}
			else
			{
				offset[0] += 4;
			}
		}
	}

	private static void skipString(byte[] data, int[] offset)
	{
		while (offset[0] < data.length && data[offset[0]++] != 0)
		{
			// Strings are only needed for offset alignment here.
		}
	}

	private static int readUnsignedByte(byte[] data, int[] offset)
	{
		return data[offset[0]++] & 0xFF;
	}

	private static int readUnsignedShort(byte[] data, int[] offset)
	{
		int value = ((data[offset[0]] & 0xFF) << 8) | (data[offset[0] + 1] & 0xFF);
		offset[0] += 2;
		return value;
	}

	private static int readInt(byte[] data, int[] offset)
	{
		int value = ((data[offset[0]] & 0xFF) << 24)
			| ((data[offset[0] + 1] & 0xFF) << 16)
			| ((data[offset[0] + 2] & 0xFF) << 8)
			| (data[offset[0] + 3] & 0xFF);
		offset[0] += 4;
		return value;
	}

	private static boolean isRoofType(int modelType)
	{
		return modelType >= TYPE_ROOF_SLOPED && modelType <= TYPE_ROOF_SLOPED_OVERHANG_HARD_OUTER_CORNER;
	}

	private static long modelKey(int objectId, int modelType)
	{
		return ((long) objectId << 32) | (modelType & 0xFFFFFFFFL);
	}

	private record RawObjectModelTables(Map<Integer, List<int[]>> typedModelIds)
	{
		private static final RawObjectModelTables EMPTY = new RawObjectModelTables(Map.of());

		private List<int[]> typedModelIds(int modelType)
		{
			return typedModelIds.getOrDefault(modelType, List.of());
		}
	}
}
