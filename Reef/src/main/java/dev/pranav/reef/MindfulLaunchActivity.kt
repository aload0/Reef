package dev.pranav.reef

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import dev.pranav.reef.ui.ReefTheme
import dev.pranav.reef.util.MindfulLaunchManager
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("CustomSplashScreen")
class MindfulLaunchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetPackage = intent.getStringExtra("target_package") ?: ""
        if (targetPackage.isBlank()) {
            finish()
            return
        }

        setContent {
            ReefTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MindfulLaunchScreen(
                        targetPackage = targetPackage,
                        onFinished = { finish() }
                    )
                }
            }
        }
    }
}

enum class LaunchPhase {
    LIMIT_REACHED,
    COUNTDOWN,
    DURATION_PICKER
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MindfulLaunchScreen(
    targetPackage: String,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    val appInfo = remember(targetPackage) {
        try {
            packageManager.getApplicationInfo(targetPackage, 0)
        } catch (_: Exception) {
            null
        }
    }
    val appLabel = remember(appInfo) {
        appInfo?.loadLabel(packageManager)?.toString() ?: targetPackage
    }
    val appIconBitmap = remember(appInfo) {
        appInfo?.loadIcon(packageManager)?.let { drawableToBitmap(it) }
    }

    val warningMessage = remember(targetPackage) {
        val msg = MindfulLaunchManager.getWarningMessage(targetPackage)
        msg.ifBlank {
            "Take a deep breath.\nDo you really need to open this app right now?"
        }
    }
    val totalCountdownSeconds = remember(targetPackage) {
        MindfulLaunchManager.getDurationSeconds(targetPackage)
    }

    var phase by remember {
        mutableStateOf(
            if (MindfulLaunchManager.isLaunchLimitReached(targetPackage)) {
                LaunchPhase.LIMIT_REACHED
            } else {
                LaunchPhase.COUNTDOWN
            }
        )
    }

    var secondsLeft by remember { mutableIntStateOf(totalCountdownSeconds) }

    var selectedMinutes by remember { mutableIntStateOf(10) }
    val quickOptions = listOf(5, 10, 15, 30, 45, 60)

    val sendHome = {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(homeIntent)
        onFinished()
    }

    BackHandler {
        sendHome()
    }

    if (phase == LaunchPhase.COUNTDOWN) {
        LaunchedEffect(secondsLeft) {
            if (secondsLeft > 0) {
                delay(1000L.milliseconds)
                secondsLeft -= 1
            } else {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        200,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
                phase = LaunchPhase.DURATION_PICKER
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                ) {
                    if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap.asImageBitmap(),
                            contentDescription = appLabel,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.HourglassEmpty,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = appLabel,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                val launches = MindfulLaunchManager.getDailyLaunchCount(targetPackage)
                val limit = MindfulLaunchManager.getLimitCount(targetPackage)
                if (MindfulLaunchManager.isLimitEnabled(targetPackage)) {
                    Text(
                        text = "Launches today: $launches / $limit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Launches today: $launches",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = phase,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(500)) togetherWith
                                fadeOut(animationSpec = tween(500))
                    },
                    label = "phase_transition"
                ) { targetPhase ->
                    when (targetPhase) {
                        LaunchPhase.LIMIT_REACHED -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(28.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = "Locked",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Launch Limit Reached",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "You have reached your daily launch limit for this app. Go do something productive!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        LaunchPhase.COUNTDOWN -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.7f
                                        )
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Text(
                                        text = warningMessage,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            lineHeight = 24.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(48.dp))
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(120.dp)
                                ) {
                                    val animatedProgress by animateFloatAsState(
                                        targetValue = if (totalCountdownSeconds > 0) {
                                            secondsLeft.toFloat() / totalCountdownSeconds.toFloat()
                                        } else {
                                            0f
                                        },
                                        animationSpec = tween(1000, easing = LinearEasing),
                                        label = "countdown_progress"
                                    )
                                    CircularProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier.fillMaxSize(),
                                        strokeWidth = 8.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(
                                            alpha = 0.3f
                                        ),
                                    )
                                    Text(
                                        text = "$secondsLeft",
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        LaunchPhase.DURATION_PICKER -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = "Choose duration",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "How long do you need the app for?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))

                                FlowRow(
                                    maxItemsInEachRow = 3,
                                    horizontalArrangement = Arrangement.spacedBy(
                                        8.dp,
                                        Alignment.CenterHorizontally
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    quickOptions.forEach { mins ->
                                        val selected = selectedMinutes == mins
                                        FilterChip(
                                            selected = selected,
                                            onClick = { selectedMinutes = mins },
                                            label = { Text("$mins min") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Duration: $selectedMinutes min",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Slider(
                                    value = selectedMinutes.toFloat(),
                                    onValueChange = { selectedMinutes = it.toInt() },
                                    valueRange = 1f..120f,
                                    steps = 119
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (phase) {
                    LaunchPhase.LIMIT_REACHED -> {
                        Button(
                            onClick = { sendHome() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(
                                text = "Go Back",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }

                    LaunchPhase.COUNTDOWN -> {
                        OutlinedButton(
                            onClick = { sendHome() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(
                                text = "Nevermind",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    else -> {
                        Button(
                            onClick = {
                                MindfulLaunchManager.unlockApp(targetPackage, selectedMinutes)
                                val launchIntent =
                                    packageManager.getLaunchIntentForPackage(targetPackage)

                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                }
                                onFinished()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(
                                text = "Unlock & Open",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { sendHome() },
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }

    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 144
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 144

    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)

    // Save previous bounds to prevent mutation side-effects in the OS layout cache
    val oldBounds = drawable.bounds
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    drawable.bounds = oldBounds

    return bitmap
}
