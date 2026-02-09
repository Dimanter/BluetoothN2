package com.example.bluetoothn2.screen

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Состояние для текущего экрана
    var currentScreen by remember { mutableStateOf<DeviceScreen>(DeviceScreen.MAIN) }

    // Состояния для выбранных индексов в разных экранах
    var mainSelectedIndex by remember { mutableStateOf(0) }
    var functionsSelectedIndex by remember { mutableStateOf(0) }
    var systemSettingsSelectedIndex by remember { mutableStateOf(0) }

    // Состояния для экранов ввода
    var directDosingValue by remember { mutableStateOf("0") }
    var partialDosingVolume by remember { mutableStateOf("0") }
    var partialDosingParts by remember { mutableStateOf("1") }
    var partialFixedVolume by remember { mutableStateOf("0") }
    var partialFixedParts by remember { mutableStateOf("1") }
    var freeCollectionValues by remember { mutableStateOf(List(5) { "0" }) }

    // Индекс активного поля для экранов ввода
    var activeInputFieldIndex by remember { mutableStateOf(0) }

    // Сохраняем выбранный индекс функций при переходе на экран дозирования
    var savedFunctionsIndex by remember { mutableStateOf(0) }

    // Job для управления задержкой навигации
    var navigationDebounceJob by remember { mutableStateOf<Job?>(null) }

    // Состояние для управления фокусом полей ввода
    var hasTextFieldFocus by remember { mutableStateOf(false) }
    var currentFocusFieldId by remember { mutableStateOf<String?>(null) }

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

    // Функция для открытия клавиатуры
    fun openKeyboard(fieldId: String) {
        keyboardController?.show()
        hasTextFieldFocus = true
        currentFocusFieldId = fieldId
    }

    // Функция для закрытия клавиатуры
    fun closeKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus()
        hasTextFieldFocus = false
        currentFocusFieldId = null
    }

    // Обработчик системной кнопки "Назад" - ЗАБЛОКИРОВАН
    BackHandler(enabled = true) {
        // Ничего не делаем - кнопка "назад" заблокирована
        // Навигация только через панель управления
    }

    // Закрываем клавиатуру при переходе между экранами
    LaunchedEffect(currentScreen) {
        closeKeyboard()
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
                    // УБРАНА КНОПКА НАЗАД
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
                    value = directDosingValue,
                    onValueChange = { directDosingValue = it },
                    connectionState = uiState.connectionState,
                    isFocused = hasTextFieldFocus && currentFocusFieldId == "direct_dosing",
                    onFocusChange = { focused ->
                        hasTextFieldFocus = focused
                        currentFocusFieldId = if (focused) "direct_dosing" else null
                    },
                    modifier = Modifier.fillMaxSize()
                )
                DeviceScreen.PARTIAL_DOSING -> PartialDosingScreen(
                    volume = partialDosingVolume,
                    parts = partialDosingParts,
                    activeFieldIndex = activeInputFieldIndex,
                    onVolumeChange = { partialDosingVolume = it },
                    onPartsChange = { partialDosingParts = it },
                    connectionState = uiState.connectionState,
                    isVolumeFocused = hasTextFieldFocus && currentFocusFieldId == "partial_volume",
                    isPartsFocused = hasTextFieldFocus && currentFocusFieldId == "partial_parts",
                    onFocusChange = { field, focused ->
                        hasTextFieldFocus = focused
                        currentFocusFieldId = if (focused) field else null
                    },
                    modifier = Modifier.fillMaxSize()
                )
                DeviceScreen.PARTIAL_FIXED_COLLECTION -> PartialFixedCollectionScreen(
                    volume = partialFixedVolume,
                    parts = partialFixedParts,
                    activeFieldIndex = activeInputFieldIndex,
                    onVolumeChange = { partialFixedVolume = it },
                    onPartsChange = { partialFixedParts = it },
                    connectionState = uiState.connectionState,
                    isVolumeFocused = hasTextFieldFocus && currentFocusFieldId == "fixed_volume",
                    isPartsFocused = hasTextFieldFocus && currentFocusFieldId == "fixed_parts",
                    onFocusChange = { field, focused ->
                        hasTextFieldFocus = focused
                        currentFocusFieldId = if (focused) field else null
                    },
                    modifier = Modifier.fillMaxSize()
                )
                DeviceScreen.FREE_COLLECTION -> FreeCollectionScreen(
                    values = freeCollectionValues,
                    activeFieldIndex = activeInputFieldIndex,
                    onValueChange = { index, value ->
                        val newValues = freeCollectionValues.toMutableList()
                        newValues[index] = value
                        freeCollectionValues = newValues
                    },
                    connectionState = uiState.connectionState,
                    isFieldFocused = { index ->
                        hasTextFieldFocus && currentFocusFieldId == "free_$index"
                    },
                    onFocusChange = { index, focused ->
                        hasTextFieldFocus = focused
                        currentFocusFieldId = if (focused) "free_$index" else null
                    },
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
                                            closeKeyboard()
                                        }
                                        DeviceScreen.SYSTEM_SETTINGS -> {
                                            currentScreen = DeviceScreen.MAIN
                                            activeInputFieldIndex = 0
                                            closeKeyboard()
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
                                                closeKeyboard()
                                            }
                                            1 -> {
                                                currentScreen = DeviceScreen.SYSTEM_SETTINGS
                                                activeInputFieldIndex = 0
                                                closeKeyboard()
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
                                                    // Автоматически открываем клавиатуру при входе
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        openKeyboard("direct_dosing")
                                                    }
                                                }
                                                "partial_dosing" -> {
                                                    currentScreen = DeviceScreen.PARTIAL_DOSING
                                                    activeInputFieldIndex = 0
                                                    // Автоматически открываем клавиатуру при входе
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        openKeyboard("partial_volume")
                                                    }
                                                }
                                                "partial_fixed_collection" -> {
                                                    currentScreen = DeviceScreen.PARTIAL_FIXED_COLLECTION
                                                    activeInputFieldIndex = 0
                                                    // Автоматически открываем клавиатуру при входе
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        openKeyboard("fixed_volume")
                                                    }
                                                }
                                                "free_collection" -> {
                                                    currentScreen = DeviceScreen.FREE_COLLECTION
                                                    activeInputFieldIndex = 0
                                                    // Автоматически открываем клавиатуру при входе
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        openKeyboard("free_0")
                                                    }
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
                        onBackClick = {
                            withNavigationDebounce {
                                currentScreen = DeviceScreen.FUNCTIONS
                                // Восстанавливаем сохраненный индекс
                                functionsSelectedIndex = savedFunctionsIndex
                                activeInputFieldIndex = 0
                                closeKeyboard()
                            }
                        },
                        onAcceptClick = {
                            withNavigationDebounce {
                                if (uiState.connectionState == ConnectionState.CONNECTED) {
                                    // Отправляем команду
                                    val value = directDosingValue.toIntOrNull() ?: 0
                                    val command = "START_DIRECT:$value\r\n"
                                    viewModel.sendCommand(command)
                                    closeKeyboard()
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
                        onBackClick = {
                            withNavigationDebounce {
                                if (activeInputFieldIndex > 0) {
                                    activeInputFieldIndex--
                                    openKeyboard(if (activeInputFieldIndex == 0) "partial_volume" else "partial_parts")
                                } else {
                                    currentScreen = DeviceScreen.FUNCTIONS
                                    // Восстанавливаем сохраненный индекс
                                    functionsSelectedIndex = savedFunctionsIndex
                                    activeInputFieldIndex = 0
                                    closeKeyboard()
                                }
                            }
                        },
                        onAcceptClick = {
                            withNavigationDebounce {
                                if (activeInputFieldIndex == 0) {
                                    activeInputFieldIndex = 1
                                    openKeyboard("partial_parts")
                                } else {
                                    if (uiState.connectionState == ConnectionState.CONNECTED) {
                                        val volume = partialDosingVolume.toIntOrNull() ?: 0
                                        val parts = partialDosingParts.toIntOrNull() ?: 1
                                        val command = "START_PARTIAL:$volume:$parts\r\n"
                                        viewModel.sendCommand(command)
                                        closeKeyboard()
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
                        onBackClick = {
                            withNavigationDebounce {
                                if (activeInputFieldIndex > 0) {
                                    activeInputFieldIndex--
                                    openKeyboard(if (activeInputFieldIndex == 0) "fixed_volume" else "fixed_parts")
                                } else {
                                    currentScreen = DeviceScreen.FUNCTIONS
                                    // Восстанавливаем сохраненный индекс
                                    functionsSelectedIndex = savedFunctionsIndex
                                    activeInputFieldIndex = 0
                                    closeKeyboard()
                                }
                            }
                        },
                        onAcceptClick = {
                            withNavigationDebounce {
                                if (activeInputFieldIndex == 0) {
                                    activeInputFieldIndex = 1
                                    openKeyboard("fixed_parts")
                                } else {
                                    if (uiState.connectionState == ConnectionState.CONNECTED) {
                                        val volume = partialFixedVolume.toIntOrNull() ?: 0
                                        val parts = partialFixedParts.toIntOrNull() ?: 1
                                        val command = "START_FIXED_COLLECTION:$volume:$parts\r\n"
                                        viewModel.sendCommand(command)
                                        closeKeyboard()
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
                        onBackClick = {
                            withNavigationDebounce {
                                if (activeInputFieldIndex > 0) {
                                    activeInputFieldIndex--
                                    openKeyboard("free_$activeInputFieldIndex")
                                } else {
                                    currentScreen = DeviceScreen.FUNCTIONS
                                    // Восстанавливаем сохраненный индекс
                                    functionsSelectedIndex = savedFunctionsIndex
                                    activeInputFieldIndex = 0
                                    closeKeyboard()
                                }
                            }
                        },
                        onAcceptClick = {
                            withNavigationDebounce {
                                if (activeInputFieldIndex < 4) {
                                    activeInputFieldIndex++
                                    openKeyboard("free_$activeInputFieldIndex")
                                } else {
                                    if (uiState.connectionState == ConnectionState.CONNECTED) {
                                        val valuesStr = freeCollectionValues.joinToString(":")
                                        val command = "START_FREE_COLLECTION:$valuesStr\r\n"
                                        viewModel.sendCommand(command)
                                        closeKeyboard()
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
    value: String,
    onValueChange: (String) -> Unit,
    connectionState: ConnectionState,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Автоматически запрашиваем фокус при появлении
    LaunchedEffect(Unit) {
        if (connectionState == ConnectionState.CONNECTED) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = modifier
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Заголовок
        Text(
            text = "Прямое дозирование",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Поле ввода
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Объем",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    width = if (isFocused) 2.dp else 1.dp
                )
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { newValue ->
                        // Фильтруем только цифры
                        val filtered = newValue.filter { it.isDigit() }
                        onValueChange(filtered)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            onFocusChange(focusState.isFocused)
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            onFocusChange(false)
                        }
                    ),
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    text = "Введите объем...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 18.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Text(
                text = "Введите значение в мл (0-300 мл)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Подсказка
        if (connectionState != ConnectionState.CONNECTED) {
            ConnectionRequiredWarning(
                message = "Для выполнения дозирования требуется подключение к устройству",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.keyboard),
                        contentDescription = "Клавиатура",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Используйте клавиатуру для ввода значения",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun PartialDosingScreen(
    volume: String,
    parts: String,
    activeFieldIndex: Int,
    onVolumeChange: (String) -> Unit,
    onPartsChange: (String) -> Unit,
    connectionState: ConnectionState,
    isVolumeFocused: Boolean,
    isPartsFocused: Boolean,
    onFocusChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val volumeFocusRequester = remember { FocusRequester() }
    val partsFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Управляем фокусом в зависимости от активного поля
    LaunchedEffect(activeFieldIndex) {
        when (activeFieldIndex) {
            0 -> {
                volumeFocusRequester.requestFocus()
                keyboardController?.show()
            }
            1 -> {
                partsFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    Column(
        modifier = modifier
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Заголовок
        Text(
            text = "Частичное дозирование",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Поля ввода
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Поле для объема
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Объем (мл)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isVolumeFocused) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        width = if (isVolumeFocused) 2.dp else 1.dp
                    )
                ) {
                    BasicTextField(
                        value = volume,
                        onValueChange = { newValue ->
                            val filtered = newValue.filter { it.isDigit() }
                            onVolumeChange(filtered)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .focusRequester(volumeFocusRequester)
                            .onFocusChanged { focusState ->
                                onFocusChange("partial_volume", focusState.isFocused)
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                onFocusChange("partial_parts", true)
                            }
                        ),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (volume.isEmpty()) {
                                    Text(
                                        text = "Введите объем...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 18.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            // Поле для количества частей
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Количество частей",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPartsFocused) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        width = if (isPartsFocused) 2.dp else 1.dp
                    )
                ) {
                    BasicTextField(
                        value = parts,
                        onValueChange = { newValue ->
                            val filtered = newValue.filter { it.isDigit() }
                            onPartsChange(filtered)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .focusRequester(partsFocusRequester)
                            .onFocusChanged { focusState ->
                                onFocusChange("partial_parts", focusState.isFocused)
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                onFocusChange("partial_parts", false)
                            }
                        ),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (parts.isEmpty()) {
                                    Text(
                                        text = "Введите количество...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 18.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Подсказка
        if (connectionState != ConnectionState.CONNECTED) {
            ConnectionRequiredWarning(
                message = "Для выполнения дозирования требуется подключение",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Активное поле: ${if (activeFieldIndex == 0) "Объем" else "Части"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Используйте клавиатуру для ввода значений",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun PartialFixedCollectionScreen(
    volume: String,
    parts: String,
    activeFieldIndex: Int,
    onVolumeChange: (String) -> Unit,
    onPartsChange: (String) -> Unit,
    connectionState: ConnectionState,
    isVolumeFocused: Boolean,
    isPartsFocused: Boolean,
    onFocusChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val volumeFocusRequester = remember { FocusRequester() }
    val partsFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Управляем фокусом в зависимости от активного поля
    LaunchedEffect(activeFieldIndex) {
        when (activeFieldIndex) {
            0 -> {
                volumeFocusRequester.requestFocus()
                keyboardController?.show()
            }
            1 -> {
                partsFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    Column(
        modifier = modifier
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Заголовок
        Text(
            text = "Частичный фиксированный забор",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Поля ввода
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Поле для объема
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Объем забора (мл)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isVolumeFocused) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        width = if (isVolumeFocused) 2.dp else 1.dp
                    )
                ) {
                    BasicTextField(
                        value = volume,
                        onValueChange = { newValue ->
                            val filtered = newValue.filter { it.isDigit() }
                            onVolumeChange(filtered)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .focusRequester(volumeFocusRequester)
                            .onFocusChanged { focusState ->
                                onFocusChange("fixed_volume", focusState.isFocused)
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                onFocusChange("fixed_parts", true)
                            }
                        ),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (volume.isEmpty()) {
                                    Text(
                                        text = "Введите объем...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 18.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            // Поле для количества частей
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Количество частей",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPartsFocused) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        width = if (isPartsFocused) 2.dp else 1.dp
                    )
                ) {
                    BasicTextField(
                        value = parts,
                        onValueChange = { newValue ->
                            val filtered = newValue.filter { it.isDigit() }
                            onPartsChange(filtered)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .focusRequester(partsFocusRequester)
                            .onFocusChanged { focusState ->
                                onFocusChange("fixed_parts", focusState.isFocused)
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                onFocusChange("fixed_parts", false)
                            }
                        ),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (parts.isEmpty()) {
                                    Text(
                                        text = "Введите количество...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 18.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Подсказка
        if (connectionState != ConnectionState.CONNECTED) {
            ConnectionRequiredWarning(
                message = "Для выполнения забора требуется подключение",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Активное поле: ${if (activeFieldIndex == 0) "Объем" else "Части"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Используйте клавиатуру для ввода значений",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun FreeCollectionScreen(
    values: List<String>,
    activeFieldIndex: Int,
    onValueChange: (Int, String) -> Unit,
    connectionState: ConnectionState,
    isFieldFocused: (Int) -> Boolean,
    onFocusChange: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequesters = remember { List(5) { FocusRequester() } }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Управляем фокусом в зависимости от активного поля
    LaunchedEffect(activeFieldIndex) {
        if (activeFieldIndex in 0..4) {
            focusRequesters[activeFieldIndex].requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = modifier
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Заголовок
        Text(
            text = "Свободный забор",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Пять полей ввода
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            values.forEachIndexed { index, value ->
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Объем ${index + 1} (мл)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFieldFocused(index)) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            width = if (isFieldFocused(index)) 2.dp else 1.dp
                        )
                    ) {
                        BasicTextField(
                            value = value,
                            onValueChange = { newValue ->
                                val filtered = newValue.filter { it.isDigit() }
                                onValueChange(index, filtered)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .focusRequester(focusRequesters[index])
                                .onFocusChanged { focusState ->
                                    onFocusChange(index, focusState.isFocused)
                                },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = if (index < 4) ImeAction.Next else ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    if (index < 4) {
                                        onFocusChange(index + 1, true)
                                    }
                                },
                                onDone = {
                                    keyboardController?.hide()
                                    onFocusChange(index, false)
                                }
                            ),
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 20.sp
                            ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (value.isEmpty()) {
                                        Text(
                                            text = "Введите объем...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 18.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Подсказка
        if (connectionState != ConnectionState.CONNECTED) {
            ConnectionRequiredWarning(
                message = "Для выполнения забора требуется подключение",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Активное поле: Объем ${activeFieldIndex + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Используйте клавиатуру для ввода значений",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
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
            // Левая сторона - кнопка НАЗАД
            SimpleControlButton(
                iconResId = R.drawable.arrow_back,
                label = "Назад",
                onClick = onBackClick,
                backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                iconColor = MaterialTheme.colorScheme.error
            )

            // Правая сторона - кнопка ВЫПОЛНИТЬ
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

@Composable
fun PartialDosingControlPanel(
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

@Composable
fun PartialFixedControlPanel(
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

@Composable
fun FreeCollectionControlPanel(
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