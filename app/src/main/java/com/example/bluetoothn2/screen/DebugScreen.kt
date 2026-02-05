package com.example.bluetoothn2.screen

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluetoothn2.model.ConnectionState
import com.example.bluetoothn2.viewmodel.BluetoothViewModel
import kotlinx.coroutines.launch

@Composable
fun DebugScreen(viewModel: BluetoothViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "BLE Debug Information",
            style = MaterialTheme.typography.headlineMedium
        )

        // Статус Bluetooth
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Bluetooth Status",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("State: ${uiState.bluetoothState}")
                Text("Enabled: ${uiState.bluetoothState is com.example.bluetoothn2.viewmodel.BluetoothState.Enabled}")
                Text("Permissions: ${uiState.hasPermissions}")
                Text("Scanning: ${uiState.isScanning}")
                Text("Connected Device: ${uiState.connectedDeviceAddress}")
            }
        }

        // Устройства
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Devices",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Discovered: ${uiState.discoveredDevices.size}")
                Text("Paired: ${uiState.pairedDevices.size}")
                Text("Filtered: ${uiState.filteredDevices.size}")

                Spacer(modifier = Modifier.height(8.dp))
                if (uiState.discoveredDevices.isNotEmpty()) {
                    Text("Last 5 devices:")
                    uiState.discoveredDevices.take(5).forEach { device ->
                        Text("  ${device.name ?: "Unknown"} (${device.address}) RSSI: ${device.rssi}")
                    }
                }
            }
        }

        // Действия для отладки
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Debug Actions",
                    style = MaterialTheme.typography.titleLarge
                )

                Button(
                    onClick = { viewModel.startScanning() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isScanning
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("Start Scan")
                    }
                }

                Button(
                    onClick = { viewModel.stopScanning() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.isScanning
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Text("Stop Scan")
                    }
                }

                Button(
                    onClick = { viewModel.refreshDevices() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("Refresh")
                    }
                }

                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Text("Open Bluetooth Settings")
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                            Text("Location Permissions")
                        }
                    }
                }
            }
        }

        // Логи
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Status Log",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(uiState.errorMessage ?: "No errors")
                Text(uiState.successMessage ?: "No success messages")
            }
        }
    }
}