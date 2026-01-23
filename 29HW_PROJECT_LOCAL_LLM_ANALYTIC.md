# 29HW_PROJECT_LOCAL_LLM_ANALYTIC

## Промпт

Ты — старший Android-разработчик (Kotlin, Jetpack Compose), а так отлично разбираешься в работе с настройкой/работой с RAG/MCP/SYSTEM_Tools. 
Есть проект мобильного приложения Ai-Chat-Bot, может взаимодействовать с:

-со сторонними Ai(deepseek/ gpt-5 и тп)
-с локальной моделью ollama (nomic-embed-text/phi3:medium/llama 3.2b)
-с ремоут/локальными mcp

Требование - на основании самого проекта Ai-Chat-Bot (его кодовой базы) в самом чате Автоматизировать Ассистента, под аналитика кодовой базы

1)Добавить тогл Project Analytic. Актуальное состояние switcher не должно зависить от сессии и должно работать вне нее.
2)Добавить MCP сервер  для работы и взаимодействия с ollama: Lllama 3.2b
3)Добавить возможность напрямую юзеру общаться с моделью ollama: Lllama 3.2b (т.е. в выпадающем списке где все модели)
4)Модели данных не должны хранить контекст общения. Каждый новый вопрос - новый контекст
5)Мсп сервер  работающий с  Lllama 3.2b должен взаимодействовать с ollama vector search на основе логики
6)Ollama Vector Search  и Project Helper работают вместе, и должны иметь возможность отдать инфомрацию по кодовой базе проекта (расширения .md .kt, .xml, .java, .kts, .sh) а так же уметь читать логи проекта
7)При необходимости для сбора критической информации добавить логи в местах где это необходимо
8) Lllama 3.2b, должен по запросу юзера (еслли запрос про проект), получить от Ollama Vector Search и Project Helper информацию по проекту(требуемуму файлу или методу/классу/проблеме или ошибке), затем ее проаналлизировать, проверить остальные места проекте, встречаются ли подобные проблемы или вопрос еще где то и выдать ответ

Дополнительно 
Удалить старый функционалл
-возможность добавлления файлов извне 
-github mcp( и все что с ним связано)
-project review mode( и все что с ним связано)

## Что было сделано

### 1. Создан MCP сервер для Ollama Llama 3.2b

**Файлы:**
- `ollama-mcp-server/src/main/kotlin/com/example/ollamamcpserver/OllamaMcpServer.kt` - основной сервер
- `ollama-mcp-server/build.gradle.kts` - конфигурация сборки
- `ollama-mcp-server/start-server.sh` - скрипт запуска

**Функционал:**
- MCP сервер на порту 8086
- Интеграция с Ollama API для чата с Llama 3.2b
- Автоматическое определение проектных запросов
- Интеграция с Project Helper MCP сервером для получения контекста проекта
- Использование Ollama Vector Search через Project Helper для поиска релевантной информации

**Принцип работы:**
1. При получении запроса проверяется, является ли он проектно-ориентированным
2. Если да, запрашивается контекст из Project Helper через Vector Search
3. Контекст передается в Llama 3.2b вместе с запросом пользователя
4. Модель анализирует контекст и выдает ответ

### 2. Добавлена модель Ollama Llama 3.2b

**Изменения:**
- `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/domain/model/AiModel.kt` - добавлен `OllamaLlama32b`
- `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/data/repository/AiModelRepositoryImpl.kt` - добавлена поддержка через MCP API
- `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/data/AuthManager.kt` - модель не требует API ключа

**Особенности:**
- Модель доступна в списке моделей для выбора
- Работает через Ollama MCP сервер
- Не требует API ключа (всегда доступна, если сервер запущен)

### 3. Добавлен тогл Project Analytic

**Изменения:**
- `core/common/src/main/java/com/example/aiagentchat/core/common/preferences/PreferencesManager.kt` - добавлено `projectAnalyticEnabled`
- `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatUiState.kt` - добавлено поле состояния
- `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatViewModel.kt` - добавлены обработчики
- `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/components/ToolsDialog.kt` - добавлен UI элемент
- `feature/home/src/main/java/com/example/aiagentchat/feature/home/presentation/HomeScreen.kt` - интеграция в UI

**Особенности:**
- Состояние сохраняется в SharedPreferences (не зависит от сессии)
- Переключатель доступен в ToolsDialog
- При включении автоматически используется для проектных запросов

### 4. Реализована логика Project Analytic

**Метод:** `handleSendMessageWithProjectAnalytic` в ChatViewModel

**Принцип работы:**
1. Проверяется, включен ли Project Analytic и выбрана ли модель Ollama Llama 3.2b
2. Отправляется только текущий запрос (без истории - каждый вопрос новый контекст)
3. Запрос обрабатывается через Ollama MCP сервер
4. MCP сервер автоматически определяет проектные запросы и получает контекст
5. Результат возвращается пользователю

**Интеграция:**
- Ollama MCP сервер взаимодействует с Project Helper MCP сервером
- Project Helper использует Ollama Vector Search для поиска релевантной информации
- Поддерживаются файлы: .md, .kt, .xml, .java, .kts, .sh
- Модель анализирует контекст и проверяет наличие подобных проблем в других местах проекта

### 5. Удален функционал загрузки файлов извне

**Удалено:**
- `filePickerLauncher` из HomeScreen
- `getFilePathFromUri` функция
- Обработчики `SelectOllamaFile` и `RemoveOllamaFile` из ChatViewModel
- UI элементы для выбора файлов

**Примечание:** `ollamaSelectedFiles` оставлен в ChatUiState для совместимости, но функционал отключен.

### 6. Обновлены скрипты запуска серверов

**Изменения:**
- `start-servers.sh` - добавлен запуск Ollama MCP сервера (порт 8086), удален GitHub MCP
- `stop-servers.sh` - обновлены порты и процессы

**Запускаемые серверы:**
- Project Helper MCP Server (порт 8081)
- Ollama MCP Server (порт 8086) - новый
- Git MCP Server (порт 8084)
- User Format MCP Server (порт 8085)

### 7. Добавлено логирование

**Места логирования:**
- `ChatViewModel.handleSendMessageWithProjectAnalytic` - логи обработки запросов
- `OllamaMcpServer` - логи работы MCP сервера
- `AiModelRepositoryImpl` - логи работы с Ollama моделью

## Как запустить

### 1. Установка и запуск Ollama

```bash
# Установка Ollama (если еще не установлен)
curl -fsSL https://ollama.com/install.sh | sh

# Запуск Ollama сервера
ollama serve

# В другом терминале - загрузка моделей
ollama pull llama3.2:1b
ollama pull nomic-embed-text
ollama pull phi3:medium
```

### 2. Запуск MCP серверов

```bash
# Запуск всех MCP серверов
./start-servers.sh

# Или запуск по отдельности:
cd ollama-mcp-server
./start-server.sh 8086
cd ..

cd project-helper-mcp-server
./start-server.sh 8081
cd ..
```

**Проверка работы серверов:**
```bash
# Проверка Ollama
curl http://localhost:11434/api/tags

# Проверка Ollama MCP Server
curl -X POST http://localhost:8086/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize"}'

# Проверка Project Helper MCP Server
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize"}'
```

### 3. Настройка проекта

**В local.properties (если нужно):**
```properties
PROJECT_ROOT=/path/to/project/root
```

### 4. Запуск Android приложения

1. Откройте проект в Android Studio
2. Соберите проект
3. Запустите на эмуляторе или устройстве
4. В приложении:
   - Откройте Tools (иконка инструментов)
   - Включите "Project Analytic"
   - Выберите модель "Ollama Llama 3.2b"
   - Задайте вопрос о проекте

## Принцип работы

### Flow обработки запроса с Project Analytic

```
1. Пользователь отправляет сообщение
   ↓
2. ChatViewModel проверяет:
   - Включен ли Project Analytic?
   - Выбрана ли модель Ollama Llama 3.2b?
   ↓
3. Если да → handleSendMessageWithProjectAnalytic()
   ↓
4. Отправка запроса в AiModelRepositoryImpl
   ↓
5. AiModelRepositoryImpl отправляет запрос в Ollama MCP Server
   ↓
6. Ollama MCP Server:
   - Определяет, является ли запрос проектно-ориентированным
   - Если да → запрашивает контекст из Project Helper MCP Server
   ↓
7. Project Helper MCP Server:
   - Использует Ollama Vector Search для поиска релевантных фрагментов
   - Возвращает контекст с релевантными чанками кода
   ↓
8. Ollama MCP Server:
   - Формирует промпт с контекстом проекта
   - Отправляет запрос в Ollama Llama 3.2b
   ↓
9. Llama 3.2b анализирует контекст и выдает ответ
   ↓
10. Ответ возвращается пользователю
```

### Особенности

1. **Без контекста истории:** Каждый новый вопрос обрабатывается независимо, без учета предыдущих сообщений
2. **Автоматическое определение проектных запросов:** MCP сервер автоматически определяет, нужен ли контекст проекта
3. **Интеграция Vector Search:** Используется семантический поиск для нахождения релевантных фрагментов кода
4. **Анализ всего проекта:** Модель может проверить наличие подобных проблем в других местах проекта

## Решение проблем

### Проблема: Ollama MCP Server не запускается

**Решение:**
1. Проверьте, что Ollama запущен: `curl http://localhost:11434/api/tags`
2. Проверьте, что порт 8086 свободен: `lsof -i :8086`
3. Проверьте логи: `tail -f /tmp/ollama-mcp-server.log`

### Проблема: Модель не отвечает

**Решение:**
1. Проверьте, что модель загружена: `ollama list`
2. Проверьте подключение к Ollama: `curl http://localhost:11434/api/tags`
3. Проверьте логи Ollama MCP Server

### Проблема: Project Helper не возвращает контекст

**Решение:**
1. Убедитесь, что Project Helper MCP Server запущен
2. Проверьте, что файлы проекта проиндексированы (вызовите `index_project_files` через MCP)
3. Проверьте логи Project Helper: `tail -f /tmp/project-helper-mcp-server.log`

### Проблема: Android приложение не может подключиться к серверам

**Решение:**
1. Для эмулятора используйте `10.0.2.2` вместо `localhost`
2. Для физического устройства используйте IP адрес вашего Mac
3. Проверьте, что серверы доступны: `curl http://10.0.2.2:8086/mcp`

## Структура файлов

```
ollama-mcp-server/
├── build.gradle.kts
├── start-server.sh
└── src/main/kotlin/com/example/ollamamcpserver/
    └── OllamaMcpServer.kt

feature/chat/
├── src/main/java/com/example/aiagentchat/feature/chat/
│   ├── domain/model/
│   │   └── AiModel.kt (добавлен OllamaLlama32b)
│   ├── data/
│   │   ├── api/
│   │   │   └── OllamaMcpApi.kt (новый)
│   │   ├── repository/
│   │   │   └── AiModelRepositoryImpl.kt (обновлен)
│   │   └── AuthManager.kt (обновлен)
│   └── presentation/
│       ├── chat/
│       │   ├── ChatViewModel.kt (добавлен Project Analytic)
│       │   └── ChatUiState.kt (добавлено поле)
│       └── components/
│           └── ToolsDialog.kt (добавлен ProjectAnalyticItem)
```

## Тестирование

### Тест 1: Базовый запрос

1. Включите Project Analytic
2. Выберите модель Ollama Llama 3.2b
3. Отправьте запрос: "Как работает ChatViewModel?"
4. Проверьте, что получен ответ с анализом кода

### Тест 2: Запрос о конкретном файле

1. Отправьте запрос: "Где находится класс PreferencesManager?"
2. Проверьте, что получен ответ с указанием файла и описанием

### Тест 3: Запрос о проблеме

1. Отправьте запрос: "Есть ли в проекте проблемы с обработкой ошибок?"
2. Проверьте, что модель проанализировала код и нашла потенциальные проблемы

## Исправления проблем

### Проблема: Модель не может дать информацию о проекте

**Причины:**
1. Неправильная структура запроса к Project Helper MCP серверу
2. Отсутствие проверки индексации файлов перед поиском
3. Недостаточное логирование для отладки

**Исправления:**

1. **Исправлена структура запроса к Project Helper:**
   - Теперь правильно передается `name` и `arguments` в `params` для метода `tools/call`
   - Добавлена обработка ошибок от Project Helper

2. **Добавлена автоматическая индексация:**
   - Перед поиском автоматически проверяется и выполняется индексация файлов проекта
   - Если индексация уже выполнена, Project Helper вернет ошибку, но поиск продолжится

3. **Улучшено логирование:**
   - Добавлены подробные логи на всех этапах обработки запроса
   - Логи показывают, получен ли контекст проекта, размер контекста, ошибки

4. **Улучшена обработка ошибок:**
   - Добавлена проверка наличия результата в ответе MCP
   - Улучшены сообщения об ошибках

5. **Автоматическое определение проектных запросов:**
   - В Android приложении добавлено автоматическое определение проектных запросов
   - Параметр `use_project_context` передается в MCP сервер

## Дополнительные заметки

- Модель Llama 3.2b работает локально, не требует интернета (кроме загрузки модели)
- Все запросы обрабатываются без сохранения истории (каждый вопрос - новый контекст)
- Project Helper автоматически индексирует файлы проекта при первом использовании
- Vector Search использует embeddings для семантического поиска
- При проблемах проверьте логи Ollama MCP сервера и Project Helper MCP сервера
