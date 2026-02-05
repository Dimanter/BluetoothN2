package com.example.bluetoothn2.screen

import android.annotation.SuppressLint
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluetoothn2.R
import com.example.bluetoothn2.ui.theme.PrimaryColor
import com.example.bluetoothn2.ui.theme.TextColor
import com.example.bluetoothn2.model.ConnectionState
import com.example.bluetoothn2.viewmodel.BluetoothViewModel
import com.example.bluetoothn2.viewmodel.ConnectedDeviceViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun ConnectedDeviceScreen(
    deviceAddress: String,
    onBack: () -> Unit,
    viewModel: ConnectedDeviceViewModel,
    bluetoothViewModel: BluetoothViewModel? = null
) {
    // Инициализируем ViewModel с адресом устройства
    LaunchedEffect(deviceAddress) {
        viewModel.setDeviceAddress(deviceAddress)

        // Синхронизируем состояние при входе на экран
        bluetoothViewModel?.let {
            val connectionState = it.getDeviceConnectionState(deviceAddress)
            if (connectionState == ConnectionState.CONNECTED) {
                viewModel.updateConnectionStateFromMain(deviceAddress, connectionState)
            }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Текущий выбранный экран
    var currentScreen by remember { mutableStateOf<DosingScreen>(DosingScreen.Main) }

    // Функция для отключения при выходе
    fun disconnectAndExit() {
        coroutineScope.launch {
            // Отключаемся в ConnectedDeviceViewModel
            viewModel.disconnect()

            // Отключаемся в BluetoothViewModel
            bluetoothViewModel?.disconnectFromDevice(deviceAddress)

            // Очищаем ресурсы
            viewModel.cleanup()

            // Ждем немного для обработки отключения
            delay(300)

            // Возвращаемся назад
            onBack()
        }
    }

    // Обработчик системной кнопки "Назад"
    BackHandler(enabled = true) {
        if (currentScreen != DosingScreen.Main) {
            currentScreen = DosingScreen.Main
        } else {
            disconnectAndExit()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            DosingScreen.Main -> "Дозирование"
                            DosingScreen.Direct -> "Прямое дозирование"
                            DosingScreen.Partial -> "Частичное дозирование"
                            DosingScreen.Reverse -> "Обратное дозирование"
                            DosingScreen.Free -> "Свободное дозирование"
                        },
                        maxLines = 1
                    )
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryColor,
                    titleContentColor = TextColor,
                    navigationIconContentColor = TextColor,
                    actionIconContentColor = TextColor
                ),
                navigationIcon = {
                    if (currentScreen != DosingScreen.Main) {
                        IconButton(onClick = { currentScreen = DosingScreen.Main }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Назад"
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { disconnectAndExit() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Назад к устройствам"
                            )
                        }
                    }
                },
                actions = {
                    // Кнопки подключения/отключения
                    when {
                        uiState.connectionState == ConnectionState.CONNECTED -> {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.disconnect()
                                        bluetoothViewModel?.disconnectFromDevice(deviceAddress)
                                    }
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.outline_bluetooth_disabled_24),
                                    contentDescription = "Отключиться",
                                    tint = TextColor
                                )
                            }
                        }
                        uiState.connectionState == ConnectionState.CONNECTING -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(end = 8.dp),
                                strokeWidth = 2.dp,
                                color = TextColor
                            )
                        }
                        else -> {
                            IconButton(
                                onClick = { viewModel.connect() },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.outline_bluetooth_24),
                                    contentDescription = "Подключиться",
                                    tint = TextColor
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when (currentScreen) {
            DosingScreen.Main -> MainDosingScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onDirectDosingClick = { currentScreen = DosingScreen.Direct },
                onPartialDosingClick = { currentScreen = DosingScreen.Partial },
                onReverseDosingClick = { currentScreen = DosingScreen.Reverse },
                onFreeDosingClick = { currentScreen = DosingScreen.Free },
                deviceName = uiState.device?.name ?: "Устройство",
                connectionState = uiState.connectionState
            )
            DosingScreen.Direct -> DirectDosingScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onSendCommand = { command ->
                    viewModel.sendCommand(command)
                },
                connectionState = uiState.connectionState,
                receivedData = uiState.receivedData,
                lastCommandResponse = uiState.lastCommandResponse,
                isWaitingForResponse = uiState.isWaitingForResponse,
                onClearData = { viewModel.clearReceivedData() },
                onClearResponse = { viewModel.clearResponse() },
                isReceivingData = uiState.isReceivingData,
                bytesReceived = uiState.bytesReceived,
                onResetCounters = { viewModel.resetCounters() }
            )
            DosingScreen.Partial -> PartialDosingScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onSendCommand = { command ->
                    viewModel.sendCommand(command)
                },
                connectionState = uiState.connectionState,
                receivedData = uiState.receivedData,
                onClearData = { viewModel.clearReceivedData() }
            )
            DosingScreen.Reverse -> ReverseDosingScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onSendCommand = { command ->
                    viewModel.sendCommand(command)
                },
                connectionState = uiState.connectionState,
                receivedData = uiState.receivedData,
                onClearData = { viewModel.clearReceivedData() }
            )
            DosingScreen.Free -> FreeDosingScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onSendCommand = { command ->
                    viewModel.sendCommand(command)
                },
                connectionState = uiState.connectionState,
                receivedData = uiState.receivedData,
                onClearData = { viewModel.clearReceivedData() }
            )
        }
    }
}

// Перечисление для экранов дозирования
enum class DosingScreen {
    Main,
    Direct,
    Partial,
    Reverse,
    Free
}

@Composable
fun MainDosingScreen(
    modifier: Modifier = Modifier,
    onDirectDosingClick: () -> Unit,
    onPartialDosingClick: () -> Unit,
    onReverseDosingClick: () -> Unit,
    onFreeDosingClick: () -> Unit,
    deviceName: String,
    connectionState: ConnectionState
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Статус устройства
        DeviceStatusCard(
            deviceName = deviceName,
            connectionState = connectionState,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Заголовок
        Text(
            text = "Выберите тип дозирования",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Сетка из 4 плиток
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Прямое дозирование
                DosingTile(
                    title = "Прямое дозирование",
                    iconResId = R.drawable.science,
                    onClick = onDirectDosingClick,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF4CAF50) // Зеленый
                )

                // Частичное дозирование
                DosingTile(
                    title = "Частичное дозирование",
                    iconResId = R.drawable.timeline,
                    onClick = onPartialDosingClick,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF2196F3) // Синий
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Обратное дозирование
                DosingTile(
                    title = "Обратное дозирование",
                    iconResId = R.drawable.rotate,
                    onClick = onReverseDosingClick,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFFF9800) // Оранжевый
                )

                // Свободное дозирование
                DosingTile(
                    title = "Свободное дозирование",
                    iconResId = R.drawable.assignment,
                    onClick = onFreeDosingClick,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF9C27B0) // Фиолетовый
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Информация о подключении
        ConnectionStatusInfo(
            connectionState = connectionState
        )
    }
}

@Composable
fun DosingTile(
    title: String,
    iconResId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PrimaryColor
) {
    Card(
        modifier = modifier
            .height(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f),
            contentColor = color
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = title,
                modifier = Modifier.size(64.dp),
                tint = color
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = color
            )
        }
    }
}

@Composable
fun DeviceStatusCard(
    deviceName: String,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Состояние: ${connectionState.getDisplayName()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (connectionState) {
                        ConnectionState.CONNECTED -> PrimaryColor
                        ConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }

            Icon(
                painter = painterResource(
                    id = when (connectionState) {
                        ConnectionState.CONNECTED -> R.drawable.outline_bluetooth_24
                        else -> R.drawable.outline_bluetooth_disabled_24
                    }
                ),
                contentDescription = "Статус подключения",
                modifier = Modifier.size(32.dp),
                tint = when (connectionState) {
                    ConnectionState.CONNECTED -> PrimaryColor
                    ConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
fun ConnectionStatusInfo(
    connectionState: ConnectionState
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (connectionState) {
            ConnectionState.CONNECTED -> {
                Text(
                    text = "✓ Устройство подключено",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryColor
                )
                Text(
                    text = "Готово к работе",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ConnectionState.CONNECTING -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Подключение...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            else -> {
                Text(
                    text = "⚠ Устройство не подключено",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Некоторые функции могут быть недоступны",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Экран прямого дозирования
// Экран прямого дозирования
@Composable
fun DirectDosingScreen(
    modifier: Modifier = Modifier,
    onSendCommand: (String) -> Unit,
    connectionState: ConnectionState,
    receivedData: String = "",
    lastCommandResponse: String? = null,
    isWaitingForResponse: Boolean = false,
    onClearData: () -> Unit = {},
    onClearResponse: () -> Unit = {},
    isReceivingData: Boolean = false,
    bytesReceived: Int = 0,
    onResetCounters: () -> Unit = {}
) {
    var customCommand by remember { mutableStateOf("Who are you?\r\n") }
    val scrollState = rememberScrollState()

    // Автоматическая прокрутка при получении новых данных
    LaunchedEffect(receivedData) {
        if (receivedData.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = modifier
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Статус потока данных
        if (connectionState == ConnectionState.CONNECTED) {
            DataStreamStatus(
                isReceiving = isReceivingData,
                bytesReceived = bytesReceived,
                onResetCounters = onResetCounters
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = "Прямое дозирование",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Отправляйте команды устройству и получайте ответы в реальном времени.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Поле для ввода команды
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Отправка команды",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = customCommand,
                onValueChange = { customCommand = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Введите команду...") },
                singleLine = false,
                maxLines = 3
            )

            Button(
                onClick = {
                    if (customCommand.isNotBlank()) {
                        val command = if (customCommand.endsWith("\r\n")) {
                            customCommand
                        } else {
                            "$customCommand\r\n"
                        }
                        onSendCommand(command)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = connectionState == ConnectionState.CONNECTED && !isWaitingForResponse && customCommand.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                    contentColor = Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isWaitingForResponse) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF4CAF50)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.send),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = if (isWaitingForResponse) "Ожидание ответа..." else "Отправить команду"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Ответ на последнюю команду
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ответ на команду:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                if (lastCommandResponse != null) {
                    IconButton(
                        onClick = onClearResponse,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Очистить ответ",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (isWaitingForResponse) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF4CAF50)
                        )
                        Text("Ожидание ответа...")
                    }
                } else if (lastCommandResponse != null) {
                    Text(
                        text = lastCommandResponse,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "Ответ появится здесь...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // История полученных данных
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "История данных (${bytesReceived} байт):",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (receivedData.isNotEmpty()) {
                    IconButton(
                        onClick = onClearData,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Очистить историю",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                if (receivedData.isEmpty()) {
                    Text(
                        text = "Данные от устройства будут отображаться здесь...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = receivedData,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Быстрые команды
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Быстрые команды",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        customCommand = "Who are you?\r\n"
                        onSendCommand("Who are you?\r\n")
                    },
                    modifier = Modifier.weight(1f),
                    enabled = connectionState == ConnectionState.CONNECTED && !isWaitingForResponse,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                        contentColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("Тест")
                }

                Button(
                    onClick = {
                        customCommand = "SETMAXVOL:100\r\n"
                        onSendCommand("SETMAXVOL:100\r\n")
                    },
                    modifier = Modifier.weight(1f),
                    enabled = connectionState == ConnectionState.CONNECTED && !isWaitingForResponse,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3).copy(alpha = 0.1f),
                        contentColor = Color(0xFF2196F3)
                    )
                ) {
                    Text("Объем")
                }

                Button(
                    onClick = {
                        customCommand = "SET_SPEED:100\r\n"
                        onSendCommand("SET_SPEED:100\r\n")
                    },
                    modifier = Modifier.weight(1f),
                    enabled = connectionState == ConnectionState.CONNECTED && !isWaitingForResponse,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800).copy(alpha = 0.1f),
                        contentColor = Color(0xFFFF9800)
                    )
                ) {
                    Text("Скорость")
                }
            }
        }

        // Предупреждение о подключении
        if (connectionState != ConnectionState.CONNECTED) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Для отправки команд требуется подключение к устройству",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun DataStreamStatus(
    isReceiving: Boolean,
    bytesReceived: Int,
    onResetCounters: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isReceiving) Color(0xFF4CAF50).copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            color = if (isReceiving) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.outline
                        )
                )
                Column {
                    Text(
                        text = if (isReceiving) "Получение данных..." else "Готов к приему",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Всего байт: $bytesReceived",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onResetCounters,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Сбросить счетчик"
                )
            }
        }
    }
}

@Composable
fun DataStreamStatus(
    isActive: Boolean,
    isReceiving: Boolean,
    bytesReceived: Int,
    lastDataTime: Long,
    onToggleStream: () -> Unit,
    onResetCounters: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF4CAF50).copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isActive) "Поток данных активен" else "Поток данных остановлен",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Получено: ${bytesReceived} байт",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onResetCounters,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Сбросить счетчик",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Button(
                        onClick = onToggleStream,
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.errorContainer
                            else Color(0xFF4CAF50),
                            contentColor = if (isActive) MaterialTheme.colorScheme.onErrorContainer
                            else Color.White
                        )
                    ) {
                        Text(if (isActive) "Стоп" else "Старт")
                    }
                }
            }

            if (isActive && isReceiving) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF4CAF50)
                )
            }

            if (lastDataTime > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                val timeDiff = System.currentTimeMillis() - lastDataTime
                val statusText = if (timeDiff < 5000) {
                    "Данные получаются"
                } else {
                    "Нет данных ${timeDiff / 1000} сек"
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (timeDiff < 5000) Color(0xFF4CAF50)
                    else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// Экран частичного дозирования
@Composable
fun PartialDosingScreen(
    modifier: Modifier = Modifier,
    onSendCommand: (String) -> Unit,
    connectionState: ConnectionState,
    receivedData: String? = null,
    onClearData: () -> Unit = {}
) {
    var customCommand by remember { mutableStateOf("") }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Частичное дозирование",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Режим частичного дозирования с возможностью задания объема и скорости.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Поле для ввода команды
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Отправка команды",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = customCommand,
                onValueChange = { customCommand = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Введите команду...") },
                singleLine = false,
                maxLines = 3
            )

            Button(
                onClick = {
                    if (customCommand.isNotBlank()) {
                        val command = if (customCommand.endsWith("\r\n")) {
                            customCommand
                        } else {
                            "$customCommand\r\n"
                        }
                        onSendCommand(command)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = connectionState == ConnectionState.CONNECTED && customCommand.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3).copy(alpha = 0.1f),
                    contentColor = Color(0xFF2196F3)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.send),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("Отправить команду")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Кнопки управления
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            DosingButton(
                text = "Частичное дозирование 50 мл",
                command = "PARTIAL_50\r\n",
                onSendCommand = onSendCommand,
                enabled = connectionState == ConnectionState.CONNECTED
            )

            DosingButton(
                text = "Частичное дозирование 100 мл",
                command = "PARTIAL_100\r\n",
                onSendCommand = onSendCommand,
                enabled = connectionState == ConnectionState.CONNECTED
            )

            DosingButton(
                text = "Задать скорость дозирования",
                command = "SET_SPEED:100\r\n",
                onSendCommand = onSendCommand,
                enabled = connectionState == ConnectionState.CONNECTED
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Отображение полученных данных
        ReceivedDataDisplay(
            receivedData = receivedData,
            onClearData = onClearData
        )

        // Предупреждение о подключении
        if (connectionState != ConnectionState.CONNECTED) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "⚠ Для управления дозированием требуется подключение к устройству",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Экран обратного дозирования
@Composable
fun ReverseDosingScreen(
    modifier: Modifier = Modifier,
    onSendCommand: (String) -> Unit,
    connectionState: ConnectionState,
    receivedData: String? = null,
    onClearData: () -> Unit = {}
) {
    var customCommand by remember { mutableStateOf("") }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Обратное дозирование",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Режим обратного дозирования для забора реагента.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Поле для ввода команды
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Отправка команды",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = customCommand,
                onValueChange = { customCommand = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Введите команду...") },
                singleLine = false,
                maxLines = 3
            )

            Button(
                onClick = {
                    if (customCommand.isNotBlank()) {
                        val command = if (customCommand.endsWith("\r\n")) {
                            customCommand
                        } else {
                            "$customCommand\r\n"
                        }
                        onSendCommand(command)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = connectionState == ConnectionState.CONNECTED && customCommand.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800).copy(alpha = 0.1f),
                    contentColor = Color(0xFFFF9800)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.send),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("Отправить команду")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Кнопки управления
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            DosingButton(
                text = "Запуск обратного дозирования",
                command = "START_REVERSE\r\n",
                onSendCommand = onSendCommand,
                enabled = connectionState == ConnectionState.CONNECTED,
                buttonColor = Color(0xFFFF9800).copy(alpha = 0.1f),
                textColor = Color(0xFFFF9800)
            )

            DosingButton(
                text = "Остановка забора",
                command = "STOP_REVERSE\r\n",
                onSendCommand = onSendCommand,
                enabled = connectionState == ConnectionState.CONNECTED,
                buttonColor = MaterialTheme.colorScheme.errorContainer,
                textColor = MaterialTheme.colorScheme.onErrorContainer
            )

            DosingButton(
                text = "Калибровка обратного хода",
                command = "CALIBRATE_REVERSE\r\n",
                onSendCommand = onSendCommand,
                enabled = connectionState == ConnectionState.CONNECTED
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Отображение полученных данных
        ReceivedDataDisplay(
            receivedData = receivedData,
            onClearData = onClearData
        )

        // Предупреждение о подключении
        if (connectionState != ConnectionState.CONNECTED) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "⚠ Для управления дозированием требуется подключение к устройству",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Экран свободного дозирования
@Composable
fun FreeDosingScreen(
    modifier: Modifier = Modifier,
    onSendCommand: (String) -> Unit,
    connectionState: ConnectionState,
    receivedData: String? = null,
    onClearData: () -> Unit = {}
) {
    var customCommand by remember { mutableStateOf("") }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Свободное дозирование",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Режим свободного дозирования с возможностью задания произвольных параметров.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Поле для ввода произвольной команды
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Отправка команды",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Введите команду для отправки на устройство",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = customCommand,
                onValueChange = { customCommand = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Введите команду...") },
                singleLine = false,
                maxLines = 3
            )

            Button(
                onClick = {
                    if (customCommand.isNotBlank()) {
                        val command = if (customCommand.endsWith("\r\n")) {
                            customCommand
                        } else {
                            "$customCommand\r\n"
                        }
                        onSendCommand(command)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = connectionState == ConnectionState.CONNECTED && customCommand.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9C27B0).copy(alpha = 0.1f),
                    contentColor = Color(0xFF9C27B0)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.send),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("Отправить команду")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Быстрые команды
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Быстрые команды",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )

            DosingButton(
                text = "Свободный режим дозирования",
                command = "FREE_MODE\r\n",
                onSendCommand = onSendCommand,
                enabled = connectionState == ConnectionState.CONNECTED,
                buttonColor = Color(0xFF9C27B0).copy(alpha = 0.1f),
                textColor = Color(0xFF9C27B0)
            )

            DosingButton(
                text = "Задать произвольный объем",
                command = "SET_VOLUME:50\r\n",
                onSendCommand = onSendCommand,
                enabled = connectionState == ConnectionState.CONNECTED,
                buttonColor = Color(0xFF9C27B0).copy(alpha = 0.1f),
                textColor = Color(0xFF9C27B0)
            )

            DosingButton(
                text = "Задать произвольную скорость",
                command = "SET_CUSTOM_SPEED:75\r\n",
                onSendCommand = onSendCommand,
                enabled = connectionState == ConnectionState.CONNECTED,
                buttonColor = Color(0xFF9C27B0).copy(alpha = 0.1f),
                textColor = Color(0xFF9C27B0)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Отображение полученных данных
        ReceivedDataDisplay(
            receivedData = receivedData,
            onClearData = onClearData
        )

        // Предупреждение о подключении
        if (connectionState != ConnectionState.CONNECTED) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "⚠ Для управления дозированием требуется подключение к устройству",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DosingButton(
    text: String,
    command: String,
    onSendCommand: (String) -> Unit,
    enabled: Boolean = true,
    buttonColor: Color = PrimaryColor.copy(alpha = 0.1f),
    textColor: Color = PrimaryColor
) {
    Button(
        onClick = { onSendCommand(command) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            contentColor = textColor
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ReceivedDataDisplay(
    receivedData: String?,
    onClearData: () -> Unit
) {
    receivedData?.let { data ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "История сообщений:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = onClearData,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Очистить",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Поле для отображения ответа с возможностью прокрутки
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    val scrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = data,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Автоматическая прокрутка вниз при получении новых данных
                    LaunchedEffect(data) {
                        scrollState.scrollTo(scrollState.maxValue)
                    }
                }

                // Счетчик символов
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Получено символов: ${data.length}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}