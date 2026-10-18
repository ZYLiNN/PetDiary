package com.zylinn.petdiary.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The tabbed main screen (pets / reminders / stats / settings). */
@Serializable
data object Main : NavKey

/** Add a pet (petId == null) or edit an existing one. */
@Serializable
data class PetEditor(val petId: String? = null) : NavKey

/** Trash: pets (and later records) deleted within the last 7 days. */
@Serializable
data object Trash : NavKey

/** A pet's daily records, opened on [dayEpochDay] (today when null). */
@Serializable
data class PetDetail(val petId: String, val dayEpochDay: Long? = null) : NavKey

/** New record(s) of [type] for [petIds] on [dateEpochDay], or edit [recordId]. */
@Serializable
data class RecordEditor(
    val petIds: List<String>,
    val type: String,
    val dateEpochDay: Long,
    val recordId: String? = null,
) : NavKey

/** Record search (S8): all pets, or pre-filtered to [petId] when opened from a pet's page. */
@Serializable
data class Search(val petId: String? = null) : NavKey
