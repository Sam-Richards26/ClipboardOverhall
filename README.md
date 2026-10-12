# ClipboardOverhall 📋

A native macOS background utility application written in Java featuring an **advanced multi-slot clipboard** and a **floating drag-and-drop shelf**.

---

## 🌟 Key Features

1. **Persistent macOS Background Utility:**
   - Runs silently in the macOS Menu Bar (`SystemTray`).
   - Suppresses dock icons (`apple.awt.UIElement=true`) for a true native background experience.

2. **Multi-Slot Global Hotkeys:**
   - Powered by `JNativeHook`.
   - **Capture Selection to Slot:** <kbd>Cmd</kbd> + <kbd>Shift</kbd> + <kbd>1..9</kbd>
   - **Paste from Slot:** <kbd>Cmd</kbd> + <kbd>Shift</kbd> + <kbd>Alt</kbd> + <kbd>1..9</kbd>
   - **Toggle Shelf Panel:** <kbd>Cmd</kbd> + <kbd>Shift</kbd> + <kbd>`</kbd> (backtick)

3. **Top-Left Corner Mouse Trigger & Floating Shelf:**
   - Lightweight background thread monitors mouse coordinates.
   - Dwelling in the extreme top-left corner (`x <= 5, y <= 5`) for ≥300ms smoothly slides open a floating, semi-transparent overlay panel.
   - Cooldown timer prevents accidental repeated triggers.

4. **Rich Drag-and-Drop Shelf:**
   - Drag **Text**, **Images**, or **Files** directly from Finder, browsers, or other apps onto any slot panel.
   - Live visual feedback and thumbnails for captured images and file lists.
   - Left-click on any filled slot to immediately paste its content into the active application.
   - Right-click on a slot to clear it.

5. **macOS Accessibility & Permissions:**
   - Prompts gracefully with descriptive dialogs if macOS Accessibility permissions are needed for global hotkey capturing.

---

## 🏗️ Project Architecture

```
ClipboardOverhall/
├── pom.xml                                      # Maven configuration (Java 17, FlatLaf, JNativeHook)
├── build.gradle                                 # Gradle configuration
├── settings.gradle
├── .gitignore
├── README.md
└── src/
    └── main/
        └── java/
            └── com/
                └── clipboardoverhall/
                    ├── Main.java                # Application lifecycle, tray setup, UIElement
                    ├── ClipboardSlotManager.java# Manages 9 slots, capture/paste, DnD data
                    ├── ClipboardMonitor.java    # Background system clipboard poller & history
                    ├── HotKeyManager.java       # JNativeHook global keyboard shortcut listener
                    ├── HotCornerDetector.java   # Threaded mouse corner detection & dwell timer
                    └── OverlayPanel.java        # Semi-transparent floating shelf with drag & drop
```

---

## ⚙️ Prerequisites

- **macOS** (10.15 Catalina or later recommended)
- **Java Development Kit (JDK) 17+** (JDK 21 LTS verified)
- **Maven** (`mvn`) or **Gradle** (`gradle`)

---

## 🚀 Building & Running

### Option 1: Using Maven
```bash
# Package into executable uber-jar
mvn clean package

# Run the utility
java -jar target/clipboard-overhall-1.0-SNAPSHOT.jar
```

### Option 2: Using Gradle
```bash
# Run directly
gradle run

# Or build fat jar
gradle shadowJar
java -jar build/libs/clipboard-overhall-1.0-SNAPSHOT-all.jar
```

---

## 🔒 macOS Accessibility Permission Setup

Because `JNativeHook` listens to global keystrokes across applications, macOS requires explicit permission:

1. When running the application for the first time, macOS will display an **Accessibility Access** prompt.
2. Go to **System Settings** → **Privacy & Security** → **Accessibility**.
3. Toggle the switch for your terminal (e.g., Terminal, iTerm2, VS Code) or Java launcher.
4. Restart the application if prompted.