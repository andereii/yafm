package com.anderei.yafm.ui.explorer

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.anderei.yafm.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Small app icon + label shown on folders that belong to an installed app. */
data class AppBadge(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
)

/**
 * Resolves folders to their owning app.
 *
 * Two signals are used:
 *  - the folder name is an installed package (Android/data, Android/obb, /data/data, …)
 *  - the directory is owned by an app UID (fallback for folders with other names)
 */
object AppBadgeCache {

    private const val NO_APP = ""
    private val packageCache = ConcurrentHashMap<String, String>()
    private val badgeCache = ConcurrentHashMap<String, AppBadge>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    fun resolve(context: Context, item: FileItem): AppBadge? {
        if (!item.isDirectory) return null

        val appContext = context.applicationContext
        val key = "${item.name}:${item.ownerUid ?: -1}"
        val packageName = packageCache[key] ?: run {
            val resolved = resolvePackage(appContext, item.name, item.ownerUid)
            packageCache[key] = resolved ?: NO_APP
            resolved
        }
        if (packageName.isNullOrEmpty()) return null

        badgeCache[packageName]?.let { return it }
        if (packageName in failed) return null

        return runCatching {
            val pm = appContext.packageManager
            val info = pm.getPackageInfo(packageName, 0)
            val applicationInfo = info.applicationInfo ?: return null
            val label = pm.getApplicationLabel(applicationInfo).toString()
            val icon = pm.getApplicationIcon(applicationInfo)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
            AppBadge(packageName, label, icon).also { badgeCache[packageName] = it }
        }.onFailure {
            failed += packageName
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun resolvePackage(context: Context, name: String, uid: Int?): String? {
        val pm = context.packageManager

        // Android/data, Android/obb, /data/data and /data/user/<id> are named after packages.
        if (runCatching { pm.getPackageInfo(name, 0) }.isSuccess) {
            return name
        }

        if (uid == null || uid <= 0) return null
        val packages = runCatching { pm.getPackagesForUid(uid) }.getOrNull() ?: return null
        if (packages.isEmpty()) return null

        val candidates = if (packages.size == 1) packages.toList() else packages.filter { it == name }
        // Ownership alone is only useful when it points at a launchable app. This avoids
        // badging shared folders owned by media/system providers.
        return candidates.firstOrNull { hasLauncher(pm, it) }
    }

    private fun hasLauncher(pm: PackageManager, packageName: String): Boolean =
        runCatching { pm.getLaunchIntentForPackage(packageName) != null }.getOrDefault(false)
}

@Composable
fun rememberAppBadge(item: FileItem): AppBadge? {
    if (!item.isDirectory) return null
    val context = LocalContext.current
    return produceState<AppBadge?>(
        initialValue = null,
        item.name,
        item.ownerUid,
    ) {
        value = withContext(Dispatchers.IO) {
            AppBadgeCache.resolve(context, item)
        }
    }.value
}
