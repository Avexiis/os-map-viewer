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
package com.xeon.view3d;

import com.xeon.atlas.AtlasStorage;
import com.xeon.io.Paths;
import com.xeon.io.Viewer3DState;
import com.xeon.io.ViewerSettings;
import com.xeon.model.MapArea;
import com.xeon.model.Tile;
import com.xeon.plugin.MapViewerPlugin;
import com.xeon.view.MapAreaSearchPanel;
import com.xeon.view.MapPanel;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLayeredPane;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.rlawt.AWTContext;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.opengl.GL33C;

public final class Map3DPanel extends JPanel
{
	private static final int FRAME_DELAY_MS = 16;
	private static final int TOOLBAR_HEIGHT = 40;
	private static final double SLOW_FRAME_MILLIS = 33.4;
	private static final double SLOW_RENDER_MILLIS = 20.0;
	private static final int HIGH_DRAW_CALLS = 128;
	private static final int HIGH_VERTEX_COUNT = 1_500_000;
	private static final String DEBUG_TEXT_COLOR = "#f2d84a";
	private static final String DEBUG_WARNING_COLOR = "#ff5656";
	private static final int DEFAULT_ANTIALIASING_SAMPLES = 4;
	private static final int MIN_FOV_DEGREES = 35;
	private static final int MAX_FOV_DEGREES = 100;
	private static final long DEFAULT_MAP_CACHE_BUDGET_BYTES = 512L * 1024L * 1024L;
	private static final long MINIMAP_CACHE_BUDGET_BYTES = 128L * 1024L * 1024L;
	private static final float WARP_CAMERA_HEIGHT_TILES = 22.0f;
	private static final long STATE_SAVE_INTERVAL_NANOS = 1_500_000_000L;

	private final TerrainRegionLoader loader = new TerrainRegionLoader();
	private final TerrainRenderer renderer = new TerrainRenderer();
	private final FreeCamera camera = new FreeCamera();
	private final Canvas canvas;
	private final JLayeredPane sceneLayer;
	private final Path cacheDirectory;
	private final Path atlasPath;
	private final long mapCacheBudgetBytes;
	private final ViewerSettings settings;
	private final Viewer3DState initialState;
	private MapViewerPlugin activePlugin;
	private Map3DLayer active3DLayer;
	private final int regionId;
	private final int originRegionX;
	private final int originRegionY;
	private final ExecutorService regionLoaderExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "3D region loader");
		thread.setDaemon(true);
		return thread;
	});
	private final Queue<Runnable> renderTasks = new ConcurrentLinkedQueue<>();
	private final Map<Integer, TerrainMesh> loadedMeshes = new LinkedHashMap<>();
	private final List<Integer> regionLoadQueue = new ArrayList<>();
	private final Set<Integer> loadedRegionIds = new HashSet<>();
	private final Set<Integer> queuedRegionIds = new HashSet<>();
	private final Set<Integer> loadingRegionIds = new HashSet<>();
	private final Set<Integer> failedRegionIds = new HashSet<>();
	private final JToggleButton lockCameraButton = new JToggleButton("Lock Camera");
	private final JToggleButton debugOverlayButton = new JToggleButton("Debug");
	private final JToggleButton viewControlsButton = new JToggleButton("View Controls");
	private final JToggleButton minimapButton = new JToggleButton("Hide Minimap");
	private final JToggleButton overlayPriorityButton = new JToggleButton();
	private final JComboBox<AntialiasingScale> antialiasingScale = new JComboBox<>(AntialiasingScale.values());
	private final JComboBox<ViewDistanceOption> viewDistanceSelect = new JComboBox<>(ViewDistanceOption.values());
	private final JSlider fovSlider = new JSlider(MIN_FOV_DEGREES, MAX_FOV_DEGREES, Math.round(camera.fovDegrees()));
	private final JLabel title = new JLabel("3D Region Viewer");
	private final JLabel detail = new JLabel("Loading terrain...");
	private final JLabel compassHud = new JLabel("Heading --");
	private final JLabel tileHud = new JLabel("Tile --");
	private final JLabel fovHud = new JLabel();
	private final JLabel debugOverlay = new JLabel();
	private final Map3DMinimapOverlay minimapOverlay;
	private final MapPanel areaSearchMapPanel;
	private final MapAreaSearchPanel areaSearchPanel;
	private final Map3DControlsOverlay controlsOverlay = new Map3DControlsOverlay();
	private final Timer renderTimer;
	private final Runnable exitAction;
	private volatile TerrainRegionLoader.Session loaderSession;
	private volatile Set<Integer> desiredRegionIds = Set.of();
	private TerrainScene currentScene;
	private HoveredTile hoveredTile;
	private AWTContext awtContext;
	private Point lastMousePoint;
	private boolean disposed;
	private boolean glContextReady;
	private boolean glContextFailed;
	private boolean middleMouseLook;
	private boolean cameraLocked;
	private boolean cameraInitialized;
	private boolean hoverPickErrorLogged;
	private int streamCenterRegionId;
	private int streamGridSize = Viewer3DState.DEFAULT_VIEW_DISTANCE_REGIONS;
	private boolean pluginOverlaysOnTop;
	private long lastFrameNanos;
	private long fpsWindowStartNanos;
	private int fpsWindowFrames;
	private double displayedFps;
	private double lastFrameMillis;
	private double lastRenderMillis;
	private int antialiasingSamples = DEFAULT_ANTIALIASING_SAMPLES;
	private boolean stateSavePending;
	private long lastStateSaveNanos;
	private Viewer3DState lastSavedState;
	private Map3DWorldMapDock worldMapDock;
	private boolean pluginPopupHandledDuringPressRelease;
	private Tile lastPluginOverlayCameraTile;
	private Set<Integer> lastPluginOverlayRegionIds = Set.of();
	private volatile boolean pluginOverlayDirty = true;

	public Map3DPanel(Path cacheDirectory, int regionId, Runnable exitAction)
	{
		this(cacheDirectory, regionId, installedAtlasPath(), DEFAULT_MAP_CACHE_BUDGET_BYTES, null, null, exitAction);
	}

	public Map3DPanel(Path cacheDirectory, int regionId, Path atlasPath, long mapCacheBudgetBytes,
	                  ViewerSettings settings, MapViewerPlugin activePlugin, Runnable exitAction)
	{
		super(new BorderLayout());
		this.cacheDirectory = cacheDirectory;
		this.atlasPath = atlasPath;
		this.mapCacheBudgetBytes = mapCacheBudgetBytes <= 0L ? DEFAULT_MAP_CACHE_BUDGET_BYTES : mapCacheBudgetBytes;
		this.settings = settings;
		this.initialState = settings == null ? null : settings.viewer3DState();
		this.lastSavedState = initialState;
		this.regionId = regionId;
		this.originRegionX = TerrainScene.regionX(regionId);
		this.originRegionY = TerrainScene.regionY(regionId);
		this.currentScene = TerrainScene.empty(originRegionX, originRegionY);
		this.streamCenterRegionId = regionId;
		this.exitAction = exitAction;
		setOpaque(true);
		setBackground(Color.BLACK);
		applyInitialPreferences();

		canvas = new Canvas();
		canvas.setBackground(Color.BLACK);
		canvas.setFocusable(true);
		canvas.setIgnoreRepaint(true);
		minimapOverlay = createMinimapOverlay();
		AreaSearch areaSearch = createAreaSearch();
		areaSearchMapPanel = areaSearch.mapPanel();
		areaSearchPanel = areaSearch.searchPanel();
		sceneLayer = buildSceneLayer();
		setActivePlugin(activePlugin);

		add(buildToolbar(), BorderLayout.NORTH);
		add(sceneLayer, BorderLayout.CENTER);
		JPanel footer = buildFooterPanel();
		if (footer != null)
		{
			add(footer, BorderLayout.SOUTH);
		}
		installInputHandlers();

		renderTimer = new Timer(FRAME_DELAY_MS, e -> renderFrame());
		renderTimer.start();
		startRegionStreaming();
	}

	public void dispose()
	{
		disposed = true;
		saveViewerStateNow();
		renderTimer.stop();
		regionLoaderExecutor.shutdownNow();
		closeLoaderSession();
		releaseLoadedMeshes();
		if (worldMapDock != null)
		{
			worldMapDock.dispose();
			worldMapDock = null;
		}
		if (minimapOverlay != null)
		{
			minimapOverlay.dispose();
		}
		if (areaSearchMapPanel != null)
		{
			areaSearchMapPanel.dispose();
		}
		try
		{
			if (renderer.isInitialized() && glContextReady && awtContext != null)
			{
				awtContext.makeCurrent();
				try
				{
					renderer.dispose();
				}
				finally
				{
					awtContext.detachCurrent();
				}
			}
		}
		catch (Throwable ex)
		{
			System.err.println("Failed to dispose 3D renderer: " + rootMessage(ex));
		}
		finally
		{
			destroyContext();
		}
	}

	public void setActivePlugin(MapViewerPlugin plugin)
	{
		activePlugin = plugin;
		active3DLayer = plugin instanceof Map3DLayer layer ? layer : null;
		controlsOverlay.setPluginControls(
			active3DLayer == null || plugin == null ? null : plugin.displayName(),
			active3DLayer == null ? List.of() : active3DLayer.controlHints()
		);
		requestPluginOverlayRefresh();
		if (worldMapDock != null)
		{
			worldMapDock.setPlugin(plugin);
		}
		if (minimapOverlay != null)
		{
			minimapOverlay.setPlugin(plugin);
		}
	}

	public void focusTile(Tile tile)
	{
		warpCameraToTile(tile);
	}

	public Tile getCameraTile()
	{
		return cameraTile();
	}

	public void repaintPluginViews()
	{
		requestPluginOverlayRefresh();
		if (worldMapDock != null)
		{
			if (SwingUtilities.isEventDispatchThread())
			{
				worldMapDock.repaintMap();
			}
			else
			{
				SwingUtilities.invokeLater(worldMapDock::repaintMap);
			}
		}
		if (minimapOverlay != null)
		{
			if (SwingUtilities.isEventDispatchThread())
			{
				minimapOverlay.repaint();
			}
			else
			{
				SwingUtilities.invokeLater(minimapOverlay::repaint);
			}
		}
	}

	public void invokeRenderLater(Runnable task)
	{
		if (task != null)
		{
			renderTasks.add(task);
		}
	}

	private JPanel buildToolbar()
	{
		JPanel toolbar = new JPanel(new BorderLayout(12, 0));
		toolbar.setPreferredSize(new Dimension(0, TOOLBAR_HEIGHT));
		toolbar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(55, 55, 55)),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)
		));

		title.setForeground(new Color(225, 225, 225));
		detail.setForeground(new Color(165, 165, 165));
		compassHud.setForeground(new Color(220, 220, 220));
		tileHud.setForeground(new Color(220, 220, 220));
		JPanel labels = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
		labels.setOpaque(false);
		labels.add(title);
		toolbar.add(labels, BorderLayout.WEST);

		styleToolbarButton(lockCameraButton);
		lockCameraButton.addActionListener(e -> cameraLocked = lockCameraButton.isSelected());
		styleToolbarButton(debugOverlayButton);
		debugOverlayButton.addActionListener(e -> {
			debugOverlay.setVisible(debugOverlayButton.isSelected());
			if (debugOverlayButton.isSelected())
			{
				updateDebugOverlay();
			}
			sceneLayer.repaint();
		});
		JLabel aaLabel = toolbarLabel("AA");
		antialiasingScale.setFocusable(false);
		antialiasingScale.addActionListener(e -> applyAntialiasingSelection());
		JLabel viewDistanceLabel = toolbarLabel("View");
		viewDistanceSelect.setFocusable(false);
		viewDistanceSelect.addActionListener(e -> applyViewDistanceSelection());
		styleToolbarButton(overlayPriorityButton);
		updateOverlayPriorityButtonText();
		overlayPriorityButton.addActionListener(e -> applyOverlayPrioritySelection());
		JLabel fovLabel = toolbarLabel("FOV");
		fovHud.setForeground(new Color(220, 220, 220));
		fovSlider.setOpaque(false);
		fovSlider.setFocusable(false);
		fovSlider.setPreferredSize(new Dimension(120, 28));
		fovSlider.addChangeListener(e -> {
			camera.setFovDegrees(fovSlider.getValue());
			updateFovHud();
			scheduleStateSave();
		});
		updateFovHud();
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		controls.setOpaque(false);
		controls.add(lockCameraButton);
		controls.add(debugOverlayButton);
		controls.add(aaLabel);
		controls.add(antialiasingScale);
		controls.add(viewDistanceLabel);
		controls.add(viewDistanceSelect);
		controls.add(overlayPriorityButton);
		controls.add(fovLabel);
		controls.add(fovSlider);
		controls.add(fovHud);
		controls.add(compassHud);
		controls.add(tileHud);
		toolbar.add(controls, BorderLayout.CENTER);

		styleToolbarButton(viewControlsButton);
		viewControlsButton.addActionListener(e -> {
			controlsOverlay.setVisible(viewControlsButton.isSelected());
			sceneLayer.revalidate();
			sceneLayer.doLayout();
			sceneLayer.repaint();
		});
		styleToolbarButton(minimapButton);
		minimapButton.setEnabled(minimapOverlay != null);
		minimapButton.addActionListener(e -> applyMinimapVisibilitySelection());
		JButton exit = new JButton("Return to 2D Map");
		styleToolbarButton(exit);
		exit.addActionListener(e -> {
			if (exitAction != null)
			{
				exitAction.run();
			}
		});
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
		actions.setOpaque(false);
		actions.add(viewControlsButton);
		actions.add(minimapButton);
		actions.add(exit);
		toolbar.add(actions, BorderLayout.EAST);
		return toolbar;
	}

	private JPanel buildFooterPanel()
	{
		if (areaSearchPanel == null)
		{
			return null;
		}
		JPanel searchHost = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		searchHost.add(areaSearchPanel);
		return searchHost;
	}

	private JLayeredPane buildSceneLayer()
	{
		debugOverlay.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
		debugOverlay.setForeground(Color.decode(DEBUG_TEXT_COLOR));
		debugOverlay.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		debugOverlay.setFocusable(false);
		debugOverlay.setVisible(false);

		JLayeredPane layeredPane = new JLayeredPane()
		{
			@Override
			public void doLayout()
			{
				Dimension size = getSize();
				canvas.setBounds(0, 0, size.width, size.height);
				int debugTop = 10;
				int rightAnchor = size.width - 12;
				if (minimapOverlay != null && minimapOverlay.isVisible())
				{
					Dimension minimapSize = minimapOverlay.getPreferredSize();
					int minimapX = Math.max(8, rightAnchor - minimapSize.width);
					minimapOverlay.setBounds(minimapX, 12, minimapSize.width, minimapSize.height);
					debugTop = minimapOverlay.getY() + minimapOverlay.getHeight() + 8;
				}
				if (controlsOverlay.isVisible())
				{
					Dimension controlsSize = controlsOverlay.getPreferredSize();
					int controlsX = Math.max(8, rightAnchor - controlsSize.width);
					controlsOverlay.setBounds(controlsX, debugTop, controlsSize.width, controlsSize.height);
					debugTop = controlsOverlay.getY() + controlsOverlay.getHeight() + 8;
				}
				Dimension preferredSize = debugOverlay.getPreferredSize();
				int x = Math.max(8, size.width - preferredSize.width - 10);
				debugOverlay.setBounds(x, debugTop, preferredSize.width, preferredSize.height);
			}
		};
		layeredPane.setOpaque(true);
		layeredPane.setBackground(Color.BLACK);
		layeredPane.add(canvas, JLayeredPane.DEFAULT_LAYER);
		if (minimapOverlay != null)
		{
			layeredPane.add(minimapOverlay, JLayeredPane.PALETTE_LAYER);
		}
		controlsOverlay.setVisible(false);
		layeredPane.add(controlsOverlay, JLayeredPane.PALETTE_LAYER);
		layeredPane.add(debugOverlay, JLayeredPane.PALETTE_LAYER);
		return layeredPane;
	}

	private AreaSearch createAreaSearch()
	{
		if (atlasPath == null)
		{
			return new AreaSearch(null, null);
		}
		try
		{
			MapPanel mapPanel = new MapPanel(atlasPath, minimapBudgetBytes(mapCacheBudgetBytes));
			MapAreaSearchPanel searchPanel = new MapAreaSearchPanel();
			searchPanel.setSearchProvider(mapPanel::searchMapAreas);
			searchPanel.onAreaSelected().addListener(this::warpCameraToArea);
			return new AreaSearch(mapPanel, searchPanel);
		}
		catch (RuntimeException ex)
		{
			System.err.println("Failed to initialize 3D area search: " + rootMessage(ex));
			return new AreaSearch(null, null);
		}
	}

	private void warpCameraToArea(MapArea area)
	{
		if (area == null)
		{
			return;
		}
		Tile tile = area.tile();
		warpCameraToTile(tile);
		detail.setText("Focused " + area.displayName() + " at " + tile.x + "," + tile.y + "," + tile.z);
	}

	private Map3DMinimapOverlay createMinimapOverlay()
	{
		if (atlasPath == null)
		{
			return null;
		}
		try
		{
			MapPanel minimapPanel = new MapPanel(atlasPath, minimapBudgetBytes(mapCacheBudgetBytes));
			minimapPanel.setShowMapFeatureTooltips(false);
			return new Map3DMinimapOverlay(minimapPanel, this::cameraMinimapCenter,
				this::cameraHeadingRadians, this::openWorldMapDock);
		}
		catch (RuntimeException ex)
		{
			System.err.println("Failed to initialize 3D minimap: " + rootMessage(ex));
			return null;
		}
	}

	private void applyInitialPreferences()
	{
		Viewer3DState state = initialState;
		if (state != null)
		{
			camera.setFovDegrees(state.fovDegrees());
			fovSlider.setValue(Math.round(camera.fovDegrees()));
			AntialiasingScale scale = AntialiasingScale.forSamples(state.antialiasingSamples());
			antialiasingSamples = scale.samples();
			antialiasingScale.setSelectedItem(scale);
			streamGridSize = Viewer3DState.clampViewDistanceRegions(state.viewDistanceRegions());
			viewDistanceSelect.setSelectedItem(ViewDistanceOption.forGridSize(streamGridSize));
			renderer.setViewDistanceRegions(streamGridSize);
			pluginOverlaysOnTop = state.pluginOverlaysOnTop();
			overlayPriorityButton.setSelected(pluginOverlaysOnTop);
			updateOverlayPriorityButtonText();
			renderer.setPluginOverlayOnTop(pluginOverlaysOnTop);
			return;
		}
		AntialiasingScale scale = AntialiasingScale.forSamples(DEFAULT_ANTIALIASING_SAMPLES);
		antialiasingSamples = scale.samples();
		antialiasingScale.setSelectedItem(scale);
		streamGridSize = Viewer3DState.DEFAULT_VIEW_DISTANCE_REGIONS;
		viewDistanceSelect.setSelectedItem(ViewDistanceOption.forGridSize(streamGridSize));
		renderer.setViewDistanceRegions(streamGridSize);
		pluginOverlaysOnTop = false;
		overlayPriorityButton.setSelected(false);
		updateOverlayPriorityButtonText();
		renderer.setPluginOverlayOnTop(false);
	}

	private void openWorldMapDock()
	{
		if (atlasPath == null)
		{
			detail.setText("World map atlas unavailable");
			return;
		}
		if (worldMapDock != null && worldMapDock.isDisplayable())
		{
			worldMapDock.open();
			return;
		}

		try
		{
			MapPanel worldMapPanel = new MapPanel(atlasPath, mapCacheBudgetBytes);
			worldMapDock = new Map3DWorldMapDock(sceneLayer, worldMapPanel, this::cameraTile, this::warpCameraToTile);
			worldMapDock.setPlugin(activePlugin);
			worldMapDock.addWindowListener(new WindowAdapter()
			{
				@Override
				public void windowClosed(WindowEvent e)
				{
					worldMapDock = null;
				}
			});
			worldMapDock.open();
		}
		catch (RuntimeException ex)
		{
			detail.setText("Failed to open world map: " + rootMessage(ex));
			System.err.println("Failed to open 3D world map dock: " + rootMessage(ex));
		}
	}

	private Map3DMinimapOverlay.Center cameraMinimapCenter()
	{
		if (!cameraInitialized)
		{
			return new Map3DMinimapOverlay.Center(
				originRegionX * (double) TerrainScene.REGION_SIZE + SceneScale.REGION_CENTER_TILES,
				originRegionY * (double) TerrainScene.REGION_SIZE + SceneScale.REGION_CENTER_TILES
			);
		}
		return new Map3DMinimapOverlay.Center(
			absoluteTileX(camera.position().x()),
			absoluteTileY(camera.position().z())
		);
	}

	private Tile cameraTile()
	{
		if (!cameraInitialized)
		{
			return new Tile(
				originRegionX * TerrainScene.REGION_SIZE + TerrainScene.REGION_SIZE / 2,
				originRegionY * TerrainScene.REGION_SIZE + TerrainScene.REGION_SIZE / 2,
				0
			);
		}
		int worldX = (int) Math.floor(
			camera.position().x() + originRegionX * TerrainScene.REGION_SIZE + SceneScale.REGION_CENTER_TILES
		);
		int worldY = (int) Math.floor(
			originRegionY * TerrainScene.REGION_SIZE + SceneScale.REGION_CENTER_TILES - camera.position().z()
		);
		return new Tile(worldX, worldY, currentCameraPlane(camera.position()));
	}

	private double cameraHeadingRadians()
	{
		Vector3f direction = camera.direction(new Vector3f());
		double heading = Math.atan2(direction.x, -direction.z);
		return heading < 0.0 ? heading + Math.PI * 2.0 : heading;
	}

	private void warpCameraToTile(Tile tile)
	{
		if (!isValidWorldTile(tile))
		{
			detail.setText("Tile outside atlas bounds");
			return;
		}

		int targetRegionId = regionIdForTile(tile);
		float worldX = tile.x - originRegionX * TerrainScene.REGION_SIZE - SceneScale.REGION_CENTER_TILES + 0.5f;
		float worldZ = originRegionY * TerrainScene.REGION_SIZE + SceneScale.REGION_CENTER_TILES - tile.y - 0.5f;
		float worldY = warpCameraHeight(tile, targetRegionId);
		camera.setPosition(new Vector3f(worldX, worldY, worldZ));
		if (!cameraInitialized)
		{
			cameraInitialized = true;
			title.setText("3D Region Stream");
		}
		streamCenterRegionId = targetRegionId;
		updateStreamedRegions();
		updateCompassHud();
		detail.setText("Warped to tile " + tile.x + "," + tile.y + "," + tile.z);
		saveViewerStateNow();
		if (minimapOverlay != null)
		{
			minimapOverlay.repaint();
		}
		canvas.requestFocusInWindow();
	}

	private float warpCameraHeight(Tile tile, int targetRegionId)
	{
		TerrainMesh mesh = loadedRegionIds.contains(targetRegionId) ? loadedMeshes.get(targetRegionId) : null;
		if (mesh == null)
		{
			return Math.max(camera.position().y(), WARP_CAMERA_HEIGHT_TILES);
		}

		float localX = Math.floorMod(tile.x, TerrainScene.REGION_SIZE) + 0.5f;
		float localY = Math.floorMod(tile.y, TerrainScene.REGION_SIZE) + 0.5f;
		int plane = Math.max(0, Math.min(3, tile.z));
		return mesh.worldHeightAt(plane, localX, localY) + WARP_CAMERA_HEIGHT_TILES;
	}

	private void initializeCamera(TerrainMesh mesh)
	{
		if (initialState != null)
		{
			camera.setPosition(new Vector3f(
				sceneWorldXFromAbsoluteTile(initialState.worldTileX()),
				(float) initialState.cameraY(),
				sceneWorldZFromAbsoluteTile(initialState.worldTileY())
			));
			camera.setOrientation(initialState.yawDegrees(), initialState.pitchDegrees());
			camera.setFovDegrees(initialState.fovDegrees());
			fovSlider.setValue(Math.round(camera.fovDegrees()));
		}
		else
		{
			camera.reset(mesh.initialCameraX(), mesh.initialCameraY(), mesh.initialCameraZ());
		}

		cameraInitialized = true;
		title.setText("3D Region Stream");
		saveViewerStateNow();
		canvas.requestFocusInWindow();
	}

	private Vector3f applyMovementConstraints(Vector3fc previous, Vector3fc attempted)
	{
		return currentScene.clampMovement(previous, attempted, new Vector3f());
	}

	private int currentCameraPlane(Vector3fc position)
	{
		if (currentScene.isEmpty())
		{
			return initialState == null ? 0 : Math.max(0, Math.min(3, initialState.plane()));
		}
		return currentScene.cameraPlaneFor(position.x(), position.y(), position.z());
	}

	private void maybeSaveViewerState(long now)
	{
		if (settings == null || !cameraInitialized || !stateSavePending)
		{
			return;
		}
		if (lastStateSaveNanos == 0L || now - lastStateSaveNanos >= STATE_SAVE_INTERVAL_NANOS)
		{
			saveViewerStateNow();
		}
	}

	private void scheduleStateSave()
	{
		if (settings != null)
		{
			stateSavePending = true;
		}
	}

	private void saveViewerStateNow()
	{
		if (settings == null || !cameraInitialized)
		{
			return;
		}
		Viewer3DState state = new Viewer3DState(
			absoluteTileX(camera.position().x()),
			absoluteTileY(camera.position().z()),
			camera.position().y(),
			currentCameraPlane(camera.position()),
			camera.yawDegrees(),
			camera.pitchDegrees(),
			camera.fovDegrees(),
			antialiasingSamples,
			streamGridSize,
			pluginOverlaysOnTop
		);
		if (!state.equals(lastSavedState))
		{
			settings.setViewer3DState(state);
			lastSavedState = state;
		}
		stateSavePending = false;
		lastStateSaveNanos = System.nanoTime();
	}

	private boolean cameraPositionChanged(Vector3fc previousPosition)
	{
		return Float.compare(previousPosition.x(), camera.position().x()) != 0
			|| Float.compare(previousPosition.y(), camera.position().y()) != 0
			|| Float.compare(previousPosition.z(), camera.position().z()) != 0;
	}

	private boolean cameraOrientationChanged(float previousYaw, float previousPitch)
	{
		return Float.compare(previousYaw, camera.yawDegrees()) != 0
			|| Float.compare(previousPitch, camera.pitchDegrees()) != 0;
	}

	private double absoluteTileX(float sceneWorldX)
	{
		return sceneWorldX + originRegionX * TerrainScene.REGION_SIZE + SceneScale.REGION_CENTER_TILES;
	}

	private double absoluteTileY(float sceneWorldZ)
	{
		return originRegionY * TerrainScene.REGION_SIZE + SceneScale.REGION_CENTER_TILES - sceneWorldZ;
	}

	private float sceneWorldXFromAbsoluteTile(double worldTileX)
	{
		return (float) (worldTileX - originRegionX * TerrainScene.REGION_SIZE - SceneScale.REGION_CENTER_TILES);
	}

	private float sceneWorldZFromAbsoluteTile(double worldTileY)
	{
		return (float) (originRegionY * TerrainScene.REGION_SIZE + SceneScale.REGION_CENTER_TILES - worldTileY);
	}

	private static boolean isValidWorldTile(Tile tile)
	{
		if (tile == null || tile.z < 0 || tile.z > 3)
		{
			return false;
		}
		int minX = Paths.MIN_RX * TerrainScene.REGION_SIZE;
		int maxX = (Paths.MAX_RX + 1) * TerrainScene.REGION_SIZE - 1;
		int minY = Paths.MIN_RY * TerrainScene.REGION_SIZE;
		int maxY = (Paths.MAX_RY + 1) * TerrainScene.REGION_SIZE - 1;
		return tile.x >= minX && tile.x <= maxX && tile.y >= minY && tile.y <= maxY;
	}

	private static int regionIdForTile(Tile tile)
	{
		return TerrainScene.regionId(
			Math.floorDiv(tile.x, TerrainScene.REGION_SIZE),
			Math.floorDiv(tile.y, TerrainScene.REGION_SIZE)
		);
	}

	private static long minimapBudgetBytes(long configuredBudget)
	{
		if (configuredBudget <= 0L || configuredBudget == Long.MAX_VALUE)
		{
			return MINIMAP_CACHE_BUDGET_BYTES;
		}
		return Math.max(16L * 1024L * 1024L, Math.min(configuredBudget, MINIMAP_CACHE_BUDGET_BYTES));
	}

	private static Path installedAtlasPath()
	{
		try
		{
			return AtlasStorage.ensureInstalledAtlas();
		}
		catch (IOException ex)
		{
			throw new IllegalStateException("Failed to prepare installed atlas: " + ex.getMessage(), ex);
		}
	}

	private void installInputHandlers()
	{
		canvas.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				setKey(e.getKeyCode(), true);
			}

			@Override
			public void keyReleased(KeyEvent e)
			{
				setKey(e.getKeyCode(), false);
			}
		});

		canvas.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (handlePluginClick(e))
				{
					return;
				}
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				canvas.requestFocusInWindow();
				updateHoveredTile(e);
				if (handlePluginPopupTrigger(e))
				{
					pluginPopupHandledDuringPressRelease = true;
					return;
				}
				if (e.getButton() == MouseEvent.BUTTON2)
				{
					middleMouseLook = true;
					lastMousePoint = e.getPoint();
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.isPopupTrigger() && pluginPopupHandledDuringPressRelease)
				{
					pluginPopupHandledDuringPressRelease = false;
					return;
				}
				if (handlePluginPopupTrigger(e))
				{
					pluginPopupHandledDuringPressRelease = true;
					return;
				}
				if (e.getButton() == MouseEvent.BUTTON2)
				{
					middleMouseLook = false;
					lastMousePoint = null;
				}
				updateHoveredTile(e);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hoveredTile = null;
				renderer.setHoveredTile(null);
				updateTileHud();
			}
		});

		canvas.addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				handleMouseMovement(e);
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				handleMouseMovement(e);
			}
		});
		canvas.addMouseWheelListener(this::handleMouseWheel);
	}

	private void setKey(int keyCode, boolean pressed)
	{
		switch (keyCode)
		{
			case KeyEvent.VK_W -> camera.setForward(pressed);
			case KeyEvent.VK_S -> camera.setBackward(pressed);
			case KeyEvent.VK_A -> camera.setLeft(pressed);
			case KeyEvent.VK_D -> camera.setRight(pressed);
			case KeyEvent.VK_PAGE_UP, KeyEvent.VK_SPACE -> camera.setRaise(pressed);
			case KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_CONTROL -> camera.setLower(pressed);
			case KeyEvent.VK_SHIFT -> camera.setFast(pressed);
			case KeyEvent.VK_LEFT -> camera.setTurnLeft(pressed);
			case KeyEvent.VK_RIGHT -> camera.setTurnRight(pressed);
			case KeyEvent.VK_UP -> camera.setLookUp(pressed);
			case KeyEvent.VK_DOWN -> camera.setLookDown(pressed);
			case KeyEvent.VK_ESCAPE ->
			{
				if (pressed)
				{
					if (exitAction != null)
					{
						exitAction.run();
					}
				}
			}
			default ->
			{
			}
		}
	}

	private void handleMouseMovement(MouseEvent event)
	{
		Point point = event.getPoint();
		if (middleMouseLook && !cameraLocked && lastMousePoint != null)
		{
			float previousYaw = camera.yawDegrees();
			float previousPitch = camera.pitchDegrees();
			camera.rotate(point.x - lastMousePoint.x, point.y - lastMousePoint.y);
			if (cameraOrientationChanged(previousYaw, previousPitch))
			{
				scheduleStateSave();
			}
		}
		updateHoveredTile(event);
		lastMousePoint = point;
	}

	private void handleMouseWheel(MouseWheelEvent event)
	{
		canvas.requestFocusInWindow();
		if (!cameraLocked)
		{
			Vector3f previousPosition = camera.copyPosition(new Vector3f());
			camera.zoom((float) event.getPreciseWheelRotation());
			if (!currentScene.isEmpty())
			{
				camera.setPosition(applyMovementConstraints(previousPosition, camera.position()));
			}
			scheduleStateSave();
			updateStreamedRegions();
		}
		updateHoveredTile(event);
	}

	private boolean handlePluginPopupTrigger(MouseEvent event)
	{
		if (active3DLayer == null || !event.isPopupTrigger() && !SwingUtilities.isRightMouseButton(event))
		{
			return false;
		}
		updateHoveredTile(event);
		Tile tile = hoveredTile();
		if (tile == null)
		{
			return false;
		}

		List<Map3DTileAction> actions = active3DLayer.tileActions(to3DMouseEvent(event, tile, true));
		if (actions == null || actions.isEmpty())
		{
			return false;
		}

		JPopupMenu popup = new JPopupMenu();
		for (Map3DTileAction action : actions)
		{
			if (action == null || action.label().isBlank())
			{
				continue;
			}
			JMenuItem item = new JMenuItem(action.label());
			item.addActionListener(e -> {
				action.run();
				repaintPluginViews();
				if (minimapOverlay != null)
				{
					minimapOverlay.repaint();
				}
			});
			popup.add(item);
		}
		if (popup.getComponentCount() == 0)
		{
			return false;
		}
		popup.show(canvas, event.getX(), event.getY());
		event.consume();
		return true;
	}

	private boolean handlePluginClick(MouseEvent event)
	{
		if (active3DLayer == null || !SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 1)
		{
			return false;
		}
		updateHoveredTile(event);
		Tile tile = hoveredTile();
		if (tile == null)
		{
			return false;
		}
		Tile warpTarget = active3DLayer.clickWarpTarget(to3DMouseEvent(event, tile, false));
		if (warpTarget == null)
		{
			return false;
		}
		warpCameraToTile(warpTarget);
		event.consume();
		return true;
	}

	private Map3DMouseEvent to3DMouseEvent(MouseEvent event, Tile tile, boolean popup)
	{
		return new Map3DMouseEvent(
			tile,
			event.getButton(),
			popup || event.isPopupTrigger(),
			event.isControlDown() || event.isMetaDown(),
			event.isShiftDown()
		);
	}

	private Tile hoveredTile()
	{
		if (hoveredTile == null)
		{
			return null;
		}
		return new Tile(hoveredTile.worldX(), hoveredTile.worldY(), hoveredTile.plane());
	}

	private void updateHoveredTile(MouseEvent event)
	{
		if (currentScene.isEmpty() || canvas.getWidth() <= 0 || canvas.getHeight() <= 0)
		{
			hoveredTile = null;
			renderer.setHoveredTile(null);
			updateTileHud();
			return;
		}
		try
		{
			AffineTransform transform = graphicsTransform();
			float screenX = (float) (event.getX() * transform.getScaleX());
			float screenY = (float) (event.getY() * transform.getScaleY());
			Vector3f rayDirection = camera.rayDirectionFromScreen(
				screenX,
				screenY,
				renderWidth(),
				renderHeight(),
				new Vector3f()
			);
			hoveredTile = currentScene.pickTile(camera.position(), rayDirection);
			renderer.setHoveredTile(hoveredTile);
			updateTileHud();
		}
		catch (RuntimeException ex)
		{
			hoveredTile = null;
			renderer.setHoveredTile(null);
			updateTileHud();
			if (!hoverPickErrorLogged)
			{
				hoverPickErrorLogged = true;
				System.err.println("Failed to update hovered 3D tile: " + rootMessage(ex));
			}
		}
	}

	private void updateCompassHud()
	{
		Vector3f direction = camera.direction(new Vector3f());
		float degrees = (float) Math.toDegrees(Math.atan2(direction.x, -direction.z));
		if (degrees < 0.0f)
		{
			degrees += 360.0f;
		}
		compassHud.setText("Heading " + cardinal(degrees) + " " + Math.round(degrees) + " deg");
	}

	private void updateTileHud()
	{
		if (hoveredTile == null)
		{
			tileHud.setText("Tile --");
			return;
		}
		tileHud.setText(
			"Tile " + hoveredTile.worldX() + "," + hoveredTile.worldY() + "," + hoveredTile.plane()
		);
	}

	private void updateFovHud()
	{
		fovHud.setText(Math.round(camera.fovDegrees()) + " deg");
	}

	private void applyAntialiasingSelection()
	{
		Object selected = antialiasingScale.getSelectedItem();
		if (!(selected instanceof AntialiasingScale scale) || scale.samples() == antialiasingSamples)
		{
			return;
		}

		antialiasingSamples = scale.samples();
		renderer.setAntialiasingSamples(antialiasingSamples);
		detail.setText("AA " + scale + " selected");
		saveViewerStateNow();
	}

	private void applyViewDistanceSelection()
	{
		Object selected = viewDistanceSelect.getSelectedItem();
		if (!(selected instanceof ViewDistanceOption option) || option.gridSize() == streamGridSize)
		{
			return;
		}

		streamGridSize = option.gridSize();
		renderer.setViewDistanceRegions(streamGridSize);
		updateStreamedRegions();
		requestPluginOverlayRefresh();
		detail.setText("3D view distance " + option);
		saveViewerStateNow();
	}

	private void applyOverlayPrioritySelection()
	{
		pluginOverlaysOnTop = overlayPriorityButton.isSelected();
		updateOverlayPriorityButtonText();
		renderer.setPluginOverlayOnTop(pluginOverlaysOnTop);
		detail.setText(pluginOverlaysOnTop ? "3D overlays forced to front" : "3D overlays depth-tested");
		saveViewerStateNow();
	}

	private void updateOverlayPriorityButtonText()
	{
		overlayPriorityButton.setText(pluginOverlaysOnTop ? "Overlays Front" : "Overlays Occluded");
	}

	private void applyMinimapVisibilitySelection()
	{
		if (minimapOverlay == null)
		{
			return;
		}
		boolean hidden = minimapButton.isSelected();
		minimapOverlay.setVisible(!hidden);
		minimapButton.setText(hidden ? "Show Minimap" : "Hide Minimap");
		sceneLayer.revalidate();
		sceneLayer.doLayout();
		sceneLayer.repaint();
	}

	private static String cardinal(float degrees)
	{
		String[] names = new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		int index = Math.floorMod(Math.round(degrees / 45.0f), names.length);
		return names[index];
	}

	private void startRegionStreaming()
	{
		hoveredTile = null;
		renderer.setHoveredTile(null);
		renderer.setScene(currentScene);
		updateTileHud();
		detail.setText("Loading region " + regionId + "...");
		desiredRegionIds = Set.of(regionId);
		loadedMeshes.put(regionId, TerrainMesh.empty(regionId));
		rebuildScene();
		queueRegion(regionId);
		pumpRegionQueue();
	}

	private void queueRegion(int requestedRegionId)
	{
		if (loadedRegionIds.contains(requestedRegionId)
			|| queuedRegionIds.contains(requestedRegionId)
			|| loadingRegionIds.contains(requestedRegionId)
			|| failedRegionIds.contains(requestedRegionId))
		{
			return;
		}

		queuedRegionIds.add(requestedRegionId);
		regionLoadQueue.add(requestedRegionId);
		updateStreamStatus();
	}

	private void pumpRegionQueue()
	{
		if (disposed || !loadingRegionIds.isEmpty())
		{
			return;
		}

		regionLoadQueue.removeIf(region -> !isRegionDesired(region));
		queuedRegionIds.retainAll(regionLoadQueue);
		if (regionLoadQueue.isEmpty())
		{
			updateStreamStatus();
			return;
		}

		int requestedRegionId = regionLoadQueue.remove(0);
		queuedRegionIds.remove(requestedRegionId);
		loadingRegionIds.add(requestedRegionId);
		updateStreamStatus();
		regionLoaderExecutor.execute(() -> {
			if (!isRegionDesired(requestedRegionId))
			{
				SwingUtilities.invokeLater(() -> cancelRegionRequest(requestedRegionId));
				return;
			}

			TerrainMesh mesh = null;
			Throwable failure = null;
			try
			{
				mesh = loaderSession().loadRegion(requestedRegionId);
			}
			catch (IOException ex)
			{
				if (isMissingRegion(ex))
				{
					mesh = TerrainMesh.empty(requestedRegionId);
				}
				else
				{
					failure = ex;
				}
			}
			catch (RuntimeException ex)
			{
				failure = ex;
			}
			catch (OutOfMemoryError ex)
			{
				failure = ex;
			}

			TerrainMesh loadedMesh = mesh;
			Throwable loadFailure = failure;
			SwingUtilities.invokeLater(() -> finishRegionRequest(requestedRegionId, loadedMesh, loadFailure));
		});
	}

	private void prioritizeRegionQueue(List<Integer> desiredRegions)
	{
		regionLoadQueue.removeIf(region -> !desiredRegionIds.contains(region));
		queuedRegionIds.retainAll(regionLoadQueue);
		regionLoadQueue.sort(Comparator.comparingInt(region -> desiredIndex(desiredRegions, region)));
	}

	private boolean isRegionDesired(int requestedRegionId)
	{
		return desiredRegionIds.contains(requestedRegionId);
	}

	private TerrainRegionLoader.Session loaderSession() throws IOException
	{
		TerrainRegionLoader.Session session = loaderSession;
		if (session == null)
		{
			session = loader.open(cacheDirectory);
			loaderSession = session;
		}
		return session;
	}

	private void finishRegionRequest(int requestedRegionId, TerrainMesh mesh, Throwable failure)
	{
		if (disposed)
		{
			return;
		}

		loadingRegionIds.remove(requestedRegionId);
		if (failure != null)
		{
			failedRegionIds.add(requestedRegionId);
			System.err.println("Failed to load 3D region " + requestedRegionId + ": " + rootMessage(failure));
			if (!cameraInitialized && requestedRegionId == regionId)
			{
				detail.setText("Failed to load center region: " + rootMessage(failure));
				return;
			}
			updateStreamStatus();
			pumpRegionQueue();
			return;
		}

		if (!isRegionDesired(requestedRegionId))
		{
			if (mesh != null)
			{
				mesh.releaseVertexData();
			}
			updateStreamStatus();
			pumpRegionQueue();
			return;
		}

		loadedMeshes.put(requestedRegionId, mesh);
		loadedRegionIds.add(requestedRegionId);
		if (!cameraInitialized && requestedRegionId == regionId)
		{
			initializeCamera(mesh);
		}
		rebuildScene();
		updateStreamedRegions();
		updateStreamStatus();
		pumpRegionQueue();
	}

	private void cancelRegionRequest(int requestedRegionId)
	{
		if (disposed)
		{
			return;
		}
		loadingRegionIds.remove(requestedRegionId);
		updateStreamStatus();
		pumpRegionQueue();
	}

	private void rebuildScene()
	{
		currentScene = new TerrainScene(originRegionX, originRegionY, loadedMeshes);
		renderer.setScene(currentScene);
		requestPluginOverlayRefresh();
	}

	private void updateStreamedRegions()
	{
		if (!cameraInitialized || currentScene.isEmpty())
		{
			return;
		}

		int nextCenter = currentScene.regionIdForWorld(camera.position());
		if (nextCenter != streamCenterRegionId)
		{
			streamCenterRegionId = nextCenter;
		}

		List<Integer> desiredRegions = desiredRegionIds(streamCenterRegionId, cameraTile());
		Set<Integer> desiredRegionSet = Set.copyOf(desiredRegions);
		boolean desiredRegionsChanged = !desiredRegionSet.equals(desiredRegionIds);
		desiredRegionIds = desiredRegionSet;
		boolean sceneChanged = loadedMeshes.entrySet().removeIf(entry -> {
			if (desiredRegionSet.contains(entry.getKey()))
			{
				return false;
			}
			entry.getValue().releaseVertexData();
			return true;
		});
		loadedRegionIds.removeIf(region -> !desiredRegionSet.contains(region));
		failedRegionIds.removeIf(region -> !desiredRegionSet.contains(region));
		regionLoadQueue.removeIf(region -> !desiredRegionSet.contains(region));
		queuedRegionIds.retainAll(regionLoadQueue);

		for (int desiredRegionId : desiredRegions)
		{
			if (!loadedMeshes.containsKey(desiredRegionId))
			{
				loadedMeshes.put(desiredRegionId, TerrainMesh.empty(desiredRegionId));
				sceneChanged = true;
			}
		}

		for (int desiredRegionId : desiredRegions)
		{
			queueRegion(desiredRegionId);
		}
		prioritizeRegionQueue(desiredRegions);
		if (sceneChanged)
		{
			rebuildScene();
		}
		else if (desiredRegionsChanged)
		{
			requestPluginOverlayRefresh();
		}
		pumpRegionQueue();
		updateStreamStatus();
	}

	private List<Integer> desiredRegionIds(int centerRegionId, Tile centerTile)
	{
		int centerX = TerrainScene.regionX(centerRegionId);
		int centerY = TerrainScene.regionY(centerRegionId);
		List<Integer> regions = new ArrayList<>();
		int startX = streamStartOffset(streamGridSize,
			centerTile == null ? 0 : Math.floorMod(centerTile.x, TerrainScene.REGION_SIZE));
		int startY = streamStartOffset(streamGridSize,
			centerTile == null ? 0 : Math.floorMod(centerTile.y, TerrainScene.REGION_SIZE));
		for (int dx = startX; dx < startX + streamGridSize; dx++)
		{
			for (int dy = startY; dy < startY + streamGridSize; dy++)
			{
				regions.add(TerrainScene.regionId(centerX + dx, centerY + dy));
			}
		}
		regions.sort(Comparator
			.comparingInt((Integer id) -> chebyshevDistance(centerX, centerY, id))
			.thenComparingInt(id -> manhattanDistance(centerX, centerY, id)));
		return regions;
	}

	private static int streamStartOffset(int gridSize, int localTile)
	{
		int clampedGridSize = Viewer3DState.clampViewDistanceRegions(gridSize);
		if ((clampedGridSize & 1) == 1)
		{
			return -clampedGridSize / 2;
		}
		return localTile >= TerrainScene.REGION_SIZE / 2
			? -(clampedGridSize / 2 - 1)
			: -clampedGridSize / 2;
	}

	private void updateStreamStatus()
	{
		if (!cameraInitialized)
		{
			detail.setText("Loading center region " + regionId + "...");
			return;
		}

		detail.setText("3D view distance " + streamGridSize + "x" + streamGridSize);
	}

	private void closeLoaderSession()
	{
		TerrainRegionLoader.Session session = loaderSession;
		loaderSession = null;
		if (session == null)
		{
			return;
		}
		try
		{
			session.close();
		}
		catch (IOException ex)
		{
			System.err.println("Failed to close 3D cache session: " + rootMessage(ex));
		}
	}

	private static boolean isMissingRegion(IOException ex)
	{
		String message = ex.getMessage();
		return message != null && message.contains("was not found in the cache");
	}

	private void releaseLoadedMeshes()
	{
		for (TerrainMesh mesh : loadedMeshes.values())
		{
			mesh.releaseVertexData();
		}
		loadedMeshes.clear();
	}

	private Map3DOverlay currentPluginOverlay()
	{
		if (active3DLayer == null)
		{
			return Map3DOverlay.empty();
		}
		Map3DOverlay overlay = active3DLayer.overlay(new Map3DRenderContext(cameraTile(), List.copyOf(desiredRegionIds)));
		return overlay == null ? Map3DOverlay.empty() : overlay;
	}

	private void requestPluginOverlayRefresh()
	{
		pluginOverlayDirty = true;
	}

	private void updatePluginOverlayContext()
	{
		Tile cameraTile = cameraTile();
		Set<Integer> regionIds = desiredRegionIds;
		if (!cameraTile.equals(lastPluginOverlayCameraTile) || !regionIds.equals(lastPluginOverlayRegionIds))
		{
			lastPluginOverlayCameraTile = new Tile(cameraTile.x, cameraTile.y, cameraTile.z);
			lastPluginOverlayRegionIds = Set.copyOf(regionIds);
			requestPluginOverlayRefresh();
		}
	}

	private void processRenderTasks()
	{
		Runnable task;
		while ((task = renderTasks.poll()) != null)
		{
			try
			{
				task.run();
			}
			catch (RuntimeException ex)
			{
				System.err.println("3D render task failed: " + rootMessage(ex));
			}
		}
	}

	private void refreshPluginOverlayIfNeeded()
	{
		if (!pluginOverlayDirty)
		{
			return;
		}
		pluginOverlayDirty = false;
		try
		{
			renderer.setPluginOverlay(currentPluginOverlay());
		}
		catch (RuntimeException ex)
		{
			renderer.setPluginOverlay(Map3DOverlay.empty());
			System.err.println("3D plugin overlay failed: " + rootMessage(ex));
		}
	}

	private static int chebyshevDistance(int centerX, int centerY, int regionId)
	{
		return Math.max(
			Math.abs(TerrainScene.regionX(regionId) - centerX),
			Math.abs(TerrainScene.regionY(regionId) - centerY)
		);
	}

	private static int manhattanDistance(int centerX, int centerY, int regionId)
	{
		return Math.abs(TerrainScene.regionX(regionId) - centerX)
			+ Math.abs(TerrainScene.regionY(regionId) - centerY);
	}

	private static int desiredIndex(List<Integer> desiredRegions, int regionId)
	{
		int index = desiredRegions.indexOf(regionId);
		return index < 0 ? Integer.MAX_VALUE : index;
	}

	private void renderFrame()
	{
		if (disposed || !canvas.isDisplayable())
		{
			return;
		}
		long now = System.nanoTime();
		if (lastFrameNanos == 0L)
		{
			lastFrameNanos = now;
		}
		long elapsedFrameNanos = now - lastFrameNanos;
		float deltaSeconds = Math.min(0.05f, elapsedFrameNanos / 1_000_000_000.0f);
		lastFrameNanos = now;
		updateFrameTiming(now, elapsedFrameNanos);
		Vector3f previousPosition = camera.copyPosition(new Vector3f());
		float previousYaw = camera.yawDegrees();
		float previousPitch = camera.pitchDegrees();
		if (!cameraLocked)
		{
			camera.update(deltaSeconds);
			if (!currentScene.isEmpty())
			{
				camera.setPosition(applyMovementConstraints(previousPosition, camera.position()));
			}
			boolean positionChanged = cameraPositionChanged(previousPosition);
			boolean orientationChanged = cameraOrientationChanged(previousYaw, previousPitch);
			if (positionChanged || orientationChanged)
			{
				scheduleStateSave();
				renderer.invalidatePluginOverlayGeometry();
			}
		}
		updateStreamedRegions();
		updatePluginOverlayContext();
		updateCompassHud();
		maybeSaveViewerState(now);
		if (minimapOverlay != null)
		{
			minimapOverlay.repaint();
		}
		if (!ensureContext())
		{
			updateDebugOverlay();
			return;
		}
		long renderStartNanos = System.nanoTime();
		boolean rendered = false;
		try
		{
			awtContext.makeCurrent();
			GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, awtContext.getFramebuffer(false));
			processRenderTasks();
			refreshPluginOverlayIfNeeded();
			renderer.render(camera, renderWidth(), renderHeight());
			awtContext.swapBuffers();
			rendered = true;
		}
		catch (Throwable ex)
		{
			String message = rootMessage(ex);
			renderTimer.stop();
			detail.setText("OpenGL render error: " + message);
			System.err.println("OpenGL render error: " + message);
		}
		finally
		{
			if (glContextReady && awtContext != null)
			{
				try
				{
					awtContext.detachCurrent();
				}
				catch (Throwable ignored)
				{
				}
			}
		}
		if (rendered)
		{
			lastRenderMillis = (System.nanoTime() - renderStartNanos) / 1_000_000.0;
			updateDebugOverlay();
		}
	}

	private void updateFrameTiming(long now, long elapsedFrameNanos)
	{
		lastFrameMillis = elapsedFrameNanos / 1_000_000.0;
		if (elapsedFrameNanos > 0L && displayedFps == 0.0)
		{
			displayedFps = 1_000_000_000.0 / elapsedFrameNanos;
		}
		if (fpsWindowStartNanos == 0L)
		{
			fpsWindowStartNanos = now;
		}
		fpsWindowFrames++;
		long elapsedNanos = now - fpsWindowStartNanos;
		if (elapsedNanos >= 1_000_000_000L)
		{
			displayedFps = fpsWindowFrames * 1_000_000_000.0 / elapsedNanos;
			fpsWindowFrames = 0;
			fpsWindowStartNanos = now;
		}
	}

	private void updateDebugOverlay()
	{
		if (!debugOverlayButton.isSelected())
		{
			return;
		}

		TerrainRenderStats stats = renderer.renderStats();
		boolean primeRequested = isNvidiaPrimeRequested();
		boolean gpuWarning = primeRequested
			&& !containsIgnoreCase(stats.glRenderer(), "nvidia")
			&& !containsIgnoreCase(stats.glVendor(), "nvidia");
		boolean loadingWarning = cameraInitialized
			&& desiredRegionIds.size() > 0
			&& loadingRegionIds.size() + regionLoadQueue.size() > Math.max(4, desiredRegionIds.size() / 2);

		StringBuilder html = new StringBuilder(512);
		html.append("<html><div align=\"right\">");
		html.append(debugLine("FPS", fpsText(), displayedFps > 0.0 && displayedFps < 30.0));
		html.append(debugLine("Frame", millisText(lastFrameMillis), lastFrameMillis > SLOW_FRAME_MILLIS));
		html.append(debugLine("Render", millisText(lastRenderMillis), lastRenderMillis > SLOW_RENDER_MILLIS));
		html.append(debugLine("GPU", stats.glRenderer(), gpuWarning));
		html.append(debugLine("Vendor", stats.glVendor(), gpuWarning));
		html.append(debugLine("GL", stats.glVersion(), false));
		html.append(debugLine("GL draws", Integer.toString(stats.drawCalls()), stats.drawCalls() > HIGH_DRAW_CALLS));
		html.append(debugLine(
			"Vertices",
			String.format(Locale.ROOT, "%,d", stats.verticesDrawn()),
			stats.verticesDrawn() > HIGH_VERTEX_COUNT
		));
		html.append(debugLine(
			"Regions",
			loadedRegionIds.size() + "/" + desiredRegionIds.size() + " loaded, "
				+ loadingRegionIds.size() + " loading, " + regionLoadQueue.size() + " queued",
			loadingWarning
		));
		html.append(debugLine(
			"Visible/Culled",
			stats.visibleRegions() + "/" + stats.culledRegions(),
			false
		));
		html.append(debugLine(
			"Uploaded/Tex",
			stats.uploadedRegions() + " uploaded, " + stats.pendingUploads() + " pending, " + stats.textureLayers() + " tex",
			renderer.isInitialized() && stats.textureLayers() <= 0
		));
		html.append(debugLine("PRIME", primeRequested ? "requested" : "not requested", false));
		html.append("</div></html>");
		debugOverlay.setText(html.toString());
		sceneLayer.revalidate();
		sceneLayer.doLayout();
		sceneLayer.repaint(debugOverlay.getBounds());
	}

	private String fpsText()
	{
		return displayedFps <= 0.0 ? "--" : String.format(Locale.ROOT, "%.1f", displayedFps);
	}

	private static String millisText(double millis)
	{
		return String.format(Locale.ROOT, "%.1f ms", millis);
	}

	private static String debugLine(String label, String value, boolean warning)
	{
		return "<font color=\"" + DEBUG_TEXT_COLOR + "\">"
			+ escapeHtml(label)
			+ " </font>"
			+ debugValue(value, warning)
			+ "<br>";
	}

	private static String debugValue(String value, boolean warning)
	{
		String color = warning ? DEBUG_WARNING_COLOR : DEBUG_TEXT_COLOR;
		return "<font color=\"" + color + "\">" + escapeHtml(value) + "</font>";
	}

	private static boolean isNvidiaPrimeRequested()
	{
		return "1".equals(System.getenv("__NV_PRIME_RENDER_OFFLOAD"))
			|| "NVIDIA_only".equalsIgnoreCase(System.getenv("__VK_LAYER_NV_optimus"))
			|| "nvidia".equalsIgnoreCase(System.getenv("__GLX_VENDOR_LIBRARY_NAME"));
	}

	private static boolean containsIgnoreCase(String value, String needle)
	{
		return value != null && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
	}

	private static String escapeHtml(String value)
	{
		if (value == null)
		{
			return "";
		}
		return value
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
	}

	private boolean ensureContext()
	{
		if (glContextReady)
		{
			return true;
		}
		if (glContextFailed || !canvas.isDisplayable())
		{
			return false;
		}

		try
		{
			AWTContext.loadNatives();
			synchronized (canvas.getTreeLock())
			{
				if (!canvas.isDisplayable())
				{
					return false;
				}
				awtContext = new AWTContext(canvas);
				awtContext.configurePixelFormat(0, 0, 0);
			}
			awtContext.createGLContext();
			awtContext.makeCurrent();
			try
			{
				renderer.setAntialiasingSamples(antialiasingSamples);
				renderer.init();
				awtContext.setSwapInterval(1);
			}
			finally
			{
				awtContext.detachCurrent();
			}
			glContextReady = true;
			return true;
		}
		catch (Throwable ex)
		{
			if (antialiasingSamples > 0)
			{
				System.err.println(
					"OpenGL context error with " + antialiasingSamples + "x AA: " + rootMessage(ex)
						+ ". Retrying without antialiasing."
				);
				antialiasingSamples = 0;
				antialiasingScale.setSelectedItem(AntialiasingScale.OFF);
				destroyContext();
				glContextFailed = false;
				return ensureContext();
			}
			glContextFailed = true;
			String message = rootMessage(ex);
			detail.setText("OpenGL context error: " + message);
			System.err.println("OpenGL context error: " + message);
			destroyContext();
			return false;
		}
	}

	private int renderWidth()
	{
		AffineTransform transform = graphicsTransform();
		return Math.max(1, (int) Math.round(canvas.getWidth() * transform.getScaleX()));
	}

	private int renderHeight()
	{
		AffineTransform transform = graphicsTransform();
		return Math.max(1, (int) Math.round(canvas.getHeight() * transform.getScaleY()));
	}

	private AffineTransform graphicsTransform()
	{
		GraphicsConfiguration configuration = canvas.getGraphicsConfiguration();
		return configuration == null ? new AffineTransform() : configuration.getDefaultTransform();
	}

	private void destroyContext()
	{
		if (awtContext == null)
		{
			return;
		}
		try
		{
			awtContext.destroy();
		}
		catch (Throwable ex)
		{
			System.err.println("Failed to destroy 3D AWT context: " + rootMessage(ex));
		}
		finally
		{
			awtContext = null;
			glContextReady = false;
		}
	}

	private void resetOpenGLContext(String status)
	{
		if (!glContextReady || awtContext == null)
		{
			glContextFailed = false;
			return;
		}

		try
		{
			awtContext.makeCurrent();
			try
			{
				renderer.dispose();
			}
			finally
			{
				awtContext.detachCurrent();
			}
		}
		catch (Throwable ex)
		{
			System.err.println("Failed to reset 3D renderer: " + rootMessage(ex));
		}
		finally
		{
			destroyContext();
			glContextFailed = false;
			renderer.setScene(currentScene);
			detail.setText(status);
		}
	}

	private static String rootMessage(Throwable throwable)
	{
		Throwable current = throwable;
		while (current.getCause() != null)
		{
			current = current.getCause();
		}
		String message = current.getMessage();
		return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
	}

	private static void styleToolbarButton(AbstractButton button)
	{
		button.setFocusable(false);
		button.setMargin(new Insets(4, 10, 4, 10));
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(80, 80, 80)),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)
		));
	}

	private static JLabel toolbarLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(new Color(210, 210, 210));
		return label;
	}

	private record AreaSearch(
		MapPanel mapPanel,
		MapAreaSearchPanel searchPanel
	)
	{
	}

	private enum ViewDistanceOption
	{
		X2("2x2", 2),
		X3("3x3", 3),
		X4("4x4", 4),
		X5("5x5", 5);

		private final String label;
		private final int gridSize;

		ViewDistanceOption(String label, int gridSize)
		{
			this.label = label;
			this.gridSize = gridSize;
		}

		int gridSize()
		{
			return gridSize;
		}

		static ViewDistanceOption forGridSize(int gridSize)
		{
			int clampedGridSize = Viewer3DState.clampViewDistanceRegions(gridSize);
			for (ViewDistanceOption option : values())
			{
				if (option.gridSize == clampedGridSize)
				{
					return option;
				}
			}
			return X3;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private enum AntialiasingScale
	{
		OFF("Off", 0),
		X2("2x", 2),
		X4("4x", 4),
		X8("8x", 8),
		X16("16x", 16);

		private final String label;
		private final int samples;

		AntialiasingScale(String label, int samples)
		{
			this.label = label;
			this.samples = samples;
		}

		int samples()
		{
			return samples;
		}

		static AntialiasingScale forSamples(int samples)
		{
			for (AntialiasingScale scale : values())
			{
				if (scale.samples == samples)
				{
					return scale;
				}
			}
			return X4;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}
