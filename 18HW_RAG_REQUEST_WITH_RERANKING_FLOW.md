# 18HW_RAG_REQUEST_WITH_RERANKING_FLOW - Реализация RAG с LLM-as-a-Reranker

## Описание задачи

Реализован функционал RAG (Retrieval-Augmented Generation) с использованием LLM-as-a-Reranker для улучшения качества поиска релевантных фрагментов документов. Reranking выполняется через LLM (`phi3:medium`), который оценивает релевантность каждого кандидата к запросу пользователя.

## Что было сделано

### 1. Реализован LLM-as-a-Reranker

**Файл:** `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatViewModel.kt`

- Добавлена функция `performLlmReranking()` - выполняет reranking через LLM для всех кандидатов
- Добавлена функция `evaluateRelevanceWithLlm()` - оценивает релевантность каждого чанка через phi3:medium
- Используется модель `phi3:medium` для оценки релевантности
- Промпт для оценки: 
  ```
  Оцени релевантность текста запросу по шкале от 0.0 до 1.0.
  Запрос: "[запрос пользователя]"
  Текст: "[текст чанка]"
  Ответь текст + релевантность текста в виде "Релевантность число". Никаких пояснений.
  ```
- Парсинг ответа LLM для извлечения оценки релевантности (0.0-1.0)
- Сортировка чанков по оценке релевантности по убыванию (1.0 = максимальная релевантность)
- Выбор топ-3 самых релевантных чанков

### 2. Обновлен OllamaApi

**Файл:** `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/data/api/OllamaApi.kt`

- Добавлена константа `DEFAULT_RERANKING_MODEL = "phi3:medium"` для модели reranking

### 3. Обновлена логика RAG запроса

**Файл:** `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatViewModel.kt`

- Модифицирован метод `handleSendMessageWithOllama()`:
  - Определяется выбранный пользователем документ из `ollamaSelectedFile` (сохраняется в PreferencesManager)
  - Имя файла извлекается из пути и сравнивается с именами документов в индексе (без учета регистра)
  - Поиск выполняется только в выбранном документе (или во всех, если не выбран)
  - Выполняется поиск до 10 кандидатов через embeddings (nomic-embed-text)
  - Если reranking включен: используется LLM-as-a-Reranker для оценки всех кандидатов, выбираются топ-3
  - Если reranking выключен: используются все найденные чанки без reranking, выбираются топ-3 по косинусному сходству
  - Добавлено подробное логирование для отладки

### 4. Упрощен UI

**Файл:** `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/components/ToolsDialog.kt`

- Убрано поле для ввода коэффициента похожести
- Оставлен только switcher для включения/выключения reranking
- Добавлено описание: "Reranking uses LLM (phi3:medium) to evaluate relevance of chunks to the query."

### 5. Обновлены модели данных

**Файлы:**
- `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatUiState.kt` - убрано поле `rerankingSimilarityThreshold`
- `core/common/src/main/java/com/example/aiagentchat/core/common/preferences/PreferencesManager.kt` - добавлено поле `ollamaSelectedFile`, убрано поле `rerankingSimilarityThreshold`
- `feature/home/src/main/java/com/example/aiagentchat/feature/home/presentation/HomeScreen.kt` - убраны параметры для коэффициента

## Принцип работы

### Шаг 1: Индексация документа (если еще не проиндексирован)

1. Пользователь выбирает файл для индексации
2. Файл разбивается на чанки (500-700 токенов каждый)
3. Для каждого чанка генерируется embedding через Ollama API (`nomic-embed-text`)
4. Embeddings нормализуются к диапазону [0,1]
5. Данные сохраняются в JSON файл (`vector_index.json`)

### Шаг 2: Поиск кандидатов (Retrieval)

1. Пользователь отправляет запрос в AI chat
2. Запрос конвертируется в embedding через Ollama API (`nomic-embed-text`)
3. Выполняется поиск похожих векторов (cosine similarity) в индексе
4. Находится до 10 наиболее релевантных чанков-кандидатов
5. Если выбран документ, поиск выполняется только в этом документе

### Шаг 3: Reranking через LLM (если включен)

1. Для каждого чанка-кандидата формируется промпт:
   ```
   Оцени релевантность текста запросу по шкале от 0.0 до 1.0.
   Запрос: "[запрос пользователя]"
   Текст: "[текст чанка (до 1000 символов)]"
   Ответь текст + релевантность текста в виде "Релевантность число". Никаких пояснений.
   ```

2. Промпт отправляется в Ollama API с моделью `phi3:medium`
3. LLM возвращает оценку релевантности (0.0-1.0)
4. Оценка парсится из ответа LLM
5. Все чанки сортируются по оценке релевантности по убыванию (1.0 = максимальная релевантность)
6. Выбираются топ-3 чанка с наивысшей оценкой

### Шаг 4: Генерация ответа

1. Контекст из топ-3 чанков передается в AI chat (выбранная модель)
2. В контексте для каждого чанка указывается:
   - Если reranking включен: оценка релевантности (0.0-1.0 и процент)
   - Если reranking выключен: оценка похожести (0.0-1.0 и процент)
3. AI chat формирует ответ на основе контекста
4. AI chat создает summary для каждого чанка
5. В ответе AI chat включает оценку релевантности для каждого чанка (если reranking включен)
6. В конце ответа добавляется:
   - "С Ollama и фильтрацией" - если reranking включен
   - "С Ollama и без фильтрацией" - если reranking выключен

## Как запустить и проверить работоспособность

### Шаг 1: Подготовка Ollama

```bash
# Установка и запуск Ollama
./setup-ollama.sh

# Убедитесь, что установлены обе модели:
ollama pull nomic-embed-text  # Для embeddings
ollama pull phi3:medium       # Для reranking

# Проверка подключения
./test-ollama-connection.sh
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
6. **Включите "Reranking (Filtering)"** (switcher)

### Шаг 4: Проверка сценария 1 (Ollama + Reranking включены)

1. Убедитесь, что оба switcher'а включены (Ollama и Reranking)
2. Выберите документ (если несколько документов проиндексировано)
3. Задайте вопрос, связанный с содержимым проиндексированного файла
4. Проверьте ответ:
   - Должен быть ответ от AI chat
   - В конце должны быть указаны номера chunks, их оценка релевантности и summary
   - Формат: `Chunk #N (Релевантность: X.XX): [summary]`
   - В конце ответа должна быть фраза **"С Ollama и фильтрацией"**
   - Chunks должны быть отранжированы по релевантности через LLM

**Проверка в логах:**
```bash
adb logcat | grep -E "ChatViewModel.*reranking|Reranking|LLM"
```

Ожидаемые логи:
```
🔄 Reranking enabled: using LLM-as-a-reranker (phi3:medium) for N candidate chunks
🔄 Starting LLM reranking for N candidate chunks
Evaluating relevance for chunk 1/N (chunk #X)
✅ Chunk 1: relevance score = 0.85 (chunk #X)
📊 Reranking completed. Top 3 scores: [Chunk #X=0.85, Chunk #Y=0.78, Chunk #Z=0.72]
📌 Selected top 3 chunks: [#X (score=0.85), #Y (score=0.78), #Z (score=0.72)]
Using RAG with Ollama (vector search), 3 matched chunks (after reranking)
Chunk #X: relevance score = 0.85 (85%)
Chunk #Y: relevance score = 0.78 (78%)
Chunk #Z: relevance score = 0.72 (72%)
```

**Ожидаемый формат ответа:**
```
[Ответ от AI chat]

---
📚 Источники (chunks):
  • Chunk #X (Релевантность: 0.85): [summary chunk'а X]
  • Chunk #Y (Релевантность: 0.78): [summary chunk'а Y]
  • Chunk #Z (Релевантность: 0.72): [summary chunk'а Z]
---
С Ollama и фильтрацией
```

### Шаг 5: Проверка сценария 2 (Ollama включен, Reranking выключен)

1. Включите Ollama, но выключите Reranking
2. Выберите документ (если нужно)
3. Задайте вопрос
4. Проверьте ответ:
   - Должен быть ответ от AI chat
   - В конце должна быть фраза **"С Ollama и без фильтрацией"**
   - Используются все найденные chunks без reranking (только по косинусному сходству)

**Проверка в логах:**
```
⏭️ Reranking disabled: using top N matched chunks by cosine similarity
Using RAG with Ollama (vector search), 3 matched chunks (without reranking)
```

### Шаг 6: Проверка сценария 3 (Ollama выключен)

1. Выключите Ollama
2. Задайте любой вопрос
3. Проверьте ответ:
   - Должен быть ответ от выбранной AI модели
   - В конце должна быть фраза **"Без Ollama"**

## Решение проблем

### Проблема: "phi3:medium model not found"

**Решение:**
1. Установите модель: `ollama pull phi3:medium`
2. Проверьте установку: `ollama list | grep phi3`
3. Перезапустите Ollama сервер: `ollama serve`

### Проблема: Reranking не работает (используются все chunks)

**Решение:**
1. Проверьте, что switcher Reranking включен
2. Проверьте логи на наличие сообщений о reranking
3. Убедитесь, что модель phi3:medium установлена
4. Проверьте, что Ollama сервер доступен

### Проблема: Не удается распарсить оценку релевантности

**Решение:**
1. Проверьте логи на ответы от LLM
2. Убедитесь, что модель phi3:medium правильно отвечает на промпт
3. При ошибке парсинга используется среднее значение (0.5)
4. Проверьте формат ответа LLM в логах

### Проблема: Reranking работает медленно

**Решение:**
1. Reranking требует запросов к LLM для каждого кандидата (до 10 запросов)
2. Это нормально - reranking улучшает качество, но требует времени
3. Можно уменьшить количество кандидатов (сейчас до 10)

### Проблема: Поиск выполняется во всех документах, а не в выбранном

**Решение:**
1. Проверьте, что документ выбран (должно отображаться "Selected: [имя файла]" в UI)
2. Убедитесь, что выбранный файл был проиндексирован
3. Проверьте логи на наличие сообщения "Searching in N document(s) (selected: [имя файла])"
4. Если документ не находится, проверьте:
   - Совпадает ли имя файла (сравнение без учета регистра)
   - Был ли файл проиндексирован после выбора
   - Сохранился ли выбранный файл между сессиями (проверьте PreferencesManager)

### Проблема: Интернет не работает на эмуляторе при включенном Ollama

**Решение:**
1. **Проверьте, что используются разные OkHttp клиенты:**
   - Для локальных адресов (10.0.2.2, localhost) используется `localOkHttpClient` с `localDns`
   - Для интернет-запросов используется `internetOkHttpClient` с обычным DNS
   - Они работают независимо и не должны мешать друг другу

2. **Проверьте логи:**
   ```
   Using local OkHttp client (with local DNS resolver) for: http://10.0.2.2:11434
   Using internet OkHttp client (with standard DNS resolver) for: https://api.example.com
   ```

3. **Если интернет все еще не работает:**
   - Проверьте DNS настройки эмулятора: `adb shell getprop net.dns1`
   - Установите DNS вручную: `adb shell "setprop net.dns1 8.8.8.8"`
   - Перезапустите эмулятор с правильными DNS

4. **Важно:** `localDns` используется ТОЛЬКО для локальных адресов и НЕ влияет на интернет-запросы

### Проблема: Файл не разбивается на чанки

**Решение:**
1. **Проверьте, что файл не пустой:**
   - Файл должен содержать текст (не только пробелы)
   - Минимальный размер для разбиения: ~3000 символов (600 токенов)
   - Если файл короче, будет создан один чанк

2. **Проверьте логи:**
   ```
   Text length: [количество] characters
   Split into [количество] chunks
   ```

3. **Если файл не разбивается:**
   - Проверьте кодировку файла (должна быть UTF-8)
   - Убедитесь, что файл читается правильно
   - Проверьте, что файл содержит текст, а не только пробелы

4. **Логика разбиения:**
   - Текст короче 3000 символов → создается один чанк
   - Текст длиннее 3000 символов → разбивается на чанки по ~3000 символов с перекрытием ~300 символов
   - Пустые чанки автоматически пропускаются

## Технические детали

### Модели

- **nomic-embed-text**: Используется для генерации embeddings (индексация и поиск)
- **phi3:medium**: Используется для reranking (оценка релевантности)

### Параметры поиска

- **Максимум кандидатов для reranking:** 10
- **Используется после reranking:** топ-3 chunks
- **Диапазон оценки релевантности:** 0.0-1.0 (Float)
- **Сортировка:** по убыванию (1.0 = максимальная релевантность)

### Формат промпта для reranking

```
Оцени релевантность текста запросу по шкале от 0.0 до 1.0.
Запрос: "[запрос пользователя]"
Текст: "[текст чанка (до 1000 символов)]"
Ответь текст + релевантность текста в виде "Релевантность число". Никаких пояснений.
```

### Парсинг ответа LLM

1. Ищется паттерн "Релевантность число"
2. Если не найден, ищется любое число от 0.0 до 1.0
3. Если не удалось распарсить, используется 0.5 (среднее значение)

### Формат ответа с reranking

```
[Ответ от AI chat на основе отранжированных chunks]

---
📚 Источники (chunks):
  • Chunk #N (Релевантность: X.XX): [summary chunk'а N]
  • Chunk #M (Релевантность: Y.YY): [summary chunk'а M]
---
С Ollama и фильтрацией
```

**Важно:** В ответе отображается оценка релевантности (0.0-1.0) для каждого чанка, полученная от reranking model (phi3:medium).

### Формат ответа без reranking

```
[Ответ от AI chat на основе всех chunks]

---
📚 Источники (chunks):
  • Chunk #N: [summary chunk'а N]
  • Chunk #M: [summary chunk'а M]
---
С Ollama и без фильтрацией
```

### Формат ответа без Ollama

```
[Ответ от AI chat без использования chunks]

---
Без Ollama
```

## Файлы, которые были изменены/созданы

1. `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatViewModel.kt` - добавлен LLM reranking
2. `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/data/api/OllamaApi.kt` - добавлена константа для модели reranking
3. `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/components/ToolsDialog.kt` - упрощен UI
4. `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatUiState.kt` - убрано поле коэффициента
5. `core/common/src/main/java/com/example/aiagentchat/core/common/preferences/PreferencesManager.kt` - добавлено поле `ollamaSelectedFile`, убрано поле коэффициента
6. `feature/home/src/main/java/com/example/aiagentchat/feature/home/presentation/HomeScreen.kt` - убраны параметры коэффициента
7. `README.md` - обновлена документация
8. `18HW_RAG_REQUEST_WITH_RERANKING_FLOW.md` - этот файл с описанием реализации

## Заключение

Реализован полноценный функционал RAG с LLM-as-a-Reranker:

- **Retrieval:** Поиск кандидатов через embeddings (nomic-embed-text)
- **Reranking:** Оценка релевантности через LLM (phi3:medium) для каждого кандидата
- **Selection:** Выбор топ-3 самых релевантных чанков (отсортированы по убыванию, где 1.0 = максимальная релевантность)
- **Generation:** Генерация ответа через AI chat с контекстом из топ-3 чанков

Функционал протестирован, компиляция проходит успешно, все зависимости корректно настроены.
