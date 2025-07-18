package com.example.translator.ui.screens.translator

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.graphics.Typeface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.translator.domain.model.Language
import com.example.translator.domain.usecase.GetLanguagesUseCase
import com.example.translator.domain.usecase.ToggleFavoriteLanguageUseCase
import com.example.translator.domain.usecase.TranslateTextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject
import com.example.translator.ui.KeyboardLanguageEvent
import com.example.translator.ui.SpeechRecognitionEvent
import com.example.translator.ui.SpeechRecognitionRequestEvent
import com.example.translator.data.local.LanguagePreferences
import java.util.Locale
import android.content.ClipData
import android.app.Application

/**
 * ViewModel для экрана перевода
 */
@HiltViewModel
class TranslatorViewModel @Inject constructor(
    private val translateTextUseCase: TranslateTextUseCase,
    private val getLanguagesUseCase: GetLanguagesUseCase,
    private val toggleFavoriteLanguageUseCase: ToggleFavoriteLanguageUseCase,
    private val languagePreferences: LanguagePreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false

    // Флаг, определяющий, происходит ли ввод через микрофон
    private var isVoiceInputActive = false

    // Слушатель изменения языка клавиатуры
    private val keyboardLanguageChangeListener: (String) -> Unit = { languageCode ->
        updateSourceLanguageByKeyboard(languageCode)
    }

    // Признак того, что изменился язык в третьем (последнем) спиннере
    private var targetLanguageChanged = false

    data class TranslatorState(
        val inputText: String = "",
        val translatedText: String = "",
        val sourceLanguage: Language? = null,
        val targetLanguage: Language? = null,
        val availableLanguages: List<Language> = emptyList(),
        val sourceLanguages: List<Language> = emptyList(),
        val targetLanguages: List<Language> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val alternatives: List<String> = emptyList(),
        val isSpeaking: Boolean = false
    )

    private val _state = MutableStateFlow(TranslatorState())
    val state = _state.asStateFlow()

    init {
        initTextToSpeech()
        
        // Подписываемся на события клавиатуры
        KeyboardLanguageEvent.addListener(keyboardLanguageChangeListener)
        
        // Подписываемся на события распознавания речи
        SpeechRecognitionEvent.addListener { recognizedText ->
            handleSpeechResult(recognizedText)
        }
        
        SpeechRecognitionEvent.addCancelListener {
            onSpeechRecognitionCancelled()
        }
        
        viewModelScope.launch {
            // Получаем списки языков для обоих спиннеров
            combine(
                getLanguagesUseCase(isSource = true),
                getLanguagesUseCase(isSource = false)
            ) { sourceLanguages, targetLanguages ->
                if (sourceLanguages.isNotEmpty() && targetLanguages.isNotEmpty()) {
                    // Инициализация языков при первой загрузке, если еще не установлены
                    if (_state.value.sourceLanguage == null || _state.value.targetLanguage == null) {
                        // Получаем избранные языки
                        val sourceFavorites = languagePreferences.getSourceLanguageFavorites()
                        val targetFavorites = languagePreferences.getTargetLanguageFavorites()
                        
                        // Получаем последние использованные языки
                        val lastSourceLanguage = languagePreferences.getLastSourceLanguage()
                        val lastTargetLanguage = languagePreferences.getLastTargetLanguage()
                        
                        // Выбираем исходный язык
                        val sourceLanguage = when {
                            // Сначала проверяем, является ли последний использованный язык избранным
                            lastSourceLanguage != null && sourceFavorites.contains(lastSourceLanguage) -> {
                                sourceLanguages.find { it.code == lastSourceLanguage }
                            }
                            // Затем проверяем, есть ли избранные языки
                            sourceFavorites.isNotEmpty() -> {
                                sourceLanguages.find { it.code == sourceFavorites.last() }
                            }
                            // Иначе берем язык системы или английский
                            else -> {
                                sourceLanguages.find { it.code == java.util.Locale.getDefault().language } 
                                    ?: sourceLanguages.find { it.code == "en" }
                                    ?: sourceLanguages.first()
                            }
                        }
                        
                        // Выбираем целевой язык
                        val targetLanguage = when {
                            // Сначала проверяем, является ли последний использованный язык избранным
                            lastTargetLanguage != null && targetFavorites.contains(lastTargetLanguage) -> {
                                targetLanguages.find { it.code == lastTargetLanguage }
                            }
                            // Затем проверяем, есть ли избранные языки
                            targetFavorites.isNotEmpty() -> {
                                targetLanguages.find { it.code == targetFavorites.last() }
                            }
                            // Иначе берем английский или русский, если исходный - английский
                            else -> {
                                if (sourceLanguage?.code == "en") {
                                    targetLanguages.find { it.code == "ru" } ?: targetLanguages.first { it.code != "en" }
                                } else {
                                    targetLanguages.find { it.code == "en" } ?: targetLanguages.first { it.code != sourceLanguage?.code }
                                }
                            }
                        }
                        
                        _state.update { 
                            it.copy(
                                sourceLanguages = sourceLanguages,
                                targetLanguages = targetLanguages,
                                sourceLanguage = sourceLanguage,
                                targetLanguage = targetLanguage
                            ) 
                        }
                    } else {
                        // При обновлении списков сохраняем текущие выбранные языки
                        val currentSourceLanguage = _state.value.sourceLanguage
                        val currentTargetLanguage = _state.value.targetLanguage
                        
                        // Находим обновленные версии текущих языков
                        val updatedSourceLanguage = sourceLanguages.find { it.code == currentSourceLanguage?.code } ?: currentSourceLanguage
                        val updatedTargetLanguage = targetLanguages.find { it.code == currentTargetLanguage?.code } ?: currentTargetLanguage
                        
                        _state.update { 
                            it.copy(
                                sourceLanguages = sourceLanguages,
                                targetLanguages = targetLanguages,
                                sourceLanguage = updatedSourceLanguage,
                                targetLanguage = updatedTargetLanguage
                            ) 
                        }
                    }
                }
            }.collect()
        }
    }
    
    /**
     * Обновляет исходный язык на основе языка клавиатуры
     */
    private fun updateSourceLanguageByKeyboard(languageCode: String) {
        viewModelScope.launch {
            val currentState = _state.value
            if (currentState.sourceLanguages.isNotEmpty()) {
                // Нормализуем код языка
                val normalizedLanguageCode = languageCode.lowercase().take(2)
                
                // Специальная обработка для украинского языка (uk -> ua)
                val searchCode = if (normalizedLanguageCode == "uk") "ua" else normalizedLanguageCode
                
                // Проверяем, что язык изменился и есть в списке доступных
                if (currentState.sourceLanguage?.code != searchCode) {
                    // Ищем язык с нужным кодом
                    currentState.sourceLanguages.find { it.code == searchCode }?.let { newSourceLanguage ->
                        android.util.Log.d("TranslatorViewModel", 
                            "Updating source language from ${currentState.sourceLanguage?.code} to ${newSourceLanguage.code}")
                        
                        // Обновляем состояние с новым исходным языком
                        _state.update { it.copy(sourceLanguage = newSourceLanguage) }
                        
                        // Если есть текст для перевода, запускаем перевод
                        if (currentState.inputText.isNotEmpty()) {
                            translate()
                        }
                    }
                }
            }
        }
    }
    
    override fun onCleared() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        // Удаляем слушатели при уничтожении ViewModel
        KeyboardLanguageEvent.removeListener(keyboardLanguageChangeListener)
        SpeechRecognitionEvent.removeListener(::handleSpeechResult)
        SpeechRecognitionEvent.removeCancelListener(::onSpeechRecognitionCancelled)
        super.onCleared()
    }

    /**
     * Устанавливает текст для перевода.
     */
    fun setInputText(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    /**
     * Устанавливает исходный язык
     */
    fun setSourceLanguage(language: Language) {
        _state.update { it.copy(sourceLanguage = language) }
        
        // Сохраняем последний выбранный исходный язык
        viewModelScope.launch {
            languagePreferences.setLastSourceLanguage(language.code)
        }
        
        // Не запускаем перевод при изменении исходного языка
    }
    
    /**
     * Устанавливает целевой язык
     */
    fun setTargetLanguage(language: Language) {
        _state.update { it.copy(targetLanguage = language) }
        
        // Сохраняем последний выбранный целевой язык
        viewModelScope.launch {
            languagePreferences.setLastTargetLanguage(language.code)
        }
        
        // Устанавливаем флаг, что изменился целевой язык
        targetLanguageChanged = true
        
        // Если есть текст для перевода, запускаем перевод с задержкой
        if (state.value.inputText.isNotEmpty()) {
            viewModelScope.launch {
                delay(500) // Задержка для лучшего UX
                
                // Проверяем, был ли изменен целевой язык
                if (targetLanguageChanged) {
                    translate()
                    // Сбрасываем флаг
                    targetLanguageChanged = false
                }
            }
        }
    }

    fun swapLanguages() {
        val currentSourceLanguage = state.value.sourceLanguage
        val currentTargetLanguage = state.value.targetLanguage
        
        _state.update {
            it.copy(
                sourceLanguage = it.targetLanguage,
                targetLanguage = it.sourceLanguage
            )
        }
        
        // Сохраняем новые значения последних языков
        viewModelScope.launch {
            currentTargetLanguage?.let { language ->
                languagePreferences.setLastSourceLanguage(language.code)
            }
            currentSourceLanguage?.let { language ->
                languagePreferences.setLastTargetLanguage(language.code)
            }
        }
        
        // Отключаем автоматический перевод при смене языков
        // if (state.value.inputText.isNotEmpty()) {
        //     translate()
        // }
    }

    fun clearInput() {
        _state.update {
            it.copy(
                inputText = "",
                translatedText = "",
                alternatives = emptyList()
            )
        }
    }

    /**
     * Запускает распознавание речи
     */
    fun startSpeechRecognition() {
        // Устанавливаем флаг, что ввод происходит через микрофон
        isVoiceInputActive = true
        
        // Логируем запуск распознавания для отладки
        android.util.Log.d("TranslatorViewModel", "Speech recognition requested, isVoiceInputActive: $isVoiceInputActive")
        
        // Запрашиваем запуск распознавания речи через MainActivity
        state.value.sourceLanguage?.let { language ->
            SpeechRecognitionRequestEvent.requestSpeechRecognition(language.code)
        } ?: run {
            // Если язык не выбран, используем системный
            SpeechRecognitionRequestEvent.requestSpeechRecognition(Locale.getDefault().language)
        }
    }
    
    /**
     * Обрабатывает результат распознавания речи
     */
    private fun handleSpeechResult(text: String?) {
        isVoiceInputActive = false
        
        if (text.isNullOrEmpty()) {
            return
        }
        
        // Устанавливаем распознанный текст в поле ввода
        setInputText(text)
        
        // Автоматический перевод после голосового ввода с небольшой задержкой для улучшения UX
        viewModelScope.launch {
            // Ждем 300 мс перед запуском перевода
            delay(300)
            
            // Проверяем, что есть текст и выбраны языки
            if (state.value.inputText.isNotEmpty() 
                && state.value.sourceLanguage != null 
                && state.value.targetLanguage != null) {
                
                // Запускаем перевод
                translate()
            }
        }
    }
    
    /**
     * Метод, вызываемый при отмене распознавания речи
     */
    fun onSpeechRecognitionCancelled() {
        isVoiceInputActive = false
        android.util.Log.d("TranslatorViewModel", "Speech recognition cancelled")
    }

    /**
     * Удаляет текст в скобках из строки
     */
    private fun removeTextInParentheses(text: String): String {
        return text.replace(Regex("\\s*\\(.*?\\)\\s*"), " ").trim()
    }

    /**
     * Озвучивает перевод.
     * При озвучивании игнорирует текст в скобках (контекст).
     * Добавляет паузу между произношением вариантов слов.
     * При повторном нажатии останавливает озвучивание.
     */
    fun speakTranslation() {
        val currentState = state.value
        val tts = this.textToSpeech
        
        if (tts == null || !ttsInitialized) {
            _state.update { it.copy(error = "Синтезатор речи не инициализирован") }
            return
        }
        
        // Проверяем, идет ли уже озвучивание - если да, то останавливаем
        if (currentState.isSpeaking) {
            try {
                tts.stop()
                _state.update { it.copy(isSpeaking = false) }
                return
            } catch (e: Exception) {
                android.util.Log.e("TTS", "Ошибка при остановке озвучивания", e)
                // Продолжаем выполнение для перезапуска озвучивания
            }
        }
        
        if (currentState.translatedText.isEmpty()) {
            return
        }
        
        viewModelScope.launch {
            try {
                _state.update { it.copy(isSpeaking = true) }
                
                // Устанавливаем язык озвучивания в соответствии с целевым языком перевода
                if (currentState.targetLanguage != null) {
                    val locale = getLocaleForLanguageCode(currentState.targetLanguage.code)
                    
                    try {
                        val result = tts.setLanguage(locale)
                        
                        when (result) {
                            TextToSpeech.LANG_MISSING_DATA -> {
                                android.util.Log.w("TTS", "Данные для языка ${locale.language} отсутствуют")
                                _state.update { it.copy(isSpeaking = false, error = "Языковые данные для озвучивания отсутствуют") }
                                return@launch
                            }
                            TextToSpeech.LANG_NOT_SUPPORTED -> {
                                android.util.Log.w("TTS", "Язык ${locale.language} не поддерживается")
                                _state.update { it.copy(isSpeaking = false, error = "Язык не поддерживается для озвучивания") }
                                return@launch
                            }
                            else -> {
                                // Замедляем скорость речи для лучшего понимания
                                tts.setSpeechRate(0.9f)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("TTS", "Ошибка установки языка", e)
                        _state.update { it.copy(isSpeaking = false, error = "Ошибка установки языка: ${e.message}") }
                        return@launch
                    }
                }
                
                // Настраиваем слушатель прогресса озвучивания
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        android.util.Log.d("TTS", "Началось озвучивание utteranceId=$utteranceId")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        viewModelScope.launch {
                            // Проверяем, закончено ли всё озвучивание
                            if (utteranceId == "translation" || utteranceId == "word_last") {
                                _state.update { it.copy(isSpeaking = false) }
                                android.util.Log.d("TTS", "Озвучивание завершено")
                            }
                        }
                    }
                    
                    override fun onError(utteranceId: String?) {
                        viewModelScope.launch {
                            _state.update { it.copy(isSpeaking = false) }
                            android.util.Log.e("TTS", "Ошибка озвучивания utteranceId=$utteranceId")
                        }
                    }
                })
                
                // Определяем тип текста для озвучивания
                val text = currentState.translatedText
                
                if (text.contains("(")) {
                    // Многовариантный перевод (слово)
                    
                    // Очищаем очередь произношения
                    tts.speak("", TextToSpeech.QUEUE_FLUSH, null, null)
                    
                    // Разбиваем на строки и обрабатываем каждый вариант перевода
                    text.split("\n").forEachIndexed { index, line ->
                        // Извлекаем только слово перед скобками
                        val word = line.substringBefore("(").trim()
                        
                        if (word.isNotEmpty()) {
                            // Определяем, является ли это последним словом
                            val isLastWord = index == text.split("\n").size - 1
                            val utterId = if (isLastWord) "word_last" else "word_$index"
                            
                            // Озвучиваем слово, добавляя в очередь
                            tts.speak(
                                word,
                                TextToSpeech.QUEUE_ADD,
                                null,
                                utterId
                            )
                            
                            // Добавляем паузу после каждого слова, кроме последнего
                            if (!isLastWord) {
                                tts.playSilentUtterance(
                                    800, // 800 мс паузы
                                    TextToSpeech.QUEUE_ADD,
                                    "pause_$index"
                                )
                            }
                        }
                    }
                } else {
                    // Обычный перевод (предложение) - озвучиваем как есть
                    tts.speak(
                        text,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "translation"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSpeaking = false, error = "Ошибка озвучивания: ${e.message}") }
                android.util.Log.e("TTS", "Ошибка при озвучивании", e)
            }
        }
    }

    fun copyTranslation() {
        val currentState = state.value
        if (currentState.translatedText.isNotEmpty()) {
            try {
                // Получаем ClipboardManager из контекста, внедренного через Hilt
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                
                // Создаем ClipData с текстом перевода, очищая форматирование
                val plainText = android.text.SpannableString(currentState.translatedText).toString()
                val clip = ClipData.newPlainText("Перевод", plainText)
                
                // Устанавливаем данные в буфер обмена
                clipboard.setPrimaryClip(clip)
                
                // Убираем сообщение об успешном копировании
                // Просто копируем текст без уведомления
            } catch (e: Exception) {
                // Обрабатываем возможные ошибки
                _state.update { it.copy(error = "Ошибка при копировании: ${e.message}") }
                android.util.Log.e("TranslatorViewModel", "Error copying translation", e)
            }
        }
    }

    /**
     * Вставляет текст из буфера обмена в поле ввода
     */
    fun pasteFromClipboard() {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        if (clipboardManager.hasPrimaryClip()) {
            val clipData = clipboardManager.primaryClip
            
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text
                
                if (!text.isNullOrEmpty()) {
                    setInputText(text.toString())
                    
                    // Автоматически запускаем перевод, если есть языки
                    if (state.value.sourceLanguage != null && state.value.targetLanguage != null) {
                        viewModelScope.launch {
                            translate()
                        }
                    }
                }
            }
        }
    }

    fun shareTranslation() {
        // Реализация отправки перевода
    }

    fun toggleSourceLanguageFavorite(language: Language) {
        viewModelScope.launch {
            toggleFavoriteLanguageUseCase(language, isSource = true)
        }
    }

    fun toggleTargetLanguageFavorite(language: Language) {
        viewModelScope.launch {
            toggleFavoriteLanguageUseCase(language, isSource = false)
        }
    }

    fun translate() {
        val currentState = state.value
        if (currentState.sourceLanguage == null || currentState.targetLanguage == null) {
            return
        }

        viewModelScope.launch {
            try {
                // Показываем индикатор загрузки и очищаем ошибки
                _state.update { it.copy(isLoading = true, error = null) }
                
                // Если текст большой, добавляем сообщение об этом
                if (currentState.inputText.length > 1000) {
                    _state.update { it.copy(
                        translatedText = "Перевод большого текста... Это может занять некоторое время.",
                        isLoading = true
                    )}
                }
                
                val result = translateTextUseCase(
                    text = currentState.inputText,
                    sourceLanguage = currentState.sourceLanguage,
                    targetLanguage = currentState.targetLanguage
                )
                
                // Определяем, является ли текст словом
                val isWord = currentState.inputText.trim().matches(Regex("^[\\p{L}'-]+$"))
                
                _state.update { 
                    it.copy(
                        translatedText = result.translatedText,
                        alternatives = if (isWord) result.alternatives else emptyList(),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        error = e.message ?: "Ошибка перевода",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun getFormattedTranslationResult(): android.text.SpannableStringBuilder {
        val currentState = state.value
        val builder = android.text.SpannableStringBuilder()
        
        // Обрабатываем основной перевод
        if (currentState.translatedText.isNotEmpty()) {
            // Разбиваем на строки для обработки каждого варианта отдельно
            val lines = currentState.translatedText.split("\n")
            
            lines.forEachIndexed { index, line ->
                if (line.isEmpty()) {
                    builder.append("\n")
                    return@forEachIndexed
                }
                
                val parenthesesIndex = line.indexOf("(")
                
                if (parenthesesIndex > 0) {
                    // Строка содержит скобки - это вариант перевода слова
                    
                    // Добавляем тире в начало строки и основной текст
                    // Теперь добавляем тире перед всеми вариантами, включая первый
                    val mainText = "— ${line.substring(0, parenthesesIndex).trim()}"
                    
                    // Применяем жирный стиль к основному тексту
                    val mainSpan = android.text.SpannableString(mainText)
                    mainSpan.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        0, 
                        mainText.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    // Увеличиваем размер текста для всех вариантов
                    mainSpan.setSpan(
                        android.text.style.RelativeSizeSpan(1.2f),
                        0,
                        mainText.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    
                    builder.append(mainSpan)
                    
                    // Добавляем текст в скобках с курсивом и отступом
                    val explanationText = " " + line.substring(parenthesesIndex)
                    val explanationSpan = android.text.SpannableString(explanationText)
                    explanationSpan.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                        0,
                        explanationText.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    // Добавляем отступ для текста в скобках
                    explanationSpan.setSpan(
                        android.text.style.LeadingMarginSpan.Standard(30),
                        0,
                        explanationText.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    
                    builder.append(explanationSpan)
                } else {
                    // Простой текст без скобок - обычный перевод
                    // Для обычного текста тире не добавляем
                    val textSpan = android.text.SpannableString(line)
                    textSpan.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        0,
                        line.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    // Увеличиваем размер основного текста
                    textSpan.setSpan(
                        android.text.style.RelativeSizeSpan(1.2f),
                        0,
                        line.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    
                    builder.append(textSpan)
                }
                
                // Добавляем перевод строки, если это не последняя строка
                if (index < lines.size - 1) {
                    builder.append("\n")
                }
            }
        }
        
        // Добавляем альтернативы, если они есть
        if (currentState.alternatives.isNotEmpty()) {
            builder.append("\n\n")
            
            // Заголовок "Альтернативы:"
            val alternativesLabel = "Альтернативы:"
            val labelSpan = android.text.SpannableString(alternativesLabel)
            labelSpan.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.NORMAL),
                0,
                alternativesLabel.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            builder.append(labelSpan)
            builder.append("\n")
            
            // Добавляем каждую альтернативу
            currentState.alternatives.forEachIndexed { index, alternative ->
                val altSpan = android.text.SpannableString("— $alternative")
                altSpan.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0,
                    altSpan.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                builder.append(altSpan)
                
                // Добавляем перевод строки, если это не последняя альтернатива
                if (index < currentState.alternatives.size - 1) {
                    builder.append("\n")
                }
            }
        }
        
        return builder
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            ttsInitialized = status == TextToSpeech.SUCCESS
            if (ttsInitialized) {
                textToSpeech?.language = Locale.getDefault()
            }
        }
    }

    /**
     * Преобразует код языка в Locale для TTS
     */
    private fun getLocaleForLanguageCode(code: String): Locale {
        return when (code) {
            "ru" -> Locale("ru", "RU")
            "en" -> Locale("en", "US")
            "uk" -> Locale("uk", "UA")
            "pl" -> Locale("pl", "PL")
            "fr" -> Locale("fr", "FR")
            "de" -> Locale("de", "DE")
            "es" -> Locale("es", "ES")
            "it" -> Locale("it", "IT")
            "pt" -> Locale("pt", "PT")
            "ja" -> Locale("ja", "JP")
            "ko" -> Locale("ko", "KR")
            "zh" -> Locale("zh", "CN")
            else -> Locale(code)
        }
    }
} 