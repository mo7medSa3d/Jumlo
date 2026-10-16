package com.universalutilities.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalutilities.app.ui.AppViewModel
import com.universalutilities.app.ui.intake.IntakeController
import com.universalutilities.app.ui.navigation.UniversalUtilitiesNavHost
import com.universalutilities.app.ui.theme.UniversalUtilitiesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint class MainActivity:AppCompatActivity() {
    private val appViewModel:AppViewModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        if(savedInstanceState==null && intent?.action in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE)) appViewModel.intake(IntakeController.collect(intent))
        setContent {
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            LaunchedEffect(settings.language) {
                val target=LocaleListCompat.forLanguageTags(settings.language.code)
                if(AppCompatDelegate.getApplicationLocales()!=target) AppCompatDelegate.setApplicationLocales(target)
            }
            UniversalUtilitiesTheme(settings.themeMode,settings.language) {
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) { UniversalUtilitiesNavHost(appViewModel) }
            }
        }
    }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent);setIntent(intent)
        if(intent.action in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE)) appViewModel.intake(IntakeController.collect(intent))
    }
}
