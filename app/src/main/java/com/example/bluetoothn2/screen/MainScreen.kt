package com.example.bluetoothn2.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bluetoothn2.R
import com.example.bluetoothn2.model.BleDeviceModel
import com.example.bluetoothn2.model.ConnectionState
import com.example.bluetoothn2.viewmodel.BluetoothState
import com.example.bluetoothn2.viewmodel.BluetoothViewModel
import com.example.bluetoothn2.viewmodel.ConnectedDeviceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MainScreen(
    viewModel: BluetoothViewModel,
    onNavigateToConnectedDevice: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Запрос разрешений
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.onPermissionsGranted()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Необходимы разрешения Bluetooth")
            }
        }
    }

    // Включение Bluetooth
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.refreshBluetoothState()
    }

    // Проверка разрешений при старте
    LaunchedEffect(Unit) {
        if (!uiState.hasPermissions) {
            val requiredPermissions = getRequiredPermissions(context)
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    // Показ сообщений
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Scanner") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    // Кнопка версии
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "Версия 1.080426.0950", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Версия",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    // Кнопка сканирования
                    IconButton(
                        onClick = {
                            when (uiState.bluetoothState) {
                                BluetoothState.Enabled -> {
                                    if (uiState.isScanning) viewModel.stopScanning()
                                    else viewModel.startScanning()
                                }
                                else -> {
                                    viewModel.getEnableBluetoothIntent()?.let {
                                        enableBluetoothLauncher.launch(it)
                                    }
                                }
                            }
                        },
                        enabled = uiState.hasPermissions
                    ) {
                        if (uiState.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.refresh),
                                contentDescription = "Сканировать",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    // Индикатор Bluetooth
                    Icon(
                        painter = painterResource(
                            id = when (uiState.bluetoothState) {
                                BluetoothState.Enabled -> R.drawable.outline_bluetooth_24
                                else -> R.drawable.outline_bluetooth_disabled_24
                            }
                        ),
                        contentDescription = "Bluetooth",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Список устройств
            BleDeviceSelectionList(
                devices = uiState.filteredDevices,
                isScanning = uiState.isScanning,
                connectedDeviceAddress = uiState.connectedDeviceAddress,
                selectedDeviceAddress = uiState.selectedDeviceAddress,
                onDeviceSelected = { device ->
                    viewModel.setSelectedDevice(device.address)
                },
                modifier = Modifier.weight(1f)
            )
            val connectedDeviceViewModel: ConnectedDeviceViewModel = viewModel()
            // Кнопка подключения
            val selectedDevice = uiState.selectedDeviceAddress?.let { address ->
                uiState.filteredDevices.find { it.address == address }
            }

            Button(
                onClick = {
                    selectedDevice?.let {
                        device ->
                        viewModel.connectToDevice(device)
                        onNavigateToConnectedDevice(device.address)
                    }
                },
                enabled = selectedDevice != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.outline_bluetooth_24),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Подключиться",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun getRequiredPermissions(context: Context): List<String> {
    val permissions = mutableListOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.addAll(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        )
    } else {
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    return permissions
}

@Composable
fun BleDeviceSelectionList(
    devices: List<BleDeviceModel>,
    isScanning: Boolean,
    connectedDeviceAddress: String?,
    selectedDeviceAddress: String?,
    onDeviceSelected: (BleDeviceModel) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (devices.isEmpty() && !isScanning) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Нажмите кнопку сканирования", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            items(devices, key = { it.address }) { device ->
                SelectableDeviceItem(
                    device = device,
                    isConnected = device.address == connectedDeviceAddress,
                    isSelected = device.address == selectedDeviceAddress,
                    onClick = { onDeviceSelected(device) }
                )
            }
        }
    }
}

@Composable
fun SelectableDeviceItem(
    device: BleDeviceModel,
    isConnected: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        isConnected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = backgroundColor,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Неизвестное",
                    fontWeight = if (isConnected || isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Text(
                    text = device.address,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${device.rssi} dBm",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    SignalStrengthIndicator(rssi = device.rssi)
                }
            }

            // Индикаторы: слева - подключение, справа - выбор
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Индикатор подключения (зеленый кружок)
                Surface(
                    modifier = Modifier.size(12.dp),
                    shape = CircleShape,
                    color = if (isConnected) Color.Green else Color.Gray
                ) {}

                Spacer(modifier = Modifier.width(8.dp))

                // Индикатор выбора (галочка)
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Выбрано",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SignalStrengthIndicator(rssi: Int) {
    val bars = when {
        rssi >= -50 -> 4
        rssi >= -60 -> 3
        rssi >= -70 -> 2
        rssi >= -80 -> 1
        else -> 0
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(((index + 1) * 4).dp)
                    .background(
                        if (index < bars) Color.Green
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}