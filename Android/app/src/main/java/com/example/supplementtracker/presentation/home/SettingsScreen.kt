package com.example.supplementtracker.presentation.home

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.platform.LocalView
import androidx.core.content.FileProvider
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel,
    activeClientManager: ActiveClientManager,
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onNavigateToStackManager: () -> Unit
) {
    val context = LocalContext.current
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val clientsRaw by activeClientManager.clients.collectAsStateWithLifecycle()
    val clients = remember(clientsRaw) { clientsRaw.distinctBy { it.id } }
    val currentClientId by activeClientManager.currentClientId.collectAsStateWithLifecycle()
    val dataTransferMessage by homeViewModel.dataTransferMessage.collectAsStateWithLifecycle()
    val cloudSyncLoading by homeViewModel.cloudSyncLoading.collectAsStateWithLifecycle()
    val hostedBinId by homeViewModel.hostedBinId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val hostView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val localViewModelStoreOwner = LocalViewModelStoreOwner.current
    val compositionContext = rememberCompositionContext()
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shareStackTitle = stringResource(R.string.share_stack)
    var isGuideExpanded by remember { mutableStateOf(false) }
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
    var downloadBinId by remember { mutableStateOf("") }

    val allSupplements = remember(uiState, currentClientId) {
        if (currentClientId == null) return@remember emptyList<UserSupplement>()
        val success = uiState as? HomeUiState.Success ?: return@remember emptyList()
        (success.activeSupplements.values.flatten().map { it.supplement } + success.restingSupplements.map { it.supplement })
            .distinctBy { it.id }
            .sortedBy { it.name }
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SettingsSection(title = "Quản lý học viên") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
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

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        if (clients.isEmpty()) {
                            Text(
                                text = stringResource(R.string.add_client_to_start),
                                style = MaterialTheme.typography.bodyMedium
                            )
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
                                                    activeClientManager.setCurrentClientId(client.id)
                                                    isMenuExpanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.edit)) },
                                                onClick = {
                                                    editingClient = client
                                                    clientNameInput = client.name
                                                    isEditClientDialogVisible = true
                                                    isMenuExpanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.delete)) },
                                                onClick = {
                                                    val deletingActive = client.id == currentClientId
                                                    homeViewModel.deleteClient(client)
                                                    if (deletingActive) activeClientManager.setCurrentClientId(null)
                                                    isMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        TextButton(
                            onClick = { isAddClientDialogVisible = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(stringResource(R.string.add_a_client))
                        }
                    }
                }

                item {
                    SettingsSection(title = stringResource(R.string.appearance_title)) {
                        AppThemeSegmentedControl(
                            appTheme = appTheme,
                            onThemeChange = onThemeChange
                        )
                    }
                }

                item {
                    SettingsSection(title = stringResource(R.string.data_tools)) {
                        SettingsRow(
                            title = shareStackTitle,
                            onClick = {
                                coroutineScope.launch(Dispatchers.Main) {
                                    try {
                                        val activity = context as? Activity
                                        if (activity == null) {
                                            Toast.makeText(context, "Lỗi chia sẻ: Missing Activity", Toast.LENGTH_LONG).show()
                                            return@launch
                                        }

                                        val shareItems = allSupplements
                                            .sortedBy { it.intakeTime }
                                            .map { StackShareItem(name = it.name, dose = it.dailyDose, time = it.intakeTime) }

                                        val savedStateRegistryOwner = hostView.findViewTreeSavedStateRegistryOwner()
                                        if (savedStateRegistryOwner == null) {
                                            Toast.makeText(context, "Lỗi chia sẻ: Missing SavedStateRegistryOwner", Toast.LENGTH_LONG).show()
                                            return@launch
                                        }

                                        val viewModelStoreOwner = localViewModelStoreOwner
                                        if (viewModelStoreOwner == null) {
                                            Toast.makeText(context, "Lỗi chia sẻ: Missing ViewModelStoreOwner", Toast.LENGTH_LONG).show()
                                            return@launch
                                        }

                                        val bitmap = StackShareImageGenerator.generate(
                                            activity = activity,
                                            context = context,
                                            lifecycleOwner = lifecycleOwner,
                                            savedStateRegistryOwner = savedStateRegistryOwner,
                                            viewModelStoreOwner = viewModelStoreOwner,
                                            compositionContext = compositionContext,
                                            items = shareItems,
                                            isDark = isDark
                                        )

                                        val imageFile = withContext(Dispatchers.IO) {
                                            val cachePath = File(context.cacheDir, "shared_images")
                                            cachePath.mkdirs()
                                            val target = File(cachePath, "oak_stack_${System.currentTimeMillis()}.png")
                                            FileOutputStream(target).use { stream ->
                                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                                stream.flush()
                                            }
                                            target
                                        }

                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            imageFile
                                        )

                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, shareStackTitle))
                                    } catch (e: Exception) {
                                        Log.e("ShareStack", "Error sharing stack", e)
                                        Toast.makeText(context, "Lỗi chia sẻ: ${e.message ?: "Unknown"}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                    }
                }

                item {
                    SettingsSection(title = "Đồng bộ đa thiết bị") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { homeViewModel.hostData() },
                                enabled = !cloudSyncLoading
                            ) {
                                if (cloudSyncLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Phát dữ liệu")
                            }
                        }

                        val currentBinId = hostedBinId
                        if (currentBinId != null) {
                            val binIdToCopy = currentBinId.trim()
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Mã liên kết của bạn:",
                                    modifier = Modifier.padding(end = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currentBinId,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("binId", binIdToCopy))
                                        Toast.makeText(context, "Đã sao chép", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Copy")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = downloadBinId,
                            onValueChange = { downloadBinId = it },
                            label = { Text("Nhập mã liên kết") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { homeViewModel.receiveData(downloadBinId) },
                            enabled = !cloudSyncLoading
                        ) {
                            if (cloudSyncLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Tải về")
                        }
                    }
                }

                item {
                    SettingsSection(title = stringResource(R.string.my_list_title)) {
                        SettingsRow(
                            title = stringResource(R.string.manage_stack),
                            trailing = "${allSupplements.size}",
                            onClick = onNavigateToStackManager.takeIf { currentClientId != null }
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        SettingsRow(
                            title = stringResource(R.string.settings_guide_title),
                            trailing = if (isGuideExpanded) "−" else "+",
                            onClick = { isGuideExpanded = !isGuideExpanded }
                        )

                        AnimatedVisibility(visible = isGuideExpanded) {
                            Text(
                                text = stringResource(R.string.settings_guide_content),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                item {
                    SettingsSection(title = "Giới thiệu & Bản quyền") {
                        Text(
                            text = stringResource(R.string.settings_intro_content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Text(
                            text = stringResource(R.string.settings_app_version),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.settings_author),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.settings_copyright),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
private fun SettingsSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    trailing: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(text = trailing, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppearanceCard(
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
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
    val shape = RoundedCornerShape(32.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant

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
private fun ExpandableInfoCard(
    title: String,
    content: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
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
                    contentDescription = null
                )
            }
            AnimatedVisibility(visible = expanded) {
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
private fun InfoCardNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

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
            Text(text = subtitle, color = subtitleColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyStackListScreen(
    homeViewModel: HomeViewModel,
    activeClientManager: ActiveClientManager,
    onBack: () -> Unit
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val currentClientId by activeClientManager.currentClientId.collectAsStateWithLifecycle()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backgroundBrush = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF120025), Color.Black))
    } else {
        Brush.linearGradient(listOf(Color(0xFFEAF7FF), Color(0xFFF1F8E9)))
    }

    val supplements = remember(uiState, currentClientId) {
        if (currentClientId == null) return@remember emptyList<UserSupplement>()
        val success = uiState as? HomeUiState.Success ?: return@remember emptyList()
        (success.activeSupplements.values.flatten().map { it.supplement } + success.restingSupplements.map { it.supplement })
            .distinctBy { it.id }
            .sortedBy { it.name }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(stringResource(R.string.my_list_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = supplements, key = { it.id }) { supplement ->
                    val time = supplement.intakeTime.trim()
                    val title = if (time.isEmpty()) supplement.name else "${supplement.name} ($time)"
                    InfoCard(
                        title = title,
                        content = getCycleSummary(supplement)
                    )
                }
            }
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
    val shape = RoundedCornerShape(32.dp)
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
    val shape = RoundedCornerShape(32.dp)
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
