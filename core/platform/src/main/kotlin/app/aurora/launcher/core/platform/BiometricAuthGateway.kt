package app.aurora.launcher.core.platform

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.aurora.launcher.core.common.safeSystemCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BiometricAuthGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // DEVICE_CREDENTIAL fallback guarantees locked apps remain accessible if biometrics change or are absent (E29).
    private val allowedAuthenticators =
        Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL

    fun isAuthenticationAvailable(): Boolean {
        return safeSystemCall {
            BiometricManager.from(context).canAuthenticate(allowedAuthenticators) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }.getOrNull() ?: false
    }

    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (cont.isActive) cont.resume(false)
            }

            override fun onAuthenticationFailed() {
                // Prompt remains open for retry until user cancels or succeeds.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()

        prompt.authenticate(promptInfo)
        cont.invokeOnCancellation {
            safeSystemCall { prompt.cancelAuthentication() }
        }
    }
}
