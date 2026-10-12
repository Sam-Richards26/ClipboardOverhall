package com.clipboardoverhall;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages multiple clipboard slots. Each slot can hold text, an image, or a list of files.
 * Provides methods to capture the current system clipboard into a slot and to paste from a slot.
 */
public class ClipboardSlotManager {
    private static final Logger LOG = Logger.getLogger(ClipboardSlotManager.class.getName());

    /**
     * Represents the content stored in a single clipboard slot.
     */
    public static class SlotContent {
        public enum ContentType { EMPTY, TEXT, IMAGE, FILES }

        private ContentType type = ContentType.EMPTY;
        private String text;
        private BufferedImage image;
        private List<File> files;

        public ContentType getType() { return type; }
        public String getText() { return text; }
        public BufferedImage getImage() { return image; }
        public List<File> getFiles() { return files; }

        public void setText(String text) {
            this.type = ContentType.TEXT;
            this.text = text;
            this.image = null;
            this.files = null;
        }

        public void setImage(BufferedImage image) {
            this.type = ContentType.IMAGE;
            this.text = null;
            this.image = image;
            this.files = null;
        }

        public void setFiles(List<File> files) {
            this.type = ContentType.FILES;
            this.text = null;
            this.image = null;
            this.files = new ArrayList<>(files);
        }

        public void clear() {
            this.type = ContentType.EMPTY;
            this.text = null;
            this.image = null;
            this.files = null;
        }

        public String getPreview() {
            switch (type) {
                case TEXT:
                    String preview = text.replace("\n", " ").trim();
                    return preview.length() > 40 ? preview.substring(0, 37) + "..." : preview;
                case IMAGE:
                    return "[Image " + image.getWidth() + "x" + image.getHeight() + "]";
                case FILES:
                    if (files.size() == 1) return files.get(0).getName();
                    return "[" + files.size() + " files]";
                default:
                    return "(empty)";
            }
        }
    }

    private final SlotContent[] slots;
    private final List<Runnable> changeListeners = new ArrayList<>();

    public ClipboardSlotManager(int slotCount) {
        slots = new SlotContent[slotCount];
        for (int i = 0; i < slotCount; i++) {
            slots[i] = new SlotContent();
        }
    }

    public int getSlotCount() {
        return slots.length;
    }

    public SlotContent getSlot(int index) {
        if (index < 0 || index >= slots.length) return null;
        return slots[index];
    }

    public String getSlotPreview(int index) {
        if (index < 0 || index >= slots.length) return "(invalid)";
        return slots[index].getPreview();
    }

    /**
     * Add a listener that is called whenever any slot content changes.
     */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void notifyChange() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Change listener error", e);
            }
        }
    }

    /**
     * Capture the current system clipboard contents into the specified slot.
     */
    public void captureToSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.length) return;

        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);
            if (contents == null) return;

            if (contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) contents.getTransferData(DataFlavor.javaFileListFlavor);
                slots[slotIndex].setFiles(files);
                LOG.info("Captured " + files.size() + " file(s) to slot " + (slotIndex + 1));
            } else if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) contents.getTransferData(DataFlavor.imageFlavor);
                BufferedImage buffered = toBufferedImage(img);
                slots[slotIndex].setImage(buffered);
                LOG.info("Captured image to slot " + (slotIndex + 1));
            } else if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
                slots[slotIndex].setText(text);
                LOG.info("Captured text to slot " + (slotIndex + 1) + ": " + slots[slotIndex].getPreview());
            }

            notifyChange();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to capture clipboard to slot " + (slotIndex + 1), e);
        }
    }

    /**
     * Paste the contents of the specified slot to the system clipboard,
     * then simulate Cmd+V to paste into the active application.
     */
    public void pasteFromSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.length) return;
        SlotContent slot = slots[slotIndex];
        if (slot.getType() == SlotContent.ContentType.EMPTY) return;

        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

            switch (slot.getType()) {
                case TEXT:
                    clipboard.setContents(new StringSelection(slot.getText()), null);
                    break;
                case IMAGE:
                    clipboard.setContents(new TransferableImage(slot.getImage()), null);
                    break;
                case FILES:
                    clipboard.setContents(new TransferableFileList(slot.getFiles()), null);
                    break;
                default:
                    return;
            }

            // Small delay to let clipboard update propagate
            Thread.sleep(50);

            // Simulate Cmd+V to paste
            Robot robot = new Robot();
            robot.keyPress(java.awt.event.KeyEvent.VK_META);
            robot.keyPress(java.awt.event.KeyEvent.VK_V);
            robot.keyRelease(java.awt.event.KeyEvent.VK_V);
            robot.keyRelease(java.awt.event.KeyEvent.VK_META);

            LOG.info("Pasted from slot " + (slotIndex + 1));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to paste from slot " + (slotIndex + 1), e);
        }
    }

    /**
     * Store text directly into a slot (used by drag and drop).
     */
    public void storeTextInSlot(int slotIndex, String text) {
        if (slotIndex < 0 || slotIndex >= slots.length) return;
        slots[slotIndex].setText(text);
        notifyChange();
    }

    /**
     * Store an image directly into a slot (used by drag and drop).
     */
    public void storeImageInSlot(int slotIndex, BufferedImage image) {
        if (slotIndex < 0 || slotIndex >= slots.length) return;
        slots[slotIndex].setImage(image);
        notifyChange();
    }

    /**
     * Store files directly into a slot (used by drag and drop).
     */
    public void storeFilesInSlot(int slotIndex, List<File> files) {
        if (slotIndex < 0 || slotIndex >= slots.length) return;
        slots[slotIndex].setFiles(files);
        notifyChange();
    }

    /**
     * Find the next empty slot index, or -1 if all full.
     */
    public int findNextEmptySlot() {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].getType() == SlotContent.ContentType.EMPTY) return i;
        }
        return -1;
    }

    /**
     * Clear a specific slot.
     */
    public void clearSlot(int index) {
        if (index >= 0 && index < slots.length) {
            slots[index].clear();
            notifyChange();
        }
    }

    /**
     * Clear all slots.
     */
    public void clearAll() {
        for (SlotContent slot : slots) {
            slot.clear();
        }
        notifyChange();
    }

    private static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) return (BufferedImage) img;
        BufferedImage buffered = new BufferedImage(
                img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buffered.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return buffered;
    }

    // ---- Transferable implementations for image and file list ----

    private static class TransferableImage implements Transferable {
        private final BufferedImage image;

        TransferableImage(BufferedImage image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            if (!isDataFlavorSupported(flavor)) return null;
            return image;
        }
    }

    private static class TransferableFileList implements Transferable {
        private final List<File> files;

        TransferableFileList(List<File> files) {
            this.files = files;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.javaFileListFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.javaFileListFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            if (!isDataFlavorSupported(flavor)) return null;
            return files;
        }
    }
}
