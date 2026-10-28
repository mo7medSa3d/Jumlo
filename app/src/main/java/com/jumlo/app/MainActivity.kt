package com.jumlo.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.intake.IntakeController
import com.jumlo.app.ui.navigation.JumloNavHost
import com.jumlo.app.ui.theme.JumloTheme
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.rememberSystemReducedMotion
import com.jumlo.app.ui.util.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val startedAt = SystemClock.elapsedRealtime()
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Slow storage never holds the operating-system splash indefinitely.
        // The application owns its loading states after this short launch.
        splash.setKeepOnScreenCondition {
            SystemClock.elapsedRealtime() - startedAt < 900L &&
                (!appViewModel.settings.value.isLoaded || !appViewModel.workspaceReady.value)
        }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate().alpha(0f).setDuration(160L)
                .withEndAction { provider.remove() }.start()
        }
        if (savedInstanceState == null) receiveIntent(intent)
        setContent {
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            LaunchedEffect(settings.isLoaded, settings.language) {
                if (settings.isLoaded) {
                    val target = LocaleListCompat.forLanguageTags(settings.language.code)
                    if (AppCompatDelegate.getApplicationLocales() != target) {
                        AppCompatDelegate.setApplicationLocales(target)
                    }
                }
            }
            val systemReduced = rememberSystemReducedMotion()
            val sound = remember { UiSoundPlayer(applicationContext) }
            DisposableEffect(sound) { onDispose { sound.release() } }
            JumloTheme(settings.themeMode, settings.language, settings.accentColor, settings.fontScale) {
                CompositionLocalProvider(
                    LocalAppHapticsEnabled provides settings.hapticFeedbackOnCommit,
                    LocalHapticIntensity provides settings.hapticIntensity,
                    LocalAppSoundsEnabled provides settings.soundsEnabled,
                    LocalUiSoundPlayer provides sound,
                    LocalReducedMotion provides (systemReduced || settings.respectReducedMotion)
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        JumloNavHost(appViewModel)
                    }
                }
            }
        }
    }

    private fun receiveIntent(incoming: Intent?) {
        if (incoming == null) return
        // A third-party share can carry a malformed Parcelable or revoked URI.
        // Keep the Activity alive and present the failure through the normal UI.
        try {
            appViewModel.openNotificationJob(incoming.getStringExtra("job_id"))
            if (incoming.action in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
                appViewModel.intake(IntakeController.collect(incoming), externalShare = true)
            }
        } catch (_: RuntimeException) {
            appViewModel.notifyMessage("INVALID_SHARED_CONTENT")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveIntent(intent)
    }
}
