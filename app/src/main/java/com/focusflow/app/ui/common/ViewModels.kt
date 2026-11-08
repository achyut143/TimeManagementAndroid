package com.focusflow.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focusflow.app.AppContainer
import com.focusflow.app.FocusFlowApp

/** Builds a ViewModel factory that hands the app's [AppContainer] to the ViewModel. */
inline fun <reified VM : ViewModel> containerFactory(crossinline create: (AppContainer) -> VM): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { create((this[APPLICATION_KEY] as FocusFlowApp).container) }
    }
