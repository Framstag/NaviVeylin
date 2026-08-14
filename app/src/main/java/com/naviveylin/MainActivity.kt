package com.naviveylin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.MapStorageManager
import com.naviveylin.navigation.NavGraph
import com.naviveylin.ui.theme.NaviVeylinTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var storageManager: MapStorageManager

    @Inject
    lateinit var darkModeController: DarkModeController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Environment dimming signal (system night mode today; car later).
            // Fed into DarkModeController — single extension point for future sources.
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(systemDark) {
                darkModeController.setEnvironmentDark(systemDark)
            }

            val darkPresentation by darkModeController.isDarkPresentation.collectAsState()
            NaviVeylinTheme(darkTheme = darkPresentation) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph(storageManager = storageManager)
                }
            }
        }
    }
}
