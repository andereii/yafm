package com.anderei.yafm.ui.home

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anderei.yafm.YafmApplication
import com.anderei.yafm.data.AppLanguage
import com.anderei.yafm.data.StorageUsage
import com.anderei.yafm.data.StorageVolumeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as YafmApplication).container

    private val _volumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val volumes: StateFlow<List<StorageVolumeInfo>> = _volumes.asStateFlow()

    private val _usage = MutableStateFlow<Map<String, StorageUsage>>(emptyMap())
    val usage: StateFlow<Map<String, StorageUsage>> = _usage.asStateFlow()

    val bookmarks = container.bookmarks.entries
    val recents = container.recents.entries
    val trash = container.trash.entries
    val language = container.settings.language
    val retentionDays = container.settings.trashRetentionDays
    val useRootForAppData = container.settings.useRootForAppData
    val correctSelinuxContext = container.settings.correctSelinuxContext

    private var refreshJob: Job? = null

    init {
        refresh()
        viewModelScope.launch(Dispatchers.IO) {
            container.trash.cleanupExpired()
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val volumes = container.storage.volumes()
            val usage = volumes.mapNotNull { volume ->
                container.storage.usage(volume.root)?.let { volume.id to it }
            }.toMap()
            _volumes.value = volumes
            _usage.value = usage
        }
    }

    fun volumeFor(path: File): StorageVolumeInfo? = container.storage.volumeFor(path)

    fun internalRoot(): File =
        container.storage.internalVolume()?.root ?: Environment.getExternalStorageDirectory()

    fun removeBookmark(path: String) = container.bookmarks.remove(path)

    fun setLanguage(language: AppLanguage) = container.settings.setLanguage(language)

    fun setTrashRetention(days: Int) {
        container.settings.setTrashRetentionDays(days)
        viewModelScope.launch(Dispatchers.IO) {
            container.trash.updateRetention(days)
        }
    }

    fun setUseRootForAppData(enabled: Boolean) =
        container.settings.setUseRootForAppData(enabled)

    fun setCorrectSelinuxContext(enabled: Boolean) =
        container.settings.setCorrectSelinuxContext(enabled)

    fun cleanupTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            container.trash.cleanupExpired()
        }
    }
}
