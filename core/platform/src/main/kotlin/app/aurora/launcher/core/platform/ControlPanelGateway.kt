package app.aurora.launcher.core.platform

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.common.safeSystemCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class ControlPanelSnapshot(
    val isTorchAvailable: Boolean = false,
    val isTorchOn: Boolean = false,
    val mediaVolume: Int = 0,
    val maxMediaVolume: Int = 15,
    val brightness: Int = 128,
    val canWriteSettings: Boolean = false,
)

open class ControlPanelGateway(
    private val context: Context? = null,
) {
    private val cameraManager = context?.getSystemService(CameraManager::class.java)
    private val audioManager = context?.getSystemService(AudioManager::class.java)

    private val torchCameraId: String? = safeSystemCall {
        cameraManager?.cameraIdList?.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            hasFlash && (facing == CameraCharacteristics.LENS_FACING_BACK || facing == null)
        } ?: cameraManager?.cameraIdList?.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private val _state = MutableStateFlow(readInitialSnapshot())
    open val state: StateFlow<ControlPanelSnapshot> = _state.asStateFlow()

    init {
        if (cameraManager != null && torchCameraId != null) {
            safeSystemCall {
                cameraManager.registerTorchCallback(
                    object : CameraManager.TorchCallback() {
                        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                            if (cameraId == torchCameraId) {
                                _state.update { it.copy(isTorchOn = enabled) }
                            }
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
            }
        }
    }

    open fun refreshSnapshot() {
        _state.update { current ->
            val fresh = readInitialSnapshot()
            fresh.copy(isTorchOn = current.isTorchOn)
        }
    }

    open fun toggleTorch(): SystemResult<Boolean> {
        val camId = torchCameraId ?: return SystemResult.Failure("Flashlight unavailable")
        val next = !_state.value.isTorchOn
        return safeSystemCall {
            cameraManager?.setTorchMode(camId, next)
            _state.update { it.copy(isTorchOn = next) }
            next
        }
    }

    open fun setMediaVolume(volume: Int): SystemResult<Unit> {
        return safeSystemCall {
            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            val clamped = volume.coerceIn(0, maxVol)
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
            _state.update { it.copy(mediaVolume = clamped, maxMediaVolume = maxVol) }
        }
    }

    open fun dispatchMediaKey(keyCode: Int): SystemResult<Unit> {
        return safeSystemCall {
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            Unit
        }
    }

    open fun setScreenBrightness(value: Int): SystemResult<Unit> {
        val ctx = context ?: return SystemResult.Failure("Context unavailable")
        val canWrite = safeSystemCall { Settings.System.canWrite(ctx) }.getOrNull() ?: false
        if (!canWrite) {
            _state.update { it.copy(canWriteSettings = false) }
            return SystemResult.Failure("Write settings permission required")
        }
        return safeSystemCall {
            val clamped = value.coerceIn(10, 255)
            Settings.System.putInt(
                ctx.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(
                ctx.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                clamped,
            )
            _state.update { it.copy(brightness = clamped, canWriteSettings = true) }
        }
    }

    open fun openWriteSettingsPermission(): SystemResult<Unit> {
        val ctx = context ?: return SystemResult.Failure("Context unavailable")
        return safeSystemCall {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${ctx.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        }
    }

    open fun openInternetPanel(): SystemResult<Unit> {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        return launchSettingsIntent(action)
    }

    open fun openBluetoothSettings(): SystemResult<Unit> =
        launchSettingsIntent(Settings.ACTION_BLUETOOTH_SETTINGS)

    open fun openDisplaySettings(): SystemResult<Unit> =
        launchSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS)

    open fun openSoundAndDndSettings(): SystemResult<Unit> =
        launchSettingsIntent(Settings.ACTION_SOUND_SETTINGS)

    private fun launchSettingsIntent(action: String): SystemResult<Unit> {
        val ctx = context ?: return SystemResult.Failure("Context unavailable")
        return safeSystemCall {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(intent)
            } else {
                ctx.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun readInitialSnapshot(): ControlPanelSnapshot {
        val maxVol = safeSystemCall {
            audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        }.getOrNull()?.coerceAtLeast(1) ?: 15
        val curVol = safeSystemCall {
            audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: (maxVol / 2)
        }.getOrNull()?.coerceIn(0, maxVol) ?: (maxVol / 2)
        val brightness = safeSystemCall {
            val cr = context?.contentResolver ?: return@safeSystemCall 140
            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 140)
        }.getOrNull()?.coerceIn(10, 255) ?: 140
        val canWrite = safeSystemCall { context?.let { Settings.System.canWrite(it) } ?: false }.getOrNull() ?: false

        return ControlPanelSnapshot(
            isTorchAvailable = torchCameraId != null,
            isTorchOn = false,
            mediaVolume = curVol,
            maxMediaVolume = maxVol,
            brightness = brightness,
            canWriteSettings = canWrite,
        )
    }
}
