# 23HW_PROJECT_TEAM_ASSISTENT.md

## Промпт задачи

Ты — старший Android-разработчик (Kotlin, Jetpack Compose), а так отлично разбираешься в работе с настройкой/работой с RAG/MCP/SYSTEM_Tools

Задача Ассистент команды. На основании текущего проекта, сделать подобие ассистента команды, в чате, по команде /tasks, на основании файлов(расширения .kt, .xml, .java, .kts, .sh) из текущего проекта(его кодовой базы). Т.о на основании проблемных мест в проекте(приоритность проблем указывается на основании правил для разработки, закрепленных ниже) ai chat  должен составить сжатого вида технические задачи, в формате:
Название:  не более 300 токенов
Источник проблемы: не более 300 токенов
Описание: не более 1000 токенов
Ожидаемый результат: не более 500 токенов (какие правила должны соблюдаться)

И отдать их Юзеру. Не более 3 задач. В следующем порядке 1 критическая, 1 важная 1 обычная

Добавить в ToolsDialog функционал switcher Project Team Assistant,  свитчер для включения локального mcp сервера, для работы по принципу Ассистента команды, работает в связке с другими mcp серверами и rag моделями (описаннми ниже).

Удалить свитчер Project Review Mode 
Свитчер Project helper(если включен) переименовать на Project Review Mode. Расширить логику нового Project Review Mode согласно логике старого Project Review Mode, т.е. иметь возможность работать с файлами(расширения .kt, .xml, .java, .kts, .sh) из текущего проекта(его кодовой базы)

Удалить весь код/файлы/текст связанный с возможностью загрузки файлов извне (т.е юзером)
Удалить весь код/файлы/текст связанный с возможностью загрузки файлов извне (т.е юзером)

Реструктуризировать Иерархию Ollama Vector Search свитчера
1) GitHub MCP
2)Ollama Vector Search (по умолчанию embedding)
 - свитчер Reranking
 - свитчер Project Review Mode "НОВЫЙ" (порядок работы моделей embedding -> reranking(если включен) ->  GitHub MCP (если включен) -> Project Review Mode
 - свитчер Project Team Asistant (порядок работы моделей embedding -> reranking(если включен) ->  GitHub MCP (если включен) -> Project helper(если включен) -> Project Review Mode(если включен) -> Project Team Asistant


Т.о если Ollama Vector Search  включен то все свитчеры внутри нее уже имеют возможность работать с embedding моделью 
 
Добавить функционал при котором, при нажатии на значок мусорка(стирание всех данных чата), все тоглы в Tools Dialog так же приходят к состоянию по умолчанию, т.е. false(включая данные pref)

Краткое описание логики работы функционала Ollama Vector Search при включенном тогл Project Team Asistant(и всех опциональных для него)

1)При включении Ollama Vector Search + Project Review Mode (новый),  производится индексация файлов указанного проекта (согласно логике Ollama Vector Search). 
- Получение эмбеддингов из файлов(расширения .kt, .xml, .java, .kts, .sh) из текущего проекта(его кодовой базы) 
- Формирование чанк-кандидатов

2)При запросе пользователя "/tasks" начинает работу Reranking через LLM-as-a-reranker(если включен свитчер Reranking)
- происходит поиск по косинусному сходству (в текущей реализации происходит поиск проблемных мест в проекте, которые необходимо срочно править
- для каждого чанк - кандидата, формируется промпт для оценки релевантности (от 0 до 1).

3)Полученные ответы и отсортируй их по убыванию( где 1 это максимальная релевантность), отдать 3 самых релевантный чанк

4)Отдать ответ ai-chat

5) ai chat должен описать ответ в формате  созданной задачи, вида:
Название:  не более 300 токенов
Источник проблемы: не более 300 токенов
Описание: не более 1000 токенов
Ожидаемый результат: не более 500 токенов (какие правила должны соблюдаться)

Правила для разработки 
-memory leaks
-crash's 
-non security values
-clean architecture
-non thread safe logic

## Что было сделано

### 1. Удален код загрузки файлов извне

- ✅ Удалены `SelectOllamaFile` и `RemoveOllamaFile` из `ChatAction`
- ✅ Удалены обработчики `handleSelectOllamaFile` и `handleRemoveOllamaFile` из `ChatViewModel`
- ✅ Удален `startIndexingForFile` из `ChatViewModel`
- ✅ Удалены `filePickerLauncher` и `storagePermissionLauncher` из `HomeScreen`
- ✅ Удалена функция `getFilePathFromUri` из `HomeScreen`
- ✅ Удалены неиспользуемые импорты из `HomeScreen`
- ✅ Удалены ссылки на `ollamaSelectedFiles` из `ChatViewModel` и `PreferencesManager`

### 2. Реструктуризирован ToolsDialog

- ✅ Удален отдельный свитчер "Project Review Mode" (теперь он внутри Ollama Vector Search)
- ✅ Переименован "Project Helper" в "Project Review Mode" (логика сохранена)
- ✅ Добавлен "Project Team Assistant" внутри Ollama Vector Search
- ✅ Добавлен "Local MCP Server" внутри Project Team Assistant
- ✅ Обновлена иерархия:
  1. GitHub MCP (верхний уровень)
  2. Ollama Vector Search
     - Reranking
     - Project Review Mode
     - Project Team Assistant
       - Local MCP Server

### 3. Обновлен PreferencesManager

- ✅ Удален `projectHelperEnabled` (заменен на `projectReviewModeEnabled`)
- ✅ Удален `ollamaSelectedFiles` (больше не нужен)
- ✅ Добавлен метод `resetAllToggles()` для сброса всех тогглов

### 4. Добавлена команда /tasks

- ✅ Реализована команда `/tasks` в `ChatViewModel`
- ✅ Логика поиска проблемных мест через embedding + reranking
- ✅ Генерация задач в формате:
  - Название (max 300 tokens)
  - Источник проблемы (max 300 tokens)
  - Описание (max 1000 tokens)
  - Ожидаемый результат (max 500 tokens)
- ✅ Приоритеты: 1 критическая, 1 важная, 1 обычная

### 5. Реализована логика Project Team Assistant

- ✅ Индексация файлов проекта при включении
- ✅ Поиск проблемных мест через embedding
- ✅ Reranking через LLM (если включен)
- ✅ Генерация задач через AI chat

### 6. Добавлен сброс всех тогглов при ClearChat

- ✅ При очистке чата все тогглы сбрасываются в `false`
- ✅ Состояние сохраняется в `PreferencesManager`

### 7. Обновлен ChatViewModel

- ✅ Удален старый код Project Helper
- ✅ Переименован `handleSendMessageWithProjectHelper` в `handleSendMessageWithProjectReviewMode`
- ✅ Добавлены обработчики `handleToggleProjectTeamAssistant` и `handleToggleLocalMcpServer`
- ✅ Добавлена функция `indexProjectFilesForTeamAssistant`
- ✅ Добавлена функция `executeTasksCommand`

### 8. Обновлен HomeScreen

- ✅ Удален UI загрузки файлов
- ✅ Обновлены вызовы `ToolsDialog` с новыми параметрами

## Как запустить

### Предварительные требования

1. **Запустите Ollama сервер:**
   ```bash
   ./setup-ollama.sh
   ```

2. **Запустите Project Helper MCP Server:**
   ```bash
   cd project-helper-mcp-server
   ./start-server.sh [PORT] [PROJECT_ROOT] [OLLAMA_URL]
   
   # Пример (по умолчанию):
   ./start-server.sh 8081 /Users/Victor/work/hw-1-AI-chat-bot http://localhost:11434
   ```

3. **Запустите приложение на Android устройстве/эмуляторе**

### Использование Project Team Assistant

1. **Включите Ollama Vector Search:**
   - Откройте настройки (⚙️)
   - Включите "Ollama Vector Search"

2. **Включите Project Team Assistant:**
   - В настройках включите "Project Team Assistant" (внутри Ollama Vector Search)
   - При включении автоматически начнется индексация файлов проекта (.kt, .xml, .java, .kts, .sh)

3. **Опционально включите дополнительные функции:**
   - **Reranking**: Для улучшения качества поиска проблемных мест
   - **GitHub MCP**: Для доступа к PR diffs и файлам через GitHub API
   - **Project Review Mode**: Для работы с файлами проекта в обычных запросах
   - **Local MCP Server**: Для работы Project Team Assistant с локальным MCP сервером

4. **Используйте команду /tasks:**
   - В чате введите `/tasks`
   - AI проанализирует проект и сгенерирует 3 технические задачи:
     - 1 критическая
     - 1 важная
     - 1 обычная

## Принцип работы

### Flow выполнения команды /tasks

```
1. Пользователь вводит "/tasks"
   ↓
2. ChatViewModel.executeTasksCommand()
   ↓
3. Проверка: Ollama Vector Search + Project Team Assistant должны быть включены
   ↓
4. Если Project Team Assistant включен впервые:
   - indexProjectFilesForTeamAssistant()
   - Project Helper MCP Server индексирует файлы проекта
   ↓
5. Поиск проблемных мест:
   - Query: "Find problematic code areas related to: memory leaks, crashes, non-security values, clean architecture violations, non-thread-safe logic"
   - Project Helper MCP Server выполняет поиск через embedding
   ↓
6. Если Reranking включен:
   - LLM (phi3:medium) оценивает релевантность каждого чанка (0.0-1.0)
   - Чанки сортируются по убыванию релевантности
   - Выбираются топ-3 самых релевантных чанка
   ↓
7. Формирование промпта для AI:
   - Добавляется контекст с проблемными местами
   - Указываются правила для разработки
   - Запрашивается генерация 3 задач в формате
   ↓
8. AI генерирует ответ с задачами:
   - Название (max 300 tokens)
   - Источник проблемы (max 300 tokens)
   - Описание (max 1000 tokens)
   - Ожидаемый результат (max 500 tokens)
   ↓
9. Ответ отображается пользователю
```

### Индексация файлов проекта

При включении Project Team Assistant автоматически запускается индексация:

1. **Поиск файлов проекта:**
   - Рекурсивный обход директории проекта
   - Фильтрация по расширениям: .kt, .xml, .java, .kts, .sh
   - Игнорирование: build, .git, node_modules, .gradle, .idea, .cursormcp, .kotlin

2. **Разбиение на чанки:**
   - Размер чанка: 300 токенов
   - Перекрытие: 30 токенов

3. **Генерация embeddings:**
   - Используется Ollama `nomic-embed-text`
   - Каждый чанк обрезается до 2000 символов перед embedding
   - Embeddings сохраняются в кэше MCP сервера

### Правила для разработки

AI ищет проблемные места по следующим правилам:

- **Memory leaks**: Утечки памяти (незакрытые ресурсы, циклические ссылки)
- **Crashes**: Потенциальные краши (null pointer exceptions, array out of bounds)
- **Non security values**: Небезопасные значения (хардкод паролей, API ключей)
- **Clean architecture**: Нарушения чистой архитектуры (зависимости между слоями)
- **Non thread safe logic**: Непотокобезопасная логика (race conditions, shared mutable state)

## Решение проблем

### Проблема: "Project Team Assistant requires Ollama Vector Search to be enabled"

**Решение:** Включите "Ollama Vector Search" в настройках перед включением "Project Team Assistant"

### Проблема: "Indexing failed: HTTP 500"

**Решение:** 
1. Проверьте, что Project Helper MCP Server запущен
2. Проверьте, что Ollama сервер доступен
3. Проверьте правильность пути к проекту в MCP сервере

### Проблема: "No problematic code areas found"

**Решение:** 
1. Убедитесь, что в проекте есть файлы с расширениями .kt, .xml, .java, .kts, .sh
2. Проверьте, что индексация завершилась успешно
3. Попробуйте включить Reranking для улучшения качества поиска

### Проблема: Тогглы не сбрасываются при очистке чата

**Решение:** 
1. Убедитесь, что используется последняя версия кода
2. Проверьте, что `handleClearChat()` вызывает `preferencesManager.resetAllToggles()`

## Файлы изменены

- `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/components/ToolsDialog.kt`
- `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatViewModel.kt`
- `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatUiState.kt`
- `feature/home/src/main/java/com/example/aiagentchat/feature/home/presentation/HomeScreen.kt`
- `core/common/src/main/java/com/example/aiagentchat/core/common/preferences/PreferencesManager.kt`

## Итоговая проверка

- ✅ Удален весь код загрузки файлов извне
- ✅ Реструктуризирован ToolsDialog согласно требованиям
- ✅ Добавлена команда /tasks
- ✅ Реализована логика Project Team Assistant
- ✅ Добавлен сброс всех тогглов при ClearChat
- ✅ Обновлены все связанные файлы
- ✅ Протестирована компиляция проекта
- ✅ Создана документация
