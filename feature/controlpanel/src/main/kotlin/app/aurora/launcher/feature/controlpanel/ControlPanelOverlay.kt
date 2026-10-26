package app.aurora.launcher.feature.controlpanel

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aurora.launcher.core.designsystem.AuroraRadii
import app.aurora.launcher.core.designsystem.AuroraSpacing
import app.aurora.launcher.core.designsystem.AuroraTheme
import app.aurora.launcher.core.designsystem.AuroraTypography
import app.aurora.launcher.core.designsystem.GlassSurface
import app.aurora.launcher.core.designsystem.R
import app.aurora.launcher.core.designsystem.auroraFocusRing
import app.aurora.launcher.core.model.GlassTier
import app.aurora.launcher.core.platform.ControlPanelGateway
import app.aurora.launcher.core.platform.ControlPanelSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ControlPanelViewModel @Inject constructor(
    private val gateway: ControlPanelGateway,
) : ViewModel() {
    val state: StateFlow<ControlPanelSnapshot> = gateway.state

    fun refresh() = gateway.refreshSnapshot()
    fun toggleTorch() = gateway.toggleTorch()
    fun setVolume(volume: Int) = gateway.setMediaVolume(volume)
    fun setBrightness(value: Int) = gateway.setScreenBrightness(value)
    fun requestBrightnessPermission() = gateway.openWriteSettingsPermission()
    fun openInternet() = gateway.openInternetPanel()
    fun openBluetooth() = gateway.openBluetoothSettings()
    fun openDisplay() = gateway.openDisplaySettings()
    fun openSoundDnd() = gateway.openSoundAndDndSettings()
    fun mediaPrev() = gateway.dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    fun mediaPlayPause() = gateway.dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun mediaNext() = gateway.dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
}

@Composable
fun ControlPanelOverlay(
    visible: Boolean,
    glassTier: GlassTier,
    onDismiss: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    viewModel: ControlPanelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = AuroraTheme.palette
    val reduceMotion = AuroraTheme.reduceMotion

    LaunchedEffect(visible) {
        if (visible) viewModel.refresh()
    }

    if (visible) {
        BackHandler(onBack = onDismiss)
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) fadeIn(tween(80)) else fadeIn(tween(150)) + slideInVertically(tween(180)) { -it / 4 },
        exit = if (reduceMotion) fadeOut(tween(80)) else fadeOut(tween(120)) + slideOutVertically(tween(140)) { -it / 4 },
    ) {
        Box(
            contentAlignment = Alignment.TopEnd,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f))
                .statusBarsPadding()
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyUp && ev.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(AuroraSpacing.md),
        ) {
            GlassSurface(
                tier = glassTier,
                cornerRadius = AuroraRadii.folder,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.surface.copy(alpha = if (glassTier == GlassTier.TIER_C_SOLID) 0.98f else 0.88f))
                        .padding(AuroraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.control_panel_title),
                            style = AuroraTypography.titleLarge,
                            color = palette.onSurface,
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.defaultMinSize(
                                minWidth = AuroraSpacing.minTouchTarget,
                                minHeight = AuroraSpacing.minTouchTarget,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = palette.onSurface,
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ControlTileButton(
                            icon = Icons.Outlined.Wifi,
                            label = stringResource(R.string.control_internet),
                            active = true,
                            onClick = {
                                viewModel.openInternet()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ControlTileButton(
                            icon = Icons.Outlined.Bluetooth,
                            label = stringResource(R.string.control_bluetooth),
                            active = false,
                            onClick = {
                                viewModel.openBluetooth()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ControlTileButton(
                            icon = if (state.isTorchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                            label = if (state.isTorchAvailable) {
                                stringResource(R.string.control_torch)
                            } else {
                                stringResource(R.string.control_torch_unavailable)
                            },
                            active = state.isTorchOn,
                            enabled = state.isTorchAvailable,
                            onClick = { viewModel.toggleTorch() },
                            modifier = Modifier.weight(1f),
                        )
                        ControlTileButton(
                            icon = Icons.Outlined.DoNotDisturbOn,
                            label = stringResource(R.string.control_dnd),
                            active = false,
                            onClick = {
                                viewModel.openSoundDnd()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ControlTileButton(
                            icon = Icons.Outlined.Settings,
                            label = stringResource(R.string.control_launcher_settings),
                            active = false,
                            onClick = {
                                onDismiss()
                                onOpenLauncherSettings()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Media playback transport card
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AuroraRadii.card))
                            .background(palette.surfaceVariant)
                            .padding(vertical = AuroraSpacing.xs),
                    ) {
                        IconButton(
                            onClick = { viewModel.mediaPrev() },
                            modifier = Modifier.defaultMinSize(
                                minWidth = AuroraSpacing.minTouchTarget,
                                minHeight = AuroraSpacing.minTouchTarget,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SkipPrevious,
                                contentDescription = stringResource(R.string.control_media_prev),
                                tint = palette.onSurface,
                            )
                        }
                        IconButton(
                            onClick = { viewModel.mediaPlayPause() },
                            modifier = Modifier.defaultMinSize(
                                minWidth = AuroraSpacing.minTouchTarget,
                                minHeight = AuroraSpacing.minTouchTarget,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = stringResource(R.string.control_media_play_pause),
                                tint = palette.accent,
                            )
                        }
                        IconButton(
                            onClick = { viewModel.mediaNext() },
                            modifier = Modifier.defaultMinSize(
                                minWidth = AuroraSpacing.minTouchTarget,
                                minHeight = AuroraSpacing.minTouchTarget,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SkipNext,
                                contentDescription = stringResource(R.string.control_media_next),
                                tint = palette.onSurface,
                            )
                        }
                    }

                    // Media volume slider
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = null,
                                tint = palette.onSurfaceSecondary,
                            )
                            Text(
                                text = stringResource(R.string.control_volume_label),
                                style = AuroraTypography.bodyMedium,
                                color = palette.onSurface,
                            )
                        }
                        Slider(
                            value = state.mediaVolume.toFloat(),
                            onValueChange = { viewModel.setVolume(it.roundToInt()) },
                            valueRange = 0f..state.maxMediaVolume.coerceAtLeast(1).toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Brightness slider or opt-in permission prompt
                    Column(verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BrightnessMedium,
                                contentDescription = null,
                                tint = palette.onSurfaceSecondary,
                            )
                            Text(
                                text = stringResource(R.string.control_brightness_label),
                                style = AuroraTypography.bodyMedium,
                                color = palette.onSurface,
                            )
                        }
                        if (state.canWriteSettings) {
                            Slider(
                                value = state.brightness.toFloat(),
                                onValueChange = { viewModel.setBrightness(it.roundToInt()) },
                                valueRange = 10f..255f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.control_brightness_permission),
                                style = AuroraTypography.bodyMedium,
                                color = palette.onSurfaceSecondary,
                            )
                            OutlinedButton(
                                onClick = {
                                    viewModel.requestBrightnessPermission()
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(AuroraRadii.input),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
                            ) {
                                Text(
                                    text = stringResource(R.string.control_brightness_grant),
                                    color = palette.accent,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlTileButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = AuroraTheme.palette
    val shape = RoundedCornerShape(AuroraRadii.card)
    val bgColor = when {
        !enabled -> palette.surfaceVariant.copy(alpha = 0.45f)
        active -> palette.accent
        else -> palette.surfaceVariant
    }
    val fgColor = when {
        !enabled -> palette.onSurfaceSecondary.copy(alpha = 0.6f)
        active -> palette.onAccent
        else -> palette.onSurface
    }
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, palette.outline.copy(alpha = 0.28f), shape)
            .auroraFocusRing(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = AuroraSpacing.md, vertical = AuroraSpacing.sm),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fgColor)
        Text(
            text = label,
            style = AuroraTypography.labelMedium,
            color = fgColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
