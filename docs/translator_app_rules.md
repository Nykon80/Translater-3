# Правила и инструкции для разработки приложения-переводчика

## 1. Архитектурные принципы

### 1.1. Трехслойная архитектура
- **UI слой**: Активити, фрагменты, композаблы, ViewModels
- **Domain слой**: Бизнес-логика, модели данных, use cases
- **Data слой**: Репозитории, источники данных, API-клиенты, БД

### 1.2. Компоненты архитектуры
- **Presentation (UI)**: MVVM с StateFlow для состояний
- **Domain**: Чистые Use Cases для бизнес-логики
- **Data**: Repository Pattern для абстракции источников данных
- **DI**: Hilt для внедрения зависимостей

## 2. Специфичные правила для переводчика

### 2.1. Обработка текста
- **Различие слов и предложений**: 
  ```kotlin
  fun isWordInput(text: String): Boolean {
      return text.trim().matches(Regex("^[\\p{L}'-]+$"))
  }
  ```
- **Ограничения**: 
  - Максимум 50 предложений для перевода
  - История: 100 записей для слов, 50 для предложений

### 2.2. Форматирование результатов перевода
- **Для слов**: 
  - Каждый вариант на новой строке
  - Основное значение полужирным
  - Пояснения в скобках обычным шрифтом
- **Для предложений**: 
  - Полный текст без особого форматирования

### 2.3. Озвучивание перевода
- **Для слов**: 
  - Озвучивать каждый вариант отдельно
  - Извлекать слово до скобок
- **Для предложений**: 
  - Озвучивать весь текст целиком
- **Индикация**: 
  - Визуальная индикация процесса озвучивания

### 2.4. Работа с языками
- **Источник**: 
  - По умолчанию - язык системы
  - Синхронизация с языком клавиатуры
- **Целевой язык**: 
  - Английский, если язык системы не английский
  - Иначе - любой другой
- **Избранные языки**: 
  - Отдельные списки для исходного и целевого языков
  - Приоритет последнего выбранного языка из избранных

## 3. Правила именования

### 3.1. Классы и интерфейсы
- Имена классов: `PascalCase`
- Модели данных: `[Название]Model` или `[Название]Entity`
- Use Cases: `[Глагол][Название]UseCase`
- Репозитории: `[Название]Repository`
- ViewModels: `[Экран]ViewModel`

### 3.2. Функции
- Имена функций: `camelCase`
- Префиксы для действий:
  - `get` - получение данных
  - `set` - установка данных 
  - `is/has` - булевы функции
  - `on` - обработчики событий

### 3.3. Константы
- Имена констант: `UPPER_SNAKE_CASE`
- Ключи SharedPreferences: `KEY_[название]`
- Ключи API: `API_KEY`, `API_BASE_URL`

## 4. Протоколирование функций

### 4.1. Структура документации KDoc
```kotlin
/**
 * Выполняет перевод текста с учетом типа ввода (слово или предложение).
 *
 * @param text Текст для перевода
 * @param sourceLang Язык исходного текста
 * @param targetLang Язык перевода
 * @return Отформатированный результат перевода
 * @throws TranslationException при ошибке перевода
 */
suspend fun performTranslation(text: String, sourceLang: String, targetLang: String): String
```

### 4.2. Запись в реестр функций
- Каждая функция должна быть добавлена в `function_registry.html`
- Структура записи:
  ```html
  <div class="function" id="perform_translation">
     <h3>
        performTranslation
        <span>
           <span class="tag domain">Domain</span>
           <span class="tag translation">Перевод</span>
        </span>
     </h3>
     <p class="description">Выполняет перевод текста с учетом типа ввода</p>
     <p class="signature">suspend fun performTranslation(text: String, sourceLang: String, targetLang: String): String</p>
     <p class="location">domain/usecase/TranslateTextUseCase.kt</p>
     <div class="dependencies">
        <h4>Зависимости:</h4>
        <ul>
           <li><a href="#translation_repository">translationRepository.translateText</a></li>
           <li><a href="#is_word_input">isWordInput</a></li>
           <li><a href="#format_word_translation">formatWordTranslation</a></li>
        </ul>
     </div>
     <div class="changelog">
        <h4>История изменений:</h4>
        <ul>
           <li>[YYYY-MM-DD] Создана функция</li>
        </ul>
     </div>
  </div>
  ```

## 5. Обновление структуры проекта

### 5.1. Архитектурная диаграмма
- Обновлять `project_structure.html` при каждом изменении архитектуры
- Добавлять новые компоненты в соответствующие слои

### 5.2. Граф зависимостей
- Все новые зависимости должны быть отражены в графе
- Проверять граф на циклические зависимости

## 6. Ключевые функции приложения

### 6.1. Перевод текста
```kotlin
/**
 * Основная функция для перевода текста с обработкой исключений и форматированием результата.
 */
suspend fun performTranslation() {
    try {
        // 1. Получаем текст и определяем тип (слово или предложение)
        val text = inputText.text.toString().trim()
        if (text.isEmpty()) return
        
        // Определяем тип ввода с помощью регулярного выражения
        val isWord = isWordInput(text)
        
        // 2. Получаем коды выбранных языков
        val sourceLang = getSelectedSourceLanguage()
        val targetLang = getSelectedTargetLanguage()
        
        // 3. Показываем индикатор загрузки
        showLoading(true)
        
        // 4. Выполняем перевод в фоновом потоке
        val result = translateTextUseCase(text, sourceLang, targetLang)
        
        // 5. Обрабатываем результат в зависимости от типа
        if (isWord) {
            // Форматируем и показываем результат перевода слова
            val formattedResult = formatWordTranslation(result)
            setFormattedTranslationResult(formattedResult)
            
            // Сохраняем в историю слов
            saveWordTranslationToHistory(sourceLang, targetLang, text, result.split("\n"))
        } else {
            // Показываем результат перевода предложения как есть
            setFormattedTranslationResult(result)
            
            // Сохраняем в историю предложений
            saveSentenceTranslationToHistory(sourceLang, targetLang, text, result)
        }
        
        // 6. Скрываем индикатор загрузки
        showLoading(false)
        
    } catch (e: Exception) {
        handleTranslationError(e)
    }
}
```

### 6.2. Голосовой ввод
```kotlin
/**
 * Инициирует распознавание речи через микрофон.
 */
fun startSpeechRecognition() {
    if (!isSpeechRecognizerAvailable()) {
        showError(R.string.speech_recognizer_not_available)
        return
    }
    
    // Настраиваем распознаватель речи
    val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    speechRecognizer.setRecognitionListener(createRecognitionListener())
    
    // Подготавливаем intent для распознавания
    val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, getCurrentSourceLanguageCode())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    
    // Активируем визуальную индикацию
    showListeningIndicator(true)
    
    // Запускаем распознавание
    speechRecognizer.startListening(recognizerIntent)
}
```

### 6.3. Озвучивание перевода
```kotlin
/**
 * Озвучивает результат перевода в зависимости от типа (слово/предложение).
 */
fun speakTranslation() {
    if (!isTtsInitialized) {
        initializeTextToSpeech { speakTranslation() }
        return
    }
    
    // Получаем текст для озвучивания
    val text = translationResult.text.toString()
    if (text.isEmpty()) return
    
    // Активируем индикацию озвучивания
    setSpeakingIndicator(true)
    
    if (isWordTranslation(text)) {
        // Для слов озвучиваем каждый вариант перевода отдельно
        text.split("\n").forEach { line ->
            // Извлекаем только слово без пояснения в скобках
            val word = line.substringBefore("(").trim()
            if (word.isNotEmpty()) {
                textToSpeech.speak(word, TextToSpeech.QUEUE_ADD, null, "word_$word")
            }
        }
    } else {
        // Для предложений озвучиваем весь текст целиком
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translation")
    }
}
```

### 6.4. История переводов
```kotlin
/**
 * Добавляет перевод слова в историю с соблюдением ограничения на количество записей.
 */
fun addWordTranslation(sourceLang: String, targetLang: String, originalWord: String, translations: List<String>) {
    viewModelScope.launch(Dispatchers.IO) {
        // Проверяем количество записей и удаляем старые, если превышен лимит
        val count = historyRepository.getWordTranslationsCount()
        if (count >= WORD_HISTORY_LIMIT) {
            historyRepository.deleteOldestNonFavoriteWordTranslations(count - WORD_HISTORY_LIMIT + 1)
        }
        
        // Создаем запись и сохраняем в репозиторий
        val wordTranslation = WordTranslation(
            id = 0,
            sourceLang = sourceLang,
            targetLang = targetLang,
            originalWord = originalWord,
            translations = translations,
            timestamp = System.currentTimeMillis(),
            isFavorite = false
        )
        
        historyRepository.saveWordTranslation(wordTranslation)
    }
}
```

## 7. Проверка качества кода

### 7.1. Обязательные тесты
- Unit-тесты для всех Use Cases
- Unit-тесты для репозиториев
- Тесты для утилитарных функций (форматирование, проверка типа ввода)
- UI-тесты для основных сценариев использования

### 7.2. Чек-лист для кодового ревью
- Соответствие архитектурным принципам
- Правильное разделение ответственности
- Обработка ошибок
- Документирование функций
- Обновление реестра функций
- Обновление структуры проекта

## 8. Специфические функции для перевода

### 8.1. Определение типа ввода
```kotlin
/**
 * Определяет, является ли входной текст отдельным словом или предложением.
 *
 * @param text Текст для проверки
 * @return true, если текст является отдельным словом, false если предложение
 */
fun isWordInput(text: String): Boolean {
    return text.trim().matches(Regex("^[\\p{L}'-]+$"))
}
```

### 8.2. Форматирование перевода слова
```kotlin
/**
 * Форматирует результат перевода слова, разделяя значения и выделяя основные слова.
 *
 * @param result Строка с результатом перевода от API
 * @return Форматированная строка с переводами
 */
fun formatWordTranslation(result: String): String {
    // Если результат не содержит разделителей, возвращаем как есть
    if (!result.contains("\n") && !result.contains("(")) {
        return result
    }
    
    // Разбиваем на отдельные значения
    val meanings = result.split("\n").filter { it.isNotEmpty() }
    
    // Форматируем каждое значение
    return meanings.joinToString("\n") { meaning ->
        // Проверяем, содержит ли значение пояснение в скобках
        if (meaning.contains("(")) {
            val mainWord = meaning.substringBefore("(").trim()
            val explanation = meaning.substringAfter("(").substringBefore(")").trim()
            "$mainWord ($explanation)"
        } else {
            meaning
        }
    }
}
```

### 8.3. Применение форматирования к тексту
```kotlin
/**
 * Применяет форматирование к тексту перевода для отображения в TextView.
 *
 * @param text Текст для форматирования
 */
fun setFormattedTranslationResult(text: String) {
    // Создаем SpannableString для форматирования
    val spannable = SpannableString(text)
    
    // По умолчанию устанавливаем полужирный шрифт для всего текста
    spannable.setSpan(
        StyleSpan(Typeface.BOLD),
        0,
        text.length,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    
    // Для текста в скобках устанавливаем обычный шрифт
    val regex = "\\(.*?\\)".toRegex()
    val matches = regex.findAll(text)
    
    matches.forEach { matchResult ->
        val start = matchResult.range.first
        val end = matchResult.range.last + 1
        
        spannable.setSpan(
            StyleSpan(Typeface.NORMAL),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    
    // Устанавливаем отформатированный текст
    resultTextView.text = spannable
}
```

## 9. Алгоритм проверки при внесении изменений

1. **Проверка локальных изменений**:
   - Согласованность с архитектурными принципами
   - Правильное разделение ответственности
   - Наличие документации KDoc

2. **Проверка взаимодействия**:
   - Идентификация всех зависимостей и зависимых функций
   - Проверка на потенциальные конфликты

3. **Обновление документации**:
   - Добавление новых функций в реестр
   - Обновление существующих записей при изменении
   - Добавление изменений в историю

4. **Обновление структуры проекта**:
   - Добавление новых компонентов в диаграмму
   - Обновление графа зависимостей
   - Проверка на циклические зависимости

5. **Тестирование**:
   - Написание юнит-тестов для новой функциональности
   - Запуск существующих тестов для проверки интеграции 