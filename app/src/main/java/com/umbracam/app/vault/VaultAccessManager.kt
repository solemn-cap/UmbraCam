package com.umbracam.app.vault

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Optional UI gate using strong biometrics or the device credential fallback. */
class VaultAccessManager(context: Context) {
    private val preferences = context.getSharedPreferences("private_vault", Context.MODE_PRIVATE)
    val enabled: Boolean get() = preferences.getBoolean(KEY_ENABLED, false)
    val canAuthenticate: Boolean get() = BiometricManager.from(preferencesContext).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    private val preferencesContext = context.applicationContext

    fun setEnabled(enabled: Boolean) { check(!enabled || canAuthenticate); preferences.edit().putBoolean(KEY_ENABLED, enabled).apply() }

    fun authenticate(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Private Vault")
            .setSubtitle("Use biometrics or your device screen lock")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build())
    }

    companion object {
        private const val KEY_ENABLED = "biometric_lock_enabled"
        private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
