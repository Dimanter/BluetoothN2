package com.example.bluetoothn2.screen

import android.R.attr.onClick
import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluetoothn2.R
import com.example.bluetoothn2.model.ConnectionState
import com.example.bluetoothn2.ui.theme.PrimaryColor
import com.example.bluetoothn2.ui.theme.TextColor
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

    // --- Состояния фокуса для экранов дозирования ---
    var directDosingFocus by remember { mutableStateOf(-1) } // -1 = нет фокуса, 0 = поле в фокусе
    var partialDosingFocus by remember { mutableStateOf(-1) } // -1, 0 (объем), 1 (части)
    var partialFixedFocus by remember { mutableStateOf(-1) }  // -1, 0 (объем), 1 (части)
    var freeCollectionFocus by remember { mutableStateOf(-1) } // -1, 0..4

    var notEmpty  by remember { mutableStateOf(false) }
    var showPowerOffDialog by remember { mutableStateOf(false) }

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
            delay(350)
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

    LaunchedEffect(uiState.connectionState == ConnectionState.CONNECTED) {
        withNavigationDebounce {
            viewModel.sendCommand("+CONNECTED\r\n")
        }
    }

    LaunchedEffect(uiState.connectionState) {
        withNavigationDebounce {
            viewModel.sendCommand("+CONNECTED\r\n")
        }
    }

    // Обработка системной кнопки "Назад"
    fun handleBack() {
        when (currentScreen) {
            DeviceScreen.MAIN -> {
                // На главном экране - отключаем устройство и выходим
                coroutineScope.launch {
                    bluetoothViewModel?.disconnectFromDevice(deviceAddress)
                    viewModel.cleanup()
                    viewModel.sendCommand("+DISCONNECT\r\n")
                    onBack()
                }
            }
            DeviceScreen.FUNCTIONS,
            DeviceScreen.SYSTEM_SETTINGS -> {
                // Возврат на главный экран
                currentScreen = DeviceScreen.MAIN
                closeKeyboard()
            }
            else -> {
                // Для всех остальных экранов (ввод параметров) возвращаемся на экран функций или настроек
                when {
                    currentScreen == DeviceScreen.DIRECT_DOSING ||
                            currentScreen == DeviceScreen.PARTIAL_DOSING ||
                            currentScreen == DeviceScreen.PARTIAL_FIXED_COLLECTION ||
                            currentScreen == DeviceScreen.FREE_COLLECTION -> {
                        currentScreen = DeviceScreen.FUNCTIONS
                    }
                    else -> {
                        currentScreen = DeviceScreen.SYSTEM_SETTINGS
                    }
                }
                closeKeyboard()
            }
        }
    }

    // Системная кнопка "Назад"
    BackHandler(enabled = true) {
        handleBack()
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
                colors = TopAppBarDefaults.topAppBarColors(
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                        .verticalScroll(rememberScrollState())
                )

                DeviceScreen.FUNCTIONS -> FunctionsScreen(
                    selectedIndex = functionsSelectedIndex,
                    connectionState = uiState.connectionState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                        .verticalScroll(rememberScrollState())
                )

                DeviceScreen.DIRECT_DOSING -> DirectDosingScreen(
                    value = directDosingValue,
                    onValueChange = { directDosingValue = it },
                    connectionState = uiState.connectionState,
                    isFocused = directDosingFocus == 0,
                    onFocusChange = { focused ->
                        directDosingFocus = if (focused) 0 else -1
                        if (focused) {
                            hasTextFieldFocus = true
                            currentFocusFieldId = "direct_dosing"
                        } else {
                            hasTextFieldFocus = false
                            currentFocusFieldId = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                        .verticalScroll(rememberScrollState())
                )

                DeviceScreen.PARTIAL_DOSING -> PartialDosingScreen(
                    volume = partialDosingVolume,
                    parts = partialDosingParts,
                    onVolumeChange = { partialDosingVolume = it },
                    onPartsChange = { partialDosingParts = it },
                    connectionState = uiState.connectionState,
                    isVolumeFocused = partialDosingFocus == 0,
                    isPartsFocused = partialDosingFocus == 1,
                    onFocusChange = { field, focused ->
                        when (field) {
                            "partial_volume" -> {
                                partialDosingFocus = if (focused) 0 else -1
                                if (focused) {
                                    hasTextFieldFocus = true
                                    currentFocusFieldId = "partial_volume"
                                } else {
                                    hasTextFieldFocus = false
                                    currentFocusFieldId = null
                                }
                            }
                            "partial_parts" -> {
                                partialDosingFocus = if (focused) 1 else -1
                                if (focused) {
                                    hasTextFieldFocus = true
                                    currentFocusFieldId = "partial_parts"
                                } else {
                                    hasTextFieldFocus = false
                                    currentFocusFieldId = null
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                        .verticalScroll(rememberScrollState())
                )

                DeviceScreen.PARTIAL_FIXED_COLLECTION -> PartialFixedCollectionScreen(
                    volume = partialFixedVolume,
                    parts = partialFixedParts,
                    onVolumeChange = { partialFixedVolume = it },
                    onPartsChange = { partialFixedParts = it },
                    connectionState = uiState.connectionState,
                    isVolumeFocused = partialFixedFocus == 0,
                    isPartsFocused = partialFixedFocus == 1,
                    onFocusChange = { field, focused ->
                        when (field) {
                            "fixed_volume" -> {
                                partialFixedFocus = if (focused) 0 else -1
                                if (focused) {
                                    hasTextFieldFocus = true
                                    currentFocusFieldId = "fixed_volume"
                                } else {
                                    hasTextFieldFocus = false
                                    currentFocusFieldId = null
                                }
                            }
                            "fixed_parts" -> {
                                partialFixedFocus = if (focused) 1 else -1
                                if (focused) {
                                    hasTextFieldFocus = true
                                    currentFocusFieldId = "fixed_parts"
                                } else {
                                    hasTextFieldFocus = false
                                    currentFocusFieldId = null
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                        .verticalScroll(rememberScrollState())
                )

                DeviceScreen.FREE_COLLECTION -> FreeCollectionScreen(
                    values = freeCollectionValues,
                    onValueChange = { index, value ->
                        val newValues = freeCollectionValues.toMutableList()
                        newValues[index] = value
                        freeCollectionValues = newValues
                    },
                    connectionState = uiState.connectionState,
                    activeFieldIndex = freeCollectionFocus,
                    onFocusChange = { index, focused ->
                        freeCollectionFocus = if (focused) index else -1
                        if (focused) {
                            hasTextFieldFocus = true
                            currentFocusFieldId = "free_$index"
                        } else {
                            hasTextFieldFocus = false
                            currentFocusFieldId = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                        .verticalScroll(rememberScrollState())
                )

                DeviceScreen.SYSTEM_SETTINGS -> SystemSettingsScreen(
                    selectedIndex = systemSettingsSelectedIndex,
                    connectionState = uiState.connectionState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                        .verticalScroll(rememberScrollState())
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                        .verticalScroll(rememberScrollState())
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                        .verticalScroll(rememberScrollState())
                )

                DeviceScreen.STROKE_SPEED -> StrokeSpeedScreen(
                    selectedIndex = strokeSpeedIndex,
                    onSelectedIndexChange = { strokeSpeedIndex = it },
                    connectionState = uiState.connectionState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                        .verticalScroll(rememberScrollState())
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                        .verticalScroll(rememberScrollState())
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                        .verticalScroll(rememberScrollState())
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
                                sendNavigationCommand("UP")
                                val maxIndex = when (currentScreen) {
                                    DeviceScreen.MAIN -> 3
                                    DeviceScreen.FUNCTIONS -> 3
                                    DeviceScreen.SYSTEM_SETTINGS -> 4
                                    else -> 0
                                }
                                when (currentScreen) {
                                    DeviceScreen.MAIN -> {
                                        if (mainSelectedIndex > 0) mainSelectedIndex--
                                        else mainSelectedIndex = maxIndex
                                    }
                                    DeviceScreen.FUNCTIONS -> {
                                        if (functionsSelectedIndex > 0) functionsSelectedIndex--
                                        else functionsSelectedIndex = maxIndex
                                    }
                                    DeviceScreen.SYSTEM_SETTINGS -> {
                                        if (systemSettingsSelectedIndex > 0) systemSettingsSelectedIndex--
                                        else systemSettingsSelectedIndex = maxIndex
                                    }
                                    else -> {}
                                }
                            }
                        },
                        onDownClick = {
                            withNavigationDebounce {
                                sendNavigationCommand("DOWN")
                                val maxIndex = when (currentScreen) {
                                    DeviceScreen.MAIN -> 3
                                    DeviceScreen.FUNCTIONS -> 3
                                    DeviceScreen.SYSTEM_SETTINGS -> 4
                                    else -> 0
                                }
                                when (currentScreen) {
                                    DeviceScreen.MAIN -> {
                                        if (mainSelectedIndex < maxIndex) mainSelectedIndex++
                                        else mainSelectedIndex = 0
                                    }
                                    DeviceScreen.FUNCTIONS -> {
                                        if (functionsSelectedIndex < maxIndex) functionsSelectedIndex++
                                        else functionsSelectedIndex = 0
                                    }
                                    DeviceScreen.SYSTEM_SETTINGS -> {
                                        if (systemSettingsSelectedIndex < maxIndex) systemSettingsSelectedIndex++
                                        else systemSettingsSelectedIndex = 0
                                    }
                                    else -> {}
                                }
                            }
                        },
                        onBackClick = {
                            if(currentScreen != DeviceScreen.MAIN) {
                                sendNavigationCommand("BACK")
                            }
                            withNavigationDebounce {
                                handleBack()
                            }
                        },
                        onAcceptClick = {
                            withNavigationDebounce {
                                sendNavigationCommand("ENTER")
                                when (currentScreen) {
                                    DeviceScreen.MAIN -> {
                                        when (mainSelectedIndex) {
                                            0 -> currentScreen = DeviceScreen.FUNCTIONS
                                            1 -> currentScreen = DeviceScreen.SYSTEM_SETTINGS
                                            2 -> {
                                                viewModel.sendCommand("BLUETOOTH_MENU\r\n")
                                            }
                                            3 -> {
                                                showPowerOffDialog = true
                                            }
                                        }
                                        closeKeyboard()
                                    }
                                    DeviceScreen.FUNCTIONS -> {
                                        val function = getFunctionsList().getOrNull(functionsSelectedIndex)
                                        function?.let {
                                            savedFunctionsIndex = functionsSelectedIndex
                                            when (it.id) {
                                                "direct_dosing" -> {
                                                    currentScreen = DeviceScreen.DIRECT_DOSING
                                                    directDosingFocus = -1 // фокус не установлен
                                                }
                                                "partial_dosing" -> {
                                                    currentScreen = DeviceScreen.PARTIAL_DOSING
                                                    partialDosingFocus = -1
                                                }
                                                "partial_fixed_collection" -> {
                                                    currentScreen = DeviceScreen.PARTIAL_FIXED_COLLECTION
                                                    partialFixedFocus = -1
                                                }
                                                "free_collection" -> {
                                                    currentScreen = DeviceScreen.FREE_COLLECTION
                                                    freeCollectionFocus = -1
                                                }
                                            }
                                            closeKeyboard()
                                        }
                                    }
                                    DeviceScreen.SYSTEM_SETTINGS -> {
                                        val setting = getSystemSettingsList().getOrNull(systemSettingsSelectedIndex)
                                        setting?.let {
                                            when (it.id) {
                                                "contrast_reduction" -> {
                                                    currentScreen = DeviceScreen.CONTRAST_REDUCTION
                                                    activeInputFieldIndex = 0
                                                    coroutineScope.launch { delay(100); openKeyboard("contrast") }
                                                }
                                                "sleep_mode" -> {
                                                    currentScreen = DeviceScreen.SLEEP_MODE
                                                    activeInputFieldIndex = 0
                                                    coroutineScope.launch { delay(100); openKeyboard("sleep") }
                                                }
                                                "stroke_speed" -> {
                                                    currentScreen = DeviceScreen.STROKE_SPEED
                                                    activeInputFieldIndex = 0
                                                    closeKeyboard()
                                                }
                                                "max_volume" -> {
                                                    currentScreen = DeviceScreen.MAX_VOLUME
                                                    activeInputFieldIndex = 0
                                                    coroutineScope.launch { delay(100); openKeyboard("max_volume") }
                                                }
                                                "coefficient_correction" -> {
                                                    currentScreen = DeviceScreen.COEFFICIENT_CORRECTION
                                                    activeInputFieldIndex = 0
                                                    coroutineScope.launch { delay(100); openKeyboard("coefficient_0") }
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
                            if(!notEmpty) {
                                val value = directDosingValue.toIntOrNull() ?: 200
                                // Если фокус на поле (directDosingFocus == 0) – отправляем BACK:значение и выходим
                                if (directDosingFocus == 0) {
                                    if (uiState.connectionState == ConnectionState.CONNECTED) {
                                        viewModel.sendCommand("BACK:${value}\r\n")
                                    }
                                    directDosingFocus = -1
                                } else {
                                    // Если фокус не установлен – просто выходим
                                    if (uiState.connectionState == ConnectionState.CONNECTED) {
                                        viewModel.sendCommand("BACK:${value}\r\n")
                                    }
                                    withNavigationDebounce {
                                        currentScreen = DeviceScreen.FUNCTIONS
                                        functionsSelectedIndex = savedFunctionsIndex
                                        closeKeyboard()
                                    }
                                }
                            }
                        },
                        onEnterClick = {
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                viewModel.sendCommand("ENTER:\r\n")
                            }
                            // "Выполнить" – переключаем фокус
                            if (directDosingFocus == -1) {
                                // Устанавливаем фокус
                                directDosingFocus = 0
                                coroutineScope.launch { delay(100); openKeyboard("direct_dosing") }
                            } else {
                                // Если уже в фокусе – ничего не делаем
                            }
                        },
                        onStartClick = {
                            // "Старт" – отправляем команду START_DIRECT и выходим
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                viewModel.sendCommand("START:\r\n")
                                notEmpty = !notEmpty
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
                            if(!notEmpty) {
                                val volume = partialDosingVolume.toIntOrNull() ?: 40
                                val parts = partialDosingParts.toIntOrNull() ?: 5
                                if (partialDosingFocus > -1) {
                                    if (uiState.connectionState == ConnectionState.CONNECTED) {
                                        viewModel.sendCommand("BACK:$volume:$parts\r\n")
                                    }
                                    focusManager.clearFocus(true)
                                    partialDosingFocus = -1
                                } else {
                                    if (uiState.connectionState == ConnectionState.CONNECTED) {
                                        viewModel.sendCommand("BACK:$volume:$parts\r\n")
                                    }
                                    withNavigationDebounce {
                                        currentScreen = DeviceScreen.FUNCTIONS
                                        functionsSelectedIndex = savedFunctionsIndex
                                        closeKeyboard()
                                    }
                                }
                            }
                        },
                        onEnterClick = {
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                viewModel.sendCommand("ENTER:\r\n")
                            }
                            // "Выполнить" – переход по полям
                            when (partialDosingFocus) {
                                -1 -> {
                                    // Нет фокуса → устанавливаем на объём
                                    partialDosingFocus = 0
                                    coroutineScope.launch { delay(100); openKeyboard("partial_volume") }
                                }
                                0 -> {
                                    // На объёме → переходим на части
                                    partialDosingFocus = 1
                                    coroutineScope.launch { delay(100); openKeyboard("partial_parts") }
                                }
                                1 -> {
                                    partialDosingFocus = -1
                                    partialDosingFocus = 0
                                    coroutineScope.launch { delay(100); openKeyboard("partial_volume") }
                                }
                            }
                        },
                        onStartClick = {
                            // "Старт" – отправляем команду START_PARIAL и выходим
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                viewModel.sendCommand("START:\r\n")
                                notEmpty = !notEmpty
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
                            if(!notEmpty) {
                                val volume = partialFixedVolume.toIntOrNull() ?: 0
                                val parts = partialFixedParts.toIntOrNull() ?: 1
                                if (partialFixedFocus > -1) {
                                    partialFixedFocus = -1
                                    focusManager.clearFocus(true)
                                    if (uiState.connectionState == ConnectionState.CONNECTED) {
                                        viewModel.sendCommand("BACK:$volume:$parts\r\n")
                                    }
                                } else {
                                    viewModel.sendCommand("BACK:$volume:$parts\r\n")
                                    withNavigationDebounce {
                                        currentScreen = DeviceScreen.FUNCTIONS
                                        functionsSelectedIndex = savedFunctionsIndex
                                        closeKeyboard()
                                    }
                                }
                            }
                        },
                        onEnterClick = {
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                viewModel.sendCommand("ENTER:\r\n")
                            }
                            // "Выполнить" – переход по полям
                            if (partialFixedFocus == -1) {
                                partialFixedFocus = 0
                                coroutineScope.launch { delay(100); openKeyboard("partial_volume") }
                            } else if (partialFixedFocus < 1) {
                                partialFixedFocus++
                                coroutineScope.launch { delay(100); openKeyboard("partial_parts") }
                            }
                            else if (partialFixedFocus == 1) {
                                partialFixedFocus = 0
                                coroutineScope.launch { delay(100); openKeyboard("partial_volume") }
                            }
                        },
                        onStartClick = {
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                viewModel.sendCommand("START:\r\n")
                                notEmpty = !notEmpty
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
                            if(!notEmpty) {
                                val valuesStr = freeCollectionValues.joinToString(":")
                                if (freeCollectionFocus > -1) {
                                    freeCollectionFocus = -1
                                    if (uiState.connectionState == ConnectionState.CONNECTED) {
                                        viewModel.sendCommand("BACK:$valuesStr\r\n")
                                    }

                                } else {
                                    viewModel.sendCommand("BACK:$valuesStr\r\n")
                                    withNavigationDebounce {
                                        currentScreen = DeviceScreen.FUNCTIONS
                                        functionsSelectedIndex = savedFunctionsIndex
                                        closeKeyboard()
                                    }
                                }
                            }
                        },
                        onEnterClick = {
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                viewModel.sendCommand("ENTER:\r\n")
                            }
                            // Переход по полям (0..4)
                            if (freeCollectionFocus == -1) {
                                freeCollectionFocus = 0
                                coroutineScope.launch { delay(100); openKeyboard("free_0") }
                            } else if (freeCollectionFocus < 4) {
                                freeCollectionFocus++
                                coroutineScope.launch { delay(100); openKeyboard("free_$freeCollectionFocus") }
                            }
                            else if (freeCollectionFocus == 4) {
                                freeCollectionFocus = 0
                                coroutineScope.launch { delay(100); openKeyboard("free_0") }
                            }
                        },
                        onStartClick = {
                            if (uiState.connectionState == ConnectionState.CONNECTED) {
                                viewModel.sendCommand("START:\r\n")
                                notEmpty = !notEmpty
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
                                if (command.isNotEmpty()) viewModel.sendCommand(command)
                            }
                            withNavigationDebounce {
                                closeKeyboard()
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
                                if (strokeSpeedIndex > 0) strokeSpeedIndex--
                            }
                        },
                        onDownClick = {
                            withNavigationDebounce {
                                sendNavigationCommand("DOWN")
                                if (strokeSpeedIndex < 2) strokeSpeedIndex++
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
                                viewModel.sendCommand("STROKE_SPEED:$speed\r\n")
                            }
                            withNavigationDebounce {
                                closeKeyboard()
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
                                    val command = "COEFFICIENT:$coefficientD6Value1:$coefficientD6Value2:$coefficientRealValue1:$coefficientRealValue2\r\n"
                                    viewModel.sendCommand(command)
                                }
                                closeKeyboard()
                                currentScreen = DeviceScreen.SYSTEM_SETTINGS
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
    // Диалог подтверждения выключения
    if (showPowerOffDialog) {
        AlertDialog(
            onDismissRequest = { showPowerOffDialog = false },
            title = { Text("Подтверждение") },
            text = { Text("Хотите выключить?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Отправляем команду Enter
                        viewModel.sendCommand("YES:\r\n")
                        showPowerOffDialog = false
                        // Выполняем отключение и выход
                        coroutineScope.launch {
                            delay(500)
                            bluetoothViewModel?.disconnectFromDevice(deviceAddress)
                            viewModel.cleanup()
                            onBack()
                        }
                    }
                ) {
                    Text("Да")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // Отправляем команду Back
                        viewModel.sendCommand("NO:\r\n")
                        showPowerOffDialog = false
                    }
                ) {
                    Text("Нет")
                }
            }
        )
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
    val iconResId: Int
)

// Модель системной настройки
data class SystemSetting(
    val id: String,
    val name: String,
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

        // Список из четырёх элементов (НЕ КЛИКАБЕЛЬНЫ)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Пункт 1: Функции
            MainMenuItem(
                title = "Функции",
                description = "",
                isSelected = selectedIndex == 0,
                iconResId = R.drawable.science,
                iconColor = Color(0xFF4CAF50),
                modifier = Modifier.fillMaxWidth()
            )

            // Пункт 2: Системные настройки
            MainMenuItem(
                title = "Настройки",
                description = "",
                isSelected = selectedIndex == 1,
                iconResId = R.drawable.settings,
                iconColor = Color(0xFF2196F3),
                modifier = Modifier.fillMaxWidth()
            )

            // Пункт 3: Блютуз
            MainMenuItem(
                title = "Блютуз",
                description = "",
                isSelected = selectedIndex == 2,
                iconResId = R.drawable.outline_bluetooth_24,
                iconColor = Color(0xFF2196F3),
                modifier = Modifier.fillMaxWidth()
            )

            // Пункт 4: Выключение
            MainMenuItem(
                title = "Выключение",
                description = "",
                isSelected = selectedIndex == 3,
                iconResId = R.drawable.close,
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
                    text = "",
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
    val focusManager = LocalFocusManager.current
    var canBeFocused by remember { mutableStateOf(false)}

    // Автоматически запрашиваем фокус при появлении
    LaunchedEffect(isFocused) {
        if (isFocused) {
            canBeFocused = true
            focusRequester.requestFocus()
            delay(150)
            keyboardController?.show()
        } else {
            canBeFocused = false
            keyboardController?.hide()
            focusManager.clearFocus(true)
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if(isFocused) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        width = if(isFocused) 2.dp else 1.dp,
                        color = if(isFocused) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
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
                        .focusProperties{canFocus = canBeFocused}
                        .onFocusChanged { focusState ->
                            onFocusChange(focusState.isFocused)
                            if(!focusState.isFocused) canBeFocused = false
                        },
                    readOnly = false,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
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
                        textAlign = TextAlign.Center
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
    val focusManager = LocalFocusManager.current

    var canVolumBeFocused by remember { mutableStateOf(false) }
    var canPartsBeFocused by remember { mutableStateOf(false) }

    // Управляем фокусом в зависимости от активного поля
    LaunchedEffect(isVolumeFocused) {
        if (isVolumeFocused) {
            canVolumBeFocused = true
            volumeFocusRequester.requestFocus()
            delay(80)
            keyboardController?.show()
        }
    }

    LaunchedEffect(isPartsFocused) {
        if (isPartsFocused) {
            canPartsBeFocused = true
            partsFocusRequester.requestFocus()
            delay(80)
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if(isVolumeFocused) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if(isVolumeFocused) 2.dp else 1.dp,
                            color = if(isVolumeFocused) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
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
                            .focusProperties{canFocus = canVolumBeFocused}
                            .onFocusChanged { focusState ->
                                onFocusChange("partial_volume", focusState.isFocused)
                                if(!isVolumeFocused) canVolumBeFocused = false
                            }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consumeAllChanges() }
                                    }
                                }
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if(isPartsFocused) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if(isPartsFocused) 2.dp else 1.dp,
                            color = if(isPartsFocused) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
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
                            .focusProperties{ canFocus = canPartsBeFocused}
                            .onFocusChanged { focusState ->
                                onFocusChange("partial_parts", focusState.isFocused)
                                if(!isPartsFocused) canPartsBeFocused = false
                            }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consumeAllChanges() }
                                    }
                                }
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
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
                    .padding(horizontal = 16.dp)
                    .clickable{ },
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
                        textAlign = TextAlign.Center
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
    val focusManager = LocalFocusManager.current

    var canVolumBeFocused by remember { mutableStateOf(false) }
    var canPartsBeFocused by remember { mutableStateOf(false) }

    // Управляем фокусом в зависимости от активного поля
    LaunchedEffect(isVolumeFocused) {
        if (isVolumeFocused) {
            canVolumBeFocused = true
            volumeFocusRequester.requestFocus()
            delay(80)
            keyboardController?.show()
        }
    }

    LaunchedEffect(isPartsFocused) {
        if (isPartsFocused) {
            canPartsBeFocused = true
            partsFocusRequester.requestFocus()
            delay(80)
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if(isVolumeFocused) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if(isVolumeFocused) 2.dp else 1.dp,
                            color = if(isVolumeFocused) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
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
                            .focusProperties{ canFocus = canVolumBeFocused}
                            .onFocusChanged { focusState ->
                                onFocusChange("fixed_volume", focusState.isFocused)
                                if(!isVolumeFocused) canVolumBeFocused = false
                            }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consumeAllChanges() }
                                    }
                                }
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if(isPartsFocused) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if(isPartsFocused) 2.dp else 1.dp,
                            color = if(isPartsFocused) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
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
                            .focusProperties{ canFocus = canPartsBeFocused}
                            .onFocusChanged { focusState ->
                                onFocusChange("fixed_parts", focusState.isFocused)
                                if(!isPartsFocused) canPartsBeFocused = false
                            }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consumeAllChanges() }
                                    }
                                }
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
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
                        textAlign = TextAlign.Center
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
    activeFieldIndex: Int,
    onFocusChange: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequesters = remember { List(5) { FocusRequester() } }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // ←←← Вот что ты просил: отдельное состояние для КАЖДОГО поля
    val canBeFocusedList = remember { List(5) { mutableStateOf(false) } }

    // Запускаем фокус только для нужного поля
    LaunchedEffect(activeFieldIndex) {
        if (activeFieldIndex in 0..4) {
            canBeFocusedList[activeFieldIndex].value = true
            focusRequesters[activeFieldIndex].requestFocus()
            delay(80)                    // можно подкрутить 60–120
            keyboardController?.show()
        } else {
            // Сбрасываем все
            canBeFocusedList.forEach { it.value = false }
            keyboardController?.hide()
            focusManager.clearFocus(true)
        }
    }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Свободный забор",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            values.forEachIndexed { index, value ->
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Объем ${index + 1} (мл)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (activeFieldIndex == index) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = if (activeFieldIndex == index) 2.dp else 1.dp,
                                color = if (activeFieldIndex == index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp)
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
                                .focusProperties {
                                    canFocus = canBeFocusedList[index].value
                                }
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            event.changes.forEach { it.consumeAllChanges() }
                                        }
                                    }
                                }
                                .onFocusChanged { focusState ->
                                    onFocusChange(index, focusState.isFocused)
                                    if (!focusState.isFocused) {
                                        canBeFocusedList[index].value = false
                                    }
                                },
                            readOnly = false,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done   // или Done для последнего поля
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
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
                        textAlign = TextAlign.Center
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
                    text = "",
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
                    label = "Ввод",
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
    onEnterClick: () -> Unit,
    onStartClick: () -> Unit,
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
            // Кнопка НАЗАД
            SimpleControlButton(
                iconResId = R.drawable.arrow_back,
                label = "Назад",
                onClick = onBackClick,
                backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                iconColor = MaterialTheme.colorScheme.error
            )

            // Кнопка ВЫПОЛНИТЬ (переход по полям)
            SimpleControlButton(
                iconResId = R.drawable.check_circle,
                label = "Ввод",
                onClick = onEnterClick,
                backgroundColor = Color(0xFF2196F3).copy(alpha = 0.1f),
                iconColor = Color(0xFF2196F3)
            )

            // Кнопка СТАРТ
            SimpleControlButton(
                iconResId = R.drawable.start,
                label = "Старт",
                onClick = onStartClick,
                backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                iconColor = Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
fun PartialDosingControlPanel(
    onBackClick: () -> Unit,
    onEnterClick: () -> Unit,
    onStartClick: () -> Unit,
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
            // Кнопка НАЗАД
            SimpleControlButton(
                iconResId = R.drawable.arrow_back,
                label = "Назад",
                onClick = onBackClick,
                backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                iconColor = MaterialTheme.colorScheme.error
            )

            // Кнопка ВЫПОЛНИТЬ (переход по полям)
            SimpleControlButton(
                iconResId = R.drawable.check_circle,
                label = "Ввод",
                onClick = onEnterClick,
                backgroundColor = Color(0xFF2196F3).copy(alpha = 0.1f),
                iconColor = Color(0xFF2196F3)
            )

            // Кнопка СТАРТ
            SimpleControlButton(
                iconResId = R.drawable.start,
                label = "Старт",
                onClick = onStartClick,
                backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                iconColor = Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
fun PartialFixedControlPanel(
    onBackClick: () -> Unit,
    onEnterClick: () -> Unit,
    onStartClick: () -> Unit,
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
            // Кнопка НАЗАД
            SimpleControlButton(
                iconResId = R.drawable.arrow_back,
                label = "Назад",
                onClick = onBackClick,
                backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                iconColor = MaterialTheme.colorScheme.error
            )

            // Кнопка ВЫПОЛНИТЬ (переход по полям)
            SimpleControlButton(
                iconResId = R.drawable.check_circle,
                label = "Ввод",
                onClick = onEnterClick,
                backgroundColor = Color(0xFF2196F3).copy(alpha = 0.1f),
                iconColor = Color(0xFF2196F3)
            )

            // Кнопка СТАРТ
            SimpleControlButton(
                iconResId = R.drawable.start,
                label = "Старт",
                onClick = onStartClick,
                backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                iconColor = Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
fun FreeCollectionControlPanel(
    onBackClick: () -> Unit,
    onEnterClick: () -> Unit,
    onStartClick: () -> Unit,
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
            // Кнопка НАЗАД
            SimpleControlButton(
                iconResId = R.drawable.arrow_back,
                label = "Назад",
                onClick = onBackClick,
                backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                iconColor = MaterialTheme.colorScheme.error
            )

            // Кнопка ВЫПОЛНИТЬ (переход по полям)
            SimpleControlButton(
                iconResId = R.drawable.check_circle,
                label = "Ввод",
                onClick = onEnterClick,
                backgroundColor = Color(0xFF2196F3).copy(alpha = 0.1f),
                iconColor = Color(0xFF2196F3)
            )

            // Кнопка СТАРТ
            SimpleControlButton(
                iconResId = R.drawable.start,
                label = "Старт",
                onClick = onStartClick,
                backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                iconColor = Color(0xFF4CAF50)
            )
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
                label = "Ввод",
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
                    label = "Ввод",
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
                    text = "Дозатор УУППО",
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
                label = "Ввод",
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
            iconResId = R.drawable.science
        ),
        DeviceFunction(
            id = "partial_dosing",
            name = "Частичное дозирование",
            iconResId = R.drawable.timeline
        ),
        DeviceFunction(
            id = "partial_fixed_collection",
            name = "Частичный фиксированный забор",
            iconResId = R.drawable.rotate
        ),
        DeviceFunction(
            id = "free_collection",
            name = "Свободный забор",
            iconResId = R.drawable.assignment
        )
    )
}

private fun getSystemSettingsList(): List<SystemSetting> {
    return listOf(
        SystemSetting(
            id = "contrast_reduction",
            name = "Снижение контрастности",
            iconResId = R.drawable.contrast
        ),
        SystemSetting(
            id = "sleep_mode",
            name = "Спящий режим",
            iconResId = R.drawable.sleep
        ),
        SystemSetting(
            id = "stroke_speed",
            name = "Скорость штока",
            iconResId = R.drawable.speed
        ),
        SystemSetting(
            id = "max_volume",
            name = "Максимальный объем забора",
            iconResId = R.drawable.volume
        ),
        SystemSetting(
            id = "coefficient_correction",
            name = "Коррекция коэффициентов",
            iconResId = R.drawable.calculate
        )
    )
}