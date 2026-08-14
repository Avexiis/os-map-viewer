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
package com.xeon.plugins.groundmarkers;

import com.xeon.model.Tile;
import com.xeon.util.NumberField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public final class GroundMarkerSidebarPanel extends JPanel
{
	private final JLabel lbRegion = new JLabel("Region: none");
	private final JLabel lbTile = new JLabel("Tile: none");
	private final JTextField tfLabel = new JTextField(18);
	private final JTextField tfColor = new JTextField(GroundMarker.DEFAULT_COLOR, 10);
	private final NumberField tfAlpha = new NumberField(3);
	private final JButton btnPickColor = new JButton("Color");
	private final JButton btnSave = new JButton("Add Marker");
	private final JButton btnDelete = new JButton("Delete Marker");
	private final JPanel swatch = new JPanel();

	private Tile selectedTile;
	private List<Tile> selectedTiles = List.of();
	private GroundMarker selectedMarker;
	private boolean suppressEvents = false;
	private Consumer<GroundMarker> onSave;
	private Runnable onDelete;

	public GroundMarkerSidebarPanel()
	{
		setLayout(new BorderLayout());

		JPanel form = new JPanel(new GridBagLayout());
		form.setBorder(new EmptyBorder(10, 10, 10, 10));
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.insets = new Insets(4, 4, 4, 4);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;

		addFullRow(form, c, title("Ground Marker"));
		addFullRow(form, c, lbRegion);
		addFullRow(form, c, lbTile);

		addLabel(form, c, "Label");
		c.gridx = 1;
		form.add(tfLabel, c);
		c.gridy++;

		addLabel(form, c, "Color");
		c.gridx = 1;
		JPanel colorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		colorRow.setOpaque(false);
		swatch.setPreferredSize(new Dimension(22, 22));
		swatch.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70)));
		colorRow.add(tfColor);
		colorRow.add(swatch);
		colorRow.add(btnPickColor);
		form.add(colorRow, c);
		c.gridy++;

		addLabel(form, c, "Alpha");
		c.gridx = 1;
		tfAlpha.setInt(255);
		form.add(tfAlpha, c);
		c.gridy++;

		c.gridx = 0;
		c.gridwidth = 2;
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		buttons.setOpaque(false);
		buttons.add(btnSave);
		buttons.add(btnDelete);
		form.add(buttons, c);

		add(form, BorderLayout.NORTH);

		btnPickColor.addActionListener(e -> chooseColor());
		btnSave.addActionListener(e -> {
			GroundMarker marker = markerFromFields();
			if (marker != null && onSave != null)
			{
				onSave.accept(marker);
			}
		});
		btnDelete.addActionListener(e -> {
			if (onDelete != null)
			{
				onDelete.run();
			}
		});

		DocumentListener updater = new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				syncSwatchFromFields();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				syncSwatchFromFields();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				syncSwatchFromFields();
			}
		};
		tfColor.getDocument().addDocumentListener(updater);
		tfAlpha.getDocument().addDocumentListener(updater);
		syncSwatchFromFields();
		updateEnabledState();
	}

	public void setOnSave(Consumer<GroundMarker> onSave)
	{
		this.onSave = onSave;
	}

	public void setOnDelete(Runnable onDelete)
	{
		this.onDelete = onDelete;
	}

	public void setSelection(List<Tile> tiles, GroundMarker marker)
	{
		selectedTiles = tiles == null ? List.of() : tiles.stream()
			.map(tile -> new Tile(tile.x, tile.y, tile.z))
			.toList();
		selectedTile = selectedTiles.isEmpty() ? null : selectedTiles.get(selectedTiles.size() - 1);
		selectedMarker = marker == null ? null : marker.copy();
		suppressEvents = true;
		try
		{
			if (selectedTile == null)
			{
				lbRegion.setText("Region: none");
				lbTile.setText("Tile: none");
			}
			else if (selectedTiles.size() > 1)
			{
				lbRegion.setText("Regions: " + selectedTiles.stream().map(GroundMarker::regionId).distinct().count());
				lbTile.setText(selectedTiles.size() + " tiles selected");
			}
			else
			{
				int regionId = GroundMarker.regionId(selectedTile);
				lbRegion.setText("Region: " + regionId + " (" + GroundMarker.regionX(regionId) + "," + GroundMarker.regionY(regionId) + ")");
				lbTile.setText("Tile: " + selectedTile.x + "," + selectedTile.y + "," + selectedTile.z);
			}

			if (selectedMarker != null && selectedTiles.size() <= 1)
			{
				tfLabel.setText(selectedMarker.label == null ? "" : selectedMarker.label);
				tfColor.setText(GroundMarker.normalizeColor(selectedMarker.color));
				tfAlpha.setInt(selectedMarker.awtColor().getAlpha());
				btnSave.setText("Update Marker");
			}
			else
			{
				btnSave.setText(selectedTiles.size() > 1 ? "Add Marker(s)" : "Add Marker");
			}
		}
		finally
		{
			suppressEvents = false;
		}
		syncSwatchFromFields();
		updateEnabledState();
	}

	public GroundMarker getSelectedMarker()
	{
		return selectedMarker == null ? null : selectedMarker.copy();
	}

	public Tile getSelectedTile()
	{
		return selectedTile == null ? null : new Tile(selectedTile.x, selectedTile.y, selectedTile.z);
	}

	private GroundMarker markerFromFields()
	{
		if (selectedTile == null)
		{
			return null;
		}
		return GroundMarker.fromTile(selectedTile, tfLabel.getText(), normalizedColorWithAlpha());
	}

	private String normalizedColorWithAlpha()
	{
		Color color = parseColor();
		int alpha = alphaFromField(color.getAlpha());
		int argb = (alpha << 24) | (color.getRGB() & 0x00FFFFFF);
		return String.format("#%08X", argb);
	}

	private void chooseColor()
	{
		Color current = parseColor();
		Color picked = JColorChooser.showDialog(this, "Ground Marker Color", current);
		if (picked == null)
		{
			return;
		}
		int alpha = alphaFromField(current.getAlpha());
		int argb = (alpha << 24) | (picked.getRGB() & 0x00FFFFFF);
		tfColor.setText(String.format("#%08X", argb));
		syncSwatchFromFields();
	}

	private Color parseColor()
	{
		try
		{
			String normalized = GroundMarker.normalizeColor(tfColor.getText());
			return new Color((int) Long.parseLong(normalized.substring(1), 16), true);
		}
		catch (RuntimeException ex)
		{
			return new Color((int) Long.parseLong(GroundMarker.DEFAULT_COLOR.substring(1), 16), true);
		}
	}

	private void syncSwatchFromFields()
	{
		if (suppressEvents)
		{
			return;
		}
		Color color = parseColor();
		int alpha = alphaFromField(color.getAlpha());
		swatch.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
		swatch.repaint();
	}

	private int alphaFromField(int fallback)
	{
		try
		{
			int value = Integer.parseInt(tfAlpha.getText().trim());
			return Math.max(0, Math.min(255, value));
		}
		catch (RuntimeException ex)
		{
			return Math.max(0, Math.min(255, fallback));
		}
	}

	private void updateEnabledState()
	{
		boolean canEdit = selectedTile != null;
		tfLabel.setEnabled(canEdit);
		tfColor.setEnabled(canEdit);
		tfAlpha.setEnabled(canEdit);
		btnPickColor.setEnabled(canEdit);
		btnSave.setEnabled(canEdit);
		btnDelete.setEnabled(selectedMarker != null);
	}

	private static JLabel title(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
		return label;
	}

	private static void addFullRow(JPanel panel, GridBagConstraints c, Component component)
	{
		c.gridx = 0;
		c.gridwidth = 2;
		panel.add(component, c);
		c.gridwidth = 1;
		c.gridy++;
	}

	private static void addLabel(JPanel panel, GridBagConstraints c, String text)
	{
		c.gridx = 0;
		c.gridwidth = 1;
		panel.add(new JLabel(text), c);
	}
}
