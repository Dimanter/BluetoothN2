package com.example.bluetoothn2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
                    // Сохраняем выбранное устройство
                    val device = bluetoothViewModel.getDeviceByAddress(deviceAddress)
                    device?.let {
                        bluetoothViewModel.selectDevice(deviceAddress)

                        // Если устройство не подключено, предлагаем подключиться при переходе
                        if (bluetoothViewModel.getDeviceConnectionState(deviceAddress) != ConnectionState.CONNECTED) {
                            // Можно автоматически инициировать подключение или просто перейти
                            // bluetoothViewModel.connectToDevice(device)
                        }

                        // Переход на экран устройства
                        navController.navigate(Screen.ConnectedDevice.createRoute(deviceAddress))
                    }
                }
            )
        }

        composable(Screen.ConnectedDevice.route) { backStackEntry ->
            val deviceAddress = backStackEntry.arguments?.getString("deviceAddress") ?: ""
            val connectedDeviceViewModel: ConnectedDeviceViewModel = viewModel()

            // Инициализируем ViewModel с адресом устройства
            LaunchedEffect(deviceAddress) {
                connectedDeviceViewModel.setDeviceAddress(deviceAddress)

                // Синхронизируем состояние подключения из основного ViewModel
                val connectionState = bluetoothViewModel.getDeviceConnectionState(deviceAddress)
                if (connectionState != ConnectionState.DISCONNECTED) {
                    connectedDeviceViewModel.updateConnectionStateFromMain(deviceAddress, connectionState)
                }

                // Если устройство не найдено в основном ViewModel, пытаемся найти его в репозитории
                val device = bluetoothViewModel.getDeviceByAddress(deviceAddress)
                if (device == null) {
                    // Можно попробовать получить устройство из кэша репозитории
                    // или создать временное устройство по адресу
                }
            }

            ConnectedDeviceScreen(
                deviceAddress = deviceAddress,
                onBack = {
                    // Отключаемся только если мы явно отключились на этом экране
                    // Иначе сохраняем состояние подключения
                    if (connectedDeviceViewModel.uiState.value.connectionState == ConnectionState.DISCONNECTED) {
                        bluetoothViewModel.disconnectFromDevice(deviceAddress)
                    }
                    connectedDeviceViewModel.cleanup()
                    navController.popBackStack()
                },
                viewModel = connectedDeviceViewModel,
                bluetoothViewModel = bluetoothViewModel
            )
        }
    }
}