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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.translator.R
import com.example.translator.domain.model.Language
import com.example.translator.ui.components.LanguageSelector
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import com.example.translator.ui.SpeechRecognitionEvent
import com.example.translator.ui.SpeechRecognitionStopEvent
import com.example.translator.ui.SpeechRecognitionRequestEvent
import android.util.Log

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.history))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Языки перевода
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
                IconButton(onClick = viewModel::swapLanguages) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = stringResource(R.string.swap_languages)
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

            // Поле ввода
            OutlinedTextField(
                value = state.inputText,
                onValueChange = viewModel::setInputText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text(stringResource(R.string.enter_text)) }
            )

            // Кнопка перевода
            val coroutineScope = rememberCoroutineScope()
            val context = LocalContext.current
            
            val buttonScale = remember { Animatable(1f) }
            
            Button(
                onClick = { 
                    if (state.inputText.isNotEmpty() && state.sourceLanguage != null && state.targetLanguage != null) {
                        // Анимация кнопки
                        coroutineScope.launch {
                            animateButton(buttonScale)
                        }
                        
                        // Вибрация
                        vibrateDevice(context)
                        
                        // Безопасный вызов функции перевода
                        try {
                            viewModel.translate()
                        } catch (e: Exception) {
                            android.util.Log.e("TranslatorScreen", "Error translating text", e)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(buttonScale.value)
            ) {
                Text(stringResource(R.string.translate))
            }

            // Кнопки управления вводом
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val micButtonScale = remember { Animatable(1f) }
                
                // Функция для обработки нажатия на кнопку микрофона
                val onMicrophoneClick = {
                    if (isListening.value) {
                        // Если микрофон уже активен, останавливаем распознавание
                        SpeechRecognitionStopEvent.requestStopSpeechRecognition()
                    } else {
                        // Иначе запускаем распознавание
                        // Вибрируем для обратной связи
                        try {
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                            if (vibrator != null && vibrator.hasVibrator()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(50)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("TranslatorScreen", "Error during vibration", e)
                        }
                        
                        // Запрашиваем распознавание речи с кодом языка
                        val languageCode = state.sourceLanguage?.code ?: "en"
                        SpeechRecognitionRequestEvent.requestSpeechRecognition(languageCode)
                    }
                }
                
                IconButton(
                    onClick = onMicrophoneClick,
                    modifier = Modifier
                        .scale(micButtonScale.value)
                ) {
                    if (isListening.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Микрофон"
                        )
                    }
                }
                IconButton(onClick = viewModel::clearInput) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear)
                    )
                }
                IconButton(onClick = viewModel::pasteFromClipboard) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = stringResource(R.string.paste)
                    )
                }
            }

            // Результат перевода
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        // Используем AndroidView для отображения форматированного текста
                        if (state.translatedText.isNotEmpty()) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { context ->
                                    android.widget.TextView(context).apply {
                                        textSize = 16f  // Базовый размер текста
                                        setLineSpacing(8f, 1f)  // Увеличиваем интервал между строками
                                        setPadding(8, 8, 8, 8)  // Добавляем отступы
                                    }
                                },
                                update = { textView ->
                                    // Получаем форматированный текст и устанавливаем его
                                    val formattedText = viewModel.getFormattedTranslationResult()
                                    textView.text = formattedText
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
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
            }

            // Кнопки управления переводом
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val speakButtonScale = remember { Animatable(1f) }
                
                IconButton(onClick = {
                    // Анимация кнопки озвучивания
                    coroutineScope.launch {
                        animateButton(speakButtonScale)
                    }
                    
                    // Вибрация
                    vibrateDevice(context)
                    
                    viewModel.speakTranslation()
                },
                modifier = Modifier.scale(speakButtonScale.value)) {
                    if (state.isSpeaking) {
                        // Показываем индикатор прогресса, когда идет озвучивание
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.speak)
                        )
                    }
                }
                IconButton(onClick = viewModel::copyTranslation) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy)
                    )
                }
                IconButton(onClick = viewModel::shareTranslation) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = stringResource(R.string.share)
                    )
                }
            }
        }
    }
} 