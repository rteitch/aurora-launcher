package app.aurora.launcher.core.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurora.launcher.core.model.IconShapeOption
import app.aurora.launcher.core.model.ProfileBadge

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIconTile(
    label: String,
    iconBitmap: ImageBitmap?,
    iconSize: Dp,
    showLabel: Boolean,
    iconShape: IconShapeOption,
    monochromeEnabled: Boolean = false,
    isSuspendedOrPaused: Boolean = false,
    isLocked: Boolean = false,
    isEditMode: Boolean = false,
    badgeCount: Int = 0,
    profileBadge: ProfileBadge = ProfileBadge.PERSONAL,
    useWallpaperScrimForLabel: Boolean = true,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onRemoveClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = AuroraTheme.palette
    val reduceMotion = AuroraTheme.reduceMotion
    val resolvedShape = remember(iconShape) { resolveIconShape(iconShape) }

    // Jiggle runs only during user-activated EditMode and stops when Reduce Motion is enabled (E26, R-19)
    val rotationDeg = if (isEditMode && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "editJiggle")
        val anim by transition.animateFloat(
            initialValue = -1.6f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 135),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "jiggleDegrees",
        )
        anim
    } else {
        0f
    }

    val colorFilter: ColorFilter? = remember(isSuspendedOrPaused, monochromeEnabled, palette.accent) {
        when {
            isSuspendedOrPaused -> {
                val matrix = ColorMatrix().apply { setToSaturation(0.12f) }
                ColorFilter.colorMatrix(matrix)
            }
            monochromeEnabled -> {
                val matrix = ColorMatrix().apply { setToSaturation(0f) }
                ColorFilter.colorMatrix(matrix)
            }
            else -> null
        }
    }

    val fallbackInitial = remember(label) {
        label.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "A"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .defaultMinSize(
                minWidth = AuroraSpacing.minTouchTarget,
                minHeight = AuroraSpacing.minTouchTarget,
            )
            .auroraFocusRing(RoundedCornerShape(14.dp))
            .semantics {
                contentDescription = label
                if (accessibilityActions.isNotEmpty()) {
                    customActions = accessibilityActions
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 2.dp, horizontal = 2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.graphicsLayer { rotationZ = rotationDeg },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconSize)
                    .clip(resolvedShape)
                    .background(
                        if (monochromeEnabled) palette.surfaceVariant else palette.surface,
                    )
                    .border(
                        width = 0.75.dp,
                        color = palette.glassHairline.copy(alpha = 0.28f),
                        shape = resolvedShape,
                    )
                    .alpha(if (isSuspendedOrPaused) 0.58f else 1f),
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilter,
                        modifier = Modifier.size(iconSize),
                    )
                } else {
                    Text(
                        text = fallbackInitial,
                        style = AuroraTypography.titleLarge,
                        color = palette.accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (isEditMode && onRemoveClick != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(palette.surface)
                        .border(1.dp, palette.outline, CircleShape)
                        .combinedClickable(onClick = onRemoveClick),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = null,
                        tint = palette.onSurface,
                        modifier = Modifier.size(14.dp),
                    )
                }
            } else if (badgeCount > 0) {
                val badgeText = if (badgeCount > 99) "99+" else badgeCount.toString()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFC62828))
                        .border(1.dp, Color.White, CircleShape)
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = AuroraTypography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (isLocked || profileBadge == ProfileBadge.WORK || profileBadge == ProfileBadge.CLONE) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(palette.surface)
                        .border(1.dp, palette.outline, CircleShape),
                ) {
                    if (isLocked) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(11.dp),
                        )
                    } else if (profileBadge == ProfileBadge.WORK) {
                        Icon(
                            imageVector = Icons.Outlined.WorkOutline,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(11.dp),
                        )
                    } else {
                        Text(
                            text = "2",
                            style = AuroraTypography.labelSmall,
                            color = palette.accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (showLabel) {
            Spacer(modifier = Modifier.height(4.dp))
            if (useWallpaperScrimForLabel) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.iconLabelScrim)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = label,
                        style = AuroraTypography.labelMedium,
                        color = palette.iconLabelText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = label,
                    style = AuroraTypography.labelMedium,
                    color = palette.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
