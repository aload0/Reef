package dev.pranav.reef.timer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pranav.reef.R
import dev.pranav.reef.util.prefs

sealed interface TimerConfig {
    data class Simple(val minutes: Int, val strictMode: Boolean): TimerConfig
    data class Pomodoro(
        val focusMinutes: Int,
        val shortBreakMinutes: Int,
        val longBreakMinutes: Int,
        val cycles: Int,
        val strictMode: Boolean
    ): TimerConfig
}

@Composable
fun TimerContent(
    isTimerRunning: Boolean,
    isPaused: Boolean,
    currentTimeLeft: String,
    currentTimerState: String,
    isStrictMode: Boolean,
    onStartTimer: (TimerConfig) -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onCancelTimer: () -> Unit,
    onRestartTimer: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isTimerRunning,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
            },
            label = "timer_state"
        ) { running ->
            if (running) {
                RunningTimerView(
                    timeLeft = currentTimeLeft,
                    timerState = currentTimerState,
                    isPaused = isPaused,
                    isStrictMode = isStrictMode,
                    onPause = onPauseTimer,
                    onResume = onResumeTimer,
                    onCancel = onCancelTimer,
                    onRestart = onRestartTimer
                )
            } else {
                FocusTimerSetupView(onStart = onStartTimer)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimerScreen(
    isTimerRunning: Boolean,
    isPaused: Boolean,
    currentTimeLeft: String,
    currentTimerState: String,
    isStrictMode: Boolean,
    onStartTimer: (TimerConfig) -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onCancelTimer: () -> Unit,
    onRestartTimer: () -> Unit,
    onBackPressed: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.focus_mode_title),
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            TimerContent(
                isTimerRunning = isTimerRunning,
                isPaused = isPaused,
                currentTimeLeft = currentTimeLeft,
                currentTimerState = currentTimerState,
                isStrictMode = isStrictMode,
                onStartTimer = onStartTimer,
                onPauseTimer = onPauseTimer,
                onResumeTimer = onResumeTimer,
                onCancelTimer = onCancelTimer,
                onRestartTimer = onRestartTimer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FocusTimerSetupView(onStart: (TimerConfig) -> Unit) {
    var selectedMode by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))

        FocusModeGroup(
            selectedMode = selectedMode,
            onSelectionChange = { selectedMode = it }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedMode == 0) {
                SimpleFocusSetup(onStart)
            } else {
                PomodoroFocusSetup(onStart)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FocusModeGroup(
    selectedMode: Int,
    onSelectionChange: (Int) -> Unit
) {
    val modes = listOf(stringResource(R.string.timer_tab), stringResource(R.string.pomodoro_tab))

    FlowRow(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        modes.forEachIndexed { index, label ->
            ToggleButton(
                checked = index == selectedMode,
                onCheckedChange = {
                    if (selectedMode != index) {
                        onSelectionChange(index)
                    }
                },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
            ) {
                Text(label)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SimpleFocusSetup(onStart: (TimerConfig) -> Unit) {
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(30) }
    var isStrictMode by remember { mutableStateOf(false) }

    val totalMinutes = hours * 60 + minutes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { if (hours < 12) hours++ },
                    modifier = Modifier.size(64.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.extraLargeSquareShape,
                        pressedShape = IconButtonDefaults.largePressedShape
                    )
                ) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.increase_hours))
                }
                Spacer(Modifier.width(120.dp))
                FilledTonalIconButton(
                    onClick = {
                        if (minutes < 59) minutes++ else if (hours < 12) {
                            hours++; minutes = 0
                        }
                    },
                    modifier = Modifier.size(64.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.extraLargeSquareShape,
                        pressedShape = IconButtonDefaults.largePressedShape
                    )
                ) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.increase_minutes))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = hours.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 92.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 92.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    text = minutes.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 92.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { if (hours > 0) hours-- },
                    modifier = Modifier.size(64.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.extraLargeSquareShape,
                        pressedShape = IconButtonDefaults.largePressedShape
                    )
                ) {
                    Icon(Icons.Rounded.Remove, stringResource(R.string.decrease_hours))
                }
                Spacer(Modifier.width(120.dp))
                FilledTonalIconButton(
                    onClick = {
                        if (minutes > 0) minutes-- else if (hours > 0) {
                            hours--; minutes = 59
                        }
                    },
                    modifier = Modifier.size(64.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.extraLargeSquareShape,
                        pressedShape = IconButtonDefaults.largePressedShape
                    )
                ) {
                    Icon(Icons.Rounded.Remove, stringResource(R.string.decrease_minutes))
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text(stringResource(R.string.hours), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.minutes), style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isStrictMode) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Column {
                    Text(
                        text = if (isStrictMode) stringResource(R.string.strict_mode) else stringResource(
                            R.string.flexible_mode
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isStrictMode) stringResource(R.string.no_pausing_allowed) else stringResource(
                            R.string.pause_resume_anytime
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = isStrictMode,
                onCheckedChange = { isStrictMode = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            AssistChip(
                onClick = { hours = 0; minutes = 5 },
                label = { Text(pluralStringResource(R.plurals.minutes_label, 5, 5)) })
            AssistChip(
                onClick = { hours = 0; minutes = 15 },
                label = { Text(pluralStringResource(R.plurals.minutes_label, 15, 15)) })
            AssistChip(
                onClick = { hours = 0; minutes = 30 },
                label = { Text(pluralStringResource(R.plurals.minutes_label, 30, 30)) })
            AssistChip(
                onClick = { hours = 1; minutes = 0 },
                label = { Text(stringResource(R.string.hour_label)) })
            AssistChip(
                onClick = { hours = 2; minutes = 0 },
                label = { Text(pluralStringResource(R.plurals.hours_label, 2, 2)) })
            AssistChip(
                onClick = { hours = 3; minutes = 0 },
                label = { Text(pluralStringResource(R.plurals.hours_label, 3, 3)) })
            AssistChip(
                onClick = { hours = 1; minutes = 30 },
                label = { Text(stringResource(R.string.hour_min_short_suffix, 1, 30, 30)) })
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onStart(TimerConfig.Simple(totalMinutes, isStrictMode)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = totalMinutes > 0,
            shapes = ButtonDefaults.shapes(pressedShape = ButtonDefaults.pressedShape),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.start),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PomodoroFocusSetup(onStart: (TimerConfig) -> Unit) {
    var focusMinutes by remember {
        mutableIntStateOf(prefs.getInt("pomodoro_focus_minutes", 25))
    }
    var shortBreakMinutes by remember {
        mutableIntStateOf(prefs.getInt("pomodoro_short_break_minutes", 5))
    }
    var longBreakMinutes by remember {
        mutableIntStateOf(prefs.getInt("pomodoro_long_break_minutes", 15))
    }
    var cycles by remember {
        mutableIntStateOf(prefs.getInt("pomodoro_cycles", 4))
    }
    var isStrictMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ExpressiveCounter(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.focus_label),
                    value = focusMinutes,
                    onValueChange = { focusMinutes = it },
                    range = 1..120,
                    suffix = stringResource(R.string.min_short_suffix)
                )
                ExpressiveCounter(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.short_break_label),
                    value = shortBreakMinutes,
                    onValueChange = { shortBreakMinutes = it },
                    range = 1..30,
                    suffix = stringResource(R.string.min_short_suffix)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ExpressiveCounter(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.long_break_label),
                    value = longBreakMinutes,
                    onValueChange = { longBreakMinutes = it },
                    range = 1..60,
                    suffix = stringResource(R.string.min_short_suffix)
                )
                ExpressiveCounter(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.cycles_label),
                    value = cycles,
                    onValueChange = { cycles = it },
                    range = 1..10,
                    suffix = ""
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isStrictMode) Icons.Outlined.Lock else Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Column {
                    Text(
                        text = if (isStrictMode) stringResource(R.string.strict_mode) else stringResource(
                            R.string.flexible_mode
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isStrictMode) stringResource(R.string.no_pausing_allowed) else stringResource(
                            R.string.pause_resume_anytime
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = isStrictMode,
                onCheckedChange = { isStrictMode = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                onStart(
                    TimerConfig.Pomodoro(
                        focusMinutes,
                        shortBreakMinutes,
                        longBreakMinutes,
                        cycles,
                        isStrictMode
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes(pressedShape = ButtonDefaults.pressedShape),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.TwoTone.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.start_pomodoro),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveCounter(
    modifier: Modifier = Modifier,
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    suffix: String
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = "$value$suffix",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                onClick = { if (value > range.first) onValueChange(value - 1) },
                modifier = Modifier.size(40.dp),
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(Icons.Rounded.Remove, stringResource(R.string.decrease))
            }
            FilledTonalIconButton(
                onClick = { if (value < range.last) onValueChange(value + 1) },
                modifier = Modifier.size(40.dp),
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(Icons.Rounded.Add, stringResource(R.string.increase))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RunningTimerView(
    timeLeft: String,
    timerState: String,
    isPaused: Boolean,
    isStrictMode: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRestart: () -> Unit = {}
) {
    val isPomodoroMode = prefs.getBoolean("pomodoro_mode", false)
    val currentCycle = prefs.getInt("pomodoro_current_cycle", 0)
    val totalCycles = prefs.getInt("pomodoro_cycles_before_long_break", 4)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (isPomodoroMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                text = stringResource(R.string.pomodoro_tab),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    if (timerState == "FOCUS") {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = stringResource(
                                        R.string.cycle_count,
                                        currentCycle,
                                        totalCycles
                                    ),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }
            }

            val stateText = when (timerState) {
                "FOCUS" -> stringResource(R.string.focus_label)
                "SHORT_BREAK" -> stringResource(R.string.short_break_label)
                "LONG_BREAK" -> stringResource(R.string.long_break_label)
                else -> stringResource(R.string.focus_label)
            }

            val isBreak = timerState == "SHORT_BREAK" || timerState == "LONG_BREAK"

            if (isBreak) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(bottom = 8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = stateText,
                style = MaterialTheme.typography.displaySmall,
                color = if (isBreak) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
            )

            Text(
                text = timeLeft,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif,
                fontSize = 88.sp,
                color = if (isBreak) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
            )

            if (isStrictMode) {
                Text(
                    text = stringResource(R.string.strict_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else if (isPaused) {
                Text(
                    text = stringResource(R.string.paused_status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else if (isBreak) {
                Text(
                    text = stringResource(R.string.take_a_break_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .padding(horizontal = 32.dp)
                )
            }
        }

        if (!isStrictMode) {
            RunningTimerActions(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.95f),
                isPaused = isPaused,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
                onRestart = onRestart
            )
        } else {
            Text(
                text = stringResource(R.string.no_interruptions_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RunningTimerActions(
    modifier: Modifier = Modifier,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRestart: () -> Unit = {}
) {
    Row(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconToggleButton(
            checked = isPaused,
            onCheckedChange = { if (isPaused) onResume() else onPause() },
            shapes = IconButtonDefaults.toggleableShapes(
                shape = IconButtonDefaults.largeSquareShape
            ),
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                MaterialTheme.colorScheme.secondaryContainer,
                checkedContainerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier
                .height(62.dp)
                .aspectRatio(0.89f)
        ) {
            Icon(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(9.dp),
                imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = if (isPaused) stringResource(R.string.resume) else stringResource(
                    R.string.pause
                )
            )
        }

        Button(
            onClick = { onCancel() },
            modifier = Modifier
                .weight(1f)
                .padding(12.dp)
                .height(84.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(
                text = stringResource(R.string.cancel),
                style = MaterialTheme.typography.titleLargeEmphasized
            )
        }

        OutlinedButton(
            onClick = onRestart,
            shapes = ButtonDefaults.shapes(shape = ButtonDefaults.elevatedShape),
            modifier = Modifier.size(60.dp)
        ) {
            Text(
                text = "↻",
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun TimerScreenPreview() {
    MaterialTheme {
        TimerScreen(
            isTimerRunning = false,
            isPaused = false,
            currentTimeLeft = "25:00",
            currentTimerState = "FOCUS",
            isStrictMode = false,
            onStartTimer = {},
            onPauseTimer = {},
            onResumeTimer = {},
            onCancelTimer = {},
            onRestartTimer = {},
            onBackPressed = {}
        )
    }
}
