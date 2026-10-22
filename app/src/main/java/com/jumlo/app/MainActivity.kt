package com.jumlo.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.JumloSplash
import com.jumlo.app.ui.intake.IntakeController
import com.jumlo.app.ui.navigation.JumloNavHost
import com.jumlo.app.ui.theme.JumloTheme
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.rememberSystemReducedMotion
import com.jumlo.app.ui.util.LocalAppHapticsEnabled
import com.jumlo.app.ui.util.LocalAppSoundsEnabled
import com.jumlo.app.ui.util.LocalHapticIntensity
import com.jumlo.app.ui.util.LocalUiSoundPlayer
import com.jumlo.app.ui.util.UiSoundPlayer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint class MainActivity:AppCompatActivity() {
    private val appViewModel:AppViewModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        val launchStartedAt = SystemClock.elapsedRealtime()
        splashScreen.setKeepOnScreenCondition {
            SystemClock.elapsedRealtime() - launchStartedAt < 180L && !appViewModel.workspaceReady.value
        }
        enableEdgeToEdge()
        if(savedInstanceState==null && intent?.action in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE)) {
            runCatching { IntakeController.collect(intent) }.getOrNull()?.let {
                appViewModel.intake(it, externalShare=true)
            }
        }
        if(savedInstanceState==null) appViewModel.openNotificationJob(intent?.getStringExtra("job_id"))
        val coldLauncher = savedInstanceState == null && intent?.action == Intent.ACTION_MAIN
        setContent {
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            val workspaceReady by appViewModel.workspaceReady.collectAsStateWithLifecycle()
            LaunchedEffect(settings.isLoaded,settings.language) {
                if(!settings.isLoaded) return@LaunchedEffect
                val target=LocaleListCompat.forLanguageTags(settings.language.code)
                if(AppCompatDelegate.getApplicationLocales()!=target) AppCompatDelegate.setApplicationLocales(target)
            }
            val systemReduced=rememberSystemReducedMotion()
            val sound=remember { UiSoundPlayer(applicationContext) }
            DisposableEffect(sound) {onDispose {sound.release()} }
            JumloTheme(settings.themeMode,settings.language,settings.accentColor,settings.fontScale) {
                CompositionLocalProvider(LocalAppHapticsEnabled provides settings.hapticFeedbackOnCommit,
                    LocalHapticIntensity provides settings.hapticIntensity,
                    LocalAppSoundsEnabled provides settings.soundsEnabled,
                    LocalUiSoundPlayer provides sound,
                    LocalReducedMotion provides (settings.respectReducedMotion || systemReduced)) {
                    var showLaunch by rememberSaveable { mutableStateOf(true) }
                    val ready = settings.isLoaded && workspaceReady
                    val reduceMotion = LocalReducedMotion.current
                    LaunchedEffect(ready, reduceMotion) {
                        if (ready) {
                            if (coldLauncher && !reduceMotion) {
                                val elapsed = SystemClock.elapsedRealtime() - launchStartedAt
                                val remaining = (650L - elapsed).coerceAtLeast(0L)
                                if (remaining > 0L) delay(remaining)
                            }
                            showLaunch = false
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        if (ready) {
                            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                JumloNavHost(appViewModel)
                            }
                        }
                        AnimatedVisibility(
                            visible = !ready || showLaunch,
                            enter = EnterTransition.None,
                            exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(180))
                        ) {
                            JumloSplash()
                        }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent);setIntent(intent)
        appViewModel.openNotificationJob(intent.getStringExtra("job_id"))
        if(intent.action in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE)) {
            runCatching { IntakeController.collect(intent) }.getOrNull()?.let {
                appViewModel.intake(it, externalShare=true)
            }
        }
    }
}
