package com.anderei.yafm.ui.cleaner

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anderei.yafm.R
import com.anderei.yafm.data.FileItem
import com.anderei.yafm.data.fileKind
import com.anderei.yafm.ui.explorer.FileExplorerViewModel
import com.anderei.yafm.ui.explorer.FileIconBadge
import com.anderei.yafm.util.formatSize
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerRoute(
    explorerViewModel: FileExplorerViewModel,
    onBack: () -> Unit,
) {
    val viewModel: CleanerViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var confirmClean by remember { mutableStateOf(false) }
    var isCleaning by remember { mutableStateOf(false) }

    val scanWithAds: () -> Unit = {
        viewModel.scan(adScanner = { explorerViewModel.scanAdFiles() })
    }

    LaunchedEffect(Unit) { scanWithAds() }
    DisposableEffect(Unit) {
        onDispose { viewModel.cancelScan() }
    }

    val selectedCount = state.selectedCount
    val selectedSize = state.selectedSize
    val confirmTitle = stringResource(R.string.cleaner_confirm_title, selectedCount)
    val confirmText = stringResource(R.string.cleaner_confirm_text)
    val confirmButton = stringResource(R.string.cleaner_delete_permanently)
    val cancelLabel = stringResource(R.string.cancel)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_cleaner)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = scanWithAds,
                        enabled = !state.isScanning && !isCleaning,
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.cleaner_scan),
                        )
                    }
                },
            )
        },
        bottomBar = {
            CleanerBottomBar(
                selectedCount = selectedCount,
                selectedSize = selectedSize,
                enabled = selectedCount > 0 && !state.isScanning && !isCleaning,
                cleaning = isCleaning,
                onClean = { confirmClean = true },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isScanning) {
                item(key = "scanning") {
                    ScanningCard(
                        scanned = state.scannedCount,
                        onCancel = viewModel::cancelScan,
                    )
                }
            }

            state.error?.let { error ->
                item(key = "error") {
                    ErrorCard(
                        message = error,
                        onRetry = viewModel::scan,
                    )
                }
            }

            if (!state.isScanning && state.error == null) {
                if (state.totalCount == 0) {
                    item(key = "empty") {
                        EmptyCleaner()
                    }
                } else {
                    state.categories.forEach { category ->
                        item(key = "header-${category.type.name}") {
                            CleanerCategoryHeader(
                                category = category,
                                state = state,
                                onToggleCategory = viewModel::toggleCategory,
                                onToggleExpanded = viewModel::toggleExpanded,
                            )
                        }

                        if (category.type in state.expanded && category.type != CleanerCategoryType.TRASH) {
                            if (category.items.isEmpty()) {
                                item(key = "no-items-${category.type.name}") {
                                    Text(
                                        text = stringResource(R.string.cleaner_empty_category),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 52.dp, bottom = 4.dp),
                                    )
                                }
                            } else {
                                items(
                                    items = category.items,
                                    key = { "${category.type.name}-${it.path}" },
                                ) { item ->
                                    CleanerItemRow(
                                        item = item,
                                        checked = item.path in state.selection,
                                        onToggle = { viewModel.toggleItem(item.path) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (confirmClean) {
        AlertDialog(
            onDismissRequest = { confirmClean = false },
            icon = { Icon(Icons.Rounded.CleaningServices, contentDescription = null) },
            title = { Text(confirmTitle) },
            text = {
                Column {
                    Text(confirmText)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.cleaner_selected_summary, selectedCount, formatSize(selectedSize)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClean = false
                        if (isCleaning) return@TextButton
                        isCleaning = true
                        val files = state.selectedItems.map { File(it.path) }
                        val trashSelected = state.trashSelected
                        val trashCount = state.trashCategory?.count ?: 0
                        scope.launch {
                            var deleted = 0
                            var failed = 0
                            try {
                                if (files.isNotEmpty()) {
                                    val result = explorerViewModel.deleteCleanerFiles(files)
                                    deleted += result.succeeded
                                    failed += result.failed
                                }
                                if (trashSelected) {
                                    val result = explorerViewModel.emptyTrash()
                                    if (result.isFailure) failed += trashCount
                                }
                                val message = if (failed == 0) {
                                    context.getString(R.string.cleaner_deleted, deleted) +
                                        " · " +
                                        context.getString(R.string.cleaner_deleted_size, formatSize(selectedSize))
                                } else {
                                    context.getString(R.string.cleaner_clean_failed)
                                }
                                snackbarHostState.showSnackbar(message)
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.cleaner_clean_failed),
                                )
                            } finally {
                                isCleaning = false
                                scanWithAds()
                            }
                        }
                    },
                ) {
                    Text(
                        text = confirmButton,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClean = false }) {
                    Text(cancelLabel)
                }
            },
        )
    }
}

@Composable
private fun CleanerBottomBar(
    selectedCount: Int,
    selectedSize: Long,
    enabled: Boolean,
    cleaning: Boolean,
    onClean: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cleaner_selected, selectedCount),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = formatSize(selectedSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onClean, enabled = enabled) {
                Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (cleaning) R.string.cleaner_cleaning else R.string.cleaner_clean))
            }
        }
    }
}

@Composable
private fun ScanningCard(scanned: Int, onCancel: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.cleaner_scanning, scanned),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cleaner_cancel_scan))
                }
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cleaner_scan_failed),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.cleaner_scan))
            }
        }
    }
}

@Composable
private fun EmptyCleaner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.CleaningServices,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.cleaner_nothing),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.cleaner_nothing_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanerCategoryHeader(
    category: CleanerCategory,
    state: CleanerUiState,
    onToggleCategory: (CleanerCategoryType) -> Unit,
    onToggleExpanded: (CleanerCategoryType) -> Unit,
) {
    val allSelected = state.isCategoryFullySelected(category)
    val partiallySelected = state.isCategoryPartiallySelected(category)
    val toggleable = when {
        allSelected -> ToggleableState.On
        partiallySelected -> ToggleableState.Indeterminate
        else -> ToggleableState.Off
    }
    val expanded = category.type in state.expanded
    val canExpand = category.items.isNotEmpty()

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = canExpand) { onToggleExpanded(category.type) },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TriStateCheckbox(
                    state = toggleable,
                    onClick = { onToggleCategory(category.type) },
                    enabled = category.count > 0,
                )
                CategoryIcon(category.type)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(category.type.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(
                            R.string.cleaner_category_count,
                            category.count,
                            formatSize(category.totalSize),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canExpand) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.cleaner_collapse else R.string.cleaner_expand,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(category.type.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp),
            )
            if (category.type == CleanerCategoryType.ADS) {
                Text(
                    text = stringResource(R.string.cleaner_ads_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 48.dp, top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryIcon(type: CleanerCategoryType) {
    val (container, content, icon) = categoryVisual(type)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = container,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun categoryVisual(type: CleanerCategoryType): Triple<Color, Color, ImageVector> {
    val colors = MaterialTheme.colorScheme
    return when (type) {
        CleanerCategoryType.TRASH ->
            Triple(colors.secondaryContainer, colors.onSecondaryContainer, Icons.Rounded.DeleteSweep)
        CleanerCategoryType.EMPTY ->
            Triple(colors.tertiaryContainer, colors.onTertiaryContainer, Icons.Rounded.FolderDelete)
        CleanerCategoryType.INSTALLERS ->
            Triple(colors.primaryContainer, colors.onPrimaryContainer, Icons.Rounded.Android)
        CleanerCategoryType.ADS ->
            Triple(colors.tertiaryContainer, colors.onTertiaryContainer, Icons.Rounded.Campaign)
        CleanerCategoryType.DOWNLOADS ->
            Triple(colors.secondaryContainer, colors.onSecondaryContainer, Icons.Rounded.Download)
    }
}

@Composable
private fun CleanerItemRow(
    item: CleanerItem,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val fileItem = remember(item) {
        FileItem(
            file = File(item.path),
            name = item.name,
            isDirectory = item.isDirectory,
            size = item.size,
            lastModified = 0L,
            kind = fileKind(item.name, item.isDirectory),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        FileIconBadge(fileItem, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = File(item.displayPath).parent ?: "/",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (item.isDirectory) stringResource(R.string.cleaner_folder) else formatSize(item.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
