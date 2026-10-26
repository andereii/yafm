package com.anderei.yafm.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anderei.yafm.R
import com.anderei.yafm.data.Bookmark
import com.anderei.yafm.data.RecentFile
import com.anderei.yafm.data.StorageUsage
import com.anderei.yafm.data.StorageVolumeInfo
import com.anderei.yafm.data.fileKind
import com.anderei.yafm.ui.explorer.FileExplorerViewModel
import com.anderei.yafm.ui.explorer.FileIconBadge
import com.anderei.yafm.data.FileItem
import com.anderei.yafm.util.formatSize
import com.anderei.yafm.util.openFile
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun HomeRoute(
    explorerViewModel: FileExplorerViewModel,
    homeViewModel: HomeViewModel,
    onOpenExplorer: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFeature: (HomeFeature) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val volumes by homeViewModel.volumes.collectAsStateWithLifecycle()
    val usage by homeViewModel.usage.collectAsStateWithLifecycle()
    val recents by homeViewModel.recents.collectAsStateWithLifecycle()
    val bookmarks by homeViewModel.bookmarks.collectAsStateWithLifecycle()
    val trash by homeViewModel.trash.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        homeViewModel.refresh()
        homeViewModel.cleanupTrash()
    }

    val internalLabel = stringResource(R.string.internal_storage)
    val removableLabel = stringResource(R.string.removable_storage)
    val storageLabels = remember(volumes, internalLabel, removableLabel) {
        volumes.associate { volume ->
            volume.id to if (volume.isPrimary) {
                internalLabel
            } else {
                volume.description.ifBlank { removableLabel }
            }
        }
    }

    val internalRoot = remember(volumes) {
        volumes.firstOrNull { it.isPrimary }?.root
            ?: homeViewModel.internalRoot()
    }

    suspend fun openFileAt(path: String, name: String, isDirectory: Boolean, size: Long) {
        explorerViewModel.recordRecentPath(path, name, isDirectory, size)
        val result = explorerViewModel.prepareForExternalUse(listOf(File(path)))
        result.onSuccess { prepared ->
            prepared.firstOrNull()?.let { file ->
                openFile(context, file).onFailure {
                    snackbarHostState.showSnackbar(context.getString(R.string.open_file_failed))
                }
            }
        }.onFailure {
            snackbarHostState.showSnackbar(context.getString(R.string.open_file_failed))
        }
    }

    HomeScreen(
        volumes = volumes,
        usage = usage,
        recents = recents,
        bookmarks = bookmarks,
        trashCount = trash.size,
        snackbarHostState = snackbarHostState,
        storageLabels = storageLabels,
        onOpenStorage = { volume ->
            explorerViewModel.openStorage(
                volume.root,
                storageLabels[volume.id] ?: volume.root.name,
            )
            onOpenExplorer()
        },
        onOpenCategory = { folder ->
            val target = File(internalRoot, folder).takeIf { it.isDirectory } ?: internalRoot
            explorerViewModel.openStorageAt(internalRoot, internalLabel, target)
            onOpenExplorer()
        },
        onOpenRecent = { recent ->
            scope.launch {
                openFileAt(recent.path, recent.name, isDirectory = false, size = recent.size)
            }
        },
        onOpenBookmark = { bookmark ->
            if (bookmark.isDirectory) {
                val target = File(bookmark.path)
                val volume = homeViewModel.volumeFor(target)
                val opened = if (volume != null) {
                    explorerViewModel.openStorageAt(
                        volume.root,
                        storageLabels[volume.id] ?: volume.root.name,
                        target,
                    )
                    true
                } else {
                    explorerViewModel.openPrivilegedPath(target)
                }
                if (opened) {
                    onOpenExplorer()
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.root_required))
                    }
                }
            } else {
                scope.launch {
                    openFileAt(
                        bookmark.path,
                        bookmark.name,
                        isDirectory = false,
                        size = File(bookmark.path).length(),
                    )
                }
            }
        },
        onRemoveBookmark = { homeViewModel.removeBookmark(it.path) },
        onOpenTrash = onOpenTrash,
        onOpenSettings = onOpenSettings,
        onOpenFeature = onOpenFeature,
        onSearch = {
            explorerViewModel.openInternalSearch()
            onOpenExplorer()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    volumes: List<StorageVolumeInfo>,
    usage: Map<String, StorageUsage>,
    recents: List<RecentFile>,
    bookmarks: List<Bookmark>,
    trashCount: Int,
    snackbarHostState: SnackbarHostState,
    storageLabels: Map<String, String>,
    onOpenStorage: (StorageVolumeInfo) -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenRecent: (RecentFile) -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    onRemoveBookmark: (Bookmark) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFeature: (HomeFeature) -> Unit,
    onSearch: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.home),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.app_full_name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
        ) {
            val compact = maxHeight < 700.dp
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (compact) Modifier.verticalScroll(scrollState) else Modifier),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomeCategories(onOpenCategory = onOpenCategory)
                StorageRow(
                    volumes = volumes,
                    usage = usage,
                    storageLabels = storageLabels,
                    onOpenStorage = onOpenStorage,
                )
                FeatureGrid(
                    trashCount = trashCount,
                    onOpenTrash = onOpenTrash,
                    onOpenFeature = onOpenFeature,
                )
                RecentSection(
                    recents = recents,
                    onOpen = onOpenRecent,
                    modifier = if (compact) {
                        Modifier.fillMaxWidth().height(190.dp)
                    } else {
                        Modifier.fillMaxWidth().weight(1f)
                    },
                )
                BookmarkSection(
                    bookmarks = bookmarks,
                    onOpen = onOpenBookmark,
                    onRemove = onRemoveBookmark,
                    modifier = if (compact) {
                        Modifier.fillMaxWidth().height(190.dp)
                    } else {
                        Modifier.fillMaxWidth().weight(1f)
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun HomeCategories(onOpenCategory: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val categories = listOf(
        HomeCategory(R.string.category_images, Icons.Rounded.Image, "Pictures", colors.tertiaryContainer, colors.onTertiaryContainer),
        HomeCategory(R.string.category_movies, Icons.Rounded.Movie, "Movies", colors.secondaryContainer, colors.onSecondaryContainer),
        HomeCategory(R.string.category_books, Icons.Rounded.MenuBook, "Documents", colors.primaryContainer, colors.onPrimaryContainer),
        HomeCategory(R.string.category_music, Icons.Rounded.MusicNote, "Music", colors.tertiaryContainer, colors.onTertiaryContainer),
        HomeCategory(R.string.category_apps, Icons.Rounded.Apps, "Download", colors.secondaryContainer, colors.onSecondaryContainer),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        categories.forEach { category ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpenCategory(category.folder) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = category.container,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = stringResource(category.labelRes),
                            tint = category.content,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(category.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private data class HomeCategory(
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val folder: String,
    val container: Color,
    val content: Color,
)

@Composable
private fun StorageRow(
    volumes: List<StorageVolumeInfo>,
    usage: Map<String, StorageUsage>,
    storageLabels: Map<String, String>,
    onOpenStorage: (StorageVolumeInfo) -> Unit,
) {
    if (volumes.isEmpty()) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.storage_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        volumes.chunked(2).forEach { rowVolumes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                rowVolumes.forEach { volume ->
                    StorageCard(
                        volume = volume,
                        usage = usage[volume.id],
                        label = storageLabels[volume.id] ?: volume.root.name,
                        onClick = { onOpenStorage(volume) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowVolumes.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StorageCard(
    volume: StorageVolumeInfo,
    usage: StorageUsage?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val percent = ((usage?.fraction ?: 0f) * 100f).toInt()
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { usage?.fraction ?: 0f },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    text = if (usage == null) "—" else "$percent%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                usage?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.storage_used_total,
                            formatSize(it.usedBytes),
                            formatSize(it.totalBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureGrid(
    trashCount: Int,
    onOpenTrash: () -> Unit,
    onOpenFeature: (HomeFeature) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val palettes = listOf(
        colors.tertiaryContainer to colors.onTertiaryContainer,
        colors.primaryContainer to colors.onPrimaryContainer,
        colors.secondaryContainer to colors.onSecondaryContainer,
        colors.tertiaryContainer to colors.onTertiaryContainer,
        colors.surfaceContainerHighest to colors.onSurface,
        colors.primaryContainer to colors.onPrimaryContainer,
        colors.secondaryContainer to colors.onSecondaryContainer,
        colors.tertiaryContainer to colors.onTertiaryContainer,
        colors.surfaceContainerHighest to colors.onSurface,
        colors.surfaceContainerHighest to colors.onSurface,
    )

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            HomeFeature.entries.chunked(5).forEachIndexed { rowIndex, row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { columnIndex, feature ->
                        val palette = palettes[rowIndex * 5 + columnIndex]
                        FeatureTile(
                            feature = feature,
                            badgeCount = if (feature == HomeFeature.RECYCLE_BIN) trashCount else 0,
                            container = palette.first,
                            content = palette.second,
                            onClick = {
                                if (feature == HomeFeature.RECYCLE_BIN) {
                                    onOpenTrash()
                                } else {
                                    onOpenFeature(feature)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(5 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureTile(
    feature: HomeFeature,
    badgeCount: Int,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text(
                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                            color = MaterialTheme.colorScheme.onError,
                        )
                    }
                }
            },
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(16.dp),
                color = container,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = stringResource(feature.titleRes),
                        tint = content,
                        modifier = Modifier.size(23.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(feature.titleRes),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
        )
        if (!feature.available) {
            Text(
                text = stringResource(R.string.soon),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RecentSection(
    recents: List<RecentFile>,
    onOpen: (RecentFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            SectionHeader(
                icon = Icons.Rounded.History,
                title = stringResource(R.string.recent_files),
                count = recents.size,
            )
            Spacer(Modifier.height(6.dp))
            if (recents.isEmpty()) {
                EmptySection(
                    icon = Icons.Rounded.History,
                    text = stringResource(R.string.no_recent_files),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    listItems(recents, key = { it.path }) { recent ->
                        RecentRow(recent = recent, onClick = { onOpen(recent) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(recent: RecentFile, onClick: () -> Unit) {
    val item = remember(recent) {
        FileItem(
            file = File(recent.path),
            name = recent.name,
            isDirectory = false,
            size = recent.size,
            lastModified = recent.timestamp,
            kind = fileKind(recent.name, false),
        )
    }
    val parent = remember(recent.path) {
        File(recent.path).parentFile?.name ?: "/"
    }
    val relative = remember(recent.timestamp) {
        DateUtils.getRelativeTimeSpanString(
            recent.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(18.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        FileIconBadge(item, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = recent.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$parent · $relative",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BookmarkSection(
    bookmarks: List<Bookmark>,
    onOpen: (Bookmark) -> Unit,
    onRemove: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            SectionHeader(
                icon = Icons.Rounded.Star,
                title = stringResource(R.string.bookmarks),
                count = bookmarks.size,
            )
            Spacer(Modifier.height(6.dp))
            if (bookmarks.isEmpty()) {
                EmptySection(
                    icon = Icons.Rounded.StarBorder,
                    text = stringResource(R.string.no_bookmarks),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(bookmarks, key = { it.path }) { bookmark ->
                        BookmarkCard(
                            bookmark = bookmark,
                            onClick = { onOpen(bookmark) },
                            onRemove = { onRemove(bookmark) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val item = remember(bookmark) {
        FileItem(
            file = File(bookmark.path),
            name = bookmark.name,
            isDirectory = bookmark.isDirectory,
            size = 0,
            lastModified = bookmark.addedAt,
            kind = fileKind(bookmark.name, bookmark.isDirectory),
        )
    }
    val parent = remember(bookmark.path) {
        val name = File(bookmark.path).parentFile?.name ?: "/"
        name.takeIf { it != "emulated" }.orEmpty()
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileIconBadge(item, size = 32.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = bookmark.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (parent.isNotEmpty()) {
                    Text(
                        text = parent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = stringResource(R.string.remove_bookmark),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptySection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
