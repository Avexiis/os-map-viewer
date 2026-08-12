package com.xeon.plugins.shortestpath;

import com.xeon.model.Tile;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

final class ShortestPathRoutePanel extends JPanel {
    private final RouteTableModel model = new RouteTableModel();
    private final JTable table = new RouteTable(model);
    private Consumer<Tile> onFocusTile;

    ShortestPathRoutePanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Route Steps");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        add(title, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new WrapCellRenderer());
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 2));
        table.setRowHeight(26);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setResizingAllowed(false);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) {
                    return;
                }
                Entry entry = model.entryAt(table.convertRowIndexToModel(row));
                if (entry != null && onFocusTile != null) {
                    onFocusTile.accept(copy(entry.tile()));
                }
            }
        });
        table.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateRowHeights();
            }
        });

        TableColumn pointColumn = table.getColumnModel().getColumn(0);
        pointColumn.setMinWidth(68);
        pointColumn.setPreferredWidth(76);
        pointColumn.setMaxWidth(92);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        add(scroll, BorderLayout.CENTER);
    }

    void setEntries(List<Entry> entries) {
        model.setEntries(entries);
        SwingUtilities.invokeLater(this::updateRowHeights);
    }

    void setOnFocusTile(Consumer<Tile> onFocusTile) {
        this.onFocusTile = onFocusTile;
    }

    private void updateRowHeights() {
        for (int row = 0; row < table.getRowCount(); row++) {
            int height = table.getRowHeight();
            for (int column = 0; column < table.getColumnCount(); column++) {
                TableCellRenderer renderer = table.getCellRenderer(row, column);
                Component component = table.prepareRenderer(renderer, row, column);
                height = Math.max(height, component.getPreferredSize().height);
            }
            if (table.getRowHeight(row) != height) {
                table.setRowHeight(row, height);
            }
        }
    }

    private static Tile copy(Tile tile) {
        return tile == null ? null : new Tile(tile.x, tile.y, tile.z);
    }

    record Entry(String pointLabel, String detail, String tooltip, Tile tile, int pointIndex) {
    }

    private static final class RouteTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Point", "Route step"};
        private List<Entry> entries = List.of();

        void setEntries(List<Entry> entries) {
            this.entries = entries == null ? List.of() : List.copyOf(entries);
            fireTableDataChanged();
        }

        Entry entryAt(int row) {
            return row >= 0 && row < entries.size() ? entries.get(row) : null;
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Entry entry = entries.get(rowIndex);
            return columnIndex == 0 ? entry.pointLabel() : entry.detail();
        }
    }

    private static final class RouteTable extends JTable {
        RouteTable(RouteTableModel model) {
            super(model);
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            if (row < 0) {
                return null;
            }
            RouteTableModel routeModel = (RouteTableModel) getModel();
            Entry entry = routeModel.entryAt(convertRowIndexToModel(row));
            return entry == null ? null : entry.tooltip();
        }
    }

    private static final class WrapCellRenderer extends JTextArea implements TableCellRenderer {
        WrapCellRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
            setBorder(new EmptyBorder(4, 6, 4, 6));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            setText(value == null ? "" : value.toString());
            setFont(table.getFont());
            setSize(table.getColumnModel().getColumn(column).getWidth(), Short.MAX_VALUE);
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(table.getBackground());
                setForeground(table.getForeground());
            }
            return this;
        }
    }
}
