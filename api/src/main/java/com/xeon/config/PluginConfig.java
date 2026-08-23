package com.xeon.config;

import com.google.gson.JsonElement;

import java.util.Set;

public final class PluginConfig
{
	private final ConfigManager manager;
	private final String namespace;

	PluginConfig(ConfigManager manager, String namespace)
	{
		this.manager = manager;
		this.namespace = namespace;
	}

	public String namespace()
	{
		return namespace;
	}

	public Set<String> keys()
	{
		return manager.keys(namespace);
	}

	public boolean contains(String key)
	{
		return manager.contains(namespace, key);
	}

	public JsonElement getElement(String key)
	{
		return manager.getElement(namespace, key);
	}

	public void setElement(String key, JsonElement value)
	{
		manager.setElement(namespace, key, value);
	}

	public <T> T getObject(String key, Class<T> type, T defaultValue)
	{
		return manager.getObject(namespace, key, type, defaultValue);
	}

	public void setObject(String key, Object value)
	{
		manager.setObject(namespace, key, value);
	}

	public String getString(String key, String defaultValue)
	{
		return manager.getString(namespace, key, defaultValue);
	}

	public void setString(String key, String value)
	{
		manager.setString(namespace, key, value);
	}

	public boolean getBoolean(String key, boolean defaultValue)
	{
		return manager.getBoolean(namespace, key, defaultValue);
	}

	public void setBoolean(String key, boolean value)
	{
		manager.setBoolean(namespace, key, value);
	}

	public int getInt(String key, int defaultValue)
	{
		return manager.getInt(namespace, key, defaultValue);
	}

	public void setInt(String key, int value)
	{
		manager.setInt(namespace, key, value);
	}

	public double getDouble(String key, double defaultValue)
	{
		return manager.getDouble(namespace, key, defaultValue);
	}

	public void setDouble(String key, double value)
	{
		manager.setDouble(namespace, key, value);
	}

	public void remove(String key)
	{
		manager.remove(namespace, key);
	}
}
