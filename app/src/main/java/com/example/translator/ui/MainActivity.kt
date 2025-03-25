package com.example.translator.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.translator.ui.theme.TranslatorTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import android.view.WindowManager

// Синглтон для передачи событий распознавания речи
object SpeechRecognitionEvent {
    private val listeners = mutableListOf<(String?) -> Unit>()
    private val cancelListeners = mutableListOf<() -> Unit>()
    private val listeningStateListeners = mutableListOf<(Boolean) -> Unit>()
    
    fun addListener(listener: (String?) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (String?) -> Unit) {
        listeners.remove(listener)
    }
    
    fun addCancelListener(listener: () -> Unit) {
        cancelListeners.add(listener)
    }
    
    fun removeCancelListener(listener: () -> Unit) {
        cancelListeners.remove(listener)
    }
    
    fun addListeningStateListener(listener: (Boolean) -> Unit) {
        listeningStateListeners.add(listener)
        // Сразу вызываем с текущим статусом
        listener(isListening)
    }
    
    fun removeListeningStateListener(listener: (Boolean) -> Unit) {
        listeningStateListeners.remove(listener)
    }
    
    fun onSpeechRecognized(text: String?) {
        listeners.forEach { it(text) }
    }
    
    fun onSpeechRecognitionCancelled() {
        cancelListeners.forEach { it() }
    }
    
    // Флаг для отслеживания запущенного распознавания
    var isListening = false
        private set
    
    // Счетчик попыток перезапуска распознавания
    private var retryCount = 0
    private const val MAX_RETRY_COUNT = 2
    
    fun resetRetryCount() {
        retryCount = 0
    }
    
    fun incrementRetryCount(): Boolean {
        retryCount++
        return retryCount <= MAX_RETRY_COUNT
    }
    
    // Получение текущего количества попыток (для логирования)
    fun getRetryCount(): Int {
        return retryCount
    }
    
    fun setListening(listening: Boolean) {
        if (!listening) {
            // Сбрасываем счетчик при остановке распознавания
            resetRetryCount()
        }
        isListening = listening
        // Уведомляем слушателей об изменении состояния
        listeningStateListeners.forEach { it(isListening) }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var keyboardLanguageListener: KeyboardLanguageListener? = null
    private var currentKeyboardLanguage: String? = null
    private lateinit var speechRecognitionLauncher: ActivityResultLauncher<Intent>
    private var currentSpeechRecognizer: SpeechRecognizer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Устанавливаем режим отображения клавиатуры программно
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        
        // Инициализация слушателя языка клавиатуры
        keyboardLanguageListener = KeyboardLanguageListener(this) { languageCode ->
            // Если язык клавиатуры изменился, обновляем переменную
            if (currentKeyboardLanguage != languageCode) {
                currentKeyboardLanguage = languageCode
                // Отправляем событие об изменении языка клавиатуры
                KeyboardLanguageEvent.onLanguageChanged(languageCode)
            }
        }
        
        // Регистрация ActivityResultLauncher для распознавания речи
        speechRecognitionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            SpeechRecognitionEvent.setListening(false)
            
            if (result.resultCode == RESULT_OK && result.data != null) {
                val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!results.isNullOrEmpty()) {
                    val recognizedText = results[0]
                    android.util.Log.d("MainActivity", "Speech recognized: $recognizedText")
                    SpeechRecognitionEvent.onSpeechRecognized(recognizedText)
                } else {
                    android.util.Log.d("MainActivity", "Speech recognition returned no results")
                    SpeechRecognitionEvent.onSpeechRecognized(null)
                }
            } else {
                android.util.Log.d("MainActivity", "Speech recognition cancelled or failed")
                SpeechRecognitionEvent.onSpeechRecognitionCancelled()
            }
        }
        
        // Подписываемся на события запроса распознавания речи
        SpeechRecognitionRequestEvent.addListener { language ->
            startSpeechRecognition(language)
        }
        
        // Подписываемся на события остановки распознавания речи
        SpeechRecognitionStopEvent.addListener {
            stopSpeechRecognition()
        }
        
        setContent {
            TranslatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TranslatorNavigation()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        keyboardLanguageListener?.register()
    }
    
    override fun onPause() {
        keyboardLanguageListener?.unregister()
        super.onPause()
    }
    
    override fun onDestroy() {
        // Отписываемся от событий запроса распознавания речи
        SpeechRecognitionRequestEvent.removeListener(::startSpeechRecognition)
        // Отписываемся от событий остановки распознавания речи
        SpeechRecognitionStopEvent.removeListener(::stopSpeechRecognition)
        // Освобождаем ресурсы распознавателя
        currentSpeechRecognizer?.destroy()
        super.onDestroy()
    }
    
    /**
     * Получение полного кода языка для распознавания речи
     */
    private fun getFullLanguageCode(languageCode: String): String {
        return when (languageCode) {
            "en" -> "en-US"
            "ru" -> "ru-RU"
            "uk" -> "uk-UA"
            "fr" -> "fr-FR"
            "de" -> "de-DE"
            "es" -> "es-ES"
            "it" -> "it-IT"
            "pl" -> "pl-PL"
            "zh" -> "zh-CN"
            "ja" -> "ja-JP"
            "ko" -> "ko-KR"
            else -> "$languageCode-$languageCode".uppercase() // Fallback
        }
    }

    /**
     * Остановка распознавания речи
     */
    private fun stopSpeechRecognition() {
        if (!SpeechRecognitionEvent.isListening) {
            android.util.Log.d("MainActivity", "Speech recognition is not active")
            return
        }
        
        android.util.Log.d("MainActivity", "Stopping speech recognition")
        try {
            currentSpeechRecognizer?.stopListening()
            currentSpeechRecognizer?.destroy()
            currentSpeechRecognizer = null
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error stopping speech recognition", e)
        } finally {
            SpeechRecognitionEvent.setListening(false)
            SpeechRecognitionEvent.onSpeechRecognitionCancelled()
        }
    }

    /**
     * Запуск распознавания речи с помощью SpeechRecognizer
     */
    private fun startSpeechRecognition(languageCode: String) {
        // Если распознавание уже запущено, останавливаем его
        if (SpeechRecognitionEvent.isListening) {
            android.util.Log.d("MainActivity", "Speech recognition is already active, stopping it")
            stopSpeechRecognition()
            return
        }
        
        // Получаем полный код языка для распознавания
        val fullLanguageCode = getFullLanguageCode(languageCode)
        android.util.Log.d("MainActivity", "Language for recognition: $languageCode -> $fullLanguageCode")
        
        // Проверяем доступность сервиса распознавания речи
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            android.util.Log.e("MainActivity", "Speech recognition is not available")
            // Пробуем использовать Intent как запасной вариант
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, fullLanguageCode)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "")
                }
                speechRecognitionLauncher.launch(intent)
                return
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to launch speech recognition intent", e)
                SpeechRecognitionEvent.onSpeechRecognitionCancelled()
                return
            }
        }
        
        android.util.Log.d("MainActivity", "Starting speech recognition for language: $fullLanguageCode")
        SpeechRecognitionEvent.setListening(true)
        
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            
            // Проверяем и настраиваем аудио
            try {
                // Проверяем режим аудио
                if (audioManager.mode != android.media.AudioManager.MODE_NORMAL) {
                    audioManager.mode = android.media.AudioManager.MODE_NORMAL
                }
                
                // Включаем микрофон если выключен
                if (audioManager.isMicrophoneMute) {
                    audioManager.isMicrophoneMute = false
                }
                
                // Устанавливаем громкость микрофона на максимум
                audioManager.setStreamVolume(
                    android.media.AudioManager.STREAM_VOICE_CALL,
                    audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL),
                    0
                )
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error configuring audio", e)
            }
            
            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            currentSpeechRecognizer = speechRecognizer
            
            // Настраиваем слушатель распознавания речи
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                private val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
                private val timeoutRunnable = Runnable {
                    android.util.Log.d("MainActivity", "Recognition timed out")
                    speechRecognizer.stopListening()
                    speechRecognizer.destroy()
                    SpeechRecognitionEvent.setListening(false)
                    SpeechRecognitionEvent.onSpeechRecognitionCancelled()
                }
                
                override fun onReadyForSpeech(params: Bundle?) {
                    android.util.Log.d("MainActivity", "Ready for speech")
                    timeoutHandler.postDelayed(timeoutRunnable, 15000) // Увеличиваем таймаут до 15 секунд
                }
                
                override fun onBeginningOfSpeech() {
                    android.util.Log.d("MainActivity", "Beginning of speech")
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    // Устанавливаем новый таймаут после начала речи
                    timeoutHandler.postDelayed(timeoutRunnable, 30000) // 30 секунд на запись
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    if (rmsdB > 0) {
                        android.util.Log.v("MainActivity", "Audio level: $rmsdB dB")
                        // Сбрасываем таймаут при наличии звука
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        timeoutHandler.postDelayed(timeoutRunnable, 30000)
                    }
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {
                    android.util.Log.d("MainActivity", "Buffer received: ${buffer?.size ?: 0} bytes")
                }
                
                override fun onEndOfSpeech() {
                    android.util.Log.d("MainActivity", "End of speech")
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                }
                
                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Ошибка аудио"
                        SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Недостаточно разрешений"
                        SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Таймаут сети"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Не распознано"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
                        SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Таймаут речи"
                        else -> "Неизвестная ошибка: $error"
                    }
                    android.util.Log.e("MainActivity", "Speech recognition error: $errorMessage")
                    
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, 
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            // Проверяем количество попыток перезапуска
                            if (SpeechRecognitionEvent.incrementRetryCount()) {
                                // Пробуем перезапустить распознавание
                                android.util.Log.d("MainActivity", "Retrying speech recognition (attempt: ${SpeechRecognitionEvent.getRetryCount()})")
                                speechRecognizer.destroy()
                                currentSpeechRecognizer = null
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    startSpeechRecognition(languageCode)
                                }, 1000)
                                return
                            } else {
                                android.util.Log.d("MainActivity", "Max retry count reached, stopping speech recognition")
                            }
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            // Если распознаватель занят, пробуем создать новый
                            speechRecognizer.destroy()
                            currentSpeechRecognizer = null
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                startSpeechRecognition(languageCode)
                            }, 500)
                            return
                        }
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            // Запрашиваем разрешения если их нет
                            requestPermissions(
                                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                                1234
                            )
                        }
                    }
                    
                    SpeechRecognitionEvent.setListening(false)
                    SpeechRecognitionEvent.onSpeechRecognitionCancelled()
                    speechRecognizer.destroy()
                    currentSpeechRecognizer = null
                }
                
                override fun onResults(results: Bundle?) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    
                    if (matches != null && matches.isNotEmpty()) {
                        val recognizedText = matches[0]
                        android.util.Log.d("MainActivity", "Recognized text: $recognizedText")
                        SpeechRecognitionEvent.onSpeechRecognized(recognizedText)
                    } else {
                        // Пробуем еще раз если нет результатов и не превышен лимит попыток
                        if (SpeechRecognitionEvent.incrementRetryCount()) {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                startSpeechRecognition(languageCode)
                            }, 1000)
                        } else {
                            android.util.Log.d("MainActivity", "Max retry count reached, no further attempts")
                            SpeechRecognitionEvent.onSpeechRecognized(null)
                        }
                    }
                    
                    SpeechRecognitionEvent.setListening(false)
                    speechRecognizer.destroy()
                    currentSpeechRecognizer = null
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    timeoutHandler.postDelayed(timeoutRunnable, 10000)
                    
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.isNotEmpty()) {
                        android.util.Log.d("MainActivity", "Partial result: ${matches[0]}")
                    }
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {
                    android.util.Log.d("MainActivity", "Speech event: $eventType")
                }
            })
            
            // Настраиваем intent для распознавания речи
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, fullLanguageCode)
                // Явно указываем локаль для распознавания
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, fullLanguageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, fullLanguageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                
                // Настройки для лучшего распознавания
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "")
                
                // Отключаем звуковые сигналы
                flags = flags or Intent.FLAG_ACTIVITY_NO_HISTORY
                flags = flags or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            
            // Запускаем распознавание
            speechRecognizer.startListening(recognizerIntent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error starting speech recognition", e)
            SpeechRecognitionEvent.setListening(false)
            SpeechRecognitionEvent.onSpeechRecognitionCancelled()
            currentSpeechRecognizer = null
        }
    }
}

// Синглтон для запросов на запуск распознавания речи
object SpeechRecognitionRequestEvent {
    private val listeners = mutableListOf<(String) -> Unit>()
    
    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }
    
    fun requestSpeechRecognition(languageCode: String) {
        listeners.forEach { it(languageCode) }
    }
}

// Синглтон для запросов на остановку распознавания речи
object SpeechRecognitionStopEvent {
    private val listeners = mutableListOf<() -> Unit>()
    
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
    
    fun requestStopSpeechRecognition() {
        listeners.forEach { it() }
    }
}

/**
 * Класс для отслеживания изменений языка клавиатуры
 */
class KeyboardLanguageListener(
    private val context: Context, 
    private val onLanguageChanged: (String) -> Unit
) {
    private val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    
    private var isRunning = false
    private var lastLanguage: String? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val checkKeyboardRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                getCurrentKeyboardLanguage()?.let { language ->
                    if (lastLanguage != language) {
                        lastLanguage = language
                        onLanguageChanged(language)
                    }
                }
                handler.postDelayed(this, 200) // Уменьшаем интервал для более быстрой реакции
            }
        }
    }
    
    fun register() {
        isRunning = true
        handler.post(checkKeyboardRunnable)
    }
    
    fun unregister() {
        isRunning = false
        handler.removeCallbacks(checkKeyboardRunnable)
    }
    
    private fun getCurrentKeyboardLanguage(): String? {
        try {
            val ims = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val currentSubtype = ims.currentInputMethodSubtype
            
            // Пробуем получить язык из разных свойств подтипа
            val language = when {
                // Пробуем получить из languageTag (новые версии Android)
                !currentSubtype?.languageTag.isNullOrEmpty() -> 
                    currentSubtype?.languageTag
                
                // Пробуем получить из locale (старые версии Android)
                !currentSubtype?.locale.isNullOrEmpty() -> {
                    val locale = currentSubtype?.locale
                    if (locale?.contains("-") == true) {
                        locale.split("-")[0]
                    } else if (locale?.contains("_") == true) {
                        locale.split("_")[0]
                    } else {
                        locale
                    }
                }
                
                else -> null
            }
            
            // Если получили язык, проверяем его на соответствие формату ISO 639-1
            if (!language.isNullOrEmpty() && language.length >= 2) {
                return language.substring(0, 2).lowercase()
            }
        } catch (e: Exception) {
            // Логируем ошибку, но не прерываем работу
            android.util.Log.e("KeyboardLanguageListener", "Error getting keyboard language", e)
        }
        
        // Если не удалось получить язык клавиатуры, возвращаем язык системы
        return Locale.getDefault().language
    }
}

/**
 * Синглтон для передачи событий изменения языка клавиатуры
 */
object KeyboardLanguageEvent {
    private val listeners = mutableListOf<(String) -> Unit>()
    
    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }
    
    fun onLanguageChanged(languageCode: String) {
        listeners.forEach { it(languageCode) }
    }
} 