/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice and
 *    this list of conditions.
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

import com.xeon.model.Tile;
import com.xeon.plugin.MapViewerPlugin;
import com.xeon.view.MapLayer;
import com.xeon.view.MapPanel;
import com.xeon.view.MapTool;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.LineBorder;

final class Map3DWorldMapDock extends JDialog
{
	private static final int BORDER_PX = 5;
	private static final int SAFE_INIT_W = 820;
	private static final int SAFE_INIT_H = 560;
	private static final int MIN_W = 420;
	private static final int MIN_H = 320;
	private static final double OPEN_ZOOM = 4.0;

	private final MapPanel mapPanel;
	private final Component boundsOwner;
	private final Supplier<Tile> focusTileSupplier;
	private final Consumer<Tile> warpConsumer;
	private final Timer clampDebounce;
	private final JComboBox<Integer> planeSelect;
	private MapViewerPlugin activePlugin;
	private ComponentAdapter clampListener;
	private boolean mapPanelDisposed;
	private int selectedPlane = 0;

	Map3DWorldMapDock(Component ownerForLocation, MapPanel mapPanel,
	                  Supplier<Tile> focusTileSupplier, Consumer<Tile> warpConsumer)
	{
		super(SwingUtilities.getWindowAncestor(ownerForLocation), "World Map", ModalityType.MODELESS);
		this.mapPanel = mapPanel;
		this.boundsOwner = ownerForLocation;
		this.focusTileSupplier = focusTileSupplier;
		this.warpConsumer = warpConsumer;
		this.clampDebounce = new Timer(50, e -> clampInsideOwner());
		this.planeSelect = new JComboBox<>(planeOptions(mapPanel.getPlaneCount()));
		this.clampDebounce.setRepeats(false);

		setUndecorated(true);
		setAlwaysOnTop(false);
		getRootPane().setBorder(new LineBorder(new Color(80, 82, 86), BORDER_PX, false));
		setLayout(new BorderLayout());

		mapPanel.setPlane(selectedPlane);
		JScrollPane scrollPane = new JScrollPane(mapPanel);
		scrollPane.setBorder(null);
		scrollPane.getViewport().setBackground(Color.BLACK);
		add(scrollPane, BorderLayout.CENTER);

		CloseOverlay overlay = new CloseOverlay();
		getRootPane().setGlassPane(overlay);
		overlay.setVisible(true);
		installPlaneSelector();

		mapPanel.setDockShiftDragEnabled(true);
		installDoubleClickWarp();
		enableEdgeDragResize(overlay, scrollPane);
		installOwnerClampListeners();
		applyInitialDockBounds(ownerForLocation);

		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent e)
			{
				disposeMapPanel();
			}
		});
	}

	void open()
	{
		setVisible(true);
		clampInsideOwner();
		focusCameraTile();
		toFront();
	}

	void setPlugin(MapViewerPlugin plugin)
	{
		if (activePlugin instanceof MapLayer layer)
		{
			mapPanel.removeLayer(layer);
		}
		if (activePlugin instanceof MapTool tool)
		{
			mapPanel.clearActiveTool(tool);
		}

		activePlugin = plugin;
		if (activePlugin instanceof MapLayer layer)
		{
			mapPanel.addLayer(layer);
		}
		if (activePlugin instanceof MapTool tool)
		{
			mapPanel.setActiveTool(tool);
		}
	}

	void repaintMap()
	{
		mapPanel.repaintVisible();
	}

	@Override
	public void dispose()
	{
		removeOwnerClampListeners();
		clampDebounce.stop();
		disposeMapPanel();
		super.dispose();
	}

	private void disposeMapPanel()
	{
		if (mapPanelDisposed)
		{
			return;
		}
		mapPanelDisposed = true;
		mapPanel.setDockShiftDragEnabled(false);
		mapPanel.dispose();
	}

	private void focusCameraTile()
	{
		Tile tile = focusTileSupplier == null ? null : focusTileSupplier.get();
		if (tile == null)
		{
			return;
		}
		Tile focusTile = new Tile(tile.x, tile.y, selectedPlane);
		SwingUtilities.invokeLater(() -> mapPanel.focusTile(focusTile, OPEN_ZOOM));
	}

	private void installPlaneSelector()
	{
		planeSelect.setFocusable(false);
		planeSelect.setSelectedItem(selectedPlane);
		planeSelect.addActionListener(e -> {
			Object selected = planeSelect.getSelectedItem();
			if (selected instanceof Integer plane)
			{
				selectedPlane = Math.max(0, Math.min(mapPanel.getPlaneCount() - 1, plane));
				mapPanel.setPlane(selectedPlane);
				mapPanel.repaintVisible();
			}
		});
	}

	private void installDoubleClickWarp()
	{
		mapPanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() < 2)
				{
					return;
				}
				Tile tile = mapPanel.pointToTile(e.getPoint());
				if (tile == null || warpConsumer == null)
				{
					return;
				}
				warpConsumer.accept(tile);
				e.consume();
			}
		});
	}

	private void applyInitialDockBounds(Component ownerForLocation)
	{
		Rectangle ownerBounds = ownerViewportBounds(ownerForLocation);
		if (ownerBounds == null)
		{
			setSize(new Dimension(SAFE_INIT_W, SAFE_INIT_H));
			setLocation(50, 50);
			return;
		}

		int w = Math.max(MIN_W, Math.min(SAFE_INIT_W, ownerBounds.width - 24));
		int h = Math.max(MIN_H, Math.min(SAFE_INIT_H, ownerBounds.height - 24));
		setSize(new Dimension(Math.min(w, ownerBounds.width), Math.min(h, ownerBounds.height)));
		setLocation(
			ownerBounds.x + Math.max(12, ownerBounds.width - getWidth() - 24),
			ownerBounds.y + 24
		);
		clampInsideOwner();
	}

	private void clampInsideOwner()
	{
		Rectangle ownerBounds = ownerViewportBounds(boundsOwner);
		if (ownerBounds == null)
		{
			return;
		}

		Rectangle b = getBounds();
		setBounds(clampedToOwner(b, ownerBounds));
	}

	private Rectangle clampedToOwner(Rectangle bounds, Rectangle ownerBounds)
	{
		Rectangle b = new Rectangle(bounds);
		b.width = Math.max(MIN_W, Math.min(b.width, ownerBounds.width));
		b.height = Math.max(MIN_H, Math.min(b.height, ownerBounds.height));
		if (b.x < ownerBounds.x)
		{
			b.x = ownerBounds.x;
		}
		if (b.y < ownerBounds.y)
		{
			b.y = ownerBounds.y;
		}
		if (b.x + b.width > ownerBounds.x + ownerBounds.width)
		{
			b.x = ownerBounds.x + ownerBounds.width - b.width;
		}
		if (b.y + b.height > ownerBounds.y + ownerBounds.height)
		{
			b.y = ownerBounds.y + ownerBounds.height - b.height;
		}
		return b;
	}

	private Rectangle ownerViewportBounds(Component ownerForLocation)
	{
		if (ownerForLocation != null && ownerForLocation.isShowing())
		{
			Point location = ownerForLocation.getLocationOnScreen();
			return new Rectangle(location.x, location.y, ownerForLocation.getWidth(), ownerForLocation.getHeight());
		}

		Window owner = getOwner();
		if (owner == null)
		{
			return null;
		}
		Rectangle bounds = owner.getBounds();
		Insets insets = owner.getInsets();
		return new Rectangle(
			bounds.x + Math.max(0, insets.left),
			bounds.y + Math.max(0, insets.top),
			Math.max(1, bounds.width - Math.max(0, insets.left) - Math.max(0, insets.right)),
			Math.max(1, bounds.height - Math.max(0, insets.top) - Math.max(0, insets.bottom))
		);
	}

	private void installOwnerClampListeners()
	{
		clampListener = new ComponentAdapter()
		{
			@Override
			public void componentMoved(ComponentEvent e)
			{
				scheduleClampInsideOwner();
			}

			@Override
			public void componentResized(ComponentEvent e)
			{
				scheduleClampInsideOwner();
			}

			@Override
			public void componentShown(ComponentEvent e)
			{
				scheduleClampInsideOwner();
			}
		};
		if (boundsOwner != null)
		{
			boundsOwner.addComponentListener(clampListener);
		}
		Window owner = getOwner();
		if (owner != null && owner != boundsOwner)
		{
			owner.addComponentListener(clampListener);
		}
	}

	private void removeOwnerClampListeners()
	{
		if (clampListener == null)
		{
			return;
		}
		if (boundsOwner != null)
		{
			boundsOwner.removeComponentListener(clampListener);
		}
		Window owner = getOwner();
		if (owner != null && owner != boundsOwner)
		{
			owner.removeComponentListener(clampListener);
		}
		clampListener = null;
	}

	private void scheduleClampInsideOwner()
	{
		if (isDisplayable())
		{
			clampDebounce.restart();
		}
	}

	private void enableEdgeDragResize(CloseOverlay overlay, JScrollPane scrollPane)
	{
		DockDragResizeHandler handler = new DockDragResizeHandler(overlay);
		addMouseListener(handler);
		addMouseMotionListener(handler);
		overlay.addMouseListener(handler);
		overlay.addMouseMotionListener(handler);
		scrollPane.addMouseListener(handler);
		scrollPane.addMouseMotionListener(handler);
		scrollPane.getViewport().addMouseListener(handler);
		scrollPane.getViewport().addMouseMotionListener(handler);
		mapPanel.addMouseListener(handler);
		mapPanel.addMouseMotionListener(handler);
	}

	private int hitTest(int x, int y)
	{
		int w = getWidth();
		int h = getHeight();
		int n = y <= BORDER_PX ? 1 : 0;
		int s = y >= h - BORDER_PX ? 2 : 0;
		int west = x <= BORDER_PX ? 4 : 0;
		int east = x >= w - BORDER_PX ? 8 : 0;
		return n | s | west | east;
	}

	private Cursor cursorFor(int mask, boolean move)
	{
		return switch (mask)
		{
			case 1 | 4 -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
			case 1 -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
			case 1 | 8 -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
			case 4 -> Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
			case 8 -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
			case 2 | 4 -> Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
			case 2 -> Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
			case 2 | 8 -> Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
			default -> move ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) : Cursor.getDefaultCursor();
		};
	}

	private final class DockDragResizeHandler extends MouseAdapter
	{
		private final CloseOverlay overlay;
		private Point dragStart;
		private Rectangle startBounds;
		private int edgeMask;
		private boolean moveDrag;

		DockDragResizeHandler(CloseOverlay overlay)
		{
			this.overlay = overlay;
		}

		@Override
		public void mousePressed(MouseEvent e)
		{
			Point p = toDialog(e);
			if (overlay.hitClose(p.x, p.y))
			{
				dragStart = null;
				startBounds = null;
				return;
			}
			edgeMask = hitTest(p.x, p.y);
			moveDrag = edgeMask == 0 && e.isShiftDown();
			if (edgeMask == 0 && !moveDrag)
			{
				dragStart = null;
				startBounds = null;
				return;
			}
			dragStart = e.getLocationOnScreen();
			startBounds = getBounds();
			e.consume();
		}

		@Override
		public void mouseReleased(MouseEvent e)
		{
			dragStart = null;
			startBounds = null;
			edgeMask = 0;
			moveDrag = false;
		}

		@Override
		public void mouseDragged(MouseEvent e)
		{
			if (dragStart == null || startBounds == null)
			{
				return;
			}

			Point scr = e.getLocationOnScreen();
			int dx = scr.x - dragStart.x;
			int dy = scr.y - dragStart.y;
			Rectangle b = new Rectangle(startBounds);
			if (moveDrag)
			{
				b.x += dx;
				b.y += dy;
			}
			else
			{
				if ((edgeMask & 1) != 0)
				{
					b.y += dy;
					b.height -= dy;
				}
				if ((edgeMask & 2) != 0)
				{
					b.height += dy;
				}
				if ((edgeMask & 4) != 0)
				{
					b.x += dx;
					b.width -= dx;
				}
				if ((edgeMask & 8) != 0)
				{
					b.width += dx;
				}
			}

			Rectangle ownerBounds = ownerViewportBounds(boundsOwner);
			setBounds(ownerBounds == null ? b : clampedToOwner(b, ownerBounds));
			repaint();
			e.consume();
		}

		@Override
		public void mouseMoved(MouseEvent e)
		{
			Point p = toDialog(e);
			if (overlay.hitClose(p.x, p.y))
			{
				e.getComponent().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				return;
			}
			e.getComponent().setCursor(cursorFor(hitTest(p.x, p.y), e.isShiftDown()));
		}

		private Point toDialog(MouseEvent e)
		{
			return SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), Map3DWorldMapDock.this);
		}
	}

	private final class CloseOverlay extends JComponent
	{
		private static final int BTN_SIZE = 20;
		private static final int PAD = 8;
		private static final int ARC = 6;
		private final JPanel planePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));

		CloseOverlay()
		{
			setOpaque(false);
			setLayout(null);
			planePanel.setOpaque(true);
			planePanel.setBackground(new Color(0, 0, 0, 165));
			JLabel label = new JLabel("Plane");
			label.setForeground(Color.WHITE);
			planePanel.add(label);
			planePanel.add(planeSelect);
			add(planePanel);
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (closeRect().contains(e.getPoint()))
					{
						Map3DWorldMapDock.this.dispose();
						e.consume();
					}
				}
			});
			addMouseMotionListener(new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					if (closeRect().contains(e.getPoint()))
					{
						setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
						return;
					}
					setCursor(cursorFor(hitTest(e.getX(), e.getY()), false));
				}
			});
		}

		@Override
		public void doLayout()
		{
			Dimension preferred = planePanel.getPreferredSize();
			planePanel.setBounds(PAD, PAD, preferred.width, preferred.height);
		}

		private Rectangle closeRect()
		{
			return new Rectangle(getWidth() - PAD - BTN_SIZE, PAD, BTN_SIZE, BTN_SIZE);
		}

		private boolean hitClose(int x, int y)
		{
			return closeRect().contains(x, y);
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			Graphics2D g = (Graphics2D) g0.create();
			try
			{
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Rectangle r = closeRect();
				g.setColor(new Color(0, 0, 0, 165));
				g.fillRoundRect(r.x, r.y, r.width, r.height, ARC, ARC);
				g.setColor(Color.WHITE);
				g.setStroke(new BasicStroke(2.0f));
				g.drawRoundRect(r.x, r.y, r.width, r.height, ARC, ARC);
				g.drawLine(r.x + 5, r.y + 5, r.x + r.width - 5, r.y + r.height - 5);
				g.drawLine(r.x + 5, r.y + r.height - 5, r.x + r.width - 5, r.y + 5);
			}
			finally
			{
				g.dispose();
			}
		}

		@Override
		public boolean contains(int x, int y)
		{
			return planePanel.getBounds().contains(x, y)
				|| closeRect().contains(x, y)
				|| Map3DWorldMapDock.this.hitTest(x, y) != 0;
		}
	}

	private static Integer[] planeOptions(int planeCount)
	{
		int count = Math.max(1, planeCount);
		Integer[] planes = new Integer[count];
		for (int i = 0; i < count; i++)
		{
			planes[i] = i;
		}
		return planes;
	}
}
