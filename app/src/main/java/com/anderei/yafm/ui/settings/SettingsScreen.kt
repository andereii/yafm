package com.anderei.yafm.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anderei.yafm.R
import com.anderei.yafm.data.AccessMode
import com.anderei.yafm.data.AppLanguage
import com.anderei.yafm.data.PrivilegeState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    language: AppLanguage,
    retentionDays: Int,
    accessMode: AccessMode,
    privilege: PrivilegeState,
    useRootForAppData: Boolean,
    correctSelinuxContext: Boolean,
    onLanguageChange: (AppLanguage) -> Unit,
    onRetentionChange: (Int) -> Unit,
    onSelectAccessMode: (AccessMode) -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestRoot: () -> Unit,
    onRefreshPrivileges: () -> Unit,
    onUseRootForAppDataChange: (Boolean) -> Unit,
    onCorrectSelinuxContextChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val version = rememberVersion(context)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsSection(title = stringResource(R.string.language)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AppLanguage.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = option == language,
                                onClick = { onLanguageChange(option) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = AppLanguage.entries.size,
                                ),
                                label = { Text(option.displayName) },
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_privileged_access)) {
                    Text(
                        text = stringResource(R.string.access_mode),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AccessMode.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = option == accessMode,
                                onClick = { onSelectAccessMode(option) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = AccessMode.entries.size,
                                ),
                                label = {
                                    Text(
                                        stringResource(
                                            when (option) {
                                                AccessMode.STANDARD -> R.string.access_mode_standard
                                                AccessMode.SHIZUKU -> R.string.access_mode_shizuku
                                                AccessMode.ROOT -> R.string.access_mode_root
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.access_mode_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))
                    PrivilegeStatusBlock(
                        privilege = privilege,
                        onRequestShizuku = onRequestShizuku,
                        onRequestRoot = onRequestRoot,
                        onRefresh = onRefreshPrivileges,
                    )

                    Spacer(Modifier.height(14.dp))
                    PrivatizedSwitchRow(
                        title = stringResource(R.string.root_use_app_data),
                        description = stringResource(R.string.root_use_app_data_description),
                        checked = useRootForAppData,
                        onCheckedChange = onUseRootForAppDataChange,
                    )
                    PrivatizedSwitchRow(
                        title = stringResource(R.string.root_correct_selinux),
                        description = stringResource(R.string.root_correct_selinux_description),
                        checked = correctSelinuxContext,
                        onCheckedChange = onCorrectSelinuxContextChange,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_privileged_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.feature_recycle_bin)) {
                    Text(
                        text = stringResource(R.string.trash_retention_keep),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.days_count,
                            retentionDays,
                            retentionDays,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    RetentionSlider(
                        value = retentionDays,
                        onValueChange = onRetentionChange,
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.about)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.app_full_name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.settings_about_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (version.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.version_label, version),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivilegeStatusBlock(
    privilege: PrivilegeState,
    onRequestShizuku: () -> Unit,
    onRequestRoot: () -> Unit,
    onRefresh: () -> Unit,
) {
    val shizukuStatus = when {
        !privilege.shizukuInstalled -> stringResource(R.string.shizuku_status_not_installed)
        !privilege.shizukuRunning -> stringResource(R.string.shizuku_status_not_running)
        !privilege.shizukuPermission -> stringResource(R.string.shizuku_status_permission)
        privilege.shizukuAsRoot -> stringResource(R.string.shizuku_status_connected_root)
        else -> stringResource(R.string.shizuku_status_connected)
    }
    val shizukuColor = when {
        !privilege.shizukuInstalled || !privilege.shizukuRunning -> MaterialTheme.colorScheme.error
        !privilege.shizukuPermission -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val shizukuDetail = buildList {
        privilege.shizukuVersion?.let { add(stringResource(R.string.shizuku_detail_api, it)) }
        privilege.shizukuUid?.let { add(stringResource(R.string.shizuku_detail_uid, it)) }
    }.joinToString(" · ")

    val rootStatus = when {
        privilege.rootChecking -> stringResource(R.string.root_status_checking)
        privilege.rootGranted -> stringResource(R.string.root_status_granted)
        privilege.rootSuFound -> stringResource(R.string.root_status_detected)
        else -> stringResource(R.string.root_status_not_found)
    }
    val rootColor = when {
        privilege.rootChecking -> MaterialTheme.colorScheme.tertiary
        privilege.rootGranted -> MaterialTheme.colorScheme.primary
        privilege.rootSuFound -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.shizuku_status),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = shizukuStatus,
                style = MaterialTheme.typography.labelMedium,
                color = shizukuColor,
            )
        }
        if (shizukuDetail.isNotEmpty()) {
            Text(
                text = shizukuDetail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (privilege.shizukuRunning && !privilege.shizukuPermission) {
            TextButton(onClick = onRequestShizuku) {
                Text(stringResource(R.string.shizuku_action_grant))
            }
        }
        if (!privilege.shizukuInstalled) {
            Text(
                text = stringResource(R.string.settings_shizuku_install_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.root_status),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = rootStatus,
                style = MaterialTheme.typography.labelMedium,
                color = rootColor,
            )
        }
        if (!privilege.rootGranted) {
            TextButton(
                onClick = onRequestRoot,
                enabled = !privilege.rootChecking,
            ) {
                Text(
                    stringResource(
                        if (privilege.rootChecking) R.string.root_action_waiting else R.string.root_action_request,
                    ),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onRefresh) {
            Text(stringResource(R.string.refresh_status))
        }
    }
}

@Composable
private fun PrivatizedSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RetentionSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderValue by remember(value) {
        mutableFloatStateOf(value.coerceIn(MIN_RETENTION, MAX_RETENTION).toFloat())
    }
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        valueRange = MIN_RETENTION.toFloat()..MAX_RETENTION.toFloat(),
        steps = MAX_RETENTION - MIN_RETENTION - 1,
        onValueChangeFinished = { onValueChange(sliderValue.roundToInt()) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.days_count,
                MIN_RETENTION,
                MIN_RETENTION,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = pluralStringResource(
                R.plurals.days_count,
                MAX_RETENTION,
                MAX_RETENTION,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun rememberVersion(context: Context): String = remember(context) {
    @Suppress("DEPRECATION")
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}

private const val MIN_RETENTION = 1
private const val MAX_RETENTION = 90
