package app.aurora.launcher.core.platform

import android.content.Context
import android.os.Build
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

open class BiometricAuthGateway(
    private val context: Context? = null,
) {
    private val allowedAuthenticators = resolveAllowedAuthenticators(Build.VERSION.SDK_INT)

    companion object {
        // AndroidX Biometric forbids combining BIOMETRIC_WEAK with DEVICE_CREDENTIAL below API 30.
        internal fun resolveAllowedAuthenticators(sdkInt: Int): Int {
            return if (sdkInt >= Build.VERSION_CODES.R) {
                Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
            } else {
                Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
            }
        }
    }

    open fun isAuthenticationAvailable(): Boolean {
        val ctx = context ?: return false
        return safeSystemCall {
            BiometricManager.from(ctx).canAuthenticate(allowedAuthenticators) ==
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

        val launched = safeSystemCall {
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
            true
        }.getOrNull() ?: false

        if (!launched && cont.isActive) {
            cont.resume(false)
        }
    }
}
