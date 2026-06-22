package com.ivan.finanzapp.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthManager @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val authModule = supabaseClient.pluginManager.getPlugin(Auth)

    /**
     * Emite el usuario logueado actualmente.
     * Emite null si no hay ninguna sesión activa.
     */
    val currentUserFlow: Flow<UserInfo?> = authModule.sessionStatus.map { status ->
        if (status is SessionStatus.Authenticated) {
            status.session.user
        } else {
            null
        }
    }

    /**
     * Retorna el usuario actual de forma síncrona/inmediata, si existe.
     */
    val currentUser: UserInfo?
        get() = authModule.currentSessionOrNull()?.user

    /**
     * Registra un nuevo usuario con correo y contraseña.
     */
    suspend fun signUp(email: String, password: String): Result<Unit> {
        return try {
            authModule.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Inicia sesión con correo y contraseña.
     */
    suspend fun signIn(email: String, password: String): Result<Unit> {
        return try {
            authModule.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cierra la sesión activa del usuario.
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            authModule.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
