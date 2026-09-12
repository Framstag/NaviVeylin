package com.naviveylin

import android.content.Intent
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.car.app.activity.CarAppActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.naviveylin.data.AmbientLightMonitor
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.DarkModePreference
import com.naviveylin.data.MapStorageManager
import com.naviveylin.navigation.NavGraph
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.share.SharedLocationParser
import com.naviveylin.ui.theme.NaviVeylinTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var storageManager: MapStorageManager

    @Inject
    lateinit var darkModeController: DarkModeController

    @Inject
    lateinit var sharedLocationHandler: SharedLocationHandler

    private val sharedLocationParser = SharedLocationParser()

    private lateinit var ambientLightMonitor: AmbientLightMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android Automotive OS: the phone UI is not the entry point there —
        // the car experience runs through CarAppActivity (template host).
        // Forward the incoming intent (deep links / shares) so the car session
        // can start navigation to the parsed destination.
        if (AutomotiveDevice.isAutomotive(this)) {
            val carIntent = Intent(this, CarAppActivity::class.java)
            carIntent.action = intent?.action
            carIntent.data = intent?.data
            intent?.getStringExtra(Intent.EXTRA_TEXT)?.let {
                carIntent.putExtra(Intent.EXTRA_TEXT, it)
            }
            intent?.getStringExtra("android.intent.extra.QUERY")?.let {
                carIntent.putExtra("android.intent.extra.QUERY", it)
            }
            startActivity(carIntent)
            finish()
            return
        }

        enableEdgeToEdge()

        // Phone → car deep links / shares: parse and queue for the map screen.
        handleSharedIntent(intent)

        // Ambient light sensor: active only when the preference is Automatic
        // AND the option is enabled; stopped on activity stop (foreground-only
        // listening, battery). Reports null on stop so the environment source
        // reverts to the system night mode signal.
        ambientLightMonitor = AmbientLightMonitor(
            sensorManager = getSystemService(SensorManager::class.java),
            onClassification = { dark -> darkModeController.setSensorDark(dark) }
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(darkModeController.preference, darkModeController.sensorEnabled) { pref, enabled ->
                    pref == DarkModePreference.AUTOMATIC && enabled
                }.collect { active ->
                    Log.d(TAG, "ambient light gating: active=$active (pref=${darkModeController.preference.value}, option=${darkModeController.sensorEnabled.value})")
                    if (active) ambientLightMonitor.start() else ambientLightMonitor.stop()
                }
            }
        }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
    }

    /**
     * Parse a shared/deep-link intent and queue it for the map screen.
     * Coordinates land in the candidate/details flow; address text runs search.
     */
    private fun handleSharedIntent(intent: Intent?) {
        if (intent == null) return
        lifecycleScope.launch {
            val request = sharedLocationParser.parse(intent)
            if (request != null) {
                Log.d(TAG, "Shared location parsed: $request")
                sharedLocationHandler.submit(request)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Foreground-only sensor listening (battery); restarted by the gating
        // collector when the activity returns to the foreground.
        if (::ambientLightMonitor.isInitialized) {
            ambientLightMonitor.stop()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
