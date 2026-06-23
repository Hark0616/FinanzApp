package com.ivan.finanzapp.ui.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object LocalDeviceAuthenticator {

    val authenticators: Int =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun availability(context: Context): Int =
        BiometricManager.from(context).canAuthenticate(authenticators)

    fun unavailableMessage(code: Int): String =
        when (code) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Configura huella, rostro o PIN de bloqueo de pantalla antes de activar esta protección."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "Este dispositivo no tiene autenticación biométrica disponible. Configura un PIN de pantalla si el sistema lo permite."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "La autenticación del dispositivo no está disponible en este momento. Inténtalo de nuevo."
            else ->
                "No se pudo validar la seguridad del dispositivo. Revisa el bloqueo de pantalla e inténtalo de nuevo."
        }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancelMessage: String? = null,
        failedMessage: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val availability = availability(activity)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            onError(unavailableMessage(availability))
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        cancelMessage?.let(onError)
                        onCancel()
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    failedMessage?.let(onError)
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(promptInfo)
    }
}

tailrec fun Context.findFragmentActivity(): FragmentActivity? =
    when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
