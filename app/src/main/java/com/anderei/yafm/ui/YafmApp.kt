package com.anderei.yafm.ui

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anderei.yafm.data.AccessMode
import com.anderei.yafm.ui.archive.CompressedFilesRoute
import com.anderei.yafm.ui.cleaner.CleanerRoute
import com.anderei.yafm.ui.explorer.FileExplorerRoute
import com.anderei.yafm.ui.explorer.FileExplorerViewModel
import com.anderei.yafm.ui.feature.FeaturePlaceholderScreen
import com.anderei.yafm.ui.home.HomeFeature
import com.anderei.yafm.ui.home.HomeRoute
import com.anderei.yafm.ui.home.HomeViewModel
import com.anderei.yafm.ui.sender.SenderRoute
import com.anderei.yafm.ui.settings.SettingsScreen
import com.anderei.yafm.ui.trash.TrashRoute

private enum class AppScreen {
    HOME,
    EXPLORER,
    TRASH,
    CLEANER,
    COMPRESSED,
    SENDER,
    SETTINGS,
    FEATURE,
}

@Composable
fun YafmApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val explorerViewModel: FileExplorerViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel()

    var screenName by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
    var featureId by rememberSaveable { mutableStateOf<String?>(null) }
    val screen = AppScreen.valueOf(screenName)

    BackHandler(
        enabled = screen == AppScreen.TRASH ||
            screen == AppScreen.COMPRESSED ||
            screen == AppScreen.SENDER ||
            screen == AppScreen.SETTINGS ||
            screen == AppScreen.FEATURE,
    ) {
        screenName = AppScreen.HOME.name
    }

    val trash by homeViewModel.trash.collectAsStateWithLifecycle()
    val retentionDays by homeViewModel.retentionDays.collectAsStateWithLifecycle()
    val language by homeViewModel.language.collectAsStateWithLifecycle()
    val explorerState by explorerViewModel.state.collectAsStateWithLifecycle()
    val privilegeState by explorerViewModel.privilegeState.collectAsStateWithLifecycle()
    val useRootForAppData by homeViewModel.useRootForAppData.collectAsStateWithLifecycle()
    val correctSelinuxContext by homeViewModel.correctSelinuxContext.collectAsStateWithLifecycle()

    val backProgress = remember { Animatable(0f) }
    val cancelScope = rememberCoroutineScope()
    val canGoBack = screen != AppScreen.HOME

    val backAction: () -> Unit = {
        if (screen == AppScreen.EXPLORER) {
            when {
                explorerState.isSelectionMode -> explorerViewModel.clearSelection()
                explorerState.searchActive -> explorerViewModel.closeSearch()
                explorerState.canGoUp -> explorerViewModel.goUp()
                else -> screenName = AppScreen.HOME.name
            }
        } else if (screen != AppScreen.HOME) {
            screenName = AppScreen.HOME.name
        }
    }
    val currentBackAction by rememberUpdatedState(backAction)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(enabled = canGoBack) { progress ->
            try {
                progress.collect { event -> backProgress.snapTo(event.progress) }
                backProgress.snapTo(0f)
                currentBackAction()
            } catch (e: CancellationException) {
                cancelScope.launch {
                    backProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    )
                }
                throw e
            }
        }
    } else {
        BackHandler(enabled = canGoBack) { currentBackAction() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val progress = backProgress.value
                scaleX = 1f - 0.06f * progress
                scaleY = 1f - 0.06f * progress
                translationX = size.width * 0.05f * progress
                alpha = 1f - 0.25f * progress
                shape = RoundedCornerShape((28f * progress).dp)
                clip = progress > 0f
            },
    ) {
        when (screen) {
        AppScreen.HOME -> HomeRoute(
            explorerViewModel = explorerViewModel,
            homeViewModel = homeViewModel,
            onOpenExplorer = { screenName = AppScreen.EXPLORER.name },
            onOpenTrash = { screenName = AppScreen.TRASH.name },
            onOpenSettings = { screenName = AppScreen.SETTINGS.name },
            onOpenFeature = { feature ->
                if (feature == HomeFeature.CLEANER) {
                    screenName = AppScreen.CLEANER.name
                } else if (feature == HomeFeature.COMPRESSED) {
                    screenName = AppScreen.COMPRESSED.name
                } else if (feature == HomeFeature.SENDER) {
                    screenName = AppScreen.SENDER.name
                } else {
                    featureId = feature.name
                    screenName = AppScreen.FEATURE.name
                }
            },
        )

        AppScreen.EXPLORER -> FileExplorerRoute(
            viewModel = explorerViewModel,
            onExitToHome = { screenName = AppScreen.HOME.name },
        )

        AppScreen.CLEANER -> CleanerRoute(
            explorerViewModel = explorerViewModel,
            onBack = { screenName = AppScreen.HOME.name },
        )

        AppScreen.COMPRESSED -> CompressedFilesRoute(
            explorerViewModel = explorerViewModel,
            onBack = { screenName = AppScreen.HOME.name },
        )

        AppScreen.SENDER -> SenderRoute(
            onBack = { screenName = AppScreen.HOME.name },
        )

        AppScreen.TRASH -> TrashRoute(
            entries = trash,
            retentionDays = retentionDays,
            onRestore = explorerViewModel::restoreTrash,
            onDelete = explorerViewModel::deleteTrash,
            onEmpty = explorerViewModel::emptyTrash,
            onCleanup = explorerViewModel::cleanupTrash,
            onOpenSettings = { screenName = AppScreen.SETTINGS.name },
            onBack = { screenName = AppScreen.HOME.name },
        )

        AppScreen.SETTINGS -> SettingsScreen(
            language = language,
            retentionDays = retentionDays,
            accessMode = explorerState.accessMode,
            privilege = privilegeState,
            useRootForAppData = useRootForAppData,
            correctSelinuxContext = correctSelinuxContext,
            onLanguageChange = { option ->
                homeViewModel.setLanguage(option)
                activity?.recreate()
            },
            onRetentionChange = homeViewModel::setTrashRetention,
            onSelectAccessMode = explorerViewModel::selectAccessMode,
            onRequestShizuku = explorerViewModel::requestShizukuPermission,
            onRequestRoot = { explorerViewModel.selectAccessMode(AccessMode.ROOT) },
            onRefreshPrivileges = explorerViewModel::refreshPrivileges,
            onUseRootForAppDataChange = homeViewModel::setUseRootForAppData,
            onCorrectSelinuxContextChange = homeViewModel::setCorrectSelinuxContext,
            onBack = { screenName = AppScreen.HOME.name },
        )

        AppScreen.FEATURE -> FeaturePlaceholderScreen(
            feature = HomeFeature.fromId(featureId) ?: HomeFeature.ALL_TOOLS,
            onBack = { screenName = AppScreen.HOME.name },
        )
    }
    }
}
