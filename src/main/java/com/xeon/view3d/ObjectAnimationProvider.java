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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.FrameDefinition;
import net.runelite.cache.definitions.FramemapDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.definitions.loaders.FrameLoader;
import net.runelite.cache.definitions.loaders.FramemapLoader;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

final class ObjectAnimationProvider
{
	private static final int CACHE_LIMIT = 512;
	private static final int FRAME_ID_MASK = 0xFFFF;

	private final Store store;
	private final SequenceLoader sequenceLoader = new SequenceLoader();
	private final FrameLoader frameLoader = new FrameLoader();
	private final FramemapLoader framemapLoader = new FramemapLoader();
	private final Map<Integer, SequenceDefinition> sequences = boundedCache();
	private final Map<Integer, FrameDefinition> frames = boundedCache();
	private final Map<Integer, FramemapDefinition> framemaps = boundedCache();
	private ArchiveFiles sequenceFiles;

	ObjectAnimationProvider(Store store)
	{
		this.store = store;
	}

	SequenceDefinition loadSequence(int sequenceId)
	{
		if (sequenceId < 0)
		{
			return null;
		}
		return sequences.computeIfAbsent(sequenceId, this::loadSequenceUncached);
	}

	int frameCount(SequenceDefinition sequence)
	{
		return sequence == null || sequence.frameIDs == null ? 0 : sequence.frameIDs.length;
	}

	int[] frameLengths(SequenceDefinition sequence)
	{
		int frameCount = frameCount(sequence);
		int[] lengths = new int[frameCount];
		for (int i = 0; i < frameCount; i++)
		{
			int length = sequence.frameLengths == null || i >= sequence.frameLengths.length ? 1 : sequence.frameLengths[i];
			lengths[i] = Math.max(1, length);
		}
		return lengths;
	}

	ModelDefinition animate(ModelDefinition baseModel, SequenceDefinition sequence, int frameIndex)
	{
		if (baseModel == null || sequence == null || sequence.frameIDs == null
			|| frameIndex < 0 || frameIndex >= sequence.frameIDs.length)
		{
			return null;
		}
		if (baseModel.packedVertexGroups == null || baseModel.packedVertexGroups.length == 0)
		{
			return null;
		}

		FrameDefinition frame = loadFrame(sequence.frameIDs[frameIndex]);
		if (frame == null || frame.framemap == null || frame.translatorCount <= 0
			|| frame.framemap.types == null || frame.framemap.frameMaps == null
			|| frame.indexFrameIds == null || frame.translator_x == null
			|| frame.translator_y == null || frame.translator_z == null)
		{
			return null;
		}

		int translatorCount = Math.min(
			frame.translatorCount,
			Math.min(
				frame.indexFrameIds.length,
				Math.min(frame.translator_x.length, Math.min(frame.translator_y.length, frame.translator_z.length))
			)
		);
		ModelDefinition model = copyModel(baseModel);
		try
		{
			model.computeAnimationTables();
			boolean animated = false;
			for (int i = 0; i < translatorCount; i++)
			{
				int group = frame.indexFrameIds[i];
				if (group < 0 || group >= frame.framemap.types.length || group >= frame.framemap.frameMaps.length)
				{
					continue;
				}
				int type = frame.framemap.types[group];
				if (type == 5)
				{
					continue;
				}
				int[] frameMap = frame.framemap.frameMaps[group];
				if (frameMap == null)
				{
					continue;
				}
				model.animate(
					type,
					frameMap,
					frame.translator_x[i],
					frame.translator_y[i],
					frame.translator_z[i]
				);
				animated = true;
			}
			return animated ? model : null;
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	void clearCache()
	{
		sequences.clear();
		frames.clear();
		framemaps.clear();
		sequenceFiles = null;
	}

	private SequenceDefinition loadSequenceUncached(int sequenceId)
	{
		try
		{
			ArchiveFiles files = sequenceFiles();
			FSFile file = files == null ? null : files.findFile(sequenceId);
			if (file == null || file.getContents() == null || file.getContents().length == 0)
			{
				return null;
			}
			return sequenceLoader.load(sequenceId, file.getContents());
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load object sequence " + sequenceId + ": " + ex.getMessage());
			return null;
		}
	}

	private ArchiveFiles sequenceFiles() throws IOException
	{
		if (sequenceFiles != null)
		{
			return sequenceFiles;
		}

		Index index = store.getIndex(IndexType.CONFIGS);
		Archive archive = index == null ? null : index.getArchive(ConfigType.SEQUENCE.getId());
		if (archive == null)
		{
			return null;
		}
		sequenceLoader.configureForRevision(archive.getRevision());
		byte[] archiveData = store.getStorage().loadArchive(archive);
		sequenceFiles = archive.getFiles(archiveData);
		return sequenceFiles;
	}

	private FrameDefinition loadFrame(int packedFrameId)
	{
		return frames.computeIfAbsent(packedFrameId, this::loadFrameUncached);
	}

	private FrameDefinition loadFrameUncached(int packedFrameId)
	{
		try
		{
			int archiveId = packedFrameId >>> 16;
			int fileId = packedFrameId & FRAME_ID_MASK;
			Index index = store.getIndex(IndexType.ANIMATIONS);
			Archive archive = index == null ? null : index.getArchive(archiveId);
			if (archive == null)
			{
				return null;
			}
			byte[] archiveData = store.getStorage().loadArchive(archive);
			ArchiveFiles files = archive.getFiles(archiveData);
			FSFile file = files.findFile(fileId);
			if (file == null || file.getContents() == null || file.getContents().length < 3)
			{
				return null;
			}
			byte[] contents = file.getContents();
			int framemapId = (contents[0] & 0xFF) << 8 | contents[1] & 0xFF;
			FramemapDefinition framemap = loadFramemap(framemapId);
			return framemap == null ? null : frameLoader.load(framemap, fileId, contents);
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load object animation frame " + packedFrameId + ": " + ex.getMessage());
			return null;
		}
	}

	private FramemapDefinition loadFramemap(int framemapId)
	{
		return framemaps.computeIfAbsent(framemapId, this::loadFramemapUncached);
	}

	private FramemapDefinition loadFramemapUncached(int framemapId)
	{
		try
		{
			Index index = store.getIndex(IndexType.SKELETONS);
			Archive archive = index == null ? null : index.getArchive(framemapId);
			if (archive == null)
			{
				return null;
			}
			byte[] archiveData = store.getStorage().loadArchive(archive);
			byte[] contents = archive.decompress(archiveData);
			return contents == null || contents.length == 0 ? null : framemapLoader.load(framemapId, contents);
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load object animation framemap " + framemapId + ": " + ex.getMessage());
			return null;
		}
	}

	private static ModelDefinition copyModel(ModelDefinition source)
	{
		ModelDefinition copy = new ModelDefinition();
		copy.id = source.id;
		copy.vertexCount = source.vertexCount;
		copy.vertexX = copy(source.vertexX);
		copy.vertexY = copy(source.vertexY);
		copy.vertexZ = copy(source.vertexZ);
		copy.faceCount = source.faceCount;
		copy.faceIndices1 = copy(source.faceIndices1);
		copy.faceIndices2 = copy(source.faceIndices2);
		copy.faceIndices3 = copy(source.faceIndices3);
		copy.faceTransparencies = copy(source.faceTransparencies);
		copy.faceColors = copy(source.faceColors);
		copy.faceRenderPriorities = copy(source.faceRenderPriorities);
		copy.faceRenderTypes = copy(source.faceRenderTypes);
		copy.numTextureFaces = source.numTextureFaces;
		copy.texIndices1 = copy(source.texIndices1);
		copy.texIndices2 = copy(source.texIndices2);
		copy.texIndices3 = copy(source.texIndices3);
		copy.texturePrimaryColors = copy(source.texturePrimaryColors);
		copy.faceTextures = copy(source.faceTextures);
		copy.faceZOffsets = copy(source.faceZOffsets);
		copy.textureCoords = copy(source.textureCoords);
		copy.textureRenderTypes = copy(source.textureRenderTypes);
		copy.packedVertexGroups = copy(source.packedVertexGroups);
		copy.packedTransparencyVertexGroups = copy(source.packedTransparencyVertexGroups);
		copy.priority = source.priority;
		copy.animayaGroups = copy(source.animayaGroups);
		copy.animayaScales = copy(source.animayaScales);
		copy.maxPriority = source.maxPriority;
		return copy;
	}

	private static int[] copy(int[] value)
	{
		return value == null ? null : Arrays.copyOf(value, value.length);
	}

	private static short[] copy(short[] value)
	{
		return value == null ? null : Arrays.copyOf(value, value.length);
	}

	private static byte[] copy(byte[] value)
	{
		return value == null ? null : Arrays.copyOf(value, value.length);
	}

	private static int[][] copy(int[][] value)
	{
		if (value == null)
		{
			return null;
		}
		int[][] copy = new int[value.length][];
		for (int i = 0; i < value.length; i++)
		{
			copy[i] = copy(value[i]);
		}
		return copy;
	}

	private static <K, V> Map<K, V> boundedCache()
	{
		return new LinkedHashMap<>(CACHE_LIMIT, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<K, V> eldest)
			{
				return size() > CACHE_LIMIT;
			}
		};
	}
}
