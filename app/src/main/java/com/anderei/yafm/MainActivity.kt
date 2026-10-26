package com.anderei.yafm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.anderei.yafm.data.SettingsStore
import com.anderei.yafm.data.withAppLanguage
import com.anderei.yafm.ui.YafmApp
import com.anderei.yafm.ui.permission.PermissionScreen
import com.anderei.yafm.ui.theme.YafmTheme

class MainActivity : ComponentActivity() {

    private var hasStorageAccess by mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        val language = SettingsStore(newBase).language.value
        super.attachBaseContext(newBase.withAppLanguage(language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasStorageAccess = checkStorageAccess()

        setContent {
            YafmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (hasStorageAccess) {
                        YafmApp()
                    } else {
                        PermissionScreen(onRequestAccess = ::openAllFilesSettings)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasStorageAccess = checkStorageAccess()
    }

    private fun checkStorageAccess(): Boolean = Environment.isExternalStorageManager()

    private fun openAllFilesSettings() {
        val packageUri = Uri.parse("package:$packageName")
        val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
        runCatching { startActivity(appIntent) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }
}
