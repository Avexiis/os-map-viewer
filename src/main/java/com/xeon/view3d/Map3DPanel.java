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
import com.xeon.util.NumberField;
import com.xeon.view.MapAreaSearchPanel;
import com.xeon.view.MapPanel;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLayeredPane;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import net.runelite.rlawt.AWTContext;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.opengl.GL33C;

public final class Map3DPanel extends JPanel
{
	public static final String CACHE_LOAD_FAILURE_PREFIX = "CACHE_LOAD_FAILURE:";

	private static final int FRAME_DELAY_MS = 16;
	private static final int CONTROLS_EXPANDED_WIDTH = 330;
	private static final int CONTROLS_COLLAPSED_SIZE = 44;
	private static final int CONTROLS_MINIMAP_GAP = 16;
	private static final int CONTROLS_INNER_WIDTH = 308;
	private static final int CONTROLS_ACTION_BUTTON_WIDTH = 102;
	private static final int CONTROLS_ACTION_GRID_GAP = 24;
	private static final int CONTROLS_ACTION_GRID_WIDTH = CONTROLS_ACTION_BUTTON_WIDTH * 2 + CONTROLS_ACTION_GRID_GAP;
	private static final int CONTROLS_FULL_BUTTON_WIDTH = 252;
	private static final int CONTROLS_BOTTOM_PADDING = 10;
	private static final int PLANE_COUNT = 4;
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
	private static final long WIKISYNC_COMBAT_REFRESH_NANOS = 2_000_000_000L;
	private static final float STREAM_PRIORITY_MOVEMENT_EPSILON = 0.05f;
	private static final double STREAM_PRIORITY_DIRECT_DOT = 0.60;
	private static final double STREAM_PRIORITY_FORWARD_DOT = 0.10;
	private static final double STREAM_PRIORITY_SIDE_DOT = -0.35;

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
	private final Tile initialFocusTile;
	private MapViewerPlugin activePlugin;
	private Map3DLayer active3DLayer;
	private final int regionId;
	private final int initialLoadRegionId;
	private final int originRegionX;
	private final int originRegionY;
	private final ExecutorService regionLoaderExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "3D region loader");
		thread.setDaemon(true);
		thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
		return thread;
	});
	private final Queue<Runnable> renderTasks = new ConcurrentLinkedQueue<>();
	private final Map<Integer, TerrainMesh> loadedMeshes = new LinkedHashMap<>();
	private final List<Integer> regionLoadQueue = new ArrayList<>();
	private final Set<Integer> loadedRegionIds = new HashSet<>();
	private final Set<Integer> queuedRegionIds = new HashSet<>();
	private final Set<Integer> loadingRegionIds = new HashSet<>();
	private final Set<Integer> failedRegionIds = new HashSet<>();
	private final Set<Integer> npcReloadAfterLoadRegionIds = new HashSet<>();
	private final JToggleButton lockCameraButton = new JToggleButton("Lock Camera");
	private final JToggleButton debugOverlayButton = new JToggleButton("Debug");
	private final JToggleButton viewControlsButton = new JToggleButton("View Controls");
	private final JToggleButton minimapButton = new JToggleButton("Hide Minimap");
	private final JToggleButton overlayPriorityButton = new JToggleButton();
	private final JCheckBox npcVisibleCheckBox = new JCheckBox("NPCs", true);
	private final JCheckBox npcOutlinesCheckBox = new JCheckBox("Outlines", true);
	private final JCheckBox npcHoverTextCheckBox = new JCheckBox("Hover Text", true);
	private final JCheckBox npcWikiSyncCombatColorsCheckBox = new JCheckBox("WikiSync Level Colors", false);
	private final JButton npcBrowserButton = new JButton("Browse NPCs");
	private final Component npcBrowserSpacer = Box.createVerticalStrut(6);
	private final Component controlsBottomSpacer = Box.createVerticalStrut(CONTROLS_BOTTOM_PADDING);
	private final JButton npcOutlineColorButton = new JButton("Outline Color");
	private final JButton tileHoverColorButton = new JButton("Hover Color");
	private final JCheckBox[] planeVisibleCheckBoxes = new JCheckBox[]{
		new JCheckBox("0", true),
		new JCheckBox("1", false),
		new JCheckBox("2", false),
		new JCheckBox("3", false)
	};
	private final NumberField jumpRegionId = new NumberField(6);
	private final NumberField jumpRegionIdPlane = new NumberField(2);
	private final NumberField jumpRegionX = new NumberField(4);
	private final NumberField jumpRegionY = new NumberField(4);
	private final NumberField jumpRegionPlane = new NumberField(2);
	private final NumberField jumpTileX = new NumberField(6);
	private final NumberField jumpTileY = new NumberField(6);
	private final NumberField jumpTilePlane = new NumberField(2);
	private final JButton jumpRegionIdButton = new JButton("Jump");
	private final JButton jumpRegionButton = new JButton("Jump");
	private final JButton jumpTileButton = new JButton("Jump");
	private final JButton collapseControlsButton = new JButton(">");
	private final JComboBox<AntialiasingScale> antialiasingScale = new JComboBox<>(AntialiasingScale.values());
	private final JComboBox<ViewDistanceOption> viewDistanceSelect = new JComboBox<>(ViewDistanceOption.values());
	private final JSlider fovSlider = new JSlider(MIN_FOV_DEGREES, MAX_FOV_DEGREES, Math.round(camera.fovDegrees()));
	private final JLabel title = new JLabel("3D Region Viewer");
	private final JLabel detail = new JLabel("Loading terrain...");
	private final JLabel compassHud = new JLabel("Heading --");
	private final JLabel tileHud = new JLabel("Tile --");
	private final JLabel fovHud = new JLabel();
	private final JLabel viewDistanceHud = new JLabel("View Distance --");
	private final JLabel debugOverlay = new JLabel();
	private final JPanel loadingOverlay;
	private final Map3DMinimapOverlay minimapOverlay;
	private final MapPanel areaSearchMapPanel;
	private final MapAreaSearchPanel areaSearchPanel;
	private final Map3DControlsOverlay controlsOverlay = new Map3DControlsOverlay();
	private final NpcHoverTextOverlay npcHoverOverlay = new NpcHoverTextOverlay();
	private final JPanel controlsBody = new JPanel(new BorderLayout(0, 8));
	private final Border controlsExpandedBorder = BorderFactory.createCompoundBorder(
		BorderFactory.createLineBorder(new Color(65, 65, 65)),
		BorderFactory.createEmptyBorder(10, 10, 10, 10)
	);
	private final Border controlsCollapsedBorder = BorderFactory.createCompoundBorder(
		BorderFactory.createLineBorder(new Color(65, 65, 65)),
		BorderFactory.createEmptyBorder(4, 4, 4, 4)
	);
	private final JPanel controlsHeader = new JPanel(new BorderLayout(8, 0));
	private final JPanel controlsPanel;
	private JPanel controlsRows;
	private JPanel npcBrowserButtonRow;
	private final Timer renderTimer;
	private final Runnable exitAction;
	private final Consumer<String> failureAction;
	private volatile TerrainRegionLoader.Session loaderSession;
	private volatile Set<Integer> desiredRegionIds = Set.of();
	private TerrainScene currentScene;
	private HoveredTile hoveredTile;
	private AWTContext awtContext;
	private Point lastMousePoint;
	private boolean disposed;
	private boolean glContextReady;
	private boolean glContextFailed;
	private boolean glFailureReported;
	private boolean middleMouseLook;
	private boolean cameraLocked;
	private boolean controlsCollapsed;
	private int maxVisiblePlane = 0;
	private boolean npcsVisible = true;
	private boolean npcOutlinesVisible = true;
	private boolean npcHoverTextVisible = true;
	private boolean npcWikiSyncCombatColors;
	private boolean minimapVisible = true;
	private boolean developerModeAvailable;
	private Integer wikiSyncCombatLevel;
	private Color npcOutlineColor = new Color(Viewer3DState.DEFAULT_NPC_OUTLINE_COLOR_ARGB, true);
	private Color tileHoverSelectorColor = new Color(Viewer3DState.DEFAULT_TILE_HOVER_COLOR_ARGB, true);
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
	private long lastWikiSyncCombatRefreshNanos;
	private Viewer3DState lastSavedState;
	private Map3DWorldMapDock worldMapDock;
	private boolean pluginPopupHandledDuringPressRelease;
	private Tile lastPluginOverlayCameraTile;
	private Set<Integer> lastPluginOverlayRegionIds = Set.of();
	private volatile boolean pluginOverlayDirty = true;
	private final Vector3f lastStreamPriorityCameraPosition = new Vector3f();
	private boolean lastStreamPriorityCameraPositionValid;

	public Map3DPanel(Path cacheDirectory, int regionId, Runnable exitAction)
	{
		this(cacheDirectory, regionId, installedAtlasPath(), DEFAULT_MAP_CACHE_BUDGET_BYTES, null, null, exitAction, null);
	}

	public Map3DPanel(Path cacheDirectory, int regionId, Path atlasPath, long mapCacheBudgetBytes,
	                  ViewerSettings settings, MapViewerPlugin activePlugin, Runnable exitAction)
	{
		this(cacheDirectory, regionId, atlasPath, mapCacheBudgetBytes, settings, activePlugin, exitAction, null);
	}

	public Map3DPanel(Path cacheDirectory, int regionId, Path atlasPath, long mapCacheBudgetBytes,
	                  ViewerSettings settings, MapViewerPlugin activePlugin, Runnable exitAction,
	                  Consumer<String> failureAction)
	{
		this(cacheDirectory, regionId, atlasPath, mapCacheBudgetBytes, settings, activePlugin, exitAction, failureAction, null);
	}

	public Map3DPanel(Path cacheDirectory, int regionId, Path atlasPath, long mapCacheBudgetBytes,
	                  ViewerSettings settings, MapViewerPlugin activePlugin, Runnable exitAction,
	                  Consumer<String> failureAction, Tile initialFocusTile)
	{
		this(cacheDirectory, regionId, atlasPath, mapCacheBudgetBytes, settings, activePlugin, exitAction,
			failureAction, initialFocusTile, false);
	}

	public Map3DPanel(Path cacheDirectory, int regionId, Path atlasPath, long mapCacheBudgetBytes,
	                  ViewerSettings settings, MapViewerPlugin activePlugin, Runnable exitAction,
	                  Consumer<String> failureAction, Tile initialFocusTile, boolean developerModeAvailable)
	{
		super(new BorderLayout());
		this.cacheDirectory = cacheDirectory;
		this.atlasPath = atlasPath;
		this.mapCacheBudgetBytes = mapCacheBudgetBytes <= 0L ? DEFAULT_MAP_CACHE_BUDGET_BYTES : mapCacheBudgetBytes;
		this.settings = settings;
		this.initialFocusTile = initialFocusTile == null ? null : new Tile(initialFocusTile.x, initialFocusTile.y, initialFocusTile.z);
		this.initialState = this.initialFocusTile == null && settings != null ? settings.viewer3DState() : null;
		this.lastSavedState = initialState;
		this.regionId = regionId;
		this.initialLoadRegionId = initialLoadRegionId(regionId, this.initialFocusTile, this.initialState);
		this.originRegionX = TerrainScene.regionX(regionId);
		this.originRegionY = TerrainScene.regionY(regionId);
		this.currentScene = TerrainScene.empty(originRegionX, originRegionY);
		this.streamCenterRegionId = initialLoadRegionId;
		this.exitAction = exitAction;
		this.failureAction = failureAction;
		setOpaque(true);
		setBackground(Color.BLACK);
		applyInitialPreferences();

		canvas = new Canvas();
		canvas.setBackground(Color.BLACK);
		canvas.setFocusable(true);
		canvas.setIgnoreRepaint(true);
		minimapOverlay = createMinimapOverlay();
		if (minimapOverlay != null)
		{
			minimapOverlay.setVisible(minimapVisible);
		}
		AreaSearch areaSearch = createAreaSearch();
		areaSearchMapPanel = areaSearch.mapPanel();
		areaSearchPanel = areaSearch.searchPanel();
		loadingOverlay = buildLoadingOverlay();
		controlsPanel = buildControlsPanel();
		sceneLayer = buildSceneLayer();
		setMapBackgroundColor(settings == null ? Color.BLACK : settings.mapBackgroundColor());
		setActivePlugin(activePlugin);
		setDeveloperModeAvailable(developerModeAvailable);

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
			renderer.shutdown();
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

	public void setDeveloperModeAvailable(boolean developerModeAvailable)
	{
		this.developerModeAvailable = developerModeAvailable;
		renderer.setNpcPickingEnabled(developerModeAvailable);
		npcBrowserButton.setVisible(developerModeAvailable);
		npcBrowserButton.setEnabled(developerModeAvailable);
		npcBrowserSpacer.setVisible(developerModeAvailable);
		if (npcBrowserButtonRow != null)
		{
			npcBrowserButtonRow.setVisible(developerModeAvailable);
		}
		updateControlsRowsSize();
		if (controlsPanel != null)
		{
			controlsPanel.revalidate();
			controlsPanel.repaint();
			Container parent = controlsPanel.getParent();
			if (parent != null)
			{
				parent.revalidate();
				parent.doLayout();
				parent.repaint();
			}
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

	public void setMapBackgroundColor(Color color)
	{
		Color background = color == null ? Color.BLACK : color;
		setBackground(background);
		canvas.setBackground(background);
		sceneLayer.setBackground(background);
		renderer.setBackgroundColor(background);
		if (minimapOverlay != null)
		{
			minimapOverlay.setMapBackgroundColor(background);
		}
		if (areaSearchMapPanel != null)
		{
			areaSearchMapPanel.setMapBackgroundColor(background);
		}
		if (worldMapDock != null)
		{
			worldMapDock.setMapBackgroundColor(background);
		}
		repaint();
	}

	public void invokeRenderLater(Runnable task)
	{
		if (task != null)
		{
			renderTasks.add(task);
		}
	}

	private JPanel buildControlsPanel()
	{
		JPanel panel = new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getPreferredSize()
			{
				if (controlsCollapsed)
				{
					return new Dimension(CONTROLS_COLLAPSED_SIZE, CONTROLS_COLLAPSED_SIZE);
				}
				Dimension preferred = super.getPreferredSize();
				return new Dimension(CONTROLS_EXPANDED_WIDTH, preferred.height);
			}
		};
		panel.setOpaque(true);
		panel.setBorder(controlsExpandedBorder);

		title.setText("3D Map Controls");
		title.setForeground(new Color(225, 225, 225));
		title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
		detail.setForeground(new Color(165, 165, 165));
		compassHud.setForeground(new Color(220, 220, 220));
		tileHud.setForeground(new Color(220, 220, 220));
		fovHud.setForeground(new Color(220, 220, 220));
		viewDistanceHud.setForeground(new Color(220, 220, 220));

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
		styleToolbarButton(viewControlsButton);
		viewControlsButton.addActionListener(e -> {
			controlsOverlay.setVisible(viewControlsButton.isSelected());
			sceneLayer.revalidate();
			sceneLayer.doLayout();
			sceneLayer.repaint();
		});
		styleToolbarButton(minimapButton);
		minimapButton.setEnabled(minimapOverlay != null);
		minimapButton.setSelected(!minimapVisible);
		minimapButton.setText(minimapVisible ? "Hide Minimap" : "Show Minimap");
		minimapButton.addActionListener(e -> applyMinimapVisibilitySelection());
		styleToolbarButton(overlayPriorityButton);
		updateOverlayPriorityButtonText();
		overlayPriorityButton.addActionListener(e -> applyOverlayPrioritySelection());
		styleColorButton(npcOutlineColorButton, npcOutlineColor);
		npcOutlineColorButton.addActionListener(e -> chooseNpcOutlineColor());
		styleToolbarButton(npcBrowserButton);
		npcBrowserButton.setVisible(developerModeAvailable);
		npcBrowserButton.addActionListener(e -> openNpcBrowser());
		styleColorButton(tileHoverColorButton, tileHoverSelectorColor);
		tileHoverColorButton.addActionListener(e -> chooseTileHoverSelectorColor());
		configureJumpControls();
		JButton exit = new JButton("Switch to 2D Map");
		styleToolbarButton(exit);
		exit.addActionListener(e -> {
			if (exitAction != null)
			{
				exitAction.run();
			}
		});

		antialiasingScale.setFocusable(false);
		antialiasingScale.addActionListener(e -> applyAntialiasingSelection());
		viewDistanceSelect.setFocusable(false);
		viewDistanceSelect.addActionListener(e -> applyViewDistanceSelection());
		fovSlider.setOpaque(false);
		fovSlider.setFocusable(false);
		fovSlider.setPreferredSize(new Dimension(170, 28));
		fovSlider.addChangeListener(e -> {
			camera.setFovDegrees(fovSlider.getValue());
			updateFovHud();
			scheduleStateSave();
		});
		updateFovHud();
		updateViewDistanceHud();

		for (int plane = 0; plane < planeVisibleCheckBoxes.length; plane++)
		{
			JCheckBox checkBox = planeVisibleCheckBoxes[plane];
			styleCheckBox(checkBox);
			int selectedPlane = plane;
			checkBox.addActionListener(e -> applyPlaneVisibilitySelection(selectedPlane));
		}
		updatePlaneControlState();

		styleCheckBox(npcVisibleCheckBox);
		styleCheckBox(npcOutlinesCheckBox);
		styleCheckBox(npcHoverTextCheckBox);
		styleCheckBox(npcWikiSyncCombatColorsCheckBox);
		npcWikiSyncCombatColorsCheckBox.setToolTipText("Show combat levels based on the stored WikiSync profile.");
		npcVisibleCheckBox.setSelected(npcsVisible);
		npcOutlinesCheckBox.setSelected(npcOutlinesVisible);
		npcHoverTextCheckBox.setSelected(npcHoverTextVisible);
		npcWikiSyncCombatColorsCheckBox.setSelected(npcWikiSyncCombatColors);
		npcVisibleCheckBox.addActionListener(e -> applyNpcVisibilitySelection());
		npcOutlinesCheckBox.addActionListener(e -> applyNpcOverlaySelection());
		npcHoverTextCheckBox.addActionListener(e -> applyNpcOverlaySelection());
		npcWikiSyncCombatColorsCheckBox.addActionListener(e -> applyNpcCombatColorSelection());
		updateNpcControlState();

		styleCollapseButton(collapseControlsButton, new Dimension(30, 28));
		collapseControlsButton.addActionListener(e -> setControlsCollapsed(!controlsCollapsed));
		controlsHeader.setOpaque(false);
		controlsHeader.add(title, BorderLayout.WEST);
		controlsHeader.add(collapseControlsButton, BorderLayout.EAST);
		panel.add(controlsHeader, BorderLayout.NORTH);

		controlsBody.setOpaque(false);
		controlsBody.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		JPanel rows = new JPanel();
		rows.setOpaque(false);
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setAlignmentX(Component.LEFT_ALIGNMENT);
		rows.add(Box.createVerticalStrut(22));
		rows.add(centeredRow(actionButtonGrid()));
		rows.add(Box.createVerticalStrut(8));
		rows.add(centeredButtonRow(exit));
		rows.add(sectionSeparator());
		rows.add(renderingStatusRow());
		rows.add(Box.createVerticalStrut(8));
		rows.add(fovRow());
		rows.add(sectionSeparator());
		rows.add(sectionTitleRow("Overlays"));
		rows.add(centeredButtonRow(tileHoverColorButton));
		rows.add(Box.createVerticalStrut(6));
		rows.add(centeredButtonRow(overlayPriorityButton));
		rows.add(sectionSeparator());
		rows.add(sectionTitleRow("Jump"));
		rows.add(jumpRegionIdRow());
		rows.add(Box.createVerticalStrut(6));
		rows.add(jumpRegionRow());
		rows.add(Box.createVerticalStrut(6));
		rows.add(jumpTileRow());
		rows.add(sectionSeparator());
		rows.add(sectionTitleRow("Planes"));
		rows.add(controlRow(planeVisibleCheckBoxes));
		rows.add(sectionSeparator());
		rows.add(sectionTitleRow("NPCs"));
		rows.add(centeredControlRow(npcVisibleCheckBox, npcOutlinesCheckBox, npcHoverTextCheckBox));
		rows.add(Box.createVerticalStrut(6));
		rows.add(centeredControlRowFixedWidth(npcOutlineColorButton,
			controlContentWidth(npcVisibleCheckBox, npcOutlinesCheckBox, npcHoverTextCheckBox)));
		rows.add(Box.createVerticalStrut(6));
		rows.add(centeredControlRowFixedWidth(npcWikiSyncCombatColorsCheckBox, CONTROLS_FULL_BUTTON_WIDTH));
		npcBrowserButtonRow = centeredButtonRow(npcBrowserButton);
		npcBrowserSpacer.setVisible(developerModeAvailable);
		npcBrowserButtonRow.setVisible(developerModeAvailable);
		rows.add(npcBrowserSpacer);
		rows.add(npcBrowserButtonRow);
		rows.add(controlsBottomSpacer);
		controlsRows = rows;
		updateControlsRowsSize();
		rows.setMaximumSize(new Dimension(CONTROLS_INNER_WIDTH, Integer.MAX_VALUE));
		JPanel rowHost = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		rowHost.setOpaque(false);
		rowHost.add(rows);
		controlsBody.add(rowHost, BorderLayout.CENTER);
		panel.add(controlsBody, BorderLayout.CENTER);
		return panel;
	}

	private void updateControlsRowsSize()
	{
		if (controlsRows == null)
		{
			return;
		}
		int height = 0;
		for (Component component : controlsRows.getComponents())
		{
			if (component.isVisible())
			{
				height += component.getPreferredSize().height;
			}
		}
		Dimension rowSize = new Dimension(CONTROLS_INNER_WIDTH, height);
		controlsRows.setMinimumSize(rowSize);
		controlsRows.setPreferredSize(rowSize);
		controlsRows.setMaximumSize(new Dimension(CONTROLS_INNER_WIDTH, Integer.MAX_VALUE));
		controlsRows.revalidate();
	}

	private JLabel controlSectionTitle(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(new Color(235, 235, 235));
		label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private JPanel controlRow(Component... components)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMinimumSize(new Dimension(CONTROLS_INNER_WIDTH, 26));
		row.setPreferredSize(new Dimension(CONTROLS_INNER_WIDTH, 26));
		row.setMaximumSize(new Dimension(CONTROLS_INNER_WIDTH, 26));
		for (Component component : components)
		{
			row.add(component);
		}
		return row;
	}

	private JPanel centeredControlRow(Component... components)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMinimumSize(new Dimension(CONTROLS_INNER_WIDTH, 26));
		row.setPreferredSize(new Dimension(CONTROLS_INNER_WIDTH, 26));
		row.setMaximumSize(new Dimension(CONTROLS_INNER_WIDTH, 26));
		for (Component component : components)
		{
			row.add(component);
		}
		return row;
	}

	private JPanel centeredControlRowFixedWidth(AbstractButton button, int width)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension rowSize = new Dimension(CONTROLS_INNER_WIDTH, 26);
		row.setMinimumSize(rowSize);
		row.setPreferredSize(rowSize);
		row.setMaximumSize(rowSize);
		Dimension buttonSize = new Dimension(
			Math.min(CONTROLS_INNER_WIDTH - 16, Math.max(button.getPreferredSize().width, width)),
			26
		);
		setFixedControlSize(button, buttonSize);
		row.add(button);
		return row;
	}

	private int controlContentWidth(Component... components)
	{
		int width = 0;
		for (int i = 0; i < components.length; i++)
		{
			width += components[i].getPreferredSize().width;
			if (i > 0)
			{
				width += 8;
			}
		}
		return width;
	}

	private JPanel sectionTitleRow(String text)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMinimumSize(new Dimension(CONTROLS_INNER_WIDTH, 18));
		row.setPreferredSize(new Dimension(CONTROLS_INNER_WIDTH, 18));
		row.setMaximumSize(new Dimension(CONTROLS_INNER_WIDTH, 18));
		row.add(controlSectionTitle(text));
		return row;
	}

	private JPanel actionButtonGrid()
	{
		JPanel grid = new JPanel(new GridLayout(2, 2, CONTROLS_ACTION_GRID_GAP, 6));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.CENTER_ALIGNMENT);
		Dimension size = new Dimension(CONTROLS_ACTION_GRID_WIDTH, 58);
		grid.setMinimumSize(size);
		grid.setPreferredSize(size);
		grid.setMaximumSize(size);
		Dimension buttonSize = new Dimension(CONTROLS_ACTION_BUTTON_WIDTH, 25);
		setFixedControlSize(lockCameraButton, buttonSize);
		setFixedControlSize(debugOverlayButton, buttonSize);
		setFixedControlSize(viewControlsButton, buttonSize);
		setFixedControlSize(minimapButton, buttonSize);
		grid.add(lockCameraButton);
		grid.add(debugOverlayButton);
		grid.add(viewControlsButton);
		grid.add(minimapButton);
		return grid;
	}

	private JPanel centeredRow(Component component)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		int height = component.getPreferredSize().height;
		row.setMinimumSize(new Dimension(CONTROLS_INNER_WIDTH, height));
		row.setPreferredSize(new Dimension(CONTROLS_INNER_WIDTH, height));
		row.setMaximumSize(new Dimension(CONTROLS_INNER_WIDTH, height));
		row.add(component);
		return row;
	}

	private JPanel centeredButtonRow(Component component)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension size = new Dimension(CONTROLS_FULL_BUTTON_WIDTH, component.getPreferredSize().height);
		row.setMinimumSize(new Dimension(CONTROLS_INNER_WIDTH, size.height));
		row.setPreferredSize(new Dimension(CONTROLS_INNER_WIDTH, size.height));
		row.setMaximumSize(new Dimension(CONTROLS_INNER_WIDTH, size.height));
		if (component instanceof AbstractButton button)
		{
			setFixedControlSize(button, size);
		}
		row.add(component);
		return row;
	}

	private Component sectionSeparator()
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
		row.add(new JSeparator(), BorderLayout.CENTER);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMinimumSize(new Dimension(CONTROLS_INNER_WIDTH, 17));
		row.setPreferredSize(new Dimension(CONTROLS_INNER_WIDTH, 17));
		row.setMaximumSize(new Dimension(CONTROLS_INNER_WIDTH, 17));
		return row;
	}

	private JPanel comboRow(String label, JComboBox<?> comboBox)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(toolbarLabel(label), BorderLayout.WEST);
		row.add(comboBox, BorderLayout.CENTER);
		return row;
	}

	private JPanel renderingStatusRow()
	{
		JPanel row = new JPanel();
		row.setOpaque(false);
		row.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMinimumSize(new Dimension(CONTROLS_INNER_WIDTH, 100));
		row.setPreferredSize(new Dimension(CONTROLS_INNER_WIDTH, 100));
		row.setMaximumSize(new Dimension(CONTROLS_INNER_WIDTH, 100));

		JPanel rendering = new JPanel();
		rendering.setOpaque(false);
		rendering.setLayout(new BoxLayout(rendering, BoxLayout.Y_AXIS));
		rendering.setPreferredSize(new Dimension(135, 96));
		rendering.setMaximumSize(new Dimension(135, 96));
		rendering.add(controlSectionTitle("Rendering"));
		rendering.add(Box.createVerticalStrut(8));
		rendering.add(compactComboRow("AA", antialiasingScale));
		rendering.add(Box.createVerticalStrut(6));
		rendering.add(compactComboRow("View", viewDistanceSelect));

		JPanel status = new JPanel();
		status.setOpaque(false);
		status.setLayout(new BoxLayout(status, BoxLayout.Y_AXIS));
		status.setPreferredSize(new Dimension(130, 96));
		status.setMaximumSize(new Dimension(130, 96));
		status.add(controlSectionTitle("Status"));
		status.add(Box.createVerticalStrut(8));
		status.add(statusLabelRow(compassHud));
		status.add(Box.createVerticalStrut(5));
		status.add(statusLabelRow(tileHud));
		status.add(Box.createVerticalStrut(5));
		status.add(statusLabelRow(viewDistanceHud));

		JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
		separator.setPreferredSize(new Dimension(1, 100));
		separator.setMaximumSize(new Dimension(1, 100));

		row.add(rendering);
		row.add(Box.createHorizontalStrut(10));
		row.add(separator);
		row.add(Box.createHorizontalStrut(14));
		row.add(status);
		return row;
	}

	private JPanel compactComboRow(String label, JComboBox<?> comboBox)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel text = toolbarLabel(label);
		text.setPreferredSize(new Dimension(24, 22));
		row.add(text, BorderLayout.WEST);
		comboBox.setPreferredSize(new Dimension(94, 24));
		comboBox.setMaximumSize(new Dimension(94, 24));
		row.add(comboBox, BorderLayout.CENTER);
		return row;
	}

	private JPanel fovRow()
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(CONTROLS_INNER_WIDTH, 28));
		row.add(toolbarLabel("FOV"), BorderLayout.WEST);
		row.add(fovSlider, BorderLayout.CENTER);
		return row;
	}

	private void configureJumpControls()
	{
		Dimension regionIdSize = new Dimension(74, 26);
		Dimension coordSize = new Dimension(54, 26);
		Dimension planeSize = new Dimension(38, 26);
		setFixedControlSize(jumpRegionId, regionIdSize);
		setFixedControlSize(jumpRegionIdPlane, planeSize);
		setFixedControlSize(jumpRegionX, coordSize);
		setFixedControlSize(jumpRegionY, coordSize);
		setFixedControlSize(jumpRegionPlane, planeSize);
		setFixedControlSize(jumpTileX, new Dimension(64, 26));
		setFixedControlSize(jumpTileY, new Dimension(64, 26));
		setFixedControlSize(jumpTilePlane, planeSize);
		jumpRegionIdPlane.setInt(0);
		jumpRegionPlane.setInt(0);
		jumpTilePlane.setInt(0);
		styleToolbarButton(jumpRegionIdButton);
		styleToolbarButton(jumpRegionButton);
		styleToolbarButton(jumpTileButton);
		jumpRegionIdButton.addActionListener(e -> jumpToRegionIdInput());
		jumpRegionButton.addActionListener(e -> jumpToRegionInput());
		jumpTileButton.addActionListener(e -> jumpToTileInput());
	}

	private JPanel jumpRegionIdRow()
	{
		JPanel row = compactInputRow();
		row.add(toolbarLabel("Region"));
		row.add(jumpRegionId);
		row.add(toolbarLabel("Z"));
		row.add(jumpRegionIdPlane);
		row.add(jumpRegionIdButton);
		return row;
	}

	private JPanel jumpRegionRow()
	{
		JPanel row = compactInputRow();
		row.add(toolbarLabel("RX"));
		row.add(jumpRegionX);
		row.add(toolbarLabel("RY"));
		row.add(jumpRegionY);
		row.add(toolbarLabel("Z"));
		row.add(jumpRegionPlane);
		row.add(jumpRegionButton);
		return row;
	}

	private JPanel jumpTileRow()
	{
		JPanel row = compactInputRow();
		row.add(toolbarLabel("X"));
		row.add(jumpTileX);
		row.add(toolbarLabel("Y"));
		row.add(jumpTileY);
		row.add(toolbarLabel("Z"));
		row.add(jumpTilePlane);
		row.add(jumpTileButton);
		return row;
	}

	private JPanel compactInputRow()
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension size = new Dimension(CONTROLS_INNER_WIDTH, 28);
		row.setMinimumSize(size);
		row.setPreferredSize(size);
		row.setMaximumSize(size);
		return row;
	}

	private void jumpToRegionIdInput()
	{
		Integer regionIdValue = jumpRegionId.getInt(null);
		if (regionIdValue == null || regionIdValue < 0)
		{
			showJumpWarning("Enter a valid region ID.");
			return;
		}
		int rx = regionIdValue >> 8;
		int ry = regionIdValue & 0xFF;
		if (!isRegionInMap(rx, ry))
		{
			showJumpWarning("That region is outside the bundled map range.");
			return;
		}
		warpCameraToTile(centerTileForRegion(rx, ry, normalizedJumpPlane(jumpRegionIdPlane.getInt(0))));
	}

	private void jumpToRegionInput()
	{
		Integer rx = jumpRegionX.getInt(null);
		Integer ry = jumpRegionY.getInt(null);
		if (rx == null || ry == null)
		{
			showJumpWarning("Enter valid integers for rx and ry.");
			return;
		}
		if (!isRegionInMap(rx, ry))
		{
			showJumpWarning("That region is outside the bundled map range.");
			return;
		}
		warpCameraToTile(centerTileForRegion(rx, ry, normalizedJumpPlane(jumpRegionPlane.getInt(0))));
	}

	private void jumpToTileInput()
	{
		Integer x = jumpTileX.getInt(null);
		Integer y = jumpTileY.getInt(null);
		if (x == null || y == null)
		{
			showJumpWarning("Enter valid integers for x and y.");
			return;
		}
		Tile tile = new Tile(x, y, normalizedJumpPlane(jumpTilePlane.getInt(0)));
		if (!isValidWorldTile(tile))
		{
			showJumpWarning("That tile is outside the bundled map range.");
			return;
		}
		warpCameraToTile(tile);
	}

	private void showJumpWarning(String message)
	{
		JOptionPane.showMessageDialog(this, message, "Jump", JOptionPane.WARNING_MESSAGE);
	}

	private static int normalizedJumpPlane(Integer value)
	{
		return Math.max(0, Math.min(3, value == null ? 0 : value));
	}

	private static boolean isRegionInMap(int rx, int ry)
	{
		return rx >= Paths.MIN_RX && rx <= Paths.MAX_RX && ry >= Paths.MIN_RY && ry <= Paths.MAX_RY;
	}

	private static Tile centerTileForRegion(int rx, int ry, int z)
	{
		return new Tile(
			rx * TerrainScene.REGION_SIZE + TerrainScene.REGION_SIZE / 2,
			ry * TerrainScene.REGION_SIZE + TerrainScene.REGION_SIZE / 2,
			z
		);
	}

	private JPanel statusLabelRow(JLabel label)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(label, BorderLayout.CENTER);
		return row;
	}

	private void setControlsCollapsed(boolean value)
	{
		if (controlsCollapsed == value)
		{
			return;
		}
		controlsCollapsed = value;
		controlsBody.setVisible(!controlsCollapsed);
		title.setVisible(!controlsCollapsed);
		controlsPanel.setBorder(controlsCollapsed ? controlsCollapsedBorder : controlsExpandedBorder);
		controlsHeader.removeAll();
		if (controlsCollapsed)
		{
			styleCollapseButton(collapseControlsButton, new Dimension(36, 36));
			collapseControlsButton.setText("<");
			controlsHeader.add(collapseControlsButton, BorderLayout.CENTER);
		}
		else
		{
			styleCollapseButton(collapseControlsButton, new Dimension(30, 28));
			collapseControlsButton.setText(">");
			controlsHeader.add(title, BorderLayout.WEST);
			controlsHeader.add(collapseControlsButton, BorderLayout.EAST);
		}
		controlsPanel.revalidate();
		Container parent = controlsPanel.getParent();
		if (parent != null)
		{
			parent.revalidate();
			parent.doLayout();
			parent.repaint();
		}
	}

	private void applyNpcVisibilitySelection()
	{
		npcsVisible = npcVisibleCheckBox.isSelected();
		updateNpcControlState();
		applyNpcRendererSettings();
		scheduleStateSave();
	}

	private void applyNpcOverlaySelection()
	{
		npcOutlinesVisible = npcOutlinesCheckBox.isSelected();
		npcHoverTextVisible = npcHoverTextCheckBox.isSelected();
		updateNpcControlState();
		applyNpcRendererSettings();
		scheduleStateSave();
	}

	private void applyNpcCombatColorSelection()
	{
		refreshWikiSyncCombatLevel();
		npcWikiSyncCombatColors = npcWikiSyncCombatColorsCheckBox.isSelected();
		npcHoverOverlay.setCombatColorProfile(npcWikiSyncCombatColors, wikiSyncCombatLevel);
		updateNpcControlState();
		if (npcWikiSyncCombatColors && wikiSyncCombatLevel == null)
		{
			detail.setText("No stored WikiSync combat level found");
		}
		scheduleStateSave();
	}

	private void chooseNpcOutlineColor()
	{
		Color picked = JColorChooser.showDialog(this, "NPC Outline Color", npcOutlineColor);
		if (picked == null)
		{
			return;
		}
		npcOutlineColor = withAlpha(picked, npcOutlineColor.getAlpha());
		styleColorButton(npcOutlineColorButton, npcOutlineColor);
		applyOverlayColorSettings();
		scheduleStateSave();
	}

	private void chooseTileHoverSelectorColor()
	{
		Color picked = JColorChooser.showDialog(this, "Tile Hover Selector Color", tileHoverSelectorColor);
		if (picked == null)
		{
			return;
		}
		tileHoverSelectorColor = withAlpha(picked, tileHoverSelectorColor.getAlpha());
		styleColorButton(tileHoverColorButton, tileHoverSelectorColor);
		applyOverlayColorSettings();
		scheduleStateSave();
	}

	private void applyOverlayColorSettings()
	{
		Color outline = npcOutlineColor;
		Color hover = tileHoverSelectorColor;
		invokeRenderLater(() -> {
			renderer.setNpcOutlineColor(outline);
			renderer.setTileHoverSelectorColor(hover);
		});
	}

	private static Color withAlpha(Color color, int alpha)
	{
		if (color == null)
		{
			return null;
		}
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(1, Math.min(255, alpha)));
	}

	private static int argb(Color color)
	{
		return color == null ? 0 : color.getRGB();
	}

	private void updateNpcControlState()
	{
		npcOutlinesCheckBox.setEnabled(npcsVisible);
		npcHoverTextCheckBox.setEnabled(npcsVisible);
		npcOutlineColorButton.setEnabled(npcsVisible && npcOutlinesVisible);
		npcWikiSyncCombatColorsCheckBox.setEnabled(npcsVisible && npcHoverTextVisible && wikiSyncCombatLevel != null);
	}

	private void applyNpcRendererSettings()
	{
		boolean visible = npcsVisible;
		boolean outlinesVisible = visible && npcOutlinesVisible;
		boolean hoverTextVisible = visible && npcHoverTextVisible;
		invokeRenderLater(() -> {
			renderer.setNpcsVisible(visible);
			renderer.setNpcOutlinesEnabled(outlinesVisible);
			renderer.setNpcHoverTextEnabled(hoverTextVisible);
		});
		if (!npcsVisible || !npcHoverTextVisible)
		{
			npcHoverOverlay.setInfo(null);
		}
	}

	private void refreshWikiSyncCombatLevel()
	{
		wikiSyncCombatLevel = settings == null ? null : settings.wikiSyncCombatLevel();
		npcHoverOverlay.setCombatColorProfile(npcWikiSyncCombatColors, wikiSyncCombatLevel);
	}

	private void maybeRefreshWikiSyncCombatLevel(long now)
	{
		if (settings == null || now - lastWikiSyncCombatRefreshNanos < WIKISYNC_COMBAT_REFRESH_NANOS)
		{
			return;
		}
		lastWikiSyncCombatRefreshNanos = now;
		Integer previous = wikiSyncCombatLevel;
		refreshWikiSyncCombatLevel();
		if (previous == null && wikiSyncCombatLevel != null
			|| previous != null && !previous.equals(wikiSyncCombatLevel))
		{
			updateNpcControlState();
		}
	}

	private void applyPlaneVisibilitySelection(int plane)
	{
		if (plane <= 0)
		{
			maxVisiblePlane = 0;
		}
		else if (planeVisibleCheckBoxes[plane].isSelected())
		{
			maxVisiblePlane = Math.max(maxVisiblePlane, plane);
		}
		else
		{
			maxVisiblePlane = plane - 1;
		}
		maxVisiblePlane = Viewer3DState.clampMaxVisiblePlane(maxVisiblePlane);
		updatePlaneControlState();
		applyPlaneRendererSettings();
		if (hoveredTile != null && hoveredTile.plane() > maxVisiblePlane)
		{
			hoveredTile = null;
			renderer.setHoveredTile(null);
			updateTileHud();
		}
		if (minimapOverlay != null)
		{
			minimapOverlay.repaint();
		}
		requestPluginOverlayRefresh();
		detail.setText("Showing planes 0-" + maxVisiblePlane);
		scheduleStateSave();
	}

	private void updatePlaneControlState()
	{
		maxVisiblePlane = Viewer3DState.clampMaxVisiblePlane(maxVisiblePlane);
		for (int plane = 0; plane < planeVisibleCheckBoxes.length; plane++)
		{
			JCheckBox checkBox = planeVisibleCheckBoxes[plane];
			checkBox.setSelected(plane <= maxVisiblePlane);
			checkBox.setEnabled(plane > 0 && plane <= maxVisiblePlane + 1);
		}
		planeVisibleCheckBoxes[0].setSelected(true);
		planeVisibleCheckBoxes[0].setEnabled(false);
	}

	private void applyPlaneRendererSettings()
	{
		int visiblePlane = maxVisiblePlane;
		invokeRenderLater(() -> renderer.setMaxVisiblePlane(visiblePlane));
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
				Dimension controlSize = controlsPanel.getPreferredSize();
				int controlsX = Math.max(8, size.width - controlSize.width - 12);
				controlsPanel.setBounds(controlsX, 12, controlSize.width, controlSize.height);
				int overlayRightAnchor = Math.max(8, controlsX - CONTROLS_MINIMAP_GAP);
				int debugTop = 12;
				int overlayColumnRight = overlayRightAnchor;
				if (npcHoverOverlay.isVisible())
				{
					Dimension hoverSize = npcHoverOverlay.getPreferredSize();
					npcHoverOverlay.setBounds(12, 12, hoverSize.width, hoverSize.height);
				}
				if (minimapOverlay != null && minimapOverlay.isVisible())
				{
					Dimension minimapSize = minimapOverlay.getPreferredSize();
					int minimapX = Math.max(8, overlayRightAnchor - minimapSize.width);
					minimapOverlay.setBounds(minimapX, 12, minimapSize.width, minimapSize.height);
					debugTop = minimapOverlay.getY() + minimapOverlay.getHeight() + 8;
					overlayColumnRight = minimapOverlay.getX() + minimapOverlay.getWidth();
				}
				if (controlsOverlay.isVisible())
				{
					Dimension controlsSize = controlsOverlay.getPreferredSize();
					int controlsOverlayX = Math.max(8, overlayColumnRight - controlsSize.width);
					controlsOverlay.setBounds(controlsOverlayX, debugTop, controlsSize.width, controlsSize.height);
					debugTop = controlsOverlay.getY() + controlsOverlay.getHeight() + 8;
				}
				Dimension preferredSize = debugOverlay.getPreferredSize();
				int x = Math.max(8, overlayRightAnchor - preferredSize.width);
				debugOverlay.setBounds(x, debugTop, preferredSize.width, preferredSize.height);
				if (loadingOverlay.isVisible())
				{
					Dimension loadingSize = loadingOverlay.getPreferredSize();
					int loadingX = Math.max(0, (size.width - loadingSize.width) / 2);
					int loadingY = Math.max(0, (size.height - loadingSize.height) / 2);
					loadingOverlay.setBounds(loadingX, loadingY, loadingSize.width, loadingSize.height);
				}
			}
		};
		layeredPane.setOpaque(true);
		layeredPane.setBackground(Color.BLACK);
		layeredPane.add(canvas, JLayeredPane.DEFAULT_LAYER);
		if (minimapOverlay != null)
		{
			layeredPane.add(minimapOverlay, JLayeredPane.PALETTE_LAYER);
		}
		layeredPane.add(controlsPanel, JLayeredPane.PALETTE_LAYER);
		npcHoverOverlay.setVisible(false);
		layeredPane.add(npcHoverOverlay, JLayeredPane.PALETTE_LAYER);
		controlsOverlay.setVisible(false);
		layeredPane.add(controlsOverlay, JLayeredPane.PALETTE_LAYER);
		layeredPane.add(debugOverlay, JLayeredPane.PALETTE_LAYER);
		layeredPane.add(loadingOverlay, JLayeredPane.MODAL_LAYER);
		return layeredPane;
	}

	private JPanel buildLoadingOverlay()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setOpaque(true);
		panel.setBackground(new Color(24, 26, 30, 232));
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(105, 108, 116)),
			BorderFactory.createEmptyBorder(12, 14, 12, 14)
		));
		panel.setPreferredSize(new Dimension(280, 82));
		panel.setFocusable(false);

		JLabel label = new JLabel("Loading 3D scene...");
		label.setForeground(new Color(235, 235, 235));
		label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
		JProgressBar progress = new JProgressBar();
		progress.setIndeterminate(true);
		progress.setFocusable(false);
		panel.add(label, BorderLayout.NORTH);
		panel.add(progress, BorderLayout.CENTER);
		return panel;
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
				this::cameraHeadingRadians, renderer::npcMapDots, this::openWorldMapDock);
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
			updateViewDistanceHud();
			renderer.setViewDistanceRegions(streamGridSize);
			pluginOverlaysOnTop = state.pluginOverlaysOnTop();
			overlayPriorityButton.setSelected(pluginOverlaysOnTop);
			updateOverlayPriorityButtonText();
			renderer.setPluginOverlayOnTop(pluginOverlaysOnTop);
			maxVisiblePlane = Viewer3DState.clampMaxVisiblePlane(state.maxVisiblePlane());
			updatePlaneControlState();
			applyPlaneRendererSettings();
			npcsVisible = state.npcsVisible();
			npcOutlinesVisible = state.npcOutlinesVisible();
			npcHoverTextVisible = state.npcHoverTextVisible();
			npcWikiSyncCombatColors = state.npcWikiSyncCombatColors();
			minimapVisible = state.minimapVisible();
			npcOutlineColor = new Color(state.npcOutlineColorArgb(), true);
			tileHoverSelectorColor = new Color(state.tileHoverColorArgb(), true);
			refreshWikiSyncCombatLevel();
			applyNpcRendererSettings();
			applyOverlayColorSettings();
			return;
		}
		AntialiasingScale scale = AntialiasingScale.forSamples(DEFAULT_ANTIALIASING_SAMPLES);
		antialiasingSamples = scale.samples();
		antialiasingScale.setSelectedItem(scale);
		streamGridSize = Viewer3DState.DEFAULT_VIEW_DISTANCE_REGIONS;
		viewDistanceSelect.setSelectedItem(ViewDistanceOption.forGridSize(streamGridSize));
		updateViewDistanceHud();
		renderer.setViewDistanceRegions(streamGridSize);
		pluginOverlaysOnTop = false;
		overlayPriorityButton.setSelected(false);
		updateOverlayPriorityButtonText();
		renderer.setPluginOverlayOnTop(false);
		maxVisiblePlane = 0;
		updatePlaneControlState();
		applyPlaneRendererSettings();
		npcsVisible = true;
		npcOutlinesVisible = true;
		npcHoverTextVisible = true;
		npcWikiSyncCombatColors = false;
		minimapVisible = true;
		npcOutlineColor = new Color(Viewer3DState.DEFAULT_NPC_OUTLINE_COLOR_ARGB, true);
		tileHoverSelectorColor = new Color(Viewer3DState.DEFAULT_TILE_HOVER_COLOR_ARGB, true);
		refreshWikiSyncCombatLevel();
		applyNpcRendererSettings();
		applyOverlayColorSettings();
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
			if (settings != null)
			{
				worldMapPanel.setMapBackgroundColor(settings.mapBackgroundColor());
			}
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
		setCameraPositionForTile(tile, targetRegionId);
		if (!cameraInitialized)
		{
			cameraInitialized = true;
			hideLoadingOverlay();
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
		if (worldMapDock != null)
		{
			worldMapDock.updateCameraTile();
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
		if (initialFocusTile != null && isValidWorldTile(initialFocusTile))
		{
			camera.reset(mesh.initialCameraX(), mesh.initialCameraY(), mesh.initialCameraZ());
			setCameraPositionForTile(initialFocusTile, regionIdForTile(initialFocusTile));
		}
		else if (initialState != null)
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
		hideLoadingOverlay();
		saveViewerStateNow();
		canvas.requestFocusInWindow();
	}

	private void setCameraPositionForTile(Tile tile, int targetRegionId)
	{
		float worldX = tile.x - originRegionX * TerrainScene.REGION_SIZE - SceneScale.REGION_CENTER_TILES + 0.5f;
		float worldZ = originRegionY * TerrainScene.REGION_SIZE + SceneScale.REGION_CENTER_TILES - tile.y - 0.5f;
		float worldY = warpCameraHeight(tile, targetRegionId);
		camera.setPosition(new Vector3f(worldX, worldY, worldZ));
		lastStreamPriorityCameraPositionValid = false;
	}

	private static int initialLoadRegionId(int fallbackRegionId, Tile initialFocusTile, Viewer3DState initialState)
	{
		if (isValidWorldTile(initialFocusTile))
		{
			return regionIdForTile(initialFocusTile);
		}
		if (initialState != null && initialState.isValid())
		{
			return initialState.regionId();
		}
		return fallbackRegionId;
	}

	private void hideLoadingOverlay()
	{
		if (!loadingOverlay.isVisible())
		{
			return;
		}
		loadingOverlay.setVisible(false);
		sceneLayer.revalidate();
		sceneLayer.repaint();
	}

	private Vector3f applyMovementConstraints(Vector3fc previous, Vector3fc attempted)
	{
		return currentScene.clampMovement(previous, attempted, new Vector3f());
	}

	private int currentCameraPlane(Vector3fc position)
	{
		if (currentScene.isEmpty())
		{
			return initialState == null ? 0 : Math.min(maxVisiblePlane, Math.max(0, Math.min(3, initialState.plane())));
		}
		return Math.min(maxVisiblePlane, currentScene.cameraPlaneFor(position.x(), position.y(), position.z()));
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
			maxVisiblePlane,
			pluginOverlaysOnTop,
			npcsVisible,
			npcOutlinesVisible,
			npcHoverTextVisible,
			npcWikiSyncCombatColors,
			minimapVisible,
			argb(npcOutlineColor),
			argb(tileHoverSelectorColor)
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
				if (handleDeveloperPopupTrigger(e))
				{
					pluginPopupHandledDuringPressRelease = true;
					return;
				}
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
				if (handleDeveloperPopupTrigger(e))
				{
					pluginPopupHandledDuringPressRelease = true;
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
				renderer.setHoverRay(null, null);
				npcHoverOverlay.setInfo(null);
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
				renderer.deferRetainedDataCompaction();
				renderer.invalidatePluginOverlayGeometry();
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
			if (cameraPositionChanged(previousPosition))
			{
				renderer.deferRetainedDataCompaction();
				renderer.invalidatePluginOverlayGeometry();
			}
			scheduleStateSave();
			updateStreamedRegions();
		}
		updateHoveredTile(event);
	}

	private boolean handleDeveloperPopupTrigger(MouseEvent event)
	{
		if (!developerModeAvailable || !event.isPopupTrigger() && !SwingUtilities.isRightMouseButton(event))
		{
			return false;
		}
		updateHoveredTile(event);
		TerrainRenderer.NpcHoverInfo npc = renderer.pickHoveredNpcInfo();
		Tile tile = hoveredTile();
		if (npc == null && tile == null)
		{
			return false;
		}

		JPopupMenu popup = new JPopupMenu();
		if (npc != null)
		{
			addDeveloperNpcActions(popup, npc, tile);
		}
		else
		{
			JMenuItem spawn = new JMenuItem("Spawn NPC...");
			spawn.addActionListener(e -> promptSpawnNpc(tile));
			popup.add(spawn);
		}
		if (popup.getComponentCount() == 0)
		{
			return false;
		}
		popup.show(canvas, event.getX(), event.getY());
		event.consume();
		return true;
	}

	private void addDeveloperNpcActions(JPopupMenu popup, TerrainRenderer.NpcHoverInfo npc, Tile tile)
	{
		JMenuItem delete = new JMenuItem("Delete NPC");
		delete.addActionListener(e -> deleteNpcSpawn(npc));
		popup.add(delete);

		JMenuItem swap = new JMenuItem("Swap NPC...");
		swap.addActionListener(e -> swapNpcSpawn(npc));
		popup.add(swap);

		JMenu faceDirection = new JMenu("Change Face Direction");
		for (NpcFaceDirection direction : NpcFaceDirection.values())
		{
			JMenuItem item = new JMenuItem(direction.toString());
			item.addActionListener(e -> changeNpcFaceDirection(npc, direction));
			faceDirection.add(item);
		}
		popup.add(faceDirection);

		JMenuItem spawnTile = new JMenuItem("Change Spawn Tile...");
		spawnTile.addActionListener(e -> changeNpcSpawnTile(npc, tile));
		popup.add(spawnTile);

		JMenuItem walk = new JMenuItem(npc.currentlyWalkingEnabled() ? "Disable Walk" : "Enable Walk");
		walk.addActionListener(e -> setNpcWalkOverride(npc, !npc.currentlyWalkingEnabled()));
		popup.add(walk);
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

	private void deleteNpcSpawn(TerrainRenderer.NpcHoverInfo npc)
	{
		NpcSpawnEditor.Entry entry = entryFor(npc);
		int answer = JOptionPane.showConfirmDialog(
			this,
			"Delete NPC " + entry.id() + " at " + entry.worldX() + "," + entry.worldY() + "," + entry.plane() + "?",
			"Delete NPC Spawn",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		);
		if (answer != JOptionPane.YES_OPTION)
		{
			return;
		}
		Set<Integer> regions = Set.of(regionIdForWorldTile(entry.worldX(), entry.worldY()));
		performNpcSpawnEdit(
			NpcSpawnEditor.filesForDelete(entry),
			regions,
			"Deleted NPC spawn " + entry.id(),
			() -> NpcSpawnEditor.delete(entry)
		);
	}

	private void changeNpcFaceDirection(TerrainRenderer.NpcHoverInfo npc, NpcFaceDirection direction)
	{
		NpcSpawnEditor.Entry original = entryFor(npc);
		NpcSpawnEditor.Entry next = new NpcSpawnEditor.Entry(
			original.id(),
			original.name(),
			original.worldX(),
			original.worldY(),
			original.plane(),
			direction.faceDirection(),
			original.walkEnabled(),
			NpcSpawnIndex.SpawnSource.TSV
		);
		Set<Integer> regions = Set.of(regionIdForWorldTile(original.worldX(), original.worldY()));
		performNpcSpawnEdit(
			NpcSpawnEditor.filesForOverride(original),
			regions,
			"Changed NPC face direction",
			() -> NpcSpawnEditor.saveOverride(original, next)
		);
	}

	private void swapNpcSpawn(TerrainRenderer.NpcHoverInfo npc)
	{
		NpcSpawnEditor.Entry original = entryFor(npc);
		NpcBrowserDialog.Selection selection = chooseNpc(this, original.id());
		if (selection == null || selection.id() == original.id())
		{
			return;
		}
		NpcSpawnEditor.Entry next = new NpcSpawnEditor.Entry(
			selection.id(),
			selection.name(),
			original.worldX(),
			original.worldY(),
			original.plane(),
			original.faceDirection(),
			original.walkEnabled(),
			NpcSpawnIndex.SpawnSource.TSV
		);
		Set<Integer> regions = Set.of(regionIdForWorldTile(original.worldX(), original.worldY()));
		performNpcSpawnEdit(
			NpcSpawnEditor.filesForOverride(original),
			regions,
			"Swapped NPC " + original.id() + " to " + selection.id(),
			() -> NpcSpawnEditor.saveOverride(original, next)
		);
	}

	private void changeNpcSpawnTile(TerrainRenderer.NpcHoverInfo npc, Tile clickedTile)
	{
		NpcSpawnEditor.Entry original = entryFor(npc);
		Tile defaultTile = clickedTile == null
			? new Tile(original.worldX(), original.worldY(), original.plane())
			: clickedTile;
		Tile nextTile = promptTile("Change Spawn Tile", defaultTile);
		if (nextTile == null)
		{
			return;
		}

		NpcSpawnEditor.Entry next = new NpcSpawnEditor.Entry(
			original.id(),
			original.name(),
			nextTile.x,
			nextTile.y,
			nextTile.z,
			original.faceDirection(),
			original.walkEnabled(),
			NpcSpawnIndex.SpawnSource.TSV
		);
		Set<Integer> regions = new LinkedHashSet<>();
		regions.add(regionIdForWorldTile(original.worldX(), original.worldY()));
		regions.add(regionIdForWorldTile(next.worldX(), next.worldY()));
		performNpcSpawnEdit(
			NpcSpawnEditor.filesForOverride(original),
			regions,
			"Changed NPC spawn tile",
			() -> NpcSpawnEditor.saveOverride(original, next)
		);
	}

	private void setNpcWalkOverride(TerrainRenderer.NpcHoverInfo npc, boolean enabled)
	{
		NpcSpawnEditor.Entry original = entryFor(npc);
		NpcSpawnEditor.Entry next = new NpcSpawnEditor.Entry(
			original.id(),
			original.name(),
			original.worldX(),
			original.worldY(),
			original.plane(),
			original.faceDirection(),
			enabled,
			NpcSpawnIndex.SpawnSource.TSV
		);
		Set<Integer> regions = Set.of(regionIdForWorldTile(original.worldX(), original.worldY()));
		performNpcSpawnEdit(
			NpcSpawnEditor.filesForOverride(original),
			regions,
			enabled ? "Enabled NPC walk" : "Disabled NPC walk",
			() -> NpcSpawnEditor.saveOverride(original, next)
		);
	}

	private void promptSpawnNpc(Tile tile)
	{
		if (tile == null)
		{
			return;
		}
		NpcSpawnEditor.Entry entry = promptNpcSpawn(tile);
		if (entry == null)
		{
			return;
		}
		Set<Integer> regions = Set.of(regionIdForWorldTile(entry.worldX(), entry.worldY()));
		performNpcSpawnEdit(
			NpcSpawnEditor.filesForOverride(null),
			regions,
			"Added NPC spawn " + entry.id(),
			() -> NpcSpawnEditor.saveOverride(null, entry)
		);
	}

	private NpcSpawnEditor.Entry promptNpcSpawn(Tile tile)
	{
		NumberField id = new NumberField(8);
		JLabel selectedName = new JLabel(" ");
		JButton searchNpc = new JButton("Search...");
		searchNpc.addActionListener(e -> {
			NpcBrowserDialog.Selection selection = chooseNpc(searchNpc, id.getInt(null));
			if (selection != null)
			{
				id.setInt(selection.id());
				selectedName.setText(selection.name());
			}
		});
		JPanel idRow = new JPanel(new BorderLayout(6, 0));
		idRow.add(id, BorderLayout.CENTER);
		idRow.add(searchNpc, BorderLayout.EAST);
		JComboBox<NpcFaceDirection> face = new JComboBox<>(NpcFaceDirection.values());
		JComboBox<NpcWalkOverride> walk = new JComboBox<>(NpcWalkOverride.values());
		JPanel panel = new JPanel(new GridLayout(0, 2, 8, 6));
		panel.add(new JLabel("NPC ID"));
		panel.add(idRow);
		panel.add(new JLabel("Name"));
		panel.add(selectedName);
		panel.add(new JLabel("Tile"));
		panel.add(new JLabel(tile.x + "," + tile.y + "," + tile.z));
		panel.add(new JLabel("Face Direction"));
		panel.add(face);
		panel.add(new JLabel("Walk"));
		panel.add(walk);

		int answer = JOptionPane.showConfirmDialog(
			this,
			panel,
			"Spawn NPC",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE
		);
		if (answer != JOptionPane.OK_OPTION)
		{
			return null;
		}
		Integer npcId = id.getInt(null);
		if (npcId == null || npcId < 0)
		{
			JOptionPane.showMessageDialog(this, "NPC ID must be a non-negative number.", "Spawn NPC", JOptionPane.ERROR_MESSAGE);
			return null;
		}
		String npcName;
		try
		{
			npcName = npcName(npcId);
		}
		catch (IOException | RuntimeException ex)
		{
			JOptionPane.showMessageDialog(this, "Failed to load NPC " + npcId + ": " + rootMessage(ex), "Spawn NPC", JOptionPane.ERROR_MESSAGE);
			return null;
		}
		if (npcName.isBlank())
		{
			JOptionPane.showMessageDialog(this, "NPC ID " + npcId + " was not found in the cache.", "Spawn NPC", JOptionPane.ERROR_MESSAGE);
			return null;
		}
		NpcFaceDirection selectedFace = (NpcFaceDirection) face.getSelectedItem();
		NpcWalkOverride selectedWalk = (NpcWalkOverride) walk.getSelectedItem();
		return new NpcSpawnEditor.Entry(
			npcId,
			npcName,
			tile.x,
			tile.y,
			tile.z,
			selectedFace == null ? null : selectedFace.faceDirection(),
			selectedWalk == null ? null : selectedWalk.walkEnabled(),
			NpcSpawnIndex.SpawnSource.TSV
		);
	}

	private void openNpcBrowser()
	{
		try
		{
			NpcBrowserDialog.browse(this, loaderSession());
		}
		catch (IOException | RuntimeException ex)
		{
			JOptionPane.showMessageDialog(this, "Failed to open NPC browser: " + rootMessage(ex), "NPC Browser", JOptionPane.ERROR_MESSAGE);
		}
	}

	private NpcBrowserDialog.Selection chooseNpc(Component owner, Integer initialNpcId)
	{
		try
		{
			return NpcBrowserDialog.chooseNpc(owner, loaderSession(), initialNpcId);
		}
		catch (IOException | RuntimeException ex)
		{
			JOptionPane.showMessageDialog(this, "Failed to open NPC browser: " + rootMessage(ex), "NPC Browser", JOptionPane.ERROR_MESSAGE);
			return null;
		}
	}

	private String npcName(int npcId) throws IOException
	{
		TerrainRegionLoader.Session session = loaderSession();
		return session.npcName(npcId);
	}

	private Tile promptTile(String title, Tile fallback)
	{
		NumberField x = new NumberField(6);
		NumberField y = new NumberField(6);
		NumberField plane = new NumberField(2);
		x.setInt(fallback == null ? null : fallback.x);
		y.setInt(fallback == null ? null : fallback.y);
		plane.setInt(fallback == null ? 0 : fallback.z);
		JPanel panel = new JPanel(new GridLayout(0, 2, 8, 6));
		panel.add(new JLabel("X"));
		panel.add(x);
		panel.add(new JLabel("Y"));
		panel.add(y);
		panel.add(new JLabel("Plane"));
		panel.add(plane);
		int answer = JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (answer != JOptionPane.OK_OPTION)
		{
			return null;
		}
		Integer worldX = x.getInt(null);
		Integer worldY = y.getInt(null);
		Integer z = plane.getInt(0);
		if (worldX == null || worldY == null || z == null || z < 0 || z >= PLANE_COUNT)
		{
			JOptionPane.showMessageDialog(this, "Enter a valid tile and plane 0-3.", title, JOptionPane.ERROR_MESSAGE);
			return null;
		}
		return new Tile(worldX, worldY, z);
	}

	private void performNpcSpawnEdit(
		List<Path> changedFiles,
		Set<Integer> changedRegions,
		String successMessage,
		NpcSpawnEditTask task
	)
	{
		if (!confirmNpcSpawnBackup(changedFiles))
		{
			return;
		}
		try
		{
			task.run();
			reloadNpcSpawnRegions(changedRegions);
			detail.setText(successMessage);
		}
		catch (IOException | RuntimeException ex)
		{
			JOptionPane.showMessageDialog(this, ex.getMessage(), "NPC Spawn Edit Failed", JOptionPane.ERROR_MESSAGE);
		}
	}

	private boolean confirmNpcSpawnBackup(List<Path> changedFiles)
	{
		if (settings != null && settings.developerNpcSpawnBackupPromptSuppressed())
		{
			return true;
		}

		JCheckBox doNotAskAgain = new JCheckBox("Do not ask again");
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.add(new JLabel("Back up affected NPC spawn files before editing?"), BorderLayout.NORTH);
		panel.add(doNotAskAgain, BorderLayout.SOUTH);
		int answer = JOptionPane.showConfirmDialog(
			this,
			panel,
			"Back Up NPC Spawn Data",
			JOptionPane.YES_NO_CANCEL_OPTION,
			JOptionPane.WARNING_MESSAGE
		);
		if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION)
		{
			return false;
		}
		if (settings != null && doNotAskAgain.isSelected())
		{
			settings.setDeveloperNpcSpawnBackupPromptSuppressed(true);
		}
		if (answer != JOptionPane.YES_OPTION)
		{
			return true;
		}

		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose Backup Folder");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setCurrentDirectory(Path.of("").toAbsolutePath().normalize().toFile());
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return false;
		}

		Path targetDir = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
		try
		{
			backupNpcSpawnFiles(changedFiles, targetDir);
			return true;
		}
		catch (IOException ex)
		{
			JOptionPane.showMessageDialog(this, ex.getMessage(), "NPC Spawn Backup Failed", JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

	private void backupNpcSpawnFiles(List<Path> changedFiles, Path targetDir) throws IOException
	{
		Files.createDirectories(targetDir);
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		for (Path path : new LinkedHashSet<>(changedFiles))
		{
			if (path == null || !Files.isRegularFile(path))
			{
				continue;
			}
			String fileName = path.getFileName() == null ? "npc-spawns" : path.getFileName().toString();
			Path target = targetDir.resolve(fileName + "." + timestamp + ".bak");
			Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void reloadNpcSpawnRegions(Set<Integer> regionIds)
	{
		NpcSpawnIndex.reloadDefault();
		TerrainRegionLoader.Session session = loaderSession;
		if (session != null)
		{
			session.reloadNpcSpawnIndex();
		}
		for (int changedRegionId : regionIds)
		{
			reloadNpcSpawnRegion(changedRegionId);
		}
		rebuildScene();
		pumpRegionQueue();
	}

	private void reloadNpcSpawnRegion(int changedRegionId)
	{
		if (loadingRegionIds.contains(changedRegionId))
		{
			npcReloadAfterLoadRegionIds.add(changedRegionId);
			return;
		}
		TerrainMesh existing = loadedMeshes.remove(changedRegionId);
		if (existing != null)
		{
			existing.releaseVertexData();
		}
		loadedRegionIds.remove(changedRegionId);
		failedRegionIds.remove(changedRegionId);
		queuedRegionIds.remove(changedRegionId);
		regionLoadQueue.removeIf(region -> region == changedRegionId);
		if (desiredRegionIds.contains(changedRegionId))
		{
			loadedMeshes.put(changedRegionId, TerrainMesh.empty(changedRegionId));
			queueRegion(changedRegionId);
		}
	}

	private static NpcSpawnEditor.Entry entryFor(TerrainRenderer.NpcHoverInfo npc)
	{
		return new NpcSpawnEditor.Entry(
			npc.npcId(),
			npc.spawnName(),
			npc.spawnWorldX(),
			npc.spawnWorldY(),
			npc.spawnPlane(),
			npc.faceDirection(),
			npc.walkEnabled(),
			npc.source()
		);
	}

	private static int regionIdForWorldTile(int x, int y)
	{
		return TerrainScene.regionId(Math.floorDiv(x, TerrainScene.REGION_SIZE), Math.floorDiv(y, TerrainScene.REGION_SIZE));
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
			renderer.setHoverRay(null, null);
			npcHoverOverlay.setInfo(null);
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
			renderer.setHoverRay(camera.position(), rayDirection);
			hoveredTile = currentScene.pickTile(camera.position(), rayDirection, maxVisiblePlane);
			renderer.setHoveredTile(hoveredTile);
			updateTileHud();
		}
		catch (RuntimeException ex)
		{
			hoveredTile = null;
			renderer.setHoveredTile(null);
			renderer.setHoverRay(null, null);
			npcHoverOverlay.setInfo(null);
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

	private void updateViewDistanceHud()
	{
		viewDistanceHud.setText("View Distance " + ViewDistanceOption.forGridSize(streamGridSize));
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
		updateViewDistanceHud();
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
		boolean overlaysOnTop = pluginOverlaysOnTop;
		invokeRenderLater(() -> renderer.setPluginOverlayOnTop(overlaysOnTop));
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
		minimapVisible = !hidden;
		minimapOverlay.setVisible(!hidden);
		minimapButton.setText(hidden ? "Show Minimap" : "Hide Minimap");
		sceneLayer.revalidate();
		sceneLayer.doLayout();
		sceneLayer.repaint();
		saveViewerStateNow();
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
		renderer.setHoverRay(null, null);
		npcHoverOverlay.setInfo(null);
		renderer.setScene(currentScene);
		updateTileHud();
		detail.setText("Loading region " + initialLoadRegionId + "...");
		desiredRegionIds = Set.of(initialLoadRegionId);
		loadedMeshes.put(initialLoadRegionId, TerrainMesh.empty(initialLoadRegionId));
		rebuildScene();
		queueRegion(initialLoadRegionId);
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
		if (npcReloadAfterLoadRegionIds.remove(requestedRegionId))
		{
			if (mesh != null)
			{
				mesh.releaseVertexData();
			}
			failedRegionIds.remove(requestedRegionId);
			if (isRegionDesired(requestedRegionId))
			{
				loadedMeshes.put(requestedRegionId, TerrainMesh.empty(requestedRegionId));
				queueRegion(requestedRegionId);
				rebuildScene();
			}
			updateStreamStatus();
			pumpRegionQueue();
			return;
		}
		if (failure != null)
		{
			failedRegionIds.add(requestedRegionId);
			System.err.println("Failed to load 3D region " + requestedRegionId + ": " + rootMessage(failure));
			if (!cameraInitialized && requestedRegionId == initialLoadRegionId)
			{
				detail.setText("Failed to load center region: " + rootMessage(failure));
				reportStartupFailure(CACHE_LOAD_FAILURE_PREFIX + " " + rootMessage(failure));
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
		if (!cameraInitialized && requestedRegionId == initialLoadRegionId)
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

		List<Integer> desiredRegions = desiredRegionIds(streamCenterRegionId, cameraTile(), streamingPriorityDirection());
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

	private boolean isRegionStreamingBusy()
	{
		return !loadingRegionIds.isEmpty()
			|| !regionLoadQueue.isEmpty()
			|| !queuedRegionIds.isEmpty()
			|| loadedRegionIds.size() + failedRegionIds.size() < desiredRegionIds.size();
	}

	private List<Integer> desiredRegionIds(int centerRegionId, Tile centerTile, Vector3fc priorityDirection)
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
			.comparingInt((Integer id) -> regionPriorityBucket(centerRegionId, id, priorityDirection))
			.thenComparingInt(id -> chebyshevDistance(centerX, centerY, id))
			.thenComparingDouble(id -> -regionDirectionDot(id, priorityDirection))
			.thenComparingInt(id -> manhattanDistance(centerX, centerY, id)));
		return regions;
	}

	private Vector3f streamingPriorityDirection()
	{
		Vector3fc position = camera.position();
		Vector3f direction = new Vector3f();
		if (lastStreamPriorityCameraPositionValid)
		{
			float dx = position.x() - lastStreamPriorityCameraPosition.x;
			float dz = position.z() - lastStreamPriorityCameraPosition.z;
			float distanceSquared = dx * dx + dz * dz;
			if (distanceSquared > STREAM_PRIORITY_MOVEMENT_EPSILON * STREAM_PRIORITY_MOVEMENT_EPSILON)
			{
				direction.set(dx, 0.0f, dz).normalize();
			}
		}
		if (direction.lengthSquared() <= 0.000001f)
		{
			camera.direction(direction);
			direction.y = 0.0f;
			if (direction.lengthSquared() > 0.000001f)
			{
				direction.normalize();
			}
		}
		if (direction.lengthSquared() <= 0.000001f)
		{
			direction.set(0.0f, 0.0f, -1.0f);
		}
		lastStreamPriorityCameraPosition.set(position);
		lastStreamPriorityCameraPositionValid = true;
		return direction;
	}

	private int regionPriorityBucket(int centerRegionId, int regionId, Vector3fc priorityDirection)
	{
		if (regionId == centerRegionId)
		{
			return 0;
		}
		double dot = regionDirectionDot(regionId, priorityDirection);
		if (dot >= STREAM_PRIORITY_DIRECT_DOT)
		{
			return 1;
		}
		if (dot >= STREAM_PRIORITY_FORWARD_DOT)
		{
			return 2;
		}
		return dot >= STREAM_PRIORITY_SIDE_DOT ? 3 : 4;
	}

	private double regionDirectionDot(int regionId, Vector3fc priorityDirection)
	{
		if (priorityDirection == null)
		{
			return 0.0;
		}
		Vector3fc position = camera.position();
		double dx = regionCenterSceneX(regionId) - position.x();
		double dz = regionCenterSceneZ(regionId) - position.z();
		double length = Math.hypot(dx, dz);
		if (length <= 0.000001)
		{
			return 1.0;
		}
		return (dx / length) * priorityDirection.x() + (dz / length) * priorityDirection.z();
	}

	private double regionCenterSceneX(int regionId)
	{
		return (TerrainScene.regionX(regionId) - originRegionX) * (double) TerrainScene.REGION_SIZE;
	}

	private double regionCenterSceneZ(int regionId)
	{
		return (originRegionY - TerrainScene.regionY(regionId)) * (double) TerrainScene.REGION_SIZE;
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
			detail.setText("Loading center region " + initialLoadRegionId + "...");
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
		Map3DOverlay overlay = active3DLayer.overlay(new Map3DRenderContext(cameraTile(), List.copyOf(loadedRegionIds)));
		return overlay == null ? Map3DOverlay.empty() : overlay;
	}

	private void requestPluginOverlayRefresh()
	{
		pluginOverlayDirty = true;
	}

	private void updatePluginOverlayContext()
	{
		Tile cameraTile = cameraTile();
		Set<Integer> regionIds = Set.copyOf(loadedRegionIds);
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
		boolean cameraChanged = false;
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
				cameraChanged = true;
				scheduleStateSave();
				renderer.deferRetainedDataCompaction();
				renderer.invalidatePluginOverlayGeometry();
			}
		}
		updateStreamedRegions();
		if (cameraChanged || isRegionStreamingBusy())
		{
			renderer.deferRetainedDataCompaction();
		}
		updatePluginOverlayContext();
		updateCompassHud();
		maybeRefreshWikiSyncCombatLevel(now);
		maybeSaveViewerState(now);
		if (minimapOverlay != null)
		{
			minimapOverlay.repaint();
		}
		if (worldMapDock != null)
		{
			worldMapDock.updateCameraTile();
		}
		if (!ensureContext())
		{
			updateDebugOverlay();
			return;
		}
		long renderStartNanos = System.nanoTime();
		boolean rendered = false;
		TerrainRenderer.NpcHoverInfo npcHoverInfo = null;
		try
		{
			awtContext.makeCurrent();
			GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, awtContext.getFramebuffer(false));
			processRenderTasks();
			refreshPluginOverlayIfNeeded();
			renderer.render(camera, renderWidth(), renderHeight());
			npcHoverInfo = renderer.hoveredNpcInfo();
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
			updateNpcHoverOverlay(npcHoverInfo);
			updateDebugOverlay();
		}
	}

	private void updateNpcHoverOverlay(TerrainRenderer.NpcHoverInfo info)
	{
		if (!npcsVisible || !npcHoverTextVisible)
		{
			npcHoverOverlay.setInfo(null);
			return;
		}
		npcHoverOverlay.setInfo(info);
		if (npcHoverOverlay.isVisible())
		{
			sceneLayer.revalidate();
			sceneLayer.doLayout();
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
			reportGlFailure(message);
			return false;
		}
	}

	private void reportGlFailure(String message)
	{
		reportStartupFailure(message);
	}

	private void reportStartupFailure(String message)
	{
		if (glFailureReported || failureAction == null)
		{
			return;
		}
		glFailureReported = true;
		SwingUtilities.invokeLater(() -> failureAction.accept(message));
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

	private static void styleColorButton(AbstractButton button, Color color)
	{
		styleToolbarButton(button);
		Color swatch = color == null ? Color.WHITE : color;
		button.setOpaque(true);
		button.setBackground(swatch);
		button.setForeground(contrastColor(swatch));
	}

	private static Color contrastColor(Color color)
	{
		int luminance = (color.getRed() * 299 + color.getGreen() * 587 + color.getBlue() * 114) / 1000;
		return luminance >= 145 ? Color.BLACK : Color.WHITE;
	}

	private static void setFixedControlSize(Component component, Dimension size)
	{
		component.setMinimumSize(size);
		component.setPreferredSize(size);
		component.setMaximumSize(size);
	}

	private static void styleCollapseButton(AbstractButton button, Dimension size)
	{
		styleToolbarButton(button);
		button.setFont(button.getFont().deriveFont(Font.BOLD, 16f));
		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.setVerticalAlignment(SwingConstants.CENTER);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setMinimumSize(size);
		button.setPreferredSize(size);
		button.setMaximumSize(size);
	}

	private static void styleCheckBox(JCheckBox checkBox)
	{
		checkBox.setOpaque(false);
		checkBox.setFocusable(false);
		checkBox.setForeground(new Color(220, 220, 220));
	}

	private static JLabel toolbarLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(new Color(210, 210, 210));
		return label;
	}

	private static final class NpcHoverTextOverlay extends JComponent
	{
		private static final Color YELLOW = new Color(255, 242, 80);
		private static final Color GREEN = new Color(86, 232, 96);
		private static final Color WHITE = new Color(245, 245, 245);
		private static final Color OUTLINE = new Color(0, 0, 0, 230);
		private static final Color COMBAT_GREEN_10 = new Color(0x00FF00);
		private static final Color COMBAT_GREEN_7 = new Color(0x40FF00);
		private static final Color COMBAT_GREEN_4 = new Color(0x80FF00);
		private static final Color COMBAT_GREEN_1 = new Color(0xC0FF00);
		private static final Color COMBAT_RED_1 = new Color(0xFFB000);
		private static final Color COMBAT_RED_4 = new Color(0xFF7000);
		private static final Color COMBAT_RED_7 = new Color(0xFF3000);
		private static final Color COMBAT_RED_10 = new Color(0xFF0000);
		private TerrainRenderer.NpcHoverInfo info;
		private boolean useWikiSyncCombatColors;
		private Integer playerCombatLevel;

		private NpcHoverTextOverlay()
		{
			setOpaque(false);
			setFocusable(false);
			setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
		}

		private void setInfo(TerrainRenderer.NpcHoverInfo next)
		{
			boolean changed = info == null && next != null
				|| info != null && !info.equals(next);
			info = next;
			setVisible(info != null);
			if (changed)
			{
				revalidate();
				repaint();
			}
		}

		private void setCombatColorProfile(boolean enabled, Integer combatLevel)
		{
			Integer nextCombatLevel = combatLevel == null || combatLevel <= 0 ? null : combatLevel;
			boolean changed = useWikiSyncCombatColors != enabled
				|| playerCombatLevel == null && nextCombatLevel != null
				|| playerCombatLevel != null && !playerCombatLevel.equals(nextCombatLevel);
			useWikiSyncCombatColors = enabled;
			playerCombatLevel = nextCombatLevel;
			if (changed)
			{
				repaint();
			}
		}

		@Override
		public Dimension getPreferredSize()
		{
			if (info == null)
			{
				return new Dimension(1, 1);
			}
			FontMetrics metrics = getFontMetrics(getFont());
			int width = segmentWidth(metrics) + 10;
			int height = metrics.getHeight() + 8;
			return new Dimension(width, height);
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			if (info == null)
			{
				return;
			}
			Graphics2D g = (Graphics2D) g0.create();
			try
			{
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g.setFont(getFont());
				FontMetrics metrics = g.getFontMetrics();
				int x = 5;
				int baseline = 4 + metrics.getAscent();
				x = drawSegment(g, displayName(), YELLOW, x, baseline);
				if (info.hasCombatLevel())
				{
					x = drawSegment(g, " - Level: ", WHITE, x, baseline);
					x = drawSegment(g, Integer.toString(info.combatLevel()), combatLevelColor(), x, baseline);
				}
				x = drawSegment(g, " | ID: ", WHITE, x, baseline);
				drawSegment(g, Integer.toString(info.npcId()), YELLOW, x, baseline);
			}
			finally
			{
				g.dispose();
			}
		}

		private int segmentWidth(FontMetrics metrics)
		{
			int width = metrics.stringWidth(displayName());
			if (info.hasCombatLevel())
			{
				width += metrics.stringWidth(" - Level: ");
				width += metrics.stringWidth(Integer.toString(info.combatLevel()));
			}
			width += metrics.stringWidth(" | ID: ");
			width += metrics.stringWidth(Integer.toString(info.npcId()));
			return width;
		}

		private String displayName()
		{
			return info.name() == null || info.name().isBlank() ? "NPC" : info.name();
		}

		private Color combatLevelColor()
		{
			if (!useWikiSyncCombatColors || playerCombatLevel == null || info == null || !info.hasCombatLevel())
			{
				return GREEN;
			}
			int delta = info.combatLevel() - playerCombatLevel;
			if (delta < -9)
			{
				return COMBAT_GREEN_10;
			}
			if (delta < -6)
			{
				return COMBAT_GREEN_7;
			}
			if (delta < -3)
			{
				return COMBAT_GREEN_4;
			}
			if (delta < 0)
			{
				return COMBAT_GREEN_1;
			}
			if (delta > 9)
			{
				return COMBAT_RED_10;
			}
			if (delta > 6)
			{
				return COMBAT_RED_7;
			}
			if (delta > 3)
			{
				return COMBAT_RED_4;
			}
			if (delta > 0)
			{
				return COMBAT_RED_1;
			}
			return YELLOW;
		}

		private int drawSegment(Graphics2D g, String text, Color color, int x, int baseline)
		{
			g.setColor(OUTLINE);
			g.drawString(text, x - 1, baseline);
			g.drawString(text, x + 1, baseline);
			g.drawString(text, x, baseline - 1);
			g.drawString(text, x, baseline + 1);
			g.setColor(color);
			g.drawString(text, x, baseline);
			return x + g.getFontMetrics().stringWidth(text);
		}
	}

	private record AreaSearch(
		MapPanel mapPanel,
		MapAreaSearchPanel searchPanel
	)
	{
	}

	@FunctionalInterface
	private interface NpcSpawnEditTask
	{
		void run() throws IOException;
	}

	private enum NpcFaceDirection
	{
		DEFAULT("Default", null),
		NORTH_WEST("North-West", 0),
		NORTH("North", 1),
		NORTH_EAST("North-East", 2),
		WEST("West", 3),
		EAST("East", 4),
		SOUTH_WEST("South-West", 5),
		SOUTH("South", 6),
		SOUTH_EAST("South-East", 7);

		private final String label;
		private final Integer faceDirection;

		NpcFaceDirection(String label, Integer faceDirection)
		{
			this.label = label;
			this.faceDirection = faceDirection;
		}

		private Integer faceDirection()
		{
			return faceDirection;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private enum NpcWalkOverride
	{
		DEFAULT("Default", null),
		ENABLED("Enabled", Boolean.TRUE),
		DISABLED("Disabled", Boolean.FALSE);

		private final String label;
		private final Boolean walkEnabled;

		NpcWalkOverride(String label, Boolean walkEnabled)
		{
			this.label = label;
			this.walkEnabled = walkEnabled;
		}

		private Boolean walkEnabled()
		{
			return walkEnabled;
		}

		@Override
		public String toString()
		{
			return label;
		}
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
