package com.xeon.view;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import com.xeon.io.Paths;
import com.xeon.model.MapArea;
import com.xeon.model.Tile;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Transparency;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.event.ChangeListener;

public class MapPanel extends JComponent implements MapView {
    private static final int REGION_TILES = 64;
    private static final int HI_PX_PER_TILE = 4;
    private static final double MIN_ZOOM = 1.00;
    private static final double MAX_ZOOM = 16.0;
    private static final int CANVAS_PAD = 100;
    private static final int MAX_INFLIGHT = 512;
    private static final int VISIBLE_AREA_NOTIFY_DELAY_MS = 80;
    private static final int DECODE_THREADS =
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));

    private enum LOD {
        FULL(1), HALF(2), QUARTER(4);

        final int subsample;

        LOD(int subsample) {
            this.subsample = subsample;
        }

        static LOD forZoom(double zoom) {
            if (zoom >= 3.0) return FULL;
            if (zoom >= 1.75) return HALF;
            return QUARTER;
        }

        List<LOD> coarserChain() {
            if (this == FULL) return Arrays.asList(HALF, QUARTER);
            if (this == HALF) return Collections.singletonList(QUARTER);
            return Collections.emptyList();
        }
    }

    private enum AtlasLayerKind {
        BASE("base"),
        ICONS("icons"),
        LABELS("labels");

        final String metadataName;

        AtlasLayerKind(String metadataName) {
            this.metadataName = metadataName;
        }

        static AtlasLayerKind fromMetadataName(String value) {
            for (AtlasLayerKind kind : values()) {
                if (kind.metadataName.equals(value)) {
                    return kind;
                }
            }
            return null;
        }
    }

    private static final long MIN_CACHE_BUDGET_BYTES = 512L * 1024L * 1024L;
    private final AtlasStoreReader store;
    private final TileCache cache;
    private final Set<String> emptyTiles = ConcurrentHashMap.newKeySet();
    private final Set<String> inflight = ConcurrentHashMap.newKeySet();
    private final List<MapLayer> layers = new CopyOnWriteArrayList<>();
    private final List<IntConsumer> planeChangeListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> visibleAreaChangeListeners = new CopyOnWriteArrayList<>();
    private final MapRenderContext renderContext = new MapRenderContext(this);

    private final int totalW;
    private final int totalH;
    private final int maxPlanes;
    private final ThreadPoolExecutor loader = new ThreadPoolExecutor(
            DECODE_THREADS,
            DECODE_THREADS,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(MAX_INFLIGHT),
            r -> {
                Thread t = new Thread(r, "Atlas-TileLoader");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    private final AtomicLong requestEpoch = new AtomicLong(1);
    private LOD lastLod = null;
    private MapTool activeTool = MapTool.NONE;
    private double zoom = 1.0;
    private boolean showGrid = false;
    private boolean showRegionCoordinates = false;
    private boolean showRegionIds = false;
    private boolean showMapIcons = true;
    private boolean showMapLabels = true;
    private boolean mapLocked = false;
    private boolean regionSelectionActive = false;
    private Color mapBackgroundColor = Color.BLACK;
    private int currentPlane = 0;
    private Tile hoverTile;
    private int hoverRegionId = -1;
    private Point dragStartView;
    private Point dragStartMouse;
    private int heldDX = 0;
    private int heldDY = 0;
    private final RepeatMover repeatMover = new RepeatMover();
    private final KeyEventDispatcher keyEventDispatcher = this::handleGlobalKeyEvent;
    private final ChangeListener viewportChangeListener = e -> scheduleVisibleAreaChanged();
    private final Timer visibleAreaNotifyTimer = new Timer(VISIBLE_AREA_NOTIFY_DELAY_MS, e -> notifyVisibleAreaChanged());
    private JViewport observedViewport;

    public MapPanel(Path atlasPath, long cacheBudgetBytes) {
        if (atlasPath == null) {
            throw new IllegalArgumentException("atlasPath must not be null");
        }
        cache = new TileCache(cacheBudgetBytes);
        AtlasStoreReader tmp;
        try {
            tmp = AtlasStoreReader.openFile(atlasPath);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to open atlas: " + atlasPath
                    + " - " + ex.getMessage(), ex);
        }
        store = tmp;
        maxPlanes = Math.max(1, Math.min(4, store.maxPlanes()));

        int fullW = store.header().srcWidth;
        int fullH = store.header().srcHeight;
        totalW = (int) Math.ceil(fullW / (double) HI_PX_PER_TILE);
        totalH = (int) Math.ceil(fullH / (double) HI_PX_PER_TILE);

        setOpaque(true);
        setBackground(mapBackgroundColor);
        setDoubleBuffered(true);
        setFocusable(true);
        setPreferredSize(getPreferredSize());
        ToolTipManager.sharedInstance().registerComponent(this);

        installMouseHandlers();
        installArrowKeyBindings();
        visibleAreaNotifyTimer.setRepeats(false);
    }

    public static void validateAtlas(Path atlasPath) throws Exception {
        if (atlasPath == null) {
            throw new IllegalArgumentException("atlasPath must not be null");
        }
        try (AtlasStoreReader ignored = AtlasStoreReader.openFile(atlasPath)) {
            // Opening the atlas validates the header, metadata, layer index, and tile index.
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyEventDispatcher);
        SwingUtilities.invokeLater(this::syncViewportListener);
    }

    @Override
    public void removeNotify() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyEventDispatcher);
        detachViewportListener();
        visibleAreaNotifyTimer.stop();
        super.removeNotify();
    }

    public void addLayer(MapLayer layer) {
        if (layer != null && !layers.contains(layer)) {
            layers.add(layer);
            repaintVisible();
        }
    }

    public void removeLayer(MapLayer layer) {
        if (layers.remove(layer)) {
            repaintVisible();
        }
    }

    public void setActiveTool(MapTool activeTool) {
        this.activeTool = activeTool == null ? MapTool.NONE : activeTool;
        if (!this.activeTool.supportsRegionSelection()) {
            updateRegionSelectionActive(false);
        }
    }

    public void clearActiveTool(MapTool tool) {
        if (activeTool == tool) {
            activeTool = MapTool.NONE;
            updateRegionSelectionActive(false);
        }
    }

    public void addPlaneChangeListener(IntConsumer listener) {
        if (listener != null) {
            planeChangeListeners.add(listener);
        }
    }

    public void removePlaneChangeListener(IntConsumer listener) {
        planeChangeListeners.remove(listener);
    }

    public void addVisibleAreaChangeListener(Runnable listener) {
        if (listener != null) {
            visibleAreaChangeListeners.add(listener);
        }
    }

    public void removeVisibleAreaChangeListener(Runnable listener) {
        visibleAreaChangeListeners.remove(listener);
    }

    public void setShowGrid(boolean value) {
        showGrid = value;
        repaintVisible();
    }

    public void setShowRegionCoordinates(boolean value) {
        showRegionCoordinates = value;
        repaintVisible();
    }

    public void setShowRegionIds(boolean value) {
        showRegionIds = value;
        repaintVisible();
    }

    public void setShowMapIcons(boolean value) {
        showMapIcons = value;
        repaintVisible();
    }

    public void setShowMapLabels(boolean value) {
        showMapLabels = value;
        repaintVisible();
    }

    public void setMapLocked(boolean value) {
        mapLocked = value;
        if (mapLocked) {
            dragStartView = null;
            dragStartMouse = null;
            setCursor(Cursor.getDefaultCursor());
        }
    }

    public boolean isMapLocked() {
        return mapLocked;
    }

    public Color getMapBackgroundColor() {
        return mapBackgroundColor;
    }

    public void setMapBackgroundColor(Color color) {
        if (color == null || color.equals(mapBackgroundColor)) {
            return;
        }
        mapBackgroundColor = color;
        setBackground(color);
        repaint();
    }

    public void setPlane(int z) {
        int next = Math.max(0, Math.min(maxPlanes - 1, z));
        if (next == currentPlane) {
            return;
        }
        currentPlane = next;
        requestEpoch.incrementAndGet();
        inflight.clear();
        repaintVisible();
        for (IntConsumer listener : planeChangeListeners) {
            listener.accept(currentPlane);
        }
        activeTool.visibleAreaChanged();
    }

    public int getPlane() {
        return currentPlane;
    }

    public int getPlaneCount() {
        return maxPlanes;
    }

    public List<MapArea> searchMapAreas(String query, int limit) {
        return store.searchMapAreas(query, limit);
    }

    @Override
    public List<MapArea> mapAreasAt(Tile tile) {
        return store.mapAreasAt(tile);
    }

    @Override
    public MapArea nearestMapArea(Tile tile, int maxDistanceTiles) {
        return store.nearestMapArea(tile, maxDistanceTiles);
    }

    public double getZoom() {
        return zoom;
    }

    public double getEffectiveZoom() {
        return effZoom();
    }

    public void setMemoryBudgetBytes(long budgetBytes) {
        cache.setBudgetBytes(budgetBytes);
        repaintVisible();
    }

    public long getMemoryBudgetBytes() {
        return cache.budgetBytes();
    }

    public Tile getHoverTile() {
        return hoverTile == null ? null : new Tile(hoverTile.x, hoverTile.y, hoverTile.z);
    }

    public int getHoverRegionId() {
        return hoverRegionId;
    }

    public boolean isRegionSelectionActive() {
        return regionSelectionActive;
    }

    public int getTotalWidthTiles() {
        return totalW;
    }

    public int getTotalHeightTiles() {
        return totalH;
    }

    public void focusTile(Tile tile, Double targetZoom) {
        if (tile == null || mapLocked) {
            return;
        }
        setPlane(tile.z);
        if (targetZoom != null) {
            setZoomCentered(targetZoom);
        }
        Rectangle device = scaleRect(tileToRect(tile), effZoom());
        device.translate(contentOriginX(), contentOriginY());
        centerViewOn(device);
        repaintTile(tile);
    }

    public void focusRegion(int regionId, int plane, Double targetZoom) {
        if (regionId < 0 || mapLocked) {
            return;
        }
        int rx = regionId >> 8;
        int ry = regionId & 0xFF;
        Tile center = new Tile(
                rx * REGION_TILES + REGION_TILES / 2,
                ry * REGION_TILES + REGION_TILES / 2,
                Math.max(0, Math.min(maxPlanes - 1, plane))
        );
        focusTile(center, targetZoom);
    }

    public Tile getCenterTile() {
        JViewport viewport = getViewport();
        if (viewport == null) {
            return null;
        }
        Rectangle view = viewport.getViewRect();
        return pointToTile(new Point(view.x + view.width / 2, view.y + view.height / 2));
    }

    public int getCenterRegionId() {
        return regionIdFor(getCenterTile());
    }

    public Tile pointToTile(Point point) {
        if (point == null) {
            return null;
        }
        return toTile(point.x, point.y);
    }

    public int regionIdFor(Tile tile) {
        if (tile == null) {
            return -1;
        }
        return (Math.floorDiv(tile.x, REGION_TILES) << 8) | Math.floorDiv(tile.y, REGION_TILES);
    }

    public Rectangle tileToRect(Tile tile) {
        int rx = Math.floorDiv(tile.x, REGION_TILES);
        int ry = Math.floorDiv(tile.y, REGION_TILES);
        int col = rx - Paths.MIN_RX;
        int rowTop = Paths.MAX_RY - ry;
        int oxTiles = Math.floorMod(tile.x, REGION_TILES);
        int oyTilesFromBottom = Math.floorMod(tile.y, REGION_TILES);
        int oyTilesTop = REGION_TILES - 1 - oyTilesFromBottom;

        double px = (col * regionW()) + oxTiles * (regionW() / REGION_TILES);
        double py = (rowTop * regionH()) + oyTilesTop * (regionH() / REGION_TILES);
        double pw = regionW() / REGION_TILES;
        double ph = regionH() / REGION_TILES;
        return new Rectangle((int) Math.round(px), (int) Math.round(py),
                Math.max(1, (int) Math.ceil(pw)),
                Math.max(1, (int) Math.ceil(ph)));
    }

    public Rectangle regionToRect(int regionId) {
        int rx = regionId >> 8;
        int ry = regionId & 0xFF;
        int col = rx - Paths.MIN_RX;
        int rowTop = Paths.MAX_RY - ry;
        return new Rectangle(
                (int) Math.round(col * regionW()),
                (int) Math.round(rowTop * regionH()),
                Math.max(1, (int) Math.ceil(regionW())),
                Math.max(1, (int) Math.ceil(regionH()))
        );
    }

    public List<Integer> visibleRegionIds(Rectangle visibleMap) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        if (visibleMap == null || visibleMap.isEmpty()) {
            return List.of();
        }
        int colStart = Math.max(0, (int) Math.floor(visibleMap.x / regionW()));
        int colEnd = Math.min(Paths.MAX_RX - Paths.MIN_RX,
                (int) Math.floor((visibleMap.x + visibleMap.width) / regionW()));
        int rowStart = Math.max(0, (int) Math.floor(visibleMap.y / regionH()));
        int rowEnd = Math.min(Paths.MAX_RY - Paths.MIN_RY,
                (int) Math.floor((visibleMap.y + visibleMap.height) / regionH()));

        for (int rowTop = rowStart; rowTop <= rowEnd; rowTop++) {
            int ry = Paths.MAX_RY - rowTop;
            for (int col = colStart; col <= colEnd; col++) {
                int rx = Paths.MIN_RX + col;
                ids.add((rx << 8) | ry);
            }
        }
        return new ArrayList<>(ids);
    }

    public List<Integer> currentVisibleRegionIds() {
        return visibleRegionIds(visibleMapPixelRect());
    }

    public void repaintVisible() {
        JViewport viewport = getViewport();
        if (viewport != null) {
            Rectangle view = viewport.getViewRect();
            repaint(view.x, view.y, view.width, view.height);
        } else {
            repaint();
        }
    }

    public void repaintTile(Tile tile) {
        if (tile == null) {
            return;
        }
        repaintLogicalRect(tileToRect(tile), 3);
    }

    public void repaintRegion(int regionId) {
        if (regionId < 0) {
            return;
        }
        repaintLogicalRect(regionToRect(regionId), 3);
    }

    public void dispose() {
        repeatMover.stop();
        visibleAreaNotifyTimer.stop();
        loader.shutdownNow();
        try {
            store.close();
        } catch (Exception ignored) {
        }
    }

    public void centerMap() {
        JViewport viewport = getViewport();
        if (viewport == null) {
            return;
        }
        Dimension size = currentViewSize();
        Rectangle view = viewport.getViewRect();
        clampAndSetViewPosition(viewport, new Point(
                (size.width - view.width) / 2,
                (size.height - view.height) / 2
        ));
        repaintVisible();
        fireVisibleAreaChanged();
    }

    @Override
    public Dimension getPreferredSize() {
        return preferredViewSize();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        Tile tile = pointToTile(event.getPoint());
        int regionId = regionIdFor(tile);
        for (int i = layers.size() - 1; i >= 0; i--) {
            String text = layers.get(i).tooltipText(renderContext, tile, regionId);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            g.setColor(mapBackgroundColor);
            g.fillRect(0, 0, getWidth(), getHeight());

            Rectangle visibleMap = visibleMapPixelRect();
            LOD lodNow = LOD.forZoom(zoom);
            drawTilesLODProgressive(g, lodNow, visibleMap, AtlasLayerKind.BASE);
            if (showMapIcons) {
                drawTilesLODProgressive(g, lodNow, visibleMap, AtlasLayerKind.ICONS);
            }
            if (showMapLabels) {
                drawTilesLODProgressive(g, lodNow, visibleMap, AtlasLayerKind.LABELS);
            }

            AffineTransform old = g.getTransform();
            g.translate(contentOriginX(), contentOriginY());
            g.scale(effZoom(), effZoom());

            if (showGrid) {
                drawGridLines(g, visibleMap);
            }
            for (MapLayer layer : layers) {
                layer.paint(renderContext, g, visibleMap);
            }
            drawHoverTileSelector(g, visibleMap);
            if (showRegionCoordinates || showRegionIds) {
                drawRegionText(g, visibleMap);
            }
            g.setTransform(old);
        } finally {
            g.dispose();
        }
    }

    private void installMouseHandlers() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                updateRegionSelectionActive(e.isShiftDown());
                activeTool.mouseClicked(toMapMouseEvent(e));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                updateRegionSelectionActive(e.isShiftDown());
                if (regionSelectionActive) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    dragStartView = null;
                    dragStartMouse = null;
                    return;
                }
                if (mapLocked) {
                    dragStartView = null;
                    dragStartMouse = null;
                    return;
                }
                JViewport viewport = getViewport();
                if (viewport != null) {
                    dragStartView = viewport.getViewPosition();
                    dragStartMouse = SwingUtilities.convertPoint(MapPanel.this, e.getPoint(), viewport);
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                updateRegionSelectionActive(e.isShiftDown());
                setCursor(regionSelectionActive
                        ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                        : Cursor.getDefaultCursor());
                dragStartView = null;
                dragStartMouse = null;
            }

            @Override
            public void mouseExited(MouseEvent e) {
                updateHover(null, -1);
                setCursor(Cursor.getDefaultCursor());
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                updateRegionSelectionActive(e.isShiftDown());
                if (regionSelectionActive) {
                    MapMouseEvent event = toMapMouseEvent(e);
                    Tile tile = event.tile();
                    updateHover(tile, event.regionId());
                    activeTool.mouseMoved(event);
                    return;
                }
                if (mapLocked) {
                    dragStartView = null;
                    dragStartMouse = null;
                    return;
                }

                JViewport viewport = getViewport();
                if (viewport == null || dragStartView == null || dragStartMouse == null) {
                    return;
                }
                Point now = SwingUtilities.convertPoint(MapPanel.this, e.getPoint(), viewport);
                Point target = new Point(
                        dragStartView.x - (now.x - dragStartMouse.x),
                        dragStartView.y - (now.y - dragStartMouse.y)
                );
                clampAndSetViewPosition(viewport, target);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateRegionSelectionActive(e.isShiftDown());
                MapMouseEvent event = toMapMouseEvent(e);
                Tile tile = event.tile();
                updateHover(tile, event.regionId());
                setCursor(regionSelectionActive
                        ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                        : Cursor.getDefaultCursor());
                activeTool.mouseMoved(event);
            }
        });

        addMouseWheelListener(this::handleMouseWheel);
    }

    private void syncViewportListener() {
        JViewport viewport = getViewport();
        if (viewport == observedViewport) {
            return;
        }
        detachViewportListener();
        observedViewport = viewport;
        if (observedViewport != null) {
            observedViewport.addChangeListener(viewportChangeListener);
        }
    }

    private void detachViewportListener() {
        if (observedViewport != null) {
            observedViewport.removeChangeListener(viewportChangeListener);
            observedViewport = null;
        }
    }

    private void scheduleVisibleAreaChanged() {
        visibleAreaNotifyTimer.restart();
    }

    private void fireVisibleAreaChanged() {
        visibleAreaNotifyTimer.stop();
        notifyVisibleAreaChanged();
    }

    private void notifyVisibleAreaChanged() {
        for (Runnable listener : visibleAreaChangeListeners) {
            listener.run();
        }
        activeTool.visibleAreaChanged();
    }

    private void handleMouseWheel(MouseWheelEvent e) {
        if (mapLocked) {
            e.consume();
            return;
        }
        double factor = e.getPreciseWheelRotation() < 0 ? 1.15 : 1.0 / 1.15;
        Point p = e.getPoint();
        setZoomAtPoint(zoom * factor, p.x, p.y);
        e.consume();
    }

    private MapMouseEvent toMapMouseEvent(MouseEvent event) {
        Tile tile = pointToTile(event.getPoint());
        return new MapMouseEvent(this, event, tile, regionIdFor(tile));
    }

    private boolean handleGlobalKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.VK_SHIFT) {
            if (event.getID() == KeyEvent.KEY_PRESSED) {
                updateRegionSelectionActive(activeTool.supportsRegionSelection());
            } else if (event.getID() == KeyEvent.KEY_RELEASED) {
                updateRegionSelectionActive(false);
            }
        } else {
            updateRegionSelectionActive(((event.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0)
                    && activeTool.supportsRegionSelection());
        }
        return false;
    }

    private void updateRegionSelectionActive(boolean next) {
        if (regionSelectionActive == next) {
            return;
        }
        regionSelectionActive = next && activeTool.supportsRegionSelection();
        repaintVisible();
        if (isShowing()) {
            setCursor(regionSelectionActive
                    ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                    : Cursor.getDefaultCursor());
        }
    }

    private double effZoom() {
        return zoom * (1.0 / HI_PX_PER_TILE);
    }

    private static double prefetchMarginLogical(double zoom) {
        double base = 80.0;
        return base * Math.max(0.6, Math.min(1.5, 1.0 / Math.sqrt(Math.max(zoom, 0.01))));
    }

    private void drawTilesLODProgressive(Graphics2D g, LOD targetLod, Rectangle visibleMap, AtlasLayerKind layerKind) {
        final int plane = currentPlane;
        final int layer = store.layerIndex(layerKind, plane);
        if (layer < 0) {
            return;
        }
        JViewport viewport = getViewport();
        if (viewport == null) {
            return;
        }
        if (lastLod != targetLod) {
            requestEpoch.incrementAndGet();
            inflight.clear();
            lastLod = targetLod;
        }
        final long epoch = requestEpoch.get();

        Rectangle view = viewport.getViewRect();
        double viewW = view.width / effZoom();
        double viewH = view.height / effZoom();
        double margin = prefetchMarginLogical(zoom);

        int originX = contentOriginX();
        int originY = contentOriginY();
        double scale = effZoom();
        int lx1 = (int) Math.floor((view.x - originX) / scale - margin);
        int ly1 = (int) Math.floor((view.y - originY) / scale - margin);
        int lx2 = (int) Math.ceil(lx1 + viewW + margin * 2);
        int ly2 = (int) Math.ceil(ly1 + viewH + margin * 2);

        lx1 = Math.max(0, lx1);
        ly1 = Math.max(0, ly1);
        lx2 = Math.min(totalW, lx2);
        ly2 = Math.min(totalH, ly2);

        final int tilePx = store.header().tilePx;
        int fullX1 = lx1 * HI_PX_PER_TILE;
        int fullY1 = ly1 * HI_PX_PER_TILE;
        int fullX2 = lx2 * HI_PX_PER_TILE;
        int fullY2 = ly2 * HI_PX_PER_TILE;

        int tX1 = Math.floorDiv(fullX1, tilePx * targetLod.subsample);
        int tY1 = Math.floorDiv(fullY1, tilePx * targetLod.subsample);
        int tX2 = Math.floorDiv(Math.max(fullX2 - 1, 0), tilePx * targetLod.subsample);
        int tY2 = Math.floorDiv(Math.max(fullY2 - 1, 0), tilePx * targetLod.subsample);

        List<int[]> order = new ArrayList<>();
        double cx = (view.getX() - originX) / effZoom() + viewW / 2.0;
        double cy = (view.getY() - originY) / effZoom() + viewH / 2.0;
        for (int ty = tY1; ty <= tY2; ty++) {
            for (int tx = tX1; tx <= tX2; tx++) {
                order.add(new int[]{tx, ty});
            }
        }
        order.sort(Comparator.comparingDouble(t -> {
            int tx = t[0];
            int ty = t[1];
            int px1 = (tx * tilePx * targetLod.subsample) / HI_PX_PER_TILE;
            int py1 = (ty * tilePx * targetLod.subsample) / HI_PX_PER_TILE;
            int px2 = ((tx * tilePx * targetLod.subsample) + tilePx * targetLod.subsample) / HI_PX_PER_TILE;
            int py2 = ((ty * tilePx * targetLod.subsample) + tilePx * targetLod.subsample) / HI_PX_PER_TILE;
            double mx = (px1 + px2) * 0.5;
            double my = (py1 + py2) * 0.5;
            double dx = mx - cx;
            double dy = my - cy;
            return dx * dx + dy * dy;
        }));

        final int maxLoadsThisPass = switch (targetLod) {
            case FULL -> 48;
            case HALF -> 96;
            case QUARTER -> 128;
        };
        int issuedLoads = 0;

        for (int[] tile : order) {
            int tx = tile[0];
            int ty = tile[1];
            if (isEmpty(layer, targetLod.subsample, tx, ty)) {
                continue;
            }

            int sx1 = tx * tilePx;
            int sy1 = ty * tilePx;
            int dx1 = (sx1 * targetLod.subsample) / HI_PX_PER_TILE;
            int dy1 = (sy1 * targetLod.subsample) / HI_PX_PER_TILE;
            int dx2 = ((sx1 + tilePx) * targetLod.subsample) / HI_PX_PER_TILE;
            int dy2 = ((sy1 + tilePx) * targetLod.subsample) / HI_PX_PER_TILE;
            Rectangle logicalTileRect = new Rectangle(dx1, dy1, dx2 - dx1, dy2 - dy1);

            if (!logicalTileRect.intersects(visibleMap)) {
                if (issuedLoads < maxLoadsThisPass && !hasCache(layer, targetLod.subsample, tx, ty)) {
                    requestTileAsync(targetLod, layer, tx, ty, epoch);
                    issuedLoads++;
                }
                continue;
            }

            BufferedImage sharp = getCache(layer, targetLod.subsample, tx, ty);
            if (sharp == null && issuedLoads < maxLoadsThisPass) {
                requestTileAsync(targetLod, layer, tx, ty, epoch);
                issuedLoads++;
            }

            BufferedImage fallbackImg = null;
            int srcX = 0;
            int srcY = 0;
            int srcW = 0;
            int srcH = 0;
            for (LOD coarse : targetLod.coarserChain()) {
                int ratio = coarse.subsample / targetLod.subsample;
                if (ratio <= 1) {
                    continue;
                }
                int cTx = Math.floorDiv(tx, ratio);
                int cTy = Math.floorDiv(ty, ratio);
                if (isEmpty(layer, coarse.subsample, cTx, cTy)) {
                    continue;
                }

                BufferedImage candidate = getCache(layer, coarse.subsample, cTx, cTy);
                if (candidate != null) {
                    int quadX = tx - (cTx * ratio);
                    int quadY = ty - (cTy * ratio);
                    int tilePxCoarse = store.header().tilePx;
                    int subW = tilePxCoarse / ratio;
                    int subH = tilePxCoarse / ratio;
                    srcX = quadX * subW;
                    srcY = quadY * subH;
                    srcW = subW;
                    srcH = subH;
                    fallbackImg = candidate;
                    break;
                } else if (issuedLoads < maxLoadsThisPass) {
                    requestTileAsync(coarse, layer, cTx, cTy, epoch);
                    issuedLoads++;
                }
            }

            if (fallbackImg != null) {
                drawImageMapped(g, fallbackImg, srcX, srcY, srcW, srcH, dx1, dy1, dx2, dy2,
                        originX, originY, scale);
            }
            if (sharp != null) {
                drawImageMapped(g, sharp, 0, 0, sharp.getWidth(), sharp.getHeight(), dx1, dy1, dx2, dy2,
                        originX, originY, scale);
            }
        }
    }

    private void drawImageMapped(Graphics2D g, BufferedImage img,
                                 int sx, int sy, int sw, int sh,
                                 int dx1, int dy1, int dx2, int dy2,
                                 int originX, int originY, double scale) {
        if (img == null || sw <= 0 || sh <= 0) {
            return;
        }
        int ddx1 = originX + (int) Math.round(dx1 * scale);
        int ddy1 = originY + (int) Math.round(dy1 * scale);
        int ddx2 = originX + (int) Math.round(dx2 * scale);
        int ddy2 = originY + (int) Math.round(dy2 * scale);
        if (ddx2 <= ddx1) {
            ddx2 = ddx1 + 1;
        }
        if (ddy2 <= ddy1) {
            ddy2 = ddy1 + 1;
        }
        g.drawImage(img, ddx1, ddy1, ddx2, ddy2, sx, sy, sx + sw, sy + sh, null);
    }

    private boolean hasCache(int layer, int lod, int tx, int ty) {
        synchronized (cache) {
            return cache.get(layer, lod, tx, ty) != null;
        }
    }

    private BufferedImage getCache(int layer, int lod, int tx, int ty) {
        synchronized (cache) {
            return cache.get(layer, lod, tx, ty);
        }
    }

    private boolean isEmpty(int layer, int lod, int tx, int ty) {
        return emptyTiles.contains(TileCache.key(layer, lod, tx, ty));
    }

    private void markEmpty(int layer, int lod, int tx, int ty) {
        emptyTiles.add(TileCache.key(layer, lod, tx, ty));
    }

    private void requestTileAsync(LOD lod, int layer, int tx, int ty, long epoch) {
        if (inflight.size() >= MAX_INFLIGHT) {
            return;
        }
        String key = layer + ":" + lod.subsample + ":" + tx + ":" + ty + ":" + epoch;
        if (!inflight.add(key)) {
            return;
        }

        try {
            loader.execute(() -> {
                try {
                    if (requestEpoch.get() != epoch) {
                        return;
                    }
                    BufferedImage img = store.readTileImage(lod.subsample, layer, tx, ty);
                    if (requestEpoch.get() != epoch) {
                        return;
                    }
                    if (img == null || isFullyTransparent(img)) {
                        markEmpty(layer, lod.subsample, tx, ty);
                        return;
                    }

                    synchronized (cache) {
                        cache.put(layer, lod.subsample, tx, ty, img);
                    }
                    SwingUtilities.invokeLater(() -> repaintAtlasTileDeviceRect(lod, tx, ty));
                } catch (Throwable ignored) {
                } finally {
                    inflight.remove(key);
                }
            });
        } catch (RejectedExecutionException ex) {
            inflight.remove(key);
        }
    }

    private static boolean isFullyTransparent(BufferedImage img) {
        if (img == null) {
            return true;
        }
        if (img.getTransparency() == Transparency.OPAQUE) {
            return false;
        }
        int width = img.getWidth();
        int height = img.getHeight();
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            img.getRGB(0, y, width, 1, row, 0, width);
            for (int pixel : row) {
                if (((pixel >>> 24) & 0xFF) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private void repaintAtlasTileDeviceRect(LOD lod, int tx, int ty) {
        int tilePx = store.header().tilePx;
        int sx1 = tx * tilePx;
        int sy1 = ty * tilePx;
        int dx1 = (sx1 * lod.subsample) / HI_PX_PER_TILE;
        int dy1 = (sy1 * lod.subsample) / HI_PX_PER_TILE;
        int dx2 = ((sx1 + tilePx) * lod.subsample) / HI_PX_PER_TILE;
        int dy2 = ((sy1 + tilePx) * lod.subsample) / HI_PX_PER_TILE;
        repaintLogicalRect(new Rectangle(dx1, dy1, dx2 - dx1, dy2 - dy1), 0);
    }

    private void drawGridLines(Graphics2D g, Rectangle visibleMap) {
        int colsLocal = Paths.MAX_RX - Paths.MIN_RX + 1;
        int rowsLocal = Paths.MAX_RY - Paths.MIN_RY + 1;
        double regionWidth = regionW();
        double regionHeight = regionH();

        int colStart = Math.max(0, (int) Math.floor(visibleMap.x / regionWidth));
        int colEnd = Math.min(colsLocal, (int) Math.ceil((visibleMap.x + visibleMap.width) / regionWidth));
        int rowStart = Math.max(0, (int) Math.floor(visibleMap.y / regionHeight));
        int rowEnd = Math.min(rowsLocal, (int) Math.ceil((visibleMap.y + visibleMap.height) / regionHeight));

        g.setColor(new Color(0x19FFFFFF, true));
        for (int c = colStart; c <= colEnd; c++) {
            int x = (int) Math.round(c * regionWidth);
            int y1 = (int) Math.round(rowStart * regionHeight);
            int y2 = (int) Math.round(rowEnd * regionHeight);
            g.drawLine(x, y1, x, y2);
        }
        for (int r = rowStart; r <= rowEnd; r++) {
            int y = (int) Math.round(r * regionHeight);
            int x1 = (int) Math.round(colStart * regionWidth);
            int x2 = (int) Math.round(colEnd * regionWidth);
            g.drawLine(x1, y, x2, y);
        }
    }

    private void drawRegionText(Graphics2D g, Rectangle visibleMap) {
        int colsLocal = Paths.MAX_RX - Paths.MIN_RX + 1;
        int rowsLocal = Paths.MAX_RY - Paths.MIN_RY + 1;
        double regionWidth = regionW();
        double regionHeight = regionH();

        int colStart = Math.max(0, (int) Math.floor(visibleMap.x / regionWidth));
        int colEnd = Math.min(colsLocal, (int) Math.ceil((visibleMap.x + visibleMap.width) / regionWidth));
        int rowStart = Math.max(0, (int) Math.floor(visibleMap.y / regionHeight));
        int rowEnd = Math.min(rowsLocal, (int) Math.ceil((visibleMap.y + visibleMap.height) / regionHeight));

        g.setFont(getFont().deriveFont(Font.BOLD, 13f));
        g.setColor(new Color(255, 255, 255, 180));
        FontMetrics fm = g.getFontMetrics();
        for (int rTop = rowStart; rTop < rowEnd; rTop++) {
            int ry = Paths.MAX_RY - rTop;
            for (int c = colStart; c < colEnd; c++) {
                int rx = Paths.MIN_RX + c;
                String text = showRegionIds ? String.valueOf((rx << 8) | ry) : rx + "," + ry;
                int cx = (int) Math.round(c * regionWidth + regionWidth / 2.0);
                int cy = (int) Math.round(rTop * regionHeight + regionHeight / 2.0);
                g.drawString(text, cx - fm.stringWidth(text) / 2, cy + fm.getAscent() / 2 - 2);
            }
        }
    }

    private void drawHoverTileSelector(Graphics2D g, Rectangle visibleMap) {
        if (regionSelectionActive || hoverTile == null || hoverTile.z != currentPlane) {
            return;
        }
        Rectangle rect = tileToRect(hoverTile);
        if (!rect.intersects(visibleMap)) {
            return;
        }
        Stroke oldStroke = g.getStroke();
        float screenPx = Math.max(0.75f, (float) (1.0 / Math.max(effZoom(), 0.0001)));
        g.setStroke(new BasicStroke(screenPx));
        g.setColor(new Color(0x3CFFFFFF, true));
        g.fillRect(rect.x, rect.y, rect.width, rect.height);
        g.setColor(new Color(0xB4FFFFFF, true));
        g.drawRect(rect.x, rect.y, rect.width, rect.height);
        g.setStroke(oldStroke);
    }

    private void updateHover(Tile nextTile, int nextRegionId) {
        Tile previousTile = hoverTile;
        int previousRegionId = hoverRegionId;
        if ((previousTile == null ? nextTile == null : previousTile.equals(nextTile))
                && previousRegionId == nextRegionId) {
            return;
        }

        hoverTile = nextTile == null ? null : new Tile(nextTile.x, nextTile.y, nextTile.z);
        hoverRegionId = nextRegionId;

        if (previousRegionId != nextRegionId) {
            repaintRegion(previousRegionId);
            repaintRegion(nextRegionId);
        }

        Rectangle previousRect = previousTile == null ? null : tileToRect(previousTile);
        Rectangle nextRect = nextTile == null ? null : tileToRect(nextTile);
        repaintLogicalUnion(previousRect, nextRect, 3);
    }

    private Tile toTile(int px, int py) {
        double x = (px - contentOriginX()) / effZoom();
        double y = (py - contentOriginY()) / effZoom();
        if (x < 0 || y < 0 || x >= totalW || y >= totalH) {
            return null;
        }
        int col = (int) Math.floor(x / regionW());
        int rowTop = (int) Math.floor(y / regionH());
        col = Math.max(0, Math.min(Paths.MAX_RX - Paths.MIN_RX, col));
        rowTop = Math.max(0, Math.min(Paths.MAX_RY - Paths.MIN_RY, rowTop));
        double ox = x - col * regionW();
        double oyTop = y - rowTop * regionH();
        int tx = (int) Math.floor(ox / (regionW() / REGION_TILES));
        int tyTop = (int) Math.floor(oyTop / (regionH() / REGION_TILES));
        tx = Math.max(0, Math.min(REGION_TILES - 1, tx));
        tyTop = Math.max(0, Math.min(REGION_TILES - 1, tyTop));
        int rowFromBottom = Paths.MAX_RY - Paths.MIN_RY - rowTop;
        int tyFromBottom = REGION_TILES - 1 - tyTop;
        int worldX = (Paths.MIN_RX + col) * REGION_TILES + tx;
        int worldY = (Paths.MIN_RY + rowFromBottom) * REGION_TILES + tyFromBottom;
        return new Tile(worldX, worldY, currentPlane);
    }

    private Rectangle visibleMapPixelRect() {
        JViewport viewport = getViewport();
        if (viewport == null) {
            return new Rectangle(0, 0, 0, 0);
        }
        Rectangle view = viewport.getViewRect();
        double ez = effZoom();
        int originX = contentOriginX();
        int originY = contentOriginY();
        int sx = (int) Math.floor((view.x - originX) / ez);
        int sy = (int) Math.floor((view.y - originY) / ez);
        int ex = (int) Math.ceil((view.x + view.width - originX) / ez);
        int ey = (int) Math.ceil((view.y + view.height - originY) / ez);

        sx = Math.max(0, Math.min(sx, totalW));
        sy = Math.max(0, Math.min(sy, totalH));
        ex = Math.max(0, Math.min(ex, totalW));
        ey = Math.max(0, Math.min(ey, totalH));

        int pad = 2;
        sx = Math.max(0, sx - pad);
        sy = Math.max(0, sy - pad);
        ex = Math.min(totalW, ex + pad);
        ey = Math.min(totalH, ey + pad);
        return new Rectangle(sx, sy, Math.max(0, ex - sx), Math.max(0, ey - sy));
    }

    private double regionW() {
        return totalW / (double) (Paths.MAX_RX - Paths.MIN_RX + 1);
    }

    private double regionH() {
        return totalH / (double) (Paths.MAX_RY - Paths.MIN_RY + 1);
    }

    private Rectangle scaleRect(Rectangle r, double factor) {
        return new Rectangle(
                (int) Math.round(r.x * factor),
                (int) Math.round(r.y * factor),
                Math.max(1, (int) Math.round(r.width * factor)),
                Math.max(1, (int) Math.round(r.height * factor))
        );
    }

    private void repaintLogicalRect(Rectangle logicalRect, int devicePadPx) {
        if (logicalRect == null || logicalRect.isEmpty()) {
            return;
        }
        Rectangle device = scaleRect(logicalRect, effZoom());
        device.translate(contentOriginX(), contentOriginY());
        device.grow(devicePadPx, devicePadPx);

        JViewport viewport = getViewport();
        if (viewport != null) {
            Rectangle view = viewport.getViewRect().intersection(new Rectangle(0, 0, getWidth(), getHeight()));
            Rectangle dirty = device.intersection(view);
            if (!dirty.isEmpty()) {
                repaint(dirty.x, dirty.y, dirty.width, dirty.height);
            }
        } else {
            repaint(device.x, device.y, device.width, device.height);
        }
    }

    private void repaintLogicalUnion(Rectangle a, Rectangle b, int devicePadPx) {
        Rectangle union = null;
        if (a != null) {
            union = new Rectangle(a);
        }
        if (b != null) {
            union = union == null ? new Rectangle(b) : union.union(b);
        }
        repaintLogicalRect(union, devicePadPx);
    }

    private void centerViewOn(Rectangle viewTargetDeviceSpace) {
        JViewport viewport = getViewport();
        if (viewport == null) {
            return;
        }
        Rectangle view = viewport.getViewRect();
        Point point = new Point(
                viewTargetDeviceSpace.x + viewTargetDeviceSpace.width / 2 - view.width / 2,
                viewTargetDeviceSpace.y + viewTargetDeviceSpace.height / 2 - view.height / 2
        );
        clampAndSetViewPosition(viewport, point);
    }

    private void clampAndSetViewPosition(JViewport viewport, Point point) {
        Dimension size = currentViewSize();
        Rectangle view = viewport.getViewRect();
        int maxX = Math.max(0, size.width - view.width);
        int maxY = Math.max(0, size.height - view.height);
        point.x = Math.max(0, Math.min(point.x, maxX));
        point.y = Math.max(0, Math.min(point.y, maxY));
        viewport.setViewPosition(point);
    }

    private JViewport getViewport() {
        return (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
    }

    private void setZoomAtPoint(double newZoomUi, int anchorX, int anchorY) {
        double clamped = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoomUi));
        JViewport viewport = getViewport();
        if (viewport == null) {
            zoom = clamped;
            revalidate();
            repaintVisible();
            return;
        }

        Rectangle view = viewport.getViewRect();
        int dx = anchorX - view.x;
        int dy = anchorY - view.y;
        Dimension oldPreferredSize = getPreferredSize();
        Dimension oldViewSize = currentViewSize();
        double worldX = (anchorX - contentOriginX(oldViewSize, oldPreferredSize)) / effZoom();
        double worldY = (anchorY - contentOriginY(oldViewSize, oldPreferredSize)) / effZoom();

        zoom = clamped;
        requestEpoch.incrementAndGet();
        inflight.clear();

        Dimension newPreferredSize = getPreferredSize();
        Dimension newViewSize = viewSizeFor(newPreferredSize, view.getSize());
        setPreferredSize(newPreferredSize);
        viewport.setViewSize(newViewSize);

        int newAnchorPx = contentOriginX(newViewSize, newPreferredSize) + (int) Math.round(worldX * effZoom());
        int newAnchorPy = contentOriginY(newViewSize, newPreferredSize) + (int) Math.round(worldY * effZoom());
        clampAndSetViewPosition(viewport, new Point(newAnchorPx - dx, newAnchorPy - dy));
        revalidate();
        repaintVisible();
        fireVisibleAreaChanged();
    }

    private void setZoomCentered(double newZoomUi) {
        double clamped = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoomUi));
        JViewport viewport = getViewport();
        if (viewport == null) {
            zoom = clamped;
            revalidate();
            repaintVisible();
            return;
        }

        Rectangle view = viewport.getViewRect();
        Point centerView = new Point(view.x + view.width / 2, view.y + view.height / 2);
        Dimension oldPreferredSize = getPreferredSize();
        Dimension oldViewSize = currentViewSize();
        double anchorX = (centerView.x - contentOriginX(oldViewSize, oldPreferredSize)) / effZoom();
        double anchorY = (centerView.y - contentOriginY(oldViewSize, oldPreferredSize)) / effZoom();

        zoom = clamped;
        requestEpoch.incrementAndGet();
        inflight.clear();

        Dimension newPreferredSize = getPreferredSize();
        Dimension newViewSize = viewSizeFor(newPreferredSize, view.getSize());
        setPreferredSize(newPreferredSize);
        viewport.setViewSize(newViewSize);

        int newCenterX = contentOriginX(newViewSize, newPreferredSize) + (int) Math.round(anchorX * effZoom());
        int newCenterY = contentOriginY(newViewSize, newPreferredSize) + (int) Math.round(anchorY * effZoom());
        revalidate();
        clampAndSetViewPosition(viewport, new Point(newCenterX - view.width / 2, newCenterY - view.height / 2));
        repaintVisible();
        fireVisibleAreaChanged();
    }

    private Dimension preferredViewSize() {
        return new Dimension((int) (totalW * effZoom()) + 2 * CANVAS_PAD,
                (int) (totalH * effZoom()) + 2 * CANVAS_PAD);
    }

    private Dimension currentViewSize() {
        Dimension size = getSize();
        Dimension preferred = getPreferredSize();
        return new Dimension(
                Math.max(size.width, preferred.width),
                Math.max(size.height, preferred.height)
        );
    }

    private Dimension viewSizeFor(Dimension preferred, Dimension extent) {
        if (extent == null) {
            return preferred;
        }
        return new Dimension(
                Math.max(preferred.width, extent.width),
                Math.max(preferred.height, extent.height)
        );
    }

    private int contentOriginX() {
        return contentOriginX(currentViewSize(), getPreferredSize());
    }

    private int contentOriginY() {
        return contentOriginY(currentViewSize(), getPreferredSize());
    }

    private int contentOriginX(Dimension viewSize, Dimension preferredSize) {
        return CANVAS_PAD + Math.max(0, (viewSize.width - preferredSize.width) / 2);
    }

    private int contentOriginY(Dimension viewSize, Dimension preferredSize) {
        return CANVAS_PAD + Math.max(0, (viewSize.height - preferredSize.height) / 2);
    }

    private void installArrowKeyBindings() {
        int condition = JComponent.WHEN_IN_FOCUSED_WINDOW;
        InputMap inputMap = getInputMap(condition);
        ActionMap actionMap = getActionMap();
        Map<String, int[]> directions = new HashMap<>();
        directions.put("LEFT", new int[]{-1, 0});
        directions.put("RIGHT", new int[]{1, 0});
        directions.put("UP", new int[]{0, 1});
        directions.put("DOWN", new int[]{0, -1});
        directions.put("KP_LEFT", new int[]{-1, 0});
        directions.put("KP_RIGHT", new int[]{1, 0});
        directions.put("KP_UP", new int[]{0, 1});
        directions.put("KP_DOWN", new int[]{0, -1});

        for (Map.Entry<String, int[]> entry : directions.entrySet()) {
            String name = entry.getKey();
            int[] vector = entry.getValue();
            String pressKey = "press_" + name;
            String releaseKey = "release_" + name;
            inputMap.put(KeyStroke.getKeyStroke(name), pressKey);
            inputMap.put(KeyStroke.getKeyStroke("released " + name), releaseKey);
            actionMap.put(pressKey, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent ev) {
                    onArrowPressed(vector[0], vector[1]);
                }
            });
            actionMap.put(releaseKey, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent ev) {
                    onArrowReleased(vector[0], vector[1]);
                }
            });
        }
    }

    private void onArrowPressed(int dx, int dy) {
        heldDX = clampStep(heldDX + dx);
        heldDY = clampStep(heldDY + dy);
        if (heldDX == 0 && heldDY == 0) {
            repeatMover.stop();
        } else {
            repeatMover.start();
        }
    }

    private void onArrowReleased(int dx, int dy) {
        heldDX = clampStep(heldDX - dx);
        heldDY = clampStep(heldDY - dy);
        if (heldDX == 0 && heldDY == 0) {
            repeatMover.stop();
        } else {
            repeatMover.bumpVectorChange();
        }
    }

    private static int clampStep(int value) {
        return value < 0 ? -1 : (value > 0 ? 1 : 0);
    }

    private final class RepeatMover implements ActionListener {
        private static final int INITIAL_DELAY_MS = 220;
        private static final int BASE_INTERVAL_MS = 90;
        private static final int FAST_INTERVAL_MS = 35;
        private static final int ACCEL_AFTER_STEPS = 8;
        private final Timer timer = new Timer(BASE_INTERVAL_MS, this);
        private int stepCount = 0;
        private boolean running = false;
        private boolean toggleAxis = false;

        RepeatMover() {
            timer.setRepeats(true);
        }

        void start() {
            if (!running) {
                running = true;
                stepCount = 0;
                toggleAxis = false;
                immediateStep();
                timer.setInitialDelay(INITIAL_DELAY_MS);
                timer.setDelay(BASE_INTERVAL_MS);
                timer.restart();
            } else if (!timer.isRunning()) {
                timer.setInitialDelay(0);
                timer.setDelay(currentInterval());
                timer.start();
            }
        }

        void stop() {
            timer.stop();
            running = false;
        }

        void bumpVectorChange() {
            if (running && timer.isRunning()) {
                timer.setDelay(currentInterval());
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (heldDX == 0 && heldDY == 0) {
                stop();
                return;
            }
            stepOnce();
            stepCount++;
            timer.setDelay(currentInterval());
        }

        private void immediateStep() {
            if (heldDX == 0 && heldDY == 0) {
                return;
            }
            stepOnce();
            stepCount++;
        }

        private int currentInterval() {
            return stepCount >= ACCEL_AFTER_STEPS ? FAST_INTERVAL_MS : BASE_INTERVAL_MS;
        }

        private void stepOnce() {
            int dx = heldDX;
            int dy = heldDY;
            if (dx != 0 && dy != 0) {
                if (toggleAxis) {
                    dx = 0;
                } else {
                    dy = 0;
                }
                toggleAxis = !toggleAxis;
            }
            activeTool.arrowStep(dx, dy);
        }
    }

    private static final class TileCache extends LinkedHashMap<String, BufferedImage> {
        private long budgetBytes;
        private long liveBytes = 0L;

        TileCache(long budgetBytes) {
            super(512, 0.75f, true);
            this.budgetBytes = normalizeBudgetBytes(budgetBytes);
        }

        synchronized void setBudgetBytes(long budgetBytes) {
            this.budgetBytes = normalizeBudgetBytes(budgetBytes);
            trimToBudget();
        }

        synchronized long budgetBytes() {
            return budgetBytes;
        }

        static String key(int plane, int lod, int tx, int ty) {
            return plane + ":" + lod + ":" + tx + ":" + ty;
        }

        synchronized BufferedImage get(int plane, int lod, int tx, int ty) {
            return super.get(key(plane, lod, tx, ty));
        }

        synchronized void put(int plane, int lod, int tx, int ty, BufferedImage img) {
            BufferedImage previous = super.put(key(plane, lod, tx, ty), img);
            if (previous != null) {
                liveBytes -= approx(previous);
            }
            if (img != null) {
                liveBytes += approx(img);
            }
            trimToBudget();
        }

        private void trimToBudget() {
            while (liveBytes > budgetBytes && !isEmpty()) {
                Map.Entry<String, BufferedImage> eldest = entrySet().iterator().next();
                BufferedImage removed = super.remove(eldest.getKey());
                if (removed != null) {
                    liveBytes -= approx(removed);
                }
            }
        }

        @Override
        public synchronized BufferedImage remove(Object key) {
            BufferedImage previous = super.remove(key);
            if (previous != null) {
                liveBytes -= approx(previous);
            }
            return previous;
        }

        private static long approx(BufferedImage image) {
            return image == null ? 0 : (long) image.getWidth() * (long) image.getHeight() * 4L;
        }

        private static long normalizeBudgetBytes(long budgetBytes) {
            return budgetBytes == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(MIN_CACHE_BUDGET_BYTES, budgetBytes);
        }
    }

    private static final class AtlasStoreReader implements AutoCloseable {
        static final class Header {
            final int srcWidth;
            final int srcHeight;
            final int tilePx;
            final long indexOffset;
            final long dataOffset;
            final long metadataOffset;
            final int metadataLength;

            Header(int srcWidth, int srcHeight, int tilePx, long indexOffset, long dataOffset,
                   long metadataOffset, int metadataLength) {
                this.srcWidth = srcWidth;
                this.srcHeight = srcHeight;
                this.tilePx = tilePx;
                this.indexOffset = indexOffset;
                this.dataOffset = dataOffset;
                this.metadataOffset = metadataOffset;
                this.metadataLength = metadataLength;
            }
        }

        static final class TileEntry {
            final int lod;
            final int z;
            final int tx;
            final int ty;
            final int imgW;
            final int imgH;
            final long relOffset;
            final int length;

            TileEntry(int lod, int z, int tx, int ty, int imgW, int imgH, long relOffset, int length) {
                this.lod = lod;
                this.z = z;
                this.tx = tx;
                this.ty = ty;
                this.imgW = imgW;
                this.imgH = imgH;
                this.relOffset = relOffset;
                this.length = length;
            }
        }

        private final RandomAccessFile raf;
        private final java.nio.channels.FileChannel channel;
        private final long dataOffsetAbs;
        private final Header header;
        private final Map<String, TileEntry> index = new ConcurrentHashMap<>();
        private final Map<String, Integer> layerIndices = new ConcurrentHashMap<>();
        private byte[] metadataBytes = new byte[0];
        private volatile AreaIndex areaIndex;
        private int maxPlanes = 1;

        Header header() {
            return header;
        }

        List<MapArea> searchMapAreas(String query, int limit) {
            String normalizedQuery = normalizeForSearch(query);
            if (normalizedQuery.length() < 3 || metadataBytes.length == 0 || limit <= 0) {
                return List.of();
            }
            return searchMapAreas(metadataBytes, normalizedQuery, limit);
        }

        List<MapArea> mapAreasAt(Tile tile) {
            if (tile == null || metadataBytes.length == 0) {
                return List.of();
            }
            return areaIndex().areasAt(tile);
        }

        MapArea nearestMapArea(Tile tile, int maxDistanceTiles) {
            if (tile == null || metadataBytes.length == 0 || maxDistanceTiles < 0) {
                return null;
            }
            return areaIndex().nearest(tile, maxDistanceTiles);
        }

        private AreaIndex areaIndex() {
            AreaIndex current = areaIndex;
            if (current != null) {
                return current;
            }
            synchronized (this) {
                current = areaIndex;
                if (current == null) {
                    current = new AreaIndex(parseMapAreas(metadataBytes));
                    areaIndex = current;
                }
                return current;
            }
        }

        static AtlasStoreReader openFile(Path path) throws Exception {
            if (path == null || !Files.isRegularFile(path)) {
                throw new IllegalStateException("Atlas file not found");
            }
            return new AtlasStoreReader(new RandomAccessFile(path.toFile(), "r"));
        }

        private AtlasStoreReader(RandomAccessFile raf) throws Exception {
            this.raf = raf;
            this.header = readHeader();
            this.channel = raf.getChannel();
            this.dataOffsetAbs = header.dataOffset;
            readMetadata();
            readIndex();
        }

        private Header readHeader() throws Exception {
            raf.seek(0);
            byte[] magic8 = new byte[8];
            raf.readFully(magic8);
            byte[] expected7 = new byte[]{'A', 'T', 'L', 'S', 'v', '3', 0x00};
            boolean first7Match = true;
            for (int i = 0; i < 7; i++) {
                if (magic8[i] != expected7[i]) {
                    first7Match = false;
                    break;
                }
            }
            if (!first7Match) {
                throw new IllegalStateException("Atlas: bad magic");
            }
            if (magic8[7] != 0x00) {
                raf.seek(7);
            }

            int version = readU32();
            if (version != 3) {
                throw new IllegalStateException("Atlas: unsupported version " + version + "; expected 3");
            }
            int srcW = readU32();
            int srcH = readU32();
            int tile = readU32();
            int numLods = readU32();
            for (int i = 0; i < numLods; i++) {
                readU32();
            }
            readU32();
            readU32();
            readU32();
            long indexOff = readU64();
            long dataOff = readU64();
            long metadataOff = readU64();
            int metadataLen = readU32();
            return new Header(srcW, srcH, tile, indexOff, dataOff, metadataOff, metadataLen);
        }

        private void readMetadata() throws Exception {
            layerIndices.clear();
            metadataBytes = new byte[0];
            areaIndex = null;
            if (header.metadataOffset <= 0 || header.metadataLength <= 0) {
                throw new IllegalStateException("Atlas: missing v3 metadata");
            }

            byte[] bytes = new byte[header.metadataLength];
            raf.seek(header.metadataOffset);
            raf.readFully(bytes);
            metadataBytes = bytes;
            boolean hasLayers = parseLayerMetadata(bytes);
            if (!hasLayers || layerIndices.isEmpty()) {
                throw new IllegalStateException("Atlas: missing v3 layer metadata");
            }
        }

        private boolean parseLayerMetadata(byte[] bytes) throws Exception {
            int maxPlaneSeen = -1;
            boolean foundLayers = false;
            try (JsonReader reader = new JsonReader(new InputStreamReader(
                    new ByteArrayInputStream(bytes),
                    StandardCharsets.UTF_8
            ))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!"layers".equals(name) || reader.peek() != JsonToken.BEGIN_ARRAY) {
                        reader.skipValue();
                        continue;
                    }

                    foundLayers = true;
                    reader.beginArray();
                    while (reader.hasNext()) {
                        LayerMetadata layer = readLayerMetadataEntry(reader);
                        if (layer.kind() == null || layer.plane() < 0 || layer.index() < 0) {
                            continue;
                        }
                        layerIndices.put(layerKey(layer.kind(), layer.plane()), layer.index());
                        maxPlaneSeen = Math.max(maxPlaneSeen, layer.plane());
                    }
                    reader.endArray();
                }
                reader.endObject();
            }
            maxPlanes = Math.max(1, maxPlaneSeen + 1);
            return foundLayers;
        }

        private static LayerMetadata readLayerMetadataEntry(JsonReader reader) throws Exception {
            AtlasLayerKind kind = null;
            int plane = -1;
            int index = -1;
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "kind" -> kind = AtlasLayerKind.fromMetadataName(readStringValue(reader));
                    case "plane" -> plane = readIntValue(reader, -1);
                    case "index" -> index = readIntValue(reader, -1);
                    default -> reader.skipValue();
                }
            }
            reader.endObject();
            return new LayerMetadata(kind, plane, index);
        }

        private static List<MapArea> searchMapAreas(byte[] bytes, String normalizedQuery, int limit) {
            int maxCandidates = Math.max(limit, Math.min(500, limit * 3));
            Map<String, MapArea> out = new LinkedHashMap<>();
            try (JsonReader reader = new JsonReader(new InputStreamReader(
                    new ByteArrayInputStream(bytes),
                    StandardCharsets.UTF_8
            ))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }
                    String name = reader.nextName();
                    if (("locationLabels".equals(name) || "mapIcons".equals(name))
                            && reader.peek() == JsonToken.BEGIN_ARRAY) {
                        collectMapAreaMatches(reader, normalizedQuery, out, maxCandidates);
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            } catch (Exception ex) {
                return List.of();
            }

            List<MapArea> sorted = new ArrayList<>(out.values());
            sorted.sort(Comparator
                    .comparingInt((MapArea area) -> searchScore(area, normalizedQuery))
                    .thenComparing(MapArea::displayName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(MapArea::plane)
                    .thenComparingInt(MapArea::worldX)
                    .thenComparingInt(MapArea::worldY)
                    .thenComparing(MapArea::source, String.CASE_INSENSITIVE_ORDER));
            if (sorted.size() > limit) {
                sorted = sorted.subList(0, limit);
            }
            return List.copyOf(sorted);
        }

        private static List<MapArea> parseMapAreas(byte[] bytes) {
            Map<String, MapArea> out = new LinkedHashMap<>();
            try (JsonReader reader = new JsonReader(new InputStreamReader(
                    new ByteArrayInputStream(bytes),
                    StandardCharsets.UTF_8
            ))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (("locationLabels".equals(name) || "mapIcons".equals(name))
                            && reader.peek() == JsonToken.BEGIN_ARRAY) {
                        collectMapAreas(reader, out);
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            } catch (Exception ex) {
                return List.of();
            }
            return List.copyOf(out.values());
        }

        private static void collectMapAreas(JsonReader reader, Map<String, MapArea> out) throws Exception {
            reader.beginArray();
            while (reader.hasNext()) {
                MapArea area = readMapArea(reader);
                if (area != null) {
                    out.putIfAbsent(areaKey(area), area);
                }
            }
            reader.endArray();
        }

        private static void collectMapAreaMatches(JsonReader reader, String normalizedQuery,
                                                  Map<String, MapArea> out, int maxCandidates) throws Exception {
            reader.beginArray();
            while (reader.hasNext()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
                if (out.size() >= maxCandidates) {
                    reader.skipValue();
                    continue;
                }

                MapArea area = readMapArea(reader);
                if (area == null || !normalizeForSearch(area.searchText()).contains(normalizedQuery)) {
                    continue;
                }
                out.putIfAbsent(areaKey(area), area);
            }
            reader.endArray();
        }

        private static MapArea readMapArea(JsonReader reader) throws Exception {
            String name = null;
            String rawName = null;
            String objectName = null;
            String source = null;
            int areaId = -1;
            Integer worldX = null;
            Integer worldY = null;
            Integer plane = null;

            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                switch (key) {
                    case "name" -> name = cleanString(readStringValue(reader));
                    case "rawName" -> rawName = cleanString(readStringValue(reader));
                    case "objectName" -> objectName = cleanString(readStringValue(reader));
                    case "source" -> source = readStringValue(reader);
                    case "areaId" -> areaId = readIntValue(reader, -1);
                    case "worldX" -> worldX = readNullableIntValue(reader);
                    case "worldY" -> worldY = readNullableIntValue(reader);
                    case "plane" -> plane = readNullableIntValue(reader);
                    default -> reader.skipValue();
                }
            }
            reader.endObject();

            String displayName = firstNonBlank(name, rawName, objectName);
            if (displayName == null || worldX == null || worldY == null || plane == null) {
                return null;
            }
            return new MapArea(displayName, rawName, objectName, source, areaId, worldX, worldY, plane);
        }

        private static String areaKey(MapArea area) {
            return area.displayName() + '\u0000'
                    + area.worldX() + '\u0000'
                    + area.worldY() + '\u0000'
                    + area.plane() + '\u0000'
                    + area.source() + '\u0000'
                    + area.areaId();
        }

        private static String firstNonBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }

        private static String readStringValue(JsonReader reader) throws Exception {
            JsonToken token = reader.peek();
            if (token == JsonToken.NULL) {
                reader.nextNull();
                return null;
            }
            if (token == JsonToken.STRING || token == JsonToken.NUMBER || token == JsonToken.BOOLEAN) {
                return reader.nextString();
            }
            reader.skipValue();
            return null;
        }

        private static Integer readNullableIntValue(JsonReader reader) throws Exception {
            JsonToken token = reader.peek();
            if (token == JsonToken.NULL) {
                reader.nextNull();
                return null;
            }
            try {
                if (token == JsonToken.NUMBER || token == JsonToken.STRING) {
                    return Integer.parseInt(reader.nextString());
                }
            } catch (NumberFormatException ex) {
                return null;
            }
            reader.skipValue();
            return null;
        }

        private static int readIntValue(JsonReader reader, int fallback) throws Exception {
            Integer value = readNullableIntValue(reader);
            return value == null ? fallback : value;
        }

        private static int searchScore(MapArea area, String query) {
            String name = normalizeForSearch(area.displayName());
            if (name.equals(query)) {
                return 0;
            }
            if (name.startsWith(query)) {
                return 1;
            }
            for (String token : name.split("\\s+")) {
                if (token.startsWith(query)) {
                    return 2;
                }
            }
            if (normalizeForSearch(area.searchText()).startsWith(query)) {
                return 3;
            }
            return 4;
        }

        private static String normalizeForSearch(String text) {
            if (text == null) {
                return "";
            }
            return text.trim().toLowerCase(java.util.Locale.ROOT);
        }

        private record LayerMetadata(AtlasLayerKind kind, int plane, int index) {
        }

        private static String cleanString(String value) {
            if (value == null) {
                return null;
            }
            String cleaned = value.replace("<br>", " ")
                    .replace("<br/>", " ")
                    .replace("<br />", " ")
                    .trim();
            return cleaned.isEmpty() ? null : cleaned;
        }

        private void readIndex() throws Exception {
            raf.seek(header.indexOffset);
            long entrySize = 36L;
            long totalEntries = (header.dataOffset - header.indexOffset) / entrySize;
            for (long i = 0; i < totalEntries; i++) {
                int lod = readU32();
                int z = readU32();
                int tx = readU32();
                int ty = readU32();
                int width = readU32();
                int height = readU32();
                long rel = readU64();
                int length = readU32();
                TileEntry entry = new TileEntry(lod, z, tx, ty, width, height, rel, length);
                index.put(key(lod, z, tx, ty), entry);
            }
        }

        private static final class AreaIndex {
            private final List<MapArea> areas;
            private final Map<String, List<MapArea>> byTile;

            AreaIndex(List<MapArea> areas) {
                this.areas = areas == null ? List.of() : List.copyOf(areas);
                Map<String, List<MapArea>> grouped = new HashMap<>();
                for (MapArea area : this.areas) {
                    grouped.computeIfAbsent(tileKey(area.worldX(), area.worldY(), area.plane()),
                            ignored -> new ArrayList<>()).add(area);
                }
                Map<String, List<MapArea>> immutable = new HashMap<>();
                for (Map.Entry<String, List<MapArea>> entry : grouped.entrySet()) {
                    immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
                }
                byTile = Map.copyOf(immutable);
            }

            List<MapArea> areasAt(Tile tile) {
                if (tile == null) {
                    return List.of();
                }
                return byTile.getOrDefault(tileKey(tile.x, tile.y, tile.z), List.of());
            }

            MapArea nearest(Tile tile, int maxDistanceTiles) {
                if (tile == null || maxDistanceTiles < 0) {
                    return null;
                }
                List<MapArea> exact = areasAt(tile);
                if (!exact.isEmpty()) {
                    return exact.get(0);
                }
                long maxDistanceSq = (long) maxDistanceTiles * maxDistanceTiles;
                MapArea best = null;
                long bestDistanceSq = Long.MAX_VALUE;
                for (MapArea area : areas) {
                    if (area.plane() != tile.z) {
                        continue;
                    }
                    long dx = (long) area.worldX() - tile.x;
                    long dy = (long) area.worldY() - tile.y;
                    long distanceSq = dx * dx + dy * dy;
                    if (distanceSq > maxDistanceSq || distanceSq > bestDistanceSq) {
                        continue;
                    }
                    if (distanceSq == bestDistanceSq && best != null
                            && area.displayName().compareToIgnoreCase(best.displayName()) >= 0) {
                        continue;
                    }
                    best = area;
                    bestDistanceSq = distanceSq;
                }
                return best;
            }

            private static String tileKey(int x, int y, int z) {
                return x + ":" + y + ":" + z;
            }
        }

        private static String key(int lod, int z, int tx, int ty) {
            return lod + ":" + z + ":" + tx + ":" + ty;
        }

        private static String layerKey(AtlasLayerKind kind, int plane) {
            return kind.metadataName + ":" + plane;
        }

        private int readU32() throws Exception {
            byte[] bytes = new byte[4];
            raf.readFully(bytes);
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }

        private long readU64() throws Exception {
            byte[] bytes = new byte[8];
            raf.readFully(bytes);
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
        }

        int maxPlanes() {
            return maxPlanes;
        }

        int layerIndex(AtlasLayerKind kind, int plane) {
            if (kind == null || plane < 0) {
                return -1;
            }
            return layerIndices.getOrDefault(layerKey(kind, plane), -1);
        }

        TileEntry getEntry(int lod, int layer, int tx, int ty) {
            return index.get(key(lod, layer, tx, ty));
        }

        BufferedImage readTileImage(int lod, int layer, int tx, int ty) throws Exception {
            TileEntry entry = getEntry(lod, layer, tx, ty);
            if (entry == null) {
                return null;
            }

            byte[] buf = new byte[entry.length];
            ByteBuffer bb = ByteBuffer.wrap(buf);
            long position = dataOffsetAbs + entry.relOffset;
            int read = 0;
            while (read < entry.length) {
                int n = channel.read(bb, position + read);
                if (n < 0) {
                    throw new EOFException("Unexpected EOF reading tile");
                }
                read += n;
            }

            try (ByteArrayInputStream bais = new ByteArrayInputStream(buf)) {
                return ImageIO.read(bais);
            }
        }

        @Override
        public void close() throws Exception {
            try {
                channel.close();
            } finally {
                raf.close();
            }
        }
    }
}
