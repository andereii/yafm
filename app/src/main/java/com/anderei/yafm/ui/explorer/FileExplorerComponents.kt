package com.anderei.yafm.ui.explorer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anderei.yafm.data.FileItem
import com.anderei.yafm.data.FileKind
import com.anderei.yafm.util.formatDateTime
import com.anderei.yafm.util.formatSize
import com.anderei.yafm.util.mimeType
import kotlinx.coroutines.delay
import java.io.File

// ---------------------------------------------------------------------------- icons & labels

fun kindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.FOLDER -> Icons.Rounded.Folder
    FileKind.IMAGE -> Icons.Rounded.Image
    FileKind.VIDEO -> Icons.Rounded.Movie
    FileKind.AUDIO -> Icons.Rounded.AudioFile
    FileKind.PDF -> Icons.Rounded.PictureAsPdf
    FileKind.DOCUMENT -> Icons.Rounded.Description
    FileKind.SPREADSHEET -> Icons.Rounded.TableChart
    FileKind.ARCHIVE -> Icons.Rounded.FolderZip
    FileKind.APK -> Icons.Rounded.Android
    FileKind.CODE -> Icons.Rounded.Code
    FileKind.TEXT -> Icons.Rounded.Article
    FileKind.OTHER -> Icons.Rounded.InsertDriveFile
}

@Composable
private fun fileIconColors(kind: FileKind): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (kind) {
        FileKind.FOLDER -> colors.primaryContainer to colors.onPrimaryContainer
        FileKind.IMAGE -> colors.tertiaryContainer to colors.onTertiaryContainer
        FileKind.VIDEO -> colors.secondaryContainer to colors.onSecondaryContainer
        FileKind.AUDIO -> colors.secondaryContainer to colors.onSecondaryContainer
        FileKind.PDF -> colors.errorContainer to colors.onErrorContainer
        FileKind.APK -> colors.primaryContainer to colors.onPrimaryContainer
        FileKind.SPREADSHEET -> colors.tertiaryContainer to colors.onTertiaryContainer
        FileKind.DOCUMENT,
        FileKind.ARCHIVE,
        FileKind.CODE,
        FileKind.TEXT,
        FileKind.OTHER,
        -> colors.surfaceContainerHighest to colors.onSurface
    }
}

fun displayName(kind: FileKind): String = when (kind) {
    FileKind.FOLDER -> "Folder"
    FileKind.IMAGE -> "Image"
    FileKind.VIDEO -> "Video"
    FileKind.AUDIO -> "Audio"
    FileKind.PDF -> "PDF document"
    FileKind.DOCUMENT -> "Document"
    FileKind.SPREADSHEET -> "Spreadsheet"
    FileKind.ARCHIVE -> "Archive"
    FileKind.APK -> "Android app"
    FileKind.CODE -> "Code"
    FileKind.TEXT -> "Text"
    FileKind.OTHER -> "File"
}

fun FileItem.subtitle(): String =
    if (isDirectory) "Folder" else "${formatSize(size)} • ${formatDateTime(lastModified)}"

@Composable
fun FileIconBadge(
    item: FileItem,
    size: Dp = 46.dp,
    appBadge: AppBadge? = null,
) {
    val (container, content) = fileIconColors(item.kind)
    Box(Modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.34f))
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = kindIcon(item.kind),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size * 0.52f),
            )
        }

        if (appBadge != null) {
            // Little owning-app icon in the corner, with a surface-coloured cutout ring.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.56f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = appBadge.icon,
                    contentDescription = "App folder: ${appBadge.label}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(size * 0.47f)
                        .clip(CircleShape),
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------- item menus

data class FileRowActions(
    val onOpen: (FileItem) -> Unit = {},
    val onRename: (FileItem) -> Unit = {},
    val onCopy: (FileItem) -> Unit = {},
    val onMove: (FileItem) -> Unit = {},
    val onShare: (FileItem) -> Unit = {},
    val onDetails: (FileItem) -> Unit = {},
    val onDelete: (FileItem) -> Unit = {},
)

@Composable
fun FileItemMenu(
    expanded: Boolean,
    item: FileItem,
    actions: FileRowActions,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (item.isDirectory) "Open" else "Open with") },
            leadingIcon = {
                Icon(
                    if (item.isDirectory) Icons.Rounded.FolderOpen else Icons.Rounded.OpenInNew,
                    contentDescription = null,
                )
            },
            onClick = {
                onDismiss()
                actions.onOpen(item)
            },
        )
        DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
            onClick = {
                onDismiss()
                actions.onRename(item)
            },
        )
        DropdownMenuItem(
            text = { Text("Copy") },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
            onClick = {
                onDismiss()
                actions.onCopy(item)
            },
        )
        DropdownMenuItem(
            text = { Text("Move") },
            leadingIcon = { Icon(Icons.Rounded.DriveFileMove, contentDescription = null) },
            onClick = {
                onDismiss()
                actions.onMove(item)
            },
        )
        if (!item.isDirectory) {
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                onClick = {
                    onDismiss()
                    actions.onShare(item)
                },
            )
        }
        DropdownMenuItem(
            text = { Text("Details") },
            leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
            onClick = {
                onDismiss()
                actions.onDetails(item)
            },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = {
                onDismiss()
                actions.onDelete(item)
            },
        )
    }
}

// --------------------------------------------------------------------------------- list row

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FileItem,
    selected: Boolean,
    selectionMode: Boolean,
    actions: FileRowActions,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val shape = MaterialTheme.shapes.large
    val appBadge = rememberAppBadge(item)

    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = shape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileIconBadge(item, appBadge = appBadge)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isDirectory) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "More options for ${item.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FileItemMenu(
                        expanded = menuOpen,
                        item = item,
                        actions = actions,
                        onDismiss = { menuOpen = false },
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------- grid card

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCard(
    item: FileItem,
    selected: Boolean,
    selectionMode: Boolean,
    actions: FileRowActions,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val shape = MaterialTheme.shapes.largeIncreased
    val appBadge = rememberAppBadge(item)

    Surface(
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                )
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileIconBadge(item, 44.dp, appBadge)
                Spacer(Modifier.weight(1f))
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = null)
                } else {
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "More options for ${item.name}",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FileItemMenu(
                            expanded = menuOpen,
                            item = item,
                            actions = actions,
                            onDismiss = { menuOpen = false },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (item.isDirectory) "Folder" else formatSize(item.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// --------------------------------------------------------------------------------- breadcrumb

@Composable
fun BreadcrumbBar(
    home: File,
    current: File,
    includeFilesystemRoot: Boolean,
    onNavigate: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val crumbs = remember(home, current, includeFilesystemRoot) {
        buildCrumbs(home, current, includeFilesystemRoot)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(crumbs) {
        val lastIndex = (crumbs.size * 2 - 2).coerceAtLeast(0)
        listState.animateScrollToItem(lastIndex)
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, crumb ->
            item(key = "crumb-${crumb.directory.absolutePath}") {
                AssistChip(
                    onClick = { onNavigate(crumb.directory) },
                    label = { Text(crumb.label, maxLines = 1) },
                    leadingIcon = if (index == 0) {
                        { Icon(Icons.Rounded.Storage, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
            if (index < crumbs.lastIndex) {
                item(key = "separator-$index") {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- button groups

data class GroupOption<T>(val value: T, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> OptionButtonGroup(
    options: List<GroupOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    ButtonGroup(
        modifier = modifier.fillMaxWidth(),
        overflowIndicator = { menuState ->
            Box {
                FilledTonalIconButton(onClick = menuState::show) {
                    Icon(Icons.Rounded.MoreHoriz, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = menuState.isShowing,
                    onDismissRequest = menuState::dismiss,
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = { Icon(option.icon, contentDescription = null) },
                            trailingIcon = {
                                if (option.value == selected) {
                                    Icon(Icons.Rounded.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                menuState.dismiss()
                                onSelect(option.value)
                            },
                        )
                    }
                }
            }
        },
    ) {
        options.forEach { option ->
            toggleableItem(
                checked = option.value == selected,
                label = option.label,
                onCheckedChange = { onSelect(option.value) },
                icon = { Icon(option.icon, contentDescription = null) },
                weight = 1f,
            )
        }
    }
}

// ------------------------------------------------------------------------------- selection UI

@Composable
fun SelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
}

// ----------------------------------------------------------------------------------- dialogs

@Composable
fun NameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(value) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun DeleteDialog(items: List<FileItem>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val single = items.size == 1
    val first = items.firstOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
        title = {
            Text(
                if (single && first != null) "Delete “${first.name}”?" else "Delete ${items.size} items?",
            )
        },
        text = {
            Text(
                if (single && first?.isDirectory == true) {
                    "The folder and everything inside it will be permanently deleted."
                } else {
                    "This action can't be undone."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun DetailsDialog(item: FileItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow("Name", item.name)
                DetailRow(
                    "Type",
                    if (item.isDirectory) "Folder" else "${displayName(item.kind)} · ${item.file.mimeType()}",
                )
                DetailRow("Location", item.file.parent ?: "/")
                if (!item.isDirectory) DetailRow("Size", formatSize(item.size))
                DetailRow("Modified", formatDateTime(item.lastModified))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ----------------------------------------------------------------------------------- states

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator(modifier = Modifier.size(56.dp))
    }
}

@Composable
fun EmptyState(
    query: String,
    searchActive: Boolean,
    onCreateFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (searchActive) Icons.Rounded.SearchOff else Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (searchActive) "No matches for “$query”" else "This folder is empty",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (searchActive) "Try a different name" else "Files you add will show up here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!searchActive) {
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(onClick = onCreateFolder) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New folder")
                }
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onAccessOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onAccessOptions) { Text("Access options") }
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}
