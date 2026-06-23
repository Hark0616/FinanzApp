package com.ivan.finanzapp.ui.auth

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

data class GoogleAuthNonce(
    val raw: String,
    val hashed: String
)

object GoogleAuthNonceGenerator {
    fun create(byteLength: Int = 32): GoogleAuthNonce {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        val raw = Base64.encodeToString(
            randomBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val hashed = MessageDigest
            .getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
        return GoogleAuthNonce(raw = raw, hashed = hashed)
    }
}
