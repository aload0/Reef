package dev.pranav.reef.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.pranav.reef.R
import dev.pranav.reef.util.prefs

@Composable
fun PomodoroSettingsContent(
    onBackPressed: () -> Unit,
    onSoundPicker: () -> Unit
) {
    BackHandler { onBackPressed() }

    var focusMinutes by remember { mutableIntStateOf(prefs.getInt("pomodoro_focus_minutes", 25)) }
    var shortBreakMinutes by remember {
        mutableIntStateOf(
            prefs.getInt(
                "pomodoro_short_break_minutes",
                5
            )
        )
    }
    var longBreakMinutes by remember {
        mutableIntStateOf(
            prefs.getInt(
                "pomodoro_long_break_minutes",
                15
            )
        )
    }
    var cycles by remember { mutableIntStateOf(prefs.getInt("pomodoro_cycles", 4)) }
    var autoStartBreaks by remember { mutableStateOf(prefs.getBoolean("auto_start_breaks", true)) }
    var autoStartPomodoros by remember {
        mutableStateOf(
            prefs.getBoolean(
                "auto_start_pomodoros",
                false
            )
        )
    }
    var soundEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                "pomodoro_sound_enabled",
                true
            )
        )
    }

    val numberSettings = listOf(
        NumberSetting(
            label = stringResource(R.string.focus_duration),
            value = focusMinutes,
            range = 1..120,
            suffix = stringResource(R.string.min_short_suffix),
            onValueChange = {
                focusMinutes = it
                prefs.edit { putInt("pomodoro_focus_minutes", it) }
            }
        ),
        NumberSetting(
            label = stringResource(R.string.short_break_duration),
            value = shortBreakMinutes,
            range = 1..30,
            suffix = stringResource(R.string.min_short_suffix),
            onValueChange = {
                shortBreakMinutes = it
                prefs.edit { putInt("pomodoro_short_break_minutes", it) }
            }
        ),
        NumberSetting(
            label = stringResource(R.string.long_break_duration),
            value = longBreakMinutes,
            range = 1..60,
            suffix = stringResource(R.string.min_short_suffix),
            onValueChange = {
                longBreakMinutes = it
                prefs.edit { putInt("pomodoro_long_break_minutes", it) }
            }
        ),
        NumberSetting(
            label = stringResource(R.string.cycles_before_long_break),
            value = cycles,
            range = 1..10,
            suffix = "",
            onValueChange = {
                cycles = it
                prefs.edit { putInt("pomodoro_cycles", it) }
            }
        )
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            Text(
                text = stringResource(R.string.pomodoro_settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.durations_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            itemsIndexed(numberSettings) { index, setting ->
                NumberSettingRow(
                    setting = setting,
                    index = index,
                    listSize = numberSettings.size
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text(
                    text = stringResource(R.string.automation),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsCard(index = 0, listSize = 2) {
                    ListItem(
                        modifier = Modifier
                            .clickable {
                                autoStartBreaks = !autoStartBreaks
                                prefs.edit { putBoolean("auto_start_breaks", autoStartBreaks) }
                            }
                            .padding(4.dp),
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.auto_start_breaks),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.auto_start_breaks_description),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = autoStartBreaks,
                                onCheckedChange = {
                                    autoStartBreaks = it
                                    prefs.edit { putBoolean("auto_start_breaks", it) }
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            item {
                SettingsCard(index = 1, listSize = 2) {
                    ListItem(
                        modifier = Modifier
                            .clickable {
                                autoStartPomodoros = !autoStartPomodoros
                                prefs.edit {
                                    putBoolean(
                                        "auto_start_pomodoros",
                                        autoStartPomodoros
                                    )
                                }
                            }
                            .padding(4.dp),
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.auto_start_focus),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.auto_start_focus_description),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = autoStartPomodoros,
                                onCheckedChange = {
                                    autoStartPomodoros = it
                                    prefs.edit { putBoolean("auto_start_pomodoros", it) }
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text(
                    text = stringResource(R.string.sound),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsCard(index = 0, listSize = 2) {
                    ListItem(
                        modifier = Modifier
                            .clickable {
                                soundEnabled = !soundEnabled
                                prefs.edit { putBoolean("pomodoro_sound_enabled", soundEnabled) }
                            }
                            .padding(4.dp),
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.transition_sound),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.transition_sound_description),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = soundEnabled,
                                onCheckedChange = {
                                    soundEnabled = it
                                    prefs.edit { putBoolean("pomodoro_sound_enabled", it) }
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            item {
                SettingsCard(index = 1, listSize = 2) {
                    ListItem(
                        modifier = Modifier
                            .clickable(enabled = soundEnabled) { onSoundPicker() }
                            .padding(4.dp),
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.select_sound),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (soundEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.5f
                                )
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.select_sound_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (soundEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.5f
                                )
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = if (soundEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.5f
                                )
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}
