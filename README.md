# AI Agent Chat

Android приложение для сравнения AI-моделей (DeepSeek, Claude 3.5 Sonnet, GPT-4o Mini, Gemini Pro 1.5) с метриками производительности.

## Архитектура

Проект использует **Multi-Module Clean Architecture** с разделением на слои:

```
app/
├── core/
│   ├── common/          # Общие утилиты
│   ├── database/        # Room database (entities, DAOs)
│   ├── network/         # Network layer (Retrofit, OkHttp)
│   └── uikit/           # Переиспользуемые UI компоненты, тема, цвета
├── feature/
│   ├── chat/            # Chat feature (Clean Architecture)
│   │   ├── data/        # Data layer (repositories, API, mappers)
│   │   ├── domain/      # Domain layer (models, use cases, repositories interfaces)
│   │   └── presentation/# Presentation layer (ViewModels, UI)
│   ├── home/            # Home feature
│   ├── profile/         # Profile feature
│   ├── settings/        # Settings feature (theme switching)
│   ├── patients/        # Patients feature
│   └── appointments/    # Appointments feature
└── app/                 # Main app module (navigation, DI)
```

## Технологии

- **Kotlin 2.0.21**
- **Jetpack Compose 1.9+** (Material 3, edge-to-edge для API>29)
- **Android Navigation 3** (for Compose)
- **Koin 4.1+** (DI)
- **Coroutines 1.10+** / Flow
- **Room 2.6.1** (для хранения истории чата)
- **Retrofit 2.11** + OkHttp 4.12
- **Gradle Version Catalog** (libs.versions.toml)

## Настройка API ключей

Добавьте в файл `local.properties`:

```properties
DEEPSEEK_API_KEY=your_deepseek_api_key_here
OPENROUTER_API_KEY=your_openrouter_api_key_here
```

## Архитектурные принципы

### Clean Architecture
- **Domain Layer**: Бизнес-логика, use cases, модели (не зависит от других слоев)
- **Data Layer**: Реализация репозиториев, API клиенты, маппинг DTO → Domain
- **Presentation Layer**: ViewModels с UDF паттерном, Compose UI

### UDF Pattern (Unidirectional Data Flow)
- **Action**: События от UI
- **UiState**: Состояние UI (data class)
- **Event**: Одноразовые события (sealed interface)

### ViewModel Pattern
- Все методы приватные, кроме `onAction(Action)`
- StateFlow для состояния
- SharedFlow для событий

## Сборка

```bash
./gradlew assembleDebug
```

## Функционал персонализации

Приложение поддерживает персонализацию AI-ответов на основе контекста сообщений пользователя.

### Как это работает:

1. При отправке сообщения AI извлекает основную тему (максимум 5 слов)
2. Система ищет похожие контексты в базе данных
3. На основе найденных контекстов определяется или создается профиль пользователя с персонализированным промптом
4. AI отвечает с учетом интересов пользователя и предлагает релевантные темы

### Проверка функционала:

1. **Первое сообщение**: Отправьте любое сообщение (например: "Расскажи про Эверест")
   - Создастся новый профиль пользователя
   - AI ответит с учетом темы

2. **Похожий контекст**: Отправьте похожее сообщение
   - Система найдет похожий контекст и использует существующий профиль

3. **Новая тема**: Отправьте сообщение на другую тему
   - Создастся новый контекст и, возможно, новый профиль

### Просмотр данных:

Используйте Android Studio Database Inspector для просмотра:
- Таблица `users` - профили пользователей с промптами персонализации
- Таблица `user_contexts` - контексты сообщений (максимум 5 слов)

Подробная документация: [30HW_PERSONALIZATION.md](30HW_PERSONALIZATION.md)

## Тестирование

- **Unit-тесты**: JUnit5 + MockK
- **UI-тесты**: Compose Testing
- **Именование**: `givenX_whenY_thenZ()`

## License

MIT
