package com.asociacionciguena.app.presentation.screens.admin.pins

data class ChildPin(
    val id: String,
    val name: String,
    val pinCount: Long
)

sealed interface PinManagementUiState {
    data object Loading : PinManagementUiState
    data class Success(val children: List<ChildPin>) : PinManagementUiState
    data class Error(val message: String) : PinManagementUiState
}
