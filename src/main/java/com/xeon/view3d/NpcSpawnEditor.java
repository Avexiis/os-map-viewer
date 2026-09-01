/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.

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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class NpcSpawnEditor
{
	private static final String TSV_HEADER = "# id\tname\tx\ty\tlevel\tfaceDirection\twalk\tcustomPathEnabled\tcustomPath";
	private static final Gson PRETTY_GSON = new GsonBuilder()
		.disableHtmlEscaping()
		.setPrettyPrinting()
		.create();

	private NpcSpawnEditor()
	{
	}

	static List<Path> filesForDelete(Entry entry)
	{
		return entry.source() == NpcSpawnIndex.SpawnSource.TSV
			? List.of(NpcSpawnIndex.sourceCustomSpawnPath())
			: List.of(NpcSpawnIndex.sourceSpawnPath());
	}

	static List<Path> filesForOverride(Entry original)
	{
		if (original == null || original.source() == NpcSpawnIndex.SpawnSource.TSV)
		{
			return List.of(NpcSpawnIndex.sourceCustomSpawnPath());
		}
		return List.of(NpcSpawnIndex.sourceSpawnPath(), NpcSpawnIndex.sourceCustomSpawnPath());
	}

	static void delete(Entry entry) throws IOException
	{
		if (entry.source() == NpcSpawnIndex.SpawnSource.TSV)
		{
			removeTsv(entry);
			return;
		}
		removeJson(entry);
	}

	static void saveOverride(Entry original, Entry next) throws IOException
	{
		if (original != null && original.source() == NpcSpawnIndex.SpawnSource.JSON)
		{
			removeJson(original);
		}
		else if (original != null)
		{
			removeTsv(original);
		}
		upsertTsv(next.withSource(NpcSpawnIndex.SpawnSource.TSV));
	}

	private static void removeJson(Entry entry) throws IOException
	{
		Path path = NpcSpawnIndex.sourceSpawnPath();
		if (!Files.isRegularFile(path))
		{
			throw new IOException("NPC JSON source file was not found: " + path);
		}

		JsonElement parsed;
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			parsed = new JsonParser().parse(reader);
		}
		if (parsed == null || !parsed.isJsonArray())
		{
			throw new IOException("NPC JSON source file is not an array: " + path);
		}

		JsonArray array = parsed.getAsJsonArray();
		for (int i = 0; i < array.size(); i++)
		{
			JsonElement element = array.get(i);
			if (element != null && element.isJsonObject() && matchesJson(element.getAsJsonObject(), entry))
			{
				array.remove(i);
				writeJson(path, array);
				return;
			}
		}
		throw new IOException("NPC spawn was not found in JSON: " + entry.displayLocation());
	}

	private static boolean matchesJson(JsonObject object, Entry entry)
	{
		return jsonInt(object, "id", Integer.MIN_VALUE) == entry.id()
			&& jsonInt(object, "x", Integer.MIN_VALUE) == entry.worldX()
			&& jsonInt(object, "y", Integer.MIN_VALUE) == entry.worldY()
			&& jsonInt(object, "level", Integer.MIN_VALUE) == entry.plane()
			&& Objects.equals(jsonString(object, "name"), entry.name());
	}

	private static int jsonInt(JsonObject object, String key, int fallback)
	{
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive())
		{
			return fallback;
		}
		try
		{
			return element.getAsInt();
		}
		catch (RuntimeException ex)
		{
			return fallback;
		}
	}

	private static String jsonString(JsonObject object, String key)
	{
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive())
		{
			return "";
		}
		try
		{
			return Objects.toString(element.getAsString(), "");
		}
		catch (RuntimeException ex)
		{
			return "";
		}
	}

	private static void writeJson(Path path, JsonArray array) throws IOException
	{
		Path parent = path.getParent();
		if (parent != null)
		{
			Files.createDirectories(parent);
		}
		Path temp = Files.createTempFile(parent, "npc-spawns-", ".json.tmp");
		try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8))
		{
			PRETTY_GSON.toJson(array, writer);
			writer.write(System.lineSeparator());
		}
		moveAtomically(temp, path);
	}

	private static void upsertTsv(Entry entry) throws IOException
	{
		List<Entry> entries = readTsvEntries();
		entries.removeIf(existing -> existing.sameSpawn(entry));
		entries.add(entry.withSource(NpcSpawnIndex.SpawnSource.TSV));
		writeTsvEntries(entries);
	}

	private static void removeTsv(Entry entry) throws IOException
	{
		List<Entry> entries = readTsvEntries();
		boolean removed = entries.removeIf(existing -> existing.sameSpawn(entry));
		if (!removed)
		{
			throw new IOException("NPC spawn was not found in custom TSV: " + entry.displayLocation());
		}
		writeTsvEntries(entries);
	}

	private static List<Entry> readTsvEntries() throws IOException
	{
		Path path = NpcSpawnIndex.sourceCustomSpawnPath();
		if (!Files.isRegularFile(path))
		{
			return new ArrayList<>();
		}

		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			String headerLine = reader.readLine();
			if (headerLine == null)
			{
				return new ArrayList<>();
			}
			String[] headers = parseHeaderLine(headerLine);
			List<Entry> entries = new ArrayList<>();
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isBlank() || line.startsWith("#"))
				{
					continue;
				}
				Entry entry = readTsvEntry(line, headers);
				if (entry != null)
				{
					entries.add(entry);
				}
			}
			return entries;
		}
	}

	private static String[] parseHeaderLine(String headerLine)
	{
		String normalized = headerLine;
		if (normalized.startsWith("# "))
		{
			normalized = normalized.substring(2);
		}
		else if (normalized.startsWith("#"))
		{
			normalized = normalized.substring(1);
		}
		return normalized.split("\t", -1);
	}

	private static Entry readTsvEntry(String line, String[] headers)
	{
		String[] fields = line.split("\t", -1);
		Map<String, String> values = new HashMap<>();
		for (int i = 0; i < headers.length; i++)
		{
			values.put(headers[i], i < fields.length ? fields[i] : "");
		}

		int id = parseInt(values.get("id"), -1);
		int x = parseInt(values.get("x"), Integer.MIN_VALUE);
		int y = parseInt(values.get("y"), Integer.MIN_VALUE);
		int plane = parseInt(values.getOrDefault("level", values.get("plane")), 0);
		if (id < 0 || x == Integer.MIN_VALUE || y == Integer.MIN_VALUE)
		{
			return null;
		}
		return new Entry(
			id,
			Objects.toString(values.get("name"), ""),
			x,
			y,
			plane,
			parseNullableInt(values.get("faceDirection")),
			parseNullableBoolean(values.get("walk")),
			NpcSpawnIndex.SpawnSource.TSV,
			NpcCustomPath.parseEnabled(values.get("customPathEnabled")),
			NpcCustomPath.parse(values.get("customPath"))
		);
	}

	private static int parseInt(String value, int fallback)
	{
		if (value == null || value.isBlank())
		{
			return fallback;
		}
		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException ex)
		{
			return fallback;
		}
	}

	private static Integer parseNullableInt(String value)
	{
		if (value == null || value.isBlank() || "default".equalsIgnoreCase(value.trim()))
		{
			return null;
		}
		int parsed = parseInt(value, Integer.MIN_VALUE);
		return parsed >= 0 && parsed <= 7 ? parsed : null;
	}

	private static Boolean parseNullableBoolean(String value)
	{
		if (value == null || value.isBlank() || "default".equalsIgnoreCase(value.trim()))
		{
			return null;
		}
		return switch (value.trim().toLowerCase())
		{
			case "true", "yes", "y", "1", "enabled", "enable" -> Boolean.TRUE;
			case "false", "no", "n", "0", "disabled", "disable" -> Boolean.FALSE;
			default -> null;
		};
	}

	private static void writeTsvEntries(List<Entry> entries) throws IOException
	{
		Path path = NpcSpawnIndex.sourceCustomSpawnPath();
		Path parent = path.getParent();
		if (parent != null)
		{
			Files.createDirectories(parent);
		}
		Path temp = Files.createTempFile(parent, "npc-spawns-custom-", ".tsv.tmp");
		try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8))
		{
			writer.write(TSV_HEADER);
			writer.write(System.lineSeparator());
			for (Entry entry : entries)
			{
				writer.write(tsv(entry.id()));
				writer.write('\t');
				writer.write(tsv(entry.name()));
				writer.write('\t');
				writer.write(tsv(entry.worldX()));
				writer.write('\t');
				writer.write(tsv(entry.worldY()));
				writer.write('\t');
				writer.write(tsv(entry.plane()));
				writer.write('\t');
				writer.write(entry.faceDirection() == null ? "" : Integer.toString(entry.faceDirection()));
				writer.write('\t');
				writer.write(entry.walkEnabled() == null ? "" : (entry.walkEnabled() ? "enabled" : "disabled"));
				writer.write('\t');
				writer.write(entry.customPathEnabled() ? "enabled" : "");
				writer.write('\t');
				writer.write(tsv(NpcCustomPath.format(entry.customPath())));
				writer.write(System.lineSeparator());
			}
		}
		moveAtomically(temp, path);
	}

	private static String tsv(int value)
	{
		return Integer.toString(value);
	}

	private static String tsv(String value)
	{
		return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}

	private static void moveAtomically(Path temp, Path target) throws IOException
	{
		try
		{
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException ex)
		{
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	record Entry(
		int id,
		String name,
		int worldX,
		int worldY,
		int plane,
		Integer faceDirection,
		Boolean walkEnabled,
		NpcSpawnIndex.SpawnSource source,
		boolean customPathEnabled,
		List<NpcCustomPath.Point> customPath
	)
	{
		Entry(
			int id,
			String name,
			int worldX,
			int worldY,
			int plane,
			Integer faceDirection,
			Boolean walkEnabled,
			NpcSpawnIndex.SpawnSource source
		)
		{
			this(id, name, worldX, worldY, plane, faceDirection, walkEnabled, source, false, List.of());
		}

		Entry
		{
			name = Objects.toString(name, "");
			plane = Math.max(0, Math.min(3, plane));
			source = source == null ? NpcSpawnIndex.SpawnSource.JSON : source;
			customPath = NpcCustomPath.normalize(customPath);
			customPathEnabled = customPathEnabled && customPath.size() >= 2;
			if (faceDirection != null && (faceDirection < 0 || faceDirection > 7))
			{
				faceDirection = null;
			}
		}

		private Entry withSource(NpcSpawnIndex.SpawnSource nextSource)
		{
			return new Entry(
				id,
				name,
				worldX,
				worldY,
				plane,
				faceDirection,
				walkEnabled,
				nextSource,
				customPathEnabled,
				customPath
			);
		}

		Entry withCustomPath(boolean enabled, List<NpcCustomPath.Point> points)
		{
			return new Entry(
				id,
				name,
				worldX,
				worldY,
				plane,
				faceDirection,
				walkEnabled,
				source,
				enabled,
				points
			);
		}

		private boolean sameSpawn(Entry other)
		{
			return other != null
				&& id == other.id
				&& worldX == other.worldX
				&& worldY == other.worldY
				&& plane == other.plane
				&& Objects.equals(name, other.name);
		}

		private String displayLocation()
		{
			return id + " at " + worldX + "," + worldY + "," + plane;
		}
	}
}
