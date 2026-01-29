# Интеграция Remote Control и Toon формата

## Обзор

Реализована полная интеграция Remote Control MCP Server для управления Android устройствами и улучшена работа с Toon форматом для оптимизации взаимодействия с AI моделями.

## Реализованный функционал

### 1. ✅ Переименование модуля

Модуль `remote-control-mcp-server` (ранее `remote-docker-mcp-server`):
- Переименованы все файлы и директории
- Обновлены все импорты и классы
- Заменены все упоминания `docker` на `control`

**Изменения:**
- `DockerAdbExecutor` → `ControlAdbExecutor`
- `RemoteDockerMcpServer` → `RemoteControlMcpServer`
- `dockerEnabled` → `remoteControlEnabled`
- Все упоминания Docker заменены на Control

### 2. ✅ Улучшение работы с Toon форматом

#### Интеграция Toon в core/common

Toon формат теперь доступен во всех модулях через `core/common`:
- `core/common/src/main/java/com/example/aiagentchat/core/common/toon/ToonEncoder.kt`
- `core/common/src/main/java/com/example/aiagentchat/core/common/toon/ToonDecoder.kt`

#### Использование Toon для контекста

Контекст предыдущих обсуждений теперь форматируется в Toon формате:
- Экономия до 60% токенов по сравнению с JSON
- Структурированный формат для лучшего понимания AI
- Автоматическое форматирование в системных сообщениях

#### Экспорт в Toon

`ExportChatHistoryUseCase` обновлен для использования Toon формата:
- История чата экспортируется в Toon
- Включает метрики и сравнения моделей
- Оптимизирован для минимального использования токенов

### 3. ✅ Управление реальными устройствами

Добавлена поддержка управления реальными подключенными устройствами:

**Новые возможности:**
- Параметр `deviceId` для всех команд
- Инструмент `list_devices` для получения списка устройств
- Инструмент `take_screenshot` для создания скриншотов
- Автоматическое определение устройства

**Инструменты:**
1. `list_devices` - Получить список подключенных устройств
2. `check_adb_availability` - Проверить доступность ADB (с опциональным deviceId)
3. `press_home` - Нажать Home (с опциональным deviceId)
4. `press_back` - Нажать Back (с опциональным deviceId)
5. `open_app` - Открыть приложение (с опциональным deviceId)
6. `minimize_app` - Свернуть приложение (с опциональным deviceId)
7. `take_screenshot` - Сделать скриншот (с опциональным deviceId)
8. `execute_adb_command` - Выполнить произвольную команду (с опциональным deviceId)

### 4. ✅ Логика включения/отключения Remote Control

Добавлено управление состоянием Remote Control с сохранением между сессиями:

**Файлы:**
- `core/common/src/main/java/com/example/aiagentchat/core/common/preferences/PreferencesManager.kt`
  - Добавлено свойство `remoteControlEnabled` с сохранением в SharedPreferences

**UI:**
- Переключатель "Remote Device Control" в настройках MCP Tools
- Состояние сохраняется между сессиями

## Логика работы

### Сценарий: User → AI Chat → Remote Control MCP Server → Connected Real Device

1. **Пользователь заходит в приложение на эмуляторе**
   - Приложение запущено на Android эмуляторе

2. **Включает Remote Device Control и настраивает Device ID**
   - Открывает настройки (⚙️)
   - Включает "Remote Device Control"
   - **Указывает Device ID** (например, `ABC123XYZ`) в поле "Device ID"
   - Включает нужные инструменты (например, `press_home`, `open_app`, `take_screenshot`)

3. **Пользователь просит AI выполнить действие на подключенном устройстве**
   - Примеры: "Нажми кнопку Home на подключенном устройстве", "Открой Chrome на реальном устройстве", "Сделай скриншот"

4. **AI Chat передает команду remote-control-mcp-server**
   - AI определяет нужный инструмент
   - **AI автоматически добавляет Device ID в аргументы** (если он указан в настройках)
   - Отправляет запрос в Remote Control MCP Server с параметром `deviceId`

5. **Remote Control MCP Server проверяет и выполняет**
   - Проверяет доступность ADB
   - Проверяет наличие устройства с указанным deviceId
   - Выполняет ADB команду на указанном устройстве: `adb -s [deviceId] shell [command]`
   - Возвращает результат

6. **AI Chat публикует ответ для пользователя**
   - Если успешно: сообщает о выполнении
   - Если ошибка: сообщает причину проблемы

## Использование

### Шаг 1: Подготовка

1. **Установите ADB:**
   ```bash
   # macOS
   brew install android-platform-tools
   
   # Linux
   sudo apt-get install android-tools-adb
   ```

2. **Подключите устройство:**
   - Для эмулятора: просто запустите эмулятор
   - Для реального устройства:
     - Включите режим разработчика
     - Включите отладку по USB
     - Подключите через USB

3. **Проверьте подключение:**
   ```bash
   adb devices
   ```

### Шаг 2: Запуск сервера

```bash
./start-servers.sh
```

### Шаг 3: Настройка в приложении

1. Откройте приложение на эмуляторе
2. Нажмите на иконку настроек (⚙️)
3. Включите "Remote Device Control"
4. Включите инструменты Remote Control MCP Server

### Шаг 4: Использование

Просто попросите AI выполнить действие:

**Примеры команд:**
- "Нажми кнопку Home на подключенном устройстве"
- "Открой Chrome на реальном устройстве"
- "Открой YouTube на подключенном устройстве"
- "Сделай скриншот подключенного устройства"
- "Нажми кнопку Назад на реальном устройстве"

## Формат Toon

### Использование в контексте

Контекст предыдущих обсуждений автоматически форматируется в Toon:

```toon
context:
  userSummaries[3]{summary,keyFacts,timestamp}:
    "Пользователь спросил о погоде",["погода","Москва"],1234567890
    "Пользователь спросил о времени",["время"],1234567900
  aiSummaries[2]{summary,keyFacts,timestamp}:
    "AI ответил о погоде",["температура","ветер"],1234567895
```

Это позволяет:
- Сэкономить до 60% токенов
- Передать больше контекста
- Улучшить понимание AI

### Экспорт истории

История чата экспортируется в Toon формате:

```toon
exportedAt: 2025-01-15 10:30:00
totalMessages: 10
messages[10]{id,content,isUser,model,timestamp,responseTimeMs,inputTokens,outputTokens,costUsd}:
  "msg1","Привет",true,"",1234567890,0,0,0,0.0
  "msg2","Здравствуй",false,"DeepSeek",1234567895,500,10,20,0.001
  ...
```

## Технические детали

### Архитектура

```
Android App (на эмуляторе)
    ↓
MultiMcpRepository
    ↓
Remote Control MCP Server (HTTP)
    ↓
ControlAdbExecutor
    ↓
ADB
    ↓
Connected Real Device
```

### Ключевые компоненты

1. **ControlAdbExecutor** - Класс для выполнения ADB команд
   - Поддержка deviceId для работы с конкретным устройством
   - Автоматический поиск ADB
   - Обработка ошибок

2. **RemoteControlMcpServer** - MCP сервер
   - HTTP сервер на Ktor
   - MCP Protocol совместимость
   - 8 инструментов для управления

3. **ToonEncoder/ToonDecoder** - Форматирование Toon
   - Доступны через core/common
   - Используются для контекста и экспорта

## Тестирование

### Проверка компиляции

```bash
./gradlew :remote-control-mcp-server:build
./gradlew :app:compileDebugKotlin
```

### Проверка работы сервера

```bash
# Запуск
./start-servers.sh

# Проверка логов
tail -f /tmp/remote-control-mcp-server.log
```

### Тестирование в приложении

1. Запустите эмулятор
2. Подключите реальное устройство
3. Запустите приложение на эмуляторе
4. Включите Remote Control в настройках
5. Попросите AI выполнить команду на реальном устройстве

## Документация

- **Remote Control MCP Server:** [remote-control-mcp-server/README.md](remote-control-mcp-server/README.md)
- **Основной README:** [README.md](README.md)

## Статус реализации

✅ Все задачи выполнены:
- [x] Проверка и улучшение работы с Toon форматом
- [x] Переименование модуля (выполнено ранее)
- [x] Замена всех упоминаний docker → control
- [x] Добавление логики управления реальным устройством
- [x] Добавление переключателя для управления реальным устройством
- [x] Обновление документации
- [x] Тестирование компиляции и работы

