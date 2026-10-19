package com.zylinn.petdiary.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("跟隨系統"), LIGHT("淺色"), DARK("深色") }

/** Small user preferences kept in SharedPreferences (theme). Changes apply immediately and survive restarts (P6-01). */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("petdiary_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
    }
}
