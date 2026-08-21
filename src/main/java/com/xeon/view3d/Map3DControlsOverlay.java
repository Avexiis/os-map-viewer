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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class Map3DControlsOverlay extends JPanel
{
	Map3DControlsOverlay()
	{
		super(new GridBagLayout());
		setOpaque(false);
		setFocusable(false);
		setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		rebuild(null, List.of());
	}

	void setPluginControls(String pluginName, List<Map3DControlHint> hints)
	{
		rebuild(pluginName, hints == null ? List.of() : hints);
	}

	private void rebuild(String pluginName, List<Map3DControlHint> hints)
	{
		removeAll();
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1.0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(0, 0, 4, 0);

		addRow(c, title("3D Viewer Controls"));
		addRow(c, line("W/A/S/D", "move"));
		addRow(c, line("Middle mouse", "look"));
		addRow(c, line("Mouse wheel", "move forward/back"));
		addRow(c, line("Space/Page Up", "raise"));
		addRow(c, line("Ctrl/Page Down", "lower"));
		addRow(c, line("Shift", "fast move"));
		addRow(c, line("Arrow keys", "turn/look"));

		c.insets = new Insets(7, 0, 4, 0);
		addRow(c, title("World Map Controls"));
		c.insets = new Insets(0, 0, 4, 0);
		addRow(c, line("Open World Map", "show floating map"));
		addRow(c, line("Double-click tile", "warp camera"));
		addRow(c, line("Drag", "pan map"));
		addRow(c, line("Mouse wheel", "zoom map"));
		addRow(c, line("Shift-drag", "move floating map"));
		addRow(c, line("Drag edges", "resize floating map"));
		addRow(c, line("X", "close floating map"));

		if (!hints.isEmpty())
		{
			c.insets = new Insets(7, 0, 4, 0);
			addRow(c, title(pluginName == null || pluginName.isBlank() ? "Plugin Controls" : pluginName + " Controls"));
			c.insets = new Insets(0, 0, 4, 0);
			for (Map3DControlHint hint : hints)
			{
				if (hint == null || hint.input().isBlank() || hint.action().isBlank())
				{
					continue;
				}
				addRow(c, line(hint.input(), hint.action()));
			}
		}
		revalidate();
		repaint();
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(252, super.getPreferredSize().height);
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		Graphics2D g = (Graphics2D) g0.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(new Color(18, 20, 22, 214));
			g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
			g.setColor(new Color(255, 255, 255, 64));
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
		}
		finally
		{
			g.dispose();
		}
		super.paintComponent(g0);
	}

	private void addRow(GridBagConstraints c, JLabel label)
	{
		add(label, c);
		c.gridy++;
	}

	private static JLabel title(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(new Color(244, 244, 244));
		label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
		return label;
	}

	private static JLabel line(String key, String value)
	{
		JLabel label = new JLabel("<html><b>" + escape(key) + "</b> - " + escape(value) + "</html>");
		label.setForeground(new Color(226, 226, 226));
		label.setFont(label.getFont().deriveFont(12f));
		return label;
	}

	private static String escape(String value)
	{
		return value
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
	}
}
