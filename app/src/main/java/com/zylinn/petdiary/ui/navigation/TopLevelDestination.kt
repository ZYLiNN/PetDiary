package com.zylinn.petdiary.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.zylinn.petdiary.R

enum class TopLevelDestination(
    @StringRes val label: Int,
    @StringRes val title: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Pets(R.string.tab_pets, R.string.title_pets, Icons.Filled.Pets, Icons.Outlined.Pets),
    Reminders(R.string.tab_reminders, R.string.title_reminders, Icons.Filled.Notifications, Icons.Outlined.Notifications),
    Stats(R.string.tab_stats, R.string.title_stats, Icons.Filled.Insights, Icons.Outlined.Insights),
    Settings(R.string.tab_settings, R.string.title_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}
