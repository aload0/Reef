package dev.pranav.reef.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.pranav.reef.R
import dev.pranav.reef.SettingsCard
import dev.pranav.reef.receivers.DailySummaryScheduler
import dev.pranav.reef.util.prefs

@Composable
fun NotificationSettingsContent(
    onBackPressed: () -> Unit
) {
    BackHandler { onBackPressed() }

    val context = LocalContext.current
    var focusReminders by remember { mutableStateOf(prefs.getBoolean("focus_reminders", true)) }
    var breakAlerts by remember { mutableStateOf(prefs.getBoolean("break_alerts", true)) }
    var dailySummary by remember { mutableStateOf(prefs.getBoolean("daily_summary", false)) }
    var limitWarnings by remember { mutableStateOf(prefs.getBoolean("limit_warnings", true)) }

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
                text = stringResource(R.string.notifications_settings_title),
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
                    text = stringResource(R.string.general_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsCard(index = 0, listSize = 4) {
                    ListItem(
                        modifier = Modifier
                            .clickable {
                                focusReminders = !focusReminders
                                prefs.edit { putBoolean("focus_reminders", focusReminders) }
                            }
                            .padding(4.dp),
                        headlineContent = {
                            Text(
                                stringResource(R.string.focus_reminders),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.focus_reminders_description),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Switch(checked = focusReminders, onCheckedChange = {
                                focusReminders = it
                                prefs.edit { putBoolean("focus_reminders", it) }
                            })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            item {
                SettingsCard(index = 1, listSize = 4) {
                    ListItem(
                        modifier = Modifier
                            .clickable {
                                breakAlerts = !breakAlerts
                                prefs.edit { putBoolean("break_alerts", breakAlerts) }
                            }
                            .padding(4.dp),
                        headlineContent = {
                            Text(
                                stringResource(R.string.break_alerts),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.break_alerts_description),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Switch(checked = breakAlerts, onCheckedChange = {
                                breakAlerts = it
                                prefs.edit { putBoolean("break_alerts", it) }
                            })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            item {
                SettingsCard(index = 2, listSize = 4) {
                    ListItem(
                        modifier = Modifier
                            .clickable {
                                dailySummary = !dailySummary
                                prefs.edit { putBoolean("daily_summary", dailySummary) }
                                if (dailySummary) {
                                    DailySummaryScheduler.scheduleNextDailySummary(context)
                                } else {
                                    DailySummaryScheduler.cancelDailySummary(context)
                                }
                            }
                            .padding(4.dp),
                        headlineContent = {
                            Text(
                                stringResource(R.string.daily_summary),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.daily_summary_description),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Switch(checked = dailySummary, onCheckedChange = {
                                dailySummary = it
                                prefs.edit { putBoolean("daily_summary", it) }
                                if (it) {
                                    DailySummaryScheduler.scheduleNextDailySummary(context)
                                } else {
                                    DailySummaryScheduler.cancelDailySummary(context)
                                }
                            })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            item {
                SettingsCard(index = 3, listSize = 4) {
                    ListItem(
                        modifier = Modifier
                            .clickable {
                                limitWarnings = !limitWarnings
                                prefs.edit { putBoolean("limit_warnings", limitWarnings) }
                            }
                            .padding(4.dp),
                        headlineContent = {
                            Text(
                                stringResource(R.string.limit_warnings),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.limit_warnings_description),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Switch(checked = limitWarnings, onCheckedChange = {
                                limitWarnings = it
                                prefs.edit { putBoolean("limit_warnings", it) }
                            })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}
