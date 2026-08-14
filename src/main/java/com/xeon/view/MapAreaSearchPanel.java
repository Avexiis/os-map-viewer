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

import com.xeon.model.MapArea;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public final class MapAreaSearchPanel extends JPanel
{
	private static final int MIN_QUERY_CHARS = 3;
	private static final int MAX_RESULTS = 150;
	private static final int MAX_VISIBLE_ROWS = 12;
	private static final int SEARCH_DELAY_MS = 180;

	private final SuggestionField field = new SuggestionField();
	private final DefaultListModel<MapArea> listModel = new DefaultListModel<>();
	private final JList<MapArea> list = new JList<>(listModel);
	private final JPopupMenu popup = new JPopupMenu();
	private final Event<MapArea> onAreaSelected = new Event<>();
	private final Timer searchTimer = new Timer(SEARCH_DELAY_MS, e -> runSearch());

	private MapArea suggestion;
	private MapArea committedArea;
	private BiFunction<String, Integer, List<MapArea>> searchProvider = (query, limit) -> List.of();
	private SwingWorker<List<MapArea>, Void> searchWorker;
	private long searchVersion = 0L;
	private boolean adjusting = false;

	public MapAreaSearchPanel()
	{
		super(new BorderLayout(8, 0));
		setOpaque(true);
		setPreferredSize(new Dimension(560, 42));
		setMinimumSize(new Dimension(360, 42));
		setMaximumSize(new Dimension(720, 42));
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(55, 55, 55)),
			new EmptyBorder(6, 8, 6, 8)
		));
		searchTimer.setRepeats(false);

		JLabel label = new JLabel("Area");
		add(label, BorderLayout.WEST);

		field.putClientProperty("JTextField.placeholderText", "Type 3+ characters to search map areas");
		field.setFocusTraversalKeysEnabled(false);
		add(field, BorderLayout.CENTER);

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new AreaCellRenderer());
		list.setFocusable(false);

		JScrollPane scroll = new JScrollPane(list);
		scroll.setBorder(BorderFactory.createLineBorder(new Color(65, 65, 65)));
		scroll.setFocusable(false);
		scroll.getViewport().setFocusable(false);
		popup.setBorder(BorderFactory.createEmptyBorder());
		popup.setFocusable(false);
		popup.add(scroll);

		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				documentChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				documentChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				documentChanged();
			}
		});

		field.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				scheduleRefresh();
			}
		});
		list.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				MapArea area = list.getSelectedValue();
				if (area != null)
				{
					activate(area);
				}
			}
		});

		installKeyBindings();
	}

	public Event<MapArea> onAreaSelected()
	{
		return onAreaSelected;
	}

	public void setSearchProvider(BiFunction<String, Integer, List<MapArea>> searchProvider)
	{
		this.searchProvider = searchProvider == null ? (query, limit) -> List.of() : searchProvider;
		field.setEnabled(true);
		clearMatches();
	}

	private void scheduleRefresh()
	{
		if (!adjusting)
		{
			searchTimer.restart();
		}
	}

	private void documentChanged()
	{
		if (!adjusting)
		{
			committedArea = null;
			scheduleRefresh();
		}
	}

	private void runSearch()
	{
		String query = field.getText();
		String normalized = normalize(query);
		if (normalized.length() < MIN_QUERY_CHARS)
		{
			clearMatches();
			return;
		}

		long version = ++searchVersion;
		if (searchWorker != null)
		{
			searchWorker.cancel(true);
		}

		searchWorker = new SwingWorker<>()
		{
			@Override
			protected List<MapArea> doInBackground()
			{
				return searchProvider.apply(query, MAX_RESULTS);
			}

			@Override
			protected void done()
			{
				if (version != searchVersion || isCancelled())
				{
					return;
				}
				try
				{
					applyMatches(query, get());
				}
				catch (InterruptedException ex)
				{
					Thread.currentThread().interrupt();
				}
				catch (CancellationException | ExecutionException ex)
				{
					clearVisibleMatches();
				}
			}
		};
		searchWorker.execute();
	}

	private void applyMatches(String query, List<MapArea> matches)
	{
		if (matches == null)
		{
			matches = List.of();
		}
		listModel.clear();
		for (MapArea area : matches)
		{
			listModel.addElement(area);
		}
		if (!matches.isEmpty())
		{
			list.setSelectedIndex(0);
		}

		suggestion = bestSuggestion(query, matches);
		field.setSuggestion(suggestion == null ? null : suggestion.displayName());

		if (field.hasFocus() && !matches.isEmpty())
		{
			showPopup();
		}
		else
		{
			popup.setVisible(false);
		}
	}

	private void clearMatches()
	{
		searchVersion++;
		if (searchWorker != null)
		{
			searchWorker.cancel(true);
		}
		clearVisibleMatches();
	}

	private void clearVisibleMatches()
	{
		listModel.clear();
		suggestion = null;
		committedArea = null;
		field.setSuggestion(null);
		popup.setVisible(false);
	}

	private MapArea bestSuggestion(String query, List<MapArea> matches)
	{
		if (query == null || query.isBlank() || matches.isEmpty())
		{
			return null;
		}
		String normalizedQuery = normalize(query);
		for (MapArea area : matches)
		{
			if (normalize(area.displayName()).startsWith(normalizedQuery))
			{
				return area;
			}
		}
		return null;
	}

	private void installKeyBindings()
	{
		bindFieldKey(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "moveDown", () -> moveSelection(1));
		bindFieldKey(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "moveUp", () -> moveSelection(-1));
		bindFieldKey(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activateSelection", this::activateBestMatch);
		bindFieldKey(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "commitSuggestion", this::commitSuggestion);
		bindFieldKey(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.SHIFT_DOWN_MASK),
			"commitSuggestionBackwards",
			this::commitSuggestion);
		bindFieldKey(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "hidePopup", () -> popup.setVisible(false));
	}

	private void bindFieldKey(KeyStroke keyStroke, String name, Runnable action)
	{
		field.getInputMap(JComponent.WHEN_FOCUSED).put(keyStroke, name);
		field.getActionMap().put(name, new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				action.run();
			}
		});
	}

	private void activateBestMatch()
	{
		MapArea area = committedAreaIfCurrent();
		if (area == null)
		{
			area = list.getSelectedValue();
		}
		if (area == null)
		{
			area = suggestion;
		}
		if (area == null && listModel.getSize() > 0)
		{
			area = listModel.getElementAt(0);
		}
		if (area != null)
		{
			activate(area);
		}
	}

	private void moveSelection(int delta)
	{
		if (listModel.isEmpty())
		{
			return;
		}
		if (!popup.isVisible())
		{
			showPopup();
		}
		int index = list.getSelectedIndex();
		if (index < 0)
		{
			index = 0;
		}
		else
		{
			index = Math.max(0, Math.min(listModel.size() - 1, index + delta));
		}
		list.setSelectedIndex(index);
		list.ensureIndexIsVisible(index);
	}

	private void commitSuggestion()
	{
		MapArea area = suggestion;
		if (area == null)
		{
			area = list.getSelectedValue();
		}
		if (area == null && listModel.getSize() > 0)
		{
			area = listModel.getElementAt(0);
		}
		if (area == null)
		{
			return;
		}
		searchTimer.stop();
		searchVersion++;
		if (searchWorker != null)
		{
			searchWorker.cancel(true);
		}
		committedArea = area;
		selectArea(area);
		adjusting = true;
		try
		{
			field.setText(area.displayName());
			field.setCaretPosition(field.getDocument().getLength());
		}
		finally
		{
			adjusting = false;
		}
		field.setSuggestion(null);
		showPopup();
	}

	private void activate(MapArea area)
	{
		searchTimer.stop();
		searchVersion++;
		if (searchWorker != null)
		{
			searchWorker.cancel(true);
		}
		committedArea = area;
		adjusting = true;
		try
		{
			field.setText(area.displayName());
			field.setCaretPosition(field.getDocument().getLength());
		}
		finally
		{
			adjusting = false;
		}
		popup.setVisible(false);
		onAreaSelected.emit(area);
	}

	private MapArea committedAreaIfCurrent()
	{
		if (committedArea == null || field.getText() == null)
		{
			return null;
		}
		return field.getText().equals(committedArea.displayName()) ? committedArea : null;
	}

	private void selectArea(MapArea area)
	{
		if (area == null)
		{
			return;
		}
		for (int i = 0; i < listModel.getSize(); i++)
		{
			if (area == listModel.getElementAt(i) || area.dropdownLabel().equals(listModel.getElementAt(i).dropdownLabel()))
			{
				list.setSelectedIndex(i);
				list.ensureIndexIsVisible(i);
				return;
			}
		}
	}

	private void showPopup()
	{
		if (!field.isShowing() || listModel.isEmpty())
		{
			return;
		}
		int rows = Math.min(MAX_VISIBLE_ROWS, Math.max(1, listModel.getSize()));
		list.setVisibleRowCount(rows);
		Dimension rowSize = list.getPreferredScrollableViewportSize();
		int width = Math.max(field.getWidth(), 360);
		int height = Math.min(320, rowSize.height + 4);
		popup.setPopupSize(width, height);
		if (!popup.isVisible())
		{
			popup.show(field, 0, field.getHeight());
		}
	}

	private static String normalize(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.trim().toLowerCase(Locale.ROOT);
	}

	private static final class AreaCellRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index,
		                                              boolean isSelected, boolean cellHasFocus)
		{
			JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (value instanceof MapArea area)
			{
				label.setText(area.dropdownLabel());
				label.setToolTipText(area.searchText());
			}
			return label;
		}
	}

	private static final class SuggestionField extends JTextField
	{
		private String suggestion;

		void setSuggestion(String suggestion)
		{
			this.suggestion = suggestion;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			paintSuggestion(g);
		}

		private void paintSuggestion(Graphics g)
		{
			String text = getText();
			if (suggestion == null || text == null || text.isEmpty() || text.length() >= suggestion.length())
			{
				return;
			}
			if (getCaretPosition() != getDocument().getLength() || getSelectionStart() != getSelectionEnd())
			{
				return;
			}
			if (!suggestion.regionMatches(true, 0, text, 0, text.length()))
			{
				return;
			}

			Rectangle2D caretRect;
			try
			{
				caretRect = modelToView2D(getDocument().getLength());
			}
			catch (BadLocationException ex)
			{
				return;
			}
			if (caretRect == null)
			{
				return;
			}

			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				FontMetrics fm = g2.getFontMetrics(getFont());
				String suffix = suggestion.substring(text.length());
				Color inactive = UIManager.getColor("TextField.inactiveForeground");
				Color color = inactive == null ? new Color(110, 110, 110) : inactive.darker();
				g2.setFont(getFont());
				g2.setColor(color);
				int x = (int) Math.round(caretRect.getX());
				int y = (int) Math.round(caretRect.getY()) + fm.getAscent();
				Rectangle clip = g2.getClipBounds();
				if (clip != null)
				{
					g2.setClip(new Rectangle(x, clip.y, Math.max(0, getWidth() - x - 4), clip.height));
				}
				g2.drawString(suffix, x, y);
			}
			finally
			{
				g2.dispose();
			}
		}
	}

	public static final class Event<T>
	{
		private final List<Consumer<T>> listeners = new ArrayList<>();

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
