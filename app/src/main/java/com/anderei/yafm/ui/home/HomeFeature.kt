package com.anderei.yafm.ui.home

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Send
import androidx.compose.ui.graphics.vector.ImageVector
import com.anderei.yafm.R

enum class HomeFeature(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val available: Boolean,
) {
    CLEANER(R.string.feature_cleaner, Icons.Rounded.CleaningServices, true),
    CLOUD(R.string.feature_cloud, Icons.Rounded.Cloud, false),
    COMPRESSED(R.string.feature_compressed, Icons.Rounded.FolderZip, true),
    SENDER(R.string.feature_sender, Icons.Rounded.Send, true),
    TEXT_EDITOR(R.string.feature_text_editor, Icons.Rounded.EditNote, false),
    NETWORK(R.string.feature_network, Icons.Rounded.Lan, false),
    RECYCLE_BIN(R.string.feature_recycle_bin, Icons.Rounded.Delete, true),
    DOWNLOADER(R.string.feature_downloader, Icons.Rounded.Download, false),
    VIEW_ON_PC(R.string.feature_view_on_pc, Icons.Rounded.Computer, false),
    ALL_TOOLS(R.string.feature_all_tools, Icons.Rounded.Apps, false),
    ;

    companion object {
        fun fromId(id: String?): HomeFeature? = entries.firstOrNull { it.name == id }
    }
}
