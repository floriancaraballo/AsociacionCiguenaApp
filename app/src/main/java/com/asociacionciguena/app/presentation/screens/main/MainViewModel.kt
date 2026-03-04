package com.asociacionciguena.app.presentation.screens.main

import androidx.lifecycle.ViewModel
import com.asociacionciguena.app.data.datasource.local.PreferencesDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource
) : ViewModel() {

    val isOnboardingCompleted: Flow<Boolean> = preferencesDataSource.isOnboardingCompleted()
}