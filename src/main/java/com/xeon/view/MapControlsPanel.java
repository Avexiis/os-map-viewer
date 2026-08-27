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
package com.xeon.view;

import com.xeon.io.Paths;
import com.xeon.model.Tile;
import com.xeon.util.NumberField;
import com.xeon.util.wikisync.WikiSyncManager;
import com.xeon.util.wikisync.WikiSyncProfileControls;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Objects;
import java.util.function.Consumer;

public final class MapControlsPanel extends JPanel
{
	private static final int EXPANDED_WIDTH = 330;
	private static final int WIKISYNC_CONTROL_WIDTH = 252;
	private static final int COLLAPSED_SIZE = 44;
	private static final Dimension EXPANDED_COLLAPSE_BUTTON_SIZE = new Dimension(30, 28);
	private static final Dimension COLLAPSED_BUTTON_SIZE = new Dimension(36, 36);
	private static final int REGION_TILE_SIZE = 64;

	private final JCheckBox chkGrid = new JCheckBox("Grid", false);
	private final JCheckBox chkRegionCoords = new JCheckBox("RX,RY", false);
	private final JCheckBox chkRegionIds = new JCheckBox("Region IDs", false);
	private final JCheckBox chkMapText = new JCheckBox("Area/Region Text", true);
	private final JCheckBox chkMapIcons = new JCheckBox("Map Icons", true);
	private final JCheckBox chkMapFeatureTooltips = new JCheckBox("Icon Tooltips", true);
	private final JCheckBox chkNpcDots = new JCheckBox("NPC Dots", false);
	private final JToggleButton btnLock = new JToggleButton("Lock Map");
	private final JComboBox<Integer> cbPlane = new JComboBox<>();
	private final NumberField tfRegionId = new NumberField(6);
	private final NumberField tfRegionIdZ = new NumberField(2);
	private final NumberField tfRegionX = new NumberField(4);
	private final NumberField tfRegionY = new NumberField(4);
	private final NumberField tfRegionCoordsZ = new NumberField(2);
	private final NumberField tfX = new NumberField(6);
	private final NumberField tfY = new NumberField(6);
	private final NumberField tfZ = new NumberField(2);
	private final WikiSyncProfileControls wikiSyncProfileControls = new WikiSyncProfileControls(true);
	private final JButton btnJumpRegionId = new JButton("Jump");
	private final JButton btnJumpRegionCoords = new JButton("Jump");
	private final JButton btnJump = new JButton("Jump");
	private final JButton btnSwitch3D = new JButton("Switch to 3D Map");
	private final JButton btnCollapse = new JButton(">");
	private final JLabel lbTitle = new JLabel("Map Controls");
	private final JPanel header = new JPanel(new BorderLayout(8, 0));
	private final JPanel body = new JPanel(new BorderLayout(0, 8));
	private final Border expandedBorder = BorderFactory.createCompoundBorder(
		BorderFactory.createLineBorder(new Color(65, 65, 65)),
		new EmptyBorder(10, 10, 10, 10)
	);
	private final Border collapsedBorder = BorderFactory.createCompoundBorder(
		BorderFactory.createLineBorder(new Color(65, 65, 65)),
		new EmptyBorder(4, 4, 4, 4)
	);
	private boolean suppressPlaneEvents = false;
	private boolean collapsed = false;

	public final Event<Boolean> onToggleGrid = new Event<>();
	public final Event<Boolean> onToggleRegionCoordinates = new Event<>();
	public final Event<Boolean> onToggleRegionIds = new Event<>();
	public final Event<Boolean> onToggleMapText = new Event<>();
	public final Event<Boolean> onToggleMapIcons = new Event<>();
	public final Event<Boolean> onToggleMapFeatureTooltips = new Event<>();
	public final Event<Boolean> onToggleNpcDots = new Event<>();
	public final Event<Boolean> onToggleLocked = new Event<>();
	public final Event<Integer> onPlaneChanged = new Event<>();
	public final Event<Tile> onJumpToTile = new Event<>();
	public final Event<Void> onSwitchTo3DMap = new Event<>();

	public MapControlsPanel()
	{
		setLayout(new BorderLayout());
		setOpaque(true);
		setBorder(expandedBorder);

		styleButton(btnLock);
		styleButton(btnJumpRegionId);
		styleButton(btnJumpRegionCoords);
		styleButton(btnJump);
		styleButton(btnSwitch3D);
		styleCollapseButton(btnCollapse, EXPANDED_COLLAPSE_BUTTON_SIZE);
		lbTitle.setFont(lbTitle.getFont().deriveFont(Font.BOLD, 13f));

		header.setOpaque(false);
		header.add(lbTitle, BorderLayout.WEST);
		header.add(btnCollapse, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		body.setOpaque(false);
		body.setBorder(new EmptyBorder(8, 0, 0, 0));
		add(body, BorderLayout.CENTER);

		JPanel modeRows = new JPanel();
		modeRows.setOpaque(false);
		modeRows.setLayout(new BoxLayout(modeRows, BoxLayout.Y_AXIS));

		JPanel topToggleRow = new JPanel(new BorderLayout(8, 0));
		topToggleRow.setOpaque(false);
		JPanel coreChecks = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		coreChecks.setOpaque(false);
		coreChecks.add(chkGrid);
		coreChecks.add(chkRegionCoords);
		coreChecks.add(chkRegionIds);
		topToggleRow.add(coreChecks, BorderLayout.WEST);
		topToggleRow.add(btnLock, BorderLayout.EAST);
		modeRows.add(topToggleRow);

		JPanel atlasToggleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		atlasToggleRow.setOpaque(false);
		atlasToggleRow.add(chkMapText);
		atlasToggleRow.add(chkMapIcons);
		modeRows.add(Box.createVerticalStrut(6));
		modeRows.add(atlasToggleRow);

		JPanel tooltipToggleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		tooltipToggleRow.setOpaque(false);
		tooltipToggleRow.add(chkMapFeatureTooltips);
		tooltipToggleRow.add(chkNpcDots);
		modeRows.add(Box.createVerticalStrut(4));
		modeRows.add(tooltipToggleRow);

		JPanel planeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		planeRow.setOpaque(false);
		planeRow.add(new JLabel("Plane"));
		cbPlane.setPreferredSize(new Dimension(70, 28));
		planeRow.add(cbPlane);
		modeRows.add(Box.createVerticalStrut(6));
		modeRows.add(planeRow);
		body.add(modeRows, BorderLayout.NORTH);

		JPanel jumpRows = new JPanel();
		jumpRows.setOpaque(false);
		jumpRows.setLayout(new BoxLayout(jumpRows, BoxLayout.Y_AXIS));
		jumpRows.add(new JSeparator());
		jumpRows.add(Box.createVerticalStrut(8));
		wikiSyncProfileControls.setContentWidth(WIKISYNC_CONTROL_WIDTH);
		jumpRows.add(leftAlignedRow(wikiSyncProfileControls));
		jumpRows.add(Box.createVerticalStrut(8));
		jumpRows.add(new JSeparator());
		jumpRows.add(Box.createVerticalStrut(8));
		jumpRows.add(regionIdRow());
		jumpRows.add(Box.createVerticalStrut(6));
		jumpRows.add(regionCoordinateRow());
		jumpRows.add(Box.createVerticalStrut(8));
		jumpRows.add(new JSeparator());
		jumpRows.add(Box.createVerticalStrut(8));
		jumpRows.add(tileCoordinateRow());

		JPanel jumpRow = new JPanel(new BorderLayout());
		jumpRow.setOpaque(false);
		jumpRow.add(btnJump, BorderLayout.CENTER);
		jumpRows.add(Box.createVerticalStrut(6));
		jumpRows.add(jumpRow);
		JPanel switch3DRow = new JPanel(new BorderLayout());
		switch3DRow.setOpaque(false);
		switch3DRow.add(btnSwitch3D, BorderLayout.CENTER);
		jumpRows.add(Box.createVerticalStrut(8));
		jumpRows.add(switch3DRow);
		body.add(jumpRows, BorderLayout.CENTER);

		chkGrid.addActionListener(e -> onToggleGrid.emit(chkGrid.isSelected()));
		chkRegionCoords.addActionListener(e -> {
			boolean selected = chkRegionCoords.isSelected();
			if (selected && chkRegionIds.isSelected())
			{
				chkRegionIds.setSelected(false);
				onToggleRegionIds.emit(false);
			}
			onToggleRegionCoordinates.emit(selected);
		});
		chkRegionIds.addActionListener(e -> {
			boolean selected = chkRegionIds.isSelected();
			if (selected && chkRegionCoords.isSelected())
			{
				chkRegionCoords.setSelected(false);
				onToggleRegionCoordinates.emit(false);
			}
			onToggleRegionIds.emit(selected);
		});
		chkMapText.addActionListener(e -> onToggleMapText.emit(chkMapText.isSelected()));
		chkMapIcons.addActionListener(e -> onToggleMapIcons.emit(chkMapIcons.isSelected()));
		chkMapFeatureTooltips.addActionListener(e -> onToggleMapFeatureTooltips.emit(chkMapFeatureTooltips.isSelected()));
		chkNpcDots.addActionListener(e -> onToggleNpcDots.emit(chkNpcDots.isSelected()));
		btnLock.addActionListener(e -> {
			boolean locked = btnLock.isSelected();
			setLocked(locked);
			onToggleLocked.emit(locked);
		});
		cbPlane.addActionListener(e -> {
			if (suppressPlaneEvents)
			{
				return;
			}
			Integer plane = (Integer) cbPlane.getSelectedItem();
			if (plane != null)
			{
				onPlaneChanged.emit(plane);
			}
		});
		btnJumpRegionId.addActionListener(e -> {
			Integer regionId = tfRegionId.getInt(null);
			Integer z = tfRegionIdZ.getInt(0);
			if (regionId == null || regionId < 0)
			{
				JOptionPane.showMessageDialog(this, "Enter a valid region ID.", "Jump", JOptionPane.WARNING_MESSAGE);
				return;
			}
			int rx = regionId >> 8;
			int ry = regionId & 0xFF;
			if (!isRegionInMap(rx, ry))
			{
				JOptionPane.showMessageDialog(this, "That region is outside the bundled map range.", "Jump", JOptionPane.WARNING_MESSAGE);
				return;
			}
			onJumpToTile.emit(centerTileForRegion(rx, ry, normalizedPlane(z)));
		});
		btnJumpRegionCoords.addActionListener(e -> {
			Integer rx = tfRegionX.getInt(null);
			Integer ry = tfRegionY.getInt(null);
			Integer z = tfRegionCoordsZ.getInt(0);
			if (rx == null || ry == null)
			{
				JOptionPane.showMessageDialog(this, "Enter valid integers for rx and ry.", "Jump", JOptionPane.WARNING_MESSAGE);
				return;
			}
			if (!isRegionInMap(rx, ry))
			{
				JOptionPane.showMessageDialog(this, "That region is outside the bundled map range.", "Jump", JOptionPane.WARNING_MESSAGE);
				return;
			}
			onJumpToTile.emit(centerTileForRegion(rx, ry, normalizedPlane(z)));
		});
		btnJump.addActionListener(e -> {
			Integer x = tfX.getInt(null);
			Integer y = tfY.getInt(null);
			Integer z = tfZ.getInt(0);
			if (x == null || y == null)
			{
				JOptionPane.showMessageDialog(this, "Enter valid integers for x and y.", "Jump", JOptionPane.WARNING_MESSAGE);
				return;
			}
			onJumpToTile.emit(new Tile(x, y, z == null ? 0 : z));
		});
		btnSwitch3D.addActionListener(e -> onSwitchTo3DMap.emit(null));
		btnCollapse.addActionListener(e -> setCollapsed(!collapsed));

		setPlaneCount(4);
		setSelectedPlane(0);
	}

	private JPanel regionIdRow()
	{
		tfRegionId.setPreferredSize(new Dimension(86, 28));
		tfRegionIdZ.setPreferredSize(new Dimension(52, 28));
		tfRegionIdZ.setInt(0);

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		row.setOpaque(false);
		row.add(new JLabel("Region ID"));
		row.add(tfRegionId);
		row.add(new JLabel("Z"));
		row.add(tfRegionIdZ);
		row.add(btnJumpRegionId);
		return row;
	}

	private JPanel regionCoordinateRow()
	{
		Dimension narrow = new Dimension(52, 28);
		tfRegionX.setPreferredSize(narrow);
		tfRegionY.setPreferredSize(narrow);
		tfRegionCoordsZ.setPreferredSize(narrow);
		tfRegionCoordsZ.setInt(0);

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		row.setOpaque(false);
		row.add(new JLabel("RX"));
		row.add(tfRegionX);
		row.add(new JLabel("RY"));
		row.add(tfRegionY);
		row.add(new JLabel("Z"));
		row.add(tfRegionCoordsZ);
		row.add(btnJumpRegionCoords);
		return row;
	}

	private JPanel tileCoordinateRow()
	{
		Dimension wide = new Dimension(76, 28);
		Dimension narrow = new Dimension(52, 28);
		tfX.setPreferredSize(wide);
		tfY.setPreferredSize(wide);
		tfZ.setPreferredSize(narrow);
		tfZ.setInt(0);

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		row.setOpaque(false);
		row.add(new JLabel("X"));
		row.add(tfX);
		row.add(new JLabel("Y"));
		row.add(tfY);
		row.add(new JLabel("Z"));
		row.add(tfZ);
		return row;
	}

	private static JPanel leftAlignedRow(Component component)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (component instanceof JComponent jComponent)
		{
			jComponent.setAlignmentX(Component.LEFT_ALIGNMENT);
		}
		row.add(component);
		Dimension size = component.getPreferredSize();
		row.setMinimumSize(size);
		row.setPreferredSize(size);
		row.setMaximumSize(size);
		return row;
	}

	private int normalizedPlane(Integer plane)
	{
		int max = Math.max(0, cbPlane.getItemCount() - 1);
		int value = plane == null ? 0 : plane;
		return Math.max(0, Math.min(max, value));
	}

	private static boolean isRegionInMap(int rx, int ry)
	{
		return rx >= Paths.MIN_RX && rx <= Paths.MAX_RX && ry >= Paths.MIN_RY && ry <= Paths.MAX_RY;
	}

	private static Tile centerTileForRegion(int rx, int ry, int z)
	{
		return new Tile(
			rx * REGION_TILE_SIZE + REGION_TILE_SIZE / 2,
			ry * REGION_TILE_SIZE + REGION_TILE_SIZE / 2,
			z
		);
	}

	@Override
	public Dimension getPreferredSize()
	{
		if (collapsed)
		{
			return new Dimension(COLLAPSED_SIZE, COLLAPSED_SIZE);
		}
		Dimension preferred = super.getPreferredSize();
		return new Dimension(Math.max(EXPANDED_WIDTH, preferred.width), preferred.height);
	}

	public void setPlaneCount(int planeCount)
	{
		DefaultComboBoxModel<Integer> model = new DefaultComboBoxModel<>();
		int count = Math.max(1, planeCount);
		for (int i = 0; i < count; i++)
		{
			model.addElement(i);
		}
		cbPlane.setModel(model);
	}

	public void setSelectedPlane(int plane)
	{
		if (cbPlane.getItemCount() == 0)
		{
			return;
		}
		int clamped = Math.max(0, Math.min(cbPlane.getItemCount() - 1, plane));
		if (!Objects.equals(cbPlane.getSelectedItem(), clamped))
		{
			suppressPlaneEvents = true;
			try
			{
				cbPlane.setSelectedItem(clamped);
			}
			finally
			{
				suppressPlaneEvents = false;
			}
		}
	}

	public void setLocked(boolean locked)
	{
		if (btnLock.isSelected() != locked)
		{
			btnLock.setSelected(locked);
		}
		btnLock.setText("Lock Map");
	}

	public void setMapFeatureTooltips(boolean enabled)
	{
		if (chkMapFeatureTooltips.isSelected() != enabled)
		{
			chkMapFeatureTooltips.setSelected(enabled);
		}
	}

	public void setNpcDotsVisible(boolean enabled)
	{
		if (chkNpcDots.isSelected() != enabled)
		{
			chkNpcDots.setSelected(enabled);
		}
	}

	public void setWikiSyncManager(WikiSyncManager manager, Consumer<String> statusSink)
	{
		wikiSyncProfileControls.bind(manager, statusSink);
	}

	private void setCollapsed(boolean value)
	{
		if (collapsed == value)
		{
			return;
		}
		collapsed = value;
		body.setVisible(!collapsed);
		lbTitle.setVisible(!collapsed);
		btnCollapse.setText(collapsed ? "<" : ">");
		setBorder(collapsed ? collapsedBorder : expandedBorder);
		header.removeAll();
		if (collapsed)
		{
			header.add(btnCollapse, BorderLayout.CENTER);
			styleCollapseButton(btnCollapse, COLLAPSED_BUTTON_SIZE);
		}
		else
		{
			header.add(lbTitle, BorderLayout.WEST);
			header.add(btnCollapse, BorderLayout.EAST);
			styleCollapseButton(btnCollapse, EXPANDED_COLLAPSE_BUTTON_SIZE);
		}
		revalidate();
		Container parent = getParent();
		if (parent != null)
		{
			parent.revalidate();
			parent.doLayout();
			parent.repaint();
		}
	}

	private static void styleButton(AbstractButton button)
	{
		button.setFocusable(false);
		button.setMargin(new Insets(4, 10, 4, 10));
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(80, 80, 80)),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)
		));
	}

	private static void styleCollapseButton(AbstractButton button, Dimension size)
	{
		styleButton(button);
		button.setFont(button.getFont().deriveFont(Font.BOLD, 16f));
		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.setVerticalAlignment(SwingConstants.CENTER);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setMinimumSize(size);
		button.setPreferredSize(size);
		button.setMaximumSize(size);
	}

	public static final class Event<T>
	{
		private final java.util.List<Consumer<T>> listeners = new java.util.ArrayList<>();

		public void addListener(Consumer<T> listener)
		{
			listeners.add(listener);
		}

		public void emit(T value)
		{
			for (Consumer<T> listener : listeners)
			{
				listener.accept(value);
			}
		}
	}
}
