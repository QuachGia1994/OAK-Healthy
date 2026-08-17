package com.example.supplementtracker.presentation.home

import com.example.supplementtracker.presentation.designsystem.OakColors
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.supplementtracker.R
import com.example.supplementtracker.domain.model.CycleStatus
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.presentation.navigation.AppTheme
import com.example.supplementtracker.presentation.designsystem.OakCard
import com.example.supplementtracker.presentation.designsystem.OakCardVariant
import com.example.supplementtracker.presentation.designsystem.rememberOakAdaptiveLayout
import com.example.supplementtracker.domain.model.ClientProfile
import java.time.format.DateTimeFormatter
import java.util.UUID

private val cycleSummaryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
internal fun SettingsSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(14.dp), content = content)
        }
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1fKB", kb)
    val mb = kb / 1024.0
    return String.format(java.util.Locale.US, "%.2fMB", mb)
}

@Composable
internal fun StepChip(label: String, done: Boolean) {
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = if (done) OakColors.Done.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = if (done) OakColors.Done else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = secondaryTextColor
        )
    }
}

@Composable
internal fun SettingsRow(
    title: String,
    trailing: String? = null,
    onClick: (() -> Unit)? = null
) {
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = primaryTextColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = primaryTextColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun AppearanceCard(
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryTextColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.appearance_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppThemeSegmentedControl(
                appTheme = appTheme,
                onThemeChange = onThemeChange
            )
        }
    }
}

@Composable
internal fun AppThemeSegmentedControl(
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val outerColor = MaterialTheme.colorScheme.surfaceVariant
    val selectedColor = MaterialTheme.colorScheme.surface

    val items = listOf(
        Triple(stringResource(R.string.appearance_light), AppTheme.LIGHT, appTheme == AppTheme.LIGHT),
        Triple(stringResource(R.string.appearance_dark), AppTheme.DARK, appTheme == AppTheme.DARK),
        Triple(stringResource(R.string.appearance_system), AppTheme.SYSTEM, appTheme == AppTheme.SYSTEM)
    )

    val adaptive = rememberOakAdaptiveLayout()
    val containerModifier = Modifier
        .fillMaxWidth()
        .background(outerColor, RoundedCornerShape(14.dp))
        .padding(4.dp)
    if (adaptive.stackMetrics) {
        Column(modifier = containerModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { (label, theme, selected) ->
                ThemeOption(label, selected, selectedColor) { onThemeChange(theme) }
            }
        }
    } else {
        Row(modifier = containerModifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { (label, theme, selected) ->
                ThemeOption(label, selected, selectedColor, Modifier.weight(1f)) { onThemeChange(theme) }
            }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(modifier = modifier.background(if (selected) selectedColor else Color.Transparent, shape)) {
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun getCycleSummary(
    supplement: UserSupplement,
    status: CycleStatus
): String {
    val config = supplement.cycleConfig
    val statusText = if (status == CycleStatus.ON) {
        R.string.cycle_status_on
    } else {
        R.string.cycle_status_off
    }

    val cycleText = if (config.isContinuous) {
        "${stringResource(statusText)} • ${stringResource(R.string.cycle_continuous)}"
    } else {
        stringResource(R.string.cycle_summary_format, stringResource(statusText), config.daysOn, config.daysOff)
    }
    val intervalText = config.intervalDays?.let { interval ->
        stringResource(R.string.cycle_every_n_days_format, interval)
    }
    val durationText = config.durationMonths?.let { days ->
        val endDate = supplement.startDate.plusDays(days.toLong())
        val dateText = endDate.format(cycleSummaryDateFormatter)
        stringResource(R.string.cycle_until_format, dateText)
    } ?: stringResource(R.string.unlimited)

    return listOfNotNull(cycleText, intervalText, durationText).joinToString(" • ")
}

@Composable
internal fun InfoCard(title: String, content: String, isOffCycle: Boolean = false) {
    val shape = RoundedCornerShape(14.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val alpha = if (isOffCycle) 0.55f else 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
        }
    }
}

@Composable
internal fun ExpandableInfoCard(
    title: String,
    content: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(if (expanded) R.string.a11y_hide else R.string.a11y_show)
                )
            }
            if (expanded) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
                    )
                }
            }
        }
    }
}

@Composable
internal fun InfoCardNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, color = subtitleColor)
            }
        }
    }
}

@Composable
internal fun ClientManagementCard(
    clients: List<ClientProfile>,
    currentClientId: UUID?,
    onSelect: (UUID?) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ClientProfile) -> Unit,
    onDelete: (ClientProfile) -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.client_management), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            if (clients.isEmpty()) {
                Text(text = stringResource(R.string.add_client_to_start), style = MaterialTheme.typography.bodyMedium)
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = clients,
                        key = { it.id },
                        contentType = { "client" }
                    ) { client ->
                        var isMenuExpanded by remember(client.id) { mutableStateOf(false) }
                        val isActive = client.id == currentClientId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = client.name, modifier = Modifier.weight(1f))
                            if (isActive) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = stringResource(R.string.a11y_confirm), tint = OakColors.Taken)
                            }

                            IconButton(onClick = { isMenuExpanded = true }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.a11y_more_options))
                            }

                            DropdownMenu(
                                expanded = isMenuExpanded,
                                onDismissRequest = { isMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.select)) },
                                    onClick = {
                                        onSelect(client.id)
                                        isMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit)) },
                                    onClick = {
                                        onEdit(client)
                                        isMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    onClick = {
                                        onDelete(client)
                                        isMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onAdd) {
                Text(stringResource(R.string.add_a_client))
            }
        }
    }
}

@Composable
internal fun LogoCard() {
    val shape = RoundedCornerShape(14.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            com.example.supplementtracker.presentation.designsystem.OakLogoMark()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_dedication),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

internal fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
