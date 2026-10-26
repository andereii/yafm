package com.anderei.yafm

import android.app.Application
import android.content.Context
import com.anderei.yafm.data.BookmarkStore
import com.anderei.yafm.data.RecentStore
import com.anderei.yafm.data.SettingsStore
import com.anderei.yafm.data.StorageRepository
import com.anderei.yafm.data.TrashStore
import com.anderei.yafm.data.archive.ArchiveRepository

class YafmApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settings = SettingsStore(appContext)
    val bookmarks = BookmarkStore(appContext)
    val recents = RecentStore(appContext)
    val trash = TrashStore(appContext, settings)
    val storage = StorageRepository(appContext)
    val archives = ArchiveRepository()
}
