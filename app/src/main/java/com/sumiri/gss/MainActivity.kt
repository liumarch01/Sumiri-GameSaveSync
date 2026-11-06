package com.sumiri.gss

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.sumiri.gss.data.local.PreferencesManager
import com.sumiri.gss.ui.MainScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 屏蔽系统的第三方强行深色/反色（针对魅族 Flyme、小米等）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
        }

        val prefsManager = PreferencesManager(this)
        applyThemeMode(prefsManager.getThemeMode())

        enableEdgeToEdge()
        setContent {
            val themeMode = prefsManager.getThemeMode()
            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            // 同步底层 Window 的状态栏/导航栏深浅标志，彻底封杀系统二次绘制
            SideEffectWindowInsets(isDark)

            MainScreen(
                modifier = Modifier.fillMaxSize(),
                onThemeModeChanged = { mode ->
                    applyThemeMode(mode)
                }
            )
        }
    }

    private fun SideEffectWindowInsets(isDark: Boolean) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = !isDark
        windowInsetsController.isAppearanceLightNavigationBars = !isDark
    }

    private fun applyThemeMode(mode: String) {
        val nightMode = when (mode) {
            "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO
            "DARK" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
