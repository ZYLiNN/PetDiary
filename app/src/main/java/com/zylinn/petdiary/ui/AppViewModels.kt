package com.zylinn.petdiary.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zylinn.petdiary.AppContainer
import com.zylinn.petdiary.PetDiaryApplication

@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as PetDiaryApplication).container

/** Creates a ViewModel that needs the [AppContainer], without a DI framework. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
