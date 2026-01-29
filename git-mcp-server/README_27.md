# Git MCP Server

HTTP Wrapper для выполнения git команд на хосте. Позволяет Android приложению получать доступ к git репозиторию через HTTP/JSON-RPC интерфейс.

## Назначение

Git MCP Server решает проблему доступа к git репозиторию с Android устройства/эмулятора. Поскольку на Android нет прямого доступа к git репозиторию проекта, сервер выполняет git команды на хосте и возвращает результаты через HTTP.

## Требования

- Java 17+
- Git установлен на хосте
- Проект должен быть git репозиторием

## Настройка

### Способ 1: В `local.properties` (рекомендуется)

Добавьте путь к проекту в файл `local.properties` в корне проекта:

```properties
PROJECT_ROOT=/Users/Victor/work/hw-1-AI-chat-bot
```

### Способ 2: Передать как аргумент

```bash
./start-server.sh 8084 /path/to/project
```

## Запуск сервера

### Базовый запуск (порт 8084 по умолчанию)

```bash
cd git-mcp-server
./start-server.sh
```

### Запуск на другом порту

```bash
./start-server.sh 8085
```

### Запуск с указанием project root

```bash
./start-server.sh 8084 /Users/Victor/work/hw-1-AI-chat-bot
```

## Проверка работы

После запуска сервер будет доступен:
- На хосте: `http://localhost:8084/mcp`
- С Android эмулятора: `http://10.0.2.2:8084/mcp`

### Проверка доступности

```bash
curl -X POST http://localhost:8084/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
```

Ожидаемый ответ:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "serverInfo": {
      "name": "git-mcp-server",
      "version": "1.0.0"
    },
    "capabilities": {
      "tools": {
        "getChangedFiles": true,
        "readFileContent": true
      }
    }
  }
}
```

### Тест получения измененных файлов

```bash
curl -X POST http://localhost:8084/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":2,
    "method":"tools/call",
    "params":{
      "name":"get_changed_files",
      "arguments":{
        "projectRoot":"/Users/Victor/work/hw-1-AI-chat-bot"
      }
    }
  }'
```

## Реализованные инструменты

### `get_changed_files`

Получает список измененных файлов из git репозитория.

**Параметры:**
- `projectRoot` (string, опциональный) - путь к корню проекта. Если не указан, используется значение по умолчанию.

**Возвращает:**
- Массив путей к измененным файлам (только .kt, .xml, .java, .kts, .sh)

**Пример:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "get_changed_files",
    "arguments": {
      "projectRoot": "/Users/Victor/work/hw-1-AI-chat-bot"
    }
  }
}
```

### `read_file_content`

Читает содержимое файла из проекта.

**Параметры:**
- `filePath` (string, обязательный) - путь к файлу (относительный или абсолютный)
- `projectRoot` (string, опциональный) - путь к корню проекта

**Возвращает:**
- Содержимое файла в виде строки

**Пример:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "read_file_content",
    "arguments": {
      "filePath": "app/src/main/AndroidManifest.xml",
      "projectRoot": "/Users/Victor/work/hw-1-AI-chat-bot"
    }
  }
}
```

## Интеграция с Android приложением

GitFileDetector автоматически использует Git MCP Server, если локальный git недоступен:

1. Сначала пытается выполнить git команды локально
2. Если локальный git недоступен, использует Git MCP Server
3. Если оба варианта не работают, показывает понятное сообщение об ошибке

## Решение проблем

### Проблема: "Not a git repository"

**Причина**: Указанный путь не является git репозиторием

**Решение**:
1. Убедитесь, что проект инициализирован как git репозиторий: `git init`
2. Проверьте, что .git директория существует
3. Укажите правильный путь к проекту в `local.properties` или при запуске сервера

### Проблема: "Port 8084 is already in use"

**Причина**: Порт 8084 уже занят другим процессом

**Решение**:
1. Проверьте, что использует порт:
   ```bash
   lsof -i :8084
   ```
2. Остановите процесс или запустите на другом порту:
   ```bash
   ./start-server.sh 8085
   ```

### Проблема: "Git command failed"

**Причина**: Git не установлен или недоступен

**Решение**:
1. Проверьте, что git установлен: `git --version`
2. Убедитесь, что git доступен в PATH
3. Проверьте права доступа к проекту

### Проблема: Сервер не доступен с эмулятора

**Решение**:
1. Убедитесь, что сервер запущен на хосте
2. Проверьте, что используется правильный адрес: `http://10.0.2.2:8084/mcp`
3. Проверьте настройки сети эмулятора
4. Убедитесь, что сервер слушает на `0.0.0.0`, а не только на `localhost`

## Дополнительная информация

- Использует JSON-RPC 2.0 протокол
- Поддерживает CORS для работы с Android приложением
- Автоматически фильтрует файлы по расширениям (.kt, .xml, .java, .kts, .sh)
- Возвращает только существующие файлы
