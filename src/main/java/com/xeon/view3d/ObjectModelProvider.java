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
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

final class ObjectModelProvider
{
	private static final int MODEL_CACHE_LIMIT = 512;

	private final Store store;
	private final ModelLoader loader = new ModelLoader();
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

	void clearCache()
	{
		models.clear();
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
}
