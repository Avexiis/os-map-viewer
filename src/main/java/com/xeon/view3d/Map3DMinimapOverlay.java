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
import com.xeon.view.MapPanel;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

final class Map3DMinimapOverlay extends JPanel
{
	private static final int MAP_SIZE = 164;
	private static final int BUTTON_HEIGHT = 30;
	private static final int PAD = 8;
	private static final double FULL_ZOOM_PIXELS_PER_TILE = 4.0;

	private final MapPanel mapPanel;
	private final Supplier<Tile> centerTileSupplier;
	private final DoubleSupplier headingRadiansSupplier;

	Map3DMinimapOverlay(MapPanel mapPanel, Supplier<Tile> centerTileSupplier,
	                    DoubleSupplier headingRadiansSupplier, Runnable openWorldMap)
	{
		super(new BorderLayout(0, 6));
		this.mapPanel = mapPanel;
		this.centerTileSupplier = centerTileSupplier;
		this.headingRadiansSupplier = headingRadiansSupplier;
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));
		setFocusable(false);

		MinimapCanvas minimap = new MinimapCanvas();
		add(minimap, BorderLayout.CENTER);

		JButton open = new JButton("Open World Map");
		open.setFocusable(false);
		open.setMargin(new Insets(3, 8, 3, 8));
		open.addActionListener(e -> {
			if (openWorldMap != null)
			{
				openWorldMap.run();
			}
		});
		add(open, BorderLayout.SOUTH);
	}

	void dispose()
	{
		mapPanel.dispose();
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(MAP_SIZE + PAD * 2, MAP_SIZE + BUTTON_HEIGHT + PAD * 2 + 6);
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		Graphics2D g = (Graphics2D) g0.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(new Color(18, 20, 22, 196));
			g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
			g.setColor(new Color(255, 255, 255, 62));
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
		}
		finally
		{
			g.dispose();
		}
		super.paintComponent(g0);
	}

	private final class MinimapCanvas extends JComponent
	{
		MinimapCanvas()
		{
			setOpaque(false);
			setFocusable(false);
			setPreferredSize(new Dimension(MAP_SIZE, MAP_SIZE));
			setMinimumSize(new Dimension(MAP_SIZE, MAP_SIZE));
			setMaximumSize(new Dimension(MAP_SIZE, MAP_SIZE));
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			Graphics2D g = (Graphics2D) g0.create();
			try
			{
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Rectangle mapBounds = new Rectangle(0, 0, getWidth(), getHeight());

				g.setColor(Color.BLACK);
				g.fillRect(mapBounds.x, mapBounds.y, mapBounds.width, mapBounds.height);

				Tile center = centerTileSupplier.get();
				if (center != null)
				{
					Shape oldClip = g.getClip();
					g.clip(mapBounds);
					Graphics2D mapGraphics = (Graphics2D) g.create();
					try
					{
						double cx = mapBounds.getCenterX();
						double cy = mapBounds.getCenterY();
						mapGraphics.rotate(-headingRadiansSupplier.getAsDouble(), cx, cy);
						int snapshotSize = (int) Math.ceil(Math.hypot(mapBounds.width, mapBounds.height)) + 8;
						Rectangle target = new Rectangle(
							(int) Math.round(cx - snapshotSize / 2.0),
							(int) Math.round(cy - snapshotSize / 2.0),
							snapshotSize,
							snapshotSize
						);
						mapPanel.paintMapSnapshot(mapGraphics, target, center, FULL_ZOOM_PIXELS_PER_TILE, true, false);
					}
					finally
					{
						mapGraphics.dispose();
					}
					g.setClip(oldClip);
				}

				g.setColor(new Color(0, 0, 0, 190));
				g.setStroke(new BasicStroke(2.0f));
				g.drawRect(mapBounds.x, mapBounds.y, mapBounds.width - 1, mapBounds.height - 1);
				g.setColor(new Color(255, 255, 255, 210));
				g.setStroke(new BasicStroke(1.0f));
				g.drawRect(mapBounds.x + 1, mapBounds.y + 1, mapBounds.width - 3, mapBounds.height - 3);
				drawCrosshair(g, mapBounds.x + mapBounds.width / 2, mapBounds.y + mapBounds.height / 2);
			}
			finally
			{
				g.dispose();
			}
		}

		private void drawCrosshair(Graphics2D g, int cx, int cy)
		{
			g.setStroke(new BasicStroke(3.0f));
			g.setColor(new Color(0, 0, 0, 180));
			g.drawLine(cx - 9, cy, cx + 9, cy);
			g.drawLine(cx, cy - 9, cx, cy + 9);
			g.setStroke(new BasicStroke(1.5f));
			g.setColor(new Color(255, 242, 80));
			g.drawLine(cx - 8, cy, cx + 8, cy);
			g.drawLine(cx, cy - 8, cx, cy + 8);
		}
	}
}
