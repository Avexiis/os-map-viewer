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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLayeredPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.rlawt.AWTContext;
import org.joml.Vector3f;

public final class Map3DPanel extends JPanel
{
	private static final int FRAME_DELAY_MS = 16;
	private static final int TOOLBAR_HEIGHT = 40;
	private static final int STREAM_RADIUS_REGIONS = 2;
	private static final double SLOW_FRAME_MILLIS = 33.4;
	private static final double SLOW_RENDER_MILLIS = 20.0;
	private static final int HIGH_DRAW_CALLS = 128;
	private static final int HIGH_VERTEX_COUNT = 1_500_000;
	private static final String DEBUG_TEXT_COLOR = "#f2d84a";
	private static final String DEBUG_WARNING_COLOR = "#ff5656";

	private final TerrainRegionLoader loader = new TerrainRegionLoader();
	private final TerrainRenderer renderer = new TerrainRenderer();
	private final FreeCamera camera = new FreeCamera();
	private final Canvas canvas;
	private final JLayeredPane sceneLayer;
	private final Path cacheDirectory;
	private final int regionId;
	private final int originRegionX;
	private final int originRegionY;
	private final ExecutorService regionLoaderExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "3D region loader");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<Integer, TerrainMesh> loadedMeshes = new LinkedHashMap<>();
	private final List<Integer> regionLoadQueue = new ArrayList<>();
	private final Set<Integer> loadedRegionIds = new HashSet<>();
	private final Set<Integer> queuedRegionIds = new HashSet<>();
	private final Set<Integer> loadingRegionIds = new HashSet<>();
	private final Set<Integer> failedRegionIds = new HashSet<>();
	private final JToggleButton lockCameraButton = new JToggleButton("Lock Camera");
	private final JToggleButton debugOverlayButton = new JToggleButton("Debug");
	private final JLabel title = new JLabel("3D Region Viewer");
	private final JLabel detail = new JLabel("Loading terrain...");
	private final JLabel compassHud = new JLabel("Heading --");
	private final JLabel tileHud = new JLabel("Tile --");
	private final JLabel debugOverlay = new JLabel();
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
	private long lastFrameNanos;
	private long fpsWindowStartNanos;
	private int fpsWindowFrames;
	private double displayedFps;
	private double lastFrameMillis;
	private double lastRenderMillis;

	public Map3DPanel(Path cacheDirectory, int regionId, Runnable exitAction)
	{
		super(new BorderLayout());
		this.cacheDirectory = cacheDirectory;
		this.regionId = regionId;
		this.originRegionX = TerrainScene.regionX(regionId);
		this.originRegionY = TerrainScene.regionY(regionId);
		this.currentScene = TerrainScene.empty(originRegionX, originRegionY);
		this.streamCenterRegionId = regionId;
		this.exitAction = exitAction;
		setOpaque(true);
		setBackground(Color.BLACK);

		canvas = new Canvas();
		canvas.setBackground(Color.BLACK);
		canvas.setFocusable(true);
		canvas.setIgnoreRepaint(true);
		sceneLayer = buildSceneLayer();

		add(buildToolbar(), BorderLayout.NORTH);
		add(sceneLayer, BorderLayout.CENTER);
		installInputHandlers();

		renderTimer = new Timer(FRAME_DELAY_MS, e -> renderFrame());
		renderTimer.start();
		startRegionStreaming();
	}

	public void dispose()
	{
		disposed = true;
		renderTimer.stop();
		regionLoaderExecutor.shutdownNow();
		closeLoaderSession();
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
		labels.add(detail);
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
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		controls.setOpaque(false);
		controls.add(lockCameraButton);
		controls.add(debugOverlayButton);
		controls.add(compassHud);
		controls.add(tileHud);
		toolbar.add(controls, BorderLayout.CENTER);

		JButton exit = new JButton("Return to 2D Map");
		styleToolbarButton(exit);
		exit.addActionListener(e -> {
			if (exitAction != null)
			{
				exitAction.run();
			}
		});
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2));
		actions.setOpaque(false);
		actions.add(exit);
		toolbar.add(actions, BorderLayout.EAST);
		return toolbar;
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
				Dimension preferredSize = debugOverlay.getPreferredSize();
				int x = Math.max(8, size.width - preferredSize.width - 10);
				debugOverlay.setBounds(x, 10, preferredSize.width, preferredSize.height);
			}
		};
		layeredPane.setOpaque(true);
		layeredPane.setBackground(Color.BLACK);
		layeredPane.add(canvas, JLayeredPane.DEFAULT_LAYER);
		layeredPane.add(debugOverlay, JLayeredPane.PALETTE_LAYER);
		return layeredPane;
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
			public void mousePressed(MouseEvent e)
			{
				canvas.requestFocusInWindow();
				updateHoveredTile(e);
				if (e.getButton() == MouseEvent.BUTTON2)
				{
					middleMouseLook = true;
					lastMousePoint = e.getPoint();
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
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
			camera.rotate(point.x - lastMousePoint.x, point.y - lastMousePoint.y);
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
				camera.setPosition(currentScene.clampMovement(previousPosition, camera.position(), new Vector3f()));
			}
			updateStreamedRegions();
		}
		updateHoveredTile(event);
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
			camera.reset(mesh.initialCameraX(), mesh.initialCameraY(), mesh.initialCameraZ());
			cameraInitialized = true;
			title.setText("3D Region Stream");
			canvas.requestFocusInWindow();
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

		List<Integer> desiredRegions = desiredRegionIds(streamCenterRegionId);
		Set<Integer> desiredRegionSet = Set.copyOf(desiredRegions);
		desiredRegionIds = desiredRegionSet;
		boolean sceneChanged = loadedMeshes.keySet().removeIf(region -> !desiredRegionSet.contains(region));
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
		pumpRegionQueue();
		updateStreamStatus();
	}

	private List<Integer> desiredRegionIds(int centerRegionId)
	{
		int centerX = TerrainScene.regionX(centerRegionId);
		int centerY = TerrainScene.regionY(centerRegionId);
		List<Integer> regions = new ArrayList<>();
		for (int dx = -STREAM_RADIUS_REGIONS; dx <= STREAM_RADIUS_REGIONS; dx++)
		{
			for (int dy = -STREAM_RADIUS_REGIONS; dy <= STREAM_RADIUS_REGIONS; dy++)
			{
				regions.add(TerrainScene.regionId(centerX + dx, centerY + dy));
			}
		}
		regions.sort(Comparator
			.comparingInt((Integer id) -> chebyshevDistance(centerX, centerY, id))
			.thenComparingInt(id -> manhattanDistance(centerX, centerY, id)));
		return regions;
	}

	private void updateStreamStatus()
	{
		int desiredCount = (STREAM_RADIUS_REGIONS * 2 + 1) * (STREAM_RADIUS_REGIONS * 2 + 1);
		if (!cameraInitialized)
		{
			detail.setText("Loading center region " + regionId + "...");
			return;
		}

		detail.setText(
			"Regions " + loadedRegionIds.size() + "/" + desiredCount
				+ " loaded, " + loadingRegionIds.size() + " loading, " + regionLoadQueue.size() + " queued"
		);
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
		if (!cameraLocked)
		{
			camera.update(deltaSeconds);
			if (!currentScene.isEmpty())
			{
				camera.setPosition(currentScene.clampMovement(previousPosition, camera.position(), new Vector3f()));
			}
		}
		updateStreamedRegions();
		updateCompassHud();
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
}
