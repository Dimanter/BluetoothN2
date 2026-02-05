package com.example.bluetoothn2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluetoothn2.screen.DebugScreen
import com.example.bluetoothn2.screen.MainScreen
import com.example.bluetoothn2.ui.theme.BluetoothScannerTheme
import com.example.bluetoothn2.viewmodel.BluetoothViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: BluetoothViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BluetoothScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Для отладки - показываем DebugScreen
                    // Для продакшена - MainScreen
                    val showDebug = false // Измените на true для отладки

                    if (showDebug) {
                        DebugScreen(viewModel)
                    } else {
                        MainApp(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun MainApp(viewModel: BluetoothViewModel) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Загружаем сопряженные устройства
    LaunchedEffect(uiState.value.hasPermissions) {
        if (uiState.value.hasPermissions) {
            viewModel.loadPairedDevices()
        }
    }

    BluetoothNavigation()
}
//direct ob
//chast ob(ne men) - chastey
//obrat ob - chastey
//svobod 1 - ob1, 2 - ob2