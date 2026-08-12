package com.xeon.plugins.shortestpath;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.xeon.config.PluginConfig;
import com.xeon.io.Paths;
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
import com.xeon.plugins.shortestpath.core.WikiSyncClient;
import com.xeon.plugins.shortestpath.core.WikiSyncProfile;
import com.xeon.plugins.shortestpath.core.WorldPointUtil;
import com.xeon.view.MapLayer;
import com.xeon.view.MapMouseEvent;
import com.xeon.view.MapRenderContext;
import com.xeon.view.MapTool;

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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ShortestPathPlugin implements MapViewerPlugin, MapLayer, MapTool {
    private static final String KEY_INCLUDE_TRANSPORTS = "includeTransports";
    private static final String KEY_INCLUDE_TELEPORTS = "includeTeleports";
    private static final String KEY_AVOID_WILDERNESS = "avoidWilderness";
    private static final String KEY_INCLUDE_POH = "includePoh";
    private static final String KEY_AVOID_ITEM_TELEPORTS = "avoidItemTeleports";
    private static final String KEY_DISABLED_TELEPORT_ITEMS = "teleportItems.disabled";
    private static final String KEY_DISABLED_TRANSPORT_TYPES = "transportTypes.disabled";
    private static final String KEY_SHOW_COLLISION_MAP = "showCollisionMap";
    private static final String KEY_WALK_LINE_COLOR = "color.walkLine";
    private static final String KEY_TRANSPORT_LINE_COLOR = "color.transportLine";
    private static final String KEY_START_MARKER_COLOR = "color.startMarker";
    private static final String KEY_STEP_MARKER_COLOR = "color.stepMarker";
    private static final String KEY_TARGET_MARKER_COLOR = "color.targetMarker";
    private static final String KEY_COLLISION_COLOR = "color.collision";
    private static final String KEY_WIKISYNC_USERNAME = "wikiSync.username";
    private static final String KEY_WIKISYNC_PROFILE = "wikiSync.profile";
    private static final Color DEFAULT_WALK_LINE_COLOR = new Color(0xFF27D6C4, true);
    private static final Color DEFAULT_TRANSPORT_LINE_COLOR = new Color(0xFFFF5C7A, true);
    private static final Color DEFAULT_START_MARKER_COLOR = new Color(0xFF35D07F, true);
    private static final Color DEFAULT_STEP_MARKER_COLOR = new Color(0xFF65A8FF, true);
    private static final Color DEFAULT_TARGET_MARKER_COLOR = new Color(0xFFFFC857, true);
    private static final Color DEFAULT_COLLISION_COLOR = new Color(0x99FFDD40, true);
    private static final int ROUTE_POINT_HIT_RADIUS_TILES = 2;
    private static final Gson ROUTE_GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "os-map-viewer-shortest-path");
        thread.setDaemon(true);
        return thread;
    });
    private final WikiSyncClient wikiSyncClient = new WikiSyncClient();

    private PluginContext context;
    private ShortestPathPanel panel;
    private ShortestPathRoutePanel routePanel;
    private ShortestPathMenu menu;
    private PathfinderConfig pathfinderConfig;
    private PathOptions options = PathOptions.defaults();
    private Set<TeleportItem> enabledTeleportItems = EnumSet.allOf(TeleportItem.class);
    private boolean showCollisionMap;
    private Color walkLineColor = DEFAULT_WALK_LINE_COLOR;
    private Color transportLineColor = DEFAULT_TRANSPORT_LINE_COLOR;
    private Color startMarkerColor = DEFAULT_START_MARKER_COLOR;
    private Color stepMarkerColor = DEFAULT_STEP_MARKER_COLOR;
    private Color targetMarkerColor = DEFAULT_TARGET_MARKER_COLOR;
    private Color collisionColor = DEFAULT_COLLISION_COLOR;
    private Tile startTile;
    private final List<Tile> stepTiles = new ArrayList<>();
    private Tile targetTile;
    private volatile Pathfinder currentPathfinder;
    private volatile List<PathfinderResult> routeResults = List.of();
    private volatile RouteFailure routeFailure;
    private volatile List<ShortestPathRoutePanel.Entry> routePointEntries = List.of();
    private Future<?> currentJob;
    private Future<?> profileJob;
    private long profileLookupSerial;
    private long calculationSerial;
    private String lastWarningSignature = "";

    @Override
    public String id() {
        return "shortest-path";
    }

    @Override
    public String displayName() {
        return "Shortest Path";
    }

    @Override
    public void install(PluginContext context) {
        this.context = context;
        panel = new ShortestPathPanel();
        routePanel = new ShortestPathRoutePanel();
        menu = new ShortestPathMenu();
        options = loadOptions(context.config());
        enabledTeleportItems = loadEnabledTeleportItems(context.config());
        showCollisionMap = context.config().getBoolean(KEY_SHOW_COLLISION_MAP, false);
        walkLineColor = loadColor(context.config(), KEY_WALK_LINE_COLOR, DEFAULT_WALK_LINE_COLOR);
        transportLineColor = loadColor(context.config(), KEY_TRANSPORT_LINE_COLOR, DEFAULT_TRANSPORT_LINE_COLOR);
        startMarkerColor = loadColor(context.config(), KEY_START_MARKER_COLOR, DEFAULT_START_MARKER_COLOR);
        stepMarkerColor = loadColor(context.config(), KEY_STEP_MARKER_COLOR, DEFAULT_STEP_MARKER_COLOR);
        targetMarkerColor = loadColor(context.config(), KEY_TARGET_MARKER_COLOR, DEFAULT_TARGET_MARKER_COLOR);
        collisionColor = loadColor(context.config(), KEY_COLLISION_COLOR, DEFAULT_COLLISION_COLOR);
        panel.setOptions(options.includeTransports(), options.includeTeleports(), options.avoidWilderness(),
                options.includePoh(), options.avoidItemTeleports(), options.enabledTransportTypes(), showCollisionMap);
        panel.setColors(walkLineColor, transportLineColor, startMarkerColor, stepMarkerColor,
                targetMarkerColor, collisionColor);
        panel.setTeleportItems(enabledTeleportItems);
        panel.setOnOptionsChanged(this::handleOptionsChanged);
        panel.setOnSelectionModeChanged(mode -> context.setStatus("Selection: " + mode.name().toLowerCase()));
        panel.setOnCenterStart(this::setStartToCenter);
        panel.setOnSwap(this::swapEndpoints);
        panel.setOnRecalculate(this::recalculate);
        panel.setOnImportRoute(this::importRoute);
        panel.setOnExportRoute(this::exportRoute);
        panel.setOnClear(this::clearPath);
        panel.setOnProfileLookup(this::lookUpProfile);
        routePanel.setOnFocusTile(tile -> {
            if (tile != null && this.context != null) {
                this.context.mapPanel().focusTile(tile, null);
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
        loadStoredProfile(context.config());
        refreshPanel();

        context.mapPanel().addLayer(this);
        context.mapPanel().setActiveTool(this);
    }

    @Override
    public void uninstall() {
        cancelCurrentJob();
        cancelProfileJob();
        if (context != null) {
            context.mapPanel().removeLayer(this);
            context.mapPanel().clearActiveTool(this);
        }
    }

    @Override
    public JPopupMenu actionMenu() {
        return menu;
    }

    @Override
    public JComponent leftComponent() {
        return routePanel;
    }

    @Override
    public JComponent rightComponent() {
        return panel;
    }

    @Override
    public boolean mouseClicked(MapMouseEvent event) {
        if (event == null || event.tile() == null) {
            return false;
        }
        if (isPopupTrigger(event)) {
            return showRoutePointMenu(event);
        }
        if (!SwingUtilities.isLeftMouseButton(event.source())) {
            return false;
        }
        Tile tile = event.tile();
        if (startTile == null || event.isAdditiveSelection()
                || !event.isShiftDown() && panel.selectionMode() == ShortestPathPanel.SelectionMode.START) {
            setStart(tile);
            panel.setSelectionMode(ShortestPathPanel.SelectionMode.TARGET);
        } else if (event.isShiftDown()) {
            addStep(tile);
        } else {
            setTarget(tile);
        }
        return true;
    }

    @Override
    public void paint(MapRenderContext context, Graphics2D g, Rectangle visibleMap) {
        paintCollisionMap(context, g, visibleMap);
        paintPath(context, g, visibleMap);
        paintEndpoint(context, g, visibleMap, startTile, startMarkerColor, "S");
        for (int i = 0; i < stepTiles.size(); i++) {
            paintEndpoint(context, g, visibleMap, stepTiles.get(i), stepMarkerColor, Integer.toString(i + 1));
        }
        paintEndpoint(context, g, visibleMap, targetTile, targetMarkerColor, "E");
    }

    @Override
    public String tooltipText(MapRenderContext context, Tile tile, int regionId) {
        if (tile == null) {
            return null;
        }
        return routePointTooltip(tile);
    }

    private boolean isPopupTrigger(MapMouseEvent event) {
        return event.source().isPopupTrigger() || SwingUtilities.isRightMouseButton(event.source());
    }

    private boolean showRoutePointMenu(MapMouseEvent event) {
        ShortestPathRoutePanel.Entry entry = routePointAt(event.tile());
        if (entry == null) {
            return false;
        }
        JPopupMenu popup = new JPopupMenu();
        JMenuItem remove = new JMenuItem("Remove " + routePointLabel(entry.pointIndex()));
        remove.addActionListener(e -> removeRoutePointFromMenu(entry.pointIndex()));
        popup.add(remove);
        MouseEvent source = event.source();
        popup.show(source.getComponent(), source.getX(), source.getY());
        return true;
    }

    private String routePointTooltip(Tile tile) {
        ShortestPathRoutePanel.Entry entry = routePointAt(tile);
        return entry == null ? null : entry.tooltip();
    }

    private ShortestPathRoutePanel.Entry routePointAt(Tile tile) {
        if (tile == null) {
            return null;
        }
        ShortestPathRoutePanel.Entry nearest = null;
        int nearestChebyshev = Integer.MAX_VALUE;
        int nearestManhattan = Integer.MAX_VALUE;
        for (ShortestPathRoutePanel.Entry entry : routePointEntries) {
            Tile entryTile = entry.tile();
            if (sameTile(tile, entryTile)) {
                return entry;
            }
            if (entryTile == null || tile.z != entryTile.z) {
                continue;
            }
            int dx = Math.abs(tile.x - entryTile.x);
            int dy = Math.abs(tile.y - entryTile.y);
            int chebyshev = Math.max(dx, dy);
            int manhattan = dx + dy;
            if (chebyshev <= ROUTE_POINT_HIT_RADIUS_TILES
                    && (chebyshev < nearestChebyshev
                    || chebyshev == nearestChebyshev && manhattan < nearestManhattan)) {
                nearest = entry;
                nearestChebyshev = chebyshev;
                nearestManhattan = manhattan;
            }
        }
        return nearest;
    }

    @Override
    public void planeChanged(int plane) {
        repaint();
    }

    @Override
    public void tileFocused(Tile tile) {
        if (tile != null && startTile == null) {
            setStart(tile);
        }
    }

    private void handleOptionsChanged() {
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
        showCollisionMap = panel.showCollisionMap();
        walkLineColor = panel.walkLineColor();
        transportLineColor = panel.transportLineColor();
        startMarkerColor = panel.startMarkerColor();
        stepMarkerColor = panel.stepMarkerColor();
        targetMarkerColor = panel.targetMarkerColor();
        collisionColor = panel.collisionColor();
        saveOptions(context.config(), options);
        saveEnabledTeleportItems(context.config(), enabledTeleportItems);
        saveColors(context.config());
        context.config().setBoolean(KEY_SHOW_COLLISION_MAP, showCollisionMap);
        cancelCurrentJob();
        pathfinderConfig.refresh(options);
        pathfinderConfig.setEnabledTeleportItems(enabledTeleportItems);
        recalculate();
    }

    private void setStartToCenter() {
        Tile tile = context.mapPanel().getCenterTile();
        if (tile != null) {
            setStart(tile);
        }
    }

    private void setTargetToCenter() {
        Tile tile = context.mapPanel().getCenterTile();
        if (tile != null) {
            setTarget(tile);
        }
    }

    private void swapEndpoints() {
        List<Tile> points = routePoints();
        if (points.size() < 2) {
            return;
        }
        List<Tile> reversed = new ArrayList<>(points.size());
        for (int i = points.size() - 1; i >= 0; i--) {
            reversed.add(copy(points.get(i)));
        }
        startTile = reversed.get(0);
        stepTiles.clear();
        for (int i = 1; i < reversed.size() - 1; i++) {
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

    private void clearPath() {
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

    private void loadStoredProfile(PluginConfig config) {
        String storedUsername = cleanUsername(config.getString(KEY_WIKISYNC_USERNAME, ""));
        WikiSyncProfile storedProfile = config.getObject(KEY_WIKISYNC_PROFILE, WikiSyncProfile.class, null);
        if (storedUsername.isBlank()) {
            panel.setProfileStatus("none");
            return;
        }

        panel.setProfileName(storedUsername);
        if (storedProfile != null && storedProfile.hasData()) {
            pathfinderConfig.setProfile(storedProfile);
            panel.setProfileStatus("cached " + storedProfile.summary());
        }
        refreshProfile(storedUsername);
    }

    private void lookUpProfile(String username) {
        String cleanUsername = cleanUsername(username);
        if (cleanUsername.isBlank()) {
            panel.setProfileStatus("enter a username");
            return;
        }
        submitProfileLookup(cleanUsername, true, false);
    }

    private void refreshProfile(String username) {
        String cleanUsername = cleanUsername(username);
        if (!cleanUsername.isBlank()) {
            submitProfileLookup(cleanUsername, false, true);
        }
    }

    private void submitProfileLookup(String username, boolean askToStore, boolean saveOnSuccess) {
        if (pathfinderConfig == null) {
            return;
        }
        cancelProfileJob();
        long lookupId = ++profileLookupSerial;
        panel.setProfileLookupRunning(true);
        panel.setProfileStatus("looking up " + username);
        context.setStatus("Looking up WikiSync profile");
        profileJob = executor.submit(() -> {
            try {
                WikiSyncProfile profile = wikiSyncClient.lookup(username, pathfinderConfig.profileRequirements());
                SwingUtilities.invokeLater(() -> finishProfileLookup(lookupId, profile, askToStore, saveOnSuccess));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> failProfileLookup(lookupId, ex, askToStore, saveOnSuccess));
            }
        });
    }

    private void finishProfileLookup(long lookupId, WikiSyncProfile profile, boolean askToStore, boolean saveOnSuccess) {
        if (lookupId != profileLookupSerial) {
            return;
        }
        panel.setProfileLookupRunning(false);
        pathfinderConfig.setProfile(profile);
        recalculate();

        boolean store = saveOnSuccess;
        if (askToStore) {
            int answer = JOptionPane.showConfirmDialog(
                    context.owner(),
                    "Store this WikiSync profile and refresh it whenever the Shortest Path plugin loads?",
                    "Store WikiSync Profile",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            store = answer == JOptionPane.YES_OPTION;
        }
        if (store) {
            saveProfile(context.config(), profile);
        }

        panel.setProfileStatus(profile.summary() + (store ? " saved" : " session only"));
        context.setStatus("WikiSync profile loaded");
        repaint();
    }

    private void failProfileLookup(long lookupId, Exception ex, boolean showDialog, boolean automaticRefresh) {
        if (lookupId != profileLookupSerial) {
            return;
        }
        panel.setProfileLookupRunning(false);
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "WikiSync lookup failed."
                : ex.getMessage();
        WikiSyncProfile currentProfile = pathfinderConfig.getProfile();
        if (automaticRefresh && currentProfile != null && currentProfile.hasData()) {
            panel.setProfileStatus("cached; update failed");
        } else {
            panel.setProfileStatus("lookup failed");
        }
        context.setStatus(message);
        if (showDialog) {
            JOptionPane.showMessageDialog(context.owner(), message, "WikiSync Lookup Failed", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void setStart(Tile tile) {
        if (!isRoutePointAccessible(tile)) {
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

    private void addStep(Tile tile) {
        if (!isRoutePointAccessible(tile)) {
            warnClearedInaccessible("Step " + (stepTiles.size() + 1), tile);
            return;
        }
        stepTiles.add(copy(tile));
        clearRouteCalculationState(true);
        refreshPanel();
        recalculate();
        repaint();
    }

    private void setTarget(Tile tile) {
        if (!isRoutePointAccessible(tile)) {
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

    private void clearRouteCalculationState(boolean resetWarningSignature) {
        routeResults = List.of();
        routeFailure = null;
        if (resetWarningSignature) {
            lastWarningSignature = "";
        }
    }

    private boolean clearBlockedRoutePoints() {
        boolean changed = false;
        if (startTile != null && !isRoutePointAccessible(startTile)) {
            Tile cleared = startTile;
            startTile = null;
            warnClearedInaccessible("Start", cleared);
            changed = true;
        }
        for (int i = stepTiles.size() - 1; i >= 0; i--) {
            Tile step = stepTiles.get(i);
            if (!isRoutePointAccessible(step)) {
                stepTiles.remove(i);
                warnClearedInaccessible("Step " + (i + 1), step);
                changed = true;
            }
        }
        if (targetTile != null && !isRoutePointAccessible(targetTile)) {
            Tile cleared = targetTile;
            targetTile = null;
            warnClearedInaccessible("End", cleared);
            changed = true;
        }
        if (changed) {
            clearRouteCalculationState(false);
            refreshPanel();
            repaint();
        }
        return changed;
    }

    private boolean isRoutePointAccessible(Tile tile) {
        if (tile == null || tile.z < 0) {
            return false;
        }
        if (pathfinderConfig == null) {
            return true;
        }
        return !pathfinderConfig.getMap().isBlocked(tile.x, tile.y, tile.z);
    }

    private boolean removeRoutePoint(int pointIndex) {
        if (pointIndex == 0) {
            if (startTile == null) {
                return false;
            }
            startTile = null;
            return true;
        }
        if (pointIndex >= 1 && pointIndex <= stepTiles.size()) {
            stepTiles.remove(pointIndex - 1);
            return true;
        }
        if (targetTile != null && pointIndex == stepTiles.size() + 1) {
            targetTile = null;
            return true;
        }
        return false;
    }

    private void removeRoutePointFromMenu(int pointIndex) {
        cancelCurrentJob();
        if (pointIndex == 0) {
            clearPath();
            return;
        }
        if (targetTile != null && pointIndex == stepTiles.size() + 1) {
            removeEndRoutePoint();
            return;
        }
        if (pointIndex >= 1 && pointIndex <= stepTiles.size()) {
            stepTiles.remove(pointIndex - 1);
            finishManualRoutePointRemoval();
        }
    }

    private void removeEndRoutePoint() {
        if (stepTiles.isEmpty()) {
            clearPath();
            return;
        }
        targetTile = stepTiles.remove(stepTiles.size() - 1);
        finishManualRoutePointRemoval();
    }

    private void finishManualRoutePointRemoval() {
        clearRouteCalculationState(true);
        refreshPanel();
        repaint();
        if (routePoints().size() >= 2) {
            recalculate();
        } else {
            panel.setStatus("idle");
        }
    }

    private void warnClearedInaccessible(String label, Tile tile) {
        String message = label + " is not accessible and was cleared: " + tileText(tile);
        panel.setStatus(message);
        context.setStatus(message);
        String signature = "cleared:" + label + ":" + tileText(tile);
        if (!signature.equals(lastWarningSignature)) {
            lastWarningSignature = signature;
            JOptionPane.showMessageDialog(context.owner(), message, "Shortest Path Blocked", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void warnInaccessible(RouteFailure failure, String label, boolean removed) {
        String message = label + " is not accessible"
                + (removed ? " and was cleared: " : ": ")
                + tileText(failure.point());
        panel.setStatus(message);
        context.setStatus(message);
        String signature = "blocked:" + label + ":" + tileText(failure.point()) + ":" + removed;
        if (!signature.equals(lastWarningSignature)) {
            lastWarningSignature = signature;
            JOptionPane.showMessageDialog(context.owner(), message, "Shortest Path Blocked", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void continueAfterRoutePointRemoval(boolean removed) {
        clearRouteCalculationState(false);
        refreshPanel();
        repaint();
        if (removed && routePoints().size() >= 2) {
            recalculate();
        }
    }

    private void recalculate() {
        cancelCurrentJob();
        clearBlockedRoutePoints();
        List<Tile> points = routePoints();
        if (context == null || pathfinderConfig == null || points.size() < 2) {
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

    private RouteCalculation calculateRoute(long jobId, List<Tile> points) {
        List<PathfinderResult> completed = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            if (jobId != calculationSerial) {
                return null;
            }
            Pathfinder next = new Pathfinder(pathfinderConfig, pack(points.get(i - 1)), Set.of(pack(points.get(i))), null);
            currentPathfinder = next;
            next.run();
            if (jobId != calculationSerial) {
                return null;
            }
            PathfinderResult leg = next.getResult();
            if (leg == null) {
                return new RouteCalculation(completed, new RouteFailure(i, copy(points.get(i)), null));
            }
            if (!leg.isReached()) {
                return new RouteCalculation(completed, new RouteFailure(i, copy(points.get(i)), leg));
            }
            completed.add(leg);
        }
        return new RouteCalculation(completed, null);
    }

    private void finishCalculation(long jobId, RouteCalculation calculation) {
        if (jobId != calculationSerial || calculation == null) {
            return;
        }
        currentPathfinder = null;
        if (calculation.failure() != null) {
            RouteFailure failure = calculation.failure();
            String label = routePointLabel(failure.pointIndex());
            boolean removed = removeRoutePoint(failure.pointIndex());
            routeResults = List.copyOf(calculation.results());
            routeFailure = removed ? null : failure;
            continueAfterRoutePointRemoval(removed);
            warnInaccessible(failure, label, removed);
        } else if (calculation.results().isEmpty()) {
            routeResults = List.copyOf(calculation.results());
            routeFailure = null;
            refreshPanel();
            repaint();
            panel.setStatus("cancelled");
        } else {
            routeResults = List.copyOf(calculation.results());
            routeFailure = null;
            refreshPanel();
            repaint();
            context.setStatus("Shortest path ready");
        }
    }

    private void cancelCurrentJob() {
        calculationSerial++;
        Pathfinder current = currentPathfinder;
        if (current != null && !current.isDone()) {
            current.cancel();
        }
        if (currentJob != null && !currentJob.isDone()) {
            currentJob.cancel(false);
        }
    }

    private void cancelProfileJob() {
        profileLookupSerial++;
        if (profileJob != null && !profileJob.isDone()) {
            profileJob.cancel(true);
        }
        if (panel != null) {
            panel.setProfileLookupRunning(false);
        }
    }

    private void refreshPanel() {
        if (panel == null) {
            return;
        }
        panel.setTiles(startTile, List.copyOf(stepTiles), targetTile);
        List<PathfinderResult> currentResults = routeResults;
        RouteFailure failure = routeFailure;
        List<Tile> points = routePoints();
        routePointEntries = routePointEntries(points, currentResults, failure);
        if (routePanel != null) {
            routePanel.setEntries(routePointEntries);
        }
        if (currentResults.isEmpty() && failure == null) {
            if (points.size() < 2) {
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

    private List<RouteSegment> routeSegments(List<PathStep> steps) {
        List<RouteSegment> segments = new ArrayList<>();
        for (int i = 1; i < steps.size(); i++) {
            PathStep previous = steps.get(i - 1);
            PathStep current = steps.get(i);
            int source = previous.getPackedPosition();
            int destination = current.getPackedPosition();
            Transport transport = pathfinderConfig.findTransport(source, destination, previous.isBankVisited());
            int distance = WorldPointUtil.distanceBetween(source, destination);
            if (isAdjacent(source, destination)) {
                segments.add(new RouteSegment(source, destination, Math.max(1, distance), null));
            } else {
                segments.add(new RouteSegment(source, destination, Math.max(1, distance), transport));
            }
        }
        return segments;
    }

    private List<ShortestPathRoutePanel.Entry> routePointEntries(List<Tile> points, List<PathfinderResult> results,
                                                                 RouteFailure failure) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        List<ShortestPathRoutePanel.Entry> entries = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
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
                                    boolean includeCoordinates) {
        if (failure != null && failure.pointIndex() == pointIndex) {
            return "Blocked";
        }
        if (pointIndex == 0 || results == null || pointIndex - 1 >= results.size()) {
            return pointIndex == 0 ? "Route start" : "Pending";
        }
        List<RouteSegment> legSegments = summarize(routeSegments(results.get(pointIndex - 1).getPathSteps()));
        if (legSegments.isEmpty()) {
            return "Reached";
        }
        StringJoiner joiner = new StringJoiner("; ");
        for (RouteSegment segment : legSegments) {
            joiner.add(includeCoordinates ? segment.tooltipLabel() : segment.label());
        }
        return joiner.toString();
    }

    private static String routePointTooltip(String pointLabel, String detail, Tile tile) {
        String text = pointLabel + " " + tileText(tile);
        if (detail == null || detail.isBlank()) {
            return text;
        }
        return text + " - " + detail;
    }

    private void paintPath(MapRenderContext context, Graphics2D g, Rectangle visibleMap) {
        List<PathStep> steps = currentPath();
        if (steps.size() < 2) {
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

        for (int i = 1; i < steps.size(); i++) {
            int previous = steps.get(i - 1).getPackedPosition();
            int current = steps.get(i).getPackedPosition();
            if (WorldPointUtil.unpackWorldPlane(previous) != context.plane()
                    || WorldPointUtil.unpackWorldPlane(current) != context.plane()) {
                continue;
            }
            Rectangle a = context.tileToRect(tile(previous));
            Rectangle b = context.tileToRect(tile(current));
            Line2D line = new Line2D.Double(centerX(a), centerY(a), centerX(b), centerY(b));
            if (!line.intersects(visibleMap)) {
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

    private void paintCollisionMap(MapRenderContext context, Graphics2D g, Rectangle visibleMap) {
        if (!showCollisionMap || pathfinderConfig == null || context.effectiveZoom() < 0.75) {
            return;
        }

        CollisionMap map = pathfinderConfig.getMap();
        Stroke oldStroke = g.getStroke();
        Color oldColor = g.getColor();
        float screenPx = Math.max(0.75f, (float) (1.0 / Math.max(context.effectiveZoom(), 0.0001)));
        g.setStroke(new BasicStroke(screenPx));
        g.setColor(collisionColor);
        int plane = context.plane();
        for (int regionId : context.visibleRegionIds(visibleMap)) {
            int regionX = regionId >> 8;
            int regionY = regionId & 0xFF;
            int startX = regionX * Paths.REGION_TILE_SIZE;
            int startY = regionY * Paths.REGION_TILE_SIZE;
            for (int x = startX; x < startX + Paths.REGION_TILE_SIZE; x++) {
                for (int y = startY; y < startY + Paths.REGION_TILE_SIZE; y++) {
                    Tile tile = new Tile(x, y, plane);
                    Rectangle rect = context.tileToRect(tile);
                    if (!rect.intersects(visibleMap)) {
                        continue;
                    }
                    int left = rect.x;
                    int right = rect.x + rect.width;
                    int top = rect.y;
                    int bottom = rect.y + rect.height;
                    if (!map.n(x, y, plane)) {
                        g.drawLine(left, top, right, top);
                    }
                    if (!map.s(x, y, plane)) {
                        g.drawLine(left, bottom, right, bottom);
                    }
                    if (!map.w(x, y, plane)) {
                        g.drawLine(left, top, left, bottom);
                    }
                    if (!map.e(x, y, plane)) {
                        g.drawLine(right, top, right, bottom);
                    }
                }
            }
        }
        g.setColor(oldColor);
        g.setStroke(oldStroke);
    }

    private void paintEndpoint(MapRenderContext context, Graphics2D g, Rectangle visibleMap, Tile tile, Color color, String label) {
        if (tile == null || tile.z != context.plane()) {
            return;
        }
        Rectangle rect = context.tileToRect(tile);
        if (!rect.intersects(visibleMap)) {
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
        if (context.effectiveZoom() > 0.18) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            float fontSize = Math.max(8f, (float) (11.0 / zoom));
            g.setFont(g.getFont().deriveFont(Font.BOLD, fontSize));
            drawOutlinedString(g, label, x + size, y, color, zoom);
        }
        g.setFont(oldFont);
        g.setStroke(oldStroke);
        if (oldTextAntialias == null) {
            g.getRenderingHints().remove(RenderingHints.KEY_TEXT_ANTIALIASING);
        } else {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldTextAntialias);
        }
    }

    private List<PathStep> currentPath() {
        return combinedPathSteps(routeResults);
    }

    private void repaint() {
        if (context != null) {
            context.mapPanel().repaintVisible();
        }
    }

    private void importRoute() {
        JFileChooser chooser = new JFileChooser(context.configManager().directory().toFile());
        chooser.setDialogTitle("Import Shortest Path Route");
        int answer = chooser.showOpenDialog(context.owner());
        if (answer != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            RouteData route = readRoute(chooser.getSelectedFile().toPath());
            startTile = copy(route.start());
            stepTiles.clear();
            for (Tile step : route.steps()) {
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
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(context.owner(), ex.getMessage(), "Import Route Failed", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void exportRoute() {
        List<Tile> points = routePoints();
        if (points.size() < 2) {
            JOptionPane.showMessageDialog(context.owner(), "Set a start and at least one step or end tile first.",
                    "Export Route", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(context.configManager().directory().toFile());
        chooser.setDialogTitle("Export Shortest Path Route");
        chooser.setSelectedFile(new java.io.File("shortest-path-route.json"));
        int answer = chooser.showSaveDialog(context.owner());
        if (answer != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = withJsonExtension(chooser.getSelectedFile().toPath());
        try {
            writeRoute(file, new RouteData(copy(startTile), List.copyOf(stepTiles), copy(targetTile)));
            context.setStatus("Shortest path route exported");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(context.owner(), ex.getMessage(), "Export Route Failed", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static RouteData readRoute(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IOException("Route JSON must contain an object.");
            }
            JsonObject root = parsed.getAsJsonObject();
            Tile start = readTile(root.get("start"), "start");
            Tile target = root.has("target") && !root.get("target").isJsonNull()
                    ? readTile(root.get("target"), "target")
                    : null;
            List<Tile> steps = new ArrayList<>();
            JsonElement stepElement = root.get("steps");
            if (stepElement != null && !stepElement.isJsonNull()) {
                if (!stepElement.isJsonArray()) {
                    throw new IOException("Route steps must be an array.");
                }
                JsonArray array = stepElement.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    steps.add(readTile(array.get(i), "step " + (i + 1)));
                }
            }
            if (target == null && steps.isEmpty()) {
                throw new IOException("Route JSON must contain at least one step or target.");
            }
            return new RouteData(start, steps, target);
        }
    }

    private static void writeRoute(Path path, RouteData route) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("start", tileJson(route.start()));
        JsonArray steps = new JsonArray();
        for (Tile step : route.steps()) {
            steps.add(tileJson(step));
        }
        root.add("steps", steps);
        if (route.target() != null) {
            root.add("target", tileJson(route.target()));
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            ROUTE_GSON.toJson(root, writer);
        }
    }

    private static Path withJsonExtension(Path path) {
        String name = path.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return path;
        }
        return path.resolveSibling(name + ".json");
    }

    private static PathOptions loadOptions(PluginConfig config) {
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

    private static void saveOptions(PluginConfig config, PathOptions options) {
        config.setBoolean(KEY_INCLUDE_TRANSPORTS, options.includeTransports());
        config.setBoolean(KEY_INCLUDE_TELEPORTS, options.includeTeleports());
        config.setBoolean(KEY_AVOID_WILDERNESS, options.avoidWilderness());
        config.setBoolean(KEY_INCLUDE_POH, options.includePoh());
        config.setBoolean(KEY_AVOID_ITEM_TELEPORTS, options.avoidItemTeleports());
        saveEnabledTransportTypes(config, options.enabledTransportTypes());
    }

    private static void saveProfile(PluginConfig config, WikiSyncProfile profile) {
        config.setString(KEY_WIKISYNC_USERNAME, profile.username());
        config.setObject(KEY_WIKISYNC_PROFILE, profile);
    }

    private static Set<TeleportItem> loadEnabledTeleportItems(PluginConfig config) {
        EnumSet<TeleportItem> enabled = EnumSet.allOf(TeleportItem.class);
        String raw = config.getString(KEY_DISABLED_TELEPORT_ITEMS, "");
        if (raw == null || raw.isBlank()) {
            return enabled;
        }
        for (String token : raw.split(",")) {
            String name = token.trim();
            if (name.isEmpty()) {
                continue;
            }
            try {
                enabled.remove(TeleportItem.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // Ignore stale names so removed or renamed items do not break plugin loading.
            }
        }
        return enabled;
    }

    private static void saveEnabledTeleportItems(PluginConfig config, Set<TeleportItem> enabledItems) {
        Set<TeleportItem> enabled = copyTeleportItems(enabledItems);
        StringJoiner disabled = new StringJoiner(",");
        for (TeleportItem item : TeleportItem.values()) {
            if (!enabled.contains(item)) {
                disabled.add(item.name());
            }
        }
        String value = disabled.toString();
        if (value.isEmpty()) {
            config.remove(KEY_DISABLED_TELEPORT_ITEMS);
        } else {
            config.setString(KEY_DISABLED_TELEPORT_ITEMS, value);
        }
    }

    private static Set<TransportType> loadEnabledTransportTypes(PluginConfig config) {
        EnumSet<TransportType> enabled = EnumSet.copyOf(PathOptions.defaultEnabledTransportTypes());
        String raw = config.getString(KEY_DISABLED_TRANSPORT_TYPES, "");
        if (raw == null || raw.isBlank()) {
            return enabled;
        }
        for (String token : raw.split(",")) {
            String name = token.trim();
            if (name.isEmpty()) {
                continue;
            }
            try {
                enabled.remove(TransportType.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // Ignore stale transport type names from older configs.
            }
        }
        return enabled;
    }

    private static void saveEnabledTransportTypes(PluginConfig config, Set<TransportType> enabledTypes) {
        Set<TransportType> enabled = PathOptions.copyTransportTypes(enabledTypes);
        StringJoiner disabled = new StringJoiner(",");
        for (TransportType type : TransportType.values()) {
            if (type.isLeagueOnly()) {
                continue;
            }
            if (!enabled.contains(type)) {
                disabled.add(type.name());
            }
        }
        String value = disabled.toString();
        if (value.isEmpty()) {
            config.remove(KEY_DISABLED_TRANSPORT_TYPES);
        } else {
            config.setString(KEY_DISABLED_TRANSPORT_TYPES, value);
        }
    }

    private void saveColors(PluginConfig config) {
        config.setString(KEY_WALK_LINE_COLOR, colorString(walkLineColor));
        config.setString(KEY_TRANSPORT_LINE_COLOR, colorString(transportLineColor));
        config.setString(KEY_START_MARKER_COLOR, colorString(startMarkerColor));
        config.setString(KEY_STEP_MARKER_COLOR, colorString(stepMarkerColor));
        config.setString(KEY_TARGET_MARKER_COLOR, colorString(targetMarkerColor));
        config.setString(KEY_COLLISION_COLOR, colorString(collisionColor));
    }

    private static Color loadColor(PluginConfig config, String key, Color fallback) {
        String raw = config.getString(key, "");
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            String clean = raw.trim();
            if (clean.startsWith("#")) {
                clean = clean.substring(1);
            }
            if (clean.length() == 6) {
                return new Color((int) Long.parseLong(clean, 16) | 0xFF000000, true);
            }
            if (clean.length() == 8) {
                return new Color((int) Long.parseLong(clean, 16), true);
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    private static String colorString(Color color) {
        int argb = (color.getAlpha() << 24) | (color.getRGB() & 0x00FFFFFF);
        return String.format("#%08X", argb);
    }

    private static Tile readTile(JsonElement element, String label) throws IOException {
        if (element == null || !element.isJsonObject()) {
            throw new IOException("Route " + label + " must be an object.");
        }
        JsonObject object = element.getAsJsonObject();
        return new Tile(readInt(object, "x", label), readInt(object, "y", label), readInt(object, "z", label));
    }

    private static int readInt(JsonObject object, String key, String label) throws IOException {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IOException("Route " + label + " is missing " + key + ".");
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException ex) {
            throw new IOException("Route " + label + " has invalid " + key + ".", ex);
        }
    }

    private static JsonObject tileJson(Tile tile) {
        JsonObject object = new JsonObject();
        object.addProperty("x", tile.x);
        object.addProperty("y", tile.y);
        object.addProperty("z", tile.z);
        return object;
    }

    private static String cleanUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private static boolean isAdjacent(int source, int destination) {
        return WorldPointUtil.unpackWorldPlane(source) == WorldPointUtil.unpackWorldPlane(destination)
                && Math.abs(WorldPointUtil.unpackWorldX(source) - WorldPointUtil.unpackWorldX(destination)) <= 1
                && Math.abs(WorldPointUtil.unpackWorldY(source) - WorldPointUtil.unpackWorldY(destination)) <= 1;
    }

    private List<Tile> routePoints() {
        if (startTile == null) {
            return List.of();
        }
        List<Tile> points = new ArrayList<>(stepTiles.size() + 2);
        points.add(copy(startTile));
        for (Tile step : stepTiles) {
            points.add(copy(step));
        }
        if (targetTile != null) {
            points.add(copy(targetTile));
        }
        return points;
    }

    private String routePointLabel(int pointIndex) {
        return routePointLabel(pointIndex, routePoints().size(), targetTile != null);
    }

    private static String routePointLabel(int pointIndex, int pointCount, boolean hasTarget) {
        if (pointIndex == 0) {
            return "Start";
        }
        if (hasTarget && pointIndex == pointCount - 1) {
            return "End";
        }
        return "Step " + pointIndex;
    }

    private static List<PathStep> combinedPathSteps(List<PathfinderResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<PathStep> out = new ArrayList<>();
        for (PathfinderResult result : results) {
            List<PathStep> steps = result.getPathSteps();
            if (steps.isEmpty()) {
                continue;
            }
            int start = out.isEmpty() ? 0 : 1;
            for (int i = start; i < steps.size(); i++) {
                out.add(steps.get(i));
            }
        }
        return out;
    }

    private static int totalNodes(List<PathfinderResult> results, RouteFailure failure) {
        int total = 0;
        if (results != null) {
            for (PathfinderResult result : results) {
                total += result.getNodesChecked() + result.getTransportsChecked();
            }
        }
        if (failure != null && failure.result() != null) {
            total += failure.result().getNodesChecked() + failure.result().getTransportsChecked();
        }
        return total;
    }

    private static long totalElapsedNanos(List<PathfinderResult> results, RouteFailure failure) {
        long total = 0L;
        if (results != null) {
            for (PathfinderResult result : results) {
                total += result.getElapsedNanos();
            }
        }
        if (failure != null && failure.result() != null) {
            total += failure.result().getElapsedNanos();
        }
        return total;
    }

    private static List<RouteSegment> summarize(List<RouteSegment> segments) {
        List<RouteSegment> summarized = new ArrayList<>();
        int walkTiles = 0;
        for (RouteSegment segment : segments) {
            if (segment.transport() == null) {
                walkTiles += segment.distance();
            } else {
                if (walkTiles > 0) {
                    summarized.add(RouteSegment.walk(walkTiles));
                    walkTiles = 0;
                }
                summarized.add(segment);
            }
        }
        if (walkTiles > 0) {
            summarized.add(RouteSegment.walk(walkTiles));
        }
        return summarized;
    }

    private static Set<TeleportItem> copyTeleportItems(Set<TeleportItem> items) {
        if (items == null) {
            return EnumSet.allOf(TeleportItem.class);
        }
        if (items.isEmpty()) {
            return EnumSet.noneOf(TeleportItem.class);
        }
        return EnumSet.copyOf(items);
    }

    private static void drawOutlinedString(Graphics2D g, String text, int x, int y, Color fill, double zoom) {
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

    private static boolean sameTile(Tile a, Tile b) {
        return a != null && b != null && a.x == b.x && a.y == b.y && a.z == b.z;
    }

    private static int pack(Tile tile) {
        return WorldPointUtil.packWorldPoint(tile.x, tile.y, tile.z);
    }

    private static Tile tile(int packed) {
        return new Tile(
                WorldPointUtil.unpackWorldX(packed),
                WorldPointUtil.unpackWorldY(packed),
                WorldPointUtil.unpackWorldPlane(packed)
        );
    }

    private static Tile copy(Tile tile) {
        return tile == null ? null : new Tile(tile.x, tile.y, tile.z);
    }

    private static String tileText(Tile tile) {
        return tile == null ? "none" : tile.x + "," + tile.y + "," + tile.z;
    }

    private static int centerX(Rectangle rect) {
        return rect.x + rect.width / 2;
    }

    private static int centerY(Rectangle rect) {
        return rect.y + rect.height / 2;
    }

    private static String packedText(int packed) {
        if (packed == WorldPointUtil.UNDEFINED) {
            return "unknown";
        }
        return WorldPointUtil.unpackWorldX(packed) + ","
                + WorldPointUtil.unpackWorldY(packed) + ","
                + WorldPointUtil.unpackWorldPlane(packed);
    }

    private record RouteSegment(int source, int destination, int distance, Transport transport) {
        static RouteSegment walk(int distance) {
            return new RouteSegment(WorldPointUtil.UNDEFINED, WorldPointUtil.UNDEFINED, distance, null);
        }

        String label() {
            if (transport == null) {
                return "Walk " + distance + " tile" + (distance == 1 ? "" : "s");
            }
            return "Transport: " + transport.label();
        }

        String tooltipLabel() {
            if (transport == null) {
                return label();
            }
            return label() + " -> " + packedText(destination);
        }
    }

    private record RouteCalculation(List<PathfinderResult> results, RouteFailure failure) {
    }

    private record RouteFailure(int pointIndex, Tile point, PathfinderResult result) {
    }

    private record RouteData(Tile start, List<Tile> steps, Tile target) {
    }
}
