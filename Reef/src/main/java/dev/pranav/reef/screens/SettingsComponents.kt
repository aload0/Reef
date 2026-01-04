package dev.pranav.reef.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pranav.reef.R

@Composable
fun SettingsMenuItemRow(
    item: SettingsMenuItem,
    index: Int,
    listSize: Int,
    onClick: () -> Unit
) {
    SettingsCard(index = index, listSize = listSize) {
        ListItem(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(4.dp),
            headlineContent = {
                Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            },
            supportingContent = {
                Text(text = item.subtitle, style = MaterialTheme.typography.bodySmall)
            },
            leadingContent = {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            },
            trailingContent = {
                if (item.title != stringResource(R.string.app_blocking)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
        )
    }
}

@Composable
fun NumberSettingRow(
    setting: NumberSetting,
    index: Int,
    listSize: Int
) {
    SettingsCard(index = index, listSize = listSize) {
        NumberSettingItem(
            label = setting.label,
            value = setting.value,
            range = setting.range,
            suffix = setting.suffix,
            onValueChange = setting.onValueChange
        )
    }
}

@Composable
fun NumberSettingItem(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String,
    onValueChange: (Int) -> Unit
) {
    ListItem(
        modifier = Modifier
            .clickable { }
            .padding(4.dp),
        headlineContent = {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (value > range.first) onValueChange(value - 1) },
                    enabled = value > range.first
                ) {
                    Text("-", style = MaterialTheme.typography.titleLarge)
                }

                Text(
                    text = "$value $suffix",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(74.dp)
                )

                IconButton(
                    onClick = { if (value < range.last) onValueChange(value + 1) },
                    enabled = value < range.last
                ) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}
