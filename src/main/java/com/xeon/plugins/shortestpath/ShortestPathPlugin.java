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
package com.xeon.plugins.shortestpath;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.xeon.config.PluginConfig;
import com.xeon.model.Tile;
import com.xeon.plugin.MapViewerPlugin;
import com.xeon.plugin.PluginContext;
import com.xeon.plugins.shortestpath.core.CollisionMap;
import com.xeon.plugins.shortestpath.core.PathOptions;
import com.xeon.plugins.shortestpath.core.PathStep;
import com.xeon.plugins.shortestpath.core.Pathfinder;
import com.xeon.plugins.shortestpath.core.PathfinderConfig;
import com.xeon.plugins.shortestpath.core.PathfinderResult;
import com.xeon.plugins.shortestpath.core.TeleportItem;
import com.xeon.plugins.shortestpath.core.Transport;
import com.xeon.plugins.shortestpath.core.TransportType;
import com.xeon.plugins.shortestpath.core.WorldPointUtil;
import com.xeon.util.wikisync.WikiSyncManager;
import com.xeon.util.wikisync.WikiSyncProfile;
import com.xeon.view.MapLayer;
import com.xeon.view.MapMouseEvent;
import com.xeon.view.MapRenderContext;
import com.xeon.view.MapTool;
import com.xeon.view.RegionChangeListener;
import com.xeon.view3d.Map3DControlHint;
import com.xeon.view3d.Map3DLabel;
import com.xeon.view3d.Map3DLayer;
import com.xeon.view3d.Map3DMarker;
import com.xeon.view3d.Map3DMouseEvent;
import com.xeon.view3d.Map3DOverlay;
import com.xeon.view3d.Map3DPathSegment;
import com.xeon.view3d.Map3DRenderContext;
import com.xeon.view3d.Map3DTileAction;

import java.io.File;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ShortestPathPlugin implements MapViewerPlugin, MapLayer, MapTool, Map3DLayer
{
	private static final String KEY_INCLUDE_TRANSPORTS = "includeTransports";
	private static final String KEY_INCLUDE_TELEPORTS = "includeTeleports";
	private static final String KEY_AVOID_WILDERNESS = "avoidWilderness";
	private static final String KEY_INCLUDE_POH = "includePoh";
	private static final String KEY_AVOID_ITEM_TELEPORTS = "avoidItemTeleports";
	private static final String KEY_DISABLED_TELEPORT_ITEMS = "teleportItems.disabled";
	private static final String KEY_DISABLED_TRANSPORT_TYPES = "transportTypes.disabled";
	private static final String KEY_SHOW_COLLISION_MAP = "showCollisionMap";
	private static final String KEY_COLLISION_MAP_MODE = "collisionMap.mode";
	private static final String KEY_3D_SHOW_ROUTE_LABELS = "viewer3d.showRouteTransportLabels";
	private static final String KEY_3D_SHOW_ALL_TRANSPORT_LABELS = "viewer3d.showAllTransportLabels";
	private static final String KEY_WALK_LINE_COLOR = "color.walkLine";
	private static final String KEY_TRANSPORT_LINE_COLOR = "color.transportLine";
	private static final String KEY_START_MARKER_COLOR = "color.startMarker";
	private static final String KEY_STEP_MARKER_COLOR = "color.stepMarker";
	private static final String KEY_TARGET_MARKER_COLOR = "color.targetMarker";
	private static final String KEY_COLLISION_COLOR = "color.collision";
	private static final Color DEFAULT_WALK_LINE_COLOR = new Color(0xFF27D6C4, true);
	private static final Color DEFAULT_TRANSPORT_LINE_COLOR = new Color(0xFFFF5C7A, true);
	private static final Color DEFAULT_START_MARKER_COLOR = new Color(0xFF35D07F, true);
	private static final Color DEFAULT_STEP_MARKER_COLOR = new Color(0xFF65A8FF, true);
	private static final Color DEFAULT_TARGET_MARKER_COLOR = new Color(0xFFFFC857, true);
	private static final Color DEFAULT_COLLISION_COLOR = new Color(0x99FFDD40, true);
	private static final int ROUTE_POINT_HIT_RADIUS_TILES = 2;
	private static final int MAX_3D_ALL_TRANSPORT_LABELS = 120;
	private static final int MAX_3D_DESTINATION_LABEL_LINES = 6;
	private static final int TRANSPORT_LABEL_GROUP_RADIUS_TILES = 10;
	private static final Gson ROUTE_GSON = new GsonBuilder()
		.disableHtmlEscaping()
		.setPrettyPrinting()
		.create();
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "os-map-viewer-shortest-path");
		thread.setDaemon(true);
		return thread;
	});

	private PluginContext context;
	private ShortestPathPanel panel;
	private ShortestPathRoutePanel routePanel;
	private ShortestPathMenu menu;
	private WikiSyncManager wikiSyncManager;
	private PathfinderConfig pathfinderConfig;
	private WikiSyncProfile activeWikiSyncProfile;
	private PathOptions options = PathOptions.defaults();
	private Set<TeleportItem> enabledTeleportItems = EnumSet.allOf(TeleportItem.class);
	private final LinkedHashSet<Integer> selectedCollisionRegionIds = new LinkedHashSet<>();
	private ShortestPathPanel.CollisionMapMode collisionMapMode = ShortestPathPanel.CollisionMapMode.OFF;
	private Color walkLineColor = DEFAULT_WALK_LINE_COLOR;
	private Color transportLineColor = DEFAULT_TRANSPORT_LINE_COLOR;
	private Color startMarkerColor = DEFAULT_START_MARKER_COLOR;
	private Color stepMarkerColor = DEFAULT_STEP_MARKER_COLOR;
	private Color targetMarkerColor = DEFAULT_TARGET_MARKER_COLOR;
	private Color collisionColor = DEFAULT_COLLISION_COLOR;
	private boolean show3DRouteLabels = true;
	private boolean show3DAllTransportLabels;
	private Tile startTile;
	private final List<Tile> stepTiles = new ArrayList<>();
	private Tile targetTile;
	private volatile Pathfinder currentPathfinder;
	private volatile List<PathfinderResult> routeResults = List.of();
	private volatile RouteFailure routeFailure;
	private volatile List<ShortestPathRoutePanel.Entry> routePointEntries = List.of();
	private Future<?> currentJob;
	private long calculationSerial;
	private String lastWarningSignature = "";
	private final RegionChangeListener hoveredRegionListener = this::handleHoveredRegionChanged;
	private final WikiSyncManager.Listener wikiSyncListener = ignored -> handleWikiSyncProfileChanged();

	@Override
	public String id()
	{
		return "shortest-path";
	}

	@Override
	public String displayName()
	{
		return "Shortest Path";
	}

	@Override
	public void install(PluginContext context)
	{
		this.context = context;
		panel = new ShortestPathPanel();
		routePanel = new ShortestPathRoutePanel();
		menu = new ShortestPathMenu();
		options = loadOptions(context.config());
		enabledTeleportItems = loadEnabledTeleportItems(context.config());
		collisionMapMode = loadCollisionMapMode(context.config());
		walkLineColor = loadColor(context.config(), KEY_WALK_LINE_COLOR, DEFAULT_WALK_LINE_COLOR);
		transportLineColor = loadColor(context.config(), KEY_TRANSPORT_LINE_COLOR, DEFAULT_TRANSPORT_LINE_COLOR);
		startMarkerColor = loadColor(context.config(), KEY_START_MARKER_COLOR, DEFAULT_START_MARKER_COLOR);
		stepMarkerColor = loadColor(context.config(), KEY_STEP_MARKER_COLOR, DEFAULT_STEP_MARKER_COLOR);
		targetMarkerColor = loadColor(context.config(), KEY_TARGET_MARKER_COLOR, DEFAULT_TARGET_MARKER_COLOR);
		collisionColor = loadColor(context.config(), KEY_COLLISION_COLOR, DEFAULT_COLLISION_COLOR);
		show3DRouteLabels = context.config().getBoolean(KEY_3D_SHOW_ROUTE_LABELS, true);
		show3DAllTransportLabels = context.config().getBoolean(KEY_3D_SHOW_ALL_TRANSPORT_LABELS, false);
		panel.setOptions(options.includeTransports(), options.includeTeleports(), options.avoidWilderness(),
			options.includePoh(), options.avoidItemTeleports(), options.enabledTransportTypes(), collisionMapMode);
		panel.setColors(walkLineColor, transportLineColor, startMarkerColor, stepMarkerColor,
			targetMarkerColor, collisionColor);
		panel.set3DOptions(show3DRouteLabels, show3DAllTransportLabels);
		panel.setTeleportItems(enabledTeleportItems);
		panel.setOnOptionsChanged(this::handleOptionsChanged);
		panel.setOn3DOptionsChanged(this::handle3DOptionsChanged);
		panel.setOnCenterStart(this::setStartToCenter);
		panel.setOnSwap(this::swapEndpoints);
		panel.setOnRecalculate(this::recalculate);
		panel.setOnImportRoute(this::importRoute);
		panel.setOnExportRoute(this::exportRoute);
		panel.setOnClear(this::clearPath);
		routePanel.setOnFocusTile(tile -> {
			if (tile != null && this.context != null)
			{
				this.context.focusTile(tile, null);
			}
		});
		menu.setOnCenterStart(this::setStartToCenter);
		menu.setOnCenterTarget(this::setTargetToCenter);
		menu.setOnRecalculate(this::recalculate);
		menu.setOnImportRoute(this::importRoute);
		menu.setOnExportRoute(this::exportRoute);
		menu.setOnClear(this::clearPath);

		panel.setStatus("loading data");
		pathfinderConfig = new PathfinderConfig(options);
		pathfinderConfig.setEnabledTeleportItems(enabledTeleportItems);
		panel.setStatus("idle");
		wikiSyncManager = WikiSyncManager.shared(context.configManager());
		wikiSyncManager.addListener(wikiSyncListener);
		applyWikiSyncProfile(wikiSyncManager.profile(), false);
		refreshPanel();

		context.mapPanel().addLayer(this);
		context.mapPanel().addHoveredRegionChangeListener(hoveredRegionListener);
		context.mapPanel().setActiveTool(this);
	}

	@Override
	public void uninstall()
	{
		cancelCurrentJob();
		if (wikiSyncManager != null)
		{
			wikiSyncManager.removeListener(wikiSyncListener);
			wikiSyncManager = null;
		}
		if (context != null)
		{
			context.mapPanel().removeHoveredRegionChangeListener(hoveredRegionListener);
			context.mapPanel().removeLayer(this);
			context.mapPanel().clearActiveTool(this);
		}
	}

	@Override
	public JPopupMenu actionMenu()
	{
		return menu;
	}

	@Override
	public JComponent leftComponent()
	{
		return routePanel;
	}

	@Override
	public JComponent rightComponent()
	{
		return panel;
	}

	@Override
	public boolean mouseClicked(MapMouseEvent event)
	{
		if (event == null || event.tile() == null)
		{
			return false;
		}
		if (isPopupTrigger(event))
		{
			return showTileActionMenu(event);
		}
		if (!SwingUtilities.isLeftMouseButton(event.source()))
		{
			return false;
		}
		if (isCollisionRegionSelectionClick(event))
		{
			toggleCollisionRegion(event.regionId());
			return true;
		}
		if (shouldClearCollisionRegions(event))
		{
			clearSelectedCollisionRegions();
			return true;
		}
		Tile tile = event.tile();
		if (startTile == null)
		{
			setStart(tile);
		}
		else if (event.isAdditiveSelection())
		{
			addStep(tile);
		}
		else
		{
			setTarget(tile);
		}
		return true;
	}

	@Override
	public boolean supportsRegionSelection()
	{
		return collisionMapMode == ShortestPathPanel.CollisionMapMode.SELECTED_REGION;
	}

	@Override
	public void paint(MapRenderContext context, Graphics2D g, Rectangle visibleMap)
	{
		paintCollisionMap(context, g, visibleMap);
		paintPath(context, g, visibleMap);
		paintEndpoint(context, g, visibleMap, startTile, startMarkerColor, "S");
		for (int i = 0; i < stepTiles.size(); i++)
		{
			paintEndpoint(context, g, visibleMap, stepTiles.get(i), stepMarkerColor, Integer.toString(i + 1));
		}
		paintEndpoint(context, g, visibleMap, targetTile, targetMarkerColor, "E");
	}

	@Override
	public String tooltipText(MapRenderContext context, Tile tile, int regionId)
	{
		if (tile == null)
		{
			return null;
		}
		if (collisionMapMode == ShortestPathPanel.CollisionMapMode.SELECTED_REGION
			&& context.regionSelectionActive()
			&& regionId >= 0)
		{
			return "Collision region " + regionText(regionId);
		}
		return routePointTooltip(tile);
	}

	@Override
	public Map3DOverlay overlay(Map3DRenderContext context)
	{
		List<Map3DPathSegment> segments = new ArrayList<>();
		List<Map3DMarker> markers = new ArrayList<>();
		List<Map3DLabel> labels = new ArrayList<>();
		Set<Integer> visibleRegions = context == null
			? Set.of()
			: new HashSet<>(context.visibleRegionIds());

		List<PathStep> steps = currentPath();
		for (int i = 1; i < steps.size(); i++)
		{
			int previous = steps.get(i - 1).getPackedPosition();
			int current = steps.get(i).getPackedPosition();
			Tile source = tile(previous);
			Tile destination = tile(current);
			boolean adjacent = isAdjacent(previous, current);
			segments.add(new Map3DPathSegment(
				source,
				destination,
				adjacent ? walkLineColor : transportLineColor,
				!adjacent
			));
		}

		add3DMarker(markers, startTile, startMarkerColor, "S");
		for (int i = 0; i < stepTiles.size(); i++)
		{
			add3DMarker(markers, stepTiles.get(i), stepMarkerColor, Integer.toString(i + 1));
		}
		add3DMarker(markers, targetTile, targetMarkerColor, "E");

		if (show3DRouteLabels)
		{
			for (RouteTransportLabel label : currentRouteTransportLabels(visibleRegions))
			{
				labels.add(new Map3DLabel(label.tile(), label.text(), transportLineColor, label.warpTarget()));
			}
		}
		if (show3DAllTransportLabels && pathfinderConfig != null)
		{
			Set<String> seen = new HashSet<>();
			for (Map3DLabel label : labels)
			{
				seen.add(tileText(label.tile()) + ":" + label.text());
			}
			List<RouteTransportLabel> extraLabels = currentAllTransportLabels(visibleRegions, context == null ? null : context.cameraTile());
			for (RouteTransportLabel label : extraLabels)
			{
				String key = tileText(label.tile()) + ":" + label.text();
				if (seen.add(key))
				{
					labels.add(new Map3DLabel(label.tile(), label.text(), transportLineColor, label.warpTarget()));
				}
			}
		}

		return new Map3DOverlay(segments, markers, labels);
	}

	@Override
	public List<Map3DTileAction> tileActions(Map3DMouseEvent event)
	{
		if (event == null || event.tile() == null)
		{
			return List.of();
		}

		Tile tile = event.tile();
		List<Map3DTileAction> actions = new ArrayList<>();
		actions.add(new Map3DTileAction("Set start", () -> setStart(tile)));
		if (startTile != null)
		{
			actions.add(new Map3DTileAction("Add step", () -> addStep(tile)));
		}

		ShortestPathRoutePanel.Entry routePoint = routePointAt(tile);
		if (routePoint != null)
		{
			actions.add(new Map3DTileAction("Remove " + routePointLabel(routePoint.pointIndex()),
				() -> removeRoutePointFromMenu(routePoint.pointIndex())));
		}

		actions.add(new Map3DTileAction("Set end", () -> setTarget(tile)));
		return List.copyOf(actions);
	}

	@Override
	public Tile clickWarpTarget(Map3DMouseEvent event)
	{
		if (event == null || event.tile() == null)
		{
			return null;
		}
		return transportWarpTargetAt(event.tile());
	}

	@Override
	public List<Map3DControlHint> controlHints()
	{
		return List.of(
			new Map3DControlHint("Right-click tile", "set start, add step, remove step, or set end"),
			new Map3DControlHint("Right-click world map", "edit route points from the floating map"),
			new Map3DControlHint("Click route step", "warp camera to that step"),
			new Map3DControlHint("Click transport flag", "warp to the next route step")
		);
	}

	private boolean isPopupTrigger(MapMouseEvent event)
	{
		return event.source().isPopupTrigger() || SwingUtilities.isRightMouseButton(event.source());
	}

	private boolean showTileActionMenu(MapMouseEvent event)
	{
		List<Map3DTileAction> actions = tileActions(new Map3DMouseEvent(
			event.tile(),
			event.source().getButton(),
			true,
			event.isAdditiveSelection(),
			event.isShiftDown()
		));
		if (actions.isEmpty())
		{
			return false;
		}
		JPopupMenu popup = new JPopupMenu();
		for (Map3DTileAction action : actions)
		{
			JMenuItem item = new JMenuItem(action.label());
			item.addActionListener(e -> action.run());
			popup.add(item);
		}
		MouseEvent source = event.source();
		popup.show(source.getComponent(), source.getX(), source.getY());
		return true;
	}

	private void add3DMarker(List<Map3DMarker> markers, Tile tile, Color color, String label)
	{
		if (tile != null)
		{
			markers.add(new Map3DMarker(tile, color, label));
		}
	}

	private List<RouteTransportLabel> currentRouteTransportLabels(Set<Integer> visibleRegions)
	{
		if (pathfinderConfig == null)
		{
			return List.of();
		}
		List<PathStep> steps = currentPath();
		if (steps.size() < 2)
		{
			return List.of();
		}

		List<RouteTransportLabel> labels = new ArrayList<>();
		for (int i = 1; i < steps.size(); i++)
		{
			PathStep previousStep = steps.get(i - 1);
			int previous = previousStep.getPackedPosition();
			int current = steps.get(i).getPackedPosition();
			if (isAdjacent(previous, current))
			{
				continue;
			}

			Transport transport = pathfinderConfig.findTransport(previous, current, previousStep.isBankVisited());
			if (!has3DTransportMarker(transport))
			{
				continue;
			}
			Tile source = tile(previous);
			Tile destination = tile(current);
			Tile labelTile = transportLabelTile(transport, source, destination);
			if (labelTile != null && isVisible(labelTile, visibleRegions))
			{
				labels.add(new RouteTransportLabel(labelTile, destination, routeTransportDisplayLabel(transport)));
			}
		}
		return List.copyOf(labels);
	}

	private List<RouteTransportLabel> currentAllTransportLabels(Set<Integer> visibleRegions, Tile cameraTile)
	{
		if (pathfinderConfig == null)
		{
			return List.of();
		}
		List<TransportLabelGroup> groups = new ArrayList<>();
		List<RouteTransportLabel> directLabels = new ArrayList<>();
		for (Transport transport : pathfinderConfig.visibleTransports())
		{
			if (!has3DTransportMarker(transport))
			{
				continue;
			}
			Tile origin = validPacked(transport.getOrigin()) ? tile(transport.getOrigin()) : null;
			Tile destination = validPacked(transport.getDestination()) ? tile(transport.getDestination()) : null;
			Tile labelTile = transportLabelTile(transport, origin, destination);
			if (labelTile == null || !isVisible(labelTile, visibleRegions))
			{
				continue;
			}
			if (isGrouped3DTransport(transport))
			{
				TransportLabelGroup group = findTransportLabelGroup(groups, transport.getType(), labelTile);
				if (group == null)
				{
					group = new TransportLabelGroup(transport.getType(), destination);
					groups.add(group);
				}
				group.add(labelTile, transport, destination);
				continue;
			}
			directLabels.add(new RouteTransportLabel(
				labelTile,
				destination,
				routeTransportDisplayLabel(transport)
			));
		}

		List<RouteTransportLabel> labels = new ArrayList<>(directLabels);
		for (TransportLabelGroup group : groups)
		{
			labels.addAll(group.labels());
		}
		labels.sort(Comparator
			.comparingInt((RouteTransportLabel label) -> tileDistance(cameraTile, label.tile()))
			.thenComparing(RouteTransportLabel::text, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(label -> tileText(label.tile())));
		return List.copyOf(labels.subList(0, Math.min(MAX_3D_ALL_TRANSPORT_LABELS, labels.size())));
	}

	private static TransportLabelGroup findTransportLabelGroup(List<TransportLabelGroup> groups, TransportType type, Tile tile)
	{
		for (TransportLabelGroup group : groups)
		{
			if (group.type() == type && tileDistance(group.tile(), tile) <= TRANSPORT_LABEL_GROUP_RADIUS_TILES)
			{
				return group;
			}
		}
		return null;
	}

	private RouteTransportLabel transportLabel(Transport transport, Set<Integer> visibleRegions)
	{
		if (!has3DTransportMarker(transport))
		{
			return null;
		}
		Tile origin = validPacked(transport.getOrigin()) ? tile(transport.getOrigin()) : null;
		Tile destination = validPacked(transport.getDestination()) ? tile(transport.getDestination()) : null;
		Tile labelTile = transportLabelTile(transport, origin, destination);
		if (labelTile == null || !isVisible(labelTile, visibleRegions))
		{
			return null;
		}
		return new RouteTransportLabel(labelTile, destination, routeTransportDisplayLabel(transport));
	}

	private static boolean has3DTransportMarker(Transport transport)
	{
		return transport != null
			&& transport.getDisplayInfo() != null
			&& !transport.getDisplayInfo().isBlank();
	}

	private Tile transportWarpTargetAt(Tile clickedTile)
	{
		for (RouteTransportLabel label : currentRouteTransportLabels(Set.of()))
		{
			if (nearTile(clickedTile, label.tile()) && label.warpTarget() != null)
			{
				return copy(label.warpTarget());
			}
		}
		return null;
	}

	private static Tile transportLabelTile(Transport transport, Tile source, Tile destination)
	{
		if (transport != null && validPacked(transport.getOrigin()))
		{
			return tile(transport.getOrigin());
		}
		return source != null ? copy(source) : copy(destination);
	}

	private static String routeTransportDisplayLabel(Transport transport)
	{
		if (transport == null)
		{
			return "Transport";
		}
		if (transport.getType() == TransportType.FAIRY_RING)
		{
			return "Fairy Ring";
		}
		if (transport.getDisplayInfo() != null && !transport.getDisplayInfo().isBlank())
		{
			String destination = destinationDisplayLabel(transport);
			if (isGrouped3DTransport(transport) && !destination.isBlank())
			{
				return transportTypeLabel(transport.getType()) + ": " + destination;
			}
			return destination;
		}
		if (transport.getType() == null)
		{
			return "Transport";
		}
		return transportTypeLabel(transport.getType());
	}

	private static boolean isGrouped3DTransport(Transport transport)
	{
		TransportType type = transport == null ? null : transport.getType();
		return type != null && !type.isTeleport() && type.getRadiusThreshold() > 0;
	}

	private static String destinationDisplayLabel(Transport transport)
	{
		if (transport == null || transport.getDisplayInfo() == null)
		{
			return "";
		}
		String label = transport.getDisplayInfo().trim();
		return label
			.replaceFirst("^[0-9A-Za-z]+\\s*[:.)-]\\s*", "")
			.trim();
	}

	private static String transportTypeLabel(TransportType type)
	{
		return type == null ? "Transport" : titleCase(type.name());
	}

	private static boolean isVisible(Tile tile, Set<Integer> visibleRegions)
	{
		return tile != null && (visibleRegions == null || visibleRegions.isEmpty() || visibleRegions.contains(regionId(tile)));
	}

	private static boolean nearTile(Tile a, Tile b)
	{
		return a != null && b != null && a.z == b.z
			&& Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y)) <= ROUTE_POINT_HIT_RADIUS_TILES;
	}

	private static int tileDistance(Tile a, Tile b)
	{
		if (a == null || b == null)
		{
			return Integer.MAX_VALUE;
		}
		int planePenalty = a.z == b.z ? 0 : 512;
		return planePenalty + Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
	}

	private static int regionId(Tile tile)
	{
		return (Math.floorDiv(tile.x, 64) << 8) | Math.floorDiv(tile.y, 64);
	}

	private static boolean validPacked(int packed)
	{
		return packed != WorldPointUtil.UNDEFINED && packed != Transport.LOCATION_PERMUTATION;
	}

	private static String titleCase(String value)
	{
		if (value == null || value.isBlank())
		{
			return "Transport";
		}
		StringBuilder out = new StringBuilder();
		for (String part : value.toLowerCase(Locale.ROOT).split("_+"))
		{
			if (part.isBlank())
			{
				continue;
			}
			if (out.length() > 0)
			{
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1)
			{
				out.append(part.substring(1));
			}
		}
		return out.length() == 0 ? "Transport" : out.toString();
	}

	private String routePointTooltip(Tile tile)
	{
		ShortestPathRoutePanel.Entry entry = routePointAt(tile);
		return entry == null ? null : entry.tooltip();
	}

	private ShortestPathRoutePanel.Entry routePointAt(Tile tile)
	{
		if (tile == null)
		{
			return null;
		}
		ShortestPathRoutePanel.Entry nearest = null;
		int nearestChebyshev = Integer.MAX_VALUE;
		int nearestManhattan = Integer.MAX_VALUE;
		for (ShortestPathRoutePanel.Entry entry : routePointEntries)
		{
			Tile entryTile = entry.tile();
			if (sameTile(tile, entryTile))
			{
				return entry;
			}
			if (entryTile == null || tile.z != entryTile.z)
			{
				continue;
			}
			int dx = Math.abs(tile.x - entryTile.x);
			int dy = Math.abs(tile.y - entryTile.y);
			int chebyshev = Math.max(dx, dy);
			int manhattan = dx + dy;
			if (chebyshev <= ROUTE_POINT_HIT_RADIUS_TILES
				&& (chebyshev < nearestChebyshev
				|| chebyshev == nearestChebyshev && manhattan < nearestManhattan))
			{
				nearest = entry;
				nearestChebyshev = chebyshev;
				nearestManhattan = manhattan;
			}
		}
		return nearest;
	}

	@Override
	public void planeChanged(int plane)
	{
		repaint();
	}

	@Override
	public void tileFocused(Tile tile)
	{
		if (tile != null && startTile == null)
		{
			setStart(tile);
		}
	}

	@Override
	public void viewer3DModeChanged(boolean active)
	{
		if (panel != null)
		{
			panel.set3DOptionsVisible(active);
		}
		repaint();
	}

	private void handleOptionsChanged()
	{
		options = new PathOptions(
			panel.includeTransports(),
			panel.includeTeleports(),
			panel.avoidWilderness(),
			panel.includePoh(),
			panel.avoidItemTeleports(),
			panel.enabledTransportTypes(),
			options.calculationCutoffMillis()
		);
		enabledTeleportItems = panel.enabledTeleportItems();
		ShortestPathPanel.CollisionMapMode previousCollisionMapMode = collisionMapMode;
		collisionMapMode = panel.collisionMapMode();
		walkLineColor = panel.walkLineColor();
		transportLineColor = panel.transportLineColor();
		startMarkerColor = panel.startMarkerColor();
		stepMarkerColor = panel.stepMarkerColor();
		targetMarkerColor = panel.targetMarkerColor();
		collisionColor = panel.collisionColor();
		show3DRouteLabels = panel.show3DRouteLabels();
		show3DAllTransportLabels = panel.show3DAllTransportLabels();
		saveOptions(context.config(), options);
		saveEnabledTeleportItems(context.config(), enabledTeleportItems);
		saveColors(context.config());
		saveCollisionMapMode(context.config(), collisionMapMode);
		save3DOptions(context.config());
		if (previousCollisionMapMode != collisionMapMode)
		{
			context.mapPanel().setActiveTool(this);
			repaintCollisionRegions(previousCollisionMapMode);
			repaintCollisionRegions(collisionMapMode);
		}
		cancelCurrentJob();
		pathfinderConfig.refresh(options);
		pathfinderConfig.setEnabledTeleportItems(enabledTeleportItems);
		recalculate();
	}

	private void handle3DOptionsChanged()
	{
		show3DRouteLabels = panel.show3DRouteLabels();
		show3DAllTransportLabels = panel.show3DAllTransportLabels();
		save3DOptions(context.config());
		repaint();
	}

	private boolean isCollisionRegionSelectionClick(MapMouseEvent event)
	{
		return collisionMapMode == ShortestPathPanel.CollisionMapMode.SELECTED_REGION
			&& event.isShiftDown()
			&& event.regionId() >= 0;
	}

	private void toggleCollisionRegion(int regionId)
	{
		if (regionId < 0)
		{
			return;
		}
		if (!selectedCollisionRegionIds.add(regionId))
		{
			selectedCollisionRegionIds.remove(regionId);
		}
		context.mapPanel().repaintRegion(regionId);
		int count = selectedCollisionRegionIds.size();
		context.setStatus(count == 0
			? "Cleared selected collision regions"
			: "Selected " + count + " collision region" + (count == 1 ? "" : "s"));
	}

	private boolean shouldClearCollisionRegions(MapMouseEvent event)
	{
		return collisionMapMode == ShortestPathPanel.CollisionMapMode.SELECTED_REGION
			&& !event.isShiftDown()
			&& !selectedCollisionRegionIds.isEmpty();
	}

	private void clearSelectedCollisionRegions()
	{
		List<Integer> previous = new ArrayList<>(selectedCollisionRegionIds);
		selectedCollisionRegionIds.clear();
		repaintRegions(previous);
		context.setStatus("Cleared selected collision regions");
	}

	private void handleHoveredRegionChanged(int previousRegionId, int currentRegionId)
	{
		if (collisionMapMode == ShortestPathPanel.CollisionMapMode.HOVERED_REGION)
		{
			repaintRegions(previousRegionId, currentRegionId);
		}
	}

	private void repaintCollisionRegions(ShortestPathPanel.CollisionMapMode mode)
	{
		if (context == null || mode == null)
		{
			return;
		}
		switch (mode)
		{
			case HOVERED_REGION -> repaintRegions(context.mapPanel().getHoveredRegionId());
			case SELECTED_REGION -> repaintRegions(selectedCollisionRegionIds);
			case OFF ->
			{
			}
		}
	}

	private void repaintRegions(int... regionIds)
	{
		if (context == null || regionIds == null)
		{
			return;
		}
		for (int regionId : regionIds)
		{
			context.mapPanel().repaintRegion(regionId);
		}
	}

	private void repaintRegions(Iterable<Integer> regionIds)
	{
		if (context == null || regionIds == null)
		{
			return;
		}
		for (Integer regionId : regionIds)
		{
			if (regionId != null)
			{
				context.mapPanel().repaintRegion(regionId);
			}
		}
	}

	private void setStartToCenter()
	{
		Tile tile = context.centerTile();
		if (tile != null)
		{
			setStart(tile);
		}
	}

	private void setTargetToCenter()
	{
		Tile tile = context.centerTile();
		if (tile != null)
		{
			setTarget(tile);
		}
	}

	private void swapEndpoints()
	{
		List<Tile> points = routePoints();
		if (points.size() < 2)
		{
			return;
		}
		List<Tile> reversed = new ArrayList<>(points.size());
		for (int i = points.size() - 1; i >= 0; i--)
		{
			reversed.add(copy(points.get(i)));
		}
		startTile = reversed.get(0);
		stepTiles.clear();
		for (int i = 1; i < reversed.size() - 1; i++)
		{
			stepTiles.add(copy(reversed.get(i)));
		}
		targetTile = reversed.size() > 1 ? copy(reversed.get(reversed.size() - 1)) : null;
		routeResults = List.of();
		routeFailure = null;
		lastWarningSignature = "";
		refreshPanel();
		recalculate();
		repaint();
	}

	private void clearPath()
	{
		cancelCurrentJob();
		startTile = null;
		stepTiles.clear();
		targetTile = null;
		routeResults = List.of();
		routeFailure = null;
		currentPathfinder = null;
		panel.setStatus("idle");
		refreshPanel();
		repaint();
	}

	private void handleWikiSyncProfileChanged()
	{
		WikiSyncManager manager = wikiSyncManager;
		applyWikiSyncProfile(manager == null ? null : manager.profile(), true);
	}

	private void applyWikiSyncProfile(WikiSyncProfile profile, boolean recalculateRoute)
	{
		if (pathfinderConfig == null || activeWikiSyncProfile == profile)
		{
			return;
		}
		activeWikiSyncProfile = profile;
		pathfinderConfig.setProfile(profile);
		if (recalculateRoute)
		{
			recalculate();
			repaint();
		}
	}

	private void setStart(Tile tile)
	{
		if (!isRoutePointAccessible(tile))
		{
			cancelCurrentJob();
			startTile = null;
			clearRouteCalculationState(false);
			refreshPanel();
			repaint();
			warnClearedInaccessible("Start", tile);
			return;
		}
		startTile = copy(tile);
		clearRouteCalculationState(true);
		refreshPanel();
		recalculate();
		repaint();
	}

	private void addStep(Tile tile)
	{
		if (!isRoutePointAccessible(tile))
		{
			warnClearedInaccessible("Step " + (stepTiles.size() + 1), tile);
			return;
		}
		stepTiles.add(copy(tile));
		clearRouteCalculationState(true);
		refreshPanel();
		recalculate();
		repaint();
	}

	private void setTarget(Tile tile)
	{
		if (!isRoutePointAccessible(tile))
		{
			targetTile = null;
			clearRouteCalculationState(false);
			refreshPanel();
			repaint();
			warnClearedInaccessible("End", tile);
			recalculate();
			return;
		}
		targetTile = copy(tile);
		clearRouteCalculationState(true);
		refreshPanel();
		recalculate();
		repaint();
	}

	private void clearRouteCalculationState(boolean resetWarningSignature)
	{
		routeResults = List.of();
		routeFailure = null;
		if (resetWarningSignature)
		{
			lastWarningSignature = "";
		}
	}

	private boolean clearBlockedRoutePoints()
	{
		boolean changed = false;
		if (startTile != null && !isRoutePointAccessible(startTile))
		{
			Tile cleared = startTile;
			startTile = null;
			warnClearedInaccessible("Start", cleared);
			changed = true;
		}
		for (int i = stepTiles.size() - 1; i >= 0; i--)
		{
			Tile step = stepTiles.get(i);
			if (!isRoutePointAccessible(step))
			{
				stepTiles.remove(i);
				warnClearedInaccessible("Step " + (i + 1), step);
				changed = true;
			}
		}
		if (targetTile != null && !isRoutePointAccessible(targetTile))
		{
			Tile cleared = targetTile;
			targetTile = null;
			warnClearedInaccessible("End", cleared);
			changed = true;
		}
		if (changed)
		{
			clearRouteCalculationState(false);
			refreshPanel();
			repaint();
		}
		return changed;
	}

	private boolean isRoutePointAccessible(Tile tile)
	{
		if (tile == null || tile.z < 0)
		{
			return false;
		}
		if (pathfinderConfig == null)
		{
			return true;
		}
		return !pathfinderConfig.getMap().isBlocked(tile.x, tile.y, tile.z);
	}

	private boolean removeRoutePoint(int pointIndex)
	{
		if (pointIndex == 0)
		{
			if (startTile == null)
			{
				return false;
			}
			startTile = null;
			return true;
		}
		if (pointIndex >= 1 && pointIndex <= stepTiles.size())
		{
			stepTiles.remove(pointIndex - 1);
			return true;
		}
		if (targetTile != null && pointIndex == stepTiles.size() + 1)
		{
			targetTile = null;
			return true;
		}
		return false;
	}

	private void removeRoutePointFromMenu(int pointIndex)
	{
		cancelCurrentJob();
		if (pointIndex == 0)
		{
			clearPath();
			return;
		}
		if (targetTile != null && pointIndex == stepTiles.size() + 1)
		{
			removeEndRoutePoint();
			return;
		}
		if (pointIndex >= 1 && pointIndex <= stepTiles.size())
		{
			stepTiles.remove(pointIndex - 1);
			finishManualRoutePointRemoval();
		}
	}

	private void removeEndRoutePoint()
	{
		if (stepTiles.isEmpty())
		{
			clearPath();
			return;
		}
		targetTile = stepTiles.remove(stepTiles.size() - 1);
		finishManualRoutePointRemoval();
	}

	private void finishManualRoutePointRemoval()
	{
		clearRouteCalculationState(true);
		refreshPanel();
		repaint();
		if (routePoints().size() >= 2)
		{
			recalculate();
		}
		else
		{
			panel.setStatus("idle");
		}
	}

	private void warnClearedInaccessible(String label, Tile tile)
	{
		String message = label + " is not accessible and was cleared: " + tileText(tile);
		panel.setStatus(message);
		context.setStatus(message);
		String signature = "cleared:" + label + ":" + tileText(tile);
		if (!signature.equals(lastWarningSignature))
		{
			lastWarningSignature = signature;
			JOptionPane.showMessageDialog(context.owner(), message, "Shortest Path Blocked", JOptionPane.WARNING_MESSAGE);
		}
	}

	private void warnInaccessible(RouteFailure failure, String label, boolean removed)
	{
		String message = label + " is not accessible"
			+ (removed ? " and was cleared: " : ": ")
			+ tileText(failure.point());
		panel.setStatus(message);
		context.setStatus(message);
		String signature = "blocked:" + label + ":" + tileText(failure.point()) + ":" + removed;
		if (!signature.equals(lastWarningSignature))
		{
			lastWarningSignature = signature;
			JOptionPane.showMessageDialog(context.owner(), message, "Shortest Path Blocked", JOptionPane.WARNING_MESSAGE);
		}
	}

	private void continueAfterRoutePointRemoval(boolean removed)
	{
		clearRouteCalculationState(false);
		refreshPanel();
		repaint();
		if (removed && routePoints().size() >= 2)
		{
			recalculate();
		}
	}

	private void recalculate()
	{
		cancelCurrentJob();
		clearBlockedRoutePoints();
		List<Tile> points = routePoints();
		if (context == null || pathfinderConfig == null || points.size() < 2)
		{
			refreshPanel();
			return;
		}
		long jobId = calculationSerial;
		routeResults = List.of();
		routeFailure = null;
		panel.setStatus("calculating");
		context.setStatus("Calculating shortest path");
		currentJob = executor.submit(() -> {
			RouteCalculation calculation = calculateRoute(jobId, points);
			SwingUtilities.invokeLater(() -> finishCalculation(jobId, calculation));
		});
		repaint();
	}

	private RouteCalculation calculateRoute(long jobId, List<Tile> points)
	{
		List<PathfinderResult> completed = new ArrayList<>();
		for (int i = 1; i < points.size(); i++)
		{
			if (jobId != calculationSerial)
			{
				return null;
			}
			Pathfinder next = new Pathfinder(pathfinderConfig, pack(points.get(i - 1)), Set.of(pack(points.get(i))), null);
			currentPathfinder = next;
			next.run();
			if (jobId != calculationSerial)
			{
				return null;
			}
			PathfinderResult leg = next.getResult();
			if (leg == null)
			{
				return new RouteCalculation(completed, new RouteFailure(i, copy(points.get(i)), null));
			}
			if (!leg.isReached())
			{
				return new RouteCalculation(completed, new RouteFailure(i, copy(points.get(i)), leg));
			}
			completed.add(leg);
		}
		return new RouteCalculation(completed, null);
	}

	private void finishCalculation(long jobId, RouteCalculation calculation)
	{
		if (jobId != calculationSerial || calculation == null)
		{
			return;
		}
		currentPathfinder = null;
		if (calculation.failure() != null)
		{
			RouteFailure failure = calculation.failure();
			String label = routePointLabel(failure.pointIndex());
			boolean removed = removeRoutePoint(failure.pointIndex());
			routeResults = List.copyOf(calculation.results());
			routeFailure = removed ? null : failure;
			continueAfterRoutePointRemoval(removed);
			warnInaccessible(failure, label, removed);
		}
		else if (calculation.results().isEmpty())
		{
			routeResults = List.copyOf(calculation.results());
			routeFailure = null;
			refreshPanel();
			repaint();
			panel.setStatus("cancelled");
		}
		else
		{
			routeResults = List.copyOf(calculation.results());
			routeFailure = null;
			refreshPanel();
			repaint();
			context.setStatus("Shortest path ready");
		}
	}

	private void cancelCurrentJob()
	{
		calculationSerial++;
		Pathfinder current = currentPathfinder;
		if (current != null && !current.isDone())
		{
			current.cancel();
		}
		if (currentJob != null && !currentJob.isDone())
		{
			currentJob.cancel(false);
		}
	}

	private void refreshPanel()
	{
		if (panel == null)
		{
			return;
		}
		panel.setTiles(startTile, List.copyOf(stepTiles), targetTile);
		List<PathfinderResult> currentResults = routeResults;
		RouteFailure failure = routeFailure;
		List<Tile> points = routePoints();
		routePointEntries = routePointEntries(points, currentResults, failure);
		if (routePanel != null)
		{
			routePanel.setEntries(routePointEntries);
		}
		if (currentResults.isEmpty() && failure == null)
		{
			if (points.size() < 2)
			{
				panel.setStatus("idle");
			}
			return;
		}
		long millis = Math.max(0L, totalElapsedNanos(currentResults, failure) / 1_000_000L);
		String status = (failure == null ? "reached" : "blocked")
			+ " in " + millis + " ms, "
			+ String.format("%,d", totalNodes(currentResults, failure))
			+ " nodes";
		panel.setStatus(status);
	}

	private List<RouteSegment> routeSegments(List<PathStep> steps)
	{
		List<RouteSegment> segments = new ArrayList<>();
		for (int i = 1; i < steps.size(); i++)
		{
			PathStep previous = steps.get(i - 1);
			PathStep current = steps.get(i);
			int source = previous.getPackedPosition();
			int destination = current.getPackedPosition();
			Transport transport = pathfinderConfig.findTransport(source, destination, previous.isBankVisited());
			int distance = WorldPointUtil.distanceBetween(source, destination);
			if (isAdjacent(source, destination))
			{
				segments.add(new RouteSegment(source, destination, Math.max(1, distance), null));
			}
			else
			{
				segments.add(new RouteSegment(source, destination, Math.max(1, distance), transport));
			}
		}
		return segments;
	}

	private List<ShortestPathRoutePanel.Entry> routePointEntries(List<Tile> points, List<PathfinderResult> results,
	                                                             RouteFailure failure)
	{
		if (points == null || points.isEmpty())
		{
			return List.of();
		}
		List<ShortestPathRoutePanel.Entry> entries = new ArrayList<>(points.size());
		for (int i = 0; i < points.size(); i++)
		{
			Tile tile = points.get(i);
			String pointLabel = routePointLabel(i, points.size(), targetTile != null);
			String detail = routePointDetail(i, results, failure, false);
			String tooltipDetail = routePointDetail(i, results, failure, true);
			entries.add(new ShortestPathRoutePanel.Entry(
				pointLabel,
				detail,
				routePointTooltip(pointLabel, tooltipDetail, tile),
				copy(tile),
				i
			));
		}
		return List.copyOf(entries);
	}

	private String routePointDetail(int pointIndex, List<PathfinderResult> results, RouteFailure failure,
	                                boolean includeCoordinates)
	{
		if (failure != null && failure.pointIndex() == pointIndex)
		{
			return "Blocked";
		}
		if (pointIndex == 0 || results == null || pointIndex - 1 >= results.size())
		{
			return pointIndex == 0 ? "Route start" : "Pending";
		}
		List<RouteSegment> legSegments = summarize(routeSegments(results.get(pointIndex - 1).getPathSteps()));
		if (legSegments.isEmpty())
		{
			return "Reached";
		}
		StringJoiner joiner = new StringJoiner("; ");
		for (RouteSegment segment : legSegments)
		{
			joiner.add(includeCoordinates ? segment.tooltipLabel() : segment.label());
		}
		return joiner.toString();
	}

	private static String routePointTooltip(String pointLabel, String detail, Tile tile)
	{
		String text = pointLabel + " " + tileText(tile);
		if (detail == null || detail.isBlank())
		{
			return text;
		}
		return text + " - " + detail;
	}

	private void paintPath(MapRenderContext context, Graphics2D g, Rectangle visibleMap)
	{
		List<PathStep> steps = currentPath();
		if (steps.size() < 2)
		{
			return;
		}

		Stroke oldStroke = g.getStroke();
		Object oldAntialias = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		float screenPx = Math.max(1.5f, (float) (2.75 / Math.max(context.effectiveZoom(), 0.0001)));
		Stroke walkStroke = new BasicStroke(screenPx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		float dash = Math.max(4f, (float) (8.0 / Math.max(context.effectiveZoom(), 0.0001)));
		Stroke transportStroke = new BasicStroke(screenPx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
			10f, new float[]{dash, dash}, 0f);

		for (int i = 1; i < steps.size(); i++)
		{
			int previous = steps.get(i - 1).getPackedPosition();
			int current = steps.get(i).getPackedPosition();
			if (WorldPointUtil.unpackWorldPlane(previous) != context.plane()
				|| WorldPointUtil.unpackWorldPlane(current) != context.plane())
			{
				continue;
			}
			Rectangle a = context.tileToRect(tile(previous));
			Rectangle b = context.tileToRect(tile(current));
			Line2D line = new Line2D.Double(centerX(a), centerY(a), centerX(b), centerY(b));
			if (!line.intersects(visibleMap))
			{
				continue;
			}
			boolean adjacent = isAdjacent(previous, current);
			g.setStroke(adjacent ? walkStroke : transportStroke);
			g.setColor(adjacent ? walkLineColor : transportLineColor);
			g.draw(line);
		}

		g.setStroke(oldStroke);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialias);
	}

	private void paintCollisionMap(MapRenderContext context, Graphics2D g, Rectangle visibleMap)
	{
		if (collisionMapMode == ShortestPathPanel.CollisionMapMode.OFF || pathfinderConfig == null)
		{
			return;
		}

		CollisionMap map = pathfinderConfig.getMap();
		Color oldColor = g.getColor();
		g.setColor(collisionColor);
		if (collisionMapMode == ShortestPathPanel.CollisionMapMode.HOVERED_REGION)
		{
			paintCollisionRegion(context, g, visibleMap, map, context.hoveredRegionId());
		}
		else if (collisionMapMode == ShortestPathPanel.CollisionMapMode.SELECTED_REGION)
		{
			for (int regionId : selectedCollisionRegionIds)
			{
				paintCollisionRegion(context, g, visibleMap, map, regionId);
			}
		}
		g.setColor(oldColor);
	}

	private void paintCollisionRegion(MapRenderContext context, Graphics2D g, Rectangle visibleMap,
	                                  CollisionMap map, int regionId)
	{
		if (regionId < 0)
		{
			return;
		}
		Rectangle regionRect = context.regionToRect(regionId);
		if (regionRect.isEmpty() || !regionRect.intersects(visibleMap))
		{
			return;
		}
		Rectangle tileBounds = context.regionTileBounds(regionId);
		if (tileBounds.isEmpty())
		{
			return;
		}
		Shape collisionShape = context.tileArea(tileBounds, context.plane(), map::isBlocked);
		if (!collisionShape.getBounds().isEmpty())
		{
			g.fill(collisionShape);
		}
	}

	private void paintEndpoint(MapRenderContext context, Graphics2D g, Rectangle visibleMap, Tile tile, Color color, String label)
	{
		if (tile == null || tile.z != context.plane())
		{
			return;
		}
		Rectangle rect = context.tileToRect(tile);
		if (!rect.intersects(visibleMap))
		{
			return;
		}
		int size = Math.max(rect.width, rect.height);
		int x = rect.x + rect.width / 2 - size / 2;
		int y = rect.y + rect.height / 2 - size / 2;

		Stroke oldStroke = g.getStroke();
		Font oldFont = g.getFont();
		Object oldTextAntialias = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
		double zoom = Math.max(context.effectiveZoom(), 0.0001);
		float screenPx = Math.max(1f, (float) (1.0 / zoom));
		g.setStroke(new BasicStroke(screenPx));
		g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 90));
		g.fillOval(x - size, y - size, size * 3, size * 3);
		g.setColor(color);
		g.drawOval(x - size, y - size, size * 3, size * 3);
		if (context.effectiveZoom() > 0.18)
		{
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			float fontSize = Math.max(8f, (float) (11.0 / zoom));
			g.setFont(g.getFont().deriveFont(Font.BOLD, fontSize));
			drawOutlinedString(g, label, x + size, y, color, zoom);
		}
		g.setFont(oldFont);
		g.setStroke(oldStroke);
		if (oldTextAntialias == null)
		{
			g.getRenderingHints().remove(RenderingHints.KEY_TEXT_ANTIALIASING);
		}
		else
		{
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldTextAntialias);
		}
	}

	private List<PathStep> currentPath()
	{
		return combinedPathSteps(routeResults);
	}

	private void repaint()
	{
		if (context != null)
		{
			context.repaintVisible();
		}
	}

	private void importRoute()
	{
		JFileChooser chooser = new JFileChooser(context.configManager().directory().toFile());
		chooser.setDialogTitle("Import Shortest Path Route");
		int answer = chooser.showOpenDialog(context.owner());
		if (answer != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		try
		{
			RouteData route = readRoute(chooser.getSelectedFile().toPath());
			startTile = copy(route.start());
			stepTiles.clear();
			for (Tile step : route.steps())
			{
				stepTiles.add(copy(step));
			}
			targetTile = copy(route.target());
			routeResults = List.of();
			routeFailure = null;
			lastWarningSignature = "";
			refreshPanel();
			recalculate();
			repaint();
			context.setStatus("Shortest path route imported");
		}
		catch (IOException | RuntimeException ex)
		{
			JOptionPane.showMessageDialog(context.owner(), ex.getMessage(), "Import Route Failed", JOptionPane.WARNING_MESSAGE);
		}
	}

	private void exportRoute()
	{
		List<Tile> points = routePoints();
		if (points.size() < 2)
		{
			JOptionPane.showMessageDialog(context.owner(), "Set a start and at least one step or end tile first.",
				"Export Route", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		JFileChooser chooser = new JFileChooser(context.configManager().directory().toFile());
		chooser.setDialogTitle("Export Shortest Path Route");
		chooser.setSelectedFile(new File("shortest-path-route.json"));
		int answer = chooser.showSaveDialog(context.owner());
		if (answer != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		Path file = withJsonExtension(chooser.getSelectedFile().toPath());
		try
		{
			writeRoute(file, new RouteData(copy(startTile), List.copyOf(stepTiles), copy(targetTile)));
			context.setStatus("Shortest path route exported");
		}
		catch (IOException ex)
		{
			JOptionPane.showMessageDialog(context.owner(), ex.getMessage(), "Export Route Failed", JOptionPane.WARNING_MESSAGE);
		}
	}

	private static RouteData readRoute(Path path) throws IOException
	{
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			JsonElement parsed = ROUTE_GSON.fromJson(reader, JsonElement.class);
			if (parsed == null || !parsed.isJsonObject())
			{
				throw new IOException("Route JSON must contain an object.");
			}
			JsonObject root = parsed.getAsJsonObject();
			Tile start = readTile(root.get("start"), "start");
			Tile target = root.has("target") && !root.get("target").isJsonNull()
				? readTile(root.get("target"), "target")
				: null;
			List<Tile> steps = new ArrayList<>();
			JsonElement stepElement = root.get("steps");
			if (stepElement != null && !stepElement.isJsonNull())
			{
				if (!stepElement.isJsonArray())
				{
					throw new IOException("Route steps must be an array.");
				}
				JsonArray array = stepElement.getAsJsonArray();
				for (int i = 0; i < array.size(); i++)
				{
					steps.add(readTile(array.get(i), "step " + (i + 1)));
				}
			}
			if (target == null && steps.isEmpty())
			{
				throw new IOException("Route JSON must contain at least one step or target.");
			}
			return new RouteData(start, steps, target);
		}
	}

	private static void writeRoute(Path path, RouteData route) throws IOException
	{
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("start", tileJson(route.start()));
		JsonArray steps = new JsonArray();
		for (Tile step : route.steps())
		{
			steps.add(tileJson(step));
		}
		root.add("steps", steps);
		if (route.target() != null)
		{
			root.add("target", tileJson(route.target()));
		}
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8))
		{
			ROUTE_GSON.toJson(root, writer);
		}
	}

	private static Path withJsonExtension(Path path)
	{
		String name = path.getFileName().toString();
		if (name.toLowerCase(Locale.ROOT).endsWith(".json"))
		{
			return path;
		}
		return path.resolveSibling(name + ".json");
	}

	private static PathOptions loadOptions(PluginConfig config)
	{
		PathOptions defaults = PathOptions.defaults();
		return new PathOptions(
			config.getBoolean(KEY_INCLUDE_TRANSPORTS, defaults.includeTransports()),
			config.getBoolean(KEY_INCLUDE_TELEPORTS, defaults.includeTeleports()),
			config.getBoolean(KEY_AVOID_WILDERNESS, defaults.avoidWilderness()),
			config.getBoolean(KEY_INCLUDE_POH, defaults.includePoh()),
			config.getBoolean(KEY_AVOID_ITEM_TELEPORTS, defaults.avoidItemTeleports()),
			loadEnabledTransportTypes(config),
			defaults.calculationCutoffMillis()
		);
	}

	private static ShortestPathPanel.CollisionMapMode loadCollisionMapMode(PluginConfig config)
	{
		String raw = config.getString(KEY_COLLISION_MAP_MODE, "");
		if (raw != null && !raw.isBlank())
		{
			try
			{
				return ShortestPathPanel.CollisionMapMode.valueOf(raw.trim());
			}
			catch (IllegalArgumentException ignored)
			{
				// Fall through to the legacy boolean.
			}
		}
		return config.getBoolean(KEY_SHOW_COLLISION_MAP, false)
			? ShortestPathPanel.CollisionMapMode.HOVERED_REGION
			: ShortestPathPanel.CollisionMapMode.OFF;
	}

	private static void saveOptions(PluginConfig config, PathOptions options)
	{
		config.setBoolean(KEY_INCLUDE_TRANSPORTS, options.includeTransports());
		config.setBoolean(KEY_INCLUDE_TELEPORTS, options.includeTeleports());
		config.setBoolean(KEY_AVOID_WILDERNESS, options.avoidWilderness());
		config.setBoolean(KEY_INCLUDE_POH, options.includePoh());
		config.setBoolean(KEY_AVOID_ITEM_TELEPORTS, options.avoidItemTeleports());
		saveEnabledTransportTypes(config, options.enabledTransportTypes());
	}

	private static void saveCollisionMapMode(PluginConfig config, ShortestPathPanel.CollisionMapMode mode)
	{
		ShortestPathPanel.CollisionMapMode value = mode == null
			? ShortestPathPanel.CollisionMapMode.OFF
			: mode;
		config.setString(KEY_COLLISION_MAP_MODE, value.name());
		config.setBoolean(KEY_SHOW_COLLISION_MAP, value != ShortestPathPanel.CollisionMapMode.OFF);
	}

	private static Set<TeleportItem> loadEnabledTeleportItems(PluginConfig config)
	{
		EnumSet<TeleportItem> enabled = EnumSet.allOf(TeleportItem.class);
		String raw = config.getString(KEY_DISABLED_TELEPORT_ITEMS, "");
		if (raw == null || raw.isBlank())
		{
			return enabled;
		}
		for (String token : raw.split(","))
		{
			String name = token.trim();
			if (name.isEmpty())
			{
				continue;
			}
			try
			{
				enabled.remove(TeleportItem.valueOf(name));
			}
			catch (IllegalArgumentException ignored)
			{
				// Ignore stale names so removed or renamed items do not break plugin loading.
			}
		}
		return enabled;
	}

	private static void saveEnabledTeleportItems(PluginConfig config, Set<TeleportItem> enabledItems)
	{
		Set<TeleportItem> enabled = copyTeleportItems(enabledItems);
		StringJoiner disabled = new StringJoiner(",");
		for (TeleportItem item : TeleportItem.values())
		{
			if (!enabled.contains(item))
			{
				disabled.add(item.name());
			}
		}
		String value = disabled.toString();
		if (value.isEmpty())
		{
			config.remove(KEY_DISABLED_TELEPORT_ITEMS);
		}
		else
		{
			config.setString(KEY_DISABLED_TELEPORT_ITEMS, value);
		}
	}

	private static Set<TransportType> loadEnabledTransportTypes(PluginConfig config)
	{
		EnumSet<TransportType> enabled = EnumSet.copyOf(PathOptions.defaultEnabledTransportTypes());
		String raw = config.getString(KEY_DISABLED_TRANSPORT_TYPES, "");
		if (raw == null || raw.isBlank())
		{
			return enabled;
		}
		for (String token : raw.split(","))
		{
			String name = token.trim();
			if (name.isEmpty())
			{
				continue;
			}
			try
			{
				enabled.remove(TransportType.valueOf(name));
			}
			catch (IllegalArgumentException ignored)
			{
				// Ignore stale transport type names from older configs.
			}
		}
		return enabled;
	}

	private static void saveEnabledTransportTypes(PluginConfig config, Set<TransportType> enabledTypes)
	{
		Set<TransportType> enabled = PathOptions.copyTransportTypes(enabledTypes);
		StringJoiner disabled = new StringJoiner(",");
		for (TransportType type : TransportType.values())
		{
			if (type.isLeagueOnly())
			{
				continue;
			}
			if (!enabled.contains(type))
			{
				disabled.add(type.name());
			}
		}
		String value = disabled.toString();
		if (value.isEmpty())
		{
			config.remove(KEY_DISABLED_TRANSPORT_TYPES);
		}
		else
		{
			config.setString(KEY_DISABLED_TRANSPORT_TYPES, value);
		}
	}

	private void saveColors(PluginConfig config)
	{
		config.setString(KEY_WALK_LINE_COLOR, colorString(walkLineColor));
		config.setString(KEY_TRANSPORT_LINE_COLOR, colorString(transportLineColor));
		config.setString(KEY_START_MARKER_COLOR, colorString(startMarkerColor));
		config.setString(KEY_STEP_MARKER_COLOR, colorString(stepMarkerColor));
		config.setString(KEY_TARGET_MARKER_COLOR, colorString(targetMarkerColor));
		config.setString(KEY_COLLISION_COLOR, colorString(collisionColor));
	}

	private void save3DOptions(PluginConfig config)
	{
		config.setBoolean(KEY_3D_SHOW_ROUTE_LABELS, show3DRouteLabels);
		config.setBoolean(KEY_3D_SHOW_ALL_TRANSPORT_LABELS, show3DAllTransportLabels);
	}

	private static Color loadColor(PluginConfig config, String key, Color fallback)
	{
		String raw = config.getString(key, "");
		if (raw == null || raw.isBlank())
		{
			return fallback;
		}
		try
		{
			String clean = raw.trim();
			if (clean.startsWith("#"))
			{
				clean = clean.substring(1);
			}
			if (clean.length() == 6)
			{
				return new Color((int) Long.parseLong(clean, 16) | 0xFF000000, true);
			}
			if (clean.length() == 8)
			{
				return new Color((int) Long.parseLong(clean, 16), true);
			}
		}
		catch (RuntimeException ignored)
		{
		}
		return fallback;
	}

	private static String colorString(Color color)
	{
		int argb = (color.getAlpha() << 24) | (color.getRGB() & 0x00FFFFFF);
		return String.format("#%08X", argb);
	}

	private static Tile readTile(JsonElement element, String label) throws IOException
	{
		if (element == null || !element.isJsonObject())
		{
			throw new IOException("Route " + label + " must be an object.");
		}
		JsonObject object = element.getAsJsonObject();
		return new Tile(readInt(object, "x", label), readInt(object, "y", label), readInt(object, "z", label));
	}

	private static int readInt(JsonObject object, String key, String label) throws IOException
	{
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive())
		{
			throw new IOException("Route " + label + " is missing " + key + ".");
		}
		try
		{
			return element.getAsInt();
		}
		catch (RuntimeException ex)
		{
			throw new IOException("Route " + label + " has invalid " + key + ".", ex);
		}
	}

	private static JsonObject tileJson(Tile tile)
	{
		JsonObject object = new JsonObject();
		object.addProperty("x", tile.x);
		object.addProperty("y", tile.y);
		object.addProperty("z", tile.z);
		return object;
	}

	private static boolean isAdjacent(int source, int destination)
	{
		return WorldPointUtil.unpackWorldPlane(source) == WorldPointUtil.unpackWorldPlane(destination)
			&& Math.abs(WorldPointUtil.unpackWorldX(source) - WorldPointUtil.unpackWorldX(destination)) <= 1
			&& Math.abs(WorldPointUtil.unpackWorldY(source) - WorldPointUtil.unpackWorldY(destination)) <= 1;
	}

	private List<Tile> routePoints()
	{
		if (startTile == null)
		{
			return List.of();
		}
		List<Tile> points = new ArrayList<>(stepTiles.size() + 2);
		points.add(copy(startTile));
		for (Tile step : stepTiles)
		{
			points.add(copy(step));
		}
		if (targetTile != null)
		{
			points.add(copy(targetTile));
		}
		return points;
	}

	private String routePointLabel(int pointIndex)
	{
		return routePointLabel(pointIndex, routePoints().size(), targetTile != null);
	}

	private static String routePointLabel(int pointIndex, int pointCount, boolean hasTarget)
	{
		if (pointIndex == 0)
		{
			return "Start";
		}
		if (hasTarget && pointIndex == pointCount - 1)
		{
			return "End";
		}
		return "Step " + pointIndex;
	}

	private static List<PathStep> combinedPathSteps(List<PathfinderResult> results)
	{
		if (results == null || results.isEmpty())
		{
			return List.of();
		}
		List<PathStep> out = new ArrayList<>();
		for (PathfinderResult result : results)
		{
			List<PathStep> steps = result.getPathSteps();
			if (steps.isEmpty())
			{
				continue;
			}
			int start = out.isEmpty() ? 0 : 1;
			for (int i = start; i < steps.size(); i++)
			{
				out.add(steps.get(i));
			}
		}
		return out;
	}

	private static int totalNodes(List<PathfinderResult> results, RouteFailure failure)
	{
		int total = 0;
		if (results != null)
		{
			for (PathfinderResult result : results)
			{
				total += result.getNodesChecked() + result.getTransportsChecked();
			}
		}
		if (failure != null && failure.result() != null)
		{
			total += failure.result().getNodesChecked() + failure.result().getTransportsChecked();
		}
		return total;
	}

	private static long totalElapsedNanos(List<PathfinderResult> results, RouteFailure failure)
	{
		long total = 0L;
		if (results != null)
		{
			for (PathfinderResult result : results)
			{
				total += result.getElapsedNanos();
			}
		}
		if (failure != null && failure.result() != null)
		{
			total += failure.result().getElapsedNanos();
		}
		return total;
	}

	private static List<RouteSegment> summarize(List<RouteSegment> segments)
	{
		List<RouteSegment> summarized = new ArrayList<>();
		int walkTiles = 0;
		for (RouteSegment segment : segments)
		{
			if (segment.transport() == null)
			{
				walkTiles += segment.distance();
			}
			else
			{
				if (walkTiles > 0)
				{
					summarized.add(RouteSegment.walk(walkTiles));
					walkTiles = 0;
				}
				summarized.add(segment);
			}
		}
		if (walkTiles > 0)
		{
			summarized.add(RouteSegment.walk(walkTiles));
		}
		return summarized;
	}

	private static Set<TeleportItem> copyTeleportItems(Set<TeleportItem> items)
	{
		if (items == null)
		{
			return EnumSet.allOf(TeleportItem.class);
		}
		if (items.isEmpty())
		{
			return EnumSet.noneOf(TeleportItem.class);
		}
		return EnumSet.copyOf(items);
	}

	private static void drawOutlinedString(Graphics2D g, String text, int x, int y, Color fill, double zoom)
	{
		Color oldColor = g.getColor();
		float offset = (float) (0.9 / Math.max(zoom, 0.0001));
		g.setColor(Color.BLACK);
		g.drawString(text, x - offset, y);
		g.drawString(text, x + offset, y);
		g.drawString(text, x, y - offset);
		g.drawString(text, x, y + offset);
		g.setColor(fill);
		g.drawString(text, x, y);
		g.setColor(oldColor);
	}

	private static boolean sameTile(Tile a, Tile b)
	{
		return a != null && b != null && a.x == b.x && a.y == b.y && a.z == b.z;
	}

	private static int pack(Tile tile)
	{
		return WorldPointUtil.packWorldPoint(tile.x, tile.y, tile.z);
	}

	private static Tile tile(int packed)
	{
		return new Tile(
			WorldPointUtil.unpackWorldX(packed),
			WorldPointUtil.unpackWorldY(packed),
			WorldPointUtil.unpackWorldPlane(packed)
		);
	}

	private static Tile copy(Tile tile)
	{
		return tile == null ? null : new Tile(tile.x, tile.y, tile.z);
	}

	private static String tileText(Tile tile)
	{
		return tile == null ? "none" : tile.x + "," + tile.y + "," + tile.z;
	}

	private static String regionText(int regionId)
	{
		if (regionId < 0)
		{
			return "none";
		}
		return regionId + " (" + (regionId >> 8) + "," + (regionId & 0xFF) + ")";
	}

	private static int centerX(Rectangle rect)
	{
		return rect.x + rect.width / 2;
	}

	private static int centerY(Rectangle rect)
	{
		return rect.y + rect.height / 2;
	}

	private static String packedText(int packed)
	{
		if (packed == WorldPointUtil.UNDEFINED)
		{
			return "unknown";
		}
		return WorldPointUtil.unpackWorldX(packed) + ","
			+ WorldPointUtil.unpackWorldY(packed) + ","
			+ WorldPointUtil.unpackWorldPlane(packed);
	}

	private record RouteSegment(int source, int destination, int distance, Transport transport)
	{
		static RouteSegment walk(int distance)
		{
			return new RouteSegment(WorldPointUtil.UNDEFINED, WorldPointUtil.UNDEFINED, distance, null);
		}

		String label()
		{
			if (transport == null)
			{
				return "Walk " + distance + " tile" + (distance == 1 ? "" : "s");
			}
			return "Transport: " + transport.label();
		}

		String tooltipLabel()
		{
			if (transport == null)
			{
				return label();
			}
			return label() + " -> " + packedText(destination);
		}
	}

	private record RouteCalculation(List<PathfinderResult> results, RouteFailure failure)
	{
	}

	private record RouteFailure(int pointIndex, Tile point, PathfinderResult result)
	{
	}

	private record RouteTransportLabel(Tile tile, Tile warpTarget, String text)
	{
	}

	private static final class TransportLabelGroup
	{
		private final TransportType type;
		private final LinkedHashSet<String> destinations = new LinkedHashSet<>();
		private Tile warpTarget;
		private int sumX;
		private int sumY;
		private int plane;
		private int count;

		private TransportLabelGroup(TransportType type, Tile warpTarget)
		{
			this.type = type;
			this.warpTarget = copy(warpTarget);
		}

		private TransportType type()
		{
			return type;
		}

		private Tile tile()
		{
			if (count <= 0)
			{
				return null;
			}
			return new Tile(Math.round(sumX / (float) count), Math.round(sumY / (float) count), plane);
		}

		private void add(Tile tile, Transport transport, Tile destination)
		{
			addTile(tile);
			if (warpTarget == null)
			{
				warpTarget = copy(destination);
			}
			if (type == TransportType.FAIRY_RING)
			{
				return;
			}
			String destinationLabel = destinationDisplayLabel(transport);
			if (!destinationLabel.isBlank())
			{
				destinations.add(destinationLabel);
			}
		}

		private List<RouteTransportLabel> labels()
		{
			Tile tile = tile();
			if (tile == null)
			{
				return List.of();
			}
			if (type == TransportType.FAIRY_RING)
			{
				return List.of(new RouteTransportLabel(tile, warpTarget, "Fairy Ring"));
			}
			List<RouteTransportLabel> labels = new ArrayList<>();
			labels.add(new RouteTransportLabel(tile, warpTarget, transportTypeLabel(type)));
			int shown = 0;
			for (String destination : destinations)
			{
				if (shown >= MAX_3D_DESTINATION_LABEL_LINES)
				{
					break;
				}
				labels.add(new RouteTransportLabel(tile, warpTarget, destination));
				shown++;
			}
			int remaining = destinations.size() - shown;
			if (remaining > 0)
			{
				labels.add(new RouteTransportLabel(tile, warpTarget, "+" + remaining + " more"));
			}
			return List.copyOf(labels);
		}

		private void addTile(Tile tile)
		{
			if (tile == null)
			{
				return;
			}
			sumX += tile.x;
			sumY += tile.y;
			plane = tile.z;
			count++;
		}
	}

	private record RouteData(Tile start, List<Tile> steps, Tile target)
	{
	}
}
