package com.zylinn.petdiary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.data.settings.ThemeMode
import com.zylinn.petdiary.ui.PetDiaryApp
import com.zylinn.petdiary.ui.theme.PetDiaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as PetDiaryApplication).container.settings
        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status / navigation bar icons must follow the chosen theme, not the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            PetDiaryTheme(darkTheme = dark) {
                PetDiaryApp()
            }
        }
    }
}
