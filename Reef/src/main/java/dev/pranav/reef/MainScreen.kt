package dev.pranav.reef

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.pranav.reef.timer.TimerStateManager
import dev.pranav.reef.ui.Typography
import dev.pranav.reef.util.isAccessibilityServiceEnabledForBlocker
import dev.pranav.reef.util.prefs
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun HomeContent(
    onNavigateToTimer: () -> Unit,
    onNavigateToUsage: () -> Unit,
    onNavigateToRoutines: () -> Unit,
    onNavigateToWhitelist: () -> Unit,
    onNavigateToIntro: () -> Unit,
    onRequestAccessibility: () -> Unit,
    @Suppress("UNUSED_PARAMETER") slideProgress: Float = 0f,
    onSlideProgressChange: (Float) -> Unit = {},
    currentTimeLeft: String = "00:00",
    currentTimerState: String = "FOCUS"
) {
    val context = LocalContext.current
    val timerState by TimerStateManager.state.collectAsState()
    var showDiscordDialog by remember { mutableStateOf(false) }
    var showDonateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (prefs.getBoolean("first_run", true)) {
            onNavigateToIntro()
        } else {
            delay(500)
            if (!prefs.getBoolean("discord_shown", false)) {
                showDiscordDialog = true
            } else if (prefs.getBoolean("show_dialog", false)) {
                showDonateDialog = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        FocusModeCard(
            onSlideProgress = onSlideProgressChange,
            onClick = {
                if (context.isAccessibilityServiceEnabledForBlocker()) {
                    onNavigateToTimer()
                } else {
                    onRequestAccessibility()
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppUsageCard(
                modifier = Modifier.weight(1f),
                onClick = onNavigateToUsage
            )
            TimeLimitsCard(
                modifier = Modifier.weight(1f),
                onClick = onNavigateToWhitelist
            )
        }

        Spacer(Modifier.height(12.dp))

        RoutinesCard(onClick = onNavigateToRoutines)

        Spacer(Modifier.height(12.dp))

        PomodoroTimerCard(
            onClick = onNavigateToTimer,
            isRunning = timerState.isRunning,
            isPaused = timerState.isPaused,
            currentTimeLeft = currentTimeLeft,
            currentTimerState = currentTimerState
        )

        Spacer(Modifier.height(16.dp))
    }

    if (showDiscordDialog) {
        CommunityDialog(
            onDismiss = {
                prefs.edit { putBoolean("discord_shown", true) }
                showDiscordDialog = false
            },
            onJoin = {
                prefs.edit { putBoolean("discord_shown", true) }
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://discord.gg/46wCMRVAre".toUri()
                    )
                )
                showDiscordDialog = false
            }
        )
    }

    if (showDonateDialog) {
        DonateDialog(
            onDismiss = {
                prefs.edit { putBoolean("show_dialog", false) }
                showDonateDialog = false
            },
            onSupport = {
                prefs.edit { putBoolean("show_dialog", false) }
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://PranavPurwar.github.io/donate.html".toUri()
                    )
                )
                showDonateDialog = false
            }
        )
    }
}

@Composable
private fun FocusModeCard(
    onSlideProgress: (Float) -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FocusTogglePill(
                onSlideProgress = onSlideProgress,
                onActivate = onClick
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Focus Mode",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontFamily = Typography.DMSerif
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Slide to start a deep work\nsession",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                lineHeight = 22.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun FocusTogglePill(
    onSlideProgress: (Float) -> Unit,
    onActivate: () -> Unit
) {
    val pillWidth = 200.dp
    val thumbSize = 60.dp
    val padding = 6.dp
    val maxOffset = with(LocalDensity.current) { (pillWidth - thumbSize - padding * 2).toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) offsetX else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "thumb_offset"
    )

    val progress = (animatedOffset / maxOffset).coerceIn(0f, 1f)

    LaunchedEffect(progress) {
        onSlideProgress(progress)
    }

    Surface(
        modifier = Modifier
            .width(pillWidth)
            .height(72.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        if (offsetX >= maxOffset * 0.7f) {
                            onActivate()
                        }
                        offsetX = 0f
                        isDragging = false
                    },
                    onDragCancel = {
                        offsetX = 0f
                        isDragging = false
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(0f, maxOffset)
                    }
                )
            },
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f + progress * 0.4f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.CenterStart
        ) {
            if (progress > 0.5f) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Release",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                            alpha = (1f - progress).coerceIn(0.3f, 0.7f)
                        ),
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Slide",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                            alpha = (1f - progress).coerceIn(0.3f, 0.7f)
                        ),
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .size(thumbSize)
                    .offset { IntOffset(animatedOffset.roundToInt(), 0) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp + (4.dp * progress)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        if (progress > 0.5f) Icons.Rounded.Check else Icons.Filled.Waves,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun AppUsageCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(180.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryFixedDim.copy(0.4F)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Rounded.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Column {
                Text(
                    text = "App Usage",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    fontFamily = Typography.DMSerif,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "2h 14m today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun TimeLimitsCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(180.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryFixedDim.copy(0.4F)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Rounded.HourglassEmpty,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Column {
                Text(
                    text = "Time Limits",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    fontFamily = Typography.DMSerif,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "3 apps capped",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun RoutinesCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.EventNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Routines",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Scheduled: \"Deep Work\" at 2:00 PM",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun PomodoroTimerCard(
    onClick: () -> Unit,
    isRunning: Boolean,
    isPaused: Boolean,
    currentTimeLeft: String,
    currentTimerState: String
) {
    val isActive = isRunning || isPaused

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = if (isActive)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        if (isPaused) Icons.Rounded.Pause
                        else if (isRunning) Icons.Rounded.PlayArrow
                        else Icons.Rounded.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isActive) {
                        currentTimerState.lowercase().replaceFirstChar { it.uppercase() }
                    } else {
                        "Pomodoro Timer"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isActive) {
                        if (isPaused) "Paused • $currentTimeLeft" else "In progress • $currentTimeLeft"
                    } else {
                        "Start a focus session"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = if (isActive)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityDialog(
    onDismiss: () -> Unit,
    onJoin: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.Groups,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.join_community),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = stringResource(R.string.join_community_desc),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onJoin) {
                Icon(
                    Icons.Rounded.Forum,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.join_discord))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.maybe_later))
            }
        }
    )
}

@Composable
private fun DonateDialog(
    onDismiss: () -> Unit,
    onSupport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = stringResource(R.string.support_development),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.support_development_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.any_amount_helps),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            Button(onClick = onSupport) {
                Icon(
                    Icons.Rounded.VolunteerActivism,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.support_development))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.maybe_later))
            }
        }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeContentPreview() {
    MaterialTheme {
        HomeContent(
            onNavigateToTimer = {},
            onNavigateToUsage = {},
            onNavigateToRoutines = {},
            onNavigateToWhitelist = {},
            onNavigateToIntro = {},
            onRequestAccessibility = {}
        )
    }
}
