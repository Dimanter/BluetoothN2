package com.example.bluetoothn2.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.example.bluetoothn2.R

class IconManager {
    @Composable
    fun getBluetoothIcon(isEnabled: Boolean) = painterResource(
        id = if (isEnabled) {
            R.drawable.outline_bluetooth_24
        } else {
            R.drawable.outline_bluetooth_disabled_24
        }
    )
}