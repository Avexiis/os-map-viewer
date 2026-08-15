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
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import net.runelite.rlawt.AWTContext;
import org.joml.Vector3f;

public final class Map3DPanel extends JPanel
{
	private static final int FRAME_DELAY_MS = 16;
	private static final int TOOLBAR_HEIGHT = 40;

	private final TerrainRegionLoader loader = new TerrainRegionLoader();
	private final TerrainRenderer renderer = new TerrainRenderer();
	private final FreeCamera camera = new FreeCamera();
	private final Canvas canvas;
	private final Path cacheDirectory;
	private final int regionId;
	private final JToggleButton lockCameraButton = new JToggleButton("Lock Camera");
	private final JLabel title = new JLabel("3D Region Viewer");
	private final JLabel detail = new JLabel("Loading terrain...");
	private final JLabel compassHud = new JLabel("Heading --");
	private final JLabel tileHud = new JLabel("Tile --");
	private final Timer renderTimer;
	private final Runnable exitAction;
	private SwingWorker<TerrainMesh, Void> loadWorker;
	private TerrainMesh currentMesh;
	private HoveredTile hoveredTile;
	private AWTContext awtContext;
	private Point lastMousePoint;
	private boolean disposed;
	private boolean glContextReady;
	private boolean glContextFailed;
	private boolean middleMouseLook;
	private boolean cameraLocked;
	private long lastFrameNanos;

	public Map3DPanel(Path cacheDirectory, int regionId, Runnable exitAction)
	{
		super(new BorderLayout());
		this.cacheDirectory = cacheDirectory;
		this.regionId = regionId;
		this.exitAction = exitAction;
		setOpaque(true);
		setBackground(Color.BLACK);

		canvas = new Canvas();
		canvas.setBackground(Color.BLACK);
		canvas.setFocusable(true);
		canvas.setIgnoreRepaint(true);

		add(buildToolbar(), BorderLayout.NORTH);
		add(canvas, BorderLayout.CENTER);
		installInputHandlers();

		renderTimer = new Timer(FRAME_DELAY_MS, e -> renderFrame());
		renderTimer.start();
		loadTerrain();
	}

	public void dispose()
	{
		disposed = true;
		if (loadWorker != null)
		{
			loadWorker.cancel(true);
		}
		renderTimer.stop();
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
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		controls.setOpaque(false);
		controls.add(lockCameraButton);
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
	}

	private void setKey(int keyCode, boolean pressed)
	{
		switch (keyCode)
		{
			case KeyEvent.VK_W -> camera.setForward(pressed);
			case KeyEvent.VK_S -> camera.setBackward(pressed);
			case KeyEvent.VK_A -> camera.setLeft(pressed);
			case KeyEvent.VK_D -> camera.setRight(pressed);
			case KeyEvent.VK_SHIFT -> camera.setRaise(pressed);
			case KeyEvent.VK_CONTROL -> camera.setLower(pressed);
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
		if (middleMouseLook && !cameraLocked && canvas.hasFocus() && lastMousePoint != null)
		{
			camera.rotate(point.x - lastMousePoint.x, point.y - lastMousePoint.y);
		}
		updateHoveredTile(event);
		lastMousePoint = point;
	}

	private void updateHoveredTile(MouseEvent event)
	{
		if (currentMesh == null || canvas.getWidth() <= 0 || canvas.getHeight() <= 0)
		{
			hoveredTile = null;
			renderer.setHoveredTile(null);
			updateTileHud();
			return;
		}
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
		hoveredTile = currentMesh.pickTile(camera.position(), rayDirection);
		renderer.setHoveredTile(hoveredTile);
		updateTileHud();
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

	private void loadTerrain()
	{
		if (loadWorker != null && !loadWorker.isDone())
		{
			loadWorker.cancel(true);
		}
		currentMesh = null;
		hoveredTile = null;
		renderer.setHoveredTile(null);
		updateTileHud();
		detail.setText("Loading region " + regionId + "...");
		loadWorker = new SwingWorker<>()
		{
			@Override
			protected TerrainMesh doInBackground() throws Exception
			{
				return loader.loadFullScene(cacheDirectory, regionId);
			}

			@Override
			protected void done()
			{
				if (disposed)
				{
					return;
				}
				if (loadWorker != this)
				{
					return;
				}
				try
				{
					TerrainMesh mesh = get();
					currentMesh = mesh;
					renderer.setMesh(mesh);
					camera.reset(mesh.initialCameraX(), mesh.initialCameraY(), mesh.initialCameraZ());
					if (!glContextFailed)
					{
						title.setText("3D Region " + mesh.regionId());
						detail.setText("Region " + mesh.regionX() + "," + mesh.regionY() + " - Full scene");
					}
					canvas.requestFocusInWindow();
				}
				catch (CancellationException ex)
				{
					detail.setText("Terrain loading cancelled");
				}
				catch (InterruptedException ex)
				{
					Thread.currentThread().interrupt();
					detail.setText("Terrain loading interrupted");
				}
				catch (ExecutionException ex)
				{
					Throwable cause = ex.getCause() == null ? ex : ex.getCause();
					detail.setText("Failed to load terrain: " + cause.getMessage());
				}
			}
		};
		loadWorker.execute();
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
		float deltaSeconds = Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000.0f);
		lastFrameNanos = now;
		if (!cameraLocked)
		{
			camera.update(deltaSeconds);
		}
		updateCompassHud();
		if (!ensureContext())
		{
			return;
		}
		try
		{
			awtContext.makeCurrent();
			renderer.render(camera, renderWidth(), renderHeight());
			awtContext.swapBuffers();
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
