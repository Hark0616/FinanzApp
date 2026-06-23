package com.ivan.finanzapp.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.ivan.finanzapp.R
import com.ivan.finanzapp.data.security.SecureLog
import kotlinx.coroutines.launch

private const val AUTH_TAG = "AuthScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val webClientId = stringResource(R.string.google_web_client_id)
    val isPlaceholder = webClientId == "YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com"

    val triggerGoogleSignIn = {
        if (isPlaceholder) {
            viewModel.onGoogleSignInError("Por favor, configura tu Client ID de Google en el archivo strings.xml para habilitar esta opción.")
        } else {
            viewModel.startGoogleSignInLoading()
            coroutineScope.launch {
                try {
                    val credentialManager = CredentialManager.create(context)
                    val nonce = GoogleAuthNonceGenerator.create()
                    val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId)
                        .setNonce(nonce.hashed)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = credentialManager.getCredential(context = context, request = request)
                    val credential = result.credential

                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        viewModel.onGoogleSignInSuccess(idToken, nonce.raw, onAuthSuccess)
                    } else {
                        viewModel.onGoogleSignInError("Tipo de credencial no soportado.")
                    }
                } catch (e: GoogleIdTokenParsingException) {
                    SecureLog.w(AUTH_TAG, "Google returned an invalid ID token credential.", e)
                    viewModel.onGoogleSignInError("Google devolvió una credencial inválida. Actualiza Google Play Services e inténtalo de nuevo.")
                } catch (e: GetCredentialCancellationException) {
                    SecureLog.i(AUTH_TAG, "Google sign-in selector was closed before an account was selected.", e)
                    viewModel.onGoogleSignInError(googleCancellationMessage(e))
                } catch (e: GetCredentialException) {
                    SecureLog.w(AUTH_TAG, "Google credential request failed.", e)
                    viewModel.onGoogleSignInError("Error de autenticación con Google: ${e.message}")
                } catch (e: Exception) {
                    SecureLog.e(AUTH_TAG, "Unexpected Google sign-in error.", e)
                    viewModel.onGoogleSignInError("Error inesperado: ${e.localizedMessage}")
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header/Logo section
            Text(
                text = "FinanzApp",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Tu contabilidad personal automatizada",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            // Auth Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (state.isLoginMode) "Iniciar Sesión" else "Crear Cuenta",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = if (state.isLoginMode) 
                            "Ingresa tus credenciales para sincronizar tus finanzas en la nube." 
                            else "Regístrate para mantener tus datos a salvo en todos tus dispositivos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Al iniciar sesión se restaura tu copia de Supabase y se activa el respaldo periódico.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    AnimatedVisibility(visible = isPlaceholder) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Falta configurar el Web Client ID de Google para habilitar el acceso con Google.",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Input: Email
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = { viewModel.onEmailChanged(it) },
                        label = { Text("Correo Electrónico") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Input: Password
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { viewModel.onPasswordChanged(it) },
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Contraseña") },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Ocultar" else "Mostrar"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Error Message Display
                    AnimatedVisibility(visible = state.errorMessage != null) {
                        state.errorMessage?.let { error ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = state.canContinueAfterSyncError) {
                        OutlinedButton(
                            onClick = { viewModel.continueAfterSyncError(onAuthSuccess) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Continuar y sincronizar después")
                        }
                    }

                    // Success Message Display
                    AnimatedVisibility(visible = state.successMessage != null) {
                        state.successMessage?.let { success ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = success,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Submit Button
                    Button(
                        onClick = {
                            viewModel.onSubmit(onSuccess = onAuthSuccess)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = state.loadingMessage ?: "Procesando...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = if (state.isLoginMode) "Ingresar" else "Registrarme",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = "o continúa con",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    // Google Sign-In Button
                    OutlinedButton(
                        onClick = { triggerGoogleSignIn() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !state.isLoading,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(20.dp),
                                tint = androidx.compose.ui.graphics.Color.Unspecified
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Continuar con Google",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Mode Toggle (Login vs Register)
                    TextButton(
                        onClick = { viewModel.toggleMode() },
                        enabled = !state.isLoading
                    ) {
                        Text(
                            text = if (state.isLoginMode) 
                                "¿No tienes cuenta? Regístrate aquí" 
                                else "¿Ya tienes una cuenta? Inicia sesión",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Optional Navigate Back (Cancel)
            if (onNavigateBack != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onNavigateBack,
                    enabled = !state.isLoading
                ) {
                    Text("Cancelar y volver", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun googleCancellationMessage(error: GetCredentialCancellationException): String {
    val message = error.message.orEmpty()
    return if (message.contains("Account reauth failed", ignoreCase = true)) {
        "Google no pudo validar esta cuenta con este APK. Crea en Google Cloud un cliente OAuth tipo Android para com.ivan.finanzapp con el SHA-1 de debug y vuelve a intentar."
    } else {
        "Se cerró el selector de Google. Elige una cuenta para continuar con el respaldo."
    }
}
