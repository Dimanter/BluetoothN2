package com.example.bluetoothn2.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothn2.model.BleDeviceModel
import com.example.bluetoothn2.model.ConnectionState
import com.example.bluetoothn2.repository.BluetoothRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ConnectedDeviceUIState(
    val device: BleDeviceModel? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isReconnecting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val commandResult: String? = null,
    val receivedData: String = "",
    val lastCommandResponse: String? = null,
    val isWaitingForResponse: Boolean = false,
    val autoConnect: Boolean = true,
    val debugLog: List<String> = emptyList(),
    val isReceivingData: Boolean = false,
    val bytesReceived: Int = 0
)

class ConnectedDeviceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BluetoothRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(ConnectedDeviceUIState())
    val uiState: StateFlow<ConnectedDeviceUIState> = _uiState.asStateFlow()

    private var deviceAddress: String = ""
    private var responseTimerJob: Job? = null
    private var autoConnectJob: Job? = null
    private var dataMonitoringJob: Job? = null

    private var totalBytesReceived = 0

    init {
        startConnectionMonitoring()
        startDataMonitoring()
    }

    fun setDeviceAddress(address: String) {
        deviceAddress = address
        loadDeviceInfo()

        if (_uiState.value.autoConnect && _uiState.value.connectionState == ConnectionState.DISCONNECTED) {
            autoConnect()
        }
    }

    fun updateConnectionStateFromMain(deviceAddress: String, connectionState: ConnectionState) {
        if (this.deviceAddress == deviceAddress) {
            _uiState.update {
                it.copy(connectionState = connectionState)
            }
        }
    }

    private fun loadDeviceInfo() {
        viewModelScope.launch {
            try {
                val device = repository.getDeviceFromCache(deviceAddress)
                val connectionState = repository.getConnectionState(deviceAddress)

                _uiState.update {
                    it.copy(
                        device = device ?: BleDeviceModel(
                            name = "Устройство $deviceAddress",
                            address = deviceAddress,
                            rssi = 0,
                            isConnectable = true,
                            timestamp = System.currentTimeMillis(),
                            connectionState = ConnectionState.DISCONNECTED
                        ),
                        connectionState = connectionState
                    )
                }
            } catch (e: Exception) {
                addDebugLog("Error loading device info: ${e.message}")
            }
        }
    }

    private fun startConnectionMonitoring() {
        viewModelScope.launch {
            repository.connectionState.collect { connectionUpdate ->
                connectionUpdate?.let { (address, state) ->
                    if (address == deviceAddress) {
                        _uiState.update {
                            it.copy(connectionState = state)
                        }

                        addDebugLog("Connection state: $state")

                        when (state) {
                            ConnectionState.DISCONNECTED -> {
                                _uiState.update { it.copy(isReconnecting = false) }
                                if (_uiState.value.autoConnect) {
                                    delay(2000)
                                    autoConnect()
                                }
                            }
                            ConnectionState.CONNECTED -> {
                                _uiState.update {
                                    it.copy(
                                        successMessage = "Устройство подключено"
                                    )
                                }
                                // Очищаем старые данные при новом подключении
                                clearReceivedData()
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun startDataMonitoring() {
        dataMonitoringJob?.cancel()
        dataMonitoringJob = viewModelScope.launch {
            repository.dataReceived.collect { dataUpdate ->
                dataUpdate?.let { (address, data) ->
                    if (address == deviceAddress && data.isNotBlank()) {
                        // Обновляем счетчики
                        totalBytesReceived += data.length

                        addDebugLog("Data received: ${if (data.length > 50) data.take(50) + "..." else data}")

                        // Устанавливаем флаг получения данных
                        _uiState.update {
                            it.copy(
                                isReceivingData = true,
                                bytesReceived = totalBytesReceived
                            )
                        }

                        // Сбрасываем флаг через 300ms
                        viewModelScope.launch {
                            delay(300)
                            _uiState.update {
                                it.copy(isReceivingData = false)
                            }
                        }

                        // Обработка ответов на команды
                        if (_uiState.value.isWaitingForResponse) {
                            _uiState.update {
                                it.copy(
                                    lastCommandResponse = data,
                                    isWaitingForResponse = false,
                                    successMessage = "Получен ответ"
                                )
                            }
                            responseTimerJob?.cancel()
                        }

                        // Добавляем данные в историю
                        _uiState.update { currentState ->
                            val newData = if (currentState.receivedData.isEmpty()) {
                                data
                            } else {
                                currentState.receivedData + "\n" + data
                            }
                            // Ограничиваем длину для предотвращения утечек памяти
                            val limitedData = if (newData.length > 10000) {
                                newData.substring(newData.length - 8000)
                            } else {
                                newData
                            }
                            currentState.copy(receivedData = limitedData)
                        }
                    }
                }
            }
        }
    }

    fun autoConnect() {
        if (_uiState.value.connectionState != ConnectionState.DISCONNECTED) return

        _uiState.update {
            it.copy(
                isReconnecting = true,
                debugLog = it.debugLog + "Auto-connect initiated"
            )
        }

        autoConnectJob?.cancel()
        autoConnectJob = viewModelScope.launch {
            try {
                delay(500)
                val result = repository.connectToDevice(deviceAddress)
                if (result.isFailure) {
                    addDebugLog("Auto-connect failed: ${result.exceptionOrNull()?.message}")
                    _uiState.update {
                        it.copy(
                            isReconnecting = false,
                            errorMessage = "Автоподключение не удалось"
                        )
                    }
                }
            } catch (e: Exception) {
                addDebugLog("Auto-connect error: ${e.message}")
                _uiState.update {
                    it.copy(
                        isReconnecting = false,
                        errorMessage = "Ошибка автоподключения"
                    )
                }
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isReconnecting = true,
                    errorMessage = null,
                    successMessage = "Подключение..."
                )
            }

            try {
                val result = repository.connectToDevice(deviceAddress)
                if (result.isFailure) {
                    addDebugLog("Connect failed: ${result.exceptionOrNull()?.message}")
                    _uiState.update {
                        it.copy(
                            isReconnecting = false,
                            errorMessage = "Ошибка подключения"
                        )
                    }
                }
            } catch (e: Exception) {
                addDebugLog("Connect error: ${e.message}")
                _uiState.update {
                    it.copy(
                        isReconnecting = false,
                        errorMessage = "Ошибка подключения"
                    )
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    errorMessage = null,
                    successMessage = "Отключение..."
                )
            }

            try {
                val result = repository.disconnectFromDevice(deviceAddress)
                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.DISCONNECTED,
                            successMessage = "Устройство отключено"
                        )
                    }
                }
            } catch (e: Exception) {
                addDebugLog("Disconnect error: ${e.message}")
            }
        }
    }

    fun sendCommand(command: String) {
        viewModelScope.launch {
            val cleanCommand = command.trim()
            if (cleanCommand.isEmpty()) return@launch

            _uiState.update {
                it.copy(
                    errorMessage = null,
                    lastCommandResponse = null,
                    isWaitingForResponse = true,
                    successMessage = "Отправка команды...",
                    debugLog = it.debugLog + "Sending: $cleanCommand"
                )
            }

            // Таймаут ожидания ответа
            responseTimerJob = viewModelScope.launch {
                delay(5000)
                if (_uiState.value.isWaitingForResponse) {
                    _uiState.update {
                        it.copy(
                            isWaitingForResponse = false,
                            errorMessage = "Таймаут ожидания ответа"
                        )
                    }
                }
            }

            try {
                val result = repository.sendData(deviceAddress, cleanCommand)
                if (result.isSuccess) {
                    addDebugLog("Command sent: $cleanCommand")
                    _uiState.update {
                        it.copy(
                            successMessage = "Команда отправлена",
                            debugLog = it.debugLog + "Command sent"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isWaitingForResponse = false,
                            errorMessage = "Ошибка отправки"
                        )
                    }
                    responseTimerJob?.cancel()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isWaitingForResponse = false,
                        errorMessage = "Ошибка отправки: ${e.message}"
                    )
                }
                responseTimerJob?.cancel()
            }
        }
    }

    fun toggleAutoConnect() {
        _uiState.update {
            it.copy(
                autoConnect = !it.autoConnect
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun clearReceivedData() {
        _uiState.update {
            it.copy(
                receivedData = "",
                bytesReceived = 0
            )
        }
        totalBytesReceived = 0
        repository.clearBuffer()
    }

    fun clearResponse() {
        _uiState.update {
            it.copy(
                lastCommandResponse = null,
                isWaitingForResponse = false
            )
        }
        responseTimerJob?.cancel()
    }

    fun clearDebugLog() {
        _uiState.update { it.copy(debugLog = emptyList()) }
    }

    fun resetCounters() {
        totalBytesReceived = 0
        _uiState.update {
            it.copy(bytesReceived = 0)
        }
    }

    fun cleanup() {
        responseTimerJob?.cancel()
        autoConnectJob?.cancel()
        dataMonitoringJob?.cancel()
    }

    private fun addDebugLog(message: String) {
        val timestamp = System.currentTimeMillis()
        val timeStr = String.format("%tT", timestamp)
        _uiState.update {
            it.copy(debugLog = it.debugLog + "[$timeStr] $message")
        }
    }
}