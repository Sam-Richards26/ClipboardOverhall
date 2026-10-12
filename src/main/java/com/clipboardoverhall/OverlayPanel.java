package com.clipboardoverhall;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Floating semi-transparent overlay panel that slides in from the top-left corner.
 * Displays clipboard slot contents and supports drag-and-drop of text, images, and files.
 */
public class OverlayPanel {
    private static final Logger LOG = Logger.getLogger(OverlayPanel.class.getName());

    private static final int PANEL_WIDTH = 320;
    private static final int SLOT_HEIGHT = 72;
    private static final int HEADER_HEIGHT = 40;
    private static final Color BG_COLOR = new Color(30, 30, 30, 230);
    private static final Color SLOT_COLOR = new Color(50, 50, 50, 220);
    private static final Color SLOT_HOVER_COLOR = new Color(70, 70, 70, 220);
    private static final Color SLOT_FILLED_COLOR = new Color(45, 65, 85, 220);
    private static final Color ACCENT_COLOR = new Color(88, 166, 255);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color TEXT_DIM_COLOR = new Color(140, 140, 140);

    private final ClipboardSlotManager slotManager;
    private JWindow window;
    private JPanel contentPanel;
    private SlotPanel[] slotPanels;
    private Timer slideTimer;
    private boolean visible = false;
    private int targetX = 0;

    public OverlayPanel(ClipboardSlotManager slotManager) {
        this.slotManager = slotManager;
        SwingUtilities.invokeLater(this::buildUI);
    }

    private void buildUI() {
        window = new JWindow();
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);

        // Enable per-pixel translucency
        window.setBackground(new Color(0, 0, 0, 0));

        int panelHeight = HEADER_HEIGHT + (SLOT_HEIGHT + 8) * slotManager.getSlotCount() + 16;

        contentPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_COLOR);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
            }
        };
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(8, 10, 8, 10));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setMaximumSize(new Dimension(PANEL_WIDTH, HEADER_HEIGHT));
        header.setPreferredSize(new Dimension(PANEL_WIDTH, HEADER_HEIGHT));

        JLabel titleLabel = new JLabel("📋 Clipboard Shelf");
        titleLabel.setFont(new Font("SF Pro Display", Font.BOLD, 14));
        titleLabel.setForeground(TEXT_COLOR);
        header.add(titleLabel, BorderLayout.WEST);

        JLabel closeLabel = new JLabel("✕");
        closeLabel.setFont(new Font("SF Pro Display", Font.PLAIN, 16));
        closeLabel.setForeground(TEXT_DIM_COLOR);
        closeLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                hidePanel();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                closeLabel.setForeground(TEXT_COLOR);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                closeLabel.setForeground(TEXT_DIM_COLOR);
            }
        });
        header.add(closeLabel, BorderLayout.EAST);

        contentPanel.add(header);
        contentPanel.add(Box.createVerticalStrut(4));

        // Slot panels
        slotPanels = new SlotPanel[slotManager.getSlotCount()];
        for (int i = 0; i < slotManager.getSlotCount(); i++) {
            SlotPanel sp = new SlotPanel(i);
            slotPanels[i] = sp;
            contentPanel.add(sp);
            contentPanel.add(Box.createVerticalStrut(6));
        }

        window.setContentPane(contentPanel);
        window.setSize(PANEL_WIDTH, panelHeight);

        // Position off-screen to the left
        window.setLocation(-PANEL_WIDTH, 30);

        // Listen for slot changes
        slotManager.addChangeListener(this::refreshSlots);

        // Auto-hide setup
        setupAutoHide();
    }

    private void setupAutoHide() {
        Timer autoHideTimer = new Timer(10000, e -> {
            if (visible) {
                hidePanel();
            }
        });
        autoHideTimer.setRepeats(false);

        window.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                autoHideTimer.restart();
            }
        });

        contentPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                autoHideTimer.restart();
            }
        });
    }

    /**
     * Show the panel with a slide-in animation.
     */
    public void showPanel() {
        if (visible) return;
        visible = true;
        refreshSlots();
        window.setLocation(-PANEL_WIDTH, 30);
        window.setVisible(true);
        targetX = 10;
        animateSlide();
    }

    /**
     * Hide the panel with a slide-out animation.
     */
    public void hidePanel() {
        if (!visible) return;
        visible = false;
        targetX = -PANEL_WIDTH;
        animateSlide();
    }

    /**
     * Toggle panel visibility.
     */
    public void togglePanel() {
        if (visible) {
            hidePanel();
        } else {
            showPanel();
        }
    }

    public boolean isVisible() {
        return visible;
    }

    private void animateSlide() {
        if (slideTimer != null && slideTimer.isRunning()) {
            slideTimer.stop();
        }

        slideTimer = new Timer(12, null); // ~83fps
        slideTimer.addActionListener(e -> {
            int currentX = window.getX();
            int diff = targetX - currentX;

            if (Math.abs(diff) <= 2) {
                window.setLocation(targetX, window.getY());
                slideTimer.stop();
                if (!visible) {
                    window.setVisible(false);
                }
            } else {
                int step = diff / 4;
                if (step == 0) step = diff > 0 ? 1 : -1;
                window.setLocation(currentX + step, window.getY());
            }
        });
        slideTimer.start();
    }

    private void refreshSlots() {
        SwingUtilities.invokeLater(() -> {
            if (slotPanels == null) return;
            for (int i = 0; i < slotPanels.length; i++) {
                slotPanels[i].updateContent();
            }
        });
    }

    // ==================== SlotPanel inner class ====================

    private class SlotPanel extends JPanel {
        private final int slotIndex;
        private JLabel indexLabel;
        private JLabel contentLabel;
        private JLabel typeLabel;
        private JPanel thumbnailPanel;
        private boolean hovered = false;

        SlotPanel(int slotIndex) {
            this.slotIndex = slotIndex;
            setOpaque(false);
            setMaximumSize(new Dimension(PANEL_WIDTH - 20, SLOT_HEIGHT));
            setPreferredSize(new Dimension(PANEL_WIDTH - 20, SLOT_HEIGHT));
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(8, 10, 8, 10));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // Left: Slot number badge
            indexLabel = new JLabel(String.valueOf(slotIndex + 1));
            indexLabel.setFont(new Font("SF Pro Display", Font.BOLD, 16));
            indexLabel.setForeground(ACCENT_COLOR);
            indexLabel.setPreferredSize(new Dimension(24, SLOT_HEIGHT - 16));
            indexLabel.setHorizontalAlignment(SwingConstants.CENTER);
            add(indexLabel, BorderLayout.WEST);

            // Center: Content preview
            JPanel centerPanel = new JPanel();
            centerPanel.setOpaque(false);
            centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

            contentLabel = new JLabel("(empty)");
            contentLabel.setFont(new Font("SF Pro Text", Font.PLAIN, 12));
            contentLabel.setForeground(TEXT_DIM_COLOR);
            contentLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            centerPanel.add(contentLabel);

            centerPanel.add(Box.createVerticalStrut(2));

            typeLabel = new JLabel("Drag content here");
            typeLabel.setFont(new Font("SF Pro Text", Font.PLAIN, 10));
            typeLabel.setForeground(new Color(100, 100, 100));
            typeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            centerPanel.add(typeLabel);

            add(centerPanel, BorderLayout.CENTER);

            // Right: Thumbnail for images
            thumbnailPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    ClipboardSlotManager.SlotContent slot = slotManager.getSlot(slotIndex);
                    if (slot != null && slot.getType() == ClipboardSlotManager.SlotContent.ContentType.IMAGE) {
                        BufferedImage img = slot.getImage();
                        if (img != null) {
                            int w = getWidth();
                            int h = getHeight();
                            double scale = Math.min((double) w / img.getWidth(), (double) h / img.getHeight());
                            int sw = (int) (img.getWidth() * scale);
                            int sh = (int) (img.getHeight() * scale);
                            int sx = (w - sw) / 2;
                            int sy = (h - sh) / 2;
                            Graphics2D g2 = (Graphics2D) g;
                            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            g2.drawImage(img, sx, sy, sw, sh, null);
                        }
                    }
                }
            };
            thumbnailPanel.setOpaque(false);
            thumbnailPanel.setPreferredSize(new Dimension(48, SLOT_HEIGHT - 16));
            add(thumbnailPanel, BorderLayout.EAST);

            // Mouse hover effects
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        ClipboardSlotManager.SlotContent slot = slotManager.getSlot(slotIndex);
                        if (slot != null && slot.getType() != ClipboardSlotManager.SlotContent.ContentType.EMPTY) {
                            hidePanel();
                            Timer t = new Timer(200, ev -> slotManager.pasteFromSlot(slotIndex));
                            t.setRepeats(false);
                            t.start();
                        }
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        slotManager.clearSlot(slotIndex);
                    }
                }
            });

            setupDragAndDrop();
        }

        private void setupDragAndDrop() {
            new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetListener() {
                @Override
                public void dragEnter(DropTargetDragEvent dtde) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                    setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(ACCENT_COLOR, 2),
                            new EmptyBorder(6, 8, 6, 8)));
                    repaint();
                }

                @Override
                public void dragOver(DropTargetDragEvent dtde) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                }

                @Override
                public void dropActionChanged(DropTargetDragEvent dtde) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                }

                @Override
                public void dragExit(DropTargetEvent dte) {
                    setBorder(new EmptyBorder(8, 10, 8, 10));
                    repaint();
                }

                @Override
                public void drop(DropTargetDropEvent dtde) {
                    setBorder(new EmptyBorder(8, 10, 8, 10));
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        Transferable transfer = dtde.getTransferable();

                        if (transfer.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            @SuppressWarnings("unchecked")
                            List<File> files = (List<File>) transfer.getTransferData(DataFlavor.javaFileListFlavor);
                            slotManager.storeFilesInSlot(slotIndex, files);
                            LOG.info("Dropped " + files.size() + " file(s) into slot " + (slotIndex + 1));
                        } else if (transfer.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                            Image img = (Image) transfer.getTransferData(DataFlavor.imageFlavor);
                            BufferedImage buffered = toBufferedImage(img);
                            slotManager.storeImageInSlot(slotIndex, buffered);
                            LOG.info("Dropped image into slot " + (slotIndex + 1));
                        } else if (transfer.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                            String text = (String) transfer.getTransferData(DataFlavor.stringFlavor);
                            slotManager.storeTextInSlot(slotIndex, text);
                            LOG.info("Dropped text into slot " + (slotIndex + 1));
                        } else {
                            DataFlavor uriListFlavor = new DataFlavor("text/uri-list;class=java.lang.String");
                            if (transfer.isDataFlavorSupported(uriListFlavor)) {
                                String uriList = (String) transfer.getTransferData(uriListFlavor);
                                java.util.List<File> files = new java.util.ArrayList<>();
                                for (String uri : uriList.split("\\r?\\n")) {
                                    uri = uri.trim();
                                    if (!uri.isEmpty() && !uri.startsWith("#")) {
                                        try {
                                            files.add(new File(new java.net.URI(uri)));
                                        } catch (Exception ex) {
                                            LOG.fine("Invalid URI in drop: " + uri);
                                        }
                                    }
                                }
                                if (!files.isEmpty()) {
                                    slotManager.storeFilesInSlot(slotIndex, files);
                                }
                            }
                        }

                        dtde.dropComplete(true);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Drop failed on slot " + (slotIndex + 1), e);
                        dtde.dropComplete(false);
                    }
                    repaint();
                }
            }, true);
        }

        void updateContent() {
            ClipboardSlotManager.SlotContent slot = slotManager.getSlot(slotIndex);
            if (slot == null) return;

            switch (slot.getType()) {
                case TEXT:
                    contentLabel.setText(slot.getPreview());
                    contentLabel.setForeground(TEXT_COLOR);
                    typeLabel.setText("⌨ Text • ⌘⇧" + (slotIndex + 1) + " copy • ⌘⇧⌥" + (slotIndex + 1) + " paste");
                    typeLabel.setForeground(TEXT_DIM_COLOR);
                    break;
                case IMAGE:
                    contentLabel.setText(slot.getPreview());
                    contentLabel.setForeground(TEXT_COLOR);
                    typeLabel.setText("🖼 Image • ⌘⇧" + (slotIndex + 1) + " copy • ⌘⇧⌥" + (slotIndex + 1) + " paste");
                    typeLabel.setForeground(TEXT_DIM_COLOR);
                    break;
                case FILES:
                    contentLabel.setText(slot.getPreview());
                    contentLabel.setForeground(TEXT_COLOR);
                    typeLabel.setText("📁 File(s) • ⌘⇧" + (slotIndex + 1) + " copy • ⌘⇧⌥" + (slotIndex + 1) + " paste");
                    typeLabel.setForeground(TEXT_DIM_COLOR);
                    break;
                default:
                    contentLabel.setText("(empty)");
                    contentLabel.setForeground(TEXT_DIM_COLOR);
                    typeLabel.setText("Drag content here or use ⌘⇧" + (slotIndex + 1));
                    typeLabel.setForeground(new Color(100, 100, 100));
                    break;
            }

            thumbnailPanel.repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            ClipboardSlotManager.SlotContent slot = slotManager.getSlot(slotIndex);
            boolean filled = slot != null && slot.getType() != ClipboardSlotManager.SlotContent.ContentType.EMPTY;

            Color bg;
            if (hovered) {
                bg = SLOT_HOVER_COLOR;
            } else if (filled) {
                bg = SLOT_FILLED_COLOR;
            } else {
                bg = SLOT_COLOR;
            }

            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

            g2.dispose();
            super.paintComponent(g);
        }

        private BufferedImage toBufferedImage(Image img) {
            if (img instanceof BufferedImage) return (BufferedImage) img;
            BufferedImage buffered = new BufferedImage(
                    img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = buffered.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            return buffered;
        }
    }
}
