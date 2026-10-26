package com.anderei.yafm.ui.cleaner

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import java.io.File
import java.util.Locale

/**
 * Best-effort storage scan for the Cleaner.
 *
 * Shared storage is scanned directly (the app holds All files access). App-private data is
 * included when readable. The scanner never deletes anything; the UI does that through the
 * active FileBackend.
 */
class CleanerScanner {

    suspend fun scan(
        roots: List<File>,
        downloadRoots: Set<String>,
        skipPaths: Set<String>,
        onProgress: (scanned: Int) -> Unit,
    ): Map<CleanerCategoryType, List<CleanerItem>> {
        val emptyItems = mutableListOf<CleanerItem>()
        val installerItems = mutableListOf<CleanerItem>()
        val adItems = mutableListOf<CleanerItem>()
        val downloadItems = mutableListOf<CleanerItem>()

        val rootPaths = roots.map { it.absolutePath }.toSet()
        val queue = ArrayDeque<File>()
        roots.filter { it.isDirectory }.forEach(queue::add)
        val visited = HashSet<String>()
        var scanned = 0

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val directory = queue.removeFirst()
            val directoryPath = directory.absolutePath

            if (skipPaths.any { skip -> directoryPath == skip || directoryPath.startsWith(skip + File.separator) }) {
                continue
            }
            if (directory.name in SKIP_DIRECTORIES) continue

            val canonical = runCatching { directory.canonicalPath }.getOrDefault(directoryPath)
            if (!visited.add(canonical)) continue

            val children = runCatching { directory.listFiles() }.getOrNull() ?: continue

            val parentIsRoot = directory.parentFile?.absolutePath in rootPaths
            if (children.isEmpty() && directoryPath !in rootPaths && !parentIsRoot &&
                !directory.name.startsWith(".")
            ) {
                emptyItems += directory.toCleanerItem(CleanerCategoryType.EMPTY)
            }

            val isDownloadRoot = directoryPath in downloadRoots
            val scanningStorageRoot = directoryPath in rootPaths
            for (child in children) {
                scanned++
                if (scanned % PROGRESS_EVERY == 0) {
                    onProgress(scanned)
                    yield()
                }

                if (child.isDirectory) {
                    if (!child.name.startsWith(".") && child.name !in SKIP_DIRECTORIES) {
                        queue.add(child)
                    }
                    continue
                }

                if (child.name.startsWith(".")) continue

                // Root-level files (and empty root folders) are intentionally never surfaced
                // by the Cleaner. We still descend into root-level folders to scan their
                // contents (Download, DCIM, app data, ...).
                if (scanningStorageRoot) continue

                val path = child.absolutePath.lowercase(Locale.ROOT)
                val installers = !path.contains("/android/data/") &&
                    !path.contains("/android/obb/")
                val extension = child.extension.lowercase(Locale.ROOT)
                val underAndroidData = path.contains("/android/data/")

                if (child.length() == 0L) {
                    emptyItems += child.toCleanerItem(CleanerCategoryType.EMPTY)
                }
                if (installers && extension in INSTALLER_EXTENSIONS) {
                    installerItems += child.toCleanerItem(CleanerCategoryType.INSTALLERS)
                }
                if (isDownloadRoot) {
                    downloadItems += child.toCleanerItem(CleanerCategoryType.DOWNLOADS)
                }
                if (underAndroidData && isAdFile(child)) {
                    adItems += child.toCleanerItem(CleanerCategoryType.ADS)
                }
            }
        }
        onProgress(scanned)

        return mapOf(
            CleanerCategoryType.EMPTY to emptyItems.sortedBy { it.name.lowercase(Locale.ROOT) },
            CleanerCategoryType.INSTALLERS to installerItems.sortedBy { it.name.lowercase(Locale.ROOT) },
            CleanerCategoryType.ADS to adItems.sortedBy { it.name.lowercase(Locale.ROOT) },
            CleanerCategoryType.DOWNLOADS to downloadItems.sortedBy { it.name.lowercase(Locale.ROOT) },
        )
    }

    private fun isAdFile(file: File): Boolean {
        val name = file.name.lowercase(Locale.ROOT)
        if (name.startsWith("ad_") || name.startsWith("ad-") ||
            name.startsWith("ads_") || name.startsWith("ads-")
        ) {
            return true
        }
        if (AD_NAME_TOKENS.any { token -> name.contains(token) }) return true

        val parent = file.parentFile?.name?.lowercase(Locale.ROOT).orEmpty()
        return parent in AD_PARENT_NAMES
    }

    private fun File.toCleanerItem(type: CleanerCategoryType): CleanerItem {
        val rawPath = absolutePath
        // Raw public-volume mounts are not user-facing. Show the normal /storage mount instead.
        val displayPath = if (rawPath.startsWith("/mnt/media_rw/")) {
            "/storage/" + rawPath.removePrefix("/mnt/media_rw/")
        } else {
            rawPath
        }
        return CleanerItem(
            path = rawPath,
            displayPath = displayPath,
            name = name,
            isDirectory = isDirectory,
            size = if (isDirectory) 0L else length(),
            type = type,
        )
    }

    private companion object {
        const val PROGRESS_EVERY = 200
        val INSTALLER_EXTENSIONS = setOf("apk", "xapk", "apkm", "apks")
        val SKIP_DIRECTORIES = setOf(
            ".thumbnails",
            ".trashed",
            ".trash",
            "lost+found",
            "LOST.DIR",
            "System Volume Information",
            "\$RECYCLE.BIN",
        )
        val AD_NAME_TOKENS = setOf(
            "admob",
            "mopub",
            "applovin",
            "unityads",
            "vungle",
            "ironsource",
            "chartboost",
            "startapp",
            "inmobi",
            "adcolony",
            "tapjoy",
            "appodeal",
            "interstitial",
            "rewarded",
            "adcache",
            "ad_banner",
            "ad_video",
        )
        val AD_PARENT_NAMES = setOf(
            "ad",
            "ads",
            "adcache",
            "adverts",
            "advertisements",
        )
    }
}
