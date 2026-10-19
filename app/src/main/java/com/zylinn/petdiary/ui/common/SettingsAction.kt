package com.zylinn.petdiary.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** The gear in the top bar of every main tab: settings is no longer a tab (P10). */
@Composable
fun SettingsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.testTag("open_settings")) {
        Icon(Icons.Outlined.Settings, contentDescription = "設定")
    }
}
