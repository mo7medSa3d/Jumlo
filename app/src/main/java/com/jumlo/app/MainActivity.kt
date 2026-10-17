package com.jumlo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import com.jumlo.app.ui.components.JumloSplash
import kotlinx.coroutines.delay
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.intake.IntakeController
import com.jumlo.app.ui.navigation.JumloNavHost
import com.jumlo.app.ui.theme.JumloTheme
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.rememberSystemReducedMotion
import com.jumlo.app.ui.util.LocalAppHapticsEnabled
import com.jumlo.app.ui.util.LocalHapticIntensity
import com.jumlo.app.ui.util.LocalAppSoundsEnabled
import com.jumlo.app.ui.util.LocalUiSoundPlayer
import com.jumlo.app.ui.util.UiSoundPlayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint class MainActivity:AppCompatActivity() {
    private val appViewModel:AppViewModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        if(savedInstanceState==null && intent?.action in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE)) appViewModel.intake(IntakeController.collect(intent))
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
                    val ready=settings.isLoaded && workspaceReady
                    LaunchedEffect(ready,systemReduced,settings.respectReducedMotion) {
                        if(ready) {
                            delay(if(systemReduced || settings.respectReducedMotion) 80L else 450L)
                            showLaunch=false
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        if(ready) {
                            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                                JumloNavHost(appViewModel)
                            }
                        }
                        if(!ready || showLaunch) JumloSplash()
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent);setIntent(intent)
        if(intent.action in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE)) appViewModel.intake(IntakeController.collect(intent))
    }
}
