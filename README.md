# AI Agent Chat

Android приложение для сравнения AI-моделей (DeepSeek, Z.ai) с метриками производительности.

## Архитектура

Проект использует **Clean Architecture** с разделением на слои:

```
app/
├── data/                    # Data Layer
│   ├── api/                 # API interfaces (DeepSeekApi, ZaiApi)
│   ├── repository/          # Repository implementations
│   ├── AuthManager.kt       # API keys management
│   └── PricingConfig.kt     # Token pricing configuration
├── domain/                  # Domain Layer
│   ├── model/               # Business models (AiModel, Message, Metrics)
│   ├── repository/          # Repository interfaces
│   └── usecase/             # Use cases (SendMessage, SwitchModel, CompareMetrics)
├── presentation/            # Presentation Layer
│   ├── ui/                  # Compose UI components
│   │   ├── components/      # Reusable components
│   │   └── theme/           # Material 3 theme
│   ├── ChatViewModel.kt     # ViewModel with UDF pattern
│   └── ChatState.kt         # UI State & Events
└── di/                      # Koin DI modules
```

## Технологии

- **Kotlin 2.1.0**
- **Jetpack Compose** (BOM 2024.12.01)
- **Material 3**
- **Koin 4.0** (DI)
- **Retrofit 2.11** + OkHttp 4.12
- **Coroutines + Flow**

## Настройка API ключей

### Способ 1: local.properties (рекомендуется)

Добавьте в файл `local.properties`:

```properties
DEEPSEEK_API_KEY=your_deepseek_api_key_here
ZAI_API_KEY=your_zai_api_key_here
```

### Способ 2: gradle.properties

Добавьте в `~/.gradle/gradle.properties`:

```properties
DEEPSEEK_API_KEY=your_deepseek_api_key_here
ZAI_API_KEY=your_zai_api_key_here
```

### Настройка цен на токены

В `build.gradle.kts` настроены цены по умолчанию ($/1M tokens):

```kotlin
buildConfigField("Double", "DEEPSEEK_INPUT_PRICE", "0.14")
buildConfigField("Double", "DEEPSEEK_OUTPUT_PRICE", "0.28")
buildConfigField("Double", "ZAI_INPUT_PRICE", "0.10")
buildConfigField("Double", "ZAI_OUTPUT_PRICE", "0.20")
```

Измените значения при необходимости.

## Функциональность

### Переключение моделей
- Dropdown в AppBar для выбора между DeepSeek и Z.ai
- История чата сохраняется при смене модели
- Индикатор конфигурации API ключа для каждой модели

### Метрики производительности
Для каждого ответа отображаются:
- ⏱️ **Время ответа** (мс)
- 🪙 **Токены** (input/output)
- 💰 **Стоимость** (USD)

### Сравнение моделей
При отправке одного запроса обеим моделям отображается карточка сравнения:
- Разница во времени ответа
- Разница в количестве токенов
- Разница в стоимости

## Сборка

```bash
./gradlew assembleDebug
```

## Получение API ключей

- **DeepSeek**: https://platform.deepseek.com/
- **Z.ai**: https://z.ai/ (замените BASE_URL в `ZaiApi.kt` на актуальный)

## Тестирование

Для unit-тестов используется `FakeMetricsSource`:

```kotlin
// В тестах
val fakeMetrics = FakeMetricsSource.createFakeMetrics(
    responseTimeMs = 500L,
    inputTokens = 100,
    outputTokens = 200
)
```

## License

MIT
