package com.example.supplementtracker.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.supplementtracker.domain.model.UserSupplement
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.supplementtracker.R
import com.example.supplementtracker.service.UpdateService
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import com.example.supplementtracker.domain.model.ClientProfile
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    activeClientManager: ActiveClientManager,
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val updateService = remember(context) { UpdateService(context.applicationContext) }
    val isUpdateAvailable by updateService.isUpdateAvailable.collectAsStateWithLifecycle()
    val updateInfo by updateService.updateInfo.collectAsStateWithLifecycle()
    val clientsRaw by activeClientManager.clients.collectAsStateWithLifecycle()
    val clients = remember(clientsRaw) { clientsRaw.distinctBy { it.id } }
    val currentClientId by activeClientManager.currentClientId.collectAsStateWithLifecycle()
    val currentClientName = clients.firstOrNull { it.id == currentClientId }?.name
    var isClientMenuExpanded by remember { mutableStateOf(false) }
    var isAddClientDialogVisible by remember { mutableStateOf(false) }
    var newClientName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        updateService.checkForUpdates(packageInfo.versionName ?: "1.0.0")
    }

    if (isUpdateAvailable) {
        val isForceUpdate = updateInfo?.forceUpdate == true
        val notes = updateInfo?.releaseNotes?.takeIf { it.isNotBlank() }
        AlertDialog(
            onDismissRequest = {
                if (!isForceUpdate) {
                    updateService.dismissUpdate()
                }
            },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { Text(notes ?: stringResource(R.string.update_available_message, updateInfo?.version ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo?.updateUrl))
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.update_now))
                }
            },
            dismissButton = if (!isForceUpdate) {
                {
                    TextButton(onClick = { updateService.skipUpdate(updateInfo?.version.orEmpty()) }) {
                        Text(stringResource(R.string.later))
                    }
                }
            } else {
                null
            }
        )
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backgroundBrush = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF120025), Color.Black))
    } else {
        Brush.linearGradient(listOf(Color(0xFFEAF7FF), Color(0xFFF1F8E9), Color.White))
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LargeTopAppBar(
                    colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Column {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { isClientMenuExpanded = true },
                                            onLongClick = { isClientMenuExpanded = true }
                                        )
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = currentClientName?.let { stringResource(R.string.student_prefix, it) } ?: stringResource(R.string.add_a_client),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = isClientMenuExpanded,
                                    onDismissRequest = { isClientMenuExpanded = false }
                                ) {
                                    clients
                                        .distinctBy { it.name }
                                        .forEach { client ->
                                            DropdownMenuItem(
                                                text = { Text(client.name) },
                                                onClick = {
                                                    isClientMenuExpanded = false
                                                    activeClientManager.setCurrentClientId(client.id)
                                                }
                                            )
                                        }
                                    Divider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.add_a_client)) },
                                        onClick = {
                                            isClientMenuExpanded = false
                                            isAddClientDialogVisible = true
                                        }
                                    )
                                }
                            }
                            Text(
                                text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM")),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onNavigateToAdd) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_supplement_title))
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (val state = uiState) {
                    is HomeUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    is HomeUiState.Success -> HomeContent(
                        state = state,
                        onToggleIntake = viewModel::toggleIntake,
                        onDelete = viewModel::deleteItem,
                        onEdit = onNavigateToEdit
                    )
                    is HomeUiState.NoClient -> Text(stringResource(R.string.add_client_to_start), modifier = Modifier.align(Alignment.Center))
                    is HomeUiState.Error -> Text(state.message, color = Color.Red, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }

    if (isAddClientDialogVisible) {
        AlertDialog(
            onDismissRequest = { isAddClientDialogVisible = false },
            title = { Text(stringResource(R.string.add_a_client)) },
            text = {
                OutlinedTextField(
                    value = newClientName,
                    onValueChange = { newClientName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newClientName.trim()
                        if (trimmed.isEmpty()) return@TextButton
                        val profile = ClientProfile(id = UUID.randomUUID(), name = trimmed, avatarColorArgb = 0)
                        viewModel.createClient(profile)
                        activeClientManager.setCurrentClientId(profile.id)
                        newClientName = ""
                        isAddClientDialogVisible = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { isAddClientDialogVisible = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Success,
    onToggleIntake: (String, Boolean) -> Unit,
    onDelete: (UserSupplement) -> Unit,
    onEdit: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Active Today
        item { SectionHeader(stringResource(R.string.today_intake_title)) }
        
        if (state.activeSupplements.isEmpty()) {
            item { EmptyStateMessage(stringResource(R.string.no_intake_today)) }
        }

        state.activeSupplements.forEach { (time, items) ->
            item { TimeGroupHeader(time) }
            items(items, key = { it.supplement.id }) { item ->
                DismissibleSupplementCard(
                    item = item,
                    onToggleIntake = onToggleIntake,
                    onDelete = onDelete,
                    onEdit = onEdit
                )
            }
        }

        // Section: Resting
        if (state.restingSupplements.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.resting_title)) }
            items(state.restingSupplements) { info ->
                RestingSupplementCard(info)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun TimeGroupHeader(time: String) {
    Text(
        text = time,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
private fun DismissibleSupplementCard(
    item: SupplementUiItem,
    onToggleIntake: (String, Boolean) -> Unit,
    onDelete: (UserSupplement) -> Unit,
    onEdit: (String) -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val dismissState = rememberDismissState(confirmStateChange = { value: DismissValue ->
        when (value) {
            DismissValue.DismissedToStart -> {
                onDelete(item.supplement)
                true
            }
            DismissValue.DismissedToEnd -> {
                onEdit(item.supplement.id.toString())
                true
            }
            else -> true
        }
    })

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != DismissValue.Default) {
            dismissState.reset()
        }
    }

    SwipeToDismiss(
        state = dismissState,
        background = {
            val direction = dismissState.dismissDirection
            val isDelete = direction == DismissDirection.EndToStart
            val backgroundColor = when (direction) {
                DismissDirection.EndToStart -> Color.Red
                DismissDirection.StartToEnd -> Color(0xFF2E7D32)
                null -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp),
                contentAlignment = if (isDelete) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                if (direction != null) {
                    Icon(
                        imageVector = if (isDelete) Icons.Default.Delete else Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        },
        dismissContent = {
        Box {
            ActiveSupplementCard(
                item = item,
                onToggleIntake = onToggleIntake,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { isMenuOpen = true }
                )
            )
            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        isMenuOpen = false
                        onEdit(item.supplement.id.toString())
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        isMenuOpen = false
                        onDelete(item.supplement)
                    }
                )
            }
        }
        }
    )
}

@Composable
private fun ActiveSupplementCard(
    item: SupplementUiItem,
    onToggleIntake: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(12.dp)
    val containerColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.50f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.70f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, shape),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.supplement.name, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(item.supplement.intakeTime, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(" • ", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(item.supplement.dailyDose, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                IconToggleButton(
                    checked = item.isTaken,
                    onCheckedChange = { checked ->
                        onToggleIntake(item.supplement.id.toString(), checked)
                    }
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (item.isTaken) Color.Green else Color.Gray
                    )
                }
            }
            if (!item.advice.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.advice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestingSupplementCard(info: RestingSupplementInfo) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(12.dp)
    val containerColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.50f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.70f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(info.supplement.name, style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                Text(
                    stringResource(
                        R.string.rest_until,
                        LocalDate.now().plusDays(info.daysRemaining.toLong()).format(DateTimeFormatter.ofPattern("dd/MM"))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Badge(containerColor = Color.Transparent) {
                Text(stringResource(R.string.days_remaining, info.daysRemaining), modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Gray,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    )
}
