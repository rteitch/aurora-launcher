package app.aurora.launcher.core.common

import android.content.pm.PackageManager
import android.os.DeadSystemException
import kotlinx.coroutines.CancellationException

sealed interface SystemResult<out T> {
    data class Success<T>(val data: T) : SystemResult<T>
    data class Failure(val reason: String, val throwable: Throwable? = null) : SystemResult<Nothing>

    fun getOrNull(): T? = (this as? Success)?.data
    fun getOrDefault(default: @UnsafeVariance T): T = (this as? Success)?.data ?: default
}

/**
 * Concentrates Android IPC and PackageManager exception guards at the platform boundary.
 */
inline fun <T> safeSystemCall(
    operationName: String = "systemCall",
    block: () -> T,
): SystemResult<T> {
    return try {
        SystemResult.Success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (e: DeadSystemException) {
        SystemResult.Failure("$operationName failed: system server restarting", e)
    } catch (e: SecurityException) {
        SystemResult.Failure("$operationName denied by security policy", e)
    } catch (e: PackageManager.NameNotFoundException) {
        SystemResult.Failure("$operationName target package missing", e)
    } catch (e: IllegalStateException) {
        SystemResult.Failure("$operationName invalid system state", e)
    } catch (e: Exception) {
        SystemResult.Failure(e.message ?: "$operationName failed", e)
    }
}
