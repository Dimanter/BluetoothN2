package com.example.bluetoothn2.screen

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluetoothn2.R
import com.example.bluetoothn2.ui.theme.PrimaryColor
import com.example.bluetoothn2.ui.theme.TextColor
import com.example.bluetoothn2.model.ConnectionState
import com.example.bluetoothn2.viewmodel.BluetoothViewModel
import com.example.bluetoothn2.viewmodel.ConnectedDeviceViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun ConnectedDeviceScreen(
    deviceAddress: String,
    onBack: () -> Unit,
    viewModel: ConnectedDeviceViewModel,
    bluetoothViewModel: BluetoothViewModel? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    // Состояние для текущего экрана
    var currentScreen by remember { mutableStateOf<DeviceScreen>(DeviceScreen.MAIN) }

    // Состояния для выбранных индексов в разных экранах
    var mainSelectedIndex by remember { mutableStateOf(0) }
    var functionsSelectedIndex by remember { mutableStateOf(0) }
    var systemSettingsSelectedIndex by remember { mutableStateOf(0) }

    // Состояния для экранов ввода
    var directDosingValue by remember { mutableStateOf(0) }
    var partialDosingVolume by remember { mutableStateOf(0) }
    var partialDosingParts by remember { mutableStateOf(1) }
    var partialFixedVolume by remember { mutableStateOf(0) }
    var partialFixedParts by remember { mutableStateOf(1) }
    var freeCollectionValues by remember { mutableStateOf(List(5) { 0 }) }

    // Индекс активного поля для экранов ввода
    var activeInputFieldIndex by remember { mutableStateOf(0) }

    // Сохраняем выбранный индекс функций при переходе на экран дозирования
    var savedFunctionsIndex by remember { mutableStateOf(0) }

    // Для прямого дозирования - временное значение, которое изменяется
    var directDosingTempValue by remember { mutableStateOf(0) }

    // Job для управления задержкой навигации
    var navigationDebounceJob by remember { mutableStateOf<Job?>(null) }

    // Получаем текущий выбранный индекс в зависимости от экрана
    val currentSelectedIndex = when (currentScreen) {
        DeviceScreen.MAIN -> mainSelectedIndex
        DeviceScreen.FUNCTIONS -> functionsSelectedIndex
        DeviceScreen.SYSTEM_SETTINGS -> systemSettingsSelectedIndex
        else -> 0
    }

    // Функция с задержкой только для навигационных действий
    fun withNavigationDebounce(action: () -> Unit) {
        navigationDebounceJob?.cancel()
        navigationDebounceJob = coroutineScope.launch {
            delay(250)
            action()
        }
    }

    // Функция без задержки для изменения чисел
    fun withoutDebounce(action: () -> Unit) {
        navigationDebounceJob?.cancel()
        action()
    }

    // Обработчик системной кнопки "Назад"
    BackHandler(enabled = true) {
        coroutineScope.launch {
            when (currentScreen) {
                DeviceScreen.MAIN -> {
                    if (uiState.connectionState == ConnectionState.DISCONNECTED) {
                        viewModel.cleanup()
                        bluetoothViewModel?.cleanupDeviceState(deviceAddress)
                    }
                    onBack()
                }
                DeviceScreen.FUNCTIONS -> {
                    currentScreen = DeviceScreen.MAIN
                    activeInputFieldIndex = 0
                }
                DeviceScreen.DIRECT_DOSING,
                DeviceScreen.PARTIAL_DOSING,
                DeviceScreen.PARTIAL_FIXED_COLLECTION,
                DeviceScreen.FREE_COLLECTION -> {
                    currentScreen = DeviceScreen.FUNCTIONS
                    // Восстанавливаем сохраненный индекс
                    functionsSelectedIndex = savedFunctionsIndex
                    activeInputFieldIndex = 0
                }
                DeviceScreen.SYSTEM_SETTINGS -> {
                    currentScreen = DeviceScreen.MAIN
                    activeInputFieldIndex = 0
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            DeviceScreen.MAIN -> "Управление устройством"
                            DeviceScreen.FUNCTIONS -> "Функции дозирования"
                            DeviceScreen.DIRECT_DOSING -> "Прямое дозирование"
                            DeviceScreen.PARTIAL_DOSING -> "Частичное дозирование"
                            DeviceScreen.PARTIAL_FIXED_COLLECTION -> "Частичный фиксированный забор"
                            DeviceScreen.FREE_COLLECTION -> "Свободный забор"
                            DeviceScreen.SYSTEM_SETTINGS -> "Системные настройки"
                        },
                        maxLines = 1,
                        fontSize = 18.sp
                    )
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryColor,
                    titleContentColor = TextColor,
                    navigationIconContentColor = TextColor,
                    actionIconContentColor = TextColor
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                when (currentScreen) {
                                    DeviceScreen.MAIN -> {
                                        viewModel.cleanup()
                                        onBack()
                                    }
                                    DeviceScreen.FUNCTIONS,
                                    DeviceScreen.SYSTEM_SETTINGS -> {
                                        currentScreen = DeviceScreen.MAIN
                                        activeInputFieldIndex = 0
                                    }
                                    DeviceScreen.DIRECT_DOSING,
                                    DeviceScreen.PARTIAL_DOSING,
                                    DeviceScreen.PARTIAL_FIXED_COLLECTION,
                                    DeviceScreen.FREE_COLLECTION -> {
                                        currentScreen = DeviceScreen.FUNCTIONS
                                        // Восстанавливаем сохраненный индекс
                                        functionsSelectedIndex = savedFunctionsIndex
                                        activeInputFieldIndex = 0
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    // Статус подключения
                    ConnectionStatusIndicator(
                        connectionState = uiState.connectionState,
                        currentScreen = currentScreen
                    )
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Основной контент
            when (currentScreen) {
                DeviceScreen.MAIN -> MainDeviceScreen(
                    selectedIndex = mainSelectedIndex,
                    deviceName = uiState.device?.name ?: "Устройство",
                    connectionState = uiState.connectionState,
                    deviceAddress = deviceAddress,
                    modifier = Modifier.fillMaxSize()
                )
                DeviceScreen.FUNCTIONS -> FunctionsScreen(
                    selectedIndex = functionsSelectedIndex,
                    connectionState = uiState.connectionState,
                    modifier = Modifier.fillMaxSize()
                )
                DeviceScreen.DIRECT_DOSING -> DirectDosingScreen(
                    tempValue = directDosingTempValue,
                    currentValue = directDosingValue,
                    onTempValueChange = { directDosingTempValue = it },
                    connectionState = uiState.connectionState,
                    modifier = Modifier.fillMaxSize()
                )
                DeviceScreen.PARTIAL_DOSING -> PartialDosingScreen(
                    volume = partialDosingVolume,
                    parts = partialDosingParts,
                    activeFieldIndex = activeInputFieldIndex,
                    connectionState = uiState.connectionState,
                    modifier = Modifier.fillMaxSize()
                )
                DeviceScreen.PARTIAL_FIXED_COLLECTION -> PartialFixedCollectionScreen(
                    volume = partialFixedVolume,
                    parts = partialFixedParts,
                    activeFieldIndex = activeInputFieldIndex,
                    connectionState = uiState.connectionState,
                    modifier = Modifier.fillMaxSize()
                )
                DeviceScreen.FREE_COLLECTION -> FreeCollectionScreen(
                    values = freeCollectionValues,
                    activeFieldIndex = activeInputFieldIndex,
                    connectionState = uiState.connectionState,
                    modifier = Modifier.fillMaxSize()
                )
                DeviceScreen.SYSTEM_SETTINGS -> SystemSettingsScreen(
                    selectedIndex = systemSettingsSelectedIndex,
                    connectionState = uiState.connectionState,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Панель управления (всегда внизу)
            when (currentScreen) {
                DeviceScreen.MAIN,
                DeviceScreen.FUNCTIONS,
                DeviceScreen.SYSTEM_SETTINGS -> {
                    MainControlPanel(
                        onUpClick = {
                            withNavigationDebounce {
                                val maxIndex = when (currentScreen) {
                                    DeviceScreen.MAIN -> 1
                                    DeviceScreen.FUNCTIONS -> 3
                                    DeviceScreen.SYSTEM_SETTINGS -> 0
                                    else -> 0
                                }
                                // Круговое меню: если наверху - переходим вниз
                                when (currentScreen) {
                                    DeviceScreen.MAIN -> {
                                        if (mainSelectedIndex > 0) {
                                            mainSelectedIndex--
                                        } else {
                                            mainSelectedIndex = maxIndex
                                        }
                                    }
                                    DeviceScreen.FUNCTIONS -> {
                                        if (functionsSelectedIndex > 0) {
                                            functionsSelectedIndex--
                                        } else {
                                            functionsSelectedIndex = maxIndex
                                        }
                                    }
                                    DeviceScreen.SYSTEM_SETTINGS -> {
                                        if (systemSettingsSelectedIndex > 0) {
                                            systemSettingsSelectedIndex--
                                        } else {
                                            systemSettingsSelectedIndex = maxIndex
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        },
                        onDownClick = {
                            withNavigationDebounce {
                                val maxIndex = when (currentScreen) {
                                    DeviceScreen.MAIN -> 1
                                    DeviceScreen.FUNCTIONS -> 3
                                    DeviceScreen.SYSTEM_SETTINGS -> 0
                                    else -> 0
                                }
                                // Круговое меню: если внизу - переходим наверх
                                when (currentScreen) {
                                    DeviceScreen.MAIN -> {
                                        if (mainSelectedIndex < maxIndex) {
                                            mainSelectedIndex++
                                        } else {
                                            mainSelectedIndex = 0
                                        }
                                    }
                                    DeviceScreen.FUNCTIONS -> {
                                        if (functionsSelectedIndex < maxIndex) {
                                            functionsSelectedIndex++
                                        } else {
                                            functionsSelectedIndex = 0
                                        }
                                    }
                                    DeviceScreen.SYSTEM_SETTINGS -> {
                                        if (systemSettingsSelectedIndex < maxIndex) {
                                            systemSettingsSelectedIndex++
                                        } else {
                                            systemSettingsSelectedIndex = 0
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        },
                        onBackClick = {
                            withNavigationDebounce {
                                coroutineScope.launch {
                                    when (currentScreen) {
                                        DeviceScreen.MAIN -> {
                                            viewModel.cleanup()
                                            onBack()
                                        }
                                        DeviceScreen.FUNCTIONS -> {
                                            currentScreen = DeviceScreen.MAIN
                                            activeInputFieldIndex = 0
                                        }
                                        DeviceScreen.SYSTEM_SETTINGS -> {
                                            currentScreen = DeviceScreen.MAIN
                                            activeInputFieldIndex = 0
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        },
                        onAcceptClick = {
                            withNavigationDebounce {
                                when (currentScreen) {
                                    DeviceScreen.MAIN -> {
                                        when (mainSelectedIndex) {
                                            0 -> {
                                                currentScreen = DeviceScreen.FUNCTIONS
                                                activeInputFieldIndex = 0
                                            }
                                            1 -> {
                                                currentScreen = DeviceScreen.SYSTEM_SETTINGS
                                                activeInputFieldIndex = 0
                                            }
                                        }
                                    }
                                    DeviceScreen.FUNCTIONS -> {
                                        val function = getFunctionsList().getOrNull(functionsSelectedIndex)
                                        function?.let {
                                            // Сохраняем текущий индекс перед переходом
                                            savedFunctionsIndex = functionsSelectedIndex
                                            when (it.id) {
                                                "direct_dosing" -> {
                                                    currentScreen = DeviceScreen.DIRECT_DOSING
                                                    activeInputFieldIndex = 0
                                                }
                                                "partial_dosing" -> {
                                                    currentScreen = DeviceScreen.PARTIAL_DOSING
                                                    activeInputFieldIndex = 0
                                                }
                                                "partial_fixed_collection" -> {
                                                    currentScreen = DeviceScreen.PARTIAL_FIXED_COLLECTION
                                                    activeInputFieldIndex = 0
                                                }
                                                "free_collection" -> {
                                                    currentScreen = DeviceScreen.FREE_COLLECTION
                                                    activeInputFieldIndex = 0
                                                }
                                            }
                                        }
                                    }
                                    DeviceScreen.SYSTEM_SETTINGS -> {
                                        // Пока ничего
                                    }
                                    else -> {}
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                DeviceScreen.DIRECT_DOSING -> {
                    DirectDosingControlPanel(
                        onUpClick = {
                            withoutDebounce {
                                if (directDosingTempValue < 300) directDosingTempValue++
                            }
                        },
                        onDownClick = {
                            withoutDebounce {
                                if (directDosingTempValue > 0) directDosingTempValue--
                            }
                        },
                        onBackClick = {
                            withNavigationDebounce {
                                currentScreen = DeviceScreen.FUNCTIONS
                                // Восстанавливаем сохраненный индекс
                                functionsSelectedIndex = savedFunctionsIndex
                                activeInputFieldIndex = 0
                            }
                        },
                        onAcceptClick = {
                            withNavigationDebounce {
                                if (uiState.connectionState == ConnectionState.CONNECTED) {
                                    // Устанавливаем окончательное значение и отправляем команду
                                    directDosingValue = directDosingTempValue
                                    val command = "START_DIRECT:$directDosingValue\r\n"
                                    viewModel.sendCommand(command)
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                DeviceScreen.PARTIAL_DOSING -> {
                    PartialDosingControlPanel(
                        onUpClick = {
                            withoutDebounce {
                                when (activeInputFieldIndex) {
                                    0 -> if (partialDosingParts < 20) partialDosingParts++
                                    1 -> if (partialDosingVolume < 1000) partialDosingVolume++
                                }
                            }
                        },
                        onDownClick = {
                            withoutDebounce {
                                when (activeInputFieldIndex) {
                                    0 -> if (partialDosingParts > 1) partialDosingParts--
                                    1 -> if (partialDosingVolume > 0) partialDosingVolume--
                                }
                            }
                        },
                        onBackClick = {
                            withNavigationDebounce {
                                if (activeInputFieldIndex > 0) {
                                    activeInputFieldIndex--
                                } else {
                                    currentScreen = DeviceScreen.FUNCTIONS
                                    // Восстанавливаем сохраненный индекс
                                    functionsSelectedIndex = savedFunctionsIndex
                                    activeInputFieldIndex = 0
                                }
                            }
                        },
                        onAcceptClick = {
                            withNavigationDebounce {
                                if (activeInputFieldIndex == 0) {
                                    activeInputFieldIndex = 1
                                } else {
                                    if (uiState.connectionState == ConnectionState.CONNECTED) {
                                        val command = "START_PARTIAL:$partialDosingVolume:$partialDosingParts\r\n"
                                        viewModel.sendCommand(command)
                                    }
                                }
                            }
                        },
                        activeFieldIndex = activeInputFieldIndex,
                        totalFields = 2,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                DeviceScreen.PARTIAL_FIXED_COLLECTION -> {
                    PartialFixedControlPanel(
                        onUpClick = {
                            withoutDebounce {
                                when (activeInputFieldIndex) {
                                    0 -> if (partialFixedVolume < 500) partialFixedVolume++
                                    1 -> if (partialFixedParts < 10) partialFixedParts++
                                }
                            }
                        },
                        onDownClick = {
                            withoutDebounce {
                                when (activeInputFieldIndex) {
                                    0 -> if (partialFixedVolume > 0) partialFixedVolume--
                                    1 -> if (partialFixedParts > 1) partialFixedParts--
                                }
                            }
                        },
                        onBackClick = {
                            withNavigationDebounce {
                                if (activeInputFieldIndex > 0) {
                                    activeInputFieldIndex--
                                } else {
                                    currentScreen = DeviceScreen.FUNCTIONS
                                    // Восстанавливаем сохраненный индекс
                                    functionsSelectedIndex = savedFunctionsIndex
                                    activeInputFieldIndex = 0
                                }
                            }
                        },
                        onAcceptClick = {
                            withNavigationDebounce {
                                if (activeInputFieldIndex == 0) {
                                    activeInputFieldIndex = 1
                                } else {
                                    if (uiState.connectionState == ConnectionState.CONNECTED) {
                                        val command = "START_FIXED_COLLECTION:$partialFixedVolume:$partialFixedParts\r\n"
                                        viewModel.sendCommand(command)
                                    }
                                }
                            }
                        },
                        activeFieldIndex = activeInputFieldIndex,
                        totalFields = 2,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                DeviceScreen.FREE_COLLECTION -> {
                    FreeCollectionControlPanel(
                        onUpClick = {
                            withoutDebounce {
                                val newValue = freeCollectionValues[activeInputFieldIndex] + 1
                                if (newValue <= 200) {
                                    freeCollectionValues = freeCollectionValues.toMutableList().apply {
                                        this[activeInputFieldIndex] = newValue
                                    }
                                }
                            }
                        },
                        onDownClick = {
                            withoutDebounce {
                                val newValue = freeCollectionValues[activeInputFieldIndex] - 1
                                if (newValue >= 0) {
                                    freeCollectionValues = freeCollectionValues.toMutableList().apply {
                                        this[activeInputFieldIndex] = newValue
                                    }
                                }
                            }
                        },
                        onBackClick = {
                            withNavigationDebounce {
                                // Всегда возвращаемся к предыдущему экрану
                                currentScreen = DeviceScreen.FUNCTIONS
                                // Восстанавливаем сохраненный индекс
                                functionsSelectedIndex = savedFunctionsIndex
                                activeInputFieldIndex = 0
                            }
                        },
                        onAcceptClick = {
                            withNavigationDebounce {
                                if (activeInputFieldIndex < 4) {
                                    activeInputFieldIndex++
                                } else {
                                    if (uiState.connectionState == ConnectionState.CONNECTED) {
                                        val valuesStr = freeCollectionValues.joinToString(":")
                                        val command = "START_FREE_COLLECTION:$valuesStr\r\n"
                                        viewModel.sendCommand(command)
                                    }
                                }
                            }
                        },
                        activeFieldIndex = activeInputFieldIndex,
                        totalFields = 5,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// Типы экранов устройства
enum class DeviceScreen {
    MAIN,
    FUNCTIONS,
    DIRECT_DOSING,
    PARTIAL_DOSING,
    PARTIAL_FIXED_COLLECTION,
    FREE_COLLECTION,
    SYSTEM_SETTINGS
}

// Модель функции устройства
data class DeviceFunction(
    val id: String,
    val name: String,
    val description: String,
    val iconResId: Int
)

@Composable
fun MainDeviceScreen(
    selectedIndex: Int,
    deviceName: String,
    connectionState: ConnectionState,
    deviceAddress: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Карточка статуса устройства
        DeviceStatusCard(
            deviceName = deviceName,
            connectionState = connectionState,
            deviceAddress = deviceAddress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Список из двух элементов (НЕ КЛИКАБЕЛЬНЫ)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Пункт 1: Функции
            MainMenuItem(
                title = "Функции",
                description = "Управление дозированием и забором",
                isSelected = selectedIndex == 0,
                iconResId = R.drawable.science,
                iconColor = Color(0xFF4CAF50),
                modifier = Modifier.fillMaxWidth()
            )

            // Пункт 2: Системные настройки
            MainMenuItem(
                title = "Системные настройки",
                description = "Настройка параметров устройства",
                isSelected = selectedIndex == 1,
                iconResId = R.drawable.settings,
                iconColor = Color(0xFF2196F3),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Подсказка
        if (connectionState != ConnectionState.CONNECTED) {
            Spacer(modifier = Modifier.height(24.dp))
            ConnectionRequiredWarning(
                message = "Для работы функций требуется подключение к устройству",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun MainMenuItem(
    title: String,
    description: String,
    isSelected: Boolean,
    iconResId: Int,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        iconColor.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isSelected) {
        iconColor
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    }

    val scale = animateFloatAsState(
        targetValue = if (isSelected) 1.005f else 1f,
        animationSpec = tween(durationMillis = 200)
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            width = if (isSelected) 1.5.dp else 0.5.dp
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Иконка
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconColor.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = title,
                    modifier = Modifier.size(22.dp),
                    tint = iconColor
                )
            }

            // Текст
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 22.sp,
                    color = if (isSelected) iconColor else MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp
                )
            }

            // Индикатор выбора
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Выбрано",
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun FunctionsScreen(
    selectedIndex: Int,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    val functions = remember { getFunctionsList() }

    Column(
        modifier = modifier
            .padding(14.dp)
    ) {
        if (connectionState != ConnectionState.CONNECTED) {
            ConnectionRequiredWarning(
                message = "Для выполнения функций требуется подключение к устройству",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Заголовок
        Text(
            text = "Выберите функцию:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 18.dp)
        )

        // Список функций (НЕ КЛИКАБЕЛЬНЫ)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            functions.forEachIndexed { index, function ->
                FunctionItem(
                    function = function,
                    isSelected = index == selectedIndex,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun FunctionItem(
    function: DeviceFunction,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = Color(0xFF4CAF50)
    val backgroundColor = if (isSelected) {
        primaryColor.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isSelected) {
        primaryColor
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    }

    val scale = animateFloatAsState(
        targetValue = if (isSelected) 1.005f else 1f,
        animationSpec = tween(durationMillis = 200)
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            width = if (isSelected) 1.5.dp else 0.5.dp
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 1.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Иконка функции
            Icon(
                painter = painterResource(id = function.iconResId),
                contentDescription = function.name,
                modifier = Modifier.size(26.dp),
                tint = if (isSelected) primaryColor else primaryColor.copy(alpha = 0.7f)
            )

            // Описание функции
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = function.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 22.sp
                )

                Text(
                    text = function.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp
                )
            }

            // Индикатор выбора
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(primaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DirectDosingScreen(
    tempValue: Int,
    currentValue: Int,
    onTempValueChange: (Int) -> Unit,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Заголовок
        Text(
            text = "Прямое дозирование",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 22.dp)
        )

        // Поле ввода временного значения (для изменения)
        NumberInputField(
            value = tempValue,
            label = "Объем забора (0-300 мл)",
            minValue = 0,
            maxValue = 300,
            isActive = true,
            isEnabled = connectionState == ConnectionState.CONNECTED,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Подсказка
        if (connectionState != ConnectionState.CONNECTED) {
            ConnectionRequiredWarning(
                message = "Для выполнения дозирования требуется подключение",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PartialDosingScreen(
    volume: Int,
    parts: Int,
    activeFieldIndex: Int,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Заголовок
        Text(
            text = "Частичное дозирование",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 22.dp)
        )

        // Поле ввода количества частей (первое поле)
        NumberInputField(
            value = parts,
            label = "Количество частей",
            minValue = 1,
            maxValue = 20,
            isActive = activeFieldIndex == 0,
            isEnabled = connectionState == ConnectionState.CONNECTED,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Поле ввода объема (второе поле)
        NumberInputField(
            value = volume,
            label = "Объем (мл)",
            minValue = 0,
            maxValue = 1000,
            isActive = activeFieldIndex == 1,
            isEnabled = connectionState == ConnectionState.CONNECTED,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Подсказка
        if (connectionState != ConnectionState.CONNECTED) {
            ConnectionRequiredWarning(
                message = "Для выполнения дозирования требуется подключение",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PartialFixedCollectionScreen(
    volume: Int,
    parts: Int,
    activeFieldIndex: Int,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Заголовок
        Text(
            text = "Частичный фиксированный забор",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 22.dp)
        )

        // Поле ввода объема
        NumberInputField(
            value = volume,
            label = "Объем забора (мл)",
            minValue = 0,
            maxValue = 500,
            isActive = activeFieldIndex == 0,
            isEnabled = connectionState == ConnectionState.CONNECTED,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Поле ввода количества частей
        NumberInputField(
            value = parts,
            label = "Количество частей",
            minValue = 1,
            maxValue = 10,
            isActive = activeFieldIndex == 1,
            isEnabled = connectionState == ConnectionState.CONNECTED,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Подсказка
        if (connectionState != ConnectionState.CONNECTED) {
            ConnectionRequiredWarning(
                message = "Для выполнения забора требуется подключение",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun FreeCollectionScreen(
    values: List<Int>,
    activeFieldIndex: Int,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Заголовок
        Text(
            text = "Свободный забор",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 22.dp)
        )

        // Пять полей ввода
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            values.forEachIndexed { index, value ->
                NumberInputField(
                    value = value,
                    label = "Объем ${index + 1} (мл)",
                    minValue = 0,
                    maxValue = 200,
                    isActive = activeFieldIndex == index,
                    isEnabled = connectionState == ConnectionState.CONNECTED,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Подсказка
        if (connectionState != ConnectionState.CONNECTED) {
            ConnectionRequiredWarning(
                message = "Для выполнения забора требуется подключение",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun NumberInputField(
    value: Int,
    label: String,
    minValue: Int,
    maxValue: Int,
    isActive: Boolean,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        !isEnabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        isActive -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }

    val backgroundColor = when {
        !isEnabled -> MaterialTheme.colorScheme.surfaceContainerLow
        isActive -> Color(0xFF4CAF50).copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        isActive -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.02f else 1f,
        animationSpec = tween(durationMillis = 200)
    )

    val borderWidth by animateFloatAsState(
        targetValue = if (isActive) 4f else 1f,
        animationSpec = tween(durationMillis = 200)
    )

    val elevation by animateFloatAsState(
        targetValue = if (isActive) 8f else 2f,
        animationSpec = tween(durationMillis = 200)
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Метка поля
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 22.sp,
            color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(backgroundColor, RoundedCornerShape(8.dp))
                .border(
                    width = if (isActive) 2.dp else 0.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Значение
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                fontSize = 24.sp
            )
        }
    }
    if (isActive && isEnabled) {
        Text(
            text = "▲ Используйте кнопки ВВЕРХ/ВНИЗ для изменения",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF4CAF50),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun SystemSettingsScreen(
    selectedIndex: Int,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Заглушка для экрана настроек
        Icon(
            painter = painterResource(id = R.drawable.settings),
            contentDescription = "Настройки",
            modifier = Modifier.size(50.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Системные настройки",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Раздел находится в разработке",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp
        )

        if (connectionState != ConnectionState.CONNECTED) {
            Spacer(modifier = Modifier.height(26.dp))
            ConnectionRequiredWarning(
                message = "Для доступа к настройкам требуется подключение",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun MainControlPanel(
    onUpClick: () -> Unit,
    onDownClick: () -> Unit,
    onBackClick: () -> Unit,
    onAcceptClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Группа навигации (Вверх/Вниз)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Кнопка ВВЕРХ
                SimpleControlButton(
                    iconResId = R.drawable.arrow_up,
                    label = "Вверх",
                    onClick = onUpClick,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    iconColor = MaterialTheme.colorScheme.primary
                )

                // Кнопка ВНИЗ
                SimpleControlButton(
                    iconResId = R.drawable.arrow_down,
                    label = "Вниз",
                    onClick = onDownClick,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    iconColor = MaterialTheme.colorScheme.primary
                )
            }

            // Группа действий (Назад/Принять)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Кнопка НАЗАД
                SimpleControlButton(
                    iconResId = R.drawable.arrow_back,
                    label = "Назад",
                    onClick = onBackClick,
                    backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    iconColor = MaterialTheme.colorScheme.error
                )

                // Кнопка ПРИНЯТЬ
                SimpleControlButton(
                    iconResId = R.drawable.check_circle,
                    label = "Принять",
                    onClick = onAcceptClick,
                    backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                    iconColor = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
fun DirectDosingControlPanel(
    onUpClick: () -> Unit,
    onDownClick: () -> Unit,
    onBackClick: () -> Unit,
    onAcceptClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Группа регулировки значений
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Кнопка ВВЕРХ (+)
                SimpleControlButton(
                    iconResId = R.drawable.arrow_up,
                    label = "+",
                    onClick = onUpClick,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    iconColor = MaterialTheme.colorScheme.primary
                )

                // Кнопка ВНИЗ (-)
                SimpleControlButton(
                    iconResId = R.drawable.arrow_down,
                    label = "-",
                    onClick = onDownClick,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    iconColor = MaterialTheme.colorScheme.error
                )
            }

            // Группа действий
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Кнопка НАЗАД
                SimpleControlButton(
                    iconResId = R.drawable.arrow_back,
                    label = "Назад",
                    onClick = onBackClick,
                    backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    iconColor = MaterialTheme.colorScheme.error
                )

                // Кнопка ВЫПОЛНИТЬ
                SimpleControlButton(
                    iconResId = R.drawable.check_circle,
                    label = "Выполнить",
                    onClick = onAcceptClick,
                    backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                    iconColor = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
fun PartialDosingControlPanel(
    onUpClick: () -> Unit,
    onDownClick: () -> Unit,
    onBackClick: () -> Unit,
    onAcceptClick: () -> Unit,
    activeFieldIndex: Int,
    totalFields: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Индикатор поля
            Text(
                text = "Поле ${activeFieldIndex + 1}/$totalFields",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Группа регулировки значений
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Кнопка ВВЕРХ (+)
                    SimpleControlButton(
                        iconResId = R.drawable.arrow_up,
                        label = "+",
                        onClick = onUpClick,
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        iconColor = MaterialTheme.colorScheme.primary
                    )

                    // Кнопка ВНИЗ (-)
                    SimpleControlButton(
                        iconResId = R.drawable.arrow_down,
                        label = "-",
                        onClick = onDownClick,
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        iconColor = MaterialTheme.colorScheme.error
                    )
                }

                // Группа действий
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Кнопка НАЗАД
                    SimpleControlButton(
                        iconResId = R.drawable.arrow_back,
                        label = "Назад",
                        onClick = onBackClick,
                        backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        iconColor = MaterialTheme.colorScheme.error
                    )

                    // Кнопка ДАЛЕЕ/ВЫПОЛНИТЬ
                    SimpleControlButton(
                        iconResId = R.drawable.check_circle,
                        label = if (activeFieldIndex < totalFields - 1) "Далее" else "Выполнить",
                        onClick = onAcceptClick,
                        backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                        iconColor = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

@Composable
fun PartialFixedControlPanel(
    onUpClick: () -> Unit,
    onDownClick: () -> Unit,
    onBackClick: () -> Unit,
    onAcceptClick: () -> Unit,
    activeFieldIndex: Int,
    totalFields: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Индикатор поля
            Text(
                text = "Поле ${activeFieldIndex + 1}/$totalFields",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Группа регулировки значений
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Кнопка ВВЕРХ (+)
                    SimpleControlButton(
                        iconResId = R.drawable.arrow_up,
                        label = "+",
                        onClick = onUpClick,
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        iconColor = MaterialTheme.colorScheme.primary
                    )

                    // Кнопка ВНИЗ (-)
                    SimpleControlButton(
                        iconResId = R.drawable.arrow_down,
                        label = "-",
                        onClick = onDownClick,
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        iconColor = MaterialTheme.colorScheme.error
                    )
                }

                // Группа действий
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Кнопка НАЗАД
                    SimpleControlButton(
                        iconResId = R.drawable.arrow_back,
                        label = "Назад",
                        onClick = onBackClick,
                        backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        iconColor = MaterialTheme.colorScheme.error
                    )

                    // Кнопка ДАЛЕЕ/ВЫПОЛНИТЬ
                    SimpleControlButton(
                        iconResId = R.drawable.check_circle,
                        label = if (activeFieldIndex < totalFields - 1) "Далее" else "Выполнить",
                        onClick = onAcceptClick,
                        backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                        iconColor = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

@Composable
fun FreeCollectionControlPanel(
    onUpClick: () -> Unit,
    onDownClick: () -> Unit,
    onBackClick: () -> Unit,
    onAcceptClick: () -> Unit,
    activeFieldIndex: Int,
    totalFields: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Индикатор поля
            Text(
                text = "Поле ${activeFieldIndex + 1}/$totalFields",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Группа регулировки значений
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Кнопка ВВЕРХ (+)
                    SimpleControlButton(
                        iconResId = R.drawable.arrow_up,
                        label = "+",
                        onClick = onUpClick,
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        iconColor = MaterialTheme.colorScheme.primary
                    )

                    // Кнопка ВНИЗ (-)
                    SimpleControlButton(
                        iconResId = R.drawable.arrow_down,
                        label = "-",
                        onClick = onDownClick,
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        iconColor = MaterialTheme.colorScheme.error
                    )
                }

                // Группа действий
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Кнопка НАЗАД
                    SimpleControlButton(
                        iconResId = R.drawable.arrow_back,
                        label = "Назад",
                        onClick = onBackClick,
                        backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        iconColor = MaterialTheme.colorScheme.error
                    )

                    // Кнопка ДАЛЕЕ/ВЫПОЛНИТЬ
                    SimpleControlButton(
                        iconResId = R.drawable.check_circle,
                        label = if (activeFieldIndex < totalFields - 1) "Далее" else "Выполнить",
                        onClick = onAcceptClick,
                        backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                        iconColor = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

@Composable
fun SimpleControlButton(
    iconResId: Int,
    label: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = backgroundColor,
            tonalElevation = 2.dp,
            onClick = onClick
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = iconColor
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@Composable
fun ConnectionStatusIndicator(
    connectionState: ConnectionState,
    currentScreen: DeviceScreen
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = when (connectionState) {
                ConnectionState.CONNECTED -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                ConnectionState.CONNECTING -> PrimaryColor.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            }
        ) {
            Icon(
                painter = painterResource(
                    id = when (connectionState) {
                        ConnectionState.CONNECTED -> R.drawable.outline_bluetooth_24
                        else -> R.drawable.outline_bluetooth_disabled_24
                    }
                ),
                contentDescription = "Статус подключения",
                modifier = Modifier.size(16.dp),
                tint = when (connectionState) {
                    ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                    ConnectionState.CONNECTING -> PrimaryColor
                    else -> MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
fun DeviceStatusCard(
    deviceName: String,
    connectionState: ConnectionState,
    deviceAddress: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    maxLines = 1
                )

                Text(
                    text = deviceAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }

            // Статус подключения
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = CircleShape,
                    color = when (connectionState) {
                        ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                        ConnectionState.CONNECTING -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.error
                    }
                ) {}

                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTED -> "Подключено"
                        ConnectionState.CONNECTING -> "Подключение..."
                        ConnectionState.DISCONNECTING -> "Отключение..."
                        else -> "Не подключено"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 13.sp,
                    color = when (connectionState) {
                        ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                        ConnectionState.CONNECTING -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ConnectionRequiredWarning(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Вспомогательные функции
private fun getFunctionsList(): List<DeviceFunction> {
    return listOf(
        DeviceFunction(
            id = "direct_dosing",
            name = "Прямое дозирование",
            description = "Прямая подача реагента",
            iconResId = R.drawable.science
        ),
        DeviceFunction(
            id = "partial_dosing",
            name = "Частичное дозирование",
            description = "Дозирование по частям",
            iconResId = R.drawable.timeline
        ),
        DeviceFunction(
            id = "partial_fixed_collection",
            name = "Частичный фиксированный забор",
            description = "Забор фиксированного объема",
            iconResId = R.drawable.rotate
        ),
        DeviceFunction(
            id = "free_collection",
            name = "Свободный забор",
            description = "Ручной забор реагента",
            iconResId = R.drawable.assignment
        )
    )
}