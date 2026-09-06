/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.

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
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

final class NpcBrowserDialog extends JDialog
{
	private static final int PAGE_SIZE = 50;

	private final TerrainRegionLoader.Session session;
	private final boolean selectionMode;
	private final NpcTableModel tableModel = new NpcTableModel();
	private final JTable table = new JTable(tableModel);
	private final JTextField search = new JTextField();
	private final JButton previous = new JButton("<");
	private final JButton next = new JButton(">");
	private final JLabel page = new JLabel(" ");
	private final JLabel detail = new JLabel("Loading NPCs");
	private final JButton save = new JButton("Save");
	private final JButton close = new JButton("Close");
	private final JSlider rotation = new JSlider(0, 359, 25);
	private final JSlider zoom = new JSlider(50, 250, 100);
	private final JCheckBox walk = new JCheckBox("Walk animation");
	private final PreviewPanel preview = new PreviewPanel();
	private List<NpcDefinitionProvider.NpcSummary> allNpcs = List.of();
	private List<NpcDefinitionProvider.NpcSummary> filteredNpcs = List.of();
	private NpcDefinitionProvider.NpcSummary selectedNpc;
	private Selection selection;
	private int currentPage;
	private int previewRequest;
	private SwingWorker<List<NpcDefinitionProvider.NpcSummary>, Void> catalogWorker;
	private SwingWorker<NpcPreviewModel, Void> previewWorker;

	static Selection chooseNpc(Component owner, TerrainRegionLoader.Session session, Integer initialNpcId)
	{
		NpcBrowserDialog dialog = new NpcBrowserDialog(ownerWindow(owner), session, true, initialNpcId);
		dialog.setVisible(true);
		return dialog.selection;
	}

	static void browse(Component owner, TerrainRegionLoader.Session session)
	{
		NpcBrowserDialog dialog = new NpcBrowserDialog(ownerWindow(owner), session, false, null);
		dialog.setVisible(true);
	}

	private NpcBrowserDialog(Window owner, TerrainRegionLoader.Session session, boolean selectionMode, Integer initialNpcId)
	{
		super(owner, selectionMode ? "Select NPC" : "NPC Browser", Dialog.ModalityType.APPLICATION_MODAL);
		this.session = session;
		this.selectionMode = selectionMode;
		buildUi();
		loadCatalog(initialNpcId);
	}

	private static Window ownerWindow(Component owner)
	{
		return owner instanceof Window window ? window : SwingUtilities.getWindowAncestor(owner);
	}

	private void buildUi()
	{
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setMinimumSize(new Dimension(780, 520));
		setSize(900, 590);
		setLocationRelativeTo(getOwner());

		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setFillsViewportHeight(true);
		table.setAutoCreateRowSorter(false);
		table.getColumnModel().getColumn(0).setPreferredWidth(70);
		table.getColumnModel().getColumn(0).setMaxWidth(100);
		table.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting())
			{
				applyTableSelection();
			}
		});

		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				applyFilter(false);
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				applyFilter(false);
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				applyFilter(false);
			}
		});

		previous.addActionListener(e -> changePage(-1));
		next.addActionListener(e -> changePage(1));
		save.setEnabled(false);
		save.setVisible(selectionMode);
		save.addActionListener(e -> saveSelection());
		close.addActionListener(e -> dispose());

		rotation.setMajorTickSpacing(90);
		rotation.setPaintTicks(true);
		rotation.addChangeListener(e -> preview.setRotationDegrees(rotation.getValue()));
		zoom.setMajorTickSpacing(50);
		zoom.setPaintTicks(true);
		zoom.addChangeListener(e -> preview.setZoomPercent(zoom.getValue()));
		walk.setEnabled(false);
		walk.addActionListener(e -> preview.setWalkEnabled(walk.isSelected()));

		JPanel searchRow = new JPanel(new BorderLayout(8, 0));
		searchRow.add(new JLabel("Search"), BorderLayout.WEST);
		searchRow.add(search, BorderLayout.CENTER);

		JPanel pageRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		pageRow.add(previous);
		page.setHorizontalAlignment(SwingConstants.CENTER);
		page.setPreferredSize(new Dimension(150, 24));
		pageRow.add(page);
		pageRow.add(next);

		JPanel left = new JPanel(new BorderLayout(0, 8));
		left.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));
		left.add(searchRow, BorderLayout.NORTH);
		left.add(new JScrollPane(table), BorderLayout.CENTER);
		left.add(pageRow, BorderLayout.SOUTH);

		JPanel sliders = new JPanel(new BorderLayout(8, 4));
		sliders.add(labeledSlider("Rotate", rotation), BorderLayout.NORTH);
		sliders.add(labeledSlider("Zoom", zoom), BorderLayout.CENTER);

		JPanel previewControls = new JPanel(new BorderLayout(8, 0));
		previewControls.add(sliders, BorderLayout.CENTER);
		previewControls.add(walk, BorderLayout.EAST);

		JPanel right = new JPanel(new BorderLayout(0, 8));
		right.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));
		detail.setHorizontalAlignment(SwingConstants.LEFT);
		right.add(detail, BorderLayout.NORTH);
		right.add(preview, BorderLayout.CENTER);
		right.add(previewControls, BorderLayout.SOUTH);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
		split.setResizeWeight(0.36);
		split.setDividerLocation(330);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(save);
		buttons.add(close);

		add(split, BorderLayout.CENTER);
		add(buttons, BorderLayout.SOUTH);
	}

	private JPanel labeledSlider(String label, JSlider slider)
	{
		JPanel panel = new JPanel(new BorderLayout(8, 0));
		JLabel text = new JLabel(label);
		text.setPreferredSize(new Dimension(48, 22));
		panel.add(text, BorderLayout.WEST);
		panel.add(slider, BorderLayout.CENTER);
		return panel;
	}

	private void loadCatalog(Integer initialNpcId)
	{
		catalogWorker = new SwingWorker<>()
		{
			@Override
			protected List<NpcDefinitionProvider.NpcSummary> doInBackground()
			{
				return session.npcCatalog();
			}

			@Override
			protected void done()
			{
				try
				{
					allNpcs = get();
					applyFilter(false);
					if (initialNpcId != null && initialNpcId >= 0)
					{
						selectNpcId(initialNpcId);
					}
					else
					{
						selectFirstRow();
					}
				}
				catch (InterruptedException ex)
				{
					Thread.currentThread().interrupt();
					showCatalogError("NPC catalog loading was interrupted.");
				}
				catch (CancellationException ex)
				{
					showCatalogError("NPC catalog loading was cancelled.");
				}
				catch (ExecutionException ex)
				{
					showCatalogError("Failed to load NPC catalog: " + rootMessage(ex));
				}
			}
		};
		catalogWorker.execute();
	}

	private void showCatalogError(String message)
	{
		allNpcs = List.of();
		filteredNpcs = List.of();
		tableModel.setRows(List.of());
		detail.setText(message);
		page.setText("0 / 0");
		previous.setEnabled(false);
		next.setEnabled(false);
		save.setEnabled(false);
		preview.setMessage(message);
	}

	private void applyFilter(boolean keepSelection)
	{
		Integer previousNpcId = keepSelection && selectedNpc != null ? selectedNpc.id() : null;
		String query = search.getText().trim();
		if (query.isEmpty())
		{
			filteredNpcs = allNpcs;
		}
		else
		{
			Pattern pattern = regexPattern(query);
			String[] terms = normalizedWords(query).split(" ");
			List<NpcDefinitionProvider.NpcSummary> matches = new ArrayList<>();
			for (NpcDefinitionProvider.NpcSummary npc : allNpcs)
			{
				if (matchesQuery(npc, pattern, terms))
				{
					matches.add(npc);
				}
			}
			filteredNpcs = List.copyOf(matches);
		}
		currentPage = previousNpcId == null ? 0 : pageForNpc(previousNpcId);
		updatePage(previousNpcId);
		if (previousNpcId == null)
		{
			selectFirstRow();
		}
	}

	private static Pattern regexPattern(String query)
	{
		try
		{
			return Pattern.compile(query.replace(' ', '_'), Pattern.CASE_INSENSITIVE);
		}
		catch (PatternSyntaxException ex)
		{
			return null;
		}
	}

	private static boolean matchesQuery(
		NpcDefinitionProvider.NpcSummary npc,
		Pattern pattern,
		String[] terms
	)
	{
		String regexText = npc.id() + " " + normalizedUnderscore(npc.name());
		if (pattern != null && pattern.matcher(regexText).find())
		{
			return true;
		}

		String wordText = npc.id() + " " + normalizedWords(npc.name());
		for (String term : terms)
		{
			if (!term.isBlank() && !wordText.contains(term))
			{
				return false;
			}
		}
		return true;
	}

	private static String normalizedWords(String value)
	{
		return value == null
			? ""
			: value.toLowerCase(Locale.ROOT).replace('_', ' ').replaceAll("\\s+", " ").trim();
	}

	private static String normalizedUnderscore(String value)
	{
		return normalizedWords(value).replace(' ', '_');
	}

	private int pageForNpc(int npcId)
	{
		for (int i = 0; i < filteredNpcs.size(); i++)
		{
			if (filteredNpcs.get(i).id() == npcId)
			{
				return i / PAGE_SIZE;
			}
		}
		return 0;
	}

	private void changePage(int delta)
	{
		int pageCount = pageCount();
		currentPage = Math.max(0, Math.min(pageCount - 1, currentPage + delta));
		updatePage(null);
		selectFirstRow();
	}

	private void updatePage(Integer selectNpcId)
	{
		int pageCount = pageCount();
		currentPage = Math.max(0, Math.min(pageCount - 1, currentPage));
		int from = Math.min(filteredNpcs.size(), currentPage * PAGE_SIZE);
		int to = Math.min(filteredNpcs.size(), from + PAGE_SIZE);
		tableModel.setRows(filteredNpcs.subList(from, to));
		page.setText((filteredNpcs.isEmpty() ? 0 : currentPage + 1) + " / " + pageCount + " (" + filteredNpcs.size() + ")");
		previous.setEnabled(currentPage > 0);
		next.setEnabled(currentPage + 1 < pageCount);
		if (selectNpcId != null && !selectNpcId(selectNpcId))
		{
			clearSelection();
		}
		else if (filteredNpcs.isEmpty())
		{
			clearSelection();
		}
	}

	private int pageCount()
	{
		return Math.max(1, (filteredNpcs.size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	private boolean selectNpcId(int npcId)
	{
		for (int row = 0; row < tableModel.getRowCount(); row++)
		{
			NpcDefinitionProvider.NpcSummary npc = tableModel.npcAt(row);
			if (npc.id() == npcId)
			{
				table.getSelectionModel().setSelectionInterval(row, row);
				table.scrollRectToVisible(table.getCellRect(row, 0, true));
				return true;
			}
		}

		int targetPage = pageForNpc(npcId);
		if (targetPage != currentPage)
		{
			currentPage = targetPage;
			updatePage(npcId);
			return selectedNpc != null && selectedNpc.id() == npcId;
		}
		return false;
	}

	private void selectFirstRow()
	{
		if (tableModel.getRowCount() > 0)
		{
			table.getSelectionModel().setSelectionInterval(0, 0);
		}
		else
		{
			clearSelection();
		}
	}

	private void clearSelection()
	{
		table.clearSelection();
		selectedNpc = null;
		save.setEnabled(false);
		walk.setEnabled(false);
		walk.setSelected(false);
		detail.setText(filteredNpcs.isEmpty() ? "No NPCs found" : "Select an NPC");
		preview.setMessage(filteredNpcs.isEmpty() ? "No NPCs found" : "Select an NPC");
	}

	private void applyTableSelection()
	{
		int row = table.getSelectedRow();
		if (row < 0 || row >= tableModel.getRowCount())
		{
			return;
		}
		selectedNpc = tableModel.npcAt(row);
		save.setEnabled(selectionMode);
		detail.setText(selectedNpc.name() + " (ID " + selectedNpc.id() + ")");
		loadPreview(selectedNpc);
	}

	private void loadPreview(NpcDefinitionProvider.NpcSummary npc)
	{
		int request = ++previewRequest;
		if (previewWorker != null)
		{
			previewWorker.cancel(true);
		}
		preview.setMessage("Loading preview");
		walk.setEnabled(false);
		previewWorker = new SwingWorker<>()
		{
			@Override
			protected NpcPreviewModel doInBackground()
			{
				return session.npcPreviewModel(npc.id());
			}

			@Override
			protected void done()
			{
				if (request != previewRequest)
				{
					return;
				}
				try
				{
					NpcPreviewModel model = get();
					if (model == null)
					{
						walk.setEnabled(false);
						walk.setSelected(false);
						preview.setMessage("No preview available");
						return;
					}
					preview.setModel(model);
					walk.setEnabled(model.hasWalkAnimation());
					if (!model.hasWalkAnimation())
					{
						walk.setSelected(false);
					}
					preview.setWalkEnabled(walk.isSelected());
				}
				catch (InterruptedException ex)
				{
					Thread.currentThread().interrupt();
					preview.setMessage("Preview loading was interrupted");
				}
				catch (CancellationException ignored)
				{
					// Superseded by a later table selection.
				}
				catch (ExecutionException ex)
				{
					walk.setEnabled(false);
					walk.setSelected(false);
					preview.setMessage("Failed to load preview: " + rootMessage(ex));
				}
			}
		};
		previewWorker.execute();
	}

	private void saveSelection()
	{
		if (selectedNpc == null)
		{
			return;
		}
		selection = new Selection(selectedNpc.id(), selectedNpc.name());
		dispose();
	}

	@Override
	public void dispose()
	{
		if (catalogWorker != null)
		{
			catalogWorker.cancel(true);
		}
		if (previewWorker != null)
		{
			previewWorker.cancel(true);
		}
		preview.stop();
		super.dispose();
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

	record Selection(int id, String name)
	{
		Selection
		{
			name = name == null ? "" : name;
		}
	}

	private static final class NpcTableModel extends AbstractTableModel
	{
		private final List<NpcDefinitionProvider.NpcSummary> rows = new ArrayList<>();

		void setRows(List<NpcDefinitionProvider.NpcSummary> values)
		{
			rows.clear();
			if (values != null)
			{
				rows.addAll(values);
			}
			fireTableDataChanged();
		}

		NpcDefinitionProvider.NpcSummary npcAt(int row)
		{
			return rows.get(row);
		}

		@Override
		public int getRowCount()
		{
			return rows.size();
		}

		@Override
		public int getColumnCount()
		{
			return 2;
		}

		@Override
		public String getColumnName(int column)
		{
			return column == 0 ? "ID" : "Name";
		}

		@Override
		public Class<?> getColumnClass(int columnIndex)
		{
			return columnIndex == 0 ? Integer.class : String.class;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			NpcDefinitionProvider.NpcSummary npc = rows.get(rowIndex);
			return columnIndex == 0 ? npc.id() : npc.name();
		}
	}

	private static final class PreviewPanel extends MeshPreviewPanel
	{
		private NpcPreviewModel model;
		private AnimatedObjectMesh.Frame lastFrame;
		private boolean walkEnabled;
		private long animationStartNanos = System.nanoTime();
		private final Timer animationTimer = new Timer(50, e -> {
			if (walkEnabled && model != null && model.hasWalkAnimation()) repaint();
		});

		private PreviewPanel() { animationTimer.start(); }

		private void setModel(NpcPreviewModel model)
		{
			this.model = model;
			lastFrame = null;
			animationStartNanos = System.nanoTime();
			repaint();
		}

		@Override
		public void setMessage(String message)
		{
			model = null;
			lastFrame = null;
			super.setMessage(message);
		}

		private void setWalkEnabled(boolean enabled)
		{
			walkEnabled = enabled;
			animationStartNanos = System.nanoTime();
			repaint();
		}

		private void stop() { animationTimer.stop(); }

		@Override
		protected Map3DMesh mesh()
		{
			if (model == null) return null;
			boolean useWalk = walkEnabled && model.hasWalkAnimation();
			AnimatedObjectMesh.Frame[] frames = useWalk ? model.walkFrames() : model.idleFrames();
			int[] lengths = useWalk ? model.walkFrameLengths() : model.idleFrameLengths();
			if (frames.length == 0) return null;
			float seconds = (System.nanoTime() - animationStartNanos) / 1_000_000_000.0f;
			int index = AnimatedObjectMesh.frameIndexAt(frames.length, lengths, -1, 0, seconds);
			AnimatedObjectMesh.Frame frame = frames[index < 0 || index >= frames.length ? 0 : index];
			if (frame != lastFrame)
			{
				lastFrame = frame;
				setMesh(ModelMeshData.fromFrame(frame));
			}
			return super.mesh();
		}
	}
}
