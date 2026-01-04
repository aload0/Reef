package dev.pranav.reef.ui.whitelist

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap

@Stable
data class WhitelistedApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val isWhitelisted: Boolean
)

sealed interface AllowedAppsState {
    data object Loading: AllowedAppsState
    data class Success(val apps: List<WhitelistedApp>): AllowedAppsState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhitelistScreen(
    uiState: AllowedAppsState,
    onToggle: (WhitelistedApp) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (uiState) {
            is AllowedAppsState.Loading -> {
                ContainedLoadingIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            is AllowedAppsState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    itemsIndexed(
                        items = uiState.apps,
                        key = { _, app -> app.packageName }
                    ) { index, app ->
                        WhitelistItem(
                            app = app,
                            index = index,
                            listSize = uiState.apps.size,
                            onToggle = { onToggle(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WhitelistItem(
    app: WhitelistedApp,
    index: Int,
    listSize: Int,
    onToggle: () -> Unit
) {
    val shape = when {
        listSize == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 6.dp,
            bottomEnd = 6.dp
        )

        index == listSize - 1 -> RoundedCornerShape(
            topStart = 6.dp,
            topEnd = 6.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp
        )

        else -> RoundedCornerShape(6.dp)
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(
            initialScale = 0.95f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = shape
        ) {
            ListItem(
                modifier = Modifier
                    .clickable(onClick = onToggle)
                    .padding(4.dp),
                headlineContent = {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                },
                leadingContent = {
                    Image(
                        painter = BitmapPainter(app.icon),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                },
                trailingContent = {
                    Checkbox(
                        checked = app.isWhitelisted,
                        onCheckedChange = { onToggle() }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) {
        return bitmap
    }
    val bitmap = createBitmap(
        intrinsicWidth.takeIf { it > 0 } ?: 1,
        intrinsicHeight.takeIf { it > 0 } ?: 1,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
