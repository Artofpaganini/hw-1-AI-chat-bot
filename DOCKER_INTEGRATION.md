# Интеграция Docker и Android Emulator Control

## Обзор

Реализована полная интеграция Docker и управления Android эмулятором через MCP сервер. Теперь AI-чат приложение может управлять Android эмулятором через голосовые команды пользователя.

## Реализованный функционал

### 1. ✅ Интеграция Toon формата

Toon формат уже был интегрирован в проект. Используется для:
- Экспорта истории чата
- Эффективной передачи данных в LLM
- Минимизации использования токенов

**Файлы:**
- `src/main/java/com/example/aiagentchat/data/toon/ToonEncoder.kt`
- `src/main/java/com/example/aiagentchat/data/toon/ToonConverter.kt`
- `src/main/java/com/example/aiagentchat/data/toon/ChatHistoryExporter.kt`

### 2. ✅ Логика включения/отключения Docker

Добавлено управление состоянием Docker с сохранением между сессиями:

**Файлы:**
- `core/common/src/main/java/com/example/aiagentchat/core/common/preferences/PreferencesManager.kt`
  - Добавлено свойство `dockerEnabled` с сохранением в SharedPreferences

**Использование:**
```kotlin
preferencesManager.dockerEnabled = true  // Включить
val isEnabled = preferencesManager.dockerEnabled  // Проверить состояние
```

### 3. ✅ Модуль remote-docker-mcp-server

Создан новый MCP сервер для управления Android эмулятором:

**Структура:**
```
remote-docker-mcp-server/
├── src/main/kotlin/com/example/remotedockermcpserver/
│   └── RemoteDockerMcpServer.kt
├── build.gradle.kts
├── start-server.sh
└── README.md
```

**Функционал:**
- Управление через ADB команды
- Поддержка работы через Docker контейнеры
- Автоматический поиск ADB и Docker контейнеров
- 6 инструментов для управления эмулятором

### 4. ✅ Управление Android эмулятором

Реализованы следующие команды:

1. **check_adb_availability** - Проверка доступности ADB
2. **press_home** - Нажатие кнопки Home
3. **press_back** - Нажатие кнопки Back
4. **open_app** - Открытие приложения по package name
5. **minimize_app** - Сворачивание текущего приложения
6. **execute_adb_command** - Выполнение произвольной ADB команды

**Примеры использования:**
- "Нажми кнопку Home" → `press_home`
- "Открой Chrome" → `open_app` с `packageName: "com.android.chrome"`
- "Открой YouTube" → `open_app` с `packageName: "com.google.android.youtube"`
- "Нажми кнопку Назад" → `press_back`

### 5. ✅ UI для управления Docker

Добавлен переключатель Docker в диалог настроек MCP Tools:

**Файлы:**
- `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/components/McpToolsDialog.kt`
  - Добавлен компонент `DockerItem`
- `feature/home/src/main/java/com/example/aiagentchat/feature/home/presentation/HomeScreen.kt`
  - Интегрирован переключатель Docker

**Расположение:**
- Настройки → MCP Tools → "Docker & Android Emulator Control"

### 6. ✅ Интеграция в MultiMcpRepository

Сервер автоматически доступен через существующую инфраструктуру:

**Файлы:**
- `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/domain/model/McpServer.kt`
  - Добавлен `REMOTE_DOCKER_SERVER_ID` в `DEFAULT_SERVERS`

**Конфигурация:**
- ID: `remote-docker-mcp-server`
- URL: `http://10.0.2.2:8082`
- Порт: `8082`

### 7. ✅ Обновление start-servers.sh

Скрипт автоматически запускает все MCP серверы:

```bash
./start-servers.sh
```

Запускает:
- Weather MCP Server (порт 8080)
- Google Storage MCP Server (порт 8081)
- Remote Docker MCP Server (порт 8082)

## Как использовать

### Шаг 1: Подготовка

1. **Установите ADB:**
   ```bash
   # macOS
   brew install android-platform-tools
   
   # Linux
   sudo apt-get install android-tools-adb
   ```

2. **Проверьте подключение эмулятора:**
   ```bash
   adb devices
   ```

3. **Запустите MCP серверы:**
   ```bash
   ./start-servers.sh
   ```

### Шаг 2: Настройка в приложении

1. Откройте приложение
2. Нажмите на иконку настроек (⚙️)
3. Включите "Docker & Android Emulator Control"
4. Найдите "Remote Docker MCP Server" в списке
5. Включите нужные инструменты:
   - `press_home`
   - `press_back`
   - `open_app`
   - и т.д.

### Шаг 3: Использование

Просто попросите AI выполнить действие:

**Примеры команд:**
- "Нажми кнопку Home на эмуляторе"
- "Открой Chrome браузер"
- "Открой YouTube"
- "Нажми кнопку Назад"
- "Сверни текущее приложение"

AI автоматически:
1. Определит нужный инструмент
2. Вызовет его через MCP сервер
3. Выполнит команду на эмуляторе
4. Сообщит о результате

## Логика работы

### Поток: User → AI Chat → Remote Docker MCP Server → Android Emulator

```
1. Пользователь: "Нажми кнопку Home"
   ↓
2. AI Chat: Определяет инструмент `press_home`
   ↓
3. AI Chat: Отправляет запрос в Remote Docker MCP Server
   ↓
4. Remote Docker MCP Server:
   - Проверяет доступность ADB
   - Выполняет: adb shell input keyevent KEYCODE_HOME
   - Возвращает результат
   ↓
5. AI Chat: Получает результат и сообщает пользователю
```

### Обработка ошибок

Если команда не может быть выполнена:
- Сервер возвращает ошибку с описанием
- AI Chat передает информацию пользователю
- Пользователь видит понятное сообщение об ошибке

## Технические детали

### Архитектура

```
Android App
    ↓
MultiMcpRepository
    ↓
Remote Docker MCP Server (HTTP)
    ↓
DockerAdbExecutor
    ↓
ADB / Docker
    ↓
Android Emulator
```

### Ключевые компоненты

1. **DockerAdbExecutor** - Класс для выполнения ADB команд
   - Автоматический поиск ADB
   - Поддержка Docker контейнеров
   - Обработка ошибок

2. **RemoteDockerMcpServer** - MCP сервер
   - HTTP сервер на Ktor
   - MCP Protocol совместимость
   - 6 инструментов для управления

3. **PreferencesManager** - Хранение состояния
   - Сохранение состояния Docker
   - Работа между сессиями

## Тестирование

### Ручное тестирование

```bash
# Проверка доступности ADB
curl -X POST http://localhost:8082/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "check_adb_availability",
      "arguments": {}
    }
  }'

# Нажатие Home
curl -X POST http://localhost:8082/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "press_home",
      "arguments": {}
    }
  }'
```

### Тестирование в приложении

1. Запустите эмулятор
2. Запустите приложение на эмуляторе
3. Включите Docker в настройках
4. Включите инструменты Remote Docker MCP Server
5. Попросите AI выполнить команду
6. Проверьте результат на эмуляторе

## Устранение неполадок

### Проблема: "device not found"

**Решение:**
1. Убедитесь, что эмулятор запущен
2. Проверьте: `adb devices`
3. Перезапустите ADB: `adb kill-server && adb start-server`

### Проблема: Сервер не запускается

**Решение:**
1. Проверьте порт: `lsof -i :8082`
2. Проверьте логи: `/tmp/remote-docker-mcp-server.log`
3. Убедитесь, что зависимости установлены

### Проблема: Команды не выполняются

**Решение:**
1. Проверьте, что ADB установлен и в PATH
2. Проверьте подключение эмулятора
3. Проверьте логи сервера

## Документация

- **Remote Docker MCP Server:** [remote-docker-mcp-server/README.md](remote-docker-mcp-server/README.md)
- **Основной README:** [README.md](README.md)

## Статус реализации

✅ Все задачи выполнены:
- [x] Интеграция Toon формата (уже был интегрирован)
- [x] Логика включения/отключения Docker
- [x] Модуль remote-docker-mcp-server
- [x] Управление Android эмулятором
- [x] UI для управления Docker
- [x] Интеграция в MultiMcpRepository
- [x] Обновление start-servers.sh
- [x] Тестирование и документация

## Следующие шаги (опционально)

- [ ] Добавить больше ADB команд (swipe, tap, text input)
- [ ] Поддержка нескольких эмуляторов одновременно
- [ ] UI для просмотра логов выполнения команд
- [ ] История выполненных команд
- [ ] Поддержка скриншотов эмулятора

