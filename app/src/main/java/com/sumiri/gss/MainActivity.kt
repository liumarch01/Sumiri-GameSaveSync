package com.sumiri.gss

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.sumiri.gss.data.local.PreferencesManager
import com.sumiri.gss.ui.MainScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 屏蔽系统的第三方强行深色/反色（针对魅族 Flyme、小米等）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
        }

        // 向 AppCompat 框架做原生系统级夜间模式声明
        val prefsManager = PreferencesManager(this)
        applyThemeMode(prefsManager.getThemeMode())

        enableEdgeToEdge()
        setContent {
            MainScreen(
                modifier = Modifier.fillMaxSize(),
                onThemeModeChanged = { mode ->
                    applyThemeMode(mode)
                }
            )
        }
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
