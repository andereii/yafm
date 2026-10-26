package com.anderei.yafm.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anderei.yafm.R
import com.anderei.yafm.data.archive.ArchiveFormat
import com.anderei.yafm.data.archive.CreateArchiveOptions
import kotlin.math.roundToInt

@Composable
fun ExtractArchiveDialog(
    archiveName: String,
    destination: String,
    onDismiss: () -> Unit,
    onExtract: (password: String?) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FolderZip, contentDescription = null) },
        title = { Text(stringResource(R.string.archive_extract_title, archiveName)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.archive_destination),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = destination,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.archive_password_optional)) },
                    singleLine = true,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = stringResource(
                                    if (showPassword) R.string.archive_hide_password else R.string.archive_show_password,
                                ),
                            )
                        }
                    },
                    supportingText = {
                        Text(stringResource(R.string.archive_password_optional_hint))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onExtract(password.takeIf { it.isNotBlank() }) }) {
                Text(stringResource(R.string.archive_extract))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.archive_cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateArchiveDialog(
    suggestedName: String,
    initialFormat: ArchiveFormat,
    onDismiss: () -> Unit,
    onCreate: (name: String, options: CreateArchiveOptions) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(suggestedName) }
    var format by remember { mutableStateOf(initialFormat) }
    var level by rememberSaveable { mutableIntStateOf(5) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    fun changeFormat(next: ArchiveFormat) {
        if (next == format) return
        val currentName = name
        val extension = format.extension
        name = when {
            currentName.endsWith(extension, ignoreCase = true) ->
                currentName.dropLast(extension.length) + next.extension

            ArchiveFormat.fromName(currentName) == null -> currentName + next.extension
            else -> currentName
        }
        format = next
    }

    val finalName = run {
        val base = ArchiveFormat.fromName(name)?.let { name.dropLast(it.extension.length) } ?: name
        base + format.extension
    }
    val canCreate = name.trim().isNotEmpty()
    val passwordEnabled = format.supportsPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FolderZip, contentDescription = null) },
        title = { Text(stringResource(R.string.archive_create)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.archive_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.archive_format),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ArchiveFormat.creationFormats.forEach { option ->
                        FilterChip(
                            selected = option == format,
                            onClick = { changeFormat(option) },
                            label = { Text(option.displayName) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (format == ArchiveFormat.TAR) {
                    Text(
                        text = stringResource(R.string.archive_tar_no_compression),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.archive_compression_level),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (level == 0) {
                            stringResource(R.string.archive_level_store)
                        } else {
                            stringResource(R.string.archive_level, level)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { level = it.roundToInt() },
                        valueRange = 0f..9f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = passwordEnabled,
                    label = { Text(stringResource(R.string.archive_password)) },
                    singleLine = true,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            enabled = passwordEnabled,
                        ) {
                            Icon(
                                imageVector = if (showPassword) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = stringResource(
                                    if (showPassword) R.string.archive_hide_password else R.string.archive_show_password,
                                ),
                            )
                        }
                    },
                    supportingText = { Text(stringResource(R.string.archive_password_support)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = finalName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        finalName.trim(),
                        CreateArchiveOptions(
                            format = format,
                            level = level,
                            password = password.takeIf { passwordEnabled && it.isNotBlank() },
                        ),
                    )
                },
                enabled = canCreate,
            ) {
                Text(stringResource(R.string.archive_create_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.archive_cancel)) }
        },
    )
}

@Composable
fun ArchiveBusyDialog(title: String, message: String) {
    AlertDialog(
        onDismissRequest = {},
        icon = { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {},
    )
}
