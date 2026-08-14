package com.naviveylin

import androidx.car.app.CarAppService
import androidx.car.app.HostInfo
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.naviveylin.auto.NavigationSession
import com.naviveylin.core.DiagnosticsLog

/**
 * Android Auto entry point in the app module.
 *
 * Delegates to [NavigationSession] from the [:auto] module.
 * Declared here (not in [:auto]) because some Android Auto hosts
 * require the CarAppService to be in the app's base package.
 */
class NaviVeylinCarAppService : CarAppService() {

    override fun onCreate() {
        super.onCreate()
        DiagnosticsLog.log(TAG, "CarAppService created")
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        DiagnosticsLog.log(TAG, sessionCreatedMessage(sessionInfo, getHostInfo()))
        return NavigationSession()
    }

    /**
     * Build the session-creation log line. Extracted for unit testing —
     * [onCreateSession] itself constructs a real [NavigationSession] whose
     * Hilt/native graph cannot load in Robolectric.
     */
    internal fun sessionCreatedMessage(sessionInfo: SessionInfo, host: HostInfo?): String =
        "onCreateSession displayType=${sessionInfo.displayType} " +
            "host=${host?.packageName ?: "unknown"} uid=${host?.uid ?: -1}"

    override fun onDestroy() {
        DiagnosticsLog.log(TAG, "CarAppService destroyed")
        super.onDestroy()
    }

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    companion object {
        private const val TAG = "NaviVeylinCarAppService"
    }
}
