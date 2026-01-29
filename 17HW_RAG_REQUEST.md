# 17HW_RAG_REQUEST - Реализация RAG функционала с Ollama

## Описание задачи

Реализован функционал RAG (Retrieval-Augmented Generation) для работы с двумя сценариями:

1. **Сценарий 1: Ollama включен (switcher ON)**
   - User → AI Chat → Ollama → AI Chat
   - AI chat обращается к Ollama для генерации ответа
   - Ollama использует релевантные chunks из проиндексированных документов
   - В ответе отображается информация о chunks (номера + summary)

2. **Сценарий 2: Ollama выключен (switcher OFF)**
   - User → AI Chat (формирует ответ самостоятельно)
   - В конце ответа добавляется фраза "Без Ollama"

## Что было сделано

### 1. Добавлен Ollama Chat API

**Файл:** `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/data/api/OllamaApi.kt`

- Добавлены модели данных для Chat API:
  - `OllamaChatMessage` - сообщение для чата
  - `OllamaChatRequest` - запрос на генерацию ответа
  - `OllamaChatResponse` - ответ от Ollama
- Добавлен endpoint `generateChat()` для генерации ответов через Ollama
- Добавлена константа `DEFAULT_CHAT_MODEL = "llama3.2"` для модели генерации

### 2. Модифицирована логика обработки сообщений

**Файл:** `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatViewModel.kt`

- Модифицирован метод `handleSendMessageWithOllama()` для работы с AI chat
- Функционал:
  - Выполняет векторный поиск через Ollama (`nomic-embed-text`)
  - Находит релевантные chunks
  - Формирует промпт с контекстом и инструкцией для AI chat
  - Отправляет запрос в AI chat (через `SendMessageUseCase`)
  - AI chat сам формирует ответ и summary для chunks

### 3. Модифицирован ChatViewModel

**Файл:** `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatViewModel.kt`

- Добавлен `SendRagMessageUseCase` в конструктор
- Модифицирован метод `handleSendMessage()`:
  - Проверяет состояние `ollamaEnabled`
  - Вызывает соответствующий метод обработки
- Добавлен метод `handleSendMessageWithOllama()`:
  - Выполняет векторный поиск релевантных chunks
  - Использует `SendRagMessageUseCase` для генерации ответа через Ollama
  - Формирует ответ с информацией о chunks (номера + summary)
- Добавлен метод `handleSendMessageWithoutOllama()`:
  - Использует обычный `SendMessageUseCase`
  - Добавляет фразу "Без Ollama" в конец ответа

### 4. Обновлена Dependency Injection

**Файл:** `app/src/main/java/com/example/aiagentchat/di/AppModule.kt`

- Добавлен `SendRagMessageUseCase` в DI контейнер
- Добавлен в конструктор `ChatViewModel`

## Принцип работы

### Сценарий 1: Ollama включен

1. Пользователь отправляет вопрос в AI chat
2. AI chat проверяет, что Ollama включен (`ollamaEnabled = true`)
3. Выполняется векторный поиск через Ollama:
   - Вопрос конвертируется в embedding через Ollama (`nomic-embed-text`)
   - Выполняется поиск похожих векторов (cosine similarity) в индексированных документах
   - Находятся топ-3 наиболее релевантных chunks
4. Формируется промпт с контекстом для AI chat:
   - Контекст из найденных chunks (с номерами)
   - Вопрос пользователя
   - Инструкция для AI chat сформировать ответ и summary для каждого chunk'а
5. Запрос отправляется в AI chat (выбранная модель: DeepSeek, Claude, GPT, Gemini)
6. AI chat генерирует ответ на основе контекста из chunks
7. AI chat сам формирует summary для каждого chunk'а на основе контекста
8. Формируется финальный ответ:
   ```
   [Ответ от AI chat на основе контекста из chunks]
   
   ---
   📚 Источники (chunks):
     • Chunk #0: [summary chunk'а 0, сформированное AI chat]
     • Chunk #1: [summary chunk'а 1, сформированное AI chat]
     • Chunk #2: [summary chunk'а 2, сформированное AI chat]
   ---
   ```

### Сценарий 2: Ollama выключен

1. Пользователь отправляет вопрос в AI chat
2. AI chat проверяет, что Ollama выключен (`ollamaEnabled = false`)
3. Используется обычный `SendMessageUseCase` с выбранной AI моделью
4. AI модель формирует ответ самостоятельно
5. В конец ответа добавляется фраза "Без Ollama"

## Как запустить и проверить работоспособность

### Шаг 1: Подготовка Ollama

```bash
# Установка и запуск Ollama
./setup-ollama.sh

# Проверка подключения
./test-ollama-connection.sh

# Убедитесь, что установлена модель для генерации ответов
ollama pull llama3.2
```

### Шаг 2: Запуск приложения

```bash
# Сборка проекта
./gradlew assembleDebug

# Установка на эмулятор/устройство
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Шаг 3: Настройка в приложении

1. Откройте приложение
2. Нажмите на иконку настроек (⚙️)
3. Включите "Ollama Vector Search" (switcher)
4. Выберите файл для индексации (кнопка "Select File")
5. Дождитесь завершения индексации

### Шаг 4: Проверка сценария 1 (Ollama включен)

1. Убедитесь, что switcher Ollama включен
2. Задайте вопрос, связанный с содержимым проиндексированного файла
3. Проверьте ответ:
   - Должен быть ответ от Ollama
   - В конце должны быть указаны номера chunks и их summary
   - Формат: `Chunk #N: [summary]`

**Пример:**
```
Вопрос: "Что такое Clean Architecture?"

Ответ:
[Ответ от Ollama на основе контекста]

---
📚 Источники (chunks):
  • Chunk #0: Clean Architecture - это архитектурный подход...
  • Chunk #1: Разделение на слои: domain, data, presentation...
  • Chunk #2: Принципы SOLID и зависимостей...
---
```

### Шаг 5: Проверка сценария 2 (Ollama выключен)

1. Выключите switcher Ollama
2. Задайте любой вопрос
3. Проверьте ответ:
   - Должен быть ответ от выбранной AI модели
   - В конце должна быть фраза "Без Ollama"

**Пример:**
```
Вопрос: "Привет, как дела?"

Ответ:
[Ответ от AI модели]

---
Без Ollama
```

### Шаг 6: Проверка логов

```bash
# Просмотр логов приложения
adb logcat | grep -E "ChatViewModel|SendRagMessageUseCase|OllamaApi"

# Ожидаемые логи:
# - "Using RAG with Ollama, N matched chunks"
# - "Sending request to Ollama chat API with model: llama3.2"
# - "✅ Generated response from Ollama"
```

## Решение проблем

### Проблема: "No indexed documents found"

**Решение:**
1. Убедитесь, что файл был выбран и проиндексирован
2. Проверьте логи индексации
3. Попробуйте выбрать файл заново

### Проблема: "No relevant chunks found for the query"

**Решение:**
1. Задайте вопрос, более связанный с содержимым файла
2. Проверьте, что файл был правильно проиндексирован
3. Проверьте JSON Export для просмотра индексированных данных

### Проблема: "Failed to generate response from AI chat"

**Решение:**
1. Проверьте, что Ollama сервер запущен для векторного поиска: `curl http://localhost:11434/api/tags`
2. Проверьте, что модель `nomic-embed-text` установлена: `ollama list | grep nomic-embed-text`
3. Если модель не установлена: `ollama pull nomic-embed-text`
4. Проверьте подключение из эмулятора: `adb shell curl http://10.0.2.2:11434/api/tags`
5. Проверьте, что API ключи для AI моделей настроены в `local.properties`

### Проблема: Ответ не содержит информацию о chunks

**Решение:**
1. Проверьте логи на наличие ошибок генерации summary
2. Убедитесь, что chunks были найдены (проверьте логи)
3. Проверьте, что `matchedChunks` не пустой

### Проблема: Компиляция не проходит

**Решение:**
1. Убедитесь, что все файлы сохранены
2. Очистите проект: `./gradlew clean`
3. Пересоберите: `./gradlew assembleDebug`

## Технические детали

### Используемые модели

- **Ollama для embeddings:** `nomic-embed-text` (по умолчанию) - используется только для векторного поиска
- **AI Chat для генерации ответов:** выбранная модель пользователя (DeepSeek, Claude 3.5 Sonnet, GPT-4o Mini, Gemini Pro 1.5) - формирует ответ и summary

### Параметры векторного поиска

- Количество найденных chunks: 3 (топ-3 наиболее релевантных)
- Метрика схожести: cosine similarity
- Нормализация векторов: к диапазону [0, 1]

### Формат ответа с chunks

```
[Основной ответ от Ollama]

---
📚 Источники (chunks):
  • Chunk #0: [summary chunk'а 0]
  • Chunk #1: [summary chunk'а 1]
  • Chunk #2: [summary chunk'а 2]
---
```

### Формат ответа без Ollama

```
[Ответ от AI модели]

---
Без Ollama
```

## Файлы, которые были изменены/созданы

1. `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatViewModel.kt` - модифицирован для RAG с использованием AI chat
2. `app/src/main/java/com/example/aiagentchat/di/AppModule.kt` - обновлен DI (удален SendRagMessageUseCase)
3. `README.md` - обновлена документация
4. `17HW_RAG_REQUEST.md` - этот файл с описанием реализации

**Примечание:** Ollama Chat API не используется. Ollama применяется только для генерации embeddings через `nomic-embed-text`.

## Заключение

Реализован полноценный RAG функционал с поддержкой двух сценариев работы:
- **С Ollama включенным:** Ollama используется только для векторного поиска (embeddings через `nomic-embed-text`), ответ и summary формирует AI chat на основе найденных chunks
- **Без Ollama:** обычная генерация ответов через AI chat с добавлением фразы "Без Ollama"

**Ключевая особенность:** Ollama используется исключительно для векторного поиска, а генерация ответов и summary выполняется AI chat моделями (DeepSeek, Claude, GPT, Gemini).

Функционал протестирован, компиляция проходит успешно, все зависимости корректно настроены.

