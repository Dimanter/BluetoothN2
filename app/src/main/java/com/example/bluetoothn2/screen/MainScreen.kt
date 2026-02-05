package com.example.bluetoothn2.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluetoothn2.R
import com.example.bluetoothn2.model.BleDeviceModel
import com.example.bluetoothn2.model.ConnectionState
import com.example.bluetoothn2.ui.theme.PrimaryColor
import com.example.bluetoothn2.ui.theme.TextColor
import com.example.bluetoothn2.viewmodel.BluetoothState
import com.example.bluetoothn2.viewmodel.BluetoothViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: BluetoothViewModel,
    onNavigateToConnectedDevice: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    // Для запроса разрешений
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.onPermissionsGranted()
            viewModel.startScanning()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Для сканирования BLE нужны все разрешения")
            }
        }
    }

    // Для включения Bluetooth
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.refreshBluetoothState()
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.startScanning()
        }
    }

    // Состояние для фильтров
    var showFilters by remember { mutableStateOf(false) }
    var nameFilter by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }

    // Находим выбранное устройство
    val selectedDevice = remember(uiState.selectedDeviceAddress) {
        uiState.selectedDeviceAddress?.let { address ->
            (uiState.discoveredDevices + uiState.pairedDevices)
                .find { it.address == address }
        }
    }

    // При изменении фильтров применяем их
    LaunchedEffect(nameFilter, uiState.scanFilters.showOnlyConnectable) {
        viewModel.updateScanFilters(
            uiState.scanFilters.copy(
                nameFilter = nameFilter,
                showOnlyConnectable = uiState.scanFilters.showOnlyConnectable
            )
        )
    }

    // Показываем сообщения
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            if (error.isNotBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(error)
                    viewModel.clearError()
                }
            }
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            if (message.isNotBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(message)
                    viewModel.clearSuccessMessage()
                }
            }
        }
    }

    // Запрашиваем разрешения при первом запуске, если их нет
    LaunchedEffect(Unit) {
        if (!uiState.hasPermissions) {
            val requiredPermissions = getRequiredPermissions(context)
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        } else if (uiState.bluetoothState is BluetoothState.Enabled && !uiState.isScanning) {
            // Автозапуск сканирования при наличии разрешений
            viewModel.startScanning()
        }
    }

    // Функция для обновления списка
    fun refreshDevices() {
        isRefreshing = true
        coroutineScope.launch {
            try {
                viewModel.refreshDevices()
                delay(1000)
            } finally {
                isRefreshing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showFilters) {
                            Text("Поиск устройств")
                        } else {
                            Text("BLE Scanner")
                        }
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryColor,
                    titleContentColor = TextColor,
                    navigationIconContentColor = TextColor,
                    actionIconContentColor = TextColor
                ),
                actions = {
                    // Кнопка фильтров
                    IconButton(
                        onClick = { showFilters = !showFilters }
                    ) {
                        Icon(
                            imageVector = if (showFilters) Icons.Default.AddCircle else Icons.Default.Close,
                            contentDescription = "Фильтры",
                            tint = TextColor
                        )
                    }

                    // Кнопка обновления
                    IconButton(
                        onClick = { refreshDevices() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = TextColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Обновить",
                                tint = TextColor
                            )
                        }
                    }

                    // Кнопка сканирования/остановки
                    IconButton(
                        onClick = {
                            if (uiState.isScanning) {
                                viewModel.stopScanning()
                            } else {
                                if (uiState.bluetoothState is BluetoothState.Enabled) {
                                    viewModel.startScanning()
                                }
                            }
                        },
                        enabled = uiState.bluetoothState is BluetoothState.Enabled
                    ) {
                        if (uiState.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = TextColor
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.outline_bluetooth_24),
                                contentDescription = "Сканировать",
                                tint = TextColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        painter = painterResource(
                            id = when (uiState.bluetoothState) {
                                BluetoothState.Enabled -> R.drawable.outline_bluetooth_24
                                else -> R.drawable.outline_bluetooth_disabled_24
                            }
                        ),
                        contentDescription = "Bluetooth Status",
                        tint = TextColor
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedDevice != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        onNavigateToConnectedDevice(selectedDevice.address)
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.outline_bluetooth_24),
                            contentDescription = "Перейти к устройству"
                        )
                    },
                    text = { Text("Управление ${selectedDevice.name ?: "устройством"}") },
                    containerColor = PrimaryColor,
                    contentColor = TextColor
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Статус Bluetooth с иконкой
            BluetoothStatusCard(
                state = uiState.bluetoothState,
                hasPermissions = uiState.hasPermissions,
                onEnableClick = {
                    viewModel.getEnableBluetoothIntent()?.let {
                        enableBluetoothLauncher.launch(it)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Фильтры поиска
            if (showFilters) {
                FilterSection(
                    nameFilter = nameFilter,
                    onNameFilterChange = { nameFilter = it },
                    showOnlyConnectable = uiState.scanFilters.showOnlyConnectable,
                    onShowOnlyConnectableChange = { enabled ->
                        viewModel.updateScanFilters(
                            uiState.scanFilters.copy(showOnlyConnectable = enabled)
                        )
                    },
                    onClearFilters = {
                        nameFilter = ""
                        viewModel.clearFilters()
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Прогресс сканирования
            if (uiState.isScanning) {
                ScanProgress(
                    progress = uiState.scanProgress,
                    devicesCount = uiState.filteredDevices.size,
                    totalDevicesCount = uiState.discoveredDevices.size
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Статистика
            ScanStats(
                totalDevices = uiState.discoveredDevices.size,
                filteredDevices = uiState.filteredDevices.size,
                connectedDevices = if (uiState.connectedDeviceAddress != null) 1 else 0,
                isScanning = uiState.isScanning
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Список устройств
            BleDeviceList(
                devices = uiState.filteredDevices,
                pairedDevices = uiState.pairedDevices,
                isScanning = uiState.isScanning,
                connectedDeviceAddress = uiState.connectedDeviceAddress,
                selectedDeviceAddress = uiState.selectedDeviceAddress,
                onDeviceClick = { device: BleDeviceModel ->
                    // Выбираем устройство для перехода
                    viewModel.selectDevice(device.address)
                },
                onDeviceLongClick = { device: BleDeviceModel ->
                    // Долгое нажатие - подключение/отключение
                    if (device.address == uiState.connectedDeviceAddress) {
                        viewModel.disconnectFromDevice(device.address)
                    } else {
                        viewModel.connectToDevice(device)
                    }
                },
                onConnectClick = { device: BleDeviceModel ->
                    viewModel.connectToDevice(device)
                },
                onDisconnectClick = { device: BleDeviceModel ->
                    viewModel.disconnectFromDevice(device.address)
                },
                lazyListState = lazyListState,
                onRefresh = { refreshDevices() },
                isRefreshing = isRefreshing
            )
        }
    }
}

private fun getRequiredPermissions(context: Context): List<String> {
    val requiredPermissions = mutableListOf<String>()

    requiredPermissions.addAll(
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        requiredPermissions.addAll(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    return requiredPermissions
}

@Composable
fun BluetoothStatusCard(
    state: BluetoothState,
    hasPermissions: Boolean,
    onEnableClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = when (state) {
                            BluetoothState.Enabled -> R.drawable.outline_bluetooth_24
                            else -> R.drawable.outline_bluetooth_disabled_24
                        }
                    ),
                    contentDescription = "Bluetooth Status",
                    modifier = Modifier.size(24.dp),
                    tint = when {
                        state is BluetoothState.Enabled -> PrimaryColor
                        !hasPermissions -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    }
                )

                Column {
                    Text(
                        text = when (state) {
                            is BluetoothState.Unsupported -> "BLE не поддерживается"
                            is BluetoothState.Disabled -> "Bluetooth выключен"
                            is BluetoothState.Enabled -> "Bluetooth включен"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = when {
                            !hasPermissions -> "Требуются разрешения"
                            state is BluetoothState.Unsupported -> "Устройство не поддерживает BLE"
                            state is BluetoothState.Disabled -> "Нажмите для включения"
                            state is BluetoothState.Enabled -> "Готов к сканированию BLE"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state is BluetoothState.Disabled && hasPermissions) {
                Button(
                    onClick = onEnableClick,
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryColor,
                        contentColor = TextColor
                    )
                ) {
                    Text("Включить")
                }
            }
        }
    }
}

@Composable
fun FilterSection(
    nameFilter: String,
    onNameFilterChange: (String) -> Unit,
    showOnlyConnectable: Boolean,
    onShowOnlyConnectableChange: (Boolean) -> Unit,
    onClearFilters: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Фильтры поиска",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onClearFilters,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Очистить фильтры",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Фильтр по имени
            OutlinedTextField(
                value = nameFilter,
                onValueChange = onNameFilterChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Имя устройства...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Поиск")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onDone = { /* Действие при завершении */ }
                )
            )

            // Фильтр по connectable
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Только подключаемые устройства",
                    style = MaterialTheme.typography.bodyMedium
                )

                Switch(
                    checked = showOnlyConnectable,
                    onCheckedChange = onShowOnlyConnectableChange
                )
            }
        }
    }
}

@Composable
fun ScanProgress(
    progress: Float,
    devicesCount: Int,
    totalDevicesCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Сканирование...",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = PrimaryColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Найдено: $devicesCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Всего в кэше: $totalDevicesCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ScanStats(
    totalDevices: Int,
    filteredDevices: Int,
    connectedDevices: Int,
    isScanning: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem(
                title = "Всего",
                value = totalDevices.toString(),
                icon = Icons.Default.AccountCircle,
                color = MaterialTheme.colorScheme.primary
            )

            StatItem(
                title = "Найдено",
                value = filteredDevices.toString(),
                icon = Icons.Default.Search,
                color = if (filteredDevices > 0) PrimaryColor else MaterialTheme.colorScheme.outline
            )

            StatItem(
                title = "Подкл.",
                value = connectedDevices.toString(),
                icon = Icons.Default.Done,
                color = if (connectedDevices > 0) PrimaryColor else MaterialTheme.colorScheme.outline
            )

            StatItem(
                title = "Статус",
                value = if (isScanning) "🔍" else "⏸️",
                icon = if (isScanning) Icons.Default.PlayArrow else Icons.Default.Clear,
                color = if (isScanning) PrimaryColor else MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun StatItem(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(20.dp),
            tint = color
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )

        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BleDeviceList(
    devices: List<BleDeviceModel>,
    pairedDevices: List<BleDeviceModel>,
    isScanning: Boolean,
    connectedDeviceAddress: String?,
    selectedDeviceAddress: String?,
    onDeviceClick: (BleDeviceModel) -> Unit,
    onDeviceLongClick: (BleDeviceModel) -> Unit,
    onConnectClick: (BleDeviceModel) -> Unit,
    onDisconnectClick: (BleDeviceModel) -> Unit,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    val allDevices = remember(devices, pairedDevices) {
        // Объединяем устройства, убирая дубликаты
        val deviceMap = mutableMapOf<String, BleDeviceModel>()

        // Сначала добавляем сопряженные устройства
        pairedDevices.forEach { deviceMap[it.address] = it }

        // Затем добавляем/обновляем обнаруженные устройства
        devices.forEach { deviceMap[it.address] = it }

        deviceMap.values.toList().sortedByDescending {
            // Сортировка: подключенные -> выбранные -> по RSSI
            when {
                it.address == connectedDeviceAddress -> 3
                it.address == selectedDeviceAddress -> 2
                else -> 1
            } }.sortedByDescending { it.rssi }
    }

    if (allDevices.isEmpty() && !isScanning) {
        EmptyState(onRefresh = onRefresh)
    } else {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Кнопка обновления вверху
            item {
                RefreshButton(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(allDevices, key = { it.address }) { device ->
                val isConnected = device.address == connectedDeviceAddress
                val isSelected = device.address == selectedDeviceAddress

                BleDeviceItem(
                    device = device,
                    isConnected = isConnected,
                    isSelected = isSelected,
                    onClick = { onDeviceClick(device) },
                    onLongClick = { onDeviceLongClick(device) },
                    onConnectClick = { onConnectClick(device) },
                    onDisconnectClick = { onDisconnectClick(device) }
                )
            }

            if (isScanning && allDevices.isEmpty()) {
                item {
                    ScanningPlaceholder()
                }
            }
        }
    }
}

@Composable
fun RefreshButton(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Обновить",
                        tint = PrimaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (isRefreshing) "Обновление..." else "Обновить список",
                style = MaterialTheme.typography.bodyMedium,
                color = PrimaryColor
            )
        }
    }
}

@Composable
fun BleDeviceItem(
    device: BleDeviceModel,
    isConnected: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val backgroundColor = when {
        isConnected -> PrimaryColor.copy(alpha = 0.1f)
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        isConnected -> PrimaryColor
        isSelected -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Первая строка: имя и действия
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConnectionStatusIcon(
                        connectionState = device.connectionState,
                        isConnected = isConnected
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name ?: "Неизвестное устройство",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )

                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                // Кнопки действий
                DeviceActions(
                    isConnected = isConnected,
                    isConnectable = device.isConnectable,
                    connectionState = device.connectionState,
                    onConnectClick = onConnectClick,
                    onDisconnectClick = onDisconnectClick
                )
            }

            // Вторая строка: детали
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // RSSI индикатор
                RssiIndicator(rssi = device.rssi)

                // Статус
                Text(
                    text = when {
                        isConnected -> "Подключено ✓"
                        device.connectionState == ConnectionState.CONNECTING -> "Подключение..."
                        device.isConnectable -> "Готово к подключению"
                        else -> "Только сканирование"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isConnected -> PrimaryColor
                        device.connectionState == ConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                // Время обновления
                val timeAgo = formatTimeAgo(device.timestamp)
                Text(
                    text = timeAgo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ConnectionStatusIcon(
    connectionState: ConnectionState,
    isConnected: Boolean
) {
    val iconRes = when {
        isConnected -> R.drawable.outline_bluetooth_24
        connectionState == ConnectionState.CONNECTING -> R.drawable.outline_bluetooth_searching_24
        else -> R.drawable.outline_bluetooth_disabled_24
    }

    val tint = when {
        isConnected -> PrimaryColor
        connectionState == ConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = "Статус подключения",
        modifier = Modifier.size(24.dp),
        tint = tint
    )
}

@Composable
fun DeviceActions(
    isConnected: Boolean,
    isConnectable: Boolean,
    connectionState: ConnectionState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    when {
        isConnected -> {
            IconButton(
                onClick = onDisconnectClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.outline_bluetooth_disabled_24),
                    contentDescription = "Отключить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        connectionState == ConnectionState.CONNECTING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = PrimaryColor
            )
        }

        isConnectable -> {
            IconButton(
                onClick = onConnectClick,
                modifier = Modifier.size(36.dp),
                enabled = connectionState != ConnectionState.CONNECTING
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.outline_bluetooth_24),
                    contentDescription = "Подключить",
                    tint = PrimaryColor
                )
            }
        }
    }
}

@Composable
fun RssiIndicator(rssi: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Индикатор уровня сигнала
        val barCount = when {
            rssi >= -50 -> 4
            rssi >= -60 -> 3
            rssi >= -70 -> 2
            rssi >= -80 -> 1
            else -> 0
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height((index + 1) * 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            color = if (index < barCount) {
                                when {
                                    rssi >= -50 -> Color(0xFF4CAF50) // Отличный
                                    rssi >= -70 -> Color(0xFFFF9800) // Хороший
                                    else -> Color(0xFFF44336) // Слабый
                                }
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                )
            }
        }

        Text(
            text = "${rssi} dBm",
            style = MaterialTheme.typography.labelSmall,
            color = when {
                rssi >= -50 -> Color(0xFF4CAF50)
                rssi >= -70 -> Color(0xFFFF9800)
                else -> Color(0xFFF44336)
            }
        )
    }
}

@Composable
fun EmptyState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.outline_bluetooth_disabled_24),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Устройства не найдены",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Нажмите кнопку сканирования или обновите список",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRefresh,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryColor,
                contentColor = TextColor
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null
                )
                Text("Обновить")
            }
        }
    }
}

@Composable
fun ScanningPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.dp,
            color = PrimaryColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Сканирование устройств...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Поднесите устройство ближе",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 1000 -> "только что"
        diff < 60000 -> "${diff / 1000} сек назад"
        diff < 3600000 -> "${diff / 60000} мин назад"
        diff < 86400000 -> "${diff / 3600000} ч назад"
        else -> "${diff / 86400000} дн назад"
    }
}