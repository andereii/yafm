package com.anderei.yafm.ui.archive

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anderei.yafm.R
import com.anderei.yafm.YafmApplication
import com.anderei.yafm.data.FileItem
import com.anderei.yafm.data.archive.ArchiveFileInfo
import com.anderei.yafm.data.archive.archiveDisplayPath
import com.anderei.yafm.ui.explorer.FileExplorerViewModel
import com.anderei.yafm.ui.explorer.FileIconBadge
import com.anderei.yafm.util.formatDateTime
import com.anderei.yafm.util.formatSize
import kotlinx.coroutines.launch
import java.io.File

private enum class ArchiveBusy { EXTRACT, CREATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressedFilesRoute(
    explorerViewModel: FileExplorerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as YafmApplication
    val repository = remember { app.container.archives }
    val viewModel: CompressedFilesViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.scan() }
    val snackbarHostState = remember { SnackbarHostState() }

    var pickerArchive by remember { mutableStateOf<ArchiveFileInfo?>(null) }
    var extractArchive by remember { mutableStateOf<ArchiveFileInfo?>(null) }
    var extractDestination by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf<ArchiveBusy?>(null) }

    fun showMessage(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    fun extractHere(archive: ArchiveFileInfo) {
        extractArchive = archive
        extractDestination = archive.file.parentFile ?: viewModel.volumeRootFor(archive.file)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.compressed_title), fontWeight = FontWeight.SemiBold)
                        if (!state.isScanning && state.archives.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.compressed_count,
                                    state.archives.size,
                                    formatSize(state.archives.sumOf { it.size }),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::scan, enabled = !state.isScanning) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.compressed_scan))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isScanning) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.compressed_scanning, state.scannedFiles),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::cancelScan) {
                                    Text(stringResource(R.string.compressed_cancel_scan))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            state.error?.let { error ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.compressed_scan_failed) + ": " + error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            if (!state.isScanning && state.archives.isEmpty() && state.error == null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Rounded.FolderZip,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.compressed_empty),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.compressed_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(state.archives, key = { it.file.absolutePath }) { archive ->
                ArchiveRow(
                    archive = archive,
                    onExtractTo = { pickerArchive = archive },
                    onExtractHere = { extractHere(archive) },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    pickerArchive?.let { archive ->
        val volumes = remember(archive.file.absolutePath) {
            app.container.storage.volumes()
        }
        DirectoryPickerSheet(
            initialDirectory = archive.file.parentFile ?: viewModel.volumeRootFor(archive.file),
            rootDirectory = viewModel.volumeRootFor(archive.file),
            volumes = volumes,
            listFolders = explorerViewModel::listFolders,
            onDismiss = { pickerArchive = null },
            onSelect = { destination ->
                pickerArchive = null
                extractArchive = archive
                extractDestination = destination
            },
        )
    }

    val archiveToExtract = extractArchive
    val destination = extractDestination
    if (archiveToExtract != null && destination != null) {
        ExtractArchiveDialog(
            archiveName = archiveToExtract.name,
            destination = destination.archiveDisplayPath(),
            onDismiss = {
                extractArchive = null
                extractDestination = null
            },
            onExtract = { password ->
                extractArchive = null
                extractDestination = null
                busy = ArchiveBusy.EXTRACT
                scope.launch {
                    val result = repository.extract(archiveToExtract.file, destination, password)
                    busy = null
                    result.fold(
                        onSuccess = {
                            showMessage(
                                context.getString(
                                    R.string.archive_extracted,
                                    destination.archiveDisplayPath(),
                                ),
                            )
                        },
                        onFailure = {
                            showMessage(
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

    busy?.let { kind ->
        ArchiveBusyDialog(
            title = stringResource(
                if (kind == ArchiveBusy.EXTRACT) R.string.archive_extract else R.string.archive_creating,
            ),
            message = stringResource(
                if (kind == ArchiveBusy.EXTRACT) R.string.archive_extracting else R.string.archive_creating,
            ),
        )
    }
}

@Composable
private fun ArchiveRow(
    archive: ArchiveFileInfo,
    onExtractTo: () -> Unit,
    onExtractHere: () -> Unit,
) {
    val item = remember(archive.file.absolutePath) { FileItem.from(archive.file) }
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExtractTo)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileIconBadge(item, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = archive.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = archive.displayPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${archive.format.displayName} · ${formatSize(archive.size)} · ${formatDateTime(archive.lastModified)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onExtractTo) {
                Icon(
                    Icons.Rounded.Unarchive,
                    contentDescription = stringResource(R.string.archive_extract_to),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.archive_extract_here)) },
                    onClick = {
                        menuOpen = false
                        onExtractHere()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.archive_extract_to)) },
                    onClick = {
                        menuOpen = false
                        onExtractTo()
                    },
                )
            }
        }
    }
}

internal fun Throwable.archiveMessage(context: Context): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("password", ignoreCase = true) ->
            context.getString(R.string.archive_password_required)

        raw.contains("already exists", ignoreCase = true) ->
            context.getString(R.string.archive_exists)

        raw.isBlank() -> context.getString(R.string.archive_working)
        else -> raw
    }
}
