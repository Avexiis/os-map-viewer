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

import com.displee.cache.CacheLibrary;
import com.displee.cache.index.archive.ArchiveSector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Store;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.index.FileData;
import net.runelite.cache.util.Djb2;

final class DispleeCacheStorage implements Storage
{
	private static final int REGION_LIMIT = 256;

	private final CacheLibrary library;
	private final Path cacheDirectory;
	private final Map<Integer, Integer> runeLiteMapNameHashes = new HashMap<>();

	DispleeCacheStorage(Path cacheDirectory) throws IOException
	{
		try
		{
			this.cacheDirectory = cacheDirectory.toAbsolutePath().normalize();
			library = CacheLibrary.create(this.cacheDirectory.toString());
			indexMapArchiveNameHashes();
		}
		catch (RuntimeException ex)
		{
			throw new IOException("Failed to open HDOS cache with Displee: " + ex.getMessage(), ex);
		}
	}

	@Override
	public void init(Store store)
	{
		for (com.displee.cache.index.Index index : library.validIndices())
		{
			store.addIndex(index.getId());
		}
	}

	@Override
	public void close()
	{
		library.close();
	}

	@Override
	public void load(Store store)
	{
		for (net.runelite.cache.fs.Index runeLiteIndex : store.getIndexes())
		{
			com.displee.cache.index.Index displeeIndex = library.index(runeLiteIndex.getId());
			if (displeeIndex != null)
			{
				populateIndex(runeLiteIndex, displeeIndex);
			}
		}
	}

	@Override
	public void save(Store store) throws IOException
	{
		throw new IOException("Displee-backed HDOS cache storage is read-only.");
	}

	@Override
	public byte[] load(int indexId, int archiveId)
	{
		com.displee.cache.index.Index index = library.index(indexId);
		if (index == null)
		{
			return null;
		}

		ArchiveSector sector = index.readArchiveSector(archiveId);
		return sector == null ? null : sector.getData();
	}

	List<DecodedFile> loadArchiveFiles(int indexId, int archiveId) throws IOException
	{
		com.displee.cache.index.archive.Archive archive = loadDecodedArchive(indexId, archiveId, null);
		return archive == null ? List.of() : decodedFiles(archive);
	}

	byte[] loadArchiveFile(int indexId, int archiveId, int fileId, int[] xtea) throws IOException
	{
		com.displee.cache.index.archive.Archive archive = loadDecodedArchive(indexId, archiveId, xtea);
		return archive == null ? null : decodedFile(archive, fileId);
	}

	byte[] loadArchiveFile(int indexId, String archiveName, int fileId, int[] xtea) throws IOException
	{
		com.displee.cache.index.archive.Archive archive = loadDecodedArchive(indexId, archiveName, xtea);
		return archive == null ? null : decodedFile(archive, fileId);
	}

	boolean hasArchive(int indexId, String archiveName)
	{
		com.displee.cache.index.Index index = library.index(indexId);
		return index != null && index.contains(archiveName);
	}

	Path cacheDirectory()
	{
		return cacheDirectory;
	}

	@Override
	public void store(int indexId, int archiveId, byte[] data) throws IOException
	{
		throw new IOException("Displee-backed HDOS cache storage is read-only.");
	}

	private com.displee.cache.index.archive.Archive loadDecodedArchive(
		int indexId,
		int archiveId,
		int[] xtea
	) throws IOException
	{
		try
		{
			com.displee.cache.index.Index index = library.index(indexId);
			return index == null ? null : index.archive(archiveId, xtea);
		}
		catch (RuntimeException ex)
		{
			throw new IOException("Failed to decode HDOS archive " + indexId + "/" + archiveId + " with Displee", ex);
		}
	}

	private com.displee.cache.index.archive.Archive loadDecodedArchive(
		int indexId,
		String archiveName,
		int[] xtea
	) throws IOException
	{
		try
		{
			com.displee.cache.index.Index index = library.index(indexId);
			return index == null ? null : index.archive(archiveName, xtea);
		}
		catch (RuntimeException ex)
		{
			throw new IOException("Failed to decode HDOS archive " + indexId + "/" + archiveName + " with Displee", ex);
		}
	}

	private void populateIndex(
		net.runelite.cache.fs.Index runeLiteIndex,
		com.displee.cache.index.Index displeeIndex
	)
	{
		boolean mapsIndex = displeeIndex.getId() == IndexType.MAPS.getNumber();
		runeLiteIndex.setProtocol(displeeIndex.getVersion());
		runeLiteIndex.setNamed(displeeIndex.isNamed());
		runeLiteIndex.setSized(displeeIndex.hasLengths());
		runeLiteIndex.setRevision(displeeIndex.getRevision());
		runeLiteIndex.setCrc(displeeIndex.getCrc());
		runeLiteIndex.setCompression(compressionId(displeeIndex.getCompressionType()));

		for (com.displee.cache.index.archive.Archive displeeArchive : displeeIndex.archives())
		{
			net.runelite.cache.fs.Archive runeLiteArchive = runeLiteIndex.addArchive(displeeArchive.getId());
			runeLiteArchive.setNameHash(nameHash(displeeArchive, mapsIndex));
			runeLiteArchive.setCrc(displeeArchive.getCrc());
			runeLiteArchive.setRevision(displeeArchive.getRevision());
			runeLiteArchive.setCompressedSize(displeeArchive.getCompressedLength());
			runeLiteArchive.setDecompressedSize(displeeArchive.getDecompressedLength());
			runeLiteArchive.setCompression(compressionId(displeeArchive.getCompressionType()));
			runeLiteArchive.setFileData(fileData(displeeArchive));
		}
	}

	private FileData[] fileData(com.displee.cache.index.archive.Archive archive)
	{
		com.displee.cache.index.archive.file.File[] files = archive.files();
		FileData[] fileData = new FileData[files.length];
		for (int i = 0; i < files.length; i++)
		{
			com.displee.cache.index.archive.file.File file = files[i];
			FileData data = new FileData();
			data.setId(file.getId());
			data.setNameHash(file.getHashName());
			fileData[i] = data;
		}
		return fileData;
	}

	private static List<DecodedFile> decodedFiles(com.displee.cache.index.archive.Archive archive)
	{
		List<DecodedFile> files = new ArrayList<>();
		for (com.displee.cache.index.archive.file.File file : archive.files())
		{
			if (file.getData() != null)
			{
				files.add(new DecodedFile(file.getId(), file.getData()));
			}
		}
		return List.copyOf(files);
	}

	private static byte[] decodedFile(com.displee.cache.index.archive.Archive archive, int fileId)
	{
		com.displee.cache.index.archive.file.File file = archive.file(fileId);
		if (file == null && archive.files().length == 1)
		{
			file = archive.first();
		}
		return file == null ? null : file.getData();
	}

	private int nameHash(com.displee.cache.index.archive.Archive archive, boolean mapsIndex)
	{
		if (!mapsIndex)
		{
			return archive.getHashName();
		}
		return runeLiteMapNameHashes.getOrDefault(archive.getHashName(), archive.getHashName());
	}

	private void indexMapArchiveNameHashes()
	{
		com.displee.cache.index.Index maps = library.index(IndexType.MAPS.getNumber());
		if (maps == null || !maps.isNamed())
		{
			return;
		}

		for (int regionX = 0; regionX < REGION_LIMIT; regionX++)
		{
			for (int regionY = 0; regionY < REGION_LIMIT; regionY++)
			{
				indexMapArchiveNameHash("m" + regionX + "_" + regionY);
				indexMapArchiveNameHash("l" + regionX + "_" + regionY);
			}
		}
	}

	private void indexMapArchiveNameHash(String name)
	{
		// Displee names modern map archives with Java hashes; RuneLite looks them up with Djb2.
		runeLiteMapNameHashes.put(name.hashCode(), Djb2.hash(name));
	}

	private static int compressionId(com.displee.compress.CompressionType compressionType)
	{
		return compressionType == null ? 0 : compressionType.ordinal();
	}

	record DecodedFile(int id, byte[] data)
	{
	}
}
