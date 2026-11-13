package app.nodx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Builds activity-scoped ViewModel factories without a DI framework. */
inline fun <reified T : ViewModel> vmFactory(crossinline create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
