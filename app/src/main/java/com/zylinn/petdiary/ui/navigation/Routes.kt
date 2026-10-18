package com.zylinn.petdiary.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The tabbed main screen (pets / reminders / stats / settings). */
@Serializable
data object Main : NavKey

/** Add a pet (petId == null) or edit an existing one. */
@Serializable
data class PetEditor(val petId: String? = null) : NavKey
