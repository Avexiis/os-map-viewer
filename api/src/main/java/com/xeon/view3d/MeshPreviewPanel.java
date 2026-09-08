package com.xeon.view3d;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class MeshPreviewPanel extends JPanel
{
	private static final Color PREVIEW_TEXT_COLOR = new Color(185, 185, 185);
	private static final Color WIREFRAME_COLOR = new Color(0, 0, 0, 65);
	private static final ExecutorService PREVIEW_RENDERER = Executors.newSingleThreadExecutor(task ->
	{
		Thread thread = new Thread(task, "mesh-preview-renderer");
		thread.setDaemon(true);
		thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
		return thread;
	});

	private final Object renderLock = new Object();
	private Map3DMesh mesh;
	private String message = "No model selected";
	private double rotationRadians = Math.toRadians(25);
	private double pitchRadians = Math.toRadians(11);
	private double zoom = 1;
	private Point dragStart;
	private boolean wireframe = true;
	private BufferedImage renderedImage;
	private Map3DMesh lastRequestedMesh;
	private int lastRequestedWidth = -1;
	private int lastRequestedHeight = -1;
	private double lastRequestedRotation;
	private double lastRequestedPitch;
	private double lastRequestedZoom;
	private boolean lastRequestedWireframe;
	private long renderGeneration;
	private RenderRequest pendingRender;
	private boolean renderWorkerScheduled;
	private PreparedMesh preparedMesh;

	public MeshPreviewPanel()
	{
		setOpaque(true);
		setBackground(Color.BLACK);
		setBorder(BorderFactory.createLineBorder(new Color(40, 40, 40)));
		setPreferredSize(new Dimension(420, 420));
		setMinimumSize(new Dimension(100, 100));
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				requestRender(mesh);
			}
		});
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				dragStart = event.getPoint();
			}

			@Override
			public void mouseReleased(MouseEvent event)
			{
				dragStart = null;
			}
		});
		addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseDragged(MouseEvent event)
			{
				if (dragStart == null)
				{
					return;
				}
				rotationRadians += Math.toRadians((event.getX() - dragStart.x) * 0.7);
				pitchRadians += Math.toRadians((event.getY() - dragStart.y) * 0.7);
				dragStart = event.getPoint();
				requestRender(mesh);
				repaint();
			}
		});
		addMouseWheelListener(event ->
		{
			zoom = Math.max(0.1, Math.min(5, zoom - event.getPreciseWheelRotation() * 0.08));
			requestRender(mesh);
			repaint();
		});
	}

	public void setMesh(Map3DMesh mesh)
	{
		this.mesh = mesh;
		message = "No preview available";
		if (mesh == null || mesh.faceCount() == 0)
		{
			clearRendering();
		}
		else
		{
			requestRender(mesh);
		}
		repaint();
	}

	protected Map3DMesh mesh()
	{
		return mesh;
	}

	public void setMessage(String message)
	{
		mesh = null;
		this.message = message == null ? "No preview available" : message;
		clearRendering();
		repaint();
	}

	public void setRotationDegrees(int degrees)
	{
		rotationRadians = Math.toRadians(degrees);
		requestRender(mesh);
		repaint();
	}

	public void setZoomPercent(int percent)
	{
		zoom = Math.max(0.1, percent / 100.0);
		requestRender(mesh);
		repaint();
	}

	public void setWireframe(boolean enabled)
	{
		wireframe = enabled;
		requestRender(mesh);
		repaint();
	}

	public void resetView()
	{
		rotationRadians = Math.toRadians(25);
		pitchRadians = Math.toRadians(11);
		zoom = 1;
		requestRender(mesh);
		repaint();
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		requestRender(mesh);
	}

	@Override
	public void removeNotify()
	{
		clearRendering();
		super.removeNotify();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			Map3DMesh current = mesh();
			if (current == null || current.faceCount() == 0)
			{
				drawMessage(g);
				return;
			}
			requestRender(current);
			if (renderedImage != null)
			{
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(renderedImage, 0, 0, getWidth(), getHeight(), null);
			}
		}
		finally
		{
			g.dispose();
		}
	}

	private void drawMessage(Graphics2D g)
	{
		g.setColor(PREVIEW_TEXT_COLOR);
		FontMetrics metrics = g.getFontMetrics();
		g.drawString(message, Math.max(8, (getWidth() - metrics.stringWidth(message)) / 2), getHeight() / 2);
	}

	private void requestRender(Map3DMesh current)
	{
		int width = getWidth();
		int height = getHeight();
		if (current == null || current.faceCount() == 0 || width <= 0 || height <= 0)
		{
			return;
		}
		if (current == lastRequestedMesh && width == lastRequestedWidth && height == lastRequestedHeight
			&& rotationRadians == lastRequestedRotation && pitchRadians == lastRequestedPitch
			&& zoom == lastRequestedZoom && wireframe == lastRequestedWireframe)
		{
			return;
		}
		lastRequestedMesh = current;
		lastRequestedWidth = width;
		lastRequestedHeight = height;
		lastRequestedRotation = rotationRadians;
		lastRequestedPitch = pitchRadians;
		lastRequestedZoom = zoom;
		lastRequestedWireframe = wireframe;
		boolean scheduleWorker = false;
		synchronized (renderLock)
		{
			pendingRender = new RenderRequest(current, width, height, rotationRadians, pitchRadians, zoom, wireframe,
				renderGeneration);
			if (!renderWorkerScheduled)
			{
				renderWorkerScheduled = true;
				scheduleWorker = true;
			}
		}
		if (scheduleWorker)
		{
			PREVIEW_RENDERER.execute(this::renderNext);
		}
	}

	private void clearRendering()
	{
		lastRequestedMesh = null;
		lastRequestedWidth = -1;
		lastRequestedHeight = -1;
		renderedImage = null;
		synchronized (renderLock)
		{
			renderGeneration++;
			pendingRender = null;
			preparedMesh = null;
		}
	}

	private void renderNext()
	{
		RenderRequest request;
		synchronized (renderLock)
		{
			request = pendingRender;
			pendingRender = null;
		}
		BufferedImage image = null;
		try
		{
			if (request != null)
			{
				image = render(request);
			}
		}
		finally
		{
			BufferedImage completedImage = image;
			SwingUtilities.invokeLater(() -> completeRender(request, completedImage));
		}
	}

	private void completeRender(RenderRequest request, BufferedImage image)
	{
		boolean scheduleWorker = false;
		synchronized (renderLock)
		{
			if (request != null && image != null && request.generation == renderGeneration)
			{
				renderedImage = image;
			}
			renderWorkerScheduled = false;
			if (pendingRender != null)
			{
				renderWorkerScheduled = true;
				scheduleWorker = true;
			}
		}
		if (scheduleWorker)
		{
			PREVIEW_RENDERER.execute(this::renderNext);
		}
		repaint();
	}

	private BufferedImage render(RenderRequest request)
	{
		PreparedMesh prepared = preparedMesh(request);
		if (prepared == null || !isCurrent(request.generation))
		{
			return null;
		}
		int vertexCount = prepared.positions.length / 3;
		double[] projectedX = new double[vertexCount];
		double[] projectedY = new double[vertexCount];
		double[] projectedDepth = new double[vertexCount];
		double scale = Math.max(1, Math.min(request.width, request.height) - 36)
			/ Math.max(1e-9, prepared.diameter) * request.zoom;
		double yawCos = Math.cos(request.rotationRadians);
		double yawSin = Math.sin(request.rotationRadians);
		double pitchCos = Math.cos(request.pitchRadians);
		double pitchSin = Math.sin(request.pitchRadians);
		for (int vertex = 0; vertex < vertexCount; vertex++)
		{
			if ((vertex & 1023) == 0 && !isCurrent(request.generation))
			{
				return null;
			}
			int offset = vertex * 3;
			double x = prepared.positions[offset] - prepared.centerX;
			double y = prepared.positions[offset + 1] - prepared.centerY;
			double z = prepared.positions[offset + 2] - prepared.centerZ;
			double rotatedX = x * yawCos + z * yawSin;
			double rotatedZ = z * yawCos - x * yawSin;
			projectedX[vertex] = request.width / 2.0 + rotatedX * scale;
			projectedY[vertex] = request.height / 2.0 - (y * pitchCos - rotatedZ * pitchSin) * scale;
			projectedDepth[vertex] = y * pitchSin + rotatedZ * pitchCos;
		}
		BufferedImage image = new BufferedImage(request.width, request.height, BufferedImage.TYPE_INT_ARGB);
		int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		double[] depthBuffer = new double[pixels.length];
		Arrays.fill(depthBuffer, Double.NEGATIVE_INFINITY);
		int faceCount = prepared.triangles.length / 3;
		for (int face = 0; face < faceCount; face++)
		{
			if ((face & 255) == 0 && !isCurrent(request.generation))
			{
				return null;
			}
			int offset = face * 3;
			int a = prepared.triangles[offset];
			int b = prepared.triangles[offset + 1];
			int c = prepared.triangles[offset + 2];
			rasterizeTriangle(
				request,
				projectedX[a], projectedY[a], projectedDepth[a],
				projectedX[b], projectedY[b], projectedDepth[b],
				projectedX[c], projectedY[c], projectedDepth[c],
				prepared.colors[face],
				pixels,
				depthBuffer
			);
		}
		return isCurrent(request.generation) ? image : null;
	}

	private static void rasterizeTriangle(
		RenderRequest request,
		double ax, double ay, double az,
		double bx, double by, double bz,
		double cx, double cy, double cz,
		int color,
		int[] pixels,
		double[] depthBuffer)
	{
		double area = edge(ax, ay, bx, by, cx, cy);
		if (Math.abs(area) < 1e-9)
		{
			return;
		}
		double direction = area < 0 ? -1 : 1;
		area *= direction;
		int minX = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
		int maxX = Math.min(request.width - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
		int minY = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
		int maxY = Math.min(request.height - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
		double edgeA = Math.max(1e-9, Math.hypot(cx - bx, cy - by));
		double edgeB = Math.max(1e-9, Math.hypot(ax - cx, ay - cy));
		double edgeC = Math.max(1e-9, Math.hypot(bx - ax, by - ay));
		int edgeColor = request.wireframe ? wireframeColor(color) : color;
		for (int y = minY; y <= maxY; y++)
		{
			double py = y + 0.5;
			for (int x = minX; x <= maxX; x++)
			{
				double px = x + 0.5;
				double wa = edge(bx, by, cx, cy, px, py) * direction;
				double wb = edge(cx, cy, ax, ay, px, py) * direction;
				double wc = edge(ax, ay, bx, by, px, py) * direction;
				if (wa < 0 || wb < 0 || wc < 0)
				{
					continue;
				}
				double depth = (wa * az + wb * bz + wc * cz) / area;
				int pixel = y * request.width + x;
				if (depth <= depthBuffer[pixel])
				{
					continue;
				}
				depthBuffer[pixel] = depth;
				boolean edgePixel = request.wireframe
					&& Math.min(wa / edgeA, Math.min(wb / edgeB, wc / edgeC)) <= 0.75;
				pixels[pixel] = edgePixel ? edgeColor : color;
			}
		}
	}

	private static double edge(double ax, double ay, double bx, double by, double px, double py)
	{
		return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
	}

	private static int wireframeColor(int color)
	{
		int remaining = 255 - WIREFRAME_COLOR.getAlpha();
		int alpha = color >>> 24;
		int red = (color >> 16 & 0xFF) * remaining / 255;
		int green = (color >> 8 & 0xFF) * remaining / 255;
		int blue = (color & 0xFF) * remaining / 255;
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private PreparedMesh preparedMesh(RenderRequest request)
	{
		synchronized (renderLock)
		{
			if (preparedMesh != null && preparedMesh.source == request.mesh)
			{
				return preparedMesh;
			}
		}
		double[] positions = request.mesh.positions();
		int[] triangles = request.mesh.triangles();
		if (!isCurrent(request.generation))
		{
			return null;
		}
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (int offset = 0; offset < positions.length; offset += 3)
		{
			if ((offset & 3071) == 0 && !isCurrent(request.generation))
			{
				return null;
			}
			minX = Math.min(minX, positions[offset]);
			minY = Math.min(minY, positions[offset + 1]);
			minZ = Math.min(minZ, positions[offset + 2]);
			maxX = Math.max(maxX, positions[offset]);
			maxY = Math.max(maxY, positions[offset + 1]);
			maxZ = Math.max(maxZ, positions[offset + 2]);
		}
		int[] colors = new int[request.mesh.faceCount()];
		for (int face = 0; face < colors.length; face++)
		{
			if ((face & 1023) == 0 && !isCurrent(request.generation))
			{
				return null;
			}
			colors[face] = request.mesh.faceColor(face);
		}
		double xSize = maxX - minX;
		double ySize = maxY - minY;
		double zSize = maxZ - minZ;
		PreparedMesh prepared = new PreparedMesh(request.mesh, positions, triangles, colors,
			(minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2,
			Math.sqrt(xSize * xSize + ySize * ySize + zSize * zSize));
		synchronized (renderLock)
		{
			if (request.generation != renderGeneration)
			{
				return null;
			}
			preparedMesh = prepared;
		}
		return prepared;
	}

	private boolean isCurrent(long generation)
	{
		synchronized (renderLock)
		{
			return generation == renderGeneration;
		}
	}

	private record RenderRequest(Map3DMesh mesh, int width, int height, double rotationRadians,
			double pitchRadians, double zoom, boolean wireframe, long generation)
	{
	}

	private record PreparedMesh(Map3DMesh source, double[] positions, int[] triangles, int[] colors,
			double centerX, double centerY, double centerZ, double diameter)
	{
	}
}
