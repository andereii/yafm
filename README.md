# YAFM — Yet Another File Manager

<p><img src="docs/icon.png" width="96" alt="YAFM app icon" /></p>

A small, functional Android file manager built with **Jetpack Compose** and **Material 3 Expressive**,
with **Shizuku** and **root (Magisk / KernelSU / APatch)** support for protected locations.

<p>
  <img src="docs/browse.png" width="240" alt="Browsing internal storage" />
  <img src="docs/grid.png" width="240" alt="Grid layout" />
</p>

## Features

- Browse internal storage with list and adaptive grid layouts
- App-owned folders show the owning app's launcher icon in the corner
  (package-name folders and UID-owned folders, e.g. Android/data, Android/obb, /data/data)
- Tap-to-open files with the system chooser (FileProvider, no exposed `file://` URIs)
- Breadcrumb navigation, back gesture support, and per-folder item counts
- Multi-select with long press: copy, move, share, rename, delete
- Folder creation, recursive copy/move, name-collision handling (`photo (1).jpg`)
- Sort by name, date, size, or type with ascending/descending direction
- In-folder search, show/hide dotfiles, manual refresh
- Details dialog with type, MIME, location, size, and modified date
- Quick locations sheet: Android/data, Android/obb, Downloads, DCIM, app files, plus
  filesystem root, `/data`, `/system` and `/data/local/tmp` when privileged
- **Three access modes**
  - **Standard** — Android's All files access
  - **Shizuku** — ADB-shell access without root (Android/data, Android/obb); if Shizuku is
    started as root, the whole filesystem is available
  - **Root** — Magisk / KernelSU / APatch `su`, full filesystem including `/`, `/data`, `/system`
- Material 3 Expressive UI: `MaterialExpressiveTheme` with expressive motion, flexible large
  app bar, FAB menu, `ButtonGroup` controls, and `LoadingIndicator`

## Privileged access

Android hides `Android/data` and `Android/obb` from ordinary apps. Open the shield icon in the
top bar (or **Access & privileges** in the menu) and pick a mode.

### Shizuku (no root required)

1. Install [Shizuku](https://shizuku.rikka.app/).
2. Start it — via wireless debugging or via root, depending on your setup.
3. In YAFM, choose **Shizuku** and grant the permission dialog.
4. Use **Locations → Android data / Android obb**, or navigate through `Android/`.

Shizuku runs shell commands with ADB-level access. If you start the Shizuku server *as root*,
YAFM detects `uid 0` and enables root-wide navigation automatically.

### Root (Magisk / KernelSU / APatch)

1. Choose **Root** and approve the root prompt (or grant YAFM in your root manager first).
2. Use **Locations → Root / Data / System / Temp**, or browse up to `/`.

YAFM runs `su -c` for listing and file operations, and stages root-only files into its own
external files directory (with `chmod a+rX`) when they need to be opened or shared.

### Safety

- The mode choice is session-scoped; grants from Shizuku and your root manager persist.
- Destructive operations (delete / rename / move) are blocked on `/`, `/system`, `/data`,
  `/storage/emulated/0`, `/data/adb` and other top-level system folders.
- Root mode is still powerful: always double-check selections before deleting.
- `MANAGE_EXTERNAL_STORAGE` (declared for Standard mode) requires a Play Store policy
  exception, so this app is intended for sideloading / personal use.

## Build and install

```bash
export JAVA_HOME=/path/to/Android/Studio/jbr   # JDK 17+
./gradlew :app:assembleDebug

adb install -r app/build/outputs/apk/debug/app-debug.apk
# Optional shortcut for local testing, instead of using the settings screen:
adb shell appops set com.anderei.yafm MANAGE_EXTERNAL_STORAGE allow
```

Run the unit tests with:

```bash
./gradlew :app:testDebugUnitTest
```

## Tech stack

| Item | Version |
| --- | --- |
| Android Gradle Plugin | 9.4.1 (built-in Kotlin) |
| Gradle | 9.6.0 |
| Kotlin | 2.2.10 |
| Compose BOM | 2026.09.00 |
| Material 3 | 1.5.0-beta02 (Expressive APIs) |
| Shizuku API | 13.1.5 |
| min / target SDK | 31 / 37 |

AndroidX and the Shizuku client library are the only runtime dependencies.

## Project layout

```
app/src/main/java/com/anderei/yafm/
├── MainActivity.kt                  Permission gate + entry point
├── data/
│   ├── FileItem.kt                  File model and type detection
│   ├── FileBackend.kt               Backend interface + access modes
│   ├── FileRepository.kt            Standard java.io.File backend
│   ├── ShellBackend.kt              Shizuku/root shell backend, listing, staging
│   ├── PrivilegeManager.kt          Shizuku permission/state and root activation
│   └── ...
├── ui/
│   ├── explorer/
│   │   ├── ExplorerState.kt         UI state, sort/layout/clipboard models
│   │   ├── ExplorerUtils.kt         Breadcrumbs and sorting helpers
│   │   ├── FileExplorerViewModel.kt State holder and file operations
│   │   ├── FileExplorerScreen.kt    Scaffold, app bars, list/grid, FAB, sheets
│   │   ├── FileExplorerComponents.kt Rows, cards, dialogs, breadcrumbs
│   │   └── AccessSheet.kt           Standard / Shizuku / Root controls
│   ├── permission/PermissionScreen.kt
│   └── theme/Theme.kt               Dynamic color + expressive theme
└── util/
    ├── FileUtils.kt                 MIME, formatting, open/share intents
    └── ShellQuoting.kt              POSIX shell argument escaping
```

## Implementation notes

- File operations run on `Dispatchers.IO`; results are reported with snackbars.
- Standard mode uses `java.io.File`; privileged modes use a NUL-safe `stat` listing with a
  fast batched path and a fallback for filenames containing newlines.
- Shell arguments are single-quoted with `'\''` escaping, so spaces and apostrophes are safe.
- Moving across filesystems falls back to copy + delete when `mv` fails.
- Copying a folder into itself is rejected.
- Opening a file the app cannot read copies it to `Android/data/<package>/files/staging` and
  serves it through FileProvider.
