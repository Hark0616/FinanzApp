package com.ivan.finanzapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.finanzapp.data.remote.CloudSyncScheduler
import com.ivan.finanzapp.data.remote.SupabaseAuthManager
import com.ivan.finanzapp.data.remote.SupabaseSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: SupabaseAuthManager,
    private val syncManager: SupabaseSyncManager,
    private val cloudSyncScheduler: CloudSyncScheduler
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
        _uiState.update {
            it.copy(
                isLoginMode = !it.isLoginMode,
                loadingMessage = null,
                errorMessage = null,
                successMessage = null,
                canContinueAfterSyncError = false
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null, canContinueAfterSyncError = false) }
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

        _uiState.update {
            it.copy(
                isLoading = true,
                loadingMessage = if (currentState.isLoginMode) "Verificando tus credenciales..." else "Creando tu cuenta...",
                errorMessage = null,
                successMessage = null,
                canContinueAfterSyncError = false
            )
        }

        viewModelScope.launch {
            if (currentState.isLoginMode) {
                // Iniciar Sesión
                authManager.signIn(email, password)
                    .onSuccess {
                        restoreBackupAfterLogin(onSuccess)
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                loadingMessage = null,
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
                                loadingMessage = null,
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
                                loadingMessage = null,
                                errorMessage = translateAuthError(error.message ?: "Error al registrarse.")
                            )
                        }
                    }
            }
        }
    }

    fun onGoogleSignInSuccess(idToken: String, nonce: String, onSuccess: () -> Unit) {
        _uiState.update {
            it.copy(
                isLoading = true,
                loadingMessage = "Validando tu cuenta de Google...",
                errorMessage = null,
                successMessage = null,
                canContinueAfterSyncError = false
            )
        }
        viewModelScope.launch {
            authManager.signInWithGoogle(idToken, nonce)
                .onSuccess {
                    restoreBackupAfterLogin(onSuccess)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingMessage = null,
                            errorMessage = translateAuthError(error.message ?: "Error al iniciar sesión con Google.")
                        )
                    }
                }
        }
    }

    fun onGoogleSignInError(message: String) {
        _uiState.update { it.copy(isLoading = false, loadingMessage = null, errorMessage = message) }
    }

    fun startGoogleSignInLoading() {
        _uiState.update {
            it.copy(
                isLoading = true,
                loadingMessage = "Abriendo selector de cuenta...",
                errorMessage = null,
                canContinueAfterSyncError = false
            )
        }
    }

    fun continueAfterSyncError(onSuccess: () -> Unit) {
        _uiState.update { it.copy(canContinueAfterSyncError = false, errorMessage = null) }
        onSuccess()
    }

    private suspend fun restoreBackupAfterLogin(onSuccess: () -> Unit) {
        _uiState.update {
            it.copy(
                loadingMessage = "Restaurando tu copia de seguridad...",
                errorMessage = null,
                successMessage = null
            )
        }
        syncManager.sync()
            .onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = null,
                        successMessage = if (summary.remoteDeletes > 0) {
                            "Copia sincronizada. Se aplicaron ${summary.remoteDeletes} borrados reales y el respaldo quedó activo."
                        } else if (summary.totalRowsTouched > 0) {
                            "Copia sincronizada. Tus datos locales y la nube quedaron alineados."
                        } else {
                            "Sesión iniciada. Tu copia ya está al día."
                        }
                    )
                }
                onSuccess()
            }
            .onFailure { error ->
                cloudSyncScheduler.syncSoon()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = null,
                        canContinueAfterSyncError = true,
                        errorMessage = "Tu sesión quedó iniciada, pero no pude restaurar la copia ahora: ${translateAuthError(error.message ?: "Error desconocido")}"
                    )
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
            error.contains("nonce", ignoreCase = true) -> "No pudimos validar la firma de Google. Revisa la configuración del proveedor Google en Supabase."
            error.contains("provider", ignoreCase = true) -> "El proveedor de Google no parece estar habilitado o configurado en Supabase."
            else -> "Ocurrió un problema: $error"
        }
    }
}

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val isLoginMode: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val canContinueAfterSyncError: Boolean = false
)
