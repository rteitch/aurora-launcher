package app.aurora.launcher.feature.folder

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurora.launcher.core.designsystem.AppIconTile
import app.aurora.launcher.core.designsystem.AuroraMotion
import app.aurora.launcher.core.designsystem.AuroraRadii
import app.aurora.launcher.core.designsystem.AuroraSpacing
import app.aurora.launcher.core.designsystem.AuroraTheme
import app.aurora.launcher.core.designsystem.AuroraTypography
import app.aurora.launcher.core.designsystem.GlassSurface
import app.aurora.launcher.core.designsystem.R
import app.aurora.launcher.core.designsystem.auroraFocusRing
import app.aurora.launcher.core.designsystem.resolveIconShape
import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.GlassTier
import app.aurora.launcher.core.model.IconShapeOption
import app.aurora.launcher.core.model.ItemId
import app.aurora.launcher.core.model.WorkspaceItem
import app.aurora.launcher.core.platform.IconCacheAndLoader
import app.aurora.launcher.core.platform.rememberAppIcon

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderTilePreview(
    folder: WorkspaceItem.Folder,
    iconSize: Dp,
    showLabel: Boolean,
    iconShape: IconShapeOption,
    isEditMode: Boolean,
    useWallpaperScrimForLabel: Boolean,
    iconLoader: IconCacheAndLoader,
    iconPackPackage: String?,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = AuroraTheme.palette
    val reduceMotion = AuroraTheme.reduceMotion
    val shape = remember(iconShape) { resolveIconShape(iconShape) }
    val displayTitle = folder.title.ifBlank { stringResource(R.string.folder_default_name) }

    val rotationDeg = if (isEditMode && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "folderJiggle")
        val anim by transition.animateFloat(
            initialValue = -1.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 140),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "folderDeg",
        )
        anim
    } else {
        0f
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
                contentDescription = displayTitle
                if (accessibilityActions.isNotEmpty()) {
                    customActions = accessibilityActions
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer { rotationZ = rotationDeg }
                .clip(shape)
                .background(palette.surfaceVariant.copy(alpha = 0.92f))
                .border(1.dp, palette.glassHairline.copy(alpha = 0.45f), shape)
                .padding(6.dp),
        ) {
            val previewChildren = remember(folder.children) { folder.children.take(9) }
            val cellSize = (iconSize - 18.dp) / 3f
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (row in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (col in 0 until 3) {
                            val idx = row * 3 + col
                            val child = previewChildren.getOrNull(idx)
                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .clip(RoundedCornerShape(4.dp)),
                            ) {
                                if (child != null) {
                                    val bmp = rememberAppIcon(iconLoader, child.component, iconPackPackage)
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(palette.accent.copy(alpha = 0.35f)),
                                        )
                                    }
                                }
                            }
                        }
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
                        text = displayTitle,
                        style = AuroraTypography.labelMedium,
                        color = palette.iconLabelText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = displayTitle,
                    style = AuroraTypography.labelMedium,
                    color = palette.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun FolderOverlayModal(
    folder: WorkspaceItem.Folder?,
    catalogByKey: Map<ComponentKey, AppEntry>,
    badgeCounts: Map<String, Int>,
    glassTier: GlassTier,
    iconSize: Dp,
    iconShape: IconShapeOption,
    monochromeIcons: Boolean,
    iconPackPackage: String?,
    iconLoader: IconCacheAndLoader,
    onDismiss: () -> Unit,
    onRenameFolder: (ItemId, String) -> Unit,
    onAppClick: (ComponentKey) -> Unit,
    onRemoveChildFromFolder: (ItemId) -> Unit,
) {
    val visible = folder != null
    val reduceMotion = AuroraTheme.reduceMotion
    val palette = AuroraTheme.palette

    if (visible) {
        BackHandler(onBack = onDismiss)
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) fadeIn(tween(80)) else fadeIn(tween(150)) + scaleIn(AuroraMotion.folderSpring(), initialScale = 0.9f),
        exit = if (reduceMotion) fadeOut(tween(80)) else fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.92f),
    ) {
        val activeFolder = folder ?: return@AnimatedVisibility
        val fallbackTitle = stringResource(R.string.folder_default_name)
        var isEditingTitle by rememberSaveable(activeFolder.id.value) { mutableStateOf(false) }
        var draftTitle by rememberSaveable(activeFolder.id.value, activeFolder.title) {
            mutableStateOf(activeFolder.title.ifBlank { fallbackTitle })
        }
        var selectedChildForAction by remember { mutableStateOf<WorkspaceItem.App?>(null) }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .imePadding()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Escape) {
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
                .padding(AuroraSpacing.lg),
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
                        .background(palette.surface.copy(alpha = if (glassTier == GlassTier.TIER_C_SOLID) 0.98f else 0.84f))
                        .padding(AuroraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isEditingTitle) {
                            OutlinedTextField(
                                value = draftTitle,
                                onValueChange = { draftTitle = it.take(36) },
                                singleLine = true,
                                label = { Text(stringResource(R.string.folder_rename_label)) },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        onRenameFolder(activeFolder.id, draftTitle.trim().ifBlank { fallbackTitle })
                                        isEditingTitle = false
                                    },
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    onRenameFolder(activeFolder.id, draftTitle.trim().ifBlank { fallbackTitle })
                                    isEditingTitle = false
                                },
                                modifier = Modifier.defaultMinSize(
                                    minWidth = AuroraSpacing.minTouchTarget,
                                    minHeight = AuroraSpacing.minTouchTarget,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = stringResource(R.string.action_save),
                                    tint = palette.accent,
                                )
                            }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeFolder.title.ifBlank { fallbackTitle },
                                    style = AuroraTypography.titleLarge,
                                    color = palette.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(R.string.folder_item_count, activeFolder.children.size),
                                    style = AuroraTypography.bodyMedium,
                                    color = palette.onSurfaceSecondary,
                                )
                            }
                            IconButton(
                                onClick = { isEditingTitle = true },
                                modifier = Modifier.defaultMinSize(
                                    minWidth = AuroraSpacing.minTouchTarget,
                                    minHeight = AuroraSpacing.minTouchTarget,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.folder_rename_action),
                                    tint = palette.onSurface,
                                )
                            }
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
                    }

                    val removeLabel = stringResource(R.string.folder_remove_item)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(vertical = AuroraSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                    ) {
                        items(activeFolder.children, key = { it.id.value }) { child ->
                            val entry = catalogByKey[child.component]
                            val label = entry?.label ?: child.component.packageName
                            val bmp = rememberAppIcon(iconLoader, child.component, iconPackPackage)
                            val badge = badgeCounts[child.component.packageName] ?: 0
                            AppIconTile(
                                label = label,
                                iconBitmap = bmp,
                                iconSize = iconSize,
                                showLabel = true,
                                iconShape = iconShape,
                                monochromeEnabled = monochromeIcons,
                                isSuspendedOrPaused = entry?.isSuspended == true || entry?.isQuietModePaused == true,
                                isLocked = entry?.isLocked == true,
                                badgeCount = badge,
                                profileBadge = entry?.profileBadge ?: app.aurora.launcher.core.model.ProfileBadge.PERSONAL,
                                useWallpaperScrimForLabel = false,
                                accessibilityActions = listOf(
                                    CustomAccessibilityAction(removeLabel) {
                                        onRemoveChildFromFolder(child.id)
                                        true
                                    },
                                ),
                                onClick = { onAppClick(child.component) },
                                onLongClick = { selectedChildForAction = child },
                            )
                        }
                    }
                }
            }

            val actionTarget = selectedChildForAction
            if (actionTarget != null) {
                val entry = catalogByKey[actionTarget.component]
                val label = entry?.label ?: actionTarget.component.packageName
                AlertDialog(
                    onDismissRequest = { selectedChildForAction = null },
                    title = {
                        Text(
                            text = label,
                            style = AuroraTypography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.onSurface,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.folder_child_action_desc, label),
                            style = AuroraTypography.bodyMedium,
                            color = palette.onSurfaceSecondary,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val targetChild = actionTarget
                                selectedChildForAction = null
                                onRemoveChildFromFolder(targetChild.id)
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.folder_remove_item),
                                color = palette.accent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedChildForAction = null }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                )
            }
        }
    }
}
