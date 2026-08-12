package com.xeon.controller;

import com.xeon.App;
import com.xeon.atlas.AtlasStorage;
import com.xeon.atlas.MapAtlasPrinter;
import com.xeon.atlas.RuneLiteCacheLocator;
import com.xeon.config.ConfigManager;
import com.xeon.config.PluginConfig;
import com.xeon.io.ViewerSettings;
import com.xeon.model.Tile;
import com.xeon.plugin.MapViewerPlugin;
import com.xeon.plugin.PluginContext;
import com.xeon.plugin.PluginRegistry;
import com.xeon.plugins.groundmarkers.GroundMarkerPlugin;
import com.xeon.plugins.shortestpath.ShortestPathPlugin;
import com.xeon.util.Images;
import com.xeon.util.Ui;
import com.xeon.view.MapAreaSearchPanel;
import com.xeon.view.MapControlsPanel;
import com.xeon.view.MapPanel;
import com.xeon.view.MapView;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MapViewerController {
    private static final Color RAIL_BACKGROUND = new Color(0x1B1D22);
    private static final Color WARNING_ORANGE = new Color(0xD98C20);
    private static final String GITHUB_URL = "https://github.com/Avexiis/os-map-viewer";
    private static final String EXPERIMENTAL_MAP_PRINT_DESCRIPTION =
            "This option will load an internal copy of RuneLite's cache module and read the OSRS game cache "
                    + "installed to your PC for the purpose of printing a new map. Use this feature if Jagex "
                    + "updates the game and adds new map areas, so OS Map Viewer shows the latest content.";
    private static final String MAP_PRINT_NOTICE =
            "NOTICE: This application makes NO attempt to modify the game's data. It runs NO risk of game "
                    + "corruption or terms-of-service violations, however it should be used at your own risk. "
                    + "The map printing process can be quite resource intensive and should only be attempted "
                    + "on devices with at least 10GB of RAM not in use. If your PC cannot handle this, or if "
                    + "the operation fails, please check the GitHub for the latest release version of OS Map Viewer.";
    private static final double FULL_JUMP_ZOOM = Double.POSITIVE_INFINITY;
    private static final MemoryPreset[] MEMORY_PRESETS = new MemoryPreset[]{
            new MemoryPreset("512 MB", 512),
            new MemoryPreset("1 GB", 1024),
            new MemoryPreset("2 GB", 2048),
            new MemoryPreset("3 GB", 3072),
            new MemoryPreset("4 GB", 4096),
            new MemoryPreset("5 GB", 5120),
            new MemoryPreset("Unlimited", ViewerSettings.MEMORY_BUDGET_UNLIMITED_MB)
    };

    private JFrame frame;
    private MapPanel mapPanel;
    private JScrollPane mapScrollPane;
    private JLabel statusLabel;
    private BufferedImage appIcon;
    private MapControlsPanel mapControls;
    private MapAreaSearchPanel areaSearchPanel;
    private JPanel westPanel;
    private JPanel toolRail;
    private JTabbedPane leftTabs;
    private JTabbedPane rightTabs;
    private JPanel rightSidebar;
    private JLayeredPane viewerPane;
    private final JButton btnOptions = new JButton(new HamburgerIcon());
    private final JPopupMenu optionsMenu = new JPopupMenu();
    private long optionsMenuHiddenAtMillis = 0L;
    private final ConfigManager configManager = ConfigManager.loadDefault();
    private final ViewerSettings settings = ViewerSettings.load(configManager);
    private final List<PluginHandle> plugins = new ArrayList<>();
    private boolean showGrid = false;
    private boolean showRegionCoordinates = false;
    private boolean showRegionIds = false;
    private boolean showMapIcons = true;
    private boolean showMapLabels = true;
    private boolean mapLocked = false;
    private boolean mapPrintInProgress = false;

    public MapViewerController() {
        for (MapViewerPlugin plugin : PluginRegistry.load(new GroundMarkerPlugin(), new ShortestPathPlugin())) {
            plugins.add(new PluginHandle(plugin, settings.pluginsEnabledOnStartup()));
        }
    }

    public void show() {
        frame = new JFrame("OS Map Viewer");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveLastViewState();
                for (PluginHandle handle : plugins) {
                    if (handle.installed) {
                        handle.plugin.uninstall();
                    }
                }
                if (mapPanel != null) {
                    mapPanel.dispose();
                }
            }
        });

        loadIcon();
        if (appIcon != null) {
            int[] sizes = new int[]{16, 20, 24, 32, 48, 64, 128, 256, 512};
            List<Image> icons = new ArrayList<>(sizes.length);
            for (int size : sizes) {
                BufferedImage scaled = Images.scale(appIcon, size, size);
                if (scaled != null) {
                    icons.add(scaled);
                }
            }
            if (!icons.isEmpty()) {
                frame.setIconImages(icons);
            }
        }

        buildUi();
        frame.setSize(1660, 950);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        for (PluginHandle handle : plugins) {
            if (handle.enabled && handle.installed) {
                handle.plugin.afterShow();
            }
        }
        SwingUtilities.invokeLater(() -> {
            if (!restoreLastViewState()) {
                mapPanel.centerMap();
            }
            if (enabledPluginCount() == 0) {
                showNoPluginsToast();
            }
        });
    }

    private void buildUi() {
        mapPanel = createMapPanel(null);
        mapScrollPane = new JScrollPane(mapPanel);
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        mapControls = new MapControlsPanel();
        mapControls.setPlaneCount(mapPanel.getPlaneCount());
        mapControls.setSelectedPlane(mapPanel.getPlane());
        areaSearchPanel = new MapAreaSearchPanel();
        areaSearchPanel.setSearchProvider(mapPanel::searchMapAreas);
        mapPanel.setMapBackgroundColor(settings.mapBackgroundColor());

        toolRail = buildToolRail();
        westPanel = new JPanel(new BorderLayout());
        westPanel.add(toolRail, BorderLayout.WEST);
        leftTabs = new JTabbedPane();
        rightTabs = new JTabbedPane();
        rightSidebar = new JPanel(new BorderLayout());
        rightSidebar.setMinimumSize(new Dimension(330, 0));
        rightSidebar.setPreferredSize(new Dimension(330, 0));
        viewerPane = buildViewerPane(mapScrollPane);

        frame.add(westPanel, BorderLayout.WEST);
        frame.add(viewerPane, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        JPanel searchHost = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        searchHost.add(areaSearchPanel);
        bottom.add(searchHost, BorderLayout.CENTER);
        bottom.add(statusLabel, BorderLayout.SOUTH);
        frame.add(bottom, BorderLayout.SOUTH);

        installMapControls();
        installMapAreaSelectionHandler();

        for (PluginHandle handle : plugins) {
            if (handle.enabled) {
                installPlugin(handle);
            }
        }
        refreshPluginHosts();
    }

    private MapPanel createMapPanel(Path preferredAtlas) {
        long budget = memoryBudgetBytes(selectedMemoryBudgetMb());
        try {
            Path atlas = preferredAtlas == null ? AtlasStorage.ensureInstalledAtlas() : preferredAtlas;
            return new MapPanel(atlas, budget);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare installed atlas: " + ex.getMessage(), ex);
        }
    }

    private JLayeredPane buildViewerPane(JScrollPane scrollPane) {
        return new JLayeredPane() {
            {
                add(scrollPane, JLayeredPane.DEFAULT_LAYER);
                add(mapControls, JLayeredPane.PALETTE_LAYER);
            }

            @Override
            public void doLayout() {
                scrollPane.setBounds(0, 0, getWidth(), getHeight());
                Dimension controlsSize = mapControls.getPreferredSize();
                int x = Math.max(0, getWidth() - controlsSize.width);
                mapControls.setBounds(x, 0, controlsSize.width, controlsSize.height);
            }
        };
    }

    private JPanel buildToolRail() {
        JPanel rail = new JPanel();
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.setOpaque(true);
        rail.setBackground(RAIL_BACKGROUND);
        rail.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(55, 55, 55)));
        rail.setPreferredSize(new Dimension(44, 0));
        rail.setMinimumSize(new Dimension(44, 0));
        styleRailButton(btnOptions);
        btnOptions.setToolTipText("Options");
        btnOptions.addActionListener(e -> toggleOptionsMenu());

        JMenuItem pluginsItem = new JMenuItem("Plugins...");
        pluginsItem.addActionListener(e -> showPluginManagerDialog());
        JMenuItem loadPlugin = new JMenuItem("Load plugin JAR...");
        loadPlugin.addActionListener(e -> promptLoadPluginJar());
        JMenuItem experimentalOptions = new JMenuItem("Experimental Options");
        experimentalOptions.addActionListener(e -> showExperimentalOptionsDialog());
        JMenuItem backgroundColor = new JMenuItem("Map Background Color...");
        backgroundColor.addActionListener(e -> chooseMapBackgroundColor());
        JCheckBoxMenuItem jumpToLast = new JCheckBoxMenuItem(
                "Jump to last region on start",
                settings.jumpToLastRegionOnStart()
        );
        jumpToLast.addActionListener(e -> {
            settings.setJumpToLastRegionOnStart(jumpToLast.isSelected());
            setStatus(jumpToLast.isSelected()
                    ? "Will jump to last region on next launch"
                    : "Will start centered on next launch");
        });
        JMenu memoryBudget = buildMemoryBudgetMenu();
        optionsMenu.add(pluginsItem);
        optionsMenu.add(loadPlugin);
        optionsMenu.add(experimentalOptions);
        optionsMenu.addSeparator();
        optionsMenu.add(backgroundColor);
        optionsMenu.add(jumpToLast);
        optionsMenu.add(memoryBudget);
        optionsMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                optionsMenuHiddenAtMillis = System.currentTimeMillis();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                optionsMenuHiddenAtMillis = System.currentTimeMillis();
            }
        });

        return rail;
    }

    private void installMapControls() {
        mapControls.onToggleGrid.addListener(value -> {
            showGrid = value;
            mapPanel.setShowGrid(value);
        });
        mapControls.onToggleRegionCoordinates.addListener(value -> {
            showRegionCoordinates = value;
            mapPanel.setShowRegionCoordinates(value);
        });
        mapControls.onToggleRegionIds.addListener(value -> {
            showRegionIds = value;
            mapPanel.setShowRegionIds(value);
        });
        mapControls.onToggleMapText.addListener(value -> {
            showMapLabels = value;
            mapPanel.setShowMapLabels(value);
        });
        mapControls.onToggleMapIcons.addListener(value -> {
            showMapIcons = value;
            mapPanel.setShowMapIcons(value);
        });
        mapControls.onToggleLocked.addListener(value -> {
            mapLocked = value;
            mapPanel.setMapLocked(value);
            setStatus(value ? "Map locked" : "Map unlocked");
        });
        mapControls.onPlaneChanged.addListener(plane -> mapPanel.setPlane(plane));
        mapControls.onJumpToTile.addListener(tile -> {
            if (tile == null) {
                return;
            }
            mapPanel.setPlane(tile.z);
            mapPanel.focusTile(tile, FULL_JUMP_ZOOM);
            for (PluginHandle handle : plugins) {
                if (handle.enabled && handle.installed) {
                    handle.plugin.tileFocused(tile);
                }
            }
        });
        attachMapPanelListeners(mapPanel);
    }

    private void attachMapPanelListeners(MapPanel panel) {
        panel.addPlaneChangeListener(plane -> {
            mapControls.setSelectedPlane(plane);
            for (PluginHandle handle : plugins) {
                if (handle.enabled && handle.installed) {
                    handle.plugin.planeChanged(plane);
                }
            }
        });
    }

    private void applyMapControlState(MapPanel panel) {
        panel.setShowGrid(showGrid);
        panel.setShowRegionCoordinates(showRegionCoordinates);
        panel.setShowRegionIds(showRegionIds);
        panel.setShowMapLabels(showMapLabels);
        panel.setShowMapIcons(showMapIcons);
        panel.setMapBackgroundColor(settings.mapBackgroundColor());
        panel.setMemoryBudgetBytes(memoryBudgetBytes(selectedMemoryBudgetMb()));
    }

    private int clampedPlane(int plane, MapPanel panel) {
        return Math.max(0, Math.min(panel.getPlaneCount() - 1, plane));
    }

    private void notifyPluginsAfterShow() {
        if (frame == null || !frame.isShowing()) {
            return;
        }
        for (PluginHandle handle : plugins) {
            if (handle.enabled && handle.installed) {
                handle.plugin.afterShow();
            }
        }
    }

    private void replaceMapPanel(Path atlasPath) {
        MapPanel oldPanel = mapPanel;
        Tile center = oldPanel == null ? null : oldPanel.getCenterTile();
        double zoom = oldPanel == null ? 1.0 : oldPanel.getZoom();
        int plane = oldPanel == null ? 0 : oldPanel.getPlane();

        MapPanel nextPanel = new MapPanel(atlasPath, memoryBudgetBytes(selectedMemoryBudgetMb()));
        applyMapControlState(nextPanel);
        nextPanel.setMapLocked(false);

        for (PluginHandle handle : plugins) {
            if (handle.installed) {
                handle.plugin.uninstall();
                handle.installed = false;
            }
        }

        mapPanel = nextPanel;
        mapScrollPane.setViewportView(nextPanel);
        mapControls.setPlaneCount(nextPanel.getPlaneCount());
        mapControls.setSelectedPlane(clampedPlane(plane, nextPanel));
        attachMapPanelListeners(nextPanel);
        areaSearchPanel.setSearchProvider(mapPanel::searchMapAreas);

        if (center == null) {
            nextPanel.centerMap();
        } else {
            nextPanel.focusTile(new Tile(center.x, center.y, clampedPlane(center.z, nextPanel)), zoom);
        }
        nextPanel.setMapLocked(mapLocked);

        if (oldPanel != null) {
            oldPanel.dispose();
        }

        for (PluginHandle handle : plugins) {
            if (handle.enabled) {
                installPlugin(handle);
            }
        }
        refreshPluginHosts();
        notifyPluginsAfterShow();
    }

    private void installMapAreaSelectionHandler() {
        areaSearchPanel.onAreaSelected().addListener(area -> {
            if (area == null) {
                return;
            }
            Tile tile = area.tile();
            mapPanel.focusTile(tile, FULL_JUMP_ZOOM);
            setStatus("Focused " + area.displayName() + " at " + tile.x + ", " + tile.y + ", " + tile.z);
        });
    }

    private boolean installPlugin(PluginHandle handle) {
        if (handle == null || handle.installed) {
            return false;
        }
        handle.plugin.install(new DefaultPluginContext(handle.plugin.id()));
        handle.installed = true;
        return true;
    }

    private void refreshPluginHosts() {
        leftTabs.removeAll();
        rightTabs.removeAll();
        rebuildToolRail();

        for (PluginHandle handle : plugins) {
            if (!handle.enabled || !handle.installed) {
                continue;
            }

            JComponent left = handle.plugin.leftComponent();
            if (left != null) {
                leftTabs.addTab(handle.plugin.displayName(), left);
            }
            JComponent right = handle.plugin.rightComponent();
            if (right != null) {
                rightTabs.addTab(handle.plugin.displayName(), right);
            }
        }

        if (leftTabs.getTabCount() > 0) {
            leftTabs.setMinimumSize(new Dimension(320, 0));
            leftTabs.setPreferredSize(new Dimension(320, 0));
            if (leftTabs.getParent() == null) {
                westPanel.add(leftTabs, BorderLayout.CENTER);
            }
        } else if (leftTabs.getParent() != null) {
            westPanel.remove(leftTabs);
        }

        rightSidebar.removeAll();
        if (rightTabs.getTabCount() > 0) {
            rightSidebar.add(rightTabs, BorderLayout.CENTER);
            if (rightSidebar.getParent() == null) {
                frame.add(rightSidebar, BorderLayout.EAST);
            }
        } else if (rightSidebar.getParent() != null) {
            frame.remove(rightSidebar);
        }
        westPanel.revalidate();
        frame.revalidate();
        frame.repaint();
    }

    private void rebuildToolRail() {
        toolRail.removeAll();
        toolRail.add(Box.createVerticalStrut(4));
        toolRail.add(btnOptions);

        boolean hasPluginButtons = false;
        for (PluginHandle handle : plugins) {
            if (!handle.enabled || !handle.installed) {
                continue;
            }
            if (!hasPluginButtons) {
                toolRail.add(Box.createVerticalStrut(6));
                toolRail.add(railSeparator());
                toolRail.add(Box.createVerticalStrut(6));
                hasPluginButtons = true;
            }
            JButton button = new JButton(iconFor(handle.plugin));
            button.setToolTipText(handle.plugin.displayName());
            button.getAccessibleContext().setAccessibleName(handle.plugin.displayName());
            styleRailButton(button);
            button.addActionListener(e -> showPluginActionMenu(button, handle.plugin));
            toolRail.add(button);
            toolRail.add(Box.createVerticalStrut(4));
        }

        toolRail.add(Box.createVerticalGlue());
        toolRail.revalidate();
        toolRail.repaint();
    }

    private Component railSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setMaximumSize(new Dimension(30, 1));
        separator.setAlignmentX(Component.CENTER_ALIGNMENT);
        separator.setBackground(RAIL_BACKGROUND);
        return separator;
    }

    private void toggleOptionsMenu() {
        if (optionsMenu.isVisible()) {
            optionsMenu.setVisible(false);
            return;
        }
        if (btnOptions.getMousePosition() != null && System.currentTimeMillis() - optionsMenuHiddenAtMillis < 250L) {
            return;
        }
        optionsMenu.show(btnOptions, btnOptions.getWidth(), 0);
    }

    private void showPluginActionMenu(AbstractButton owner, MapViewerPlugin plugin) {
        JPopupMenu menu = plugin.actionMenu();
        if (menu == null) {
            setStatus(plugin.displayName());
            return;
        }
        if (menu.isVisible()) {
            menu.setVisible(false);
            return;
        }
        menu.show(owner, owner.getWidth(), 0);
    }

    private Icon iconFor(MapViewerPlugin plugin) {
        Icon icon = plugin.icon();
        return icon == null ? new LetterIcon(initials(plugin.displayName())) : icon;
    }

    private static String initials(String text) {
        if (text == null || text.isBlank()) {
            return "?";
        }
        StringBuilder out = new StringBuilder(2);
        for (String part : text.trim().split("\\s+")) {
            if (!part.isBlank()) {
                out.append(Character.toUpperCase(part.charAt(0)));
                if (out.length() == 2) {
                    break;
                }
            }
        }
        return out.isEmpty() ? "?" : out.toString();
    }

    private void showPluginManagerDialog() {
        JDialog dialog = new JDialog(frame, "Plugins", true);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        if (plugins.isEmpty()) {
            JLabel empty = new JLabel("No plugins installed.");
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            list.add(empty);
        } else {
            for (PluginHandle handle : plugins) {
                JCheckBox enabled = new JCheckBox(handle.plugin.displayName(), handle.enabled);
                JLabel id = new JLabel(handle.plugin.id());
                id.setForeground(new Color(155, 155, 155));
                enabled.addActionListener(e -> setPluginEnabled(handle, enabled.isSelected(), true));

                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(65, 65, 65)),
                        BorderFactory.createEmptyBorder(8, 8, 8, 8)
                ));
                row.add(enabled, BorderLayout.WEST);
                row.add(id, BorderLayout.EAST);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
                list.add(row);
                list.add(Box.createVerticalStrut(6));
            }
        }

        JCheckBox startWithPlugins = new JCheckBox("Start with plugins enabled", settings.pluginsEnabledOnStartup());
        startWithPlugins.setToolTipText("Saved to user.home/os-map-viewer/config.json and applied on next launch.");
        startWithPlugins.addActionListener(e -> {
            settings.setPluginsEnabledOnStartup(startWithPlugins.isSelected());
            setStatus(startWithPlugins.isSelected()
                    ? "Plugins will start enabled next launch"
                    : "Plugins will start disabled next launch");
        });
        JPanel startup = new JPanel(new BorderLayout());
        startup.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        startup.add(startWithPlugins, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton load = new JButton("Load plugin JAR...");
        JButton close = new JButton("Close");
        styleToolbarButton(load);
        styleToolbarButton(close);
        load.addActionListener(e -> {
            dialog.dispose();
            promptLoadPluginJar();
            showPluginManagerDialog();
        });
        close.addActionListener(e -> dialog.dispose());
        buttons.add(load);
        buttons.add(close);

        JPanel listHost = new JPanel(new BorderLayout());
        listHost.add(list, BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(startup, BorderLayout.NORTH);
        content.add(new JScrollPane(listHost), BorderLayout.CENTER);
        dialog.add(content, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setSize(520, 360);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void setPluginEnabled(PluginHandle handle, boolean enabled, boolean runAfterShow) {
        if (handle == null || handle.enabled == enabled) {
            return;
        }
        if (enabled) {
            handle.enabled = true;
            if (installPlugin(handle) && runAfterShow && frame != null && frame.isShowing()) {
                handle.plugin.afterShow();
            }
            refreshPluginHosts();
            setStatus("Enabled plugin: " + handle.plugin.displayName());
            return;
        }

        if (handle.installed) {
            handle.plugin.uninstall();
            handle.installed = false;
        }
        handle.enabled = false;
        refreshPluginHosts();
        setStatus("Disabled plugin: " + handle.plugin.displayName());
    }

    private void promptLoadPluginJar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load plugin JAR");
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            List<MapViewerPlugin> loaded = PluginRegistry.loadFromJar(file);
            int installed = 0;
            for (MapViewerPlugin plugin : loaded) {
                if (plugin == null || plugins.stream().anyMatch(handle -> handle.plugin.id().equals(plugin.id()))) {
                    continue;
                }
                PluginHandle handle = new PluginHandle(plugin, false);
                plugins.add(handle);
                setPluginEnabled(handle, true, true);
                if (handle.enabled) {
                    installed++;
                }
            }
            refreshPluginHosts();
            setStatus("Loaded " + installed + " plugin(s) from " + file.getName());
        } catch (Exception ex) {
            Ui.error("Failed to load plugin JAR: " + ex.getMessage());
        }
    }

    private void showExperimentalOptionsDialog() {
        JDialog dialog = new JDialog(frame, "Experimental Options", true);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextArea description = wrappedText(EXPERIMENTAL_MAP_PRINT_DESCRIPTION, 470, 88);
        JButton print = new JButton("Print New Map");
        styleToolbarButton(print);
        print.addActionListener(e -> promptPrintNewMap(dialog));

        JPanel row = new JPanel(new BorderLayout(10, 8));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(65, 65, 65)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        row.add(description, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.add(print);
        row.add(actions, BorderLayout.SOUTH);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.add(row);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        styleToolbarButton(close);
        close.addActionListener(e -> dialog.dispose());
        buttons.add(close);

        dialog.add(list, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setSize(560, 300);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void promptPrintNewMap(Window owner) {
        if (mapPrintInProgress) {
            setStatus("Map printing is already running");
            return;
        }
        if (!confirmMapPrintNotice(owner)) {
            return;
        }

        Path cacheDirectory = RuneLiteCacheLocator.locateCacheDirectory();
        if (cacheDirectory == null) {
            cacheDirectory = chooseCacheDirectory(owner);
        }
        if (cacheDirectory == null) {
            return;
        }
        if (owner instanceof JDialog dialog) {
            dialog.dispose();
        }
        startMapPrint(cacheDirectory);
    }

    private boolean confirmMapPrintNotice(Window owner) {
        JTextArea message = wrappedText(MAP_PRINT_NOTICE, 540, 180);
        JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION);
        JDialog dialog = pane.createDialog(owner == null ? frame : owner, "Print New Map");
        dialog.setVisible(true);
        Object value = pane.getValue();
        return value instanceof Integer answer && answer == JOptionPane.YES_OPTION;
    }

    private Path chooseCacheDirectory(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose OSRS Cache Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        Path candidate = firstExistingCacheCandidate();
        if (candidate != null) {
            chooser.setCurrentDirectory(candidate.getParent().toFile());
            chooser.setSelectedFile(candidate.toFile());
        } else {
            chooser.setCurrentDirectory(Path.of(System.getProperty("user.home")).toFile());
        }

        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        Path selected = chooser.getSelectedFile().toPath();
        if (!Files.isDirectory(selected)) {
            Ui.warn("Choose the folder that contains the OSRS cache files.");
            return null;
        }
        if (!Files.isRegularFile(selected.resolve("main_file_cache.dat2"))) {
            int answer = JOptionPane.showConfirmDialog(
                    frame,
                    "That folder does not appear to contain main_file_cache.dat2.\n\nTry using it anyway?",
                    "Choose OSRS Cache Directory",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (answer != JOptionPane.YES_OPTION) {
                return null;
            }
        }
        return selected;
    }

    private static Path firstExistingCacheCandidate() {
        for (Path candidate : RuneLiteCacheLocator.candidateCacheDirectories()) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void startMapPrint(Path cacheDirectory) {
        mapPrintInProgress = true;
        setStatus("Printing new map...");

        JDialog dialog = new JDialog(frame, "Printing New Map", true);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));

        JLabel message = new JLabel("Preparing map printer...");
        JProgressBar progress = new JProgressBar(0, MapAtlasPrinter.TOTAL_STEPS + 3);
        progress.setStringPainted(true);
        progress.setString("Step 0 of " + progress.getMaximum());

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        content.add(message, BorderLayout.NORTH);
        content.add(progress, BorderLayout.CENTER);
        dialog.add(content, BorderLayout.CENTER);
        dialog.setSize(520, 150);
        dialog.setLocationRelativeTo(frame);

        MapPrintWorker worker = new MapPrintWorker(cacheDirectory, dialog, message, progress);
        worker.execute();
        dialog.setVisible(true);
    }

    private JTextArea wrappedText(String text, int width, int height) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setOpaque(false);
        area.setWrapStyleWord(true);
        area.setFont(UIManager.getFont("Label.font"));
        area.setPreferredSize(new Dimension(width, height));
        return area;
    }

    private int enabledPluginCount() {
        int count = 0;
        for (PluginHandle handle : plugins) {
            if (handle.enabled && handle.installed) {
                count++;
            }
        }
        return count;
    }

    private void chooseMapBackgroundColor() {
        Color picked = JColorChooser.showDialog(
                frame,
                "Map Background Color",
                mapPanel == null ? settings.mapBackgroundColor() : mapPanel.getMapBackgroundColor()
        );
        if (picked == null) {
            return;
        }
        settings.setMapBackgroundColor(picked);
        if (mapPanel != null) {
            mapPanel.setMapBackgroundColor(picked);
        }
        setStatus("Map background color updated");
    }

    private JMenu buildMemoryBudgetMenu() {
        JMenu menu = new JMenu("Memory Budget");
        ButtonGroup group = new ButtonGroup();
        int configured = selectedMemoryBudgetMb();
        for (MemoryPreset preset : MEMORY_PRESETS) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(preset.label(), preset.megabytes() == configured);
            item.putClientProperty("preset", preset);
            if (preset.isUnlimited()) {
                item.setForeground(WARNING_ORANGE);
            }
            item.addActionListener(e -> {
                if (!applyMemoryBudget(preset)) {
                    syncMemoryBudgetSelection(group);
                }
            });
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    private boolean applyMemoryBudget(MemoryPreset preset) {
        if (settings.memoryBudgetMb() == preset.megabytes()) {
            return true;
        }
        if (preset.isUnlimited() && !confirmUnlimitedMemoryBudget()) {
            setStatus("Memory budget unchanged");
            return false;
        }
        settings.setMemoryBudgetMb(preset.megabytes());
        if (mapPanel != null) {
            mapPanel.setMemoryBudgetBytes(memoryBudgetBytes(preset.megabytes()));
        }
        setStatus("Memory budget set to " + preset.label());
        return true;
    }

    private boolean confirmUnlimitedMemoryBudget() {
        JTextArea message = new JTextArea(
                "Unlimited memory can use as much RAM as the map cache needs and may make the system less responsive.\n\n"
                        + "Enable unlimited map cache memory?"
        );
        message.setEditable(false);
        message.setFocusable(false);
        message.setLineWrap(true);
        message.setOpaque(false);
        message.setWrapStyleWord(true);
        message.setFont(UIManager.getFont("Label.font"));

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(new EmptyBorder(6, 6, 2, 6));
        content.setPreferredSize(new Dimension(440, 110));
        content.add(message, BorderLayout.CENTER);

        JOptionPane pane = new JOptionPane(content, JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION);
        JDialog dialog = pane.createDialog(frame, "Unlimited Memory Budget");
        dialog.pack();
        Dimension minimum = new Dimension(500, 210);
        if (dialog.getWidth() < minimum.width || dialog.getHeight() < minimum.height) {
            dialog.setSize(new Dimension(
                    Math.max(dialog.getWidth(), minimum.width),
                    Math.max(dialog.getHeight(), minimum.height)
            ));
        }
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);

        Object answer = pane.getValue();
        return answer instanceof Integer value && value == JOptionPane.YES_OPTION;
    }

    private void syncMemoryBudgetSelection(ButtonGroup group) {
        int configured = selectedMemoryBudgetMb();
        java.util.Enumeration<AbstractButton> buttons = group.getElements();
        while (buttons.hasMoreElements()) {
            AbstractButton button = buttons.nextElement();
            Object preset = button.getClientProperty("preset");
            if (preset instanceof MemoryPreset memoryPreset) {
                button.setSelected(memoryPreset.megabytes() == configured);
            }
        }
    }

    private int selectedMemoryBudgetMb() {
        int configured = settings.memoryBudgetMb();
        for (MemoryPreset preset : MEMORY_PRESETS) {
            if (preset.megabytes() == configured) {
                return configured;
            }
        }
        return MEMORY_PRESETS[0].megabytes();
    }

    private static long memoryBudgetBytes(int megabytes) {
        if (megabytes == ViewerSettings.MEMORY_BUDGET_UNLIMITED_MB) {
            return Long.MAX_VALUE;
        }
        return Math.max(512L, megabytes) * 1024L * 1024L;
    }

    private boolean restoreLastViewState() {
        if (mapPanel == null || !settings.jumpToLastRegionOnStart()) {
            return false;
        }
        int regionId = settings.lastRegionId();
        if (regionId < 0) {
            return false;
        }
        mapPanel.focusRegion(regionId, settings.lastPlane(), settings.lastZoom());
        return true;
    }

    private void saveLastViewState() {
        if (mapPanel == null) {
            return;
        }
        int regionId = mapPanel.getCenterRegionId();
        if (regionId >= 0) {
            settings.setLastView(regionId, mapPanel.getPlane(), mapPanel.getZoom());
        }
    }

    private void showNoPluginsToast() {
        if (viewerPane == null || !viewerPane.isShowing()) {
            return;
        }

        JPanel toast = new JPanel(new BorderLayout(10, 0));
        toast.setOpaque(true);
        toast.setBackground(new Color(0x2B2F36));
        toast.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x5B6470)),
                BorderFactory.createEmptyBorder(8, 10, 8, 8)
        ));

        JLabel message = new JLabel("Plugins are disabled. Use the top-left menu -> Plugins... to enable them.");
        message.setForeground(new Color(0xE6EAF0));
        toast.add(message, BorderLayout.CENTER);

        JButton close = new JButton("Close 5");
        styleToolbarButton(close);
        toast.add(close, BorderLayout.EAST);

        Dimension preferred = toast.getPreferredSize();
        int width = Math.min(preferred.width, Math.max(240, viewerPane.getWidth() - 24));
        toast.setBounds(12, 12, width, preferred.height);
        viewerPane.add(toast, JLayeredPane.MODAL_LAYER);
        viewerPane.repaint(toast.getBounds());

        final int[] remaining = {5};
        final Timer[] timer = new Timer[1];
        Runnable remove = () -> {
            if (timer[0] != null) {
                timer[0].stop();
            }
            if (toast.getParent() != null) {
                Rectangle bounds = toast.getBounds();
                viewerPane.remove(toast);
                viewerPane.repaint(bounds);
            }
        };
        close.addActionListener(e -> remove.run());
        timer[0] = new Timer(1000, e -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                remove.run();
            } else {
                close.setText("Close " + remaining[0]);
            }
        });
        timer[0].start();
    }

    private void loadIcon() {
        try (InputStream in = App.class.getResourceAsStream("icon.png")) {
            if (in != null) {
                appIcon = ImageIO.read(in);
            }
        } catch (Throwable t) {
            System.err.println("Failed to load icon resource: " + t.getMessage());
        }
    }

    private record ProgressUpdate(String message, int step, int totalSteps) {
    }

    private record MapPrintResult(Path installedAtlas) {
    }

    private final class MapPrintWorker extends SwingWorker<MapPrintResult, ProgressUpdate> {
        private final Path cacheDirectory;
        private final JDialog dialog;
        private final JLabel message;
        private final JProgressBar progress;
        private Path jobDirectory;
        private Path backupAtlas;
        private boolean hadPreviousAtlas;
        private boolean installedAtlasTouched;

        private MapPrintWorker(Path cacheDirectory, JDialog dialog, JLabel message, JProgressBar progress) {
            this.cacheDirectory = cacheDirectory;
            this.dialog = dialog;
            this.message = message;
            this.progress = progress;
        }

        @Override
        protected MapPrintResult doInBackground() throws Exception {
            AtlasStorage.ensureDirectories();
            jobDirectory = Files.createTempDirectory(AtlasStorage.tempRoot(), "map-print-");
            Path generatedAtlas = jobDirectory.resolve(AtlasStorage.ATLAS_FILE_NAME);

            MapAtlasPrinter.PrintRequest request = new MapAtlasPrinter.PrintRequest(
                    cacheDirectory,
                    null,
                    generatedAtlas,
                    jobDirectory,
                    MapAtlasPrinter.DEFAULT_TILE_PX,
                    MapAtlasPrinter.defaultLods(),
                    true
            );
            MapAtlasPrinter.print(request, (text, step, totalSteps) ->
                    publish(new ProgressUpdate(text, step, totalSteps + 3)));

            publish(new ProgressUpdate("Validating generated atlas", MapAtlasPrinter.TOTAL_STEPS + 1,
                    MapAtlasPrinter.TOTAL_STEPS + 3));
            MapPanel.validateAtlas(generatedAtlas);

            publish(new ProgressUpdate("Installing generated atlas", MapAtlasPrinter.TOTAL_STEPS + 2,
                    MapAtlasPrinter.TOTAL_STEPS + 3));
            Path installedAtlas = AtlasStorage.installedAtlasPath();
            hadPreviousAtlas = Files.isRegularFile(installedAtlas);
            if (hadPreviousAtlas) {
                backupAtlas = jobDirectory.resolve("previous-" + AtlasStorage.ATLAS_FILE_NAME);
                Files.copy(installedAtlas, backupAtlas, StandardCopyOption.REPLACE_EXISTING);
            }

            AtlasStorage.moveReplacing(generatedAtlas, installedAtlas);
            installedAtlasTouched = true;
            try {
                MapPanel.validateAtlas(installedAtlas);
            } catch (Exception ex) {
                rollbackInstalledAtlas();
                throw ex;
            }

            publish(new ProgressUpdate("Refreshing map viewer", MapAtlasPrinter.TOTAL_STEPS + 3,
                    MapAtlasPrinter.TOTAL_STEPS + 3));
            return new MapPrintResult(installedAtlas);
        }

        @Override
        protected void process(List<ProgressUpdate> updates) {
            if (updates == null || updates.isEmpty()) {
                return;
            }
            ProgressUpdate update = updates.get(updates.size() - 1);
            message.setText(update.message());
            progress.setMaximum(update.totalSteps());
            progress.setValue(update.step());
            progress.setString("Step " + update.step() + " of " + update.totalSteps());
        }

        @Override
        protected void done() {
            try {
                MapPrintResult result = get();
                try {
                    replaceMapPanel(result.installedAtlas());
                } catch (RuntimeException ex) {
                    rollbackInstalledAtlasQuietly();
                    throw ex;
                }
                try {
                    AtlasStorage.recordGeneratedAtlasInstalled();
                } catch (IOException ex) {
                    System.err.println("Failed to record generated atlas metadata: " + ex.getMessage());
                }
                setStatus("Printed and loaded new map atlas");
                dialog.dispose();
                Ui.info("New map atlas printed and loaded.");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                rollbackInstalledAtlasQuietly();
                dialog.dispose();
                showMapPrintFailure(ex);
            } catch (ExecutionException ex) {
                rollbackInstalledAtlasQuietly();
                dialog.dispose();
                showMapPrintFailure(rootCause(ex));
            } catch (RuntimeException ex) {
                rollbackInstalledAtlasQuietly();
                dialog.dispose();
                showMapPrintFailure(ex);
            } finally {
                AtlasStorage.deleteRecursively(jobDirectory);
                mapPrintInProgress = false;
            }
        }

        private void rollbackInstalledAtlasQuietly() {
            try {
                rollbackInstalledAtlas();
            } catch (IOException ignored) {
            }
        }

        private void rollbackInstalledAtlas() throws IOException {
            if (!installedAtlasTouched) {
                return;
            }
            Path installedAtlas = AtlasStorage.installedAtlasPath();
            if (hadPreviousAtlas && backupAtlas != null && Files.isRegularFile(backupAtlas)) {
                AtlasStorage.moveReplacing(backupAtlas, installedAtlas);
            } else {
                Files.deleteIfExists(installedAtlas);
            }
            installedAtlasTouched = false;
        }
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void showMapPrintFailure(Throwable throwable) {
        setStatus("Map print failed");
        String detail = throwable == null || throwable.getMessage() == null
                ? "Unknown error"
                : throwable.getMessage();
        JTextArea message = wrappedText(
                "The new atlas could not be printed or loaded, so OS Map Viewer reverted to the last working atlas.\n\n"
                        + detail + "\n\n"
                        + "For the latest release version, check:\n" + GITHUB_URL,
                520,
                160
        );

        Object[] options = new Object[]{"Open GitHub", "Close"};
        int answer = JOptionPane.showOptionDialog(
                frame,
                message,
                "Map Print Failed",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                options,
                options[1]
        );
        if (answer == JOptionPane.YES_OPTION) {
            openGitHub();
        }
    }

    private void openGitHub() {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(GITHUB_URL));
        } catch (Exception ignored) {
        }
    }

    private final class DefaultPluginContext implements PluginContext {
        private final PluginConfig pluginConfig;

        private DefaultPluginContext(String pluginId) {
            this.pluginConfig = configManager.scope(pluginId);
        }

        @Override
        public JFrame frame() {
            return frame;
        }

        @Override
        public MapView mapPanel() {
            return mapPanel;
        }

        @Override
        public void setStatus(String message) {
            MapViewerController.this.setStatus(message);
        }

        @Override
        public void promptLoadPluginJar() {
            MapViewerController.this.promptLoadPluginJar();
        }

        @Override
        public ConfigManager configManager() {
            return configManager;
        }

        @Override
        public PluginConfig config() {
            return pluginConfig;
        }
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message == null || message.isBlank() ? "Ready" : message);
        }
    }

    private static void styleToolbarButton(AbstractButton button) {
        button.setFocusable(false);
        button.setMargin(new Insets(4, 10, 4, 10));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80)),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)
        ));
    }

    private static void styleRailButton(AbstractButton button) {
        button.setFocusable(false);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        Dimension size = new Dimension(36, 36);
        button.setMinimumSize(size);
        button.setPreferredSize(size);
        button.setMaximumSize(size);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    private static final class PluginHandle {
        final MapViewerPlugin plugin;
        boolean enabled;
        boolean installed;

        PluginHandle(MapViewerPlugin plugin, boolean enabled) {
            this.plugin = plugin;
            this.enabled = enabled;
        }
    }

    private record MemoryPreset(String label, int megabytes) {
        boolean isUnlimited() {
            return megabytes == ViewerSettings.MEMORY_BUDGET_UNLIMITED_MB;
        }
    }

    private static final class HamburgerIcon implements Icon {
        private static final int W = 16;
        private static final int H = 14;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c.isEnabled() ? c.getForeground() : UIManager.getColor("Label.disabledForeground"));
                int lineY = y + 3;
                for (int i = 0; i < 3; i++) {
                    g2.fillRoundRect(x, lineY + i * 5, W, 2, 2, 2);
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return W;
        }

        @Override
        public int getIconHeight() {
            return H;
        }
    }

    private static final class LetterIcon implements Icon {
        private static final int SIZE = 24;
        private final String text;

        LetterIcon(String text) {
            this.text = text == null || text.isBlank() ? "?" : text;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x3B4A5A));
                g2.fillRoundRect(x, y, SIZE, SIZE, 6, 6);
                g2.setColor(new Color(0xDDE7F0));
                Font font = c.getFont().deriveFont(Font.BOLD, text.length() > 1 ? 10f : 12f);
                g2.setFont(font);
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (SIZE - fm.stringWidth(text)) / 2;
                int ty = y + (SIZE - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, tx, ty);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
