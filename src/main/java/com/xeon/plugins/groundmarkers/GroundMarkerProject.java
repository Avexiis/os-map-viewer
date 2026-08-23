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
package com.xeon.plugins.groundmarkers;

import com.xeon.model.Tile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class GroundMarkerProject
{
	private final Map<GroundMarker.MarkerKey, GroundMarker> markers = new LinkedHashMap<>();

	public List<GroundMarker> all()
	{
		return sortedCopies(markers.values());
	}

	public int size()
	{
		return markers.size();
	}

	public void clear()
	{
		markers.clear();
	}

	public Optional<GroundMarker> at(Tile tile)
	{
		if (tile == null)
		{
			return Optional.empty();
		}
		GroundMarker marker = markers.get(GroundMarker.MarkerKey.fromTile(tile));
		return marker == null ? Optional.empty() : Optional.of(marker.copy());
	}

	public void upsert(GroundMarker marker)
	{
		if (marker == null)
		{
			return;
		}
		GroundMarker normalized = marker.copy();
		normalized.label = GroundMarker.normalizeLabel(normalized.label);
		normalized.color = GroundMarker.normalizeColor(normalized.color);
		markers.put(normalized.key(), normalized);
	}

	public int addAll(Collection<GroundMarker> incoming)
	{
		int changed = 0;
		if (incoming == null)
		{
			return 0;
		}
		for (GroundMarker marker : incoming)
		{
			if (marker == null)
			{
				continue;
			}
			GroundMarker copy = marker.copy();
			GroundMarker previous = markers.put(copy.key(), copy);
			if (!copy.equals(previous))
			{
				changed++;
			}
		}
		return changed;
	}

	public boolean remove(Tile tile)
	{
		if (tile == null)
		{
			return false;
		}
		return markers.remove(GroundMarker.MarkerKey.fromTile(tile)) != null;
	}

	public boolean remove(GroundMarker marker)
	{
		if (marker == null)
		{
			return false;
		}
		return markers.remove(marker.key()) != null;
	}

	public List<GroundMarker> inRegion(int regionId)
	{
		List<GroundMarker> out = new ArrayList<>();
		for (GroundMarker marker : markers.values())
		{
			if (marker.regionId == regionId)
			{
				out.add(marker.copy());
			}
		}
		return sortedCopies(out);
	}

	public List<GroundMarker> inRegions(Set<Integer> regionIds)
	{
		List<GroundMarker> out = new ArrayList<>();
		if (regionIds == null || regionIds.isEmpty())
		{
			return out;
		}
		for (GroundMarker marker : markers.values())
		{
			if (regionIds.contains(marker.regionId))
			{
				out.add(marker.copy());
			}
		}
		return sortedCopies(out);
	}

	public int countInRegion(int regionId)
	{
		int count = 0;
		for (GroundMarker marker : markers.values())
		{
			if (marker.regionId == regionId)
			{
				count++;
			}
		}
		return count;
	}

	private static List<GroundMarker> sortedCopies(Collection<GroundMarker> source)
	{
		List<GroundMarker> out = new ArrayList<>();
		if (source != null)
		{
			for (GroundMarker marker : source)
			{
				if (marker != null)
				{
					out.add(marker.copy());
				}
			}
		}
		out.sort(Comparator
			.comparingInt((GroundMarker marker) -> marker.regionId)
			.thenComparingInt(marker -> marker.z)
			.thenComparingInt(marker -> marker.regionX)
			.thenComparingInt(marker -> marker.regionY)
			.thenComparing(marker -> marker.label == null ? "" : marker.label));
		return out;
	}
}
