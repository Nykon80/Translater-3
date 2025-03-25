package com.example.translator.ui.screens.translator

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.translator.R
import com.example.translator.domain.model.Language
import com.example.translator.ui.components.LanguageSelector
import com.example.translator.ui.SpeechRecognitionEvent
import com.example.translator.ui.SpeechRecognitionStopEvent
import com.example.translator.ui.SpeechRecognitionRequestEvent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import android.view.Menu
import android.view.MenuItem
import android.view.ActionMode

/**
 * Вызывает тактильную вибрацию устройства с безопасной обработкой ошибок
 */
private fun vibrateDevice(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        
        // Проверяем, доступна ли вибрация
        if (vibrator == null) {
            android.util.Log.w("TranslatorScreen", "Vibrator service not available")
            return
        }
        
        // Проверяем, имеет ли устройство возможность вибрации
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                android.util.Log.w("TranslatorScreen", "Device does not have vibrator capability")
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    } catch (e: Exception) {
        // Безопасно обрабатываем любые исключения
        android.util.Log.e("TranslatorScreen", "Error while trying to vibrate device", e)
    }
}

/**
 * Анимирует кнопку путем масштабирования
 */
private suspend fun animateButton(buttonScale: Animatable<Float, AnimationVector1D>) {
    buttonScale.animateTo(
        targetValue = 0.9f,
        animationSpec = tween(
            durationMillis = 100,
            easing = LinearEasing
        )
    )
    delay(100)
    buttonScale.animateTo(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 100,
            easing = LinearEasing
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorScreen(
    onNavigateToHistory: () -> Unit,
    viewModel: TranslatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    // Отслеживаем состояние распознавания речи
    val isListening = remember { mutableStateOf(false) }
    
    // Отслеживаем фокус ввода для обработки клавиатуры
    val isInputFocused = remember { mutableStateOf(false) }
    
    // Состояние прокрутки для основного контента
    val scrollState = rememberScrollState()
    
    // Отслеживаем состояние клавиатуры
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    
    // Эффект для отслеживания состояния распознавания речи
    DisposableEffect(Unit) {
        // Создаем слушатель изменения состояния распознавания речи
        val listeningStateListener: (Boolean) -> Unit = { listening ->
            isListening.value = listening
        }
        
        // Добавляем слушатель
        SpeechRecognitionEvent.addListeningStateListener(listeningStateListener)
        
        // Удаляем слушатель при уничтожении компонента
        onDispose {
            SpeechRecognitionEvent.removeListeningStateListener(listeningStateListener)
        }
    }
    
    // Корутина для анимации и вибрации
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Используем BoxWithConstraints для определения доступного пространства
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            // Отключаем автоматическую обработку инсетов
            contentWindowInsets = WindowInsets(0)
        ) { paddingValues ->
            // Основной контейнер с прокруткой и обработкой инсетов клавиатуры
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp) // Уменьшенный вертикальный отступ
                    .imePadding(), // Важно! Добавляем отступ под клавиатуру
                verticalArrangement = Arrangement.spacedBy(12.dp) // Уменьшенные промежутки
            ) {
                // Языки перевода - немного выше по сравнению с предыдущей версией
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.sourceLanguage?.let { sourceLang ->
                        LanguageSelector(
                            selectedLanguage = sourceLang,
                            languages = state.sourceLanguages,
                            onLanguageSelected = viewModel::setSourceLanguage,
                            onFavoriteToggled = viewModel::toggleSourceLanguageFavorite,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    IconButton(
                        onClick = viewModel::swapLanguages,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(4.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = stringResource(R.string.swap_languages),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    state.targetLanguage?.let { targetLang ->
                        LanguageSelector(
                            selectedLanguage = targetLang,
                            languages = state.targetLanguages,
                            onLanguageSelected = viewModel::setTargetLanguage,
                            onFavoriteToggled = viewModel::toggleTargetLanguageFavorite,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Поле ввода с кнопками внутри - "парящее" с тенями
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.large, // Более выраженное скругление
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        tonalElevation = 8.dp, // Увеличенное значение elevation
                        shadowElevation = 4.dp // Добавляем тень
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Контент поля ввода с прокруткой
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(16.dp)
                            ) {
                                OutlinedTextField(
                                    value = state.inputText,
                                    onValueChange = viewModel::setInputText,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .onFocusChanged { focusState ->
                                            isInputFocused.value = focusState.isFocused
                                        },
                                    placeholder = {
                                        Text(
                                            stringResource(R.string.enter_text),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    },
                                    textStyle = MaterialTheme.typography.bodyLarge,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent
                                    )
                                )
                            }
                            
                            // Кнопки управления вводом вертикально справа
                            Column(
                                modifier = Modifier.padding(end = 8.dp, top = 8.dp),
                                verticalArrangement = Arrangement.Top,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val micButtonScale = remember { Animatable(1f) }
                                
                                IconButton(onClick = {
                                    // Анимация кнопки микрофона
                                    coroutineScope.launch {
                                        animateButton(micButtonScale)
                                    }
                                    
                                    // Вибрация
                                    vibrateDevice(context)
                                    
                                    // Запускаем или останавливаем распознавание речи
                                    if (isListening.value) {
                                        // Если микрофон уже активен, останавливаем распознавание
                                        SpeechRecognitionStopEvent.requestStopSpeechRecognition()
                                    } else {
                                        // Иначе запускаем распознавание
                                        // Запрашиваем распознавание речи с кодом языка
                                        val languageCode = state.sourceLanguage?.code ?: "en"
                                        SpeechRecognitionRequestEvent.requestSpeechRecognition(languageCode)
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .scale(micButtonScale.value),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (isListening.value) 
                                        MaterialTheme.colorScheme.primaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isListening.value) 
                                        MaterialTheme.colorScheme.onPrimaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )) {
                                    if (isListening.value) {
                                        Icon(
                                            Icons.Filled.Stop,
                                            contentDescription = stringResource(R.string.listening),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Mic,
                                            contentDescription = stringResource(R.string.speak),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                
                                IconButton(
                                    onClick = viewModel::clearInput,
                                    modifier = Modifier.size(48.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = stringResource(R.string.clear),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                IconButton(
                                    onClick = viewModel::pasteFromClipboard,
                                    modifier = Modifier.size(48.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        Icons.Filled.ContentPaste,
                                        contentDescription = stringResource(R.string.paste),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Кнопка перевода - более современная и заметная
                Button(
                    onClick = {
                        // Анимация кнопки и вибрация
                        coroutineScope.launch {
                            val buttonScale = Animatable(1f)
                            animateButton(buttonScale)
                        }
                        
                        vibrateDevice(context)
                        
                        // Безопасный вызов функции перевода
                        try {
                            viewModel.translate()
                        } catch (e: Exception) {
                            android.util.Log.e("TranslatorScreen", "Error translating text", e)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp), // Увеличенная высота кнопки
                    shape = MaterialTheme.shapes.medium, // Более выраженное скругление
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Text(
                        stringResource(R.string.translate),
                        style = MaterialTheme.typography.titleMedium // Увеличенный шрифт кнопки
                    )
                }

                // Результат перевода с кнопками внутри - "парящий" с тенями
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.large, // Более выраженное скругление
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        tonalElevation = 8.dp, // Увеличенное значение elevation
                        shadowElevation = 4.dp // Добавляем тень
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Контент результата перевода с прокруткой
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(16.dp)
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 4.dp
                                    )
                                } else {
                                    // Используем AndroidView для TextView с возможностью выделения текста
                                    if (state.translatedText.isNotEmpty()) {
                                        androidx.compose.ui.viewinterop.AndroidView(
                                            factory = { context ->
                                                // Создаём базовый ScrollView с надежной конфигурацией
                                                val scrollView = android.widget.ScrollView(context).apply {
                                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                                    )
                                                    
                                                    // Основные настройки для корректной прокрутки
                                                    this.isFillViewport = true
                                                    this.isVerticalScrollBarEnabled = true
                                                    this.isSmoothScrollingEnabled = true
                                                    
                                                    // Упрощаем настройки и фокусируемся на основном функционале
                                                    this.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
                                                    this.isClickable = true
                                                    this.isFocusable = true
                                                }
                                                
                                                // Создаем LinearLayout как контейнер для TextView
                                                val linearLayout = android.widget.LinearLayout(context).apply {
                                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                                                    )
                                                    orientation = android.widget.LinearLayout.VERTICAL
                                                    setPadding(16, 16, 16, 16)
                                                }
                                                
                                                // Создаем TextView для отображения текста с увеличенным размером шрифта
                                                val textView = android.widget.TextView(context).apply {
                                                    layoutParams = android.widget.LinearLayout.LayoutParams(
                                                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                                    )
                                                    
                                                    textSize = 18f // Увеличенный размер шрифта
                                                    setLineSpacing(10f, 1f) // Увеличенный интервал между строками
                                                    setTextIsSelectable(true)
                                                    
                                                    // Меньше кастомных настроек, больше стандартного поведения
                                                    isLongClickable = true
                                                }
                                                
                                                // Собираем компоненты вместе
                                                linearLayout.addView(textView)
                                                scrollView.addView(linearLayout)
                                                
                                                // Сохраняем ссылку на TextView в теге для доступа в update
                                                scrollView.tag = textView
                                                
                                                // Возвращаем корневой вид
                                                scrollView
                                            },
                                            update = { scrollView ->
                                                // Получаем TextView из тега
                                                val textView = scrollView.tag as android.widget.TextView
                                                
                                                // Обновляем текст
                                                textView.text = viewModel.getFormattedTranslationResult()
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight()
                                        )
                                    }
                                    
                                    // Показываем ошибку, если она есть
                                    state.error?.let { error ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = error,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            
                            // Кнопки управления переводом вертикально справа в отдельном контейнере
                            Column(
                                modifier = Modifier
                                    .padding(end = 8.dp, top = 8.dp)
                                    .width(48.dp),  // Фиксированная ширина для кнопок
                                verticalArrangement = Arrangement.Top,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val speakButtonScale = remember { Animatable(1f) }
                                
                                IconButton(
                                    onClick = {
                                        // Анимация кнопки озвучивания
                                        coroutineScope.launch {
                                            animateButton(speakButtonScale)
                                        }
                                        
                                        // Вибрация
                                        vibrateDevice(context)
                                        
                                        viewModel.speakTranslation()
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .scale(speakButtonScale.value),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (state.isSpeaking) 
                                            MaterialTheme.colorScheme.primaryContainer 
                                        else 
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (state.isSpeaking) 
                                            MaterialTheme.colorScheme.onPrimaryContainer 
                                        else 
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    if (state.isSpeaking) {
                                        // Показываем индикатор прогресса, когда идет озвучивание
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = stringResource(R.string.speak),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                
                                IconButton(
                                    onClick = viewModel::copyTranslation,
                                    modifier = Modifier.size(48.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = stringResource(R.string.copy),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                IconButton(
                                    onClick = viewModel::shareTranslation,
                                    modifier = Modifier.size(48.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        Icons.Filled.Share,
                                        contentDescription = stringResource(R.string.share),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                // Кнопка истории перемещена под кнопку "поделиться"
                                IconButton(
                                    onClick = onNavigateToHistory,
                                    modifier = Modifier.size(48.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        Icons.Filled.History, 
                                        contentDescription = stringResource(R.string.history),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
} 