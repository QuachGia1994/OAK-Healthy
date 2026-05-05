package com.example.supplementtracker.presentation.home

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import com.example.supplementtracker.presentation.navigation.AppTheme
import com.example.supplementtracker.presentation.navigation.ActiveClientManager
import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.export.SupplementExportCycleDTO
import com.example.supplementtracker.domain.export.SupplementExportFileDTO
import com.example.supplementtracker.domain.export.SupplementExportJson
import com.example.supplementtracker.domain.export.SupplementExportSchema
import com.example.supplementtracker.domain.export.SupplementExportSupplementDTO
import com.example.supplementtracker.presentation.share.StackShareImageGenerator
import com.example.supplementtracker.presentation.share.StackShareItem
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel,
    activeClientManager: ActiveClientManager,
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val context = LocalContext.current
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val clientsRaw by activeClientManager.clients.collectAsStateWithLifecycle()
    val clients = remember(clientsRaw) { clientsRaw.distinctBy { it.id } }
    val currentClientId by activeClientManager.currentClientId.collectAsStateWithLifecycle()
    val dataTransferMessage by homeViewModel.dataTransferMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shareChooserTitle = stringResource(R.string.share_stack)
    val backgroundBrush = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF120025), Color.Black))
    } else {
        Brush.linearGradient(listOf(Color(0xFFEAF7FF), Color(0xFFF1F8E9)))
    }
    var isAddClientDialogVisible by remember { mutableStateOf(false) }
    var isEditClientDialogVisible by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<ClientProfile?>(null) }
    var clientNameInput by remember { mutableStateOf("") }
    var isFactoryResetDialogVisible by remember { mutableStateOf(false) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    val createJsonDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(json.toByteArray(Charsets.UTF_8))
        }
        pendingExportJson = null
    }

    val openJsonDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: return@rememberLauncherForActivityResult
        homeViewModel.importSupplementsFromJson(json)
    }

    LaunchedEffect(dataTransferMessage) {
        val message = dataTransferMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        homeViewModel.clearDataTransferMessage()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
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

                item {
                    if (currentClientId != null) {
                        val allSupplements = when (uiState) {
                            is HomeUiState.Success -> {
                                val successState = uiState as HomeUiState.Success
                                (successState.activeSupplements.values.flatten().map { it.supplement } +
                                    successState.restingSupplements.map { it.supplement })
                                    .distinctBy { it.id }
                            }
                            else -> emptyList()
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    val dto = SupplementExportFileDTO(
                                        schemaVersion = SupplementExportSchema.VERSION,
                                        exportedAtEpochMs = System.currentTimeMillis(),
                                        supplements = allSupplements.map { supplement ->
                                            SupplementExportSupplementDTO(
                                                name = supplement.name,
                                                dailyDose = supplement.dailyDose,
                                                intakeTime = supplement.intakeTime,
                                                startDate = supplement.startDate.toString(),
                                                category = null,
                                                cycle = SupplementExportCycleDTO(
                                                    isContinuous = supplement.cycleConfig.isContinuous,
                                                    daysOn = supplement.cycleConfig.daysOn,
                                                    daysOff = supplement.cycleConfig.daysOff,
                                                    durationMonths = supplement.cycleConfig.durationMonths
                                                )
                                            )
                                        }
                                    )
                                    pendingExportJson = SupplementExportJson.encode(dto)
                                    createJsonDocument.launch("OAKHealthy_Stack.json")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.export_data))
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { openJsonDocument.launch(arrayOf("application/json")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.import_data))
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            val shareItems = allSupplements
                                                .sortedBy { it.intakeTime }
                                                .map { StackShareItem(name = it.name, dose = it.dailyDose, time = it.intakeTime) }

                                            val bitmap = StackShareImageGenerator.generate(
                                                context = context,
                                                items = shareItems,
                                                isDark = isDark
                                            )

                                            val file = withContext(Dispatchers.IO) {
                                                val target = File(context.cacheDir, "OAKHealthy_Stack.png")
                                                FileOutputStream(target).use { stream ->
                                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                                }
                                                target
                                            }

                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )

                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "image/png"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Share failed")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.share_stack))
                            }
                        }
                    }
                }

                item {
                    TextButton(onClick = { isFactoryResetDialogVisible = true }) {
                        Text(
                            text = stringResource(R.string.factory_reset),
                            color = Color(0xFFD32F2F),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    if (isFactoryResetDialogVisible) {
        AlertDialog(
            onDismissRequest = { isFactoryResetDialogVisible = false },
            title = { Text(stringResource(R.string.factory_reset)) },
            text = { Text(stringResource(R.string.wipe_data_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    clients.forEach { homeViewModel.deleteClient(it) }
                    activeClientManager.setCurrentClientId(null)
                    isFactoryResetDialogVisible = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { isFactoryResetDialogVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
    val containerColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.04f)
    val borderColor = Color.White.copy(alpha = 0.20f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDark) 12.dp else 2.dp, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            AppThemeSegmentedControl(
                appTheme = appTheme,
                onThemeChange = onThemeChange
            )
        }
    }
}

@Composable
private fun AppThemeSegmentedControl(
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val outerColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFF2F2F7)
    val selectedColor = if (isDark) Color.White.copy(alpha = 0.22f) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0x14000000)

    val items = listOf(
        Triple(stringResource(R.string.appearance_light), AppTheme.LIGHT, appTheme == AppTheme.LIGHT),
        Triple(stringResource(R.string.appearance_dark), AppTheme.DARK, appTheme == AppTheme.DARK),
        Triple(stringResource(R.string.appearance_system), AppTheme.SYSTEM, appTheme == AppTheme.SYSTEM)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(outerColor, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { (label, theme, selected) ->
            val pillShape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .shadow(if (selected) 2.dp else 0.dp, pillShape)
                    .background(if (selected) selectedColor else Color.Transparent, pillShape)
            ) {
                TextButton(
                    onClick = { onThemeChange(theme) },
                    colors = ButtonDefaults.textButtonColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = label,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isDark) Color.White else Color(0xFF111111)
                    )
                }
            }
        }
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
    val containerColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.04f)
    val borderColor = Color.White.copy(alpha = 0.20f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDark) 10.dp else 2.dp, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor),
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
    val containerColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.04f)
    val borderColor = Color.White.copy(alpha = 0.20f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDark) 12.dp else 2.dp, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.client_management), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            if (clients.isEmpty()) {
                Text(text = stringResource(R.string.add_client_to_start), style = MaterialTheme.typography.bodyMedium)
            } else {
                clients.forEach { client ->
                    key(client.id) {
                        var isMenuExpanded by remember { mutableStateOf(false) }
                        val isActive = client.id == currentClientId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = client.name, modifier = Modifier.weight(1f))
                            if (isActive) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            }

                            IconButton(onClick = { isMenuExpanded = true }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
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
private fun LogoCard() {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(20.dp)
    val containerColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.04f)
    val borderColor = Color.White.copy(alpha = 0.20f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDark) 14.dp else 2.dp, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor),
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
