package com.example.bluetoothn2.model

import android.bluetooth.le.ScanResult
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BleDeviceModel(
    val name: String?,
    val address: String,
    val rssi: Int,
    val isConnectable: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED
) : Parcelable {
    companion object {
        fun fromScanResult(result: ScanResult): BleDeviceModel {
            val deviceName = result.device.name ?: "Unknown"
            return BleDeviceModel(
                name = deviceName,
                address = result.device.address,
                rssi = result.rssi,
                isConnectable = result.isConnectable
            )
        }
    }

    val displayName: String
        get() = name ?: "Unknown Device"
}