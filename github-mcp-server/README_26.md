# GitHub MCP HTTP Wrapper

Локальный HTTP Wrapper для GitHub MCP Server, предоставляющий HTTP интерфейс для работы с GitHub API через MCP протокол.

## Предварительные требования

1. **Java 17+** - должен быть установлен
2. **GitHub Personal Access Token (PAT)** - для доступа к GitHub API

## Создание GitHub Personal Access Token

1. Перейдите на https://github.com/settings/tokens
2. Нажмите "Generate new token (classic)"
3. Выберите необходимые scopes:
   - `repo` - для работы с репозиториями
   - `read:packages` - для доступа к пакетам
   - `read:org` - для работы с организациями
4. Скопируйте созданный токен

## Настройка

Токен можно установить одним из способов (в порядке приоритета):

### Способ 1: В `local.properties` (рекомендуется)

Добавьте токен в файл `local.properties` в корне проекта:

```properties
GITHUB_PERSONAL_ACCESS_TOKEN=your_github_personal_access_token
```

**Преимущества:**
- Централизованное хранение всех API ключей
- Автоматически загружается скриптами
- Уже в `.gitignore`

### Способ 2: Переменная окружения

```bash
export GITHUB_PERSONAL_ACCESS_TOKEN=your_github_personal_access_token
# или
export GITHUB_PAT=your_github_personal_access_token
```

### Способ 3: Файл .env

Создайте файл `.env` в директории `github-mcp-server/`:

```env
GITHUB_PAT=your_github_personal_access_token
```

**Важно:** Добавьте `.env` в `.gitignore`, чтобы не коммитить токен!

## Запуск сервера

### Базовый запуск (порт 8083 по умолчанию)

```bash
cd github-mcp-server
./start-server.sh
```

### Запуск на другом порту

```bash
./start-server.sh 8084
```

### Запуск с переменной окружения

```bash
export GITHUB_PAT=your_token_here
./start-server.sh
```

## Проверка работы

После запуска сервер будет доступен:
- На хосте: `http://localhost:8083/mcp`
- С Android эмулятора: `http://10.0.2.2:8083/mcp`

Проверка доступности:
```bash
curl -X POST http://localhost:8083/mcp \
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
      "name": "github-mcp-server",
      "version": "1.0.0"
    }
  }
}
```

## Использование в Android приложении

1. Запустите сервер: `./start-server.sh`
2. В Android приложении откройте Tools
3. Включите переключатель "GitHub MCP"
4. Используйте команду `/review` для code review с PR diffs

## Доступные инструменты

GitHub MCP Server предоставляет множество инструментов для работы с GitHub:
- `pull_request_read` - получение информации о PR, включая diff
- `get_file_contents` - получение содержимого файлов из репозитория
- `list_pull_requests` - список pull requests
- И многие другие (см. https://mcpservers.org/servers/github/github-mcp-server)

## Решение проблем

### Java не установлена
```bash
# Проверьте версию Java
java -version

# Установите Java 17+ (macOS)
brew install openjdk@17

# Или используйте пакетный менеджер вашей ОС
```

### Порт уже занят
```bash
# Проверьте, что использует порт
lsof -i :8083

# Запустите на другом порту
./start-server.sh 8084
```

### Ошибка аутентификации
- Убедитесь, что GITHUB_PERSONAL_ACCESS_TOKEN установлен правильно в `local.properties`
- Проверьте, что токен не истек
- Убедитесь, что токен имеет необходимые scopes (repo, read:packages, read:org)
- Проверьте, что токен не закомментирован в `local.properties` (не начинается с #)

### Сервер не доступен с эмулятора
- Убедитесь, что сервер запущен на хосте
- Проверьте, что используется правильный адрес: `http://10.0.2.2:8083/mcp`
- Проверьте настройки сети эмулятора
- Убедитесь, что сервер слушает на `0.0.0.0`, а не только на `localhost`

### Ошибка сборки
```bash
# Очистите и пересоберите
cd github-mcp-server
./gradlew clean fatJar

# Проверьте логи на наличие ошибок
```

## Дополнительная информация

- Официальная документация: https://mcpservers.org/servers/github/github-mcp-server
- GitHub MCP Server репозиторий: https://github.com/github/github-mcp-server
