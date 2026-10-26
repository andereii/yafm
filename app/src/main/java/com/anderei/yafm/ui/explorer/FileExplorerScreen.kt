package com.anderei.yafm.ui.explorer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anderei.yafm.R
import com.anderei.yafm.YafmApplication
import com.anderei.yafm.data.AccessMode
import com.anderei.yafm.data.FileItem
import com.anderei.yafm.data.archive.ArchiveFormat
import com.anderei.yafm.data.archive.CreateArchiveOptions
import com.anderei.yafm.ui.archive.ArchiveBusyDialog
import com.anderei.yafm.ui.archive.CreateArchiveDialog
import com.anderei.yafm.ui.archive.DirectoryPickerSheet
import com.anderei.yafm.ui.archive.ExtractArchiveDialog
import com.anderei.yafm.ui.archive.archiveMessage
import com.anderei.yafm.util.openFile
import com.anderei.yafm.util.shareFiles
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FileExplorerRoute(
    viewModel: FileExplorerViewModel = viewModel(),
    onExitToHome: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    FileExplorerScreen(
        state = state,
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        onExitToHome = onExitToHome,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FileExplorerScreen(
    state: ExplorerState,
    viewModel: FileExplorerViewModel,
    snackbarHostState: SnackbarHostState,
    onExitToHome: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    var fabExpanded by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showLocationsSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAccessSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteTargets by remember { mutableStateOf<List<FileItem>?>(null) }
    var detailsTarget by remember { mutableStateOf<FileItem?>(null) }
    var pickerArchive by remember { mutableStateOf<FileItem?>(null) }
    var extractTarget by remember { mutableStateOf<FileItem?>(null) }
    var extractDestination by remember { mutableStateOf<File?>(null) }
    var compressTargets by remember { mutableStateOf<List<FileItem>?>(null) }
    var archiveBusy by remember { mutableStateOf<ArchiveWork?>(null) }
    val archives = remember {
        (context.applicationContext as YafmApplication).container.archives
    }

    val visible = remember(state.entries, state.sort, state.ascending, state.showHidden, state.query) {
        state.entries
            .filter { state.showHidden || !it.name.startsWith(".") }
            .filter { state.query.isBlank() || it.name.contains(state.query, ignoreCase = true) }
            .let { sortEntries(it, state.sort, state.ascending) }
    }

    val selectedItems = remember(state.entries, state.selection) {
        state.entries.filter { it.path in state.selection }
    }

    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val bookmarkedPaths = remember(bookmarks) {
        bookmarks.mapTo(mutableSetOf()) { it.path }
    }
    val trashRetentionDays by viewModel.trashRetentionDays.collectAsStateWithLifecycle()
    val currentFolder = remember(state.currentDir, state.currentTitle) {
        FileItem.from(state.currentDir).copy(name = state.currentTitle)
    }
    val currentFolderBookmarked = state.currentDir.absolutePath in bookmarkedPaths

    val showSnackbar: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val openItem: (FileItem) -> Unit = { item ->
        if (item.isDirectory) {
            viewModel.navigateTo(item.file)
        } else {
            scope.launch {
                if (!item.file.canRead()) {
                    showSnackbar("Copying “${item.name}” to a readable location…")
                }
                viewModel.prepareForExternalUse(listOf(item.file))
                    .onSuccess { prepared ->
                        prepared.firstOrNull()?.let { file ->
                            viewModel.recordRecent(item)
                            openFile(context, file).onFailure {
                                showSnackbar("No app can open “${item.name}”")
                            }
                        }
                    }
                    .onFailure {
                        showSnackbar(it.message ?: "Couldn't access “${item.name}”")
                    }
            }
        }
    }

    val actions = FileRowActions(
        onOpen = openItem,
        onRename = { renameTarget = it },
        onCopy = { viewModel.copyToClipboard(listOf(it.file), ClipboardMode.COPY) },
        onMove = { viewModel.copyToClipboard(listOf(it.file), ClipboardMode.MOVE) },
        onShare = { item ->
            scope.launch {
                viewModel.prepareForExternalUse(listOf(item.file))
                    .onSuccess { prepared ->
                        shareFiles(context, prepared).onFailure {
                            showSnackbar("Couldn't share “${item.name}”")
                        }
                    }
                    .onFailure {
                        showSnackbar(it.message ?: "Couldn't share “${item.name}”")
                    }
            }
        },
        onDetails = { detailsTarget = it },
        onDelete = { deleteTargets = listOf(it) },
        onBookmark = { viewModel.toggleBookmark(it) },
        onExtractHere = { item ->
            extractTarget = item
            extractDestination = item.file.parentFile ?: state.currentDir
        },
        onExtractTo = { item -> pickerArchive = item },
        onCompress = { item -> compressTargets = listOf(item) },
    )

    val onItemClick: (FileItem) -> Unit = { item ->
        if (state.isSelectionMode) viewModel.toggleSelection(item.path) else openItem(item)
    }
    val onItemLongClick: (FileItem) -> Unit = { item -> viewModel.toggleSelection(item.path) }

    LaunchedEffect(state.currentDir, state.searchActive, state.isSelectionMode) {
        fabExpanded = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when {
                state.isSelectionMode -> SelectionTopBar(
                    count = state.selection.size,
                    singleSelection = selectedItems.singleOrNull(),
                    onClose = viewModel::clearSelection,
                    onSelectAll = { viewModel.selectAll(visible.map(FileItem::path)) },
                    onDetails = { selectedItems.singleOrNull()?.let { detailsTarget = it } },
                )

                state.searchActive -> SearchTopBar(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    onClose = viewModel::closeSearch,
                )

                else -> ExplorerTopBar(
                    state = state,
                    itemCount = visible.size,
                    scrollBehavior = scrollBehavior,
                    onBack = viewModel::goUp,
                    onSearch = viewModel::startSearch,
                    onSelectAll = { viewModel.selectAll(visible.map(FileItem::path)) },
                    onNewFolder = { showNewFolderDialog = true },
                    onSort = { showSortSheet = true },
                    onLocations = { showLocationsSheet = true },
                    onToggleHidden = viewModel::toggleHiddenFiles,
                    onRefresh = viewModel::refresh,
                    onAbout = { showAboutDialog = true },
                    onAccess = { showAccessSheet = true },
                    onExitToHome = onExitToHome,
                    currentFolderBookmarked = currentFolderBookmarked,
                    onToggleCurrentBookmark = { viewModel.toggleBookmark(currentFolder) },
                )
            }
        },
        floatingActionButton = {
            if (!state.isSelectionMode) {
                ExplorerFabMenu(
                    expanded = fabExpanded,
                    clipboardMode = state.clipboard?.mode,
                    onExpandedChange = { fabExpanded = it },
                    onNewFolder = {
                        fabExpanded = false
                        showNewFolderDialog = true
                    },
                    onPaste = {
                        fabExpanded = false
                        viewModel.paste()
                    },
                    onSort = {
                        fabExpanded = false
                        showSortSheet = true
                    },
                    onRefresh = {
                        fabExpanded = false
                        viewModel.refresh()
                    },
                )
            }
        },
        bottomBar = {
            if (state.isSelectionMode) {
                SelectionActionBar(
                    singleSelection = selectedItems.singleOrNull(),
                    canShare = selectedItems.isNotEmpty() && selectedItems.none(FileItem::isDirectory),
                    onCopy = { viewModel.copyToClipboard(selectedItems.map(FileItem::file), ClipboardMode.COPY) },
                    onMove = { viewModel.copyToClipboard(selectedItems.map(FileItem::file), ClipboardMode.MOVE) },
                    onShare = {
                        scope.launch {
                            viewModel.prepareForExternalUse(selectedItems.map(FileItem::file))
                                .onSuccess { prepared ->
                                    shareFiles(context, prepared).onFailure {
                                        showSnackbar("Couldn't share the selected items")
                                    }
                                }
                                .onFailure {
                                    showSnackbar(it.message ?: "Couldn't share the selected items")
                                }
                        }
                    },
                    onRename = { selectedItems.singleOrNull()?.let { renameTarget = it } },
                    onDelete = { deleteTargets = selectedItems },
                    onCompress = { compressTargets = selectedItems },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { fabExpanded = false },
        ) {
            if (!state.searchActive && !state.isSelectionMode) {
                BreadcrumbBar(
                    home = state.home,
                    current = state.currentDir,
                    includeFilesystemRoot = state.root.absolutePath == "/",
                    root = state.root,
                    rootLabel = state.rootLabel,
                    onNavigate = viewModel::navigateTo,
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.entries.isEmpty() -> LoadingState()
                    state.error != null -> ErrorState(
                        message = state.error.orEmpty(),
                        onRetry = viewModel::refresh,
                        onAccessOptions = { showAccessSheet = true },
                    )

                    visible.isEmpty() -> EmptyState(
                        query = state.query,
                        searchActive = state.searchActive,
                        onCreateFolder = { showNewFolderDialog = true },
                    )

                    state.layout == LayoutMode.GRID -> FileGrid(
                        entries = visible,
                        selection = state.selection,
                        bookmarkedPaths = bookmarkedPaths,
                        selectionMode = state.isSelectionMode,
                        actions = actions,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                    )

                    else -> FileList(
                        entries = visible,
                        selection = state.selection,
                        bookmarkedPaths = bookmarkedPaths,
                        selectionMode = state.isSelectionMode,
                        actions = actions,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                    )
                }

                if (state.isLoading && state.entries.isNotEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                    )
                }
            }
        }
    }

    if (showNewFolderDialog) {
        NameDialog(
            title = "New folder",
            initialValue = "New folder",
            confirmLabel = "Create",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                showNewFolderDialog = false
                viewModel.createFolder(name)
            },
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Rename",
            initialValue = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                viewModel.rename(target.file, name)
            },
        )
    }

    deleteTargets?.let { targets ->
        DeleteDialog(
            items = targets,
            retentionDays = trashRetentionDays,
            onDismiss = { deleteTargets = null },
            onConfirm = { permanent ->
                deleteTargets = null
                viewModel.delete(targets.map(FileItem::file), permanent)
            },
        )
    }

    detailsTarget?.let { target ->
        DetailsDialog(item = target, onDismiss = { detailsTarget = null })
    }

    pickerArchive?.let { archive ->
        val volumes = remember(archive.path) {
            (context.applicationContext as YafmApplication).container.storage.volumes()
        }
        DirectoryPickerSheet(
            initialDirectory = state.currentDir,
            rootDirectory = state.root,
            volumes = volumes,
            listFolders = viewModel::listFolders,
            onDismiss = { pickerArchive = null },
            onSelect = { destination ->
                pickerArchive = null
                extractTarget = archive
                extractDestination = destination
            },
        )
    }

    extractTarget?.let { archive ->
        val destination = extractDestination ?: archive.file.parentFile ?: state.currentDir
        ExtractArchiveDialog(
            archiveName = archive.name,
            destination = destination.absolutePath,
            onDismiss = {
                extractTarget = null
                extractDestination = null
            },
            onExtract = { password ->
                extractTarget = null
                extractDestination = null
                archiveBusy = ArchiveWork.EXTRACT
                scope.launch {
                    val result = archives.extract(archive.file, destination, password)
                    archiveBusy = null
                    result.fold(
                        onSuccess = {
                            showSnackbar(
                                context.getString(
                                    R.string.archive_extracted,
                                    destination.absolutePath,
                                ),
                            )
                            viewModel.refresh()
                        },
                        onFailure = {
                            showSnackbar(
                                context.getString(
                                    R.string.archive_extract_failed,
                                    it.archiveMessage(context),
                                ),
                            )
                        },
                    )
                }
            },
        )
    }

    compressTargets?.let { targets ->
        CreateArchiveDialog(
            suggestedName = suggestedArchiveName(targets),
            initialFormat = ArchiveFormat.ZIP,
            onDismiss = { compressTargets = null },
            onCreate = { name, options ->
                compressTargets = null
                val destination = File(state.currentDir, name)
                if (destination.exists()) {
                    showSnackbar(context.getString(R.string.archive_exists))
                } else {
                    archiveBusy = ArchiveWork.CREATE
                    scope.launch {
                        val result = archives.create(targets.map(FileItem::file), destination, options)
                        archiveBusy = null
                        result.fold(
                            onSuccess = {
                                showSnackbar(context.getString(R.string.archive_created, name))
                                viewModel.refresh()
                            },
                            onFailure = {
                                showSnackbar(
                                    context.getString(
                                        R.string.archive_create_failed,
                                        it.archiveMessage(context),
                                    ),
                                )
                            },
                        )
                    }
                }
            },
        )
    }

    archiveBusy?.let { work ->
        ArchiveBusyDialog(
            title = stringResource(
                if (work == ArchiveWork.EXTRACT) R.string.archive_extract else R.string.archive_create,
            ),
            message = stringResource(
                if (work == ArchiveWork.EXTRACT) R.string.archive_extracting else R.string.archive_creating,
            ),
        )
    }

    if (showSortSheet) {
        SortAndViewSheet(
            state = state,
            onDismiss = { showSortSheet = false },
            onSort = viewModel::setSort,
            onAscending = viewModel::setAscending,
            onLayout = viewModel::setLayout,
            onToggleHidden = viewModel::toggleHiddenFiles,
        )
    }

    if (showLocationsSheet) {
        LocationsSheet(
            state = state,
            onNavigate = viewModel::navigateTo,
            onDismiss = { showLocationsSheet = false },
        )
    }

    if (showAccessSheet) {
        LaunchedEffect(Unit) { viewModel.refreshPrivileges() }
        AccessSheet(
            state = state,
            onDismiss = { showAccessSheet = false },
            onSelectMode = viewModel::selectAccessMode,
            onRequestShizuku = viewModel::requestShizukuPermission,
            onRequestRoot = { viewModel.selectAccessMode(AccessMode.ROOT) },
            onRefresh = viewModel::refreshPrivileges,
            onOpenAllFilesSettings = { openAllFilesSettings(context) },
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

// ------------------------------------------------------------------------------- list & grid

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileList(
    entries: List<FileItem>,
    selection: Set<String>,
    bookmarkedPaths: Set<String>,
    selectionMode: Boolean,
    actions: FileRowActions,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items = entries, key = { it.path }) { item ->
            FileRow(
                item = item,
                selected = item.path in selection,
                selectionMode = selectionMode,
                bookmarked = item.path in bookmarkedPaths,
                actions = actions,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGrid(
    entries: List<FileItem>,
    selection: Set<String>,
    bookmarkedPaths: Set<String>,
    selectionMode: Boolean,
    actions: FileRowActions,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = entries, key = { it.path }) { item ->
            FileGridCard(
                item = item,
                selected = item.path in selection,
                selectionMode = selectionMode,
                bookmarked = item.path in bookmarkedPaths,
                actions = actions,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

// --------------------------------------------------------------------------------- app bars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExplorerTopBar(
    state: ExplorerState,
    itemCount: Int,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSelectAll: () -> Unit,
    onNewFolder: () -> Unit,
    onSort: () -> Unit,
    onLocations: () -> Unit,
    onToggleHidden: () -> Unit,
    onRefresh: () -> Unit,
    onAbout: () -> Unit,
    onAccess: () -> Unit,
    onExitToHome: (() -> Unit)?,
    currentFolderBookmarked: Boolean,
    onToggleCurrentBookmark: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    LargeFlexibleTopAppBar(
        title = {
            Text(
                text = state.currentTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        subtitle = {
            val count = if (itemCount == 1) "1 item" else "$itemCount items"
            val mode = state.modeLabel
            Text(
                text = if (mode.isEmpty()) count else "$count · $mode",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (state.isAtRoot) {
                if (onExitToHome != null) {
                    IconButton(onClick = onExitToHome) {
                        Icon(Icons.Rounded.Home, contentDescription = "Home")
                    }
                }
            } else {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Up one level")
                }
            }
        },
        actions = {
            IconButton(onClick = onAccess) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = "Access & privileges",
                    tint = if (state.accessMode == AccessMode.STANDARD) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Rounded.Search, contentDescription = "Search this folder")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        leadingIcon = { Icon(Icons.Rounded.SelectAll, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onSelectAll()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("New folder") },
                        leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onNewFolder()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Sort & view") },
                        leadingIcon = { Icon(Icons.Rounded.Sort, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onSort()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Locations") },
                        leadingIcon = { Icon(Icons.Rounded.Storage, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onLocations()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (currentFolderBookmarked) {
                                        R.string.remove_bookmark
                                    } else {
                                        R.string.add_bookmark
                                    },
                                ),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (currentFolderBookmarked) {
                                    Icons.Rounded.Star
                                } else {
                                    Icons.Rounded.StarBorder
                                },
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onToggleCurrentBookmark()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Show hidden files") },
                        leadingIcon = {
                            Icon(
                                if (state.showHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (state.showHidden) Icon(Icons.Rounded.Check, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onToggleHidden()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRefresh()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("About YAFM") },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onAbout()
                        },
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    singleSelection: FileItem?,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDetails: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(if (count == 1) "1 item selected" else "$count items selected")
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Exit selection")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Rounded.SelectAll, contentDescription = "Select all")
            }
            if (singleSelection != null) {
                IconButton(onClick = onDetails) {
                    Icon(Icons.Rounded.Info, contentDescription = "Details")
                }
            }
        },
    )
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search")
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search this folder",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------- fab

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExplorerFabMenu(
    expanded: Boolean,
    clipboardMode: ClipboardMode?,
    onExpandedChange: (Boolean) -> Unit,
    onNewFolder: () -> Unit,
    onPaste: () -> Unit,
    onSort: () -> Unit,
    onRefresh: () -> Unit,
) {
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                content = {
                    with(ToggleFloatingActionButtonDefaults) {
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.Close else Icons.Rounded.Add,
                            contentDescription = if (expanded) "Close actions" else "More actions",
                            modifier = Modifier.animateIcon({ checkedProgress }),
                        )
                    }
                },
            )
        },
    ) {
        if (clipboardMode != null) {
            FloatingActionButtonMenuItem(
                onClick = onPaste,
                icon = { Icon(Icons.Rounded.ContentPaste, contentDescription = null) },
                text = { Text(if (clipboardMode == ClipboardMode.COPY) "Paste copy" else "Paste move") },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            )
        }
        FloatingActionButtonMenuItem(
            onClick = onNewFolder,
            icon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
            text = { Text("New folder") },
        )
        FloatingActionButtonMenuItem(
            onClick = onSort,
            icon = { Icon(Icons.Rounded.Sort, contentDescription = null) },
            text = { Text("Sort & view") },
        )
        FloatingActionButtonMenuItem(
            onClick = onRefresh,
            icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
            text = { Text("Refresh") },
        )
    }
}

// --------------------------------------------------------------------------- selection bar

@Composable
private fun SelectionActionBar(
    singleSelection: FileItem?,
    canShare: Boolean,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCompress: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SelectionAction(
                icon = Icons.Rounded.ContentCopy,
                label = "Copy",
                onClick = onCopy,
                modifier = Modifier.weight(1f),
            )
            SelectionAction(
                icon = Icons.Rounded.DriveFileMove,
                label = "Move",
                onClick = onMove,
                modifier = Modifier.weight(1f),
            )
            SelectionAction(
                icon = Icons.Rounded.Share,
                label = "Share",
                onClick = onShare,
                modifier = Modifier.weight(1f),
                enabled = canShare,
            )
            SelectionAction(
                icon = Icons.Rounded.Edit,
                label = "Rename",
                onClick = onRename,
                modifier = Modifier.weight(1f),
                enabled = singleSelection != null,
            )
            SelectionAction(
                icon = Icons.Rounded.FolderZip,
                label = stringResource(R.string.archive_compress),
                onClick = onCompress,
                modifier = Modifier.weight(1f),
            )
            SelectionAction(
                icon = Icons.Rounded.Delete,
                label = "Delete",
                onClick = onDelete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private enum class ArchiveWork { EXTRACT, CREATE }

private fun suggestedArchiveName(targets: List<FileItem>): String {
    val base = if (targets.size == 1) {
        val name = targets.first().name
        ArchiveFormat.fromName(name)?.let { name.dropLast(it.extension.length) } ?: name
    } else {
        "Archive"
    }
    return base + ".zip"
}

// ---------------------------------------------------------------------------------- sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortAndViewSheet(
    state: ExplorerState,
    onDismiss: () -> Unit,
    onSort: (SortOption) -> Unit,
    onAscending: (Boolean) -> Unit,
    onLayout: (LayoutMode) -> Unit,
    onToggleHidden: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Sort & view", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))

            Text(
                "Sort by",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OptionButtonGroup(
                options = listOf(
                    GroupOption(SortOption.NAME, "Name", Icons.Rounded.SortByAlpha),
                    GroupOption(SortOption.DATE, "Date", Icons.Rounded.Schedule),
                    GroupOption(SortOption.SIZE, "Size", Icons.Rounded.DataUsage),
                    GroupOption(SortOption.TYPE, "Type", Icons.Rounded.Category),
                ),
                selected = state.sort,
                onSelect = onSort,
            )

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Direction", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (state.ascending) "Ascending" else "Descending",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = { onAscending(!state.ascending) }) {
                    Icon(
                        imageVector = if (state.ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                        contentDescription = "Toggle sort direction",
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Layout",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OptionButtonGroup(
                options = listOf(
                    GroupOption(LayoutMode.LIST, "List", Icons.Rounded.ViewList),
                    GroupOption(LayoutMode.GRID, "Grid", Icons.Rounded.GridView),
                ),
                selected = state.layout,
                onSelect = onLayout,
            )

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Show hidden files", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Names that start with a dot",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.showHidden, onCheckedChange = { onToggleHidden() })
            }
        }
    }
}

private data class Location(
    val label: String,
    val description: String,
    val directory: File,
    val icon: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationsSheet(
    state: ExplorerState,
    onNavigate: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val root = state.home
    val locations = remember(root, state.accessMode, state.root, context) {
        buildList {
            add(Location("Internal storage", "/storage/emulated/0", root, Icons.Rounded.Storage))

            if (state.accessMode != AccessMode.STANDARD || state.rootForAppDataEnabled) {
                add(
                    Location(
                        "Android data",
                        "Android/data",
                        File(root, "Android/data"),
                        Icons.Rounded.Android,
                    ),
                )
                add(
                    Location(
                        "Android obb",
                        "Android/obb",
                        File(root, "Android/obb"),
                        Icons.Rounded.Android,
                    ),
                )
            }

            if (state.root.absolutePath == "/") {
                add(Location("Root", "Filesystem root", File("/"), Icons.Rounded.Shield))
                add(Location("Data", "/data", File("/data"), Icons.Rounded.Storage))
                add(Location("System", "/system", File("/system"), Icons.Rounded.Storage))
                add(
                    Location(
                        "Temp",
                        "/data/local/tmp",
                        File("/data/local/tmp"),
                        Icons.Rounded.Folder,
                    ),
                )
            }

            listOf(
                Triple("Downloads", "Download", Icons.Rounded.Download),
                Triple("Documents", "Documents", Icons.Rounded.Description),
                Triple("Camera", "DCIM", Icons.Rounded.PhotoCamera),
                Triple("Pictures", "Pictures", Icons.Rounded.Image),
                Triple("Music", "Music", Icons.Rounded.MusicNote),
                Triple("Movies", "Movies", Icons.Rounded.Movie),
            ).forEach { (label, folderName, icon) ->
                val directory = File(root, folderName)
                if (directory.isDirectory) {
                    add(Location(label, folderName, directory, icon))
                }
            }
            context.getExternalFilesDir(null)?.let { directory ->
                add(
                    Location(
                        "App files",
                        "Android/data/${context.packageName}/files",
                        directory,
                        Icons.Rounded.Folder,
                    ),
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Locations",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            locations.forEach { location ->
                ListItem(
                    headlineContent = { Text(location.label) },
                    supportingContent = {
                        Text(
                            text = location.description,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = location.icon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    },
                    trailingContent = if (
                        location.directory.absolutePath == state.currentDir.absolutePath
                    ) {
                        { Icon(Icons.Rounded.Check, contentDescription = "Current folder") }
                    } else {
                        null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        onDismiss()
                        onNavigate(location.directory)
                    },
                )
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "YAFM",
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Yet Another File Manager",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        text = {
            Text(
                "A small file manager built with Jetpack Compose and Material 3 Expressive.\n\n" +
                    "Browse, search, copy, move, rename, share and delete files and folders on your device.",
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Nice") }
        },
    )
}

// --------------------------------------------------------------------------------- helpers

private fun openAllFilesSettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
    runCatching { context.startActivity(appIntent) }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }
}
