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
    var contrastReductionValue by remember { mutableStateOf("0") }
    var sleepModeValue by remember { mutableStateOf("0") }
    var strokeSpeedIndex by remember { mutableStateOf(1) } // 0: высокая, 1: средняя, 2: низкая
    var maxVolumeValue by remember { mutableStateOf("0") }
    var coefficientD6Value1 by remember { mutableStateOf("0") }
    var coefficientD6Value2 by remember { mutableStateOf("0") }
    var coefficientRealValue1 by remember { mutableStateOf("0") }
    var coefficientRealValue2 by remember { mutableStateOf("0") }

    // Индекс активного поля для экранов ввода
    var activeInputFieldIndex by remember { mutableStateOf(0) }

    // Состояния для экранов ввода
    var directDosingValue by remember { mutableStateOf("200") }
    var partialDosingVolume by remember { mutableStateOf("40") }
    var partialDosingParts by remember { mutableStateOf("5") }
    var partialFixedVolume by remember { mutableStateOf("0") }
    var partialFixedParts by remember { mutableStateOf("1") }
    var freeCollectionValues by remember { mutableStateOf(listOf("20", "30", "10", "50", "60")) }

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

    // Функция для отправки команд навигации
    fun sendNavigationCommand(command: String) {
        if (uiState.connectionState == ConnectionState.CONNECTED) {
            viewModel.sendCommand("$command:\r\n")
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
                            DeviceScreen.CONTRAST_REDUCTION -> "Снижение контрастности"
                            DeviceScreen.SLEEP_MODE -> "Спящий режим"
                            DeviceScreen.STROKE_SPEED -> "Скорость штока"
                            DeviceScreen.MAX_VOLUME -> "Максимальный объем забора"
                            DeviceScreen.COEFFICIENT_CORRECTION -> "Коррекция коэффициентов"
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

                DeviceScreen.CONTRAST_REDUCTION -> ContrastReductionScreen(
                    value = contrastReductionValue,
                    onValueChange = { contrastReductionValue = it },
                    connectionState = uiState.connectionState,
                    isFocused = hasTextFieldFocus && currentFocusFieldId == "contrast",
                    onFocusChange = { focused ->
                        hasTextFieldFocus = focused
                        currentFocusFieldId = if (focused) "contrast" else null
                    },
                    modifier = Modifier.fillMaxSize()
                )

                DeviceScreen.SLEEP_MODE -> SleepModeScreen(
                    value = sleepModeValue,
                    onValueChange = { sleepModeValue = it },
                    connectionState = uiState.connectionState,
                    isFocused = hasTextFieldFocus && currentFocusFieldId == "sleep",
                    onFocusChange = { focused ->
                        hasTextFieldFocus = focused
                        currentFocusFieldId = if (focused) "sleep" else null
                    },
                    modifier = Modifier.fillMaxSize()
                )

                DeviceScreen.STROKE_SPEED -> StrokeSpeedScreen(
                    selectedIndex = strokeSpeedIndex,
                    onSelectedIndexChange = { strokeSpeedIndex = it },
                    connectionState = uiState.connectionState,
                    modifier = Modifier.fillMaxSize()
                )

                DeviceScreen.MAX_VOLUME -> MaxVolumeScreen(
                    value = maxVolumeValue,
                    onValueChange = { maxVolumeValue = it },
                    connectionState = uiState.connectionState,
                    isFocused = hasTextFieldFocus && currentFocusFieldId == "max_volume",
                    onFocusChange = { focused ->
                        hasTextFieldFocus = focused
                        currentFocusFieldId = if (focused) "max_volume" else null
                    },
                    modifier = Modifier.fillMaxSize()
                )

                DeviceScreen.COEFFICIENT_CORRECTION -> CoefficientCorrectionScreen(
                    d6Value1 = coefficientD6Value1,
                    d6Value2 = coefficientD6Value2,
                    realValue1 = coefficientRealValue1,
                    realValue2 = coefficientRealValue2,
                    activeFieldIndex = activeInputFieldIndex,
                    onD6Value1Change = { coefficientD6Value1 = it },
                    onD6Value2Change = { coefficientD6Value2 = it },
                    onRealValue1Change = { coefficientRealValue1 = it },
                    onRealValue2Change = { coefficientRealValue2 = it },
                    connectionState = uiState.connectionState,
                    isFieldFocused = { index ->
                        hasTextFieldFocus && currentFocusFieldId == "coefficient_$index"
                    },
                    onFocusChange = { index, focused ->
                        hasTextFieldFocus = focused
                        currentFocusFieldId = if (focused) "coefficient_$index" else null
                    },
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
                                // Отправляем команду "up"
                                sendNavigationCommand("UP")

                                val maxIndex = when (currentScreen) {
                                    DeviceScreen.MAIN -> 1
                                    DeviceScreen.FUNCTIONS -> 3
                                    DeviceScreen.SYSTEM_SETTINGS -> 4 // 5 пунктов (0-4)
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
                                // Отправляем команду "down"
                                sendNavigationCommand("DOWN")

                                val maxIndex = when (currentScreen) {
                                    DeviceScreen.MAIN -> 1
                                    DeviceScreen.FUNCTIONS -> 3
                                    DeviceScreen.SYSTEM_SETTINGS -> 4 // 5 пунктов (0-4)
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
                                // Отправляем команду "back"
                                sendNavigationCommand("BACK")

                                coroutineScope.launch {
                                    when (currentScreen) {
                                        DeviceScreen.MAIN -> {
                                            viewModel.cleanup()
                                            onBack()
                                        }

                                        DeviceScreen.FUNCTIONS -> {
                                            currentScreen = DeviceScreen.MAIN
                                            closeKeyboard()
                                        }

                                        DeviceScreen.SYSTEM_SETTINGS -> {
                                            currentScreen = DeviceScreen.MAIN
                                            closeKeyboard()
                                        }

                                        else -> {}
                                    }
                                }
                            }
                        },
                        onAcceptClick = {
                            withNavigationDebounce {
                                // Отправляем команду "enter"
                                sendNavigationCommand("ENTER")

                                when (currentScreen) {
                                    DeviceScreen.MAIN -> {
                                        when (mainSelectedIndex) {
                                            0 -> {
                                                currentScreen = DeviceScreen.FUNCTIONS
                                                closeKeyboard()
                                            }

                                            1 -> {
                                                currentScreen = DeviceScreen.SYSTEM_SETTINGS
                                                closeKeyboard()
                                            }
                                        }
                                    }

                                    DeviceScreen.FUNCTIONS -> {
                                        val function =
                                            getFunctionsList().getOrNull(functionsSelectedIndex)
                                        function?.let {
                                            // Сохраняем текущий индекс перед переходом
                                            savedFunctionsIndex = functionsSelectedIndex
                                            when (it.id) {
                                                "direct_dosing" -> {
                                                    currentScreen = DeviceScreen.DIRECT_DOSING
                                                    // Автоматически открываем клавиатуру при входе
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        openKeyboard("direct_dosing")
                                                    }
                                                }

                                                "partial_dosing" -> {
                                                    currentScreen = DeviceScreen.PARTIAL_DOSING
                                                    // Автоматически открываем клавиатуру при входе
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        openKeyboard("partial_volume")
                                                    }
                                                }

                                                "partial_fixed_collection" -> {
                                                    currentScreen =
                                                        DeviceScreen.PARTIAL_FIXED_COLLECTION
                                                    // Автоматически открываем клавиатуру при входе
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        openKeyboard("fixed_volume")
                                                    }
                                                }

                                                "free_collection" -> {
                                                    currentScreen = DeviceScreen.FREE_COLLECTION
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
                                        val setting = getSystemSettingsList().getOrNull(
                                            systemSettingsSelectedIndex
                                        )
                                        setting?.let {
                                            when (it.id) {
                                                "contrast_reduction" -> {
                                                    currentScreen = DeviceScreen.CONTRAST_REDUCTION
                                                    activeInputFieldIndex = 0
                                                    // Автоматически открываем клавиатуру при входе
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        openKeyboard("contrast")
                                                    }
                                                }

                                                "sleep_mode" -> {
                                                    currentScreen = DeviceScreen.SLEEP_MODE
                                                    activeInputFieldIndex = 0
                                                    // Автоматически открываем клавиатуру при входе
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        openKeyboard("sleep")
                                                    }
                                                }

                                                "stroke_speed" -> {
                                                    currentScreen = DeviceScreen.STROKE_SPEED
                                                    activeInputFieldIndex = 0
                                                    closeKeyboard()
                                                }

                                                "max_volume" -> {
                                                    currentScreen = DeviceScreen.MAX_VOLUME
                                                    activeInputFieldIndex = 0
                                                    // Автоматически открываем клавиатуру при входе
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        openKeyboard("max_volume")
                                                    }
                                                }

                                                "coefficient_correction" -> {
                                                    currentScreen =
                                                        DeviceScreen.COEFFICIENT_CORRECTION
                                                    activeInputFieldIndex = 0
                                                    // Автоматически открываем клавиатуру при входе
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        openKeyboard("coefficient_0")
                                                    }
                                                }
                                            }
                                        }
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
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                // Отправляем команду
                                val value = directDosingValue.toIntOrNull() ?: 200
                                val command = "START_DIRECT:$value\r\n"
                                viewModel.sendCommand(command)
                            }
                            withNavigationDebounce {
                                currentScreen = DeviceScreen.FUNCTIONS
                                // Восстанавливаем сохраненный индекс
                                functionsSelectedIndex = savedFunctionsIndex
                                closeKeyboard()
                            }
                        },
                        onAcceptClick = {
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                // Отправляем команду
                                val value = directDosingValue.toIntOrNull() ?: 200
                                val command = "START_DIRECT:$value\r\n"
                                viewModel.sendCommand(command)
                            }
                            withNavigationDebounce {
                                closeKeyboard()
                                // Возвращаемся на предыдущий экран
                                currentScreen = DeviceScreen.FUNCTIONS
                                functionsSelectedIndex = savedFunctionsIndex
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
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                val volume = partialDosingVolume.toIntOrNull() ?: 40
                                val parts = partialDosingParts.toIntOrNull() ?: 5
                                val command = "START_PARIAL:$volume:$parts\r\n"
                                viewModel.sendCommand(command)
                            }
                            withNavigationDebounce {
                                currentScreen = DeviceScreen.FUNCTIONS
                                // Восстанавливаем сохраненный индекс
                                functionsSelectedIndex = savedFunctionsIndex
                                closeKeyboard()
                            }
                        },
                        onAcceptClick = {
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                val volume = partialDosingVolume.toIntOrNull() ?: 40
                                val parts = partialDosingParts.toIntOrNull() ?: 5
                                val command = "START_PARIAL:$volume:$parts\r\n"
                                viewModel.sendCommand(command)
                            }
                            withNavigationDebounce {

                                closeKeyboard()
                                // Возвращаемся на предыдущий экран
                                currentScreen = DeviceScreen.FUNCTIONS
                                functionsSelectedIndex = savedFunctionsIndex

                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                DeviceScreen.PARTIAL_FIXED_COLLECTION -> {
                    PartialFixedControlPanel(
                        onBackClick = {
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                val volume = partialFixedVolume.toIntOrNull() ?: 0
                                val parts = partialFixedParts.toIntOrNull() ?: 1
                                val command = "START_FIXED:$volume:$parts\r\n"
                                viewModel.sendCommand(command)
                            }
                            withNavigationDebounce {
                                closeKeyboard()
                                // Возвращаемся на предыдущий экран
                                currentScreen = DeviceScreen.FUNCTIONS
                                functionsSelectedIndex = savedFunctionsIndex
                            }
                        },
                        onAcceptClick = {
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                val volume = partialFixedVolume.toIntOrNull() ?: 0
                                val parts = partialFixedParts.toIntOrNull() ?: 1
                                val command = "START_FIXED:$volume:$parts\r\n"
                                viewModel.sendCommand(command)
                            }
                            withNavigationDebounce {
                                closeKeyboard()
                                // Возвращаемся на предыдущий экран
                                currentScreen = DeviceScreen.FUNCTIONS
                                functionsSelectedIndex = savedFunctionsIndex
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                DeviceScreen.FREE_COLLECTION -> {
                    FreeCollectionControlPanel(
                        onBackClick = {
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                val valuesStr = freeCollectionValues.joinToString(":")
                                val command = "START_FREE:$valuesStr\r\n"
                                viewModel.sendCommand(command)
                            }
                            withNavigationDebounce {
                                closeKeyboard()
                                // Возвращаемся на предыдущий экран
                                currentScreen = DeviceScreen.FUNCTIONS
                                functionsSelectedIndex = savedFunctionsIndex
                            }
                        },
                        onAcceptClick = {
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                val valuesStr = freeCollectionValues.joinToString(":")
                                val command = "START_FREE:$valuesStr\r\n"
                                viewModel.sendCommand(command)
                            }
                            withNavigationDebounce {
                                closeKeyboard()
                                // Возвращаемся на предыдущий экран
                                currentScreen = DeviceScreen.FUNCTIONS
                                functionsSelectedIndex = savedFunctionsIndex
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                DeviceScreen.CONTRAST_REDUCTION,
                DeviceScreen.SLEEP_MODE,
                DeviceScreen.MAX_VOLUME -> {
                    SystemSettingControlPanel(
                        onBackClick = {
                            withNavigationDebounce {
                                currentScreen = DeviceScreen.SYSTEM_SETTINGS
                                closeKeyboard()
                            }
                        },
                        onAcceptClick = {
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                // Отправляем команду в зависимости от текущего экрана
                                val command = when (currentScreen) {
                                    DeviceScreen.CONTRAST_REDUCTION -> {
                                        val value = contrastReductionValue.toIntOrNull() ?: 0
                                        "CONTRAST:$value\r\n"
                                    }

                                    DeviceScreen.SLEEP_MODE -> {
                                        val value = sleepModeValue.toIntOrNull() ?: 0
                                        "SLEEP:$value\r\n"
                                    }

                                    DeviceScreen.MAX_VOLUME -> {
                                        val value = maxVolumeValue.toIntOrNull() ?: 0
                                        "MAX_VOLUME:$value\r\n"
                                    }

                                    else -> ""
                                }
                                if (command.isNotEmpty()) {
                                    viewModel.sendCommand(command)
                                }
                            }
                            withNavigationDebounce {
                                closeKeyboard()
                                // Возвращаемся на предыдущий экран
                                currentScreen = DeviceScreen.SYSTEM_SETTINGS
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                DeviceScreen.STROKE_SPEED -> {
                    StrokeSpeedControlPanelWithArrows(
                        onBackClick = {
                            withNavigationDebounce {
                                currentScreen = DeviceScreen.SYSTEM_SETTINGS
                                closeKeyboard()
                            }
                        },
                        onUpClick = {
                            withNavigationDebounce {
                                sendNavigationCommand("UP")
                                // Уменьшаем индекс, если можно
                                if (strokeSpeedIndex > 0) {
                                    strokeSpeedIndex--
                                }
                            }
                        },
                        onDownClick = {
                            withNavigationDebounce {
                                sendNavigationCommand("DOWN")
                                // Увеличиваем индекс, если можно
                                if (strokeSpeedIndex < 2) {
                                    strokeSpeedIndex++
                                }
                            }
                        },
                        onAcceptClick = {
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                val speed = when (strokeSpeedIndex) {
                                    0 -> "HIGH"
                                    1 -> "MEDIUM"
                                    2 -> "LOW"
                                    else -> "MEDIUM"
                                }
                                val command = "STROKE_SPEED:$speed\r\n"
                                viewModel.sendCommand(command)
                            }
                            withNavigationDebounce {
                                closeKeyboard()
                                // Возвращаемся на предыдущий экран
                                currentScreen = DeviceScreen.SYSTEM_SETTINGS
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                DeviceScreen.COEFFICIENT_CORRECTION -> {
                    CoefficientCorrectionControlPanel(
                        onBackClick = {
                            withNavigationDebounce {
                                currentScreen = DeviceScreen.SYSTEM_SETTINGS
                                closeKeyboard()
                            }
                        },
                        onAcceptClick = {
                            withNavigationDebounce {
                                if (uiState.connectionState == ConnectionState.CONNECTED) {
                                    val command =
                                        "COEFFICIENT:$coefficientD6Value1:$coefficientD6Value2:$coefficientRealValue1:$coefficientRealValue2\r\n"
                                    viewModel.sendCommand(command)
                                    closeKeyboard()
                                    // Возвращаемся на предыдущий экран
                                    currentScreen = DeviceScreen.SYSTEM_SETTINGS
                                }
                            }
                        },
                        activeFieldIndex = activeInputFieldIndex,
                        totalFields = 4,
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
    SYSTEM_SETTINGS,
    CONTRAST_REDUCTION,
    SLEEP_MODE,
    STROKE_SPEED,
    MAX_VOLUME,
    COEFFICIENT_CORRECTION
}

// Модель функции устройства
data class DeviceFunction(
    val id: String,
    val name: String,
    val description: String,
    val iconResId: Int
)

// Модель системной настройки
data class SystemSetting(
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
        iconColor.copy(alpha = 0.15f)
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
        primaryColor.copy(alpha = 0.2f)
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
    LaunchedEffect(isVolumeFocused) {
        if (isVolumeFocused) {
            volumeFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(isPartsFocused) {
        if (isPartsFocused) {
            partsFocusRequester.requestFocus()
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
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                onFocusChange("partial_volume", false)
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
    LaunchedEffect(isVolumeFocused) {
        if (isVolumeFocused) {
            volumeFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(isPartsFocused) {
        if (isPartsFocused) {
            partsFocusRequester.requestFocus()
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
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                onFocusChange("fixed_volume", false)
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
    onValueChange: (Int, String) -> Unit,
    connectionState: ConnectionState,
    isFieldFocused: (Int) -> Boolean,
    onFocusChange: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequesters = remember { List(5) { FocusRequester() } }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Управляем фокусом в зависимости от активного поля
    LaunchedEffect(isFieldFocused) {
        (0..4).forEach { index ->
            if (isFieldFocused(index)) {
                focusRequesters[index].requestFocus()
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
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
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
    val settings = remember { getSystemSettingsList() }

    Column(
        modifier = modifier
            .padding(14.dp)
    ) {
        if (connectionState != ConnectionState.CONNECTED) {
            ConnectionRequiredWarning(
                message = "Для изменения настроек требуется подключение к устройству",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Заголовок
        Text(
            text = "Выберите настройку:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 18.dp)
        )

        // Список настроек (НЕ КЛИКАБЕЛЬНЫ)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            settings.forEachIndexed { index, setting ->
                SystemSettingItem(
                    setting = setting,
                    isSelected = index == selectedIndex,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SystemSettingItem(
    setting: SystemSetting,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = Color(0xFF2196F3)
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
            // Иконка настройки
            Icon(
                painter = painterResource(id = setting.iconResId),
                contentDescription = setting.name,
                modifier = Modifier.size(26.dp),
                tint = if (isSelected) primaryColor else primaryColor.copy(alpha = 0.7f)
            )

            // Описание настройки
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = setting.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 22.sp
                )

                Text(
                    text = setting.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp
                )
            }
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
fun StrokeSpeedControlPanelWithArrows(
    onBackClick: () -> Unit,
    onUpClick: () -> Unit,
    onDownClick: () -> Unit,
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
fun PartialFixedControlPanel(
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
fun FreeCollectionControlPanel(
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
fun CoefficientCorrectionControlPanel(
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
fun ContrastReductionScreen(
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
            text = "Снижение контрастности",
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
                text = "Время снижения контраста дисплея",
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
                                    text = "Введите время...",
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
                text = "Введите значение в секундах",
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
                message = "Для изменения настроек требуется подключение к устройству",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SleepModeScreen(
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
            text = "Спящий режим",
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
                text = "Время перехода в спящий режим",
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
                                    text = "Введите время...",
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
                text = "Введите значение в минутах",
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
                message = "Для изменения настроек требуется подключение к устройству",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun StrokeSpeedScreen(
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    val speedOptions = remember {
        listOf(
            "Высокая",
            "Средняя",
            "Низкая"
        )
    }

    Column(
        modifier = modifier
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Заголовок
        Text(
            text = "Скорость штока",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Список опций
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            speedOptions.forEachIndexed { index, option ->
                StrokeSpeedOption(
                    text = option,
                    isSelected = index == selectedIndex,
                    onClick = { onSelectedIndexChange(index) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Подсказка
        if (connectionState != ConnectionState.CONNECTED) {
            ConnectionRequiredWarning(
                message = "Для изменения настроек требуется подключение к устройству",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun StrokeSpeedOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = Color(0xFF2196F3)
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
                .padding(vertical = 16.dp, horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 22.sp,
                color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface
            )

            // Индикатор выбора
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
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
fun CoefficientCorrectionScreen(
    d6Value1: String,
    d6Value2: String,
    realValue1: String,
    realValue2: String,
    activeFieldIndex: Int,
    onD6Value1Change: (String) -> Unit,
    onD6Value2Change: (String) -> Unit,
    onRealValue1Change: (String) -> Unit,
    onRealValue2Change: (String) -> Unit,
    connectionState: ConnectionState,
    isFieldFocused: (Int) -> Boolean,
    onFocusChange: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequesters = remember { List(4) { FocusRequester() } }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Управляем фокусом в зависимости от активного поля
    LaunchedEffect(activeFieldIndex) {
        if (activeFieldIndex in 0..3) {
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
            text = "Коррекция коэффициентов",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Описание
        Text(
            text = "Вычисление поправочного коэффициента",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Два столбика
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Столбик D6
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Д6",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Первое поле D6
                CoefficientField(
                    value = d6Value1,
                    onValueChange = onD6Value1Change,
                    isFocused = isFieldFocused(0),
                    onFocusChange = { onFocusChange(0, it) },
                    focusRequester = focusRequesters[0],
                    label = "Значение 1",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                // Второе поле D6
                CoefficientField(
                    value = d6Value2,
                    onValueChange = onD6Value2Change,
                    isFocused = isFieldFocused(1),
                    onFocusChange = { onFocusChange(1, it) },
                    focusRequester = focusRequesters[1],
                    label = "Значение 2",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Столбик Реал
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Реал",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Первое поле Реал
                CoefficientField(
                    value = realValue1,
                    onValueChange = onRealValue1Change,
                    isFocused = isFieldFocused(2),
                    onFocusChange = { onFocusChange(2, it) },
                    focusRequester = focusRequesters[2],
                    label = "Значение 1",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                // Второе поле Реал
                CoefficientField(
                    value = realValue2,
                    onValueChange = onRealValue2Change,
                    isFocused = isFieldFocused(3),
                    onFocusChange = { onFocusChange(3, it) },
                    focusRequester = focusRequesters[3],
                    label = "Значение 2",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Подсказка
        if (connectionState != ConnectionState.CONNECTED) {
            ConnectionRequiredWarning(
                message = "Для изменения настроек требуется подключение к устройству",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = "Активное поле: ${activeFieldIndex + 1}/4",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun MaxVolumeScreen(
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
            text = "Максимальный объем забора",
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
                text = "Максимальный объем забора",
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
                text = "Введите значение в мл",
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
                message = "Для изменения настроек требуется подключение к устройству",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun CoefficientField(
    value: String,
    onValueChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    label: String,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
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
                    .height(50.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        onFocusChange(focusState.isFocused)
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        keyboardController?.hide()
                        onFocusChange(false)
                    }
                ),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = "Введите...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
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
fun SystemSettingControlPanel(
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
            // Правая сторона - кнопка ПРИМЕНИТЬ
            SimpleControlButton(
                iconResId = R.drawable.check_circle,
                label = "Применить",
                onClick = onAcceptClick,
                backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                iconColor = Color(0xFF4CAF50)
            )
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

private fun getSystemSettingsList(): List<SystemSetting> {
    return listOf(
        SystemSetting(
            id = "contrast_reduction",
            name = "Снижение контрастности",
            description = "Настройка времени снижения контраста",
            iconResId = R.drawable.contrast
        ),
        SystemSetting(
            id = "sleep_mode",
            name = "Спящий режим",
            description = "Настройка времени перехода в спящий режим",
            iconResId = R.drawable.sleep
        ),
        SystemSetting(
            id = "stroke_speed",
            name = "Скорость штока",
            description = "Выбор скорости работы штока",
            iconResId = R.drawable.speed
        ),
        SystemSetting(
            id = "max_volume",
            name = "Максимальный объем забора",
            description = "Настройка максимального объема",
            iconResId = R.drawable.volume
        ),
        SystemSetting(
            id = "coefficient_correction",
            name = "Коррекция коэффициентов",
            description = "Вычисление поправочных коэффициентов",
            iconResId = R.drawable.calculate
        )
    )
}