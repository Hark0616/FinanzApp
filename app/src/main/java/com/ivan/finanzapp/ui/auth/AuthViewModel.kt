package com.ivan.finanzapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.finanzapp.data.remote.SupabaseAuthManager
import dagger.hilt.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: SupabaseAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun toggleMode() {
        _uiState.update { it.copy(isLoginMode = !it.isLoginMode, errorMessage = null, successMessage = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun onSubmit(onSuccess: () -> Unit) {
        val currentState = _uiState.value
        val email = currentState.email.trim()
        val password = currentState.password

        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El correo y la contraseña son obligatorios.") }
            return
        }

        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "La contraseña debe tener al menos 6 caracteres.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            if (currentState.isLoginMode) {
                // Iniciar Sesión
                authManager.signIn(email, password)
                    .onSuccess {
                        _uiState.update { it.copy(isLoading = false, successMessage = "¡Sesión iniciada con éxito!") }
                        onSuccess()
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = translateAuthError(error.message ?: "Error desconocido")
                            )
                        }
                    }
            } else {
                // Registrarse
                authManager.signUp(email, password)
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                successMessage = "¡Registro exitoso! Por favor verifica tu correo si es necesario o inicia sesión."
                            )
                        }
                        // Opcionalmente iniciar sesión directo o cambiar a modo login
                        toggleMode()
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = translateAuthError(error.message ?: "Error al registrarse.")
                            )
                        }
                    }
            }
        }
    }

    private fun translateAuthError(error: String): String {
        return when {
            error.contains("Invalid login credentials", ignoreCase = true) -> "Correo o contraseña incorrectos."
            error.contains("Email not confirmed", ignoreCase = true) -> "Debes confirmar tu correo electrónico antes de ingresar."
            error.contains("User already exists", ignoreCase = true) -> "Ya existe una cuenta con este correo electrónico."
            error.contains("Password should be", ignoreCase = true) -> "La contraseña es muy débil."
            error.contains("NetworkError", ignoreCase = true) -> "Error de conexión. Revisa tu internet."
            else -> "Ocurrió un problema: $error"
        }
    }
}

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isLoginMode: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
