package app.aurora.launcher.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import app.aurora.launcher.core.model.ComponentKey

@Composable
fun rememberAppIcon(
    iconLoader: IconCacheAndLoader,
    key: ComponentKey,
    iconPackPackage: String? = null,
): ImageBitmap? {
    val bitmap by produceState(
        initialValue = iconLoader.peekCachedIcon(key, iconPackPackage),
        key1 = key,
        key2 = iconPackPackage,
    ) {
        if (value == null) {
            value = iconLoader.loadAppIcon(key, iconPackPackage)
        }
    }
    return bitmap
}
