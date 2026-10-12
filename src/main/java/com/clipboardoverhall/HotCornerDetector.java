package com.clipboardoverhall;

import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors the mouse position on a lightweight background thread.
 * When the cursor stays in the extreme top-left corner (x <= 5, y <= 5)
 * for more than 300ms, the overlay panel is shown.
 */
public class HotCornerDetector {
    private static final Logger LOG = Logger.getLogger(HotCornerDetector.class.getName());

    private static final int CORNER_THRESHOLD = 5;
    private static final long DWELL_TIME_MS = 300;
    private static final long POLL_INTERVAL_MS = 50;
    private static final long COOLDOWN_MS = 2000; // Prevent retriggering immediately

    private final OverlayPanel overlayPanel;
    private volatile boolean running = false;
    private Thread detectorThread;

    public HotCornerDetector(OverlayPanel overlayPanel) {
        this.overlayPanel = overlayPanel;
    }

    /**
     * Start the hot corner detector on a background thread.
     */
    public void start() {
        if (running) return;
        running = true;
        detectorThread = new Thread(this::detectLoop, "HotCornerDetector");
        detectorThread.setDaemon(true);
        detectorThread.start();
        LOG.info("Hot corner detector started.");
    }

    /**
     * Stop the hot corner detector.
     */
    public void stop() {
        running = false;
        if (detectorThread != null) {
            detectorThread.interrupt();
        }
        LOG.info("Hot corner detector stopped.");
    }

    private void detectLoop() {
        long cornerEntryTime = 0;
        boolean triggered = false;
        long lastTriggerTime = 0;

        while (running) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);

                Point mousePos = MouseInfo.getPointerInfo().getLocation();
                boolean inCorner = mousePos.x <= CORNER_THRESHOLD && mousePos.y <= CORNER_THRESHOLD;

                if (inCorner) {
                    if (cornerEntryTime == 0) {
                        cornerEntryTime = System.currentTimeMillis();
                    }

                    long dwellTime = System.currentTimeMillis() - cornerEntryTime;

                    if (dwellTime >= DWELL_TIME_MS && !triggered) {
                        long timeSinceLastTrigger = System.currentTimeMillis() - lastTriggerTime;
                        if (timeSinceLastTrigger >= COOLDOWN_MS) {
                            triggered = true;
                            lastTriggerTime = System.currentTimeMillis();
                            LOG.info("Hot corner triggered - showing overlay panel.");
                            javax.swing.SwingUtilities.invokeLater(() -> overlayPanel.showPanel());
                        }
                    }
                } else {
                    cornerEntryTime = 0;
                    triggered = false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.log(Level.FINE, "Hot corner detection error", e);
            }
        }
    }
}
