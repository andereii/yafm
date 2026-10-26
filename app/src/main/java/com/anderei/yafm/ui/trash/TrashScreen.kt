package com.anderei.yafm.ui.trash

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anderei.yafm.R
import com.anderei.yafm.data.FileItem
import com.anderei.yafm.data.TrashEntry
import com.anderei.yafm.data.fileKind
import com.anderei.yafm.ui.explorer.FileIconBadge
import com.anderei.yafm.util.formatDateTime
import com.anderei.yafm.util.formatSize
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashRoute(
    entries: List<TrashEntry>,
    retentionDays: Int,
    onRestore: suspend (TrashEntry) -> Result<Unit>,
    onDelete: suspend (TrashEntry) -> Result<Unit>,
    onEmpty: suspend () -> Result<Unit>,
    onCleanup: suspend () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var deleteTarget by remember { mutableStateOf<TrashEntry?>(null) }
    var detailsTarget by remember { mutableStateOf<TrashEntry?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onCleanup() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_recycle_bin)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) {
                            Text(stringResource(R.string.trash_empty_action))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.trash_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.trash_retention_note, retentionDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                text = stringResource(R.string.trash_retention_note, retentionDays),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onOpenSettings) {
                                Text(stringResource(R.string.settings))
                            }
                        }
                    }
                }
                items(entries, key = { it.id }) { entry ->
                    TrashRow(
                        entry = entry,
                        onRestore = {
                            scope.launch {
                                val result = onRestore(entry)
                                snackbarHostState.showSnackbar(
                                    if (result.isSuccess) {
                                        context.getString(R.string.trash_restored)
                                    } else {
                                        context.getString(
                                            R.string.trash_restore_failed,
                                            result.exceptionOrNull()?.message.orEmpty(),
                                        )
                                    },
                                )
                            }
                        },
                        onDelete = { deleteTarget = entry },
                        onInfo = { detailsTarget = entry },
                    )
                }
            }
        }
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.trash_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.trash_delete_confirm_text) + "\n\n" + entry.name,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            val result = onDelete(entry)
                            snackbarHostState.showSnackbar(
                                if (result.isSuccess) {
                                    context.getString(R.string.trash_deleted)
                                } else {
                                    context.getString(
                                        R.string.trash_delete_failed,
                                        result.exceptionOrNull()?.message.orEmpty(),
                                    )
                                },
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.trash_empty_confirm_title)) },
            text = { Text(stringResource(R.string.trash_empty_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEmpty = false
                        scope.launch {
                            val result = onEmpty()
                            snackbarHostState.showSnackbar(
                                if (result.isSuccess) {
                                    context.getString(R.string.trash_emptied)
                                } else {
                                    context.getString(R.string.trash_empty_failed)
                                },
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.trash_empty_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    detailsTarget?.let { entry ->
        val item = remember(entry) { entry.toFileItem() }
        AlertDialog(
            onDismissRequest = { detailsTarget = null },
            title = { Text(stringResource(R.string.trash_info)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    DetailLine(
                        label = stringResource(R.string.trash_original_location),
                        value = entry.originalPath,
                    )
                    DetailLine(
                        label = stringResource(R.string.trash_deleted_at),
                        value = formatDateTime(entry.deletedAt),
                    )
                    DetailLine(
                        label = stringResource(R.string.trash_expires_at),
                        value = formatDateTime(entry.expiresAt),
                    )
                    DetailLine(
                        label = stringResource(R.string.trash_size),
                        value = if (entry.isDirectory) "—" else formatSize(entry.size),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsTarget = null }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun TrashRow(
    entry: TrashEntry,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
) {
    val item = remember(entry) { entry.toFileItem() }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileIconBadge(item, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = File(entry.originalPath).parent ?: "/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onInfo) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = stringResource(R.string.trash_info),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.trash_deleted_on, formatDateTime(entry.deletedAt)) +
                    " · " +
                    stringResource(R.string.trash_expires_on, formatDateTime(entry.expiresAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRestore) {
                    Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.trash_restore))
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.trash_delete_now))
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun TrashEntry.toFileItem(): FileItem = FileItem(
    file = File(originalPath),
    name = name,
    isDirectory = isDirectory,
    size = size,
    lastModified = deletedAt,
    kind = fileKind(name, isDirectory),
)
