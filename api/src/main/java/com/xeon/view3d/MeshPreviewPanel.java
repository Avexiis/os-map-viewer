package com.xeon.view3d;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

public class MeshPreviewPanel extends JPanel
{
	private Map3DMesh mesh;
	private String message = "No model selected";
	private double rotationRadians = Math.toRadians(25);
	private double pitchRadians = Math.toRadians(11);
	private double zoom = 1;
	private Point dragStart;
	private boolean wireframe = true;

	public MeshPreviewPanel()
	{
		setOpaque(true);
		setBackground(Color.BLACK);
		setBorder(BorderFactory.createLineBorder(new Color(40, 40, 40)));
		setPreferredSize(new Dimension(420, 420));
		setMinimumSize(new Dimension(100, 100));
		addMouseListener(new MouseAdapter()
		{
			@Override public void mousePressed(MouseEvent e) { dragStart = e.getPoint(); }
			@Override public void mouseReleased(MouseEvent e) { dragStart = null; }
		});
		addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override public void mouseDragged(MouseEvent e)
			{
				if (dragStart == null) return;
				rotationRadians += Math.toRadians((e.getX() - dragStart.x) * 0.7);
				pitchRadians += Math.toRadians((e.getY() - dragStart.y) * 0.7);
				dragStart = e.getPoint();
				repaint();
			}
		});
		addMouseWheelListener(e -> {
			zoom = Math.max(0.1, Math.min(5, zoom - e.getPreciseWheelRotation() * 0.08));
			repaint();
		});
	}

	public void setMesh(Map3DMesh mesh)
	{
		this.mesh = mesh;
		message = "No preview available";
		repaint();
	}

	protected Map3DMesh mesh() { return mesh; }

	public void setMessage(String message)
	{
		mesh = null;
		this.message = message == null ? "No preview available" : message;
		repaint();
	}

	public void setRotationDegrees(int degrees) { rotationRadians = Math.toRadians(degrees); repaint(); }
	public void setZoomPercent(int percent) { zoom = Math.max(0.1, percent / 100.0); repaint(); }
	public void setWireframe(boolean enabled) { wireframe = enabled; repaint(); }
	public void resetView()
	{
		rotationRadians = Math.toRadians(25);
		pitchRadians = Math.toRadians(11);
		zoom = 1;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			Map3DMesh current = mesh();
			if (current == null || current.faceCount() == 0)
			{
				g.setColor(new Color(185, 185, 185));
				FontMetrics metrics = g.getFontMetrics();
				g.drawString(message, Math.max(8, (getWidth() - metrics.stringWidth(message)) / 2), getHeight() / 2);
				return;
			}
			drawModel(g, current);
		}
		finally { g.dispose(); }
	}

	private void drawModel(Graphics2D g, Map3DMesh model)
	{
		double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
		double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
		for (int v = 0; v < model.vertexCount(); v++)
		{
			for (int axis = 0; axis < 3; axis++)
			{
				min[axis] = Math.min(min[axis], model.coordinate(v, axis));
				max[axis] = Math.max(max[axis], model.coordinate(v, axis));
			}
		}
		double diameter = Math.sqrt(Math.pow(max[0] - min[0], 2) + Math.pow(max[1] - min[1], 2)
			+ Math.pow(max[2] - min[2], 2));
		double scale = Math.max(1, Math.min(getWidth(), getHeight()) - 36) / Math.max(1e-9, diameter) * zoom;
		double yawCos = Math.cos(rotationRadians), yawSin = Math.sin(rotationRadians);
		double pitchCos = Math.cos(pitchRadians), pitchSin = Math.sin(pitchRadians);
		ProjectedVertex[] projected = new ProjectedVertex[model.vertexCount()];
		for (int v = 0; v < projected.length; v++)
		{
			double x = model.coordinate(v, 0) - (min[0] + max[0]) / 2;
			double y = model.coordinate(v, 1) - (min[1] + max[1]) / 2;
			double z = model.coordinate(v, 2) - (min[2] + max[2]) / 2;
			double rotatedX = x * yawCos + z * yawSin;
			double rotatedZ = z * yawCos - x * yawSin;
			projected[v] = new ProjectedVertex(
				(int) Math.round(getWidth() / 2.0 + rotatedX * scale),
				(int) Math.round(getHeight() / 2.0 - (y * pitchCos - rotatedZ * pitchSin) * scale),
				y * pitchSin + rotatedZ * pitchCos);
		}
		List<Triangle> triangles = new ArrayList<>(model.faceCount());
		for (int f = 0; f < model.faceCount(); f++)
		{
			ProjectedVertex a = projected[model.vertexIndex(f, 0)];
			ProjectedVertex b = projected[model.vertexIndex(f, 1)];
			ProjectedVertex c = projected[model.vertexIndex(f, 2)];
			triangles.add(new Triangle(new int[]{a.x, b.x, c.x}, new int[]{a.y, b.y, c.y},
				(a.depth + b.depth + c.depth) / 3, new Color(model.faceColor(f), true)));
		}
		triangles.sort(Comparator.comparingDouble(Triangle::depth));
		for (Triangle triangle : triangles)
		{
			Polygon polygon = new Polygon(triangle.x, triangle.y, 3);
			g.setColor(triangle.color);
			g.fillPolygon(polygon);
			if (wireframe)
			{
				g.setColor(new Color(0, 0, 0, 65));
				g.drawPolygon(polygon);
			}
		}
	}

	private record ProjectedVertex(int x, int y, double depth) { }
	private record Triangle(int[] x, int[] y, double depth, Color color) { }
}
