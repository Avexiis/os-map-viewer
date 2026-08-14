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

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import net.runelite.rlawt.AWTContext;

public final class Map3DPanel extends JPanel
{
	private static final int FRAME_DELAY_MS = 16;
	private static final int TOOLBAR_HEIGHT = 40;

	private final TerrainRegionLoader loader = new TerrainRegionLoader();
	private final TerrainRenderer renderer = new TerrainRenderer();
	private final FreeCamera camera = new FreeCamera();
	private final Canvas canvas;
	private final JLabel title = new JLabel("3D Region Viewer");
	private final JLabel detail = new JLabel("Loading terrain...");
	private final Timer renderTimer;
	private final Runnable exitAction;
	private SwingWorker<TerrainMesh, Void> loadWorker;
	private AWTContext awtContext;
	private Cursor defaultCursor;
	private Cursor blankCursor;
	private Robot robot;
	private Point lastMousePoint;
	private boolean disposed;
	private boolean glContextReady;
	private boolean glContextFailed;
	private boolean mouseCaptured;
	private boolean suppressNextMouseMove;
	private long lastFrameNanos;

	public Map3DPanel(Path cacheDirectory, int regionId, int plane, Runnable exitAction)
	{
		super(new BorderLayout());
		this.exitAction = exitAction;
		setOpaque(true);
		setBackground(Color.BLACK);

		canvas = new Canvas();
		canvas.setBackground(Color.BLACK);
		canvas.setFocusable(true);
		canvas.setIgnoreRepaint(true);
		defaultCursor = canvas.getCursor();

		add(buildToolbar(), BorderLayout.NORTH);
		add(canvas, BorderLayout.CENTER);
		installInputHandlers();

		renderTimer = new Timer(FRAME_DELAY_MS, e -> renderFrame());
		renderTimer.start();
		loadTerrain(cacheDirectory, regionId, plane);
	}

	public void dispose()
	{
		disposed = true;
		if (loadWorker != null)
		{
			loadWorker.cancel(true);
		}
		renderTimer.stop();
		releaseMouse();
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
		JPanel labels = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
		labels.setOpaque(false);
		labels.add(title);
		labels.add(detail);
		toolbar.add(labels, BorderLayout.WEST);

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
				captureMouse();
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
					releaseMouse();
				}
			}
			default ->
			{
			}
		}
	}

	private void handleMouseMovement(MouseEvent event)
	{
		if (mouseCaptured)
		{
			if (suppressNextMouseMove)
			{
				suppressNextMouseMove = false;
				return;
			}
			Point center = canvasCenterOnScreen();
			if (center == null)
			{
				return;
			}
			int deltaX = event.getXOnScreen() - center.x;
			int deltaY = event.getYOnScreen() - center.y;
			if (deltaX != 0 || deltaY != 0)
			{
				camera.rotate(deltaX, deltaY);
				centerMouse(center);
			}
			return;
		}

		Point point = event.getPoint();
		if (canvas.hasFocus() && lastMousePoint != null)
		{
			camera.rotate(point.x - lastMousePoint.x, point.y - lastMousePoint.y);
		}
		lastMousePoint = point;
	}

	private void captureMouse()
	{
		if (mouseCaptured)
		{
			return;
		}
		mouseCaptured = true;
		lastMousePoint = null;
		if (blankCursor == null)
		{
			Image image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(0, 0), "3D camera cursor");
		}
		canvas.setCursor(blankCursor);
		Point center = canvasCenterOnScreen();
		if (center == null)
		{
			return;
		}
		if (robot == null)
		{
			try
			{
				GraphicsConfiguration configuration = canvas.getGraphicsConfiguration();
				robot = configuration == null ? new Robot() : new Robot(configuration.getDevice());
			}
			catch (AWTException ex)
			{
				System.err.println("Mouse capture is unavailable: " + ex.getMessage());
				mouseCaptured = false;
				canvas.setCursor(defaultCursor == null ? Cursor.getDefaultCursor() : defaultCursor);
				return;
			}
		}
		centerMouse(center);
	}

	private void releaseMouse()
	{
		mouseCaptured = false;
		suppressNextMouseMove = false;
		lastMousePoint = null;
		if (canvas != null)
		{
			canvas.setCursor(defaultCursor == null ? Cursor.getDefaultCursor() : defaultCursor);
		}
	}

	private Point canvasCenterOnScreen()
	{
		if (!canvas.isShowing())
		{
			return null;
		}
		Point location = canvas.getLocationOnScreen();
		return new Point(location.x + canvas.getWidth() / 2, location.y + canvas.getHeight() / 2);
	}

	private void centerMouse(Point center)
	{
		if (robot == null)
		{
			return;
		}
		suppressNextMouseMove = true;
		robot.mouseMove(center.x, center.y);
	}

	private void loadTerrain(Path cacheDirectory, int regionId, int plane)
	{
		detail.setText("Loading region " + regionId + "...");
		loadWorker = new SwingWorker<>()
		{
			@Override
			protected TerrainMesh doInBackground() throws Exception
			{
				return loader.load(cacheDirectory, regionId, plane);
			}

			@Override
			protected void done()
			{
				if (disposed)
				{
					return;
				}
				try
				{
					TerrainMesh mesh = get();
					renderer.setMesh(mesh);
					camera.reset(mesh.initialCameraX(), mesh.initialCameraY(), mesh.initialCameraZ());
					if (!glContextFailed)
					{
						title.setText("3D Region " + mesh.regionId());
						detail.setText("Region " + mesh.regionX() + "," + mesh.regionY() + " - Plane " + mesh.plane());
					}
					canvas.requestFocusInWindow();
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
		camera.update(deltaSeconds);
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

	private static void styleToolbarButton(JButton button)
	{
		button.setFocusable(false);
		button.setMargin(new Insets(4, 10, 4, 10));
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(80, 80, 80)),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)
		));
	}
}
