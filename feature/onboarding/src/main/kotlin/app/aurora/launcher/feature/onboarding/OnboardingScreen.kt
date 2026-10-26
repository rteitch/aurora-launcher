package app.aurora.launcher.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aurora.launcher.core.designsystem.AuroraRadii
import app.aurora.launcher.core.designsystem.AuroraSpacing
import app.aurora.launcher.core.designsystem.AuroraTheme
import app.aurora.launcher.core.designsystem.AuroraTypography
import app.aurora.launcher.core.designsystem.R
import app.aurora.launcher.core.designsystem.auroraFocusRing
import app.aurora.launcher.core.model.GridPreset
import app.aurora.launcher.core.model.ThemeMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = AuroraTheme.palette
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDefaultRoleState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.completeOnboarding(onFinished)
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AuroraSpacing.lg, vertical = AuroraSpacing.xl),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AuroraSpacing.md)) {
                Text(
                    text = stringResource(R.string.aurora_app_name),
                    style = AuroraTypography.titleMedium,
                    color = palette.accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.onboarding_headline),
                    style = AuroraTypography.headlineMedium,
                    color = palette.onBackground,
                )
                Text(
                    text = stringResource(R.string.onboarding_subtitle),
                    style = AuroraTypography.bodyLarge,
                    color = palette.onSurfaceSecondary,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AuroraRadii.card))
                        .background(palette.surface)
                        .border(1.dp, palette.outline.copy(alpha = 0.35f), RoundedCornerShape(AuroraRadii.card))
                        .padding(AuroraSpacing.md),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = palette.accent,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_privacy_badge),
                        style = AuroraTypography.bodyMedium,
                        color = palette.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(AuroraSpacing.xs))

                Text(
                    text = stringResource(R.string.onboarding_theme_label),
                    style = AuroraTypography.titleMedium,
                    color = palette.onBackground,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                ) {
                    val themes = listOf(
                        ThemeMode.SYSTEM to R.string.settings_theme_system,
                        ThemeMode.LIGHT to R.string.settings_theme_light,
                        ThemeMode.DARK to R.string.settings_theme_dark,
                    )
                    themes.forEach { (mode, labelRes) ->
                        SelectableOptionChip(
                            label = stringResource(labelRes),
                            selected = state.settings.themeMode == mode,
                            onClick = { viewModel.selectTheme(mode) },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.onboarding_grid_label),
                    style = AuroraTypography.titleMedium,
                    color = palette.onBackground,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                ) {
                    val presets = listOf(
                        GridPreset.GRID_4X5 to "4 x 5",
                        GridPreset.GRID_4X6 to "4 x 6",
                        GridPreset.GRID_5X5 to "5 x 5",
                        GridPreset.GRID_5X6 to "5 x 6",
                    )
                    presets.forEach { (preset, label) ->
                        SelectableOptionChip(
                            label = label,
                            selected = state.settings.gridPreset == preset,
                            onClick = { viewModel.selectGridPreset(preset) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AuroraSpacing.xl))

            Column(verticalArrangement = Arrangement.spacedBy(AuroraSpacing.sm)) {
                if (state.isDefaultHome) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                        modifier = Modifier.padding(bottom = AuroraSpacing.xs),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = palette.accent,
                        )
                        Text(
                            text = stringResource(R.string.onboarding_default_active),
                            style = AuroraTypography.bodyMedium,
                            color = palette.onSurface,
                        )
                    }
                    Button(
                        onClick = { viewModel.completeOnboarding(onFinished) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.onAccent,
                        ),
                        shape = RoundedCornerShape(AuroraRadii.input),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget)
                            .auroraFocusRing(RoundedCornerShape(AuroraRadii.input)),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_cta_enter_home),
                            style = AuroraTypography.titleMedium,
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            val intent = viewModel.buildRequestRoleIntent()
                            if (intent != null) {
                                runCatching { roleLauncher.launch(intent) }
                                    .onFailure { viewModel.completeOnboarding(onFinished) }
                            } else {
                                viewModel.completeOnboarding(onFinished)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.onAccent,
                        ),
                        shape = RoundedCornerShape(AuroraRadii.input),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget)
                            .auroraFocusRing(RoundedCornerShape(AuroraRadii.input)),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_cta_set_default),
                            style = AuroraTypography.titleMedium,
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.completeOnboarding(onFinished) },
                        shape = RoundedCornerShape(AuroraRadii.input),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget)
                            .auroraFocusRing(RoundedCornerShape(AuroraRadii.input)),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_cta_preview),
                            style = AuroraTypography.titleMedium,
                            color = palette.onBackground,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = AuroraTheme.palette
    val shape = RoundedCornerShape(AuroraRadii.input)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = 76.dp, minHeight = AuroraSpacing.minTouchTarget)
            .clip(shape)
            .background(if (selected) palette.accent else palette.surface)
            .border(
                width = 1.dp,
                color = if (selected) palette.accent else palette.outline.copy(alpha = 0.5f),
                shape = shape,
            )
            .auroraFocusRing(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = AuroraSpacing.md, vertical = AuroraSpacing.sm),
    ) {
        Text(
            text = label,
            style = AuroraTypography.bodyMedium,
            color = if (selected) palette.onAccent else palette.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
