# Remote Control MCP Server

MCP сервер для управления Android устройствами и эмуляторами через ADB команды.

## Описание

Remote Control MCP Server предоставляет инструменты для управления Android устройствами/эмуляторами через ADB команды. Сервер поддерживает работу как с эмуляторами, так и с реальными подключенными устройствами.

## Подготовка

### Требования

1. **ADB (Android Debug Bridge)**
   - Должен быть установлен локально
   - Доступен в PATH или через переменные окружения

2. **Android Device/Emulator**
   - Устройство должно быть подключено и доступно через ADB
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
ABC123XYZ        device
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
REMOTE_CONTROL_MCP_SERVER_URL=http://10.0.2.2:8082/
```

**Примечание:** `10.0.2.2` - это специальный IP адрес для доступа к localhost хоста из Android эмулятора.

## Включение

### Запуск сервера

#### Через скрипт (рекомендуется)
```bash
cd remote-control-mcp-server
./start-server.sh
```

#### Через Gradle
```bash
./gradlew :remote-control-mcp-server:fatJar
java -jar remote-control-mcp-server/build/libs/remote-control-mcp-server-1.0.0-all.jar 8082
```

#### Через общий скрипт
```bash
./start-servers.sh
```

Этот скрипт запустит все MCP серверы, включая Remote Control MCP Server.

### Проверка работы

После запуска сервер будет доступен по адресу:
- **Локально:** `http://localhost:8082/mcp`
- **Из Android эмулятора:** `http://10.0.2.2:8082/mcp`

Логи сервера можно посмотреть:
```bash
tail -f /tmp/remote-control-mcp-server.log
```

## Взаимодействие

### Доступные инструменты

#### 1. `list_devices`
Получает список всех подключенных Android устройств/эмуляторов.

**Параметры:** нет

**Пример использования:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "list_devices",
    "arguments": {}
  }
}
```

**Ответ:**
```
Device ID: emulator-5554, Model: sdk_gphone64_arm64, Device: emulator64_arm64
Device ID: ABC123XYZ, Model: Pixel_7, Device: panther
```

#### 2. `check_adb_availability`
Проверяет доступность ADB и список подключенных устройств. Опционально можно указать deviceId для проверки конкретного устройства.

**Параметры:**
- `deviceId` (string, опциональный) - ID устройства для проверки

**Пример использования:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "check_adb_availability",
    "arguments": {
      "deviceId": "emulator-5554"
    }
  }
}
```

#### 3. `press_home`
Нажимает кнопку Home на Android устройстве/эмуляторе.

**Параметры:**
- `deviceId` (string, опциональный) - ID устройства для подключенного реального устройства

**Пример использования:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "press_home",
    "arguments": {
      "deviceId": "ABC123XYZ"
    }
  }
}
```

#### 4. `press_back`
Нажимает кнопку Back на Android устройстве/эмуляторе.

**Параметры:**
- `deviceId` (string, опциональный) - ID устройства для подключенного реального устройства

**Пример использования:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "press_back",
    "arguments": {
      "deviceId": "ABC123XYZ"
    }
  }
}
```

#### 5. `open_app`
Открывает приложение по package name.

**Параметры:**
- `packageName` (string, обязательный) - Package name приложения
- `deviceId` (string, опциональный) - ID устройства для подключенного реального устройства

**Пример использования:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "open_app",
    "arguments": {
      "packageName": "com.android.chrome",
      "deviceId": "ABC123XYZ"
    }
  }
}
```

**Популярные package names:**
- Chrome: `com.android.chrome`
- YouTube: `com.google.android.youtube`
- Settings: `com.android.settings`
- Gmail: `com.google.android.gm`

#### 6. `minimize_app`
Сворачивает текущее приложение (нажимает Home).

**Параметры:**
- `deviceId` (string, опциональный) - ID устройства для подключенного реального устройства

**Пример использования:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "minimize_app",
    "arguments": {
      "deviceId": "ABC123XYZ"
    }
  }
}
```

#### 7. `take_screenshot`
Делает скриншот экрана устройства. Скриншот сохраняется в `/tmp/screenshot_[timestamp].png` на хосте.

**Параметры:**
- `deviceId` (string, опциональный) - ID устройства для подключенного реального устройства

**Пример использования:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "take_screenshot",
    "arguments": {
      "deviceId": "ABC123XYZ"
    }
  }
}
```

#### 8. `execute_adb_command`
Выполняет произвольную ADB shell команду.

**Параметры:**
- `command` (string, обязательный) - ADB shell команда
- `deviceId` (string, опциональный) - ID устройства для подключенного реального устройства

**Пример использования:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "execute_adb_command",
    "arguments": {
      "command": "input tap 500 500",
      "deviceId": "ABC123XYZ"
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

1. **Включите Remote Control в настройках:**
   - Откройте приложение
   - Нажмите на иконку настроек (⚙️)
   - Включите переключатель "Remote Device Control"

2. **Укажите Device ID (опционально, но рекомендуется):**
   - В поле "Device ID" введите уникальный идентификатор устройства
   - Например: `emulator-5554` или `ABC123XYZ`
   - Если оставить пустым, будет использоваться устройство по умолчанию
   - Чтобы узнать доступные устройства, используйте инструмент `list_devices`

3. **Включите инструменты Remote Control MCP Server:**
   - В том же диалоге найдите "Remote Control MCP Server"
   - Включите нужные инструменты (например, `press_home`, `open_app`, `take_screenshot`)

4. **Используйте AI Chat:**
   - Попросите AI выполнить действие на устройстве
   - Например: "Нажми кнопку Home на подключенном устройстве", "Открой Chrome на реальном устройстве", "Сделай скриншот"
   - AI автоматически использует указанный Device ID для выполнения команд

5. **AI автоматически:**
   - Определит, какой инструмент использовать
   - Добавит Device ID в аргументы (если он указан в настройках)
   - Вызовет соответствующий инструмент через MCP сервер
   - Сообщит о результате выполнения

## Логика работы

### Сценарий: User → AI Chat → Remote Control MCP Server → Connected Real Device

1. **Пользователь:** "Нажми кнопку Home на подключенном устройстве"
2. **AI Chat:** Определяет, что нужно использовать инструмент `press_home`
3. **AI Chat:** Автоматически добавляет Device ID в аргументы (если он указан в настройках приложения)
4. **AI Chat:** Отправляет запрос в Remote Control MCP Server с параметром `deviceId`
5. **Remote Control MCP Server:**
   - Проверяет доступность ADB
   - Проверяет наличие устройства с указанным deviceId (если указан)
   - Выполняет команду `adb -s [deviceId] shell input keyevent KEYCODE_HOME` (если deviceId указан) или `adb shell input keyevent KEYCODE_HOME` (если не указан)
   - Возвращает результат выполнения
6. **AI Chat:** Получает результат и сообщает пользователю

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

## Работа с реальными устройствами

### Подключение реального устройства

1. **Включите режим разработчика на устройстве:**
   - Перейдите в Настройки → О телефоне
   - Нажмите 7 раз на "Номер сборки"

2. **Включите отладку по USB:**
   - Перейдите в Настройки → Для разработчиков
   - Включите "Отладка по USB"

3. **Подключите устройство к компьютеру:**
   - Подключите устройство через USB
   - Разрешите отладку по USB на устройстве

4. **Проверьте подключение:**
   ```bash
   adb devices
   ```

### Использование deviceId

При работе с реальным устройством, AI Chat может автоматически определить deviceId или использовать первый доступный. Для явного указания устройства используйте параметр `deviceId` в запросах.

## Устранение неполадок

### Проблема: "device not found"

**Решение:**
1. Убедитесь, что устройство/эмулятор запущен
2. Проверьте подключение: `adb devices`
3. Если устройство не видно, перезапустите ADB: `adb kill-server && adb start-server`
4. Для реального устройства проверьте, что включена отладка по USB

### Проблема: "ADB command failed"

**Решение:**
1. Проверьте, что ADB установлен и доступен в PATH
2. Проверьте права доступа к ADB
3. Убедитесь, что устройство/устройство подключено
4. Проверьте, что deviceId указан правильно (если используется)

### Проблема: Сервер не запускается

**Решение:**
1. Проверьте, что порт 8082 свободен: `lsof -i :8082`
2. Проверьте логи: `/tmp/remote-control-mcp-server.log`
3. Убедитесь, что все зависимости установлены

### Проблема: Команды не выполняются из Android приложения

**Решение:**
1. Убедитесь, что сервер запущен на хосте
2. Проверьте URL в приложении: должен быть `http://10.0.2.2:8082/mcp`
3. Проверьте сетевые настройки эмулятора

## Тестирование

### Ручное тестирование через curl

```bash
# Получение списка устройств
curl -X POST http://localhost:8082/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "list_devices",
      "arguments": {}
    }
  }'

# Нажатие Home на конкретном устройстве
curl -X POST http://localhost:8082/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "press_home",
      "arguments": {
        "deviceId": "ABC123XYZ"
      }
    }
  }'

# Скриншот
curl -X POST http://localhost:8082/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "take_screenshot",
      "arguments": {
        "deviceId": "ABC123XYZ"
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
remote-control-mcp-server/
├── src/main/kotlin/
│   └── com/example/remotecontrolmcpserver/
│       └── RemoteControlMcpServer.kt  # Основной файл сервера
├── build.gradle.kts                  # Конфигурация сборки
├── start-server.sh                   # Скрипт запуска
└── README.md                         # Документация
```

## Лицензия

MIT
