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
package com.xeon.util.wikisync;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class WikiSyncClient
{
	private static final String PROFILE_TYPE = "STANDARD";
	private static final String PLAYER_ENDPOINT = "https://sync.runescape.wiki/runelite/player/";
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
	private static final Gson GSON = new Gson();

	private final HttpClient httpClient;

	public WikiSyncClient()
	{
		this(HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build());
	}

	WikiSyncClient(HttpClient httpClient)
	{
		this.httpClient = httpClient;
	}

	public WikiSyncProfile lookup(String username)
		throws IOException, InterruptedException
	{
		String cleanUsername = cleanUsername(username);
		if (cleanUsername.isBlank())
		{
			throw new IOException("Enter a RuneScape username.");
		}

		URI uri = URI.create(PLAYER_ENDPOINT + encodePathSegment(cleanUsername) + "/" + PROFILE_TYPE);
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(15))
			.header("Accept", "application/json")
			.header("User-Agent", USER_AGENT)
			.GET()
			.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() < 200 || response.statusCode() >= 300)
		{
			throw new IOException(errorMessage(response.statusCode(), response.body()));
		}
		return parseProfile(response.body(), cleanUsername);
	}

	public static WikiSyncProfile parseProfile(String json, String requestedUsername)
		throws IOException
	{
		JsonElement parsed;
		try
		{
			parsed = GSON.fromJson(json, JsonElement.class);
		}
		catch (RuntimeException ex)
		{
			throw new IOException("WikiSync returned invalid JSON.", ex);
		}
		if (parsed == null || !parsed.isJsonObject())
		{
			throw new IOException("WikiSync returned an unexpected response.");
		}

		JsonObject root = parsed.getAsJsonObject();
		Map<String, Integer> allLevels = parseLevels(object(root, "levels"));
		Map<String, Integer> storedLevels = withDerivedLevels(allLevels);
		Set<String> completedQuests = completedQuests(object(root, "quests"));
		String username = stringValue(root.get("username"), cleanUsername(requestedUsername));
		Instant fetchedAt = instantValue(root.get("timestamp"), Instant.now());
		return new WikiSyncProfile(username, PROFILE_TYPE, fetchedAt, storedLevels, completedQuests);
	}

	private static Map<String, Integer> parseLevels(JsonObject levelsObject)
	{
		if (levelsObject == null)
		{
			return Map.of();
		}
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : levelsObject.entrySet())
		{
			Integer level = intValue(entry.getValue());
			String skill = ProfileNames.canonicalSkill(entry.getKey());
			if (level != null && !skill.isBlank())
			{
				levels.merge(skill, level, Math::max);
			}
		}
		return levels;
	}

	private static Map<String, Integer> withDerivedLevels(Map<String, Integer> allLevels)
	{
		if (allLevels.isEmpty())
		{
			return Map.of();
		}
		Map<String, Integer> levels = new LinkedHashMap<>(allLevels);
		Integer totalLevel = totalLevel(levels);
		if (totalLevel != null)
		{
			levels.put(ProfileNames.TOTAL_LEVEL, totalLevel);
		}
		Integer combatLevel = combatLevel(levels);
		if (combatLevel != null)
		{
			levels.put(ProfileNames.COMBAT_LEVEL, combatLevel);
		}
		return levels;
	}

	private static Set<String> completedQuests(JsonObject questsObject)
	{
		if (questsObject == null)
		{
			return Set.of();
		}
		Set<String> completed = new LinkedHashSet<>();
		for (Map.Entry<String, JsonElement> entry : questsObject.entrySet())
		{
			String quest = ProfileNames.canonicalQuest(entry.getKey());
			if (!quest.isBlank() && isFinishedQuest(entry.getValue()))
			{
				completed.add(quest);
			}
		}
		return completed;
	}

	private static Integer totalLevel(Map<String, Integer> levels)
	{
		Integer explicit = levels.get(ProfileNames.TOTAL_LEVEL);
		if (explicit != null)
		{
			return explicit;
		}
		int total = 0;
		for (String skill : ProfileNames.regularSkills())
		{
			Integer level = levels.get(skill);
			if (level == null)
			{
				return null;
			}
			total += level;
		}
		return total;
	}

	private static Integer combatLevel(Map<String, Integer> levels)
	{
		Integer explicit = levels.get(ProfileNames.COMBAT_LEVEL);
		if (explicit != null)
		{
			return explicit;
		}
		Integer attack = levels.get("attack");
		Integer strength = levels.get("strength");
		Integer defence = levels.get("defence");
		Integer hitpoints = levels.get("hitpoints");
		Integer ranged = levels.get("ranged");
		Integer prayer = levels.get("prayer");
		Integer magic = levels.get("magic");
		if (attack == null || strength == null || defence == null || hitpoints == null
			|| ranged == null || prayer == null || magic == null)
		{
			return null;
		}

		double base = 0.25 * (defence + hitpoints + Math.floor(prayer / 2.0));
		double melee = 0.325 * (attack + strength);
		double range = 0.325 * Math.floor(ranged * 1.5);
		double mage = 0.325 * Math.floor(magic * 1.5);
		return (int) Math.floor(base + Math.max(melee, Math.max(range, mage)));
	}

	private static boolean isFinishedQuest(JsonElement element)
	{
		if (element == null || !element.isJsonPrimitive())
		{
			return false;
		}
		try
		{
			if (element.getAsJsonPrimitive().isNumber())
			{
				return element.getAsInt() == 2;
			}
			if (element.getAsJsonPrimitive().isBoolean())
			{
				return element.getAsBoolean();
			}
			String value = element.getAsString();
			return "2".equals(value) || "finished".equalsIgnoreCase(value);
		}
		catch (RuntimeException ex)
		{
			return false;
		}
	}

	private static JsonObject object(JsonObject root, String key)
	{
		JsonElement element = root == null ? null : root.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static Integer intValue(JsonElement element)
	{
		if (element == null || !element.isJsonPrimitive())
		{
			return null;
		}
		try
		{
			return element.getAsInt();
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	private static Instant instantValue(JsonElement element, Instant fallback)
	{
		String value = stringValue(element, null);
		if (value == null || value.isBlank())
		{
			return fallback;
		}
		try
		{
			return Instant.parse(value);
		}
		catch (RuntimeException ex)
		{
			return fallback;
		}
	}

	private static String stringValue(JsonElement element, String fallback)
	{
		if (element == null || !element.isJsonPrimitive())
		{
			return fallback;
		}
		try
		{
			return element.getAsString();
		}
		catch (RuntimeException ex)
		{
			return fallback;
		}
	}

	private static String errorMessage(int statusCode, String body)
	{
		try
		{
			JsonElement parsed = GSON.fromJson(body, JsonElement.class);
			if (parsed != null && parsed.isJsonObject())
			{
				JsonObject object = parsed.getAsJsonObject();
				String error = stringValue(object.get("error"), null);
				if (error != null && !error.isBlank())
				{
					return "WikiSync lookup failed (" + statusCode + "): " + error;
				}
			}
		}
		catch (RuntimeException ignored)
		{
		}
		return "WikiSync lookup failed (" + statusCode + ").";
	}

	private static String cleanUsername(String username)
	{
		return username == null ? "" : username.trim();
	}

	private static String encodePathSegment(String value)
	{
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}
}
