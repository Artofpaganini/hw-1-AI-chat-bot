# Remote Docker MCP Server

MCP сервер для управления Android эмулятором через Docker и ADB команды.

## Описание

Remote Docker MCP Server предоставляет инструменты для управления Android эмулятором/устройством через ADB команды. Сервер может работать как напрямую через ADB (если установлен локально), так и через Docker контейнеры с Android эмулятором.

## Подготовка

### Требования

1. **ADB (Android Debug Bridge)**
   - Установлен локально, или
   - Доступен через Docker контейнер с Android эмулятором

2. **Docker** (опционально)
   - Требуется только если эмулятор запущен в Docker контейнере

3. **Android Emulator/Device**
   - Эмулятор должен быть запущен и доступен через ADB
   - Проверка: `adb devices` должна показать подключенное устройство

### Установка ADB

#### macOS
```bash
brew install android-platform-tools
```

#### Linux
```bash
sudo apt-get install android-tools-adb
```

#### Windows
Скачайте [Android SDK Platform Tools](https://developer.android.com/studio/releases/platform-tools) и добавьте в PATH.

### Проверка подключения

```bash
adb devices
```

Должен показать список подключенных устройств:
```
List of devices attached
emulator-5554    device
```

## Настройка

### 1. Настройка порта

По умолчанию сервер запускается на порту **8082**. Для изменения порта:

```bash
./start-server.sh 8082
```

### 2. Настройка в Android приложении

В файле `local.properties` добавьте (если нужно изменить URL):

```properties
REMOTE_DOCKER_MCP_SERVER_URL=http://10.0.2.2:8082/
```

**Примечание:** `10.0.2.2` - это специальный IP адрес для доступа к localhost хоста из Android эмулятора.

## Включение

### Запуск сервера

#### Через скрипт (рекомендуется)
```bash
cd remote-docker-mcp-server
./start-server.sh
```

#### Через Gradle
```bash
./gradlew :remote-docker-mcp-server:fatJar
java -jar remote-docker-mcp-server/build/libs/remote-docker-mcp-server-1.0.0-all.jar 8082
```

#### Через общий скрипт
```bash
./start-servers.sh
```

Этот скрипт запустит все MCP серверы, включая Remote Docker MCP Server.

### Проверка работы

После запуска сервер будет доступен по адресу:
- **Локально:** `http://localhost:8082/mcp`
- **Из Android эмулятора:** `http://10.0.2.2:8082/mcp`

Логи сервера можно посмотреть:
```bash
tail -f /tmp/remote-docker-mcp-server.log
```

## Взаимодействие

### Доступные инструменты

#### 1. `check_adb_availability`
Проверяет доступность ADB и список подключенных устройств.

**Параметры:** нет

**Пример использования:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "check_adb_availability",
    "arguments": {}
  }
}
```

#### 2. `press_home`
Нажимает кнопку Home на Android устройстве/эмуляторе.

**Параметры:** нет

**Пример использования:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "press_home",
    "arguments": {}
  }
}
```

#### 3. `press_back`
Нажимает кнопку Back на Android устройстве/эмуляторе.

**Параметры:** нет

**Пример использования:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "press_back",
    "arguments": {}
  }
}
```

#### 4. `open_app`
Открывает приложение по package name.

**Параметры:**
- `packageName` (string, обязательный) - Package name приложения

**Пример использования:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "open_app",
    "arguments": {
      "packageName": "com.android.chrome"
    }
  }
}
```

**Популярные package names:**
- Chrome: `com.android.chrome`
- YouTube: `com.google.android.youtube`
- Settings: `com.android.settings`
- Gmail: `com.google.android.gm`

#### 5. `minimize_app`
Сворачивает текущее приложение (нажимает Home).

**Параметры:** нет

**Пример использования:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "minimize_app",
    "arguments": {}
  }
}
```

#### 6. `execute_adb_command`
Выполняет произвольную ADB shell команду.

**Параметры:**
- `command` (string, обязательный) - ADB shell команда

**Пример использования:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "execute_adb_command",
    "arguments": {
      "command": "input tap 500 500"
    }
  }
}
```

**Полезные ADB команды:**
- `input tap x y` - Нажатие на координаты (x, y)
- `input swipe x1 y1 x2 y2` - Свайп от (x1, y1) к (x2, y2)
- `am start -n com.android.settings/.Settings` - Открыть Settings
- `input text "текст"` - Ввод текста
- `input keyevent KEYCODE_VOLUME_UP` - Увеличить громкость

### Использование через AI Chat

1. **Включите Docker в настройках:**
   - Откройте приложение
   - Нажмите на иконку настроек (⚙️)
   - Включите переключатель "Docker & Android Emulator Control"

2. **Включите инструменты Remote Docker MCP Server:**
   - В том же диалоге найдите "Remote Docker MCP Server"
   - Включите нужные инструменты (например, `press_home`, `open_app`)

3. **Используйте AI Chat:**
   - Попросите AI выполнить действие на эмуляторе
   - Например: "Нажми кнопку Home", "Открой Chrome", "Открой YouTube"

4. **AI автоматически:**
   - Определит, какой инструмент использовать
   - Вызовет соответствующий инструмент через MCP сервер
   - Сообщит о результате выполнения

## Логика работы

### Сценарий: User → AI Chat → Remote Docker MCP Server

1. **Пользователь:** "Нажми кнопку Home на эмуляторе"
2. **AI Chat:** Определяет, что нужно использовать инструмент `press_home`
3. **AI Chat:** Отправляет запрос в Remote Docker MCP Server
4. **Remote Docker MCP Server:**
   - Проверяет доступность ADB
   - Выполняет команду `adb shell input keyevent KEYCODE_HOME`
   - Возвращает результат выполнения
5. **AI Chat:** Получает результат и сообщает пользователю

### Обработка ошибок

Если команда не может быть выполнена, сервер вернет ошибку с описанием проблемы:

```json
{
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Error: ADB command failed: device not found"
      }
    ],
    "isError": true
  }
}
```

AI Chat автоматически передаст эту информацию пользователю.

## Работа с Docker

### Поиск Docker контейнера

Сервер автоматически пытается найти Docker контейнер с Android эмулятором, проверяя:
- Имена контейнеров, содержащие "android" или "emulator"
- Если контейнер найден, команды выполняются через `docker exec`

### Прямое использование ADB

Если Docker контейнер не найден, сервер использует ADB напрямую:
- Ищет ADB в стандартных путях
- Проверяет переменные окружения `ANDROID_HOME` и `ANDROID_SDK_ROOT`

## Устранение неполадок

### Проблема: "device not found"

**Решение:**
1. Убедитесь, что эмулятор запущен
2. Проверьте подключение: `adb devices`
3. Если устройство не видно, перезапустите ADB: `adb kill-server && adb start-server`

### Проблема: "ADB command failed"

**Решение:**
1. Проверьте, что ADB установлен и доступен в PATH
2. Проверьте права доступа к ADB
3. Убедитесь, что эмулятор/устройство подключено

### Проблема: Сервер не запускается

**Решение:**
1. Проверьте, что порт 8082 свободен: `lsof -i :8082`
2. Проверьте логи: `/tmp/remote-docker-mcp-server.log`
3. Убедитесь, что все зависимости установлены

### Проблема: Команды не выполняются из Android приложения

**Решение:**
1. Убедитесь, что сервер запущен на хосте
2. Проверьте URL в приложении: должен быть `http://10.0.2.2:8082/mcp`
3. Проверьте сетевые настройки эмулятора

## Тестирование

### Ручное тестирование через curl

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

# Открытие Chrome
curl -X POST http://localhost:8082/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "open_app",
      "arguments": {
        "packageName": "com.android.chrome"
      }
    }
  }'
```

## Архитектура

Сервер построен на основе:
- **Ktor** - HTTP сервер
- **Kotlinx Serialization** - JSON сериализация
- **MCP Protocol** - Model Context Protocol

Структура:
```
remote-docker-mcp-server/
├── src/main/kotlin/
│   └── com/example/remotedockermcpserver/
│       └── RemoteDockerMcpServer.kt  # Основной файл сервера
├── build.gradle.kts                  # Конфигурация сборки
├── start-server.sh                   # Скрипт запуска
└── README.md                         # Документация
```

## Лицензия

MIT

