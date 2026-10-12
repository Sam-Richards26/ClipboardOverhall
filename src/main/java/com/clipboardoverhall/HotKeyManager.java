package com.clipboardoverhall;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javax.swing.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages global hotkeys using JNativeHook.
 *
 * Hotkey scheme:
 *   Cmd+Shift+1..9  →  Capture current clipboard to slot 1..9
 *   Cmd+Shift+Alt+1..9  →  Paste from slot 1..9
 *   Cmd+Shift+`  →  Toggle overlay panel
 */
public class HotKeyManager implements NativeKeyListener {
    private static final Logger LOG = Logger.getLogger(HotKeyManager.class.getName());

    private final ClipboardSlotManager slotManager;
    private final OverlayPanel overlayPanel;
    private volatile boolean registered = false;

    // Modifier masks for JNativeHook
    private static final int META_MASK = NativeKeyEvent.META_L_MASK | NativeKeyEvent.META_R_MASK;
    private static final int SHIFT_MASK = NativeKeyEvent.SHIFT_L_MASK | NativeKeyEvent.SHIFT_R_MASK;
    private static final int ALT_MASK = NativeKeyEvent.ALT_L_MASK | NativeKeyEvent.ALT_R_MASK;

    public HotKeyManager(ClipboardSlotManager slotManager, OverlayPanel overlayPanel) {
        this.slotManager = slotManager;
        this.overlayPanel = overlayPanel;
    }

    /**
     * Register global hotkeys via JNativeHook.
     * This requires macOS Accessibility permissions.
     */
    public void register() {
        if (registered) return;

        // Suppress JNativeHook's verbose logging
        Logger jnhLogger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        jnhLogger.setLevel(Level.WARNING);
        jnhLogger.setUseParentHandlers(false);

        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            registered = true;
            LOG.info("Global hotkeys registered successfully.");
        } catch (NativeHookException e) {
            LOG.log(Level.SEVERE, "Failed to register native hook. " +
                    "Please grant Accessibility permissions in System Settings > Privacy & Security > Accessibility.", e);
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(null,
                            "ClipboardOverhall needs Accessibility permissions to register global hotkeys.\n\n" +
                            "Please go to:\n" +
                            "System Settings → Privacy & Security → Accessibility\n\n" +
                            "and add this application to the list.",
                            "Accessibility Permission Required",
                            JOptionPane.WARNING_MESSAGE));
        }
    }

    /**
     * Unregister global hotkeys.
     */
    public void unregister() {
        if (!registered) return;
        try {
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.unregisterNativeHook();
            registered = false;
            LOG.info("Global hotkeys unregistered.");
        } catch (NativeHookException e) {
            LOG.log(Level.WARNING, "Error unregistering native hook", e);
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        int modifiers = e.getModifiers();
        int keyCode = e.getKeyCode();

        boolean hasMeta = (modifiers & META_MASK) != 0;
        boolean hasShift = (modifiers & SHIFT_MASK) != 0;
        boolean hasAlt = (modifiers & ALT_MASK) != 0;

        // Require at least Cmd+Shift
        if (!hasMeta || !hasShift) return;

        // Map key code to slot index (0-8 for keys 1-9)
        int slotIndex = keyCodeToSlotIndex(keyCode);

        if (slotIndex >= 0) {
            if (hasAlt) {
                // Cmd+Shift+Alt+N → Paste from slot N
                LOG.info("Hotkey: Paste from slot " + (slotIndex + 1));
                slotManager.pasteFromSlot(slotIndex);
            } else {
                // Cmd+Shift+N → Capture to slot N
                LOG.info("Hotkey: Capture to slot " + (slotIndex + 1));
                slotManager.captureToSlot(slotIndex);
            }
        }

        // Cmd+Shift+` (backtick) → Toggle overlay panel
        if (keyCode == NativeKeyEvent.VC_BACKQUOTE && !hasAlt) {
            LOG.info("Hotkey: Toggle overlay panel");
            SwingUtilities.invokeLater(() -> overlayPanel.togglePanel());
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        // Not used
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // Not used
    }

    /**
     * Map NativeKeyEvent key codes for number keys 1-9 to slot indices 0-8.
     */
    private int keyCodeToSlotIndex(int keyCode) {
        switch (keyCode) {
            case NativeKeyEvent.VC_1: return 0;
            case NativeKeyEvent.VC_2: return 1;
            case NativeKeyEvent.VC_3: return 2;
            case NativeKeyEvent.VC_4: return 3;
            case NativeKeyEvent.VC_5: return 4;
            case NativeKeyEvent.VC_6: return 5;
            case NativeKeyEvent.VC_7: return 6;
            case NativeKeyEvent.VC_8: return 7;
            case NativeKeyEvent.VC_9: return 8;
            default: return -1;
        }
    }
}
