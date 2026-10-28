package com.tkdpro

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import com.tkdpro.core.datastore.ThemeMode
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkdpro.core.designsystem.theme.TkdTheme
import com.tkdpro.core.ui.Formatter
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.navigation.TkdRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var stoppedAt: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.state.value == null }
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val s = state ?: return@setContent
            val locale = LocalConfiguration.current.locales[0]
            val formatter = Formatter(locale, s.settings.units, DateFormat.is24HourFormat(this))
            val dark = when (s.settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // System bar icons follow the app theme, not only the device theme.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            CompositionLocalProvider(LocalFormatter provides formatter) {
                TkdTheme(themeMode = s.settings.themeMode) {
                    TkdRoot(state = s, onUnlocked = viewModel::unlock, onOnboardingDone = viewModel::completeOnboarding)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Pick up changes made on the computer whenever the app comes to the front.
        viewModel.syncOnForeground()
        val stopped = stoppedAt ?: return
        stoppedAt = null
        if (SystemClock.elapsedRealtime() - stopped > RELOCK_AFTER_MS) viewModel.lock()
    }

    private companion object {
        const val RELOCK_AFTER_MS = 60_000L
    }
}
