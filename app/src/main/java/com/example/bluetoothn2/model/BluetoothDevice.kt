package com.example.bluetoothn2.model

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Parcelable
import androidx.annotation.RequiresPermission
import kotlinx.parcelize.Parcelize
import java.lang.reflect.Method


// Модель Bluetooth устройства
data class BluetoothDeviceModel(
    val name: String?,
    val address: String,
    val type: DeviceType,
    val rssi: Int? = null,
    val isPaired: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED
) {
    companion object {
        @SuppressLint("MissingPermission")
        fun fromBluetoothDevice(device: BluetoothDevice, rssi: Int? = null): BluetoothDeviceModel {
            return try {
                val deviceName = try {
                    device.name ?: "Неизвестное"
                } catch (e: Exception) {
                    "Неизвестное"
                }

                val deviceAddress = try {
                    device.address ?: "00:00:00:00:00:00"
                } catch (e: Exception) {
                    "00:00:00:00:00:00"
                }

                val deviceType = try {
                    DeviceType.fromInt(device.type)
                } catch (e: Exception) {
                    DeviceType.UNKNOWN
                }

                val isPaired = try {
                    device.bondState == BluetoothDevice.BOND_BONDED
                } catch (e: Exception) {
                    false
                }

                BluetoothDeviceModel(
                    name = deviceName,
                    address = deviceAddress,
                    type = deviceType,
                    rssi = rssi,
                    isPaired = isPaired,
                    connectionState = ConnectionState.DISCONNECTED
                )
            } catch (e: Exception) {
                BluetoothDeviceModel(
                    name = "Неизвестное",
                    address = "00:00:00:00:00:00",
                    type = DeviceType.UNKNOWN,
                    rssi = rssi,
                    isPaired = false,
                    connectionState = ConnectionState.DISCONNECTED
                )
            }
        }
    }
}

// Типы устройств
enum class DeviceType(val displayName: String) {
    UNKNOWN("Неизвестно"),
    CLASSIC("Классическое"),
    LE("BLE"),
    DUAL("Двойное"),
    PHONE("Телефон"),
    AUDIO("Аудио"),
    COMPUTER("Компьютер"),
    PERIPHERAL("Периферия");

    companion object {
        fun fromInt(type: Int): DeviceType {
            return when (type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> CLASSIC
                BluetoothDevice.DEVICE_TYPE_LE -> LE
                BluetoothDevice.DEVICE_TYPE_DUAL -> DUAL
                else -> UNKNOWN
            }
        }
    }
}

// Состояния подключения
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR;

    fun getDisplayName(): String {
        return when (this) {
            DISCONNECTED -> "Отключено"
            CONNECTING -> "Подключение..."
            CONNECTED -> "Подключено"
            DISCONNECTING -> "Отключение..."
            ERROR -> "Ошибка"
        }
    }

    fun getColorResId(): Long {
        return when (this) {
            DISCONNECTED -> 0xFF666666 // Серый
            CONNECTING -> 0xFFFFA500 // Оранжевый
            CONNECTED -> 0xFF4CAF50 // Зеленый
            DISCONNECTING -> 0xFFFF9800 // Оранжевый
            ERROR -> 0xFFF44336 // Красный
        }
    }
}