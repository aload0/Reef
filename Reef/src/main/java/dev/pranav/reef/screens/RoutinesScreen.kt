package dev.pranav.reef.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.pranav.reef.R
import dev.pranav.reef.data.Routine
import dev.pranav.reef.data.RoutineSchedule
import dev.pranav.reef.routine.Routines
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onCreateRoutine: () -> Unit,
    onEditRoutine: (Routine) -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var routines by remember { mutableStateOf(Routines.getAll()) }
    var showActivateDialog by remember { mutableStateOf<Routine?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                routines = Routines.getAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        routines = Routines.getAll()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                RoutineInfoCard()
            }

            if (routines.isEmpty()) {
                item {
                    RoutineEmptyState()
                }
            } else {
                items(routines, key = { it.id }) { routine ->
                    RoutineItem(
                        routine = routine,
                        onClick = {
                            if (routine.schedule.type == RoutineSchedule.ScheduleType.MANUAL) {
                                showActivateDialog = routine
                            } else {
                                onEditRoutine(routine)
                            }
                        },
                        onToggle = { _ ->
                            Routines.toggle(routine.id, context)
                            routines = Routines.getAll()
                        },
                        context = context
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onCreateRoutine,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.create_routine)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    showActivateDialog?.let { routine ->
        AlertDialog(
            onDismissRequest = { showActivateDialog = null },
            title = { Text(stringResource(R.string.activate_routine)) },
            text = { Text(stringResource(R.string.activate_routine_confirmation, routine.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        Routines.startSession(context, routine)
                        showActivateDialog = null

                        kotlinx.coroutines.MainScope().launch {
                            snackbarHostState.showSnackbar(
                                resources.getString(
                                    R.string.routine_activated_toast,
                                    routine.name,
                                    ""
                                ),
                                duration = SnackbarDuration.Long
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.activate))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showActivateDialog = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            showActivateDialog = null
                            onEditRoutine(routine)
                        }
                    ) {
                        Text(stringResource(R.string.edit))
                    }
                }
            }
        )
    }
}

@Composable
private fun RoutineInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.routine_card_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.routine_card_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RoutineEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = stringResource(R.string.no_routines),
                modifier = Modifier.padding(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_routines_yet),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.create_routine_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RoutineItem(
    routine: Routine,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    context: android.content.Context
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = stringResource(R.string.routine_icon),
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatSchedule(routine.schedule, context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = when (routine.limits.size) {
                                0 -> stringResource(R.string.no_app_limits_set)
                                else -> pluralStringResource(
                                    R.plurals.app_limits_applied,
                                    routine.limits.size,
                                    routine.limits.size
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    AnimatedVisibility(visible = routine.isEnabled) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.active_status),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = routine.isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

private fun formatSchedule(schedule: RoutineSchedule?, context: android.content.Context): String {
    if (schedule == null) return context.getString(R.string.unknown_schedule)

    return when (schedule.type) {
        RoutineSchedule.ScheduleType.DAILY -> {
            val timeRange = if (schedule.time != null && schedule.endTime != null) {
                context.getString(
                    R.string.schedule_from_to,
                    formatTime(schedule.time!!),
                    formatTime(schedule.endTime!!)
                )
            } else if (schedule.time != null) {
                context.getString(R.string.schedule_at, formatTime(schedule.time!!))
            } else ""
            context.getString(R.string.daily) + timeRange
        }

        RoutineSchedule.ScheduleType.WEEKLY -> {
            val days = if (schedule.daysOfWeek.size == 7) {
                context.getString(R.string.every_day)
            } else if (schedule.daysOfWeek.containsAll(
                    listOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY
                    )
                )
            ) {
                context.getString(R.string.weekdays)
            } else if (schedule.daysOfWeek.containsAll(
                    listOf(
                        DayOfWeek.SATURDAY,
                        DayOfWeek.SUNDAY
                    )
                )
            ) {
                context.getString(R.string.weekends)
            } else {
                schedule.daysOfWeek.sortedBy { it.value }
                    .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
            }
            val timeRange = if (schedule.time != null && schedule.endTime != null) {
                context.getString(
                    R.string.schedule_from_to,
                    formatTime(schedule.time!!),
                    formatTime(schedule.endTime!!)
                )
            } else if (schedule.time != null) {
                context.getString(R.string.schedule_at, formatTime(schedule.time!!))
            } else ""
            "$days$timeRange"
        }

        RoutineSchedule.ScheduleType.MANUAL -> context.getString(R.string.manual_activation)
    }
}

private fun formatTime(time: LocalTime): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return time.format(formatter)
}
