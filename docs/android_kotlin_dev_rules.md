# Правила разработки Android-приложений на Kotlin

## Архитектурные принципы

### 1. Чистая архитектура
- Строгое разделение на слои: UI, Domain, Data
- Зависимости направлены внутрь (к Domain слою)
- Domain слой не зависит от Android и других внешних библиотек
- Каждый слой имеет свои модели данных

### 2. MVVM + Clean Use Cases
- ViewModels только для UI логики
- Use Cases для бизнес-логики
- Repositories для работы с данными
- Single Responsibility Principle для всех компонентов

### 3. Dependency Injection
- Использование Hilt для DI
- Модули по слоям (UI, Domain, Data)
- Scopes: Activity, Fragment, ViewModel
- Тестируемые зависимости

### 4. Асинхронное программирование
- Coroutines для асинхронных операций
- Flow для реактивного программирования
- Structured concurrency
- Обработка ошибок через sealed classes

## Правила кодирования

### 1. Именование
- Классы: PascalCase (UserRepository)
- Функции: camelCase (getUserById)
- Переменные: camelCase (userList)
- Константы: UPPER_SNAKE_CASE (MAX_RETRY_COUNT)
- Пакеты: lowercase.with.dots (com.example.app.data)

### 2. Структура файлов
```
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/app/
│   │   │   ├── ui/           # UI компоненты
│   │   │   ├── domain/       # Бизнес-логика
│   │   │   └── data/         # Работа с данными
│   │   └── res/              # Ресурсы
│   └── test/                 # Unit тесты
└── build.gradle.kts
```

### 3. Документация
- KDoc для всех публичных классов и функций
- README.md в каждом модуле
- Архитектурные решения в docs/architecture/
- API документация в docs/api/

### 4. Тестирование
- Unit тесты для Use Cases и Repositories
- UI тесты для ViewModels
- Integration тесты для Repository
- 80% покрытие кода тестами

## Правила работы с Git

### 1. Ветвление
- main: продакшн код
- develop: разработка
- feature/*: новые функции
- bugfix/*: исправления
- release/*: подготовка релиза

### 2. Коммиты
- Conventional Commits
- Атомарные коммиты
- Описательные сообщения
- Связанные с задачами

### 3. Code Review
- Проверка архитектурных принципов
- Проверка тестов
- Проверка документации
- Проверка производительности

## Правила безопасности

### 1. Данные
- Шифрование чувствительных данных
- Безопасное хранение ключей
- Валидация входных данных
- Защита от инъекций

### 2. Сеть
- HTTPS только
- Сертификаты
- Таймауты
- Обработка ошибок

### 3. Локальное хранилище
- Шифрование SharedPreferences
- Безопасное хранение в Room
- Очистка данных при выходе
- Защита от SQL-инъекций

## Правила производительности

### 1. UI
- Ленивая загрузка
- Кэширование изображений
- Оптимизация layout
- Предотвращение утечек памяти

### 2. Сеть
- Кэширование ответов
- Пакетные запросы
- Отмена устаревших запросов
- Офлайн режим

### 3. База данных
- Индексы
- Миграции
- Транзакции
- Очистка старых данных

## Правила локализации

### 1. Строки
- В strings.xml
- Параметризация
- Множественные числа
- Форматирование дат/чисел

### 2. Ресурсы
- Разделение по языкам
- Fallback ресурсы
- RTL поддержка
- Адаптивные размеры

## Правила доступности

### 1. Контент
- Описания для изображений
- Подписи к видео
- Альтернативный текст
- Семантическая разметка

### 2. Навигация
- Клавиатурная навигация
- TalkBack поддержка
- Увеличение текста
- Высокий контраст

## Правила аналитики

### 1. События
- Ключевые действия
- Ошибки
- Производительность
- Использование функций

### 2. Данные
- Анонимизация
- Минимальный объем
- Периодичность отправки
- Согласие пользователя 