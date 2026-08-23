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
package com.xeon.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

public final class AtlasInspectorApp
{
	private static final Path DEFAULT_ATLAS_PATH = Path.of("src/main/resources/com/xeon/FullWorldMap.atlas");
	private static final int TILE_INDEX_ENTRY_SIZE = 36;
	private static final Gson GSON = new GsonBuilder()
		.disableHtmlEscaping()
		.setPrettyPrinting()
		.create();

	private AtlasInspectorApp()
	{
	}

	public static void main(String[] args)
	{
		Path atlasPath = args.length == 0
			? findProjectRoot().resolve(DEFAULT_ATLAS_PATH)
			: Path.of(args[0]).toAbsolutePath().normalize();

		EventQueue.invokeLater(() -> {
			setSystemLookAndFeel();
			AtlasInspectorFrame frame = new AtlasInspectorFrame();
			frame.setVisible(true);
			frame.openAtlas(atlasPath);
		});
	}

	private static void setSystemLookAndFeel()
	{
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception ignored)
		{
		}
	}

	private static Path findProjectRoot()
	{
		Path current = Path.of("").toAbsolutePath().normalize();
		for (Path candidate = current; candidate != null; candidate = candidate.getParent())
		{
			if (Files.isRegularFile(candidate.resolve("settings.gradle.kts"))
				&& Files.isRegularFile(candidate.resolve(DEFAULT_ATLAS_PATH)))
			{
				return candidate;
			}
		}
		return current;
	}

	private static final class AtlasInspectorFrame extends JFrame
	{
		private final JLabel statusLabel = new JLabel("No atlas loaded");
		private final JTabbedPane tabs = new JTabbedPane();
		private AtlasFile atlas;

		AtlasInspectorFrame()
		{
			super("OS Map Viewer Atlas Inspector");
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			setSize(1320, 850);
			setLocationByPlatform(true);
			setJMenuBar(buildMenuBar());
			add(buildToolbar(), BorderLayout.NORTH);
			add(tabs, BorderLayout.CENTER);
			statusLabel.setBorder(new EmptyBorder(4, 8, 4, 8));
			add(statusLabel, BorderLayout.SOUTH);
		}

		void openAtlas(Path path)
		{
			try
			{
				AtlasFile next = AtlasFile.open(path);
				atlas = next;
				rebuildTabs();
				statusLabel.setText("Loaded " + next.path + " with %,d index entries".formatted(next.entries.size()));
			}
			catch (Exception ex)
			{
				statusLabel.setText("Failed to load atlas: " + ex.getMessage());
				JOptionPane.showMessageDialog(
					this,
					ex.getMessage(),
					"Atlas load failed",
					JOptionPane.ERROR_MESSAGE
				);
			}
		}

		private JMenuBar buildMenuBar()
		{
			JMenu file = new JMenu("File");
			JMenuItem open = new JMenuItem("Open Atlas...");
			open.addActionListener(e -> chooseAtlas());
			JMenuItem exit = new JMenuItem("Exit");
			exit.addActionListener(e -> dispose());
			file.add(open);
			file.addSeparator();
			file.add(exit);

			JMenuBar bar = new JMenuBar();
			bar.add(file);
			return bar;
		}

		private JPanel buildToolbar()
		{
			JButton open = new JButton("Open Atlas...");
			open.addActionListener(e -> chooseAtlas());
			JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			panel.add(open);
			return panel;
		}

		private void chooseAtlas()
		{
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Open .atlas file");
			if (atlas != null)
			{
				chooser.setSelectedFile(atlas.path.toFile());
			}
			else
			{
				chooser.setSelectedFile(findProjectRoot().resolve(DEFAULT_ATLAS_PATH).toFile());
			}
			int result = chooser.showOpenDialog(this);
			if (result == JFileChooser.APPROVE_OPTION)
			{
				openAtlas(chooser.getSelectedFile().toPath());
			}
		}

		private void rebuildTabs()
		{
			tabs.removeAll();
			tabs.addTab("Header", buildHeaderTab());
			tabs.addTab("Metadata", buildMetadataTab());
			tabs.addTab("Layers", buildLayersTab());
			tabs.addTab("Tile Index", buildTileIndexTab());
		}

		private JPanel buildHeaderTab()
		{
			DefaultTableModel headerModel = tableModel("Property", "Value");
			addRow(headerModel, "Atlas file", atlas.path.toString());
			addRow(headerModel, "File size", formatBytes(atlas.header.fileSize));
			addRow(headerModel, "Version", atlas.header.version);
			addRow(headerModel, "Source image", atlas.header.srcWidth + " x " + atlas.header.srcHeight);
			addRow(headerModel, "Tile size", atlas.header.tilePx + " px");
			addRow(headerModel, "LODs", atlas.header.lods);
			addRow(headerModel, "Full-resolution tile grid", atlas.header.tilesXFull + " x " + atlas.header.tilesYFull);
			addRow(headerModel, "Layer count", atlas.header.layerCount);
			addRow(headerModel, "Header size", formatBytes(atlas.header.headerSize));
			addRow(headerModel, "Index offset", atlas.header.indexOffset);
			addRow(headerModel, "Data offset", atlas.header.dataOffset);
			addRow(headerModel, "Metadata offset", atlas.header.metadataOffset);
			addRow(headerModel, "Metadata length", formatBytes(atlas.header.metadataLength));
			addRow(headerModel, "Index entries", atlas.entries.size());
			addRow(headerModel, "Image data bytes", formatBytes(atlas.header.metadataOffset - atlas.header.dataOffset));
			addRow(headerModel, "Metadata JSON valid", atlas.metadataObject == null ? "No" : "Yes");

			JTable headerTable = table(headerModel);
			JTable summaryTable = table(new LayerSummaryTableModel(atlas.layerSummaries()));
			JPanel panel = new JPanel(new GridLayout(1, 2, 8, 8));
			panel.setBorder(new EmptyBorder(8, 8, 8, 8));
			panel.add(wrap("Header", headerTable));
			panel.add(wrap("Index Summary By Layer And LOD", summaryTable));
			return panel;
		}

		private JPanel buildMetadataTab()
		{
			DefaultTableModel categoryModel = tableModel("Name", "Type", "Count / Size", "Preview");
			if (atlas.metadataObject != null)
			{
				for (Map.Entry<String, JsonElement> entry : atlas.metadataObject.entrySet())
				{
					JsonElement value = entry.getValue();
					addRow(categoryModel,
						entry.getKey(),
						jsonType(value),
						jsonCount(value),
						jsonPreview(value));
				}
			}

			JTextArea json = new JTextArea(atlas.prettyMetadata());
			json.setEditable(false);
			json.setCaretPosition(0);
			json.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));

			JSplitPane split = new JSplitPane(
				JSplitPane.VERTICAL_SPLIT,
				wrap("Metadata Categories", table(categoryModel)),
				wrap("Raw Metadata JSON", new JScrollPane(json))
			);
			split.setResizeWeight(0.28);
			JPanel panel = new JPanel(new BorderLayout());
			panel.add(split, BorderLayout.CENTER);
			return panel;
		}

		private JPanel buildLayersTab()
		{
			DefaultTableModel layersModel = tableModel("Layer Index", "Kind", "Plane", "Metadata Object");
			for (LayerInfo layer : atlas.layersByIndex.values())
			{
				addRow(layersModel, layer.index, layer.kind, layer.plane, layer.raw);
			}

			JTable layers = table(layersModel);
			JTable summaries = table(new LayerSummaryTableModel(atlas.layerSummaries()));
			JPanel panel = new JPanel(new GridLayout(1, 2, 8, 8));
			panel.setBorder(new EmptyBorder(8, 8, 8, 8));
			panel.add(wrap("Layer Metadata", layers));
			panel.add(wrap("Tile Index Summary", summaries));
			return panel;
		}

		private JPanel buildTileIndexTab()
		{
			TileEntryTableModel model = new TileEntryTableModel(atlas.entries, atlas.layersByIndex);
			JTable table = table(model);
			table.setAutoCreateRowSorter(true);
			table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			table.getSelectionModel().addListSelectionListener(event -> {
				if (event.getValueIsAdjusting())
				{
					return;
				}
				int row = table.getSelectedRow();
				if (row < 0)
				{
					return;
				}
				int modelRow = table.convertRowIndexToModel(row);
				TileEntry entry = model.entryAt(modelRow);
				TilePreviewPanel preview = (TilePreviewPanel) table.getClientProperty("preview");
				preview.showTile(atlas, entry);
			});

			TilePreviewPanel preview = new TilePreviewPanel();
			table.putClientProperty("preview", preview);
			JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(table), preview);
			split.setResizeWeight(0.72);

			JPanel panel = new JPanel(new BorderLayout());
			panel.setBorder(new EmptyBorder(8, 8, 8, 8));
			panel.add(split, BorderLayout.CENTER);
			return panel;
		}
	}

	private static JTable table(TableModel model)
	{
		JTable table = new JTable(model);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setFillsViewportHeight(true);
		if (!(model instanceof TileEntryTableModel))
		{
			TableRowSorter<TableModel> sorter = new TableRowSorter<>(model);
			table.setRowSorter(sorter);
		}
		return table;
	}

	private static JScrollPane wrap(String title, JTable table)
	{
		JScrollPane pane = new JScrollPane(table);
		pane.setBorder(BorderFactory.createTitledBorder(title));
		return pane;
	}

	private static JScrollPane wrap(String title, JScrollPane pane)
	{
		pane.setBorder(BorderFactory.createTitledBorder(title));
		return pane;
	}

	private static DefaultTableModel tableModel(String... columns)
	{
		return new DefaultTableModel(columns, 0)
		{
			@Override
			public boolean isCellEditable(int row, int column)
			{
				return false;
			}
		};
	}

	private static void addRow(DefaultTableModel model, Object... values)
	{
		model.addRow(values);
	}

	private static String formatBytes(long bytes)
	{
		if (bytes < 0)
		{
			return "%,d bytes".formatted(bytes);
		}
		double mib = bytes / (1024.0 * 1024.0);
		return "%,d bytes (%.2f MiB)".formatted(bytes, mib);
	}

	private static String jsonType(JsonElement value)
	{
		if (value == null || value.isJsonNull())
		{
			return "null";
		}
		if (value.isJsonArray())
		{
			return "array";
		}
		if (value.isJsonObject())
		{
			return "object";
		}
		if (value.isJsonPrimitive())
		{
			return "primitive";
		}
		return value.getClass().getSimpleName();
	}

	private static String jsonCount(JsonElement value)
	{
		if (value == null || value.isJsonNull())
		{
			return "";
		}
		if (value.isJsonArray())
		{
			return "%,d items".formatted(value.getAsJsonArray().size());
		}
		if (value.isJsonObject())
		{
			return "%,d fields".formatted(value.getAsJsonObject().size());
		}
		return "";
	}

	private static String jsonPreview(JsonElement value)
	{
		if (value == null || value.isJsonNull())
		{
			return "";
		}
		if (value.isJsonPrimitive())
		{
			String text = value.getAsString();
			return text.length() > 160 ? text.substring(0, 160) + "..." : text;
		}
		return "";
	}

	private static final class TilePreviewPanel extends JPanel
	{
		private final ImageCanvas canvas = new ImageCanvas();
		private final JTextArea details = new JTextArea();

		TilePreviewPanel()
		{
			super(new BorderLayout(8, 8));
			setBorder(BorderFactory.createTitledBorder("Selected Tile Preview"));
			setPreferredSize(new Dimension(420, 600));
			details.setEditable(false);
			details.setRows(8);
			details.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
			add(canvas, BorderLayout.CENTER);
			add(new JScrollPane(details), BorderLayout.SOUTH);
		}

		void showTile(AtlasFile atlas, TileEntry entry)
		{
			details.setText(tileDetails(atlas, entry));
			try
			{
				BufferedImage image = atlas.readImage(entry);
				canvas.setImage(image, null);
			}
			catch (Exception ex)
			{
				canvas.setImage(null, "Could not decode image: " + ex.getMessage());
			}
		}

		private static String tileDetails(AtlasFile atlas, TileEntry entry)
		{
			LayerInfo layer = atlas.layersByIndex.get(entry.layer);
			return """
				lod: %d
				layer: %d%s
				tile: %d, %d
				image: %d x %d
				relative offset: %,d
				absolute offset: %,d
				length: %,d bytes
				""".formatted(
				entry.lod,
				entry.layer,
				layer == null ? "" : " (" + layer.kind + ", plane " + layer.plane + ")",
				entry.tx,
				entry.ty,
				entry.imgW,
				entry.imgH,
				entry.relOffset,
				atlas.header.dataOffset + entry.relOffset,
				entry.length
			);
		}
	}

	private static final class ImageCanvas extends JPanel
	{
		private static final Color CHECKER_A = new Color(0xEEEEEE);
		private static final Color CHECKER_B = new Color(0xCFCFCF);
		private BufferedImage image;
		private String message = "Select a tile index row to decode and preview its PNG.";

		ImageCanvas()
		{
			setPreferredSize(new Dimension(390, 390));
			setMinimumSize(new Dimension(240, 240));
		}

		void setImage(BufferedImage image, String message)
		{
			this.image = image;
			this.message = message == null && image == null ? "No image" : message;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			super.paintComponent(g0);
			Graphics2D g = (Graphics2D) g0.create();
			try
			{
				if (image == null)
				{
					g.setColor(Color.DARK_GRAY);
					g.fillRect(0, 0, getWidth(), getHeight());
					g.setColor(Color.WHITE);
					String text = message == null ? "" : message;
					g.drawString(text, 12, 24);
					return;
				}

				paintCheckerboard(g);
				double scale = Math.min(
					getWidth() / (double) image.getWidth(),
					getHeight() / (double) image.getHeight()
				);
				int drawW = Math.max(1, (int) Math.floor(image.getWidth() * scale));
				int drawH = Math.max(1, (int) Math.floor(image.getHeight() * scale));
				int x = (getWidth() - drawW) / 2;
				int y = (getHeight() - drawH) / 2;
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				g.drawImage(image, x, y, drawW, drawH, null);
			}
			finally
			{
				g.dispose();
			}
		}

		private void paintCheckerboard(Graphics2D g)
		{
			int square = 12;
			for (int y = 0; y < getHeight(); y += square)
			{
				for (int x = 0; x < getWidth(); x += square)
				{
					boolean alternate = ((x / square) + (y / square)) % 2 == 0;
					g.setColor(alternate ? CHECKER_A : CHECKER_B);
					g.fillRect(x, y, square, square);
				}
			}
		}
	}

	private static final class TileEntryTableModel extends AbstractTableModel
	{
		private static final String[] COLUMNS = {
			"LOD", "Layer", "Kind", "Plane", "TX", "TY", "Image W", "Image H", "Relative Offset", "Length"
		};
		private final List<TileEntry> entries;
		private final Map<Integer, LayerInfo> layersByIndex;

		TileEntryTableModel(List<TileEntry> entries, Map<Integer, LayerInfo> layersByIndex)
		{
			this.entries = entries;
			this.layersByIndex = layersByIndex;
		}

		TileEntry entryAt(int row)
		{
			return entries.get(row);
		}

		@Override
		public int getRowCount()
		{
			return entries.size();
		}

		@Override
		public int getColumnCount()
		{
			return COLUMNS.length;
		}

		@Override
		public String getColumnName(int column)
		{
			return COLUMNS[column];
		}

		@Override
		public Class<?> getColumnClass(int column)
		{
			return switch (column)
			{
				case 2 -> String.class;
				case 8 -> Long.class;
				default -> Integer.class;
			};
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			TileEntry entry = entries.get(rowIndex);
			LayerInfo layer = layersByIndex.get(entry.layer);
			return switch (columnIndex)
			{
				case 0 -> entry.lod;
				case 1 -> entry.layer;
				case 2 -> layer == null ? "" : layer.kind;
				case 3 -> layer == null ? -1 : layer.plane;
				case 4 -> entry.tx;
				case 5 -> entry.ty;
				case 6 -> entry.imgW;
				case 7 -> entry.imgH;
				case 8 -> entry.relOffset;
				case 9 -> entry.length;
				default -> "";
			};
		}
	}

	private static final class LayerSummaryTableModel extends AbstractTableModel
	{
		private static final String[] COLUMNS = {
			"Layer", "Kind", "Plane", "LOD", "Entries", "Total Bytes", "TX Range", "TY Range", "Image Sizes"
		};
		private final List<LayerSummary> summaries;

		LayerSummaryTableModel(List<LayerSummary> summaries)
		{
			this.summaries = summaries;
		}

		@Override
		public int getRowCount()
		{
			return summaries.size();
		}

		@Override
		public int getColumnCount()
		{
			return COLUMNS.length;
		}

		@Override
		public String getColumnName(int column)
		{
			return COLUMNS[column];
		}

		@Override
		public Class<?> getColumnClass(int column)
		{
			return switch (column)
			{
				case 0, 2, 3, 4 -> Integer.class;
				case 5 -> Long.class;
				default -> String.class;
			};
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			LayerSummary summary = summaries.get(rowIndex);
			return switch (columnIndex)
			{
				case 0 -> summary.layer;
				case 1 -> summary.kind;
				case 2 -> summary.plane;
				case 3 -> summary.lod;
				case 4 -> summary.entries;
				case 5 -> summary.totalBytes;
				case 6 -> summary.minTx + "..." + summary.maxTx;
				case 7 -> summary.minTy + "..." + summary.maxTy;
				case 8 -> String.join(", ", summary.imageSizes);
				default -> "";
			};
		}
	}

	private static final class AtlasFile
	{
		private final Path path;
		private final AtlasHeader header;
		private final byte[] metadataBytes;
		private final JsonObject metadataObject;
		private final Map<Integer, LayerInfo> layersByIndex;
		private final List<TileEntry> entries;

		private AtlasFile(Path path, AtlasHeader header, byte[] metadataBytes, JsonObject metadataObject,
		                  Map<Integer, LayerInfo> layersByIndex, List<TileEntry> entries)
		{
			this.path = path;
			this.header = header;
			this.metadataBytes = metadataBytes;
			this.metadataObject = metadataObject;
			this.layersByIndex = layersByIndex;
			this.entries = entries;
		}

		static AtlasFile open(Path path) throws IOException
		{
			Path normalized = path.toAbsolutePath().normalize();
			if (!Files.isRegularFile(normalized))
			{
				throw new IOException("Atlas file not found: " + normalized);
			}

			long fileSize = Files.size(normalized);
			try (RandomAccessFile raf = new RandomAccessFile(normalized.toFile(), "r"))
			{
				AtlasHeader header = readHeader(raf, fileSize);
				byte[] metadataBytes = readMetadata(raf, header);
				JsonObject metadataObject = parseMetadata(metadataBytes);
				Map<Integer, LayerInfo> layers = readLayerMetadata(metadataObject);
				List<TileEntry> entries = readIndex(raf, header);
				entries.sort(Comparator
					.comparingInt((TileEntry entry) -> entry.layer)
					.thenComparingInt(entry -> entry.lod)
					.thenComparingInt(entry -> entry.ty)
					.thenComparingInt(entry -> entry.tx));
				return new AtlasFile(normalized, header, metadataBytes, metadataObject, layers, entries);
			}
		}

		BufferedImage readImage(TileEntry entry) throws IOException
		{
			byte[] bytes = new byte[entry.length];
			try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r"))
			{
				raf.seek(header.dataOffset + entry.relOffset);
				raf.readFully(bytes);
			}
			try (ByteArrayInputStream in = new ByteArrayInputStream(bytes))
			{
				BufferedImage image = ImageIO.read(in);
				if (image == null)
				{
					throw new IOException("ImageIO did not recognize the tile image");
				}
				return image;
			}
		}

		String prettyMetadata()
		{
			if (metadataObject != null)
			{
				return GSON.toJson(metadataObject);
			}
			return new String(metadataBytes, StandardCharsets.UTF_8);
		}

		List<LayerSummary> layerSummaries()
		{
			Map<LayerSummaryKey, LayerSummary> byLayerAndLod = new LinkedHashMap<>();
			for (TileEntry entry : entries)
			{
				LayerInfo info = layersByIndex.get(entry.layer);
				LayerSummaryKey key = new LayerSummaryKey(entry.layer, entry.lod);
				LayerSummary summary = byLayerAndLod.computeIfAbsent(key, ignored -> new LayerSummary(
					entry.layer,
					info == null ? "" : info.kind,
					info == null ? -1 : info.plane,
					entry.lod
				));
				summary.add(entry);
			}
			return byLayerAndLod.values().stream()
				.sorted(Comparator
					.comparingInt((LayerSummary summary) -> summary.layer)
					.thenComparingInt(summary -> summary.lod))
				.toList();
		}

		private static AtlasHeader readHeader(RandomAccessFile raf, long fileSize) throws IOException
		{
			raf.seek(0);
			byte[] magic8 = new byte[8];
			raf.readFully(magic8);

			byte[] expected7 = new byte[]{'A', 'T', 'L', 'S', 'v', '3', 0x00};
			for (int i = 0; i < expected7.length; i++)
			{
				if (magic8[i] != expected7[i])
				{
					throw new IOException("Atlas has an invalid ATLSv3 header");
				}
			}
			if (magic8[7] != 0x00)
			{
				raf.seek(7);
			}

			int version = readU32(raf);
			if (version != 3)
			{
				throw new IOException("Unsupported atlas version " + version + "; expected 3");
			}
			int srcWidth = readU32(raf);
			int srcHeight = readU32(raf);
			int tilePx = readU32(raf);
			int lodCount = readU32(raf);
			if (lodCount <= 0 || lodCount > 64)
			{
				throw new IOException("Atlas has an invalid LOD count: " + lodCount);
			}

			List<Integer> lods = new ArrayList<>(lodCount);
			for (int i = 0; i < lodCount; i++)
			{
				lods.add(readU32(raf));
			}

			int tilesXFull = readU32(raf);
			int tilesYFull = readU32(raf);
			int layerCount = readU32(raf);
			long indexOffset = readU64(raf);
			long dataOffset = readU64(raf);
			long metadataOffset = readU64(raf);
			int metadataLength = readU32(raf);
			long headerSize = raf.getFilePointer();

			validateOffsets(indexOffset, dataOffset, metadataOffset, metadataLength, fileSize);
			return new AtlasHeader(
				fileSize,
				version,
				srcWidth,
				srcHeight,
				tilePx,
				List.copyOf(lods),
				tilesXFull,
				tilesYFull,
				layerCount,
				indexOffset,
				dataOffset,
				metadataOffset,
				metadataLength,
				headerSize
			);
		}

		private static void validateOffsets(long indexOffset, long dataOffset, long metadataOffset,
		                                    int metadataLength, long fileSize) throws IOException
		{
			if (indexOffset < 0 || dataOffset < indexOffset || metadataOffset < dataOffset)
			{
				throw new IOException("Atlas offsets are not ordered");
			}
			long metadataEnd = metadataOffset + metadataLength;
			if (metadataEnd < metadataOffset || metadataEnd > fileSize)
			{
				throw new IOException("Atlas metadata extends past end of file");
			}
			if ((dataOffset - indexOffset) % TILE_INDEX_ENTRY_SIZE != 0)
			{
				throw new IOException("Atlas index length is not divisible by " + TILE_INDEX_ENTRY_SIZE);
			}
		}

		private static byte[] readMetadata(RandomAccessFile raf, AtlasHeader header) throws IOException
		{
			byte[] bytes = new byte[header.metadataLength];
			raf.seek(header.metadataOffset);
			raf.readFully(bytes);
			return bytes;
		}

		private static JsonObject parseMetadata(byte[] metadataBytes) throws IOException
		{
			try
			{
				JsonElement parsed = new JsonParser().parse(new String(metadataBytes, StandardCharsets.UTF_8));
				if (!parsed.isJsonObject())
				{
					throw new IOException("Atlas metadata is not a JSON object");
				}
				return parsed.getAsJsonObject();
			}
			catch (JsonSyntaxException ex)
			{
				throw new IOException("Atlas metadata is not valid JSON", ex);
			}
		}

		private static Map<Integer, LayerInfo> readLayerMetadata(JsonObject metadataObject)
		{
			Map<Integer, LayerInfo> layers = new LinkedHashMap<>();
			JsonElement layersElement = metadataObject.get("layers");
			if (layersElement == null || !layersElement.isJsonArray())
			{
				return layers;
			}

			JsonArray array = layersElement.getAsJsonArray();
			for (JsonElement element : array)
			{
				if (!element.isJsonObject())
				{
					continue;
				}
				JsonObject object = element.getAsJsonObject();
				int index = readInt(object, "index", -1);
				String kind = readString(object, "kind", "");
				int plane = readInt(object, "plane", -1);
				if (index >= 0)
				{
					layers.put(index, new LayerInfo(index, kind, plane, GSON.toJson(object)));
				}
			}
			return layers;
		}

		private static List<TileEntry> readIndex(RandomAccessFile raf, AtlasHeader header) throws IOException
		{
			long entryCount = (header.dataOffset - header.indexOffset) / TILE_INDEX_ENTRY_SIZE;
			if (entryCount > Integer.MAX_VALUE)
			{
				throw new IOException("Atlas has too many index entries for this inspector: " + entryCount);
			}

			List<TileEntry> entries = new ArrayList<>((int) entryCount);
			raf.seek(header.indexOffset);
			for (long i = 0; i < entryCount; i++)
			{
				entries.add(new TileEntry(
					readU32(raf),
					readU32(raf),
					readU32(raf),
					readU32(raf),
					readU32(raf),
					readU32(raf),
					readU64(raf),
					readU32(raf)
				));
			}
			return entries;
		}
	}

	private static int readInt(JsonObject object, String key, int fallback)
	{
		JsonElement value = object.get(key);
		if (value == null || value.isJsonNull() || !value.isJsonPrimitive())
		{
			return fallback;
		}
		try
		{
			return value.getAsInt();
		}
		catch (NumberFormatException ex)
		{
			return fallback;
		}
	}

	private static String readString(JsonObject object, String key, String fallback)
	{
		JsonElement value = object.get(key);
		if (value == null || value.isJsonNull() || !value.isJsonPrimitive())
		{
			return fallback;
		}
		String text = value.getAsString();
		return text == null ? fallback : text;
	}

	private static int readU32(RandomAccessFile raf) throws IOException
	{
		byte[] bytes = new byte[4];
		raf.readFully(bytes);
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
	}

	private static long readU64(RandomAccessFile raf) throws IOException
	{
		byte[] bytes = new byte[8];
		raf.readFully(bytes);
		long value = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
		if (value < 0)
		{
			throw new EOFException("Atlas offset is larger than supported by this inspector");
		}
		return value;
	}

	private record AtlasHeader(
		long fileSize,
		int version,
		int srcWidth,
		int srcHeight,
		int tilePx,
		List<Integer> lods,
		int tilesXFull,
		int tilesYFull,
		int layerCount,
		long indexOffset,
		long dataOffset,
		long metadataOffset,
		int metadataLength,
		long headerSize
	)
	{
	}

	private record TileEntry(int lod, int layer, int tx, int ty, int imgW, int imgH, long relOffset, int length)
	{
	}

	private record LayerInfo(int index, String kind, int plane, String raw)
	{
	}

	private record LayerSummaryKey(int layer, int lod)
	{
	}

	private static final class LayerSummary
	{
		private final int layer;
		private final String kind;
		private final int plane;
		private final int lod;
		private final TreeSet<String> imageSizes = new TreeSet<>();
		private int entries;
		private long totalBytes;
		private int minTx = Integer.MAX_VALUE;
		private int maxTx = Integer.MIN_VALUE;
		private int minTy = Integer.MAX_VALUE;
		private int maxTy = Integer.MIN_VALUE;

		LayerSummary(int layer, String kind, int plane, int lod)
		{
			this.layer = layer;
			this.kind = Objects.requireNonNullElse(kind, "");
			this.plane = plane;
			this.lod = lod;
		}

		void add(TileEntry entry)
		{
			entries++;
			totalBytes += entry.length;
			minTx = Math.min(minTx, entry.tx);
			maxTx = Math.max(maxTx, entry.tx);
			minTy = Math.min(minTy, entry.ty);
			maxTy = Math.max(maxTy, entry.ty);
			imageSizes.add(entry.imgW + "x" + entry.imgH);
		}
	}
}
