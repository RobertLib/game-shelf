package cz.gameshelf.app.ui.common

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import cz.gameshelf.app.GameShelfApplication
import cz.gameshelf.app.di.AppContainer

/** Access to the manual DI graph from `viewModelFactory { initializer { … } }`. */
val CreationExtras.appContainer: AppContainer
    get() = (checkNotNull(this[APPLICATION_KEY]) as GameShelfApplication).container
