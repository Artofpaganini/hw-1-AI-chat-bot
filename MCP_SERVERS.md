# MCP Servers Documentation

## Обзор

Проект включает два MCP (Model Context Protocol) сервера:

1. **Weather MCP Server** - для получения информации о погоде
2. **Google Storage MCP Server** - для сохранения данных в Google Drive

## Weather MCP Server

### Описание

Сервер предоставляет инструмент для получения информации о погоде через Weather.gov API.

### Запуск

```bash
cd weather-mcp-server
./start-server.sh [port]
```

По умолчанию запускается на порту 8080.

### Инструменты

#### `get_weather`

Получение информации о погоде для указанного местоположения.

**Параметры:**
- `location` (обязательный, string) - Название города, адрес или координаты в формате "lat,lon" (например, "40.7128,-74.0060" или "New York")

**Возвращает:**
- Сжатую информацию о погоде (максимум 2 предложения)

**Пример использования:**
```json
{
  "name": "get_weather",
  "arguments": {
    "location": "New York"
  }
}
```

### API

Сервер использует бесплатный API от National Weather Service (NOAA) по адресу https://api.weather.gov/

**Важно:** API работает только для территории США. Для других регионов будет использован геокодинг через OpenStreetMap Nominatim, но прогноз будет доступен только если координаты находятся в США.

## Google Storage MCP Server

### Описание

Сервер предоставляет инструмент для сохранения JSON данных в Google Drive.

### Запуск

```bash
cd google-storage-mcp-server
./start-server.sh [port]
```

По умолчанию запускается на порту 8081.

### Инструменты

#### `save_to_drive`

Сохранение JSON данных в Google Drive. Если файл с указанным именем существует, он перезаписывается. Если файл не существует, создается новый.

**Параметры:**
- `accessToken` (обязательный, string) - Google Drive API access token (OAuth 2.0)
- `fileName` (опциональный, string) - Имя файла для сохранения (по умолчанию: "ai-chat-results")
- `data` (обязательный, string) - JSON данные для сохранения в файл

**Возвращает:**
- Сообщение об успешном сохранении или обновлении файла

**Пример использования:**
```json
{
  "name": "save_to_drive",
  "arguments": {
    "accessToken": "ya29.a0AfH6SMC...",
    "fileName": "ai-chat-results",
    "data": "{\"weather\": \"sunny\", \"temperature\": 25}"
  }
}
```

### Настройка Google Drive

📖 **Подробная инструкция:** См. [GOOGLE_DRIVE_SETUP.md](GOOGLE_DRIVE_SETUP.md)

**Краткая версия:**

1. **Создайте проект в Google Cloud Console** (требует ручной настройки)
2. **Включите Google Drive API** (требует ручной настройки)
3. **Создайте OAuth 2.0 credentials** (требует ручной настройки)
4. **Авторизуйтесь в приложении** (автоматически через Google Sign-In)

**Что можно автоматизировать:**
- ✅ Получение access token через Google Sign-In в приложении
- ✅ Авторизация пользователя через встроенный OAuth flow
- ✅ Сохранение access token в приложении

**Что требует ручной настройки:**
- ❌ Создание проекта в Google Cloud Console
- ❌ Включение Google Drive API
- ❌ Создание OAuth 2.0 credentials

**Важно:** 
- Access token имеет ограниченный срок действия
- Для долгосрочного использования рекомендуется использовать refresh token для обновления access token
- Пользователю нужно только один раз авторизоваться в приложении - access token будет автоматически получен и сохранен

## Интеграция с Android приложением

### Настройка URL серверов

В Android приложении серверы настроены по умолчанию:
- Weather MCP Server: `http://10.0.2.2:8080/mcp` (для эмулятора)
- Google Storage MCP Server: `http://10.0.2.2:8081/mcp` (для эмулятора)

Для физического устройства замените `10.0.2.2` на IP адрес вашего компьютера.

### Использование в приложении

1. Запустите нужные MCP серверы на хосте
2. Откройте приложение
3. Нажмите на иконку настроек (⚙️) в верхней панели
4. В диалоге "MCP Tools" вы увидите список доступных серверов
5. Включите нужные инструменты для каждого сервера
6. Состояние инструментов сохраняется между сессиями

## Логика работы

### Поток данных: User → Weather MCP Server → AI Chat → Google Storage MCP Server

1. **Пользователь запрашивает погоду:**
   - Пользователь отправляет запрос о погоде в AI чат
   - Пример: "Какая погода в Нью-Йорке?"

2. **AI чат использует Weather MCP Server:**
   - AI чат определяет, что нужен инструмент `get_weather`
   - Вызывает инструмент с параметром `location: "New York"`
   - Weather MCP Server возвращает данные о погоде

3. **AI чат обрабатывает данные:**
   - AI чат получает данные о погоде
   - Сжимает информацию, оставляя основной контекст в виде JSON
   - Формирует ответ пользователю

4. **AI чат сохраняет данные в Google Drive:**
   - Если включен инструмент `save_to_drive` из Google Storage MCP Server
   - AI чат вызывает инструмент с параметрами:
     - `accessToken`: токен доступа к Google Drive
     - `fileName`: "ai-chat-results" (по умолчанию)
     - `data`: сжатая информация в формате JSON
   - Google Storage MCP Server:
     - Проверяет наличие файла `ai-chat-results` в корне Google Drive
     - Если файл существует, перезаписывает его
     - Если файл не существует, создает новый и записывает данные

## Запуск всех серверов

Для запуска всех MCP серверов одновременно используйте скрипт:

```bash
./start-servers.sh
```

Этот скрипт:
- Запускает Weather MCP Server на порту 8080
- Запускает Google Storage MCP Server на порту 8081
- Выводит PID процессов для управления
- Позволяет остановить все серверы нажатием Ctrl+C

## Проверка работоспособности

### Проверка Weather MCP Server

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "get_weather",
      "arguments": {
        "location": "New York"
      }
    }
  }'
```

### Проверка Google Storage MCP Server

```bash
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "save_to_drive",
      "arguments": {
        "accessToken": "your_access_token",
        "fileName": "test-file",
        "data": "{\"test\": \"data\"}"
      }
    }
  }'
```

## Troubleshooting

### Сервер не запускается

- Убедитесь, что порт не занят другим процессом
- Проверьте, что Java 17+ установлена
- Проверьте логи в `/tmp/weather-mcp-server.log` и `/tmp/google-storage-mcp-server.log`

### Android приложение не может подключиться к серверу

- Убедитесь, что сервер запущен на хосте
- Для эмулятора используйте `10.0.2.2` вместо `localhost`
- Для физического устройства используйте IP адрес вашего компьютера
- Проверьте, что файрвол не блокирует подключения

### Google Drive API ошибки

- Убедитесь, что Google Drive API включен в Google Cloud Console
- Проверьте, что access token действителен
- Убедитесь, что у пользователя есть права на запись в Google Drive

