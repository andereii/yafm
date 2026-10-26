package com.anderei.yafm.ui.explorer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anderei.yafm.data.AccessMode
import com.anderei.yafm.data.PrivilegeManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccessSheet(
    state: ExplorerState,
    onDismiss: () -> Unit,
    onSelectMode: (AccessMode) -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestRoot: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAllFilesSettings: () -> Unit,
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Access & privileges",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh privilege status")
                }
            }

            Text(
                text = "Android hides Android/data and Android/obb from most apps. " +
                    "Shizuku adds ADB-shell access; Magisk, KernelSU or APatch root unlocks " +
                    "the whole filesystem, including /data and /system.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Access mode",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OptionButtonGroup(
                options = listOf(
                    GroupOption(AccessMode.STANDARD, "Standard", Icons.Rounded.PhoneAndroid),
                    GroupOption(AccessMode.SHIZUKU, "Shizuku", Icons.Rounded.Terminal),
                    GroupOption(AccessMode.ROOT, "Root", Icons.Rounded.Shield),
                ),
                selected = state.accessMode,
                onSelect = onSelectMode,
            )

            Spacer(Modifier.height(20.dp))
            ShizukuCard(
                state = state,
                onRequestPermission = onRequestShizuku,
                onOpenShizuku = { openShizuku(context) },
            )

            Spacer(Modifier.height(12.dp))
            RootCard(state = state, onRequestRoot = onRequestRoot)

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Root mode can modify system files. YAFM blocks destructive operations " +
                    "on /, /system, /data and other top-level system folders, but always " +
                    "double-check what you delete.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.accessMode == AccessMode.STANDARD) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenAllFilesSettings) {
                    Text("Open All files access settings")
                }
            }
        }
    }
}

@Composable
private fun ShizukuCard(
    state: ExplorerState,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
    val status: String
    val statusColor: Color
    val detail: String
    when {
        !state.shizukuInstalled -> {
            status = "Not installed"
            statusColor = MaterialTheme.colorScheme.error
            detail = "Install Shizuku to browse Android/data and Android/obb."
        }

        !state.shizukuRunning -> {
            status = "Installed, not running"
            statusColor = MaterialTheme.colorScheme.error
            detail = "Start Shizuku from its app, then grant permission."
        }

        !state.shizukuPermission -> {
            status = "Permission required"
            statusColor = MaterialTheme.colorScheme.tertiary
            detail = "YAFM needs permission to use Shizuku's shell."
        }

        else -> {
            status = if (state.shizukuAsRoot) "Connected · root" else "Connected · ADB shell"
            statusColor = MaterialTheme.colorScheme.primary
            detail = buildString {
                state.shizukuVersion?.let { append("API $it") }
                state.shizukuUid?.let {
                    if (isNotEmpty()) append(" · ")
                    append("uid $it")
                }
                if (state.shizukuAsRoot) append(" · full filesystem access")
            }
        }
    }

    PrivilegeCard(
        title = "Shizuku",
        subtitle = "ADB-level shell access",
        icon = Icons.Rounded.Terminal,
        status = status,
        statusColor = statusColor,
        detail = detail,
        actions = {
            when {
                !state.shizukuRunning || state.shizukuInstalled -> {
                    FilledTonalButton(onClick = onOpenShizuku) {
                        Text(if (state.shizukuInstalled) "Open Shizuku" else "Get Shizuku")
                    }
                }
            }
            if (state.shizukuRunning && !state.shizukuPermission) {
                FilledTonalButton(onClick = onRequestPermission) {
                    Text("Grant permission")
                }
            }
        },
    )
}

@Composable
private fun RootCard(
    state: ExplorerState,
    onRequestRoot: () -> Unit,
) {
    val status: String
    val statusColor: Color
    val detail: String
    when {
        state.rootChecking -> {
            status = "Requesting…"
            statusColor = MaterialTheme.colorScheme.tertiary
            detail = "Approve the root request on your device."
        }

        state.rootGranted -> {
            status = "Granted"
            statusColor = MaterialTheme.colorScheme.primary
            detail = "su is available. You can browse /, /data and /system."
        }

        state.rootSuFound -> {
            status = "su detected"
            statusColor = MaterialTheme.colorScheme.tertiary
            detail = "Your root manager will ask for approval when you request root."
        }

        else -> {
            status = "su not detected"
            statusColor = MaterialTheme.colorScheme.error
            detail = "No su binary was found. In KernelSU Next, open the Superuser screen, " +
                "search for YAFM and enable its Superusuario toggle, restart YAFM, then " +
                "request root again."
        }
    }

    PrivilegeCard(
        title = "Root",
        subtitle = "Magisk · KernelSU · APatch",
        icon = Icons.Rounded.Shield,
        status = status,
        statusColor = statusColor,
        detail = detail,
        actions = {
            if (!state.rootGranted) {
                FilledTonalButton(onClick = onRequestRoot, enabled = !state.rootChecking) {
                    Text(if (state.rootChecking) "Waiting for approval…" else "Request root")
                }
            } else {
                Text(
                    text = "Full filesystem access is active",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun PrivilegeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    status: String,
    statusColor: Color,
    detail: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

private fun openShizuku(context: Context) {
    val launchIntent = context.packageManager
        .getLaunchIntentForPackage(PrivilegeManager.SHIZUKU_PACKAGE)
    if (launchIntent != null) {
        runCatching { context.startActivity(launchIntent) }
    } else {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")),
            )
        }
    }
}
