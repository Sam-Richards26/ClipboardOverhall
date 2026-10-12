package com.clipboardoverhall;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors the system clipboard for changes on a background thread.
 * Maintains a clipboard history and can automatically store new clipboard
 * content into the next available slot.
 */
public class ClipboardMonitor {
    private static final Logger LOG = Logger.getLogger(ClipboardMonitor.class.getName());
    private static final long POLL_INTERVAL_MS = 500;
    private static final int MAX_HISTORY = 50;

    private final ClipboardSlotManager slotManager;
    private final List<ClipboardSlotManager.SlotContent> history = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean running = false;
    private Thread monitorThread;
    private String lastTextHash = "";
    private int lastImageHash = 0;

    public ClipboardMonitor(ClipboardSlotManager slotManager) {
        this.slotManager = slotManager;
    }

    /**
     * Start monitoring the system clipboard in a background thread.
     */
    public void start() {
        if (running) return;
        running = true;
        monitorThread = new Thread(this::monitorLoop, "ClipboardMonitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
        LOG.info("Clipboard monitor started.");
    }

    /**
     * Stop the clipboard monitor.
     */
    public void stop() {
        running = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        LOG.info("Clipboard monitor stopped.");
    }

    /**
     * Get the clipboard history.
     */
    public List<ClipboardSlotManager.SlotContent> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    private void monitorLoop() {
        while (running) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
                checkClipboard();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.log(Level.FINE, "Clipboard check error (transient)", e);
            }
        }
    }

    private void checkClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);
            if (contents == null) return;

            if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
                if (text != null) {
                    String hash = Integer.toHexString(text.hashCode());
                    if (!hash.equals(lastTextHash)) {
                        lastTextHash = hash;
                        lastImageHash = 0;
                        addToHistory(text);
                    }
                }
            } else if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) contents.getTransferData(DataFlavor.imageFlavor);
                if (img != null) {
                    int hash = System.identityHashCode(img);
                    if (hash != lastImageHash) {
                        lastImageHash = hash;
                        lastTextHash = "";
                        // We track image changes in history but don't auto-store
                        // to avoid filling slots with screenshots, etc.
                    }
                }
            }
        } catch (IllegalStateException e) {
            // Clipboard is being used by another process, skip
        } catch (Exception e) {
            LOG.log(Level.FINE, "Clipboard read error", e);
        }
    }

    private void addToHistory(String text) {
        ClipboardSlotManager.SlotContent entry = new ClipboardSlotManager.SlotContent();
        entry.setText(text);

        history.add(0, entry);
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }

        LOG.fine("Clipboard history updated. Size: " + history.size());
    }
}
