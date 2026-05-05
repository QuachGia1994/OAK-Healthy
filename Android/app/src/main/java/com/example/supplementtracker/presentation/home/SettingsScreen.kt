package com.example.supplementtracker.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.model.CycleStatus
import com.example.supplementtracker.domain.usecase.CalculateCycleUseCase
import java.time.LocalDate

import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import com.example.supplementtracker.presentation.navigation.AppTheme
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import com.example.supplementtracker.domain.model.ClientProfile
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel,
    activeClientManager: ActiveClientManager,
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val clientsRaw by activeClientManager.clients.collectAsStateWithLifecycle()
    val clients = remember(clientsRaw) { clientsRaw.distinctBy { it.id } }
    val currentClientId by activeClientManager.currentClientId.collectAsStateWithLifecycle()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backgroundColor = Color(0xFFF2F2F7)
    val backgroundBrush = Brush.linearGradient(listOf(Color(0xFF120025), Color.Black))
    var isAddClientDialogVisible by remember { mutableStateOf(false) }
    var isEditClientDialogVisible by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<ClientProfile?>(null) }
    var clientNameInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) backgroundBrush else backgroundColor)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(stringResource(R.string.settings_title)) }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    LogoCard()
                }

                item {
                    ClientManagementCard(
                        clients = clients,
                        currentClientId = currentClientId,
                        onSelect = { activeClientManager.setCurrentClientId(it) },
                        onAdd = { isAddClientDialogVisible = true },
                        onEdit = {
                            editingClient = it
                            clientNameInput = it.name
                            isEditClientDialogVisible = true
                        },
                        onDelete = { client ->
                            val deletingActive = client.id == currentClientId
                            homeViewModel.deleteClient(client)
                            if (deletingActive) {
                                activeClientManager.setCurrentClientId(null)
                            }
                        }
                    )
                }

                item {
                    AppearanceCard(
                        appTheme = appTheme,
                        onThemeChange = onThemeChange
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.app_information_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (uiState is HomeUiState.Success) {
                    val successState = uiState as HomeUiState.Success
                    val allSupplements = (successState.activeSupplements.values.flatten().map { it.supplement } +
                        successState.restingSupplements.map { it.supplement })
                        .distinctBy { it.id }
                        .sortedBy { it.name }

                    item {
                        Text(
                            text = stringResource(R.string.my_list_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.fillMaxWidth(),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(items = allSupplements) { supplement ->
                        InfoCard(
                            title = supplement.name,
                            content = getCycleSummary(supplement)
                        )
                    }
                }

                item {
                    InfoCard(
                        title = stringResource(R.string.settings_guide_title),
                        content = stringResource(R.string.settings_guide_content)
                    )
                }

                item {
                    InfoCard(
                        title = stringResource(R.string.settings_intro_title),
                        content = stringResource(R.string.settings_intro_content)
                    )
                }

                item {
                    InfoCard(
                        title = stringResource(R.string.settings_copyright_title),
                        content = """
                            ${stringResource(R.string.settings_app_version)}
                            ${stringResource(R.string.settings_author)}
                            ${stringResource(R.string.settings_copyright)}
                        """.trimIndent()
                    )
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
                    value = clientNameInput,
                    onValueChange = { clientNameInput = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = clientNameInput.trim()
                    if (trimmed.isEmpty()) return@TextButton
                    val created = ClientProfile(id = UUID.randomUUID(), name = trimmed, avatarColorArgb = 0)
                    homeViewModel.createClient(created)
                    activeClientManager.setCurrentClientId(created.id)
                    clientNameInput = ""
                    isAddClientDialogVisible = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    clientNameInput = ""
                    isAddClientDialogVisible = false
                }) { Text("Cancel") }
            }
        )
    }

    if (isEditClientDialogVisible) {
        val target = editingClient
        if (target != null) {
            AlertDialog(
                onDismissRequest = { isEditClientDialogVisible = false },
                title = { Text(stringResource(R.string.edit)) },
                text = {
                    OutlinedTextField(
                        value = clientNameInput,
                        onValueChange = { clientNameInput = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val trimmed = clientNameInput.trim()
                        if (trimmed.isEmpty()) return@TextButton
                        homeViewModel.updateClient(target.copy(name = trimmed))
                        clientNameInput = ""
                        isEditClientDialogVisible = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        clientNameInput = ""
                        isEditClientDialogVisible = false
                    }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun AppearanceCard(
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(16.dp)
    val containerColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color(0x14000000)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDark) 12.dp else 2.dp, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                ThemeRow(stringResource(R.string.appearance_light), appTheme == AppTheme.LIGHT) { onThemeChange(AppTheme.LIGHT) }
                ThemeRow(stringResource(R.string.appearance_dark), appTheme == AppTheme.DARK) { onThemeChange(AppTheme.DARK) }
                ThemeRow(stringResource(R.string.appearance_system), appTheme == AppTheme.SYSTEM) { onThemeChange(AppTheme.SYSTEM) }
            }
        }
    }
}

@Composable
private fun ThemeRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label)
    }
}

@Composable
private fun getCycleSummary(supplement: UserSupplement): String {
    val config = supplement.cycleConfig
    val calculateCycleUseCase = CalculateCycleUseCase()
    val status = calculateCycleUseCase(supplement.startDate, config, LocalDate.now())
    val statusText = if (status == CycleStatus.ON) {
        R.string.cycle_status_on
    } else {
        R.string.cycle_status_off
    }

    return if (config.isContinuous) {
        stringResource(R.string.cycle_continuous)
    } else {
        stringResource(R.string.cycle_summary_format, stringResource(statusText), config.daysOn, config.daysOff)
    }
}

@Composable
private fun InfoCard(title: String, content: String) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(16.dp)
    val containerColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color(0x14000000)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDark) 10.dp else 2.dp, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
            )
        }
    }
}

@Composable
private fun ClientManagementCard(
    clients: List<ClientProfile>,
    currentClientId: UUID?,
    onSelect: (UUID?) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ClientProfile) -> Unit,
    onDelete: (ClientProfile) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(16.dp)
    val containerColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color(0x14000000)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDark) 12.dp else 2.dp, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.client_management), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            if (clients.isEmpty()) {
                Text(text = "No clients yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                clients.forEach { client ->
                    key(client.id) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = client.name, modifier = Modifier.weight(1f))
                            val isActive = client.id == currentClientId
                            if (isActive) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            }
                            TextButton(onClick = { onSelect(client.id) }) { Text(stringResource(R.string.select)) }
                            TextButton(onClick = { onEdit(client) }) { Text(stringResource(R.string.edit)) }
                            TextButton(onClick = { onDelete(client) }) { Text(stringResource(R.string.delete)) }
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
private fun LogoCard() {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(20.dp)
    val containerColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color(0x14000000)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDark) 14.dp else 2.dp, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                imageVector = ImageVector.vectorResource(R.drawable.ic_oak_logo),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_dedication),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic
            )
        }
    }
}
