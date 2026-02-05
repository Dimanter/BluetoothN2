package com.example.bluetoothn2.repository

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.bluetoothn2.model.BleDeviceModel
import com.example.bluetoothn2.model.ConnectionState
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("InlinedApi")
class BluetoothRepository(private val context: Context) {
    private val TAG = "BluetoothRepository"

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }

    private val bluetoothLeScanner: BluetoothLeScanner? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothAdapter?.bluetoothLeScanner
        } else {
            null
        }
    }

    private val devicesCache = ConcurrentHashMap<String, BleDeviceModel>()
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: android.os.CancellationSignal? = null

    // Потоки данных
    private val _discoveredDevices = MutableStateFlow<List<BleDeviceModel>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDeviceModel>> = _discoveredDevices.asStateFlow()

    private val _connectionState = MutableStateFlow<Pair<String, ConnectionState>?>(null)
    val connectionState: StateFlow<Pair<String, ConnectionState>?> = _connectionState.asStateFlow()

    private val _dataReceived = MutableStateFlow<Pair<String, String>?>(null)
    val dataReceived: StateFlow<Pair<String, String>?> = _dataReceived.asStateFlow()

    // BLE GATT объекты
    private var gatt: BluetoothGatt? = null
    private var connectedDeviceAddress: String? = null

    // Характеристики для обмена данными
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    // Буфер для накопления данных
    private val receivedDataBuffer = StringBuilder()
    private var bufferSize = 0

    companion object {
        // Стандартные UUID для Serial Port Service (SPP)
        val SPP_SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val SPP_WRITE_CHAR_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val SPP_READ_CHAR_UUID = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb")

        // Альтернативные UUID для разных устройств
        val SERVICE_UUID_1 = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
        val SERVICE_UUID_2 = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

        // UUID для дескриптора уведомлений
        val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            result.device?.let { device ->
                val deviceName = device.name ?: "Unknown"
                val deviceAddress = device.address
                val rssi = result.rssi

                Log.d(TAG, "Found device: $deviceName ($deviceAddress) RSSI: $rssi")

                val deviceModel = BleDeviceModel(
                    name = deviceName,
                    address = deviceAddress,
                    rssi = rssi,
                    isConnectable = result.isConnectable,
                    timestamp = System.currentTimeMillis()
                )

                devicesCache[deviceAddress] = deviceModel
                _discoveredDevices.value = devicesCache.values.toList()
                    .sortedByDescending { it.timestamp }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "BLE Scan failed with error: $errorCode")
            _isScanning.value = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            val deviceAddress = gatt.device.address
            Log.d(TAG, "Connection state changed for $deviceAddress: $newState (status: $status)")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server")
                    _connectionState.value = Pair(deviceAddress, ConnectionState.CONNECTED)

                    // Обнаруживаем сервисы
                    val success = gatt.discoverServices()
                    Log.d(TAG, "Discover services started: $success")
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server")
                    _connectionState.value = Pair(deviceAddress, ConnectionState.DISCONNECTED)
                    cleanupGatt()
                }

                BluetoothProfile.STATE_CONNECTING -> {
                    Log.i(TAG, "Connecting to GATT server")
                    _connectionState.value = Pair(deviceAddress, ConnectionState.CONNECTING)
                }

                BluetoothProfile.STATE_DISCONNECTING -> {
                    Log.i(TAG, "Disconnecting from GATT server")
                    _connectionState.value = Pair(deviceAddress, ConnectionState.DISCONNECTING)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered successfully")

                // Логируем все сервисы и характеристики
                logAllServices(gatt)

                // Находим и настраиваем характеристики
                findAndSetupCharacteristics(gatt)
            } else {
                Log.e(TAG, "Service discovery failed: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)

            val data = characteristic.value
            if (data != null && data.isNotEmpty()) {
                processReceivedData(data, "Notification")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            val dataStr = String(characteristic.value ?: byteArrayOf(), Charsets.UTF_8).trim()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Data written successfully: '$dataStr'")
            } else {
                Log.e(TAG, "Failed to write characteristic: $status")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write successful: ${descriptor.uuid}")
            } else {
                Log.e(TAG, "Descriptor write failed: $status")
            }
        }
    }

    private fun processReceivedData(data: ByteArray, source: String) {
        try {
            // Пытаемся декодировать как текст
            val text = String(data, Charsets.UTF_8).trim()
            if (text.isNotEmpty()) {
                Log.d(TAG, "$source received: '$text' (${data.size} bytes)")

                // Добавляем в буфер
                synchronized(receivedDataBuffer) {
                    if (receivedDataBuffer.isNotEmpty() && !receivedDataBuffer.endsWith("\n")) {
                        receivedDataBuffer.append("\n")
                    }
                    receivedDataBuffer.append(text)
                    bufferSize += text.length

                    // Ограничиваем размер буфера
                    if (bufferSize > 10000) {
                        val excess = bufferSize - 8000
                        var removed = 0
                        var index = 0
                        while (removed < excess && index < receivedDataBuffer.length) {
                            if (receivedDataBuffer[index] == '\n') {
                                removed += index + 1
                                receivedDataBuffer.delete(0, index + 1)
                                break
                            }
                            index++
                        }
                        bufferSize -= removed
                    }
                }

                // Отправляем данные через Flow
                connectedDeviceAddress?.let { address ->
                    _dataReceived.value = Pair(address, text)
                }
            } else {
                // Если текст пустой, показываем hex представление
                val hex = data.joinToString("") { "%02x".format(it) }
                Log.d(TAG, "$source received (hex): $hex (${data.size} bytes)")

                connectedDeviceAddress?.let { address ->
                    _dataReceived.value = Pair(address, "[HEX] $hex")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing received data", e)
            // Показываем hex представление при ошибке
            val hex = data.joinToString("") { "%02x".format(it) }
            connectedDeviceAddress?.let { address ->
                _dataReceived.value = Pair(address, "[HEX] $hex")
            }
        }
    }

    private fun logAllServices(gatt: BluetoothGatt) {
        Log.d(TAG, "=== ALL SERVICES AND CHARACTERISTICS ===")
        gatt.services?.forEachIndexed { serviceIndex, service ->
            Log.d(TAG, "Service #${serviceIndex + 1}: ${service.uuid}")

            service.characteristics?.forEachIndexed { charIndex, characteristic ->
                val properties = characteristic.properties
                val propStrings = mutableListOf<String>()

                if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) propStrings.add("READ")
                if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) propStrings.add("WRITE")
                if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) propStrings.add("WRITE_NO_RESPONSE")
                if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) propStrings.add("NOTIFY")
                if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) propStrings.add("INDICATE")

                Log.d(TAG, "  Characteristic #${charIndex + 1}: ${characteristic.uuid}")
                Log.d(TAG, "    Properties: ${propStrings.joinToString(", ")} (0x${properties.toString(16)})")

                // Показываем дескрипторы
                characteristic.descriptors?.forEach { descriptor ->
                    Log.d(TAG, "    Descriptor: ${descriptor.uuid}")
                }
            }
        }
        Log.d(TAG, "=== END SERVICES ===")
    }

    @SuppressLint("MissingPermission")
    private fun findAndSetupCharacteristics(gatt: BluetoothGatt) {
        Log.d(TAG, "Searching for characteristics...")

        val servicesToCheck = listOf(
            SPP_SERVICE_UUID,
            SERVICE_UUID_1,
            SERVICE_UUID_2
        )

        var foundService: BluetoothGattService? = null
        var foundWriteChar: BluetoothGattCharacteristic? = null
        var foundNotifyChar: BluetoothGattCharacteristic? = null

        // Ищем подходящие сервисы
        for (serviceUuid in servicesToCheck) {
            val service = gatt.getService(serviceUuid)
            if (service != null) {
                foundService = service
                Log.d(TAG, "Found service: $serviceUuid")
                break
            }
        }

        // Если не нашли специфичные сервисы, используем первый доступный
        if (foundService == null && gatt.services?.isNotEmpty() == true) {
            foundService = gatt.services!![0]
            Log.d(TAG, "Using first available service: ${foundService.uuid}")
        }

        if (foundService == null) {
            Log.e(TAG, "No services found!")
            return
        }

        // Ищем характеристики в найденном сервисе
        foundService.characteristics?.forEach { characteristic ->
            val properties = characteristic.properties

            // Характеристика для записи (отправки команд)
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                if (foundWriteChar == null) {
                    foundWriteChar = characteristic
                    Log.d(TAG, "Found write characteristic: ${characteristic.uuid}")
                }
            }

            // Характеристика для уведомлений (получения данных)
            if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                if (foundNotifyChar == null) {
                    foundNotifyChar = characteristic
                    Log.d(TAG, "Found notify characteristic: ${characteristic.uuid}")
                }
            }
        }

        writeCharacteristic = foundWriteChar
        notifyCharacteristic = foundNotifyChar

        // Включаем уведомления, если нашли характеристику
        if (notifyCharacteristic != null) {
            enableNotifications(gatt, notifyCharacteristic!!)
        } else {
            Log.w(TAG, "No notify characteristic found! Device won't send data automatically.")
        }

        // Увеличиваем MTU для получения больше данных
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            gatt.requestMtu(512)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        try {
            Log.d(TAG, "Enabling notifications for ${characteristic.uuid}")

            // Включаем уведомления
            val enabled = gatt.setCharacteristicNotification(characteristic, true)
            Log.d(TAG, "setCharacteristicNotification result: $enabled")

            if (enabled) {
                // Находим дескриптор для уведомлений
                val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                if (descriptor != null) {
                    // Определяем тип уведомлений
                    val value = when {
                        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 -> {
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        }
                        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 -> {
                            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        }
                        else -> {
                            Log.w(TAG, "Characteristic doesn't have NOTIFY or INDICATE property, trying NOTIFY anyway")
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        }
                    }

                    descriptor.value = value
                    val writeSuccess = gatt.writeDescriptor(descriptor)
                    Log.d(TAG, "writeDescriptor result: $writeSuccess")

                    if (writeSuccess) {
                        Log.i(TAG, "Notifications enabled successfully for ${characteristic.uuid}")
                    } else {
                        Log.e(TAG, "Failed to write descriptor for ${characteristic.uuid}")
                    }
                } else {
                    Log.e(TAG, "No descriptor found for ${characteristic.uuid}")
                }
            } else {
                Log.e(TAG, "Failed to enable notifications for ${characteristic.uuid}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling notifications", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun cleanupGatt() {
        // Отключаем уведомления перед закрытием
        if (gatt != null && notifyCharacteristic != null) {
            try {
                gatt?.setCharacteristicNotification(notifyCharacteristic, false)
                val descriptor = notifyCharacteristic?.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                descriptor?.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                gatt?.writeDescriptor(descriptor)
            } catch (e: Exception) {
                Log.e(TAG, "Error disabling notifications", e)
            }
        }

        gatt?.close()
        gatt = null
        connectedDeviceAddress = null
        writeCharacteristic = null
        notifyCharacteristic = null
        receivedDataBuffer.clear()
        bufferSize = 0
        Log.d(TAG, "GATT cleaned up")
    }

    @SuppressLint("MissingPermission")
    fun startContinuousScan(scanPeriod: Long = 15000L) {
        if (_isScanning.value) {
            Log.d(TAG, "Scan already in progress")
            return
        }

        scanJob?.cancel()

        Log.d(TAG, "Starting continuous scan for ${scanPeriod}ms")
        _isScanning.value = true

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val scanFilters = emptyList<ScanFilter>()

        try {
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            Log.d(TAG, "BLE scan started successfully")

            // Останавливаем сканирование через указанное время
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                stopScan()
            }, scanPeriod)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting scan: ${e.message}")
            _isScanning.value = false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException starting scan: ${e.message}")
            _isScanning.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting scan: ${e.message}")
            _isScanning.value = false
        }
    }

    fun stopScan() {
        Log.d(TAG, "Stopping scan...")
        scanJob?.cancel()

        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Scanner not started", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }

        _isScanning.value = false
        Log.d(TAG, "Scan stopped")
    }

    @SuppressLint("MissingPermission")
    suspend fun connectToDevice(deviceAddress: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Connecting to BLE device: $deviceAddress")

            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                ?: return@withContext Result.failure(Exception("Device not found"))

            _connectionState.value = Pair(deviceAddress, ConnectionState.CONNECTING)
            cleanupGatt()

            // Подключаемся
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }

            connectedDeviceAddress = deviceAddress

            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(Exception("No permissions to connect: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.message}"))
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun sendData(deviceAddress: String, data: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            if (gatt == null) {
                return@withContext Result.failure(Exception("Not connected to device"))
            }

            if (connectedDeviceAddress != deviceAddress) {
                return@withContext Result.failure(Exception("Wrong device address"))
            }

            if (writeCharacteristic == null) {
                return@withContext Result.failure(Exception("No write characteristic available"))
            }

            Log.d(TAG, "Sending data to $deviceAddress: '$data'")

            // Преобразуем строку в байты
            val bytes = if (data.endsWith("\r\n")) {
                data.toByteArray(Charsets.UTF_8)
            } else {
                "$data\r\n".toByteArray(Charsets.UTF_8)
            }

            Log.d(TAG, "Bytes to send: ${bytes.joinToString(" ") { "%02x".format(it) }}")

            // Устанавливаем значение
            writeCharacteristic!!.value = bytes

            // Определяем тип записи
            val properties = writeCharacteristic!!.properties
            when {
                properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 -> {
                    writeCharacteristic!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 -> {
                    writeCharacteristic!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
                else -> {
                    writeCharacteristic!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
            }

            // Отправляем
            val writeStarted = gatt?.writeCharacteristic(writeCharacteristic!!) ?: false
            if (!writeStarted) {
                return@withContext Result.failure(Exception("Failed to initiate write"))
            }

            Log.d(TAG, "Write initiated successfully")

            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(Exception("No permissions to send data: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to send data: ${e.message}"))
        }
    }

    fun getBufferedData(): String {
        synchronized(receivedDataBuffer) {
            return receivedDataBuffer.toString()
        }
    }

    fun clearBuffer() {
        synchronized(receivedDataBuffer) {
            receivedDataBuffer.clear()
            bufferSize = 0
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun disconnectFromDevice(deviceAddress: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            if (gatt == null) {
                return@withContext Result.failure(Exception("Not connected"))
            }

            _connectionState.value = Pair(deviceAddress, ConnectionState.DISCONNECTING)

            gatt?.disconnect()
            kotlinx.coroutines.delay(500)
            cleanupGatt()

            _connectionState.value = Pair(deviceAddress, ConnectionState.DISCONNECTED)

            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(Exception("No permissions to disconnect: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Disconnect failed: ${e.message}"))
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BleDeviceModel> {
        return try {
            val bondedDevices = bluetoothAdapter?.bondedDevices ?: return emptyList()
            Log.d(TAG, "Found ${bondedDevices.size} paired devices")

            bondedDevices.mapNotNull { device ->
                try {
                    BleDeviceModel(
                        name = device.name ?: "Unknown",
                        address = device.address,
                        rssi = 0,
                        isConnectable = true,
                        timestamp = System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing paired device", e)
                    null
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting paired devices", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting paired devices", e)
            emptyList()
        }
    }

    fun getDeviceFromCache(deviceAddress: String): BleDeviceModel? {
        return devicesCache[deviceAddress]
    }

    fun addOrUpdateDevice(device: BleDeviceModel) {
        devicesCache[device.address] = device.copy(timestamp = System.currentTimeMillis())
        _discoveredDevices.value = devicesCache.values.toList().sortedByDescending { it.timestamp }
    }

    fun clearCache() {
        devicesCache.clear()
        _discoveredDevices.value = emptyList()
    }

    fun isBluetoothEnabled(): Boolean {
        return try {
            bluetoothAdapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    fun isBluetoothSupported(): Boolean {
        return try {
            bluetoothAdapter != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        } catch (e: Exception) {
            false
        }
    }

    fun hasPermissions(): Boolean {
        val requiredPermissions = getRequiredPermissions()
        val result = requiredPermissions.all { permission ->
            ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        Log.d(TAG, "Permissions check: $result")
        return result
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        permissions.add(Manifest.permission.BLUETOOTH)
        permissions.add(Manifest.permission.BLUETOOTH_ADMIN)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return permissions
    }

    fun getConnectionState(deviceAddress: String): ConnectionState {
        return if (connectedDeviceAddress == deviceAddress && gatt != null) {
            ConnectionState.CONNECTED
        } else {
            ConnectionState.DISCONNECTED
        }
    }

    fun getIsScanning(): Boolean = _isScanning.value

    @SuppressLint("MissingPermission")
    fun cleanup() {
        stopScan()
        cleanupGatt()
        devicesCache.clear()
        _discoveredDevices.value = emptyList()
        _connectionState.value = null
        _dataReceived.value = null
    }
}