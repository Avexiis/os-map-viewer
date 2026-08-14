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
package com.xeon.atlas;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RuneLiteCacheLocator
{
	private static final String CACHE_DATA_FILE = "main_file_cache.dat2";
	private static final String BOLT_DIR = "bolt-launcher";
	private static final String BOLT_FLATPAK_ID = "com.adamcake.Bolt";
	private static final String RUNELITE_FLATPAK_ID = "net.runelite.RuneLite";

	private RuneLiteCacheLocator()
	{
	}

	public static Path locateCacheDirectory()
	{
		for (Path candidate : candidateCacheDirectories())
		{
			if (isCacheDirectory(candidate))
			{
				return candidate;
			}
		}
		return null;
	}

	public static boolean isCacheDirectory(Path directory)
	{
		return directory != null
			&& Files.isDirectory(directory)
			&& Files.isRegularFile(directory.resolve(CACHE_DATA_FILE));
	}

	public static List<Path> candidateCacheDirectories()
	{
		LinkedHashSet<Path> dirs = new LinkedHashSet<>();
		for (Path runeLiteDir : candidateRuneLiteDirs())
		{
			addCacheCandidates(dirs, runeLiteDir);
		}
		for (Path boltDataDir : candidateBoltDataDirs())
		{
			addCacheCandidates(dirs, boltDataDir);
			addCacheCandidates(dirs, boltDataDir.resolve(".runelite"));
		}
		return new ArrayList<>(dirs);
	}

	private static void addCacheCandidates(Set<Path> dirs, Path root)
	{
		if (root == null)
		{
			return;
		}
		addPath(dirs, root.resolve("jagexcache").resolve("oldschool").resolve("LIVE"));
	}

	private static List<Path> candidateRuneLiteDirs()
	{
		LinkedHashSet<Path> dirs = new LinkedHashSet<>();
		for (Path home : candidateUserHomes())
		{
			addPath(dirs, home.resolve(".runelite"));
			addPath(dirs, home.resolve("Library").resolve("Application Support").resolve("RuneLite"));
			addPath(dirs, home.resolve(".config").resolve("runelite"));
			addPath(dirs, home.resolve("snap").resolve("runelite").resolve("current").resolve(".runelite"));
			addPath(dirs, home.resolve(".var").resolve("app").resolve(RUNELITE_FLATPAK_ID).resolve("config").resolve("RuneLite"));
			addPath(dirs, home.resolve(".var").resolve("app").resolve(RUNELITE_FLATPAK_ID).resolve("data").resolve("RuneLite"));
			addPath(dirs, home.resolve(".var").resolve("app").resolve(RUNELITE_FLATPAK_ID).resolve("data"));
		}

		addEnvDir(dirs, "APPDATA", "RuneLite");
		addEnvDir(dirs, "appdata", "RuneLite");
		addEnvDir(dirs, "LOCALAPPDATA", "RuneLite");
		addEnvDir(dirs, "XDG_CONFIG_HOME", "runelite");
		addEnvDir(dirs, "XDG_DATA_HOME", "RuneLite");
		return new ArrayList<>(dirs);
	}

	private static List<Path> candidateUserHomes()
	{
		LinkedHashSet<Path> homes = new LinkedHashSet<>();
		addPropertyPath(homes, "user.home");
		addEnvPath(homes, "HOME");
		addEnvPath(homes, "USERPROFILE");
		return new ArrayList<>(homes);
	}

	private static List<Path> candidateBoltDataDirs()
	{
		LinkedHashSet<Path> dirs = new LinkedHashSet<>();
		for (Path home : candidateUserHomes())
		{
			addPath(dirs, home.resolve(".local").resolve("share").resolve(BOLT_DIR));
			addPath(dirs, home.resolve(".var").resolve("app").resolve(BOLT_FLATPAK_ID).resolve("data").resolve(BOLT_DIR));
		}

		addEnvDir(dirs, "XDG_DATA_HOME", BOLT_DIR);
		addEnvDir(dirs, "APPDATA", Path.of(BOLT_DIR, "data").toString());
		addEnvDir(dirs, "appdata", Path.of(BOLT_DIR, "data").toString());
		addEnvDir(dirs, "LOCALAPPDATA", Path.of(BOLT_DIR, "data").toString());
		return new ArrayList<>(dirs);
	}

	private static void addPropertyPath(Set<Path> paths, String property)
	{
		addPath(paths, System.getProperty(property));
	}

	private static void addEnvPath(Set<Path> paths, String env)
	{
		addPath(paths, System.getenv(env));
	}

	private static void addEnvDir(Set<Path> dirs, String env, String child)
	{
		String base = System.getenv(env);
		if (base == null || base.isBlank())
		{
			return;
		}
		addPath(dirs, Path.of(base).resolve(child));
	}

	private static void addPath(Set<Path> paths, String value)
	{
		if (value == null || value.isBlank())
		{
			return;
		}
		addPath(paths, Path.of(value));
	}

	private static void addPath(Set<Path> paths, Path path)
	{
		if (path == null)
		{
			return;
		}
		paths.add(path.toAbsolutePath().normalize());
	}
}
