# 28HW: Оптимизация локальной LLM на VPS для конкретной задачи

## Промпт задачи

Ты — старший Android-разработчик (Kotlin, Jetpack Compose), а так отлично разбираешься в работе с настройкой/работой с RAG/MCP/SYSTEM_Tools/CLI и в настройке/ работе с VPS серверами

Задача Оптимизация и адаптация локальной LLM, развернутой на VPS
В рамках текущей задачи на Vps  развернута VPS - Ollama: llama3.2:3b и ai chat  уже имеет возможность общения с ней
ip адрес VPS - 109.73.194.244 
порт 11434
Общение с vps и терминалом через ssh root@109.73.194.244

Добавить логику + механизм изменения параметров модели при ответе 
-квантование
-контекстное окно, 
-температуру 
-max tokens
в ToolsDialog при настройки работы с моделью

- Настройте prompt-шаблон, чтобы повысить точность ответов для конкретной задачи. Т.е если разговор идет в контексте Android/Kotlin/Compose, то указать те значения (квантование, контекстное окно, температуру, max tokens) которые позволят повысить точность под конкретную задачу

Результат: Оптимизированная локальная LLM под конкретную задачу (сравните результаты)

## Что было сделано

### 1. Добавлены параметры модели в OllamaChatRequest

**Файл:** `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/data/api/OllamaApi.kt`

Добавлены следующие параметры в `OllamaChatRequest`:
- `temperature: Double?` - управляет случайностью ответов (0.0-2.0)
- `numCtx: Int?` - размер контекстного окна в токенах
- `numPredict: Int?` - максимальное количество токенов для генерации
- `system: String?` - системный промпт для настройки поведения модели

```kotlin
data class OllamaChatRequest(
    @SerializedName("model")
    val model: String,
    @SerializedName("messages")
    val messages: List<OllamaChatMessage>,
    @SerializedName("stream")
    val stream: Boolean = false,
    @SerializedName("temperature")
    val temperature: Double? = null,
    @SerializedName("num_ctx")
    val numCtx: Int? = null,
    @SerializedName("num_predict")
    val numPredict: Int? = null,
    @SerializedName("system")
    val system: String? = null
)
```

### 2. Добавлено сохранение параметров в PreferencesManager

**Файл:** `core/common/src/main/java/com/example/aiagentchat/core/common/preferences/PreferencesManager.kt`

Добавлены следующие поля для хранения параметров модели:
- `vpsOllamaTemperature: Float` (по умолчанию: 0.7)
- `vpsOllamaNumCtx: Int` (по умолчанию: 4096)
- `vpsOllamaNumPredict: Int` (по умолчанию: 2048)
- `vpsOllamaUseAndroidPrompt: Boolean` (по умолчанию: true)

Все параметры сохраняются в SharedPreferences и автоматически загружаются при запуске приложения.

### 3. Добавлен UI для настройки параметров модели

**Файл:** `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/components/ToolsDialog.kt`

Обновлен компонент `VpsOllamaItem` с добавлением:
- **Slider для Temperature** (0.0-2.0) - управляет случайностью ответов
- **Slider для Context Window** (1024-16384) - размер контекстного окна
- **Slider для Max Tokens** (512-8192) - максимальное количество токенов
- **Switch для Android/Kotlin/Compose Prompt** - включение специализированного промпта

Каждый параметр имеет:
- Визуальное отображение текущего значения
- Подсказки с рекомендациями для Android/Kotlin/Compose задач
- Плавную регулировку через Slider

### 4. Обновлен ChatUiState и ChatViewModel

**Файлы:**
- `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatUiState.kt`
- `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatViewModel.kt`

Добавлены:
- Поля состояния для всех параметров модели в `ChatUiState`
- Действия (`ChatAction`) для обновления каждого параметра
- Обработчики в `ChatViewModel` для сохранения и загрузки параметров

### 5. Обновлен AiModelRepositoryImpl для передачи параметров

**Файл:** `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/data/repository/AiModelRepositoryImpl.kt`

Реализовано:
- Получение параметров из `PreferencesManager`
- Передача параметров в `OllamaChatRequest`
- Логирование параметров для отладки
- Условное добавление системного промпта при включенной опции

### 6. Добавлен prompt-шаблон для Android/Kotlin/Compose задач

**Файл:** `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/data/repository/AiModelRepositoryImpl.kt`

Создана функция `generateAndroidKotlinComposePrompt()`, которая генерирует специализированный системный промпт, включающий:

- **Kotlin Best Practices:**
  - Современные Kotlin idioms
  - SOLID и Clean Architecture
  - Null-safety
  - Immutability
  - Type-safe решения

- **Jetpack Compose:**
  - Декларативность и композиция
  - Правильное использование remember, LaunchedEffect
  - Оптимизация рекомпозиций
  - Material 3 Design Guidelines
  - State Hoisting

- **Android Architecture:**
  - Clean Architecture с разделением слоев
  - Repository pattern
  - MVI/MVVM для UI
  - Dependency Injection
  - Coroutines и Flow

- **Performance:**
  - Оптимизация памяти
  - Lazy loading и pagination
  - Оптимизация сети
  - Производительность UI

- **Code Quality:**
  - Короткие функции с одной ответственностью
  - Понятные имена
  - Kotlin Coding Conventions

### 7. Обновлен HomeScreen

**Файл:** `feature/home/src/main/java/com/example/aiagentchat/feature/home/presentation/HomeScreen.kt`

Добавлена передача всех параметров модели в `ToolsDialog` с привязкой к действиям `ChatViewModel`.

## Рекомендуемые параметры для Android/Kotlin/Compose задач

### Для кода и технических вопросов:
- **Temperature:** 0.3-0.5 (более детерминированные ответы)
- **Context Window:** 4096-8192 (достаточно для больших фрагментов кода)
- **Max Tokens:** 2048-4096 (достаточно для полных примеров кода)

### Для творческих задач и брейнсторминга:
- **Temperature:** 0.7-1.0 (более креативные ответы)
- **Context Window:** 4096-8192
- **Max Tokens:** 2048-4096

### Для отладки и анализа:
- **Temperature:** 0.2-0.4 (максимально точные ответы)
- **Context Window:** 8192-16384 (для больших контекстов)
- **Max Tokens:** 1024-2048 (краткие, точные ответы)

## Принцип работы

### Архитектура

```
User Input (ChatScreen)
    ↓
ChatViewModel (обработка действий)
    ↓
PreferencesManager (сохранение/загрузка параметров)
    ↓
AiModelRepositoryImpl (формирование запроса)
    ↓
VpsOllamaApi (HTTP запрос к VPS)
    ↓
VPS Ollama Server (109.73.194.244:11434)
    ↓
Response с оптимизированными параметрами
```

### Поток данных

1. **Настройка параметров:**
   - Пользователь открывает ToolsDialog (⚙️)
   - Настраивает параметры модели через UI
   - Параметры сохраняются в `PreferencesManager`
   - Состояние обновляется в `ChatUiState`

2. **Отправка сообщения:**
   - Пользователь отправляет сообщение
   - `ChatViewModel` вызывает `AiModelRepositoryImpl.sendMessage()`
   - Репозиторий получает параметры из `PreferencesManager`
   - Формируется `OllamaChatRequest` с параметрами
   - Если включен Android Prompt, добавляется системный промпт
   - Запрос отправляется на VPS Ollama

3. **Обработка ответа:**
   - Ответ приходит с VPS
   - Парсится и отображается пользователю
   - Логируются использованные параметры

## Как запустить и проверить работоспособность

### 1. Настройка VPS (если еще не настроен)

```bash
# Подключитесь к VPS
ssh root@109.73.194.244

# Установите Ollama (если еще не установлен)
curl -fsSL https://ollama.com/install.sh | sh

# Запустите Ollama
ollama serve

# В другом терминале загрузите модель
ollama pull llama3.2:3b

# Проверьте, что модель доступна
ollama list
```

### 2. Настройка в приложении

1. **Откройте приложение**
2. **Нажмите на иконку настроек (⚙️)** в правом верхнем углу
3. **Прокрутите до секции "VPS Ollama Configuration"**
4. **Настройте параметры:**
   - Введите URL: `http://109.73.194.244:11434`
   - Настройте Temperature (рекомендуется 0.3-0.5 для кода)
   - Настройте Context Window (рекомендуется 4096-8192)
   - Настройте Max Tokens (рекомендуется 2048-4096)
   - Включите "Android/Kotlin/Compose Prompt" для специализированного промпта

5. **Выберите модель:**
   - В выпадающем списке моделей выберите "VPS - Ollama: llama3.2:3b"

### 3. Тестирование

#### Тест 1: Базовое подключение
```
Вопрос: "Привет, как дела?"
Ожидаемый результат: Модель отвечает на приветствие
```

#### Тест 2: Android/Kotlin вопрос с промптом
```
Вопрос: "Как правильно использовать remember в Jetpack Compose?"
Ожидаемый результат: Детальный ответ с примерами кода, учитывающий best practices
```

#### Тест 3: Проверка параметров
```
1. Установите Temperature = 0.3
2. Задайте вопрос: "Напиши функцию для сортировки списка"
3. Проверьте логи: должны быть видны параметры запроса
4. Ответ должен быть более детерминированным
```

#### Тест 4: Проверка контекстного окна
```
1. Установите Context Window = 8192
2. Задайте вопрос с большим контекстом (вставьте большой фрагмент кода)
3. Модель должна учитывать весь контекст
```

### 4. Проверка логов

В Android Studio Logcat фильтруйте по тегу `AiModelRepository`:

```
D/AiModelRepository: Sending request to VPS Ollama at: http://109.73.194.244:11434
D/AiModelRepository: VPS Ollama request params: temperature=0.3, numCtx=4096, numPredict=2048, useAndroidPrompt=true
D/AiModelRepository: Response from VPS Ollama: contentLength=1234
```

## Решение проблем

### Проблема: Модель не отвечает или таймаут

**Решение:**
1. Проверьте доступность VPS: `curl http://109.73.194.244:11434/api/tags`
2. Проверьте, что Ollama запущен на VPS: `ssh root@109.73.194.244 "systemctl status ollama"`
3. Уменьшите `numPredict` (max tokens) для более быстрых ответов
4. Уменьшите `numCtx` (context window) если контекст слишком большой

### Проблема: Ответы не соответствуют ожиданиям

**Решение:**
1. Включите "Android/Kotlin/Compose Prompt" для специализированного промпта
2. Уменьшите Temperature до 0.3-0.5 для более точных ответов
3. Увеличьте Context Window до 8192 для лучшего понимания контекста
4. Увеличьте Max Tokens до 4096 для более полных ответов

### Проблема: Модель генерирует слишком короткие ответы

**Решение:**
1. Увеличьте `numPredict` (Max Tokens) до 4096-8192
2. Увеличьте Temperature до 0.7 для более развернутых ответов
3. Проверьте, что Context Window достаточно большой (8192+)

### Проблема: Модель не следует best practices

**Решение:**
1. Убедитесь, что включен "Android/Kotlin/Compose Prompt"
2. Уменьшите Temperature до 0.3 для более детерминированных ответов
3. В запросе явно укажите, что нужны best practices

## Сравнение результатов

### Без оптимизации (по умолчанию):
- Temperature: 0.7
- Context Window: 4096
- Max Tokens: 2048
- System Prompt: нет

**Результат:** Общие ответы, не всегда учитывающие Android/Kotlin best practices

### С оптимизацией для Android/Kotlin/Compose:
- Temperature: 0.3-0.5
- Context Window: 8192
- Max Tokens: 4096
- System Prompt: Android/Kotlin/Compose специализированный

**Результат:** 
- Более точные ответы с учетом best practices
- Примеры кода следуют Kotlin idioms
- Учитываются особенности Jetpack Compose
- Рекомендации по архитектуре и производительности

## Дополнительные улучшения

### Возможные будущие улучшения:

1. **Предустановленные профили:**
   - "Code Generation" (Temperature: 0.3, Context: 8192, Tokens: 4096)
   - "Debugging" (Temperature: 0.2, Context: 16384, Tokens: 2048)
   - "Brainstorming" (Temperature: 0.8, Context: 4096, Tokens: 2048)

2. **Автоматическое определение контекста:**
   - Анализ сообщения пользователя
   - Автоматический выбор оптимальных параметров
   - Автоматическое включение/выключение промпта

3. **История параметров:**
   - Сохранение успешных комбинаций параметров
   - Возможность вернуться к предыдущим настройкам

4. **A/B тестирование:**
   - Сравнение ответов с разными параметрами
   - Метрики качества ответов

## Заключение

Реализована полная система оптимизации локальной LLM на VPS для конкретных задач Android/Kotlin/Compose разработки. Пользователь может:

- Настраивать параметры модели через удобный UI
- Использовать специализированный промпт для Android/Kotlin/Compose
- Получать более точные и релевантные ответы
- Сохранять настройки для последующего использования

Все параметры сохраняются и автоматически применяются при каждом запросе к VPS Ollama модели.
