package com.example.bluetoothn2.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothn2.model.BleDeviceModel
import com.example.bluetoothn2.model.ConnectionState
import com.example.bluetoothn2.repository.BluetoothRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Состояния Bluetooth
sealed class BluetoothState {
    object Unsupported : BluetoothState()
    object Disabled : BluetoothState()
    object Enabled : BluetoothState()
}

// Фильтры сканирования
data class ScanFilters(
    val nameFilter: String = "",
    val showOnlyConnectable: Boolean = false,
    val minRssi: Int = -100,
    val maxRssi: Int = 0
)

data class BluetoothUIState(
    val bluetoothState: BluetoothState = BluetoothState.Disabled,
    val isScanning: Boolean = false,
    val discoveredDevices: List<BleDeviceModel> = emptyList(),
    val filteredDevices: List<BleDeviceModel> = emptyList(),
    val pairedDevices: List<BleDeviceModel> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val scanProgress: Float = 0f,
    val hasPermissions: Boolean = false,
    val connectingDeviceAddress: String? = null,
    val connectedDeviceAddress: String? = null,
    val scanFilters: ScanFilters = ScanFilters(),
    val isRefreshing: Boolean = false,
    val selectedDeviceAddress: String? = null
)

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BluetoothRepository(application.applicationContext)
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(BluetoothUIState())
    val uiState: StateFlow<BluetoothUIState> = _uiState.asStateFlow()

    private var scanningJob: Job? = null
    private var refreshJob: Job? = null
    private val scanDuration = 30_000L // 30 секунд

    // Карта состояний подключений для всех устройств
    private val deviceConnectionStates = mutableMapOf<String, ConnectionState>()

    init {
        checkPermissions()
        checkBluetoothState()
        loadPairedDevices()
        setupObservers()
    }

    private fun setupObservers() {
        // Следим за изменениями состояния подключения
        viewModelScope.launch {
            repository.connectionState.collect { connectionUpdate ->
                connectionUpdate?.let { (deviceAddress, connectionState) ->
                    deviceConnectionStates[deviceAddress] = connectionState

                    when (connectionState) {
                        ConnectionState.CONNECTED -> {
                            _uiState.update {
                                it.copy(
                                    connectedDeviceAddress = deviceAddress,
                                    successMessage = "Устройство подключено"
                                )
                            }
                        }
                        ConnectionState.DISCONNECTED -> {
                            if (_uiState.value.connectedDeviceAddress == deviceAddress) {
                                _uiState.update {
                                    it.copy(
                                        connectedDeviceAddress = null,
                                        successMessage = "Устройство отключено"
                                    )
                                }
                            }
                        }
                        else -> {}
                    }

                    refreshDeviceLists()
                }
            }
        }

        // Следим за полученными данными
        viewModelScope.launch {
            repository.dataReceived.collect { dataUpdate ->
                // Можно обрабатывать полученные данные если нужно
            }
        }

        // Следим за изменениями в отсканированных устройствах
        viewModelScope.launch {
            repository.discoveredDevices.collect { devices ->
                _uiState.update { currentState ->
                    val updatedDevices = devices.map { device ->
                        val connectionState = deviceConnectionStates[device.address] ?: ConnectionState.DISCONNECTED
                        device.copy(connectionState = connectionState)
                    }

                    val filtered = applyFilters(updatedDevices, currentState.scanFilters)

                    currentState.copy(
                        discoveredDevices = updatedDevices,
                        filteredDevices = filtered
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.cleanup()
        scanningJob?.cancel()
        refreshJob?.cancel()
    }

    private fun checkPermissions() {
        val hasPermissions = hasBluetoothPermissions()
        _uiState.update { it.copy(hasPermissions = hasPermissions) }
    }

    private fun hasBluetoothPermissions(): Boolean {
        val requiredPermissions = getRequiredPermissions()
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        // Базовые разрешения для всех версий
        permissions.addAll(
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            permissions.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        }

        // Location permission для сканирования BLE (требуется до Android 12)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return permissions
    }

    private fun checkBluetoothState() {
        try {
            when {
                !repository.isBluetoothSupported() -> {
                    _uiState.update {
                        it.copy(bluetoothState = BluetoothState.Unsupported)
                    }
                }
                !repository.isBluetoothEnabled() -> {
                    _uiState.update {
                        it.copy(bluetoothState = BluetoothState.Disabled)
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(bluetoothState = BluetoothState.Enabled)
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(bluetoothState = BluetoothState.Unsupported)
            }
        }
    }

    fun loadPairedDevices() {
        if (!hasBluetoothPermissions()) {
            return
        }

        try {
            val pairedDevices = repository.getPairedDevices()
            _uiState.update {
                it.copy(pairedDevices = pairedDevices)
            }
            applyFiltersAndUpdate()
        } catch (e: SecurityException) {
            _uiState.update {
                it.copy(
                    errorMessage = "Нет разрешений для доступа к Bluetooth",
                    pairedDevices = emptyList()
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    errorMessage = "Ошибка при загрузке сопряженных устройств",
                    pairedDevices = emptyList()
                )
            }
        }
    }

    private fun refreshDeviceLists() {
        loadPairedDevices()
        applyFiltersAndUpdate()
    }

    private fun applyFilters(
        devices: List<BleDeviceModel>,
        filters: ScanFilters
    ): List<BleDeviceModel> {
        return devices.filter { device ->
            // Фильтр по имени
            val nameMatches = filters.nameFilter.isEmpty() ||
                    device.name?.contains(filters.nameFilter, ignoreCase = true) == true

            // Фильтр по connectable
            val connectableMatches = !filters.showOnlyConnectable || device.isConnectable

            // Фильтр по RSSI
            val rssiMatches = device.rssi in filters.minRssi..filters.maxRssi

            nameMatches && connectableMatches && rssiMatches
        }.sortedByDescending { it.rssi } // Сортировка по силе сигнала
    }

    private fun applyFiltersAndUpdate() {
        _uiState.update { currentState ->
            val allDevices = currentState.discoveredDevices + currentState.pairedDevices
            val filtered = applyFilters(allDevices, currentState.scanFilters)

            currentState.copy(filteredDevices = filtered)
        }
    }

    fun updateScanFilters(filters: ScanFilters) {
        _uiState.update {
            it.copy(scanFilters = filters)
        }
        applyFiltersAndUpdate()
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(scanFilters = ScanFilters())
        }
        applyFiltersAndUpdate()
    }

    fun startScanning() {
        if (!hasBluetoothPermissions()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Требуются разрешения для сканирования Bluetooth"
                )
            }
            return
        }

        if (!repository.isBluetoothEnabled()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Bluetooth выключен"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isScanning = true,
                errorMessage = null,
                successMessage = "Сканирование начато",
                scanProgress = 0f
            )
        }

        scanningJob = viewModelScope.launch {
            // Таймер прогресса сканирования
            val timerJob = launch {
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < scanDuration) {
                    val progress = (System.currentTimeMillis() - startTime).toFloat() / scanDuration
                    _uiState.update { it.copy(scanProgress = progress) }
                    delay(100)
                }
                // Автоматическая остановка сканирования
                stopScanning()
                _uiState.update { it.copy(successMessage = "Сканирование завершено") }
            }

            // Запускаем сканирование
            repository.startContinuousScan(scanDuration)

            // Ждем завершения таймера
            timerJob.join()
        }
    }

    fun startQuickScan() {
        if (!hasBluetoothPermissions()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Требуются разрешения для сканирования Bluetooth"
                )
            }
            return
        }

        if (!repository.isBluetoothEnabled()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Bluetooth выключен"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isScanning = true,
                errorMessage = null,
                successMessage = "Быстрое сканирование начато",
                scanProgress = 0f
            )
        }

        scanningJob?.cancel()
        scanningJob = viewModelScope.launch {
            // Быстрое сканирование на 10 секунд
            val quickScanDuration = 10000L

            val timerJob = launch {
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < quickScanDuration) {
                    val progress = (System.currentTimeMillis() - startTime).toFloat() / quickScanDuration
                    _uiState.update { it.copy(scanProgress = progress) }
                    delay(100)
                }
                stopScanning()
                _uiState.update { it.copy(successMessage = "Быстрое сканирование завершено") }
            }

            repository.startContinuousScan(quickScanDuration)
            timerJob.join()
        }
    }

    fun stopScanning() {
        scanningJob?.cancel()
        scanningJob = null
        repository.stopScan()
        _uiState.update {
            it.copy(
                isScanning = false,
                scanProgress = 0f
            )
        }
    }

    fun refreshDevices() {
        if (_uiState.value.isRefreshing) return

        _uiState.update { it.copy(isRefreshing = true) }

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                // Очищаем кэш и начинаем быстрое сканирование
                repository.clearCache()
                startQuickScan()

                // Задержка для завершения сканирования
                delay(500)
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun connectToDevice(device: BleDeviceModel) {
        if (!hasBluetoothPermissions()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Требуются разрешения для подключения к устройству"
                )
            }
            return
        }

        if (!repository.isBluetoothEnabled()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Bluetooth выключен"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                connectingDeviceAddress = device.address,
                selectedDeviceAddress = device.address,
                successMessage = "Подключение к ${device.name}..."
            )
        }

        viewModelScope.launch {
            val result = repository.connectToDevice(device.address)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        connectingDeviceAddress = null,
                        errorMessage = "Ошибка подключения: ${error?.message ?: "неизвестная ошибка"}"
                    )
                }
            }
        }
    }

    fun selectDevice(deviceAddress: String) {
        _uiState.update {
            it.copy(selectedDeviceAddress = deviceAddress)
        }
    }

    fun disconnectFromDevice(deviceAddress: String) {
        viewModelScope.launch {
            val result = repository.disconnectFromDevice(deviceAddress)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        errorMessage = "Ошибка отключения: ${error?.message ?: "неизвестная ошибка"}"
                    )
                }
            } else {
                deviceConnectionStates.remove(deviceAddress)

                if (_uiState.value.connectedDeviceAddress == deviceAddress) {
                    _uiState.update {
                        it.copy(
                            connectedDeviceAddress = null,
                            selectedDeviceAddress = null,
                            successMessage = "Устройство отключено"
                        )
                    }
                }
                refreshDeviceLists()
            }
        }
    }

    fun disconnectFromCurrentDevice() {
        val connectedDevice = _uiState.value.connectedDeviceAddress
        if (connectedDevice != null) {
            disconnectFromDevice(connectedDevice)
        } else {
            _uiState.update {
                it.copy(
                    errorMessage = "Нет подключенных устройств"
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun refreshBluetoothState() {
        checkBluetoothState()
        checkPermissions()
        if (hasBluetoothPermissions() && repository.isBluetoothEnabled()) {
            loadPairedDevices()
        }
    }

    fun getEnableBluetoothIntent(): Intent? {
        return try {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        } catch (e: Exception) {
            null
        }
    }

    fun onPermissionsGranted() {
        checkPermissions()
        if (hasBluetoothPermissions()) {
            loadPairedDevices()
        }
    }

    fun cleanupDeviceState(deviceAddress: String) {
        deviceConnectionStates.remove(deviceAddress)

        if (_uiState.value.connectedDeviceAddress == deviceAddress) {
            _uiState.update {
                it.copy(
                    connectedDeviceAddress = null,
                    connectingDeviceAddress = null,
                    selectedDeviceAddress = null,
                    errorMessage = null
                )
            }
        }

        refreshDeviceLists()
    }

    fun getDeviceConnectionState(deviceAddress: String): ConnectionState {
        return deviceConnectionStates[deviceAddress] ?: ConnectionState.DISCONNECTED
    }

    fun getDeviceByName(name: String): BleDeviceModel? {
        return (_uiState.value.discoveredDevices + _uiState.value.pairedDevices)
            .find { it.name?.equals(name, ignoreCase = true) == true }
    }

    fun getDeviceByAddress(address: String): BleDeviceModel? {
        return (_uiState.value.discoveredDevices + _uiState.value.pairedDevices)
            .find { it.address == address }
    }

    fun addCustomDevice(device: BleDeviceModel) {
        repository.addOrUpdateDevice(device)
        refreshDeviceLists()
    }

    fun getIsScanning(): Boolean = repository.getIsScanning()
}