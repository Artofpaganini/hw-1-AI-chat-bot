# AI Agent Chat

Android приложение для сравнения AI-моделей (DeepSeek, Claude 3.5 Sonnet, GPT-4o Mini, Gemini Pro 1.5) с метриками производительности.

## Архитектура

Проект использует **Clean Architecture** с разделением на слои:

```
app/
├── data/                    # Data Layer
│   ├── api/                 # API interfaces (DeepSeekApi, OpenRouterApi)
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
OPENROUTER_API_KEY=your_openrouter_api_key_here
```

**Важно:** Для моделей через OpenRouter (Claude 3.5 Sonnet, GPT-4o Mini, Gemini Pro 1.5) используется один API ключ `OPENROUTER_API_KEY`.

### Способ 2: gradle.properties

Добавьте в `~/.gradle/gradle.properties`:

```properties
DEEPSEEK_API_KEY=your_deepseek_api_key_here
OPENROUTER_API_KEY=your_openrouter_api_key_here
```

### Настройка цен на токены

В `build.gradle.kts` настроены цены по умолчанию ($/1M tokens):

```kotlin
// DeepSeek
buildConfigField("Double", "DEEPSEEK_INPUT_PRICE", "0.14")
buildConfigField("Double", "DEEPSEEK_OUTPUT_PRICE", "0.28")
// Claude 3.5 Sonnet (через OpenRouter)
buildConfigField("Double", "CLAUDE_35_SONNET_INPUT_PRICE", "3.00")
buildConfigField("Double", "CLAUDE_35_SONNET_OUTPUT_PRICE", "15.00")
// GPT-4o Mini (через OpenRouter)
buildConfigField("Double", "GPT_4O_MINI_INPUT_PRICE", "0.15")
buildConfigField("Double", "GPT_4O_MINI_OUTPUT_PRICE", "0.60")
// Gemini Pro 1.5 (через OpenRouter)
buildConfigField("Double", "GEMINI_PRO_15_INPUT_PRICE", "1.25")
buildConfigField("Double", "GEMINI_PRO_15_OUTPUT_PRICE", "5.00")
```

Измените значения при необходимости.

## Функциональность

### Переключение моделей
- Dropdown в AppBar для выбора между доступными моделями:
  - **DeepSeek** - прямое API
  - **Claude 3.5 Sonnet** - через OpenRouter (Anthropic)
  - **GPT-4o Mini** - через OpenRouter (OpenAI)
  - **Gemini Pro 1.5** - через OpenRouter (Google)
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
- **OpenRouter** (для Claude 3.5 Sonnet, GPT-4o Mini, Gemini Pro 1.5): https://openrouter.ai/
  - Создайте аккаунт на OpenRouter
  - Получите API ключ в настройках
  - Пополните баланс для использования моделей

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
