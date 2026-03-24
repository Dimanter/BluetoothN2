package com.example.bluetoothn2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bluetoothn2.screen.ConnectedDeviceScreen
import com.example.bluetoothn2.screen.MainScreen
import com.example.bluetoothn2.viewmodel.BluetoothViewModel
import com.example.bluetoothn2.viewmodel.ConnectedDeviceViewModel
import com.example.bluetoothn2.model.ConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object ConnectedDevice : Screen("connected_device/{deviceAddress}") {
        fun createRoute(deviceAddress: String) = "connected_device/$deviceAddress"
    }
}



@Composable
fun BluetoothNavigation(
    navController: NavHostController = rememberNavController()
) {


    val bluetoothViewModel: BluetoothViewModel = viewModel()
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                viewModel = bluetoothViewModel,
                onNavigateToConnectedDevice = { deviceAddress ->
                    // Просто переходим на экран устройства
                    navController.navigate(Screen.ConnectedDevice.createRoute(deviceAddress))
                },
            )
        }

        composable(Screen.ConnectedDevice.route) { backStackEntry ->
            val deviceAddress = backStackEntry.arguments?.getString("deviceAddress") ?: ""
            val connectedDeviceViewModel: ConnectedDeviceViewModel = viewModel()

            LaunchedEffect(deviceAddress) {
                connectedDeviceViewModel.setDeviceAddress(deviceAddress)

                val connectionState = bluetoothViewModel.getDeviceConnectionState(deviceAddress)
                if (connectionState != ConnectionState.DISCONNECTED) {
                    connectedDeviceViewModel.updateConnectionStateFromMain(deviceAddress, connectionState)
                }

                val device = bluetoothViewModel.getDeviceByAddress(deviceAddress)
                if (device == null) {
                    // Можно попробовать получить устройство из кэша репозитории
                    // или создать временное устройство по адресу
                }

                // Отправляем команду +CONNECTED при каждом входе на экран, если устройство уже подключено
                if (connectedDeviceViewModel.uiState.value.connectionState == ConnectionState.CONNECTED) {
                    connectedDeviceViewModel.sendCommand("+CONNECTED\r\n")
                }
            }

            ConnectedDeviceScreen(
                deviceAddress = deviceAddress,
                onBack = {
                    // Убрали вызов disconnectFromDevice, только cleanup и навигация
                    connectedDeviceViewModel.cleanup()
                    navController.popBackStack()
                },
                viewModel = connectedDeviceViewModel,
                bluetoothViewModel = bluetoothViewModel
            )
        }
    }
}