# 22HW_PROJECT_USER_ASSISTENT

## Описание

Реализован функционал автоматизированного ассистента для поддержки пользователей на основе кодовой базы проекта Ai-Chat-Bot. Ассистент использует RAG (Retrieval-Augmented Generation) с Ollama для поиска релевантной информации в файлах проекта и MCP сервер для адаптации ответов под тип пользователя.

## Что было сделано

### 1. Создан новый MCP сервер `user-format-mcp-server`

**Расположение:** `user-format-mcp-server/`

**Функционал:**
- Фильтрация ответов AI по формату пользователя
- Адаптация технических ответов под разные типы пользователей (ребенок, программист, домохозяйка и т.д.)
- Использование Ollama (phi3:medium) для форматирования ответов
- Ограничение ответов до 10 предложений

**Порт:** 8085

**API:**
- `tools/list` - список доступных инструментов
- `tools/call` с методом `format_response` - форматирование ответа

### 2. Обновлен ToolsDialog

**Файл:** `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/components/ToolsDialog.kt`

**Добавлено:**
- Switcher "Project User Assistant" для включения/выключения функционала
- Текстовое поле "User Format Type" для указания типа пользователя (по умолчанию "программист")
- Switcher "Project Files" для включения работы с файлами проекта
- Скрытие загрузки источников, если Project User Assistant включен

### 3. Обновлен PreferencesManager

**Файл:** `core/common/src/main/java/com/example/aiagentchat/core/common/preferences/PreferencesManager.kt`

**Добавлено:**
- `projectUserAssistantEnabled` - состояние включения Project User Assistant
- `userFormatType` - тип формата пользователя (по умолчанию "программист")
- `projectFilesEnabled` - состояние включения работы с файлами проекта

### 4. Обновлен ChatUiState

**Файл:** `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatUiState.kt`

**Добавлено:**
- `projectUserAssistantEnabled: Boolean`
- `userFormatType: String`
- `projectFilesEnabled: Boolean`

**Добавлены действия:**
- `ToggleProjectUserAssistant`
- `SetUserFormatType`
- `ToggleProjectFiles`

### 5. Обновлен ChatViewModel

**Файл:** `feature/chat/src/main/java/com/example/aiagentchat/feature/chat/presentation/chat/ChatViewModel.kt`

**Добавлено:**
- Инициализация `UserFormatMcpApi`
- Обработчики для новых действий
- Метод `handleSendMessageWithProjectUserAssistant` - основной метод для обработки сообщений с RAG + MCP фильтрацией
- Загрузка и сохранение состояния настроек

**Логика работы:**
1. Если включены Project User Assistant + Ollama + Project Files, используется RAG + MCP фильтрация
2. RAG через Project Helper MCP находит релевантную информацию в файлах проекта
3. AI генерирует ответ на основе найденной информации
4. Ответ фильтруется через User Format MCP для адаптации под тип пользователя
5. Отфильтрованный ответ возвращается пользователю

### 6. Обновлен HomeScreen

**Файл:** `feature/home/src/main/java/com/example/aiagentchat/feature/home/presentation/HomeScreen.kt`

**Добавлено:**
- Передача новых параметров в ToolsDialog
- Обработка действий для Project User Assistant

### 7. Обновлен start-servers.sh

**Файл:** `start-servers.sh`

**Добавлено:**
- Запуск User Format MCP Server на порту 8085

## Принцип работы

### Общая схема

```
User -> AI Chat -> Ollama (embedding/reranking) + БД -> MCP (формат ответа) -> AI Chat -> User
```

### Детальный flow

1. **Пользователь заходит в приложение:**
   - Если включены инструменты Ollama + switcher Project User Assistant (+ текстовое поле заполнено - по умолчанию "программист") + Project Files, то AI-chat работает с RAG + MCP
   - Функционал работает в контексте файлов текущего проекта (.md, .kt, .xml, .java, .kts, .sh)

2. **Пользователь пишет вопрос в AI-chat:**
   - Вопрос передается в ChatViewModel

3. **RAG через Project Helper MCP (использует Ollama для embedding/reranking):**
   - Если файлы проекта еще не проиндексированы, выполняется автоматическая индексация
   - Запрос конвертируется в embedding через Ollama (nomic-embed-text) внутри Project Helper MCP
   - Выполняется поиск похожих векторов в проиндексированных файлах проекта
   - Находится до 20 релевантных chunks-кандидатов (если включен reranking) или до 10 (если выключен)
   - Если включен reranking, используется LLM (phi3:medium) для оценки релевантности каждого кандидата
   - Выбираются топ-10 самых релевантных chunks после reranking

4. **Генерация ответа AI:**
   - На основе найденных chunks AI генерирует ответ
   - Ответ содержит информацию из файлов проекта

5. **Фильтрация через MCP:**
   - Ответ передается в User Format MCP Server
   - MCP сервер адаптирует ответ под указанный тип пользователя (из текстового поля)
   - Ответ ограничивается до 10 предложений
   - Отфильтрованный ответ возвращается

6. **Отображение результата:**
   - Отфильтрованный ответ отображается пользователю
   - Ответ адаптирован под тип пользователя (ребенок/домохозяйка/программист)

## Как запустить

### 1. Запуск Ollama (ОБЯЗАТЕЛЬНО ПЕРВЫМ!)

**КРИТИЧЕСКИ ВАЖНО:** Ollama должен быть запущен ДО запуска MCP серверов!

```bash
# Запустите Ollama сервер
ollama serve

# Оставьте этот терминал открытым и запустите серверы в других терминалах
```

**Проверка работы Ollama:**
```bash
# В другом терминале проверьте доступность
curl http://localhost:11434/api/tags

# Должен вернуться JSON с информацией о моделях
```

### 2. Запуск MCP серверов

**ВАЖНО:** Для работы Project User Assistant необходимо запустить два MCP сервера:
- **Project Helper MCP Server** (порт 8081) - для RAG поиска в файлах проекта
- **User Format MCP Server** (порт 8085) - для фильтрации ответов по формату пользователя

```bash
# Запуск всех серверов (включая User Format MCP Server)
./start-servers.sh

# Или запуск серверов по отдельности в разных терминалах:

# Терминал 1: Project Helper MCP Server (обязательно для Project User Assistant)
cd project-helper-mcp-server
./start-server.sh 8081

# Терминал 2: User Format MCP Server (обязательно для Project User Assistant)
cd user-format-mcp-server
./start-server.sh 8085
```

**ВАЖНО:** 
- Оба сервера должны быть запущены одновременно
- Project Helper MCP Server должен быть запущен ДО первого использования
- Если серверы не запущены, приложение покажет понятное сообщение об ошибке

**Проверка работы серверов:**
```bash
# Проверка Project Helper MCP Server (порт 8081)
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

# Проверка User Format MCP Server (порт 8085)
curl -X POST http://localhost:8085/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
```

**Примечание:** Если серверы не запущены, приложение автоматически переключится на обычный режим работы (без RAG + MCP фильтрации) и покажет соответствующее сообщение об ошибке.


### 3. Настройка в приложении

1. Откройте приложение
2. Откройте Tools Dialog (кнопка настроек)
3. Включите "Ollama Vector Search"
4. Включите "Project User Assistant"
5. Укажите тип пользователя в поле "User Format Type" (например, "Ребенок 8 лет", "программист", "домохозяйка")
6. Включите "Project Files" для работы с файлами проекта
7. Индексация файлов проекта будет выполнена автоматически при первом использовании (при отправке первого сообщения)

### 4. Использование

После настройки просто задавайте вопросы в чате. Ассистент будет:
- Искать релевантную информацию в файлах проекта
- Генерировать ответ на основе найденной информации
- Адаптировать ответ под указанный тип пользователя
- Ограничивать ответ до 10 предложений

**Примеры вопросов:**
- "Почему не работает авторизация?"
- "Как работает RAG в этом проекте?"
- "Какие модели AI поддерживаются?"

## Решение проблем

### Проблема: User Format MCP Server не запускается

**Решение:**
1. Проверьте, что порт 8085 свободен: `lsof -i :8085`
2. Проверьте логи: `/tmp/user-format-mcp-server.log`
3. Убедитесь, что Ollama запущен и доступен

### Проблема: Ошибка "Project files not indexed"

**Решение:**
1. Убедитесь, что включен "Project Files" в Tools Dialog
2. Дождитесь завершения индексации (проверьте логи)
3. Перезапустите Project Helper MCP Server, если необходимо

### Проблема: Ответы не адаптируются под тип пользователя

**Решение:**
1. Проверьте, что User Format MCP Server запущен
2. Проверьте, что в поле "User Format Type" указан правильный тип
3. Проверьте логи User Format MCP Server на наличие ошибок

### Проблема: Ollama недоступен

**Решение:**
1. Убедитесь, что Ollama запущен: `ollama serve`
2. Проверьте доступность: `curl http://localhost:11434/api/tags`
3. Для Android эмулятора используйте `http://10.0.2.2:11434`

### Проблема: ConnectException при подключении к MCP серверам

**Ошибка:** `ConnectException: Failed to connect to /10.0.2.2:8081` или `Failed to connect to /10.0.2.2:8085`

**Причина:** Project Helper MCP Server или User Format MCP Server не запущены.

**Решение:**

1. **Убедитесь, что Ollama запущен:**
   ```bash
   # Запустите Ollama в отдельном терминале
   ollama serve
   
   # Проверьте доступность
   curl http://localhost:11434/api/tags
   ```

2. **Запустите Project Helper MCP Server:**
   ```bash
   cd project-helper-mcp-server
   ./start-server.sh 8081
   ```
   
   **Проверка:** Должно появиться сообщение:
   ```
   Starting Project Helper MCP Server on port 8081...
   ✅ Ollama is available at http://localhost:11434
   ```

3. **Запустите User Format MCP Server (в другом терминале):**
   ```bash
   cd user-format-mcp-server
   ./start-server.sh 8085
   ```

4. **Проверьте, что серверы доступны:**
   ```bash
   # Проверка Project Helper MCP Server (порт 8081)
   curl -X POST http://localhost:8081/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
   
   # Проверка User Format MCP Server (порт 8085)
   curl -X POST http://localhost:8085/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
   ```

5. **Для Android эмулятора:**
   - Приложение автоматически использует адрес `http://10.0.2.2` вместо `localhost`
   - Убедитесь, что серверы запущены на хосте (не в эмуляторе)

6. **Если серверы не запущены:**
   - Приложение покажет понятное сообщение об ошибке с инструкциями
   - Автоматический fallback на обычный режим работы не выполняется (нужно запустить серверы)

**Важно:** 
- Оба сервера должны быть запущены одновременно
- Ollama должен быть запущен ДО запуска MCP серверов
- Данные индексации хранятся в памяти Project Helper MCP Server, поэтому при перезапуске сервера нужно заново выполнить индексацию

## Технические детали

### Поддерживаемые форматы файлов

- `.md` - Markdown файлы
- `.kt` - Kotlin файлы
- `.xml` - XML файлы
- `.java` - Java файлы
- `.kts` - Kotlin Script файлы
- `.sh` - Shell скрипты

### Типы пользователей

- **Ребенок** - простой язык, короткие предложения, примеры и аналогии
- **Домохозяйка** - простой язык, бытовые аналогии
- **Программист** - технические термины, точность, конкретность
- **Другие** - адаптация под указанный тип

### Ограничения

- Ответы ограничены до 10 предложений
- Индексация выполняется автоматически при первом использовании (если файлы еще не проиндексированы)
- Состояние настроек сохраняется в SharedPreferences и не зависит от сессии
- При очистке чата все tools настройки сбрасываются в false
- **ВАЖНО:** Данные индексации хранятся в памяти Project Helper MCP Server, а не в Room БД
  - При перезапуске Project Helper MCP Server нужно заново выполнить индексацию
  - Room БД используется только для другого функционала (Ollama Vector Search с файлами)

## Исправления и улучшения

### Исправлено в текущей версии:

1. **Исправлена логика включения Project Helper/Project Files:**
   - При включении тогглов больше не происходит автоматическое подключение к серверу
   - Индексация выполняется автоматически при первом использовании (при отправке первого сообщения)
   - Это предотвращает ошибки подключения при включении тогглов

2. **Исправлен функционал embedding/reranking:**
   - При включенном ProjectUserAssistant правильно используется embedding и reranking через Project Helper MCP
   - Параметр `reranking_enabled` корректно передается в Project Helper MCP Server
   - Project Helper MCP Server использует Ollama для embedding (nomic-embed-text) и reranking (phi3:medium)

3. **Удален неиспользуемый функционал:**
   - Удалены все упоминания и код, связанный с Weather MCP Server
   - Удалены все упоминания и код, связанный с Google Storage MCP Server
   - Удалены все упоминания и код, связанный с Remote Control MCP Server
   - Удалены файлы: WeatherSummaryUseCase.kt, WeatherDataStorage.kt, NotificationManager.kt
   - Обновлен start-servers.sh для запуска только необходимых серверов
   - Обновлены все .md файлы для удаления упоминаний неиспользуемых серверов

4. **Улучшена обработка ошибок:**
   - Все вызовы к MCP серверам обернуты в try-catch для обработки ConnectException
   - При недоступности серверов выполняется автоматический fallback на обычный режим работы
   - Показываются понятные сообщения об ошибках пользователю

5. **Обновлена логика очистки чата:**
   - При нажатии на иконку очистки чата все tools тогглы сбрасываются в false
   - Настройки сбрасываются как в UI, так и в PreferencesManager

## Итоговая проверка

- ✅ Создан User Format MCP Server
- ✅ Обновлен ToolsDialog с новыми элементами UI
- ✅ Реализована логика RAG + MCP фильтрации с правильным использованием embedding/reranking
- ✅ Сохранение состояния настроек
- ✅ Интеграция с существующим функционалом
- ✅ Исправлена логика включения Project Helper/Project Files
- ✅ Исправлен функционал embedding/reranking при включенном ProjectUserAssistant
- ✅ Удален неиспользуемый функционал (weather, google storage, remote control MCP)
- ✅ Обновлена документация
- ✅ Протестирована компиляция проекта
