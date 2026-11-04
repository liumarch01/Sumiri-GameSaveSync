package com.sumiri.gss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.sumiri.gss.ui.MainScreen
import com.sumiri.gss.ui.theme.GameSSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameSSTheme {
                MainScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}