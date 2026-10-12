package com.clipboardoverhall;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main entry point for ClipboardOverhall - a macOS menu bar clipboard utility.
 * 
 * Runs as a background utility with no Dock icon (LSUIElement behavior via
 * System property apple.awt.UIElement). Lives entirely in the macOS menu bar tray.
 */
public class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        // Hide from Dock - run as background utility
        System.setProperty("apple.awt.UIElement", "true");

        // Install FlatLaf dark theme
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to set FlatLaf L&F", e);
        }

        SwingUtilities.invokeLater(() -> {
            try {
                initApplication();
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Failed to initialize application", e);
                JOptionPane.showMessageDialog(null,
                        "Failed to start ClipboardOverhall:\n" + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    private static void initApplication() throws Exception {
        // Verify SystemTray support
        if (!SystemTray.isSupported()) {
            throw new UnsupportedOperationException("System tray is not supported on this platform.");
        }

        // Initialize the clipboard slot manager (9 slots)
        ClipboardSlotManager slotManager = new ClipboardSlotManager(9);

        // Initialize and start the clipboard monitor (watches system clipboard)
        ClipboardMonitor clipboardMonitor = new ClipboardMonitor(slotManager);
        clipboardMonitor.start();

        // Create the overlay panel (hot corner panel)
        OverlayPanel overlayPanel = new OverlayPanel(slotManager);

        // Initialize the hot corner detector
        HotCornerDetector hotCornerDetector = new HotCornerDetector(overlayPanel);
        hotCornerDetector.start();

        // Initialize global hotkey manager (registers JNativeHook)
        HotKeyManager hotKeyManager = new HotKeyManager(slotManager, overlayPanel);
        hotKeyManager.register();

        // Create the system tray icon and menu
        setupSystemTray(slotManager, clipboardMonitor, overlayPanel, hotCornerDetector, hotKeyManager);

        LOG.info("ClipboardOverhall started successfully.");
    }

    private static void setupSystemTray(ClipboardSlotManager slotManager,
                                         ClipboardMonitor clipboardMonitor,
                                         OverlayPanel overlayPanel,
                                         HotCornerDetector hotCornerDetector,
                                         HotKeyManager hotKeyManager) throws AWTException {
        SystemTray tray = SystemTray.getSystemTray();

        // Create a simple tray icon (clipboard emoji rendered as image)
        Image trayImage = createTrayIcon();
        TrayIcon trayIcon = new TrayIcon(trayImage, "ClipboardOverhall");
        trayIcon.setImageAutoSize(true);

        // Build popup menu
        PopupMenu popup = new PopupMenu();

        MenuItem showPanelItem = new MenuItem("Show Shelf Panel");
        showPanelItem.addActionListener(e -> SwingUtilities.invokeLater(() -> overlayPanel.showPanel()));
        popup.add(showPanelItem);

        popup.addSeparator();

        // Show slot contents preview
        for (int i = 0; i < slotManager.getSlotCount(); i++) {
            final int slot = i;
            MenuItem slotItem = new MenuItem("Slot " + (i + 1) + ": " + slotManager.getSlotPreview(i));
            slotItem.addActionListener(e -> slotManager.pasteFromSlot(slot));
            popup.add(slotItem);
        }

        popup.addSeparator();

        MenuItem clearAllItem = new MenuItem("Clear All Slots");
        clearAllItem.addActionListener(e -> {
            slotManager.clearAll();
            // Refresh menu labels
            refreshTrayMenu(popup, slotManager, overlayPanel);
        });
        popup.add(clearAllItem);

        popup.addSeparator();

        MenuItem aboutItem = new MenuItem("About ClipboardOverhall");
        aboutItem.addActionListener(e -> SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null,
                        "ClipboardOverhall v1.0\n\nAdvanced Multi-Slot Clipboard & Drag-and-Drop Shelf\n\n" +
                        "Hotkeys:\n" +
                        "  Cmd+Shift+1..9  →  Copy to slot\n" +
                        "  Cmd+Shift+Alt+1..9  →  Paste from slot\n\n" +
                        "Move mouse to top-left corner to open the shelf panel.",
                        "About", JOptionPane.INFORMATION_MESSAGE)));
        popup.add(aboutItem);

        MenuItem quitItem = new MenuItem("Quit");
        quitItem.addActionListener(e -> {
            hotKeyManager.unregister();
            hotCornerDetector.stop();
            clipboardMonitor.stop();
            tray.remove(trayIcon);
            System.exit(0);
        });
        popup.add(quitItem);

        trayIcon.setPopupMenu(popup);
        tray.add(trayIcon);
    }

    private static void refreshTrayMenu(PopupMenu popup, ClipboardSlotManager slotManager, OverlayPanel overlayPanel) {
        popup.removeAll();

        MenuItem showPanelItem = new MenuItem("Show Shelf Panel");
        showPanelItem.addActionListener(e -> SwingUtilities.invokeLater(() -> overlayPanel.showPanel()));
        popup.add(showPanelItem);

        popup.addSeparator();

        for (int i = 0; i < slotManager.getSlotCount(); i++) {
            final int slot = i;
            MenuItem slotItem = new MenuItem("Slot " + (i + 1) + ": " + slotManager.getSlotPreview(i));
            slotItem.addActionListener(e -> slotManager.pasteFromSlot(slot));
            popup.add(slotItem);
        }

        popup.addSeparator();

        MenuItem clearAllItem = new MenuItem("Clear All Slots");
        clearAllItem.addActionListener(e -> {
            slotManager.clearAll();
            refreshTrayMenu(popup, slotManager, overlayPanel);
        });
        popup.add(clearAllItem);

        popup.addSeparator();

        MenuItem quitItem = new MenuItem("Quit");
        quitItem.addActionListener(e -> System.exit(0));
        popup.add(quitItem);
    }

    /**
     * Creates a simple 22x22 tray icon with a clipboard symbol.
     */
    private static Image createTrayIcon() {
        int size = 22;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw clipboard body
        g.setColor(new Color(200, 200, 200));
        g.fillRoundRect(3, 4, 16, 17, 3, 3);

        // Draw clipboard clip
        g.setColor(new Color(160, 160, 160));
        g.fillRoundRect(7, 1, 8, 6, 2, 2);

        // Draw lines (text representation)
        g.setColor(new Color(80, 80, 80));
        g.fillRect(6, 10, 10, 2);
        g.fillRect(6, 14, 10, 2);
        g.fillRect(6, 18, 6, 2);

        g.dispose();
        return img;
    }
}
