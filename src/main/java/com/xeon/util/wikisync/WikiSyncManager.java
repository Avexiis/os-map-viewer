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

import com.xeon.config.ConfigManager;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.SwingUtilities;

public final class WikiSyncManager
{
	public static final String KEY_USERNAME = "wikiSync.username";
	public static final String KEY_PROFILE = "wikiSync.profile";

	private static final String CORE_NAMESPACE = ConfigManager.CORE_NAMESPACE;
	private static final String LEGACY_SHORTEST_PATH_NAMESPACE = "shortest-path";
	private static final Map<ConfigManager, WikiSyncManager> SHARED =
		Collections.synchronizedMap(new WeakHashMap<>());

	private final ConfigManager configManager;
	private final WikiSyncClient client;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "os-map-viewer-wikisync");
		thread.setDaemon(true);
		return thread;
	});
	private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

	private volatile String username = "";
	private volatile WikiSyncProfile profile;
	private volatile boolean lookupRunning;
	private volatile String message = "WikiSync profile not configured";
	private volatile Exception lastError;
	private volatile long stateVersion;
	private Future<?> lookupJob;
	private long lookupSerial;

	private WikiSyncManager(ConfigManager configManager)
	{
		this(configManager, new WikiSyncClient());
	}

	WikiSyncManager(ConfigManager configManager, WikiSyncClient client)
	{
		if (configManager == null)
		{
			throw new IllegalArgumentException("configManager must not be null");
		}
		this.configManager = configManager;
		this.client = client == null ? new WikiSyncClient() : client;
		migrateLegacyConfig();
		reloadFromConfig();
	}

	public static WikiSyncManager shared(ConfigManager configManager)
	{
		if (configManager == null)
		{
			throw new IllegalArgumentException("configManager must not be null");
		}
		synchronized (SHARED)
		{
			return SHARED.computeIfAbsent(configManager, WikiSyncManager::new);
		}
	}

	public synchronized void migrateLegacyConfig()
	{
		String coreUsername = cleanUsername(configManager.getString(CORE_NAMESPACE, KEY_USERNAME, ""));
		WikiSyncProfile coreProfile = configManager.getObject(CORE_NAMESPACE, KEY_PROFILE, WikiSyncProfile.class, null);
		String legacyUsername = cleanUsername(configManager.getString(LEGACY_SHORTEST_PATH_NAMESPACE, KEY_USERNAME, ""));
		WikiSyncProfile legacyProfile = configManager.getObject(
			LEGACY_SHORTEST_PATH_NAMESPACE,
			KEY_PROFILE,
			WikiSyncProfile.class,
			null
		);

		if (coreUsername.isBlank())
		{
			String migratedUsername = !legacyUsername.isBlank()
				? legacyUsername
				: legacyProfile == null ? "" : cleanUsername(legacyProfile.username());
			if (!migratedUsername.isBlank())
			{
				configManager.setString(CORE_NAMESPACE, KEY_USERNAME, migratedUsername);
			}
		}
		if (coreProfile == null && legacyProfile != null && legacyProfile.hasData())
		{
			configManager.setObject(CORE_NAMESPACE, KEY_PROFILE, legacyProfile);
		}

		if (configManager.contains(LEGACY_SHORTEST_PATH_NAMESPACE, KEY_USERNAME))
		{
			configManager.remove(LEGACY_SHORTEST_PATH_NAMESPACE, KEY_USERNAME);
		}
		if (configManager.contains(LEGACY_SHORTEST_PATH_NAMESPACE, KEY_PROFILE))
		{
			configManager.remove(LEGACY_SHORTEST_PATH_NAMESPACE, KEY_PROFILE);
		}
	}

	public void reloadFromConfig()
	{
		WikiSyncProfile loadedProfile = configManager.getObject(CORE_NAMESPACE, KEY_PROFILE, WikiSyncProfile.class, null);
		String loadedUsername = cleanUsername(configManager.getString(CORE_NAMESPACE, KEY_USERNAME, ""));
		if (loadedUsername.isBlank() && loadedProfile != null)
		{
			loadedUsername = cleanUsername(loadedProfile.username());
		}
		synchronized (this)
		{
			profile = loadedProfile;
			username = loadedUsername;
			lastError = null;
			message = loadedProfile != null && loadedProfile.hasData()
				? "WikiSync profile loaded"
				: "WikiSync profile not configured";
			stateVersion++;
		}
		notifyListeners();
	}

	public void refreshStoredProfileAsync()
	{
		String storedUsername = cleanUsername(configManager.getString(CORE_NAMESPACE, KEY_USERNAME, username));
		if (storedUsername.isBlank())
		{
			WikiSyncProfile currentProfile = profile;
			storedUsername = currentProfile == null ? "" : cleanUsername(currentProfile.username());
		}
		if (!storedUsername.isBlank())
		{
			lookupAndStoreAsync(storedUsername);
		}
	}

	public void lookupAndStoreAsync(String username)
	{
		submitLookup(username, true);
	}

	public String username()
	{
		return username;
	}

	public WikiSyncProfile profile()
	{
		return profile;
	}

	public boolean hasProfile()
	{
		WikiSyncProfile current = profile;
		return current != null && current.hasData();
	}

	public Integer level(String skill)
	{
		WikiSyncProfile current = profile;
		return current == null ? null : current.level(skill);
	}

	public boolean isLookupRunning()
	{
		return lookupRunning;
	}

	public String message()
	{
		return message;
	}

	public Exception lastError()
	{
		return lastError;
	}

	public long stateVersion()
	{
		return stateVersion;
	}

	public void addListener(Listener listener)
	{
		if (listener != null)
		{
			listeners.addIfAbsent(listener);
		}
	}

	public void removeListener(Listener listener)
	{
		if (listener != null)
		{
			listeners.remove(listener);
		}
	}

	public void shutdown()
	{
		synchronized (this)
		{
			lookupSerial++;
			if (lookupJob != null && !lookupJob.isDone())
			{
				lookupJob.cancel(true);
			}
			lookupRunning = false;
			stateVersion++;
		}
		executor.shutdownNow();
		notifyListeners();
	}

	private void submitLookup(String rawUsername, boolean saveOnSuccess)
	{
		String cleanUsername = cleanUsername(rawUsername);
		if (cleanUsername.isBlank())
		{
			synchronized (this)
			{
				lookupRunning = false;
				lastError = null;
				message = "Enter a RuneScape username.";
				stateVersion++;
			}
			notifyListeners();
			return;
		}

		long lookupId;
		synchronized (this)
		{
			lookupId = ++lookupSerial;
			if (lookupJob != null && !lookupJob.isDone())
			{
				lookupJob.cancel(true);
			}
			username = cleanUsername;
			lookupRunning = true;
			lastError = null;
			message = "Looking up WikiSync profile";
			stateVersion++;
		}
		notifyListeners();

		Future<?> job = executor.submit(() -> {
			try
			{
				WikiSyncProfile loadedProfile = client.lookup(cleanUsername);
				finishLookup(lookupId, loadedProfile, saveOnSuccess);
			}
			catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
			}
			catch (Exception ex)
			{
				failLookup(lookupId, ex);
			}
		});
		synchronized (this)
		{
			if (lookupId == lookupSerial)
			{
				lookupJob = job;
			}
			else
			{
				job.cancel(true);
			}
		}
	}

	private void finishLookup(long lookupId, WikiSyncProfile loadedProfile, boolean saveOnSuccess)
	{
		runOnEventThread(() -> {
			boolean accepted;
			synchronized (this)
			{
				accepted = lookupId == lookupSerial;
				if (accepted)
				{
					profile = loadedProfile;
					String loadedUsername = cleanUsername(loadedProfile == null ? "" : loadedProfile.username());
					if (!loadedUsername.isBlank())
					{
						username = loadedUsername;
					}
					lookupRunning = false;
					lastError = null;
					message = "WikiSync profile loaded";
					stateVersion++;
					if (saveOnSuccess && loadedProfile != null)
					{
						saveProfile(loadedProfile);
					}
				}
			}
			if (accepted)
			{
				notifyListeners();
			}
		});
	}

	private void failLookup(long lookupId, Exception ex)
	{
		runOnEventThread(() -> {
			boolean accepted;
			synchronized (this)
			{
				accepted = lookupId == lookupSerial;
				if (accepted)
				{
					lookupRunning = false;
					lastError = ex;
					message = ex.getMessage() == null || ex.getMessage().isBlank()
						? "WikiSync lookup failed."
						: ex.getMessage();
					stateVersion++;
				}
			}
			if (accepted)
			{
				notifyListeners();
			}
		});
	}

	private void saveProfile(WikiSyncProfile profile)
	{
		String savedUsername = cleanUsername(profile.username());
		if (!savedUsername.isBlank())
		{
			configManager.setString(CORE_NAMESPACE, KEY_USERNAME, savedUsername);
		}
		configManager.setObject(CORE_NAMESPACE, KEY_PROFILE, profile);
	}

	private void notifyListeners()
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			notifyListenersNow();
			return;
		}
		SwingUtilities.invokeLater(this::notifyListenersNow);
	}

	private void notifyListenersNow()
	{
		for (Listener listener : listeners)
		{
			listener.wikiSyncChanged(this);
		}
	}

	private static void runOnEventThread(Runnable runnable)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			runnable.run();
			return;
		}
		SwingUtilities.invokeLater(runnable);
	}

	private static String cleanUsername(String username)
	{
		return username == null ? "" : username.trim();
	}

	public interface Listener
	{
		void wikiSyncChanged(WikiSyncManager manager);
	}
}
