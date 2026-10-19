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

    private val _cloudSyncEnabled = MutableStateFlow(prefs.getBoolean(KEY_CLOUD, false))
    /** Cloud backup & sync is opt-in (off until the user agrees on the consent dialog). */
    val cloudSyncEnabled: StateFlow<Boolean> = _cloudSyncEnabled.asStateFlow()

    fun setCloudSyncEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_CLOUD, on).apply()
        _cloudSyncEnabled.value = on
    }

    private val _displayName = MutableStateFlow(prefs.getString(KEY_NAME, "") ?: "")
    /** The name family members see next to my records (empty = not set yet). */
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    fun setDisplayName(name: String) {
        val clean = name.trim().take(30)
        prefs.edit().putString(KEY_NAME, clean).apply()
        _displayName.value = clean
    }

    private companion object {
        const val KEY_NAME = "display_name"
        const val KEY_CLOUD = "cloud_sync_enabled"
        const val KEY_THEME = "theme_mode"
    }
}
