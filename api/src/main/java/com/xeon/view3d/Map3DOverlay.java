package com.xeon.view3d;

import java.util.List;

public record Map3DOverlay(
	List<Map3DPathSegment> segments,
	List<Map3DMarker> markers,
	List<Map3DLabel> labels,
	List<Map3DTileOverlay> tileOverlays
)
{
	private static final Map3DOverlay EMPTY = new Map3DOverlay(List.of(), List.of(), List.of(), List.of());

	public Map3DOverlay(List<Map3DPathSegment> segments, List<Map3DMarker> markers, List<Map3DLabel> labels)
	{
		this(segments, markers, labels, List.of());
	}

	public Map3DOverlay
	{
		segments = segments == null ? List.of() : List.copyOf(segments);
		markers = markers == null ? List.of() : List.copyOf(markers);
		labels = labels == null ? List.of() : List.copyOf(labels);
		tileOverlays = tileOverlays == null ? List.of() : List.copyOf(tileOverlays);
	}

	public static Map3DOverlay empty()
	{
		return EMPTY;
	}
}
