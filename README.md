# AI Agent Chat

Android приложение для сравнения AI-моделей (DeepSeek, Claude 3.5 Sonnet, GPT-4o Mini, Gemini Pro 1.5) с метриками производительности.

## Архитектура

Проект использует **Multi-Module Clean Architecture** с разделением на слои:

```
app/
├── core/
│   ├── common/          # Общие утилиты
│   ├── database/        # Room database (entities, DAOs)
│   ├── network/         # Network layer (Retrofit, OkHttp)
│   └── uikit/           # Переиспользуемые UI компоненты, тема, цвета
├── feature/
│   ├── chat/            # Chat feature (Clean Architecture)
│   │   ├── data/        # Data layer (repositories, API, mappers)
│   │   ├── domain/      # Domain layer (models, use cases, repositories interfaces)
│   │   └── presentation/# Presentation layer (ViewModels, UI)
│   ├── home/            # Home feature
│   ├── profile/         # Profile feature
│   ├── settings/        # Settings feature (theme switching)
│   ├── patients/        # Patients feature
│   └── appointments/    # Appointments feature
└── app/                 # Main app module (navigation, DI)
```

## Технологии

- **Kotlin 2.0.21**
- **Jetpack Compose 1.9+** (Material 3, edge-to-edge для API>29)
- **Android Navigation 3** (for Compose)
- **Koin 4.1+** (DI)
- **Coroutines 1.10+** / Flow
- **Room 2.6.1** (для хранения истории чата)
- **Retrofit 2.11** + OkHttp 4.12
- **Gradle Version Catalog** (libs.versions.toml)

## Настройка API ключей

Добавьте в файл `local.properties`:

```properties
DEEPSEEK_API_KEY=your_deepseek_api_key_here
OPENROUTER_API_KEY=your_openrouter_api_key_here
CONTEXT7_API_KEY=your_context7_api_key_here
MCP_SERVER_URL=http://10.0.2.2:8080/
GOOGLE_STORAGE_MCP_SERVER_URL=http://10.0.2.2:8081/
GOOGLE_DRIVE_CLIENT_ID=your_google_drive_client_id_here.apps.googleusercontent.com
GOOGLE_DRIVE_ACCESS_TOKEN=your_google_drive_access_token_here
```

## Ollama Vector Search

Проект поддерживает векторный поиск с использованием Ollama для индексации документов и семантического поиска в чате.

### Настройка Ollama

1. **Установите и запустите Ollama:**
   ```bash
   ./setup-ollama.sh
   ```
   
   Этот скрипт:
   - Проверяет установку Ollama (устанавливает через Homebrew, если нужно)
   - Запускает Ollama сервер на `http://localhost:11434`
   - Скачивает embedding модель `nomic-embed-text`

2. **Проверьте подключение (для эмулятора):**
   ```bash
   ./test-ollama-connection.sh
   ```
   
   Скрипт проверит:
   - Доступность Ollama на Mac
   - Доступность Ollama из эмулятора (через `10.0.2.2`)
   - Работу генерации embeddings
   - Интернет на эмуляторе

3. **Для Android эмулятора:**
   - Приложение автоматически использует `http://10.0.2.2:11434`
   - `10.0.2.2` - специальный IP адрес эмулятора для доступа к localhost хоста
   - Интернет на эмуляторе работает независимо от Ollama

3. **Подготовьте файл для индексации:**
   
   **Вариант 1 (автоматический - рекомендуется):**
   - При первом включении Ollama приложение автоматически создаст тестовый файл `README.md` во внутреннем хранилище
   - Вы можете заменить его своим файлом позже
   
   **Вариант 2 (ручной - не требует разрешений):**
   - Скопируйте `README.md` во внутреннее хранилище приложения:
     ```bash
     # Сначала скопируйте на внешнее хранилище
     adb push README.md /sdcard/README.md
     # Затем скопируйте во внутреннее хранилище через shell
     adb shell "run-as com.example.aiagentchat cp /sdcard/README.md /data/data/com.example.aiagentchat/files/README.md"
     ```
   
   **Вариант 3 (требует разрешений на чтение файлов):**
   - Скопируйте `README.md` на внешнее хранилище:
     ```bash
     adb push README.md /sdcard/README.md
     ```
   - Приложение запросит разрешения на чтение файлов при первом включении Ollama
   - После предоставления разрешений приложение сможет прочитать файл
   
   **Проверка файла:**
   - Приложение проверяет файл в следующем порядке:
     1. Внутреннее хранилище (`/data/data/com.example.aiagentchat/files/README.md`)
     2. Внешнее хранилище приложения (`getExternalFilesDir`)
     3. Стандартные пути (`/sdcard/README.md`, `/storage/emulated/0/README.md`, и т.д.)
   - Все проверенные пути логируются в Logcat с тегом `ChatViewModel`

### Использование Ollama в приложении

1. **Включите Ollama:**
   - Откройте приложение
   - Нажмите на иконку настроек (⚙️)
   - Включите "Ollama Vector Search"

2. **Автоматическая индексация:**
   - При первом включении Ollama автоматически индексирует `README.md`
   - Прогресс индексации отображается в логах
   - После завершения индексации появится уведомление

3. **Использование векторного поиска:**
   - После индексации задавайте вопросы в чате
   - AI будет использовать контекст из проиндексированных документов
   - Векторы хранятся в локальной БД и не требуют повторной индексации

### Логика работы

1. **Индексация:**
   - Текст разбивается на чанки (500-1000 токенов)
   - Между чанками перекрытие 50-100 токенов
   - Для каждого чанка генерируется embedding через Ollama
   - Векторы нормализуются к диапазону [0,1]
   - Сохраняются в локальной БД с хешем файла

2. **Поиск:**
   - Вопрос пользователя конвертируется в embedding
   - Выполняется поиск похожих векторов (cosine similarity)
   - Найденные чанки добавляются в контекст запроса
   - AI использует контекст для ответа

3. **Обновление индекса:**
   - Если файл изменился (изменился хеш), индексация повторяется
   - Если файл тот же, используется существующий индекс

### Проверка работоспособности

1. **Проверьте Ollama сервер:**
   ```bash
   curl http://localhost:11434/api/tags
   ```

2. **Проверьте embedding модель:**
   ```bash
   ollama list | grep nomic-embed-text
   ```

3. **Проверьте логи приложения:**
   - Откройте Logcat в Android Studio
   - Фильтр: `ChatViewModel` или `TextIndexingService`
   - Должны быть логи о процессе индексации

4. **Проверьте работу поиска:**
   - Включите Ollama в настройках
   - Дождитесь завершения индексации
   - Задайте вопрос, связанный с содержимым README.md
   - AI должен использовать контекст из документа

## MCP Серверы

Проект поддерживает работу с несколькими MCP (Model Context Protocol) серверами. Подробная документация по каждому серверу находится в соответствующих README файлах:

- **Weather MCP Server** - см. [weather-mcp-server/README.md](weather-mcp-server/README.md)
- **Google Storage MCP Server** - см. [google-storage-mcp-server/README.md](google-storage-mcp-server/README.md)
- **Remote Control MCP Server** - см. [remote-control-mcp-server/README.md](remote-control-mcp-server/README.md)

### Быстрый старт

1. **Запустите MCP серверы:**
   ```bash
   ./start-servers.sh
   ```

2. **Откройте приложение** и нажмите на иконку настроек (⚙️) в верхней панели

3. **Включите нужные инструменты** для каждого сервера в диалоге "MCP Tools"

4. **Состояние инструментов сохраняется** между сессиями

## Логика работы

### Поток данных: User → Weather MCP Server → AI Chat → Google Storage MCP Server

1. Пользователь запрашивает погоду у AI чата
2. AI чат использует инструмент `get_weather` из Weather MCP Server
3. Weather MCP Server возвращает данные о погоде
4. AI чат сжимает полученную информацию в JSON формат
5. AI чат использует инструменты `delete_file_from_drive` и `save_to_drive` из Google Storage MCP Server
6. Google Storage MCP Server удаляет старый файл `ai-chat-results` (если существует) и создает новый с обновленными данными

### Поток данных: User → AI Chat → Remote Control MCP Server → Connected Real Device

1. Пользователь включает Remote Control в настройках приложения
2. Пользователь указывает Device ID (опционально) в настройках Remote Device Control
3. Пользователь включает инструменты Remote Control MCP Server (например, `press_home`, `open_app`, `take_screenshot`)
4. Пользователь просит AI выполнить действие на подключенном устройстве (например, "Нажми кнопку Home на реальном устройстве" или "Открой Chrome на подключенном устройстве")
5. AI чат определяет нужный инструмент и автоматически добавляет Device ID в аргументы (если он указан в настройках)
6. AI чат отправляет запрос в Remote Control MCP Server с параметром `deviceId`
7. Remote Control MCP Server выполняет ADB команду на указанном устройстве: `adb -s [deviceId] shell [command]`
8. Remote Control MCP Server возвращает результат выполнения
9. AI чат сообщает пользователю о результате

## Формат Toon

Приложение использует формат **Toon (Token-Oriented Object Notation)** для взаимодействия с AI моделями. Toon - это эффективный формат для работы с LLM, оптимизированный для минимального использования токенов (экономия 30-60% по сравнению с JSON).

### Использование Toon

Toon формат используется для:
- **Экспорта истории чата** - история экспортируется в формате Toon для экономии места
- **Форматирования контекста** - системные сообщения с контекстом форматируются в Toon для экономии токенов
- **Эффективной передачи данных в LLM** - контекст предыдущих обсуждений передается в Toon формате

### Экспорт в Toon

1. Откройте приложение
2. Нажмите на иконку экспорта (📥) в верхней панели
3. История чата будет экспортирована в формате Toon
4. Вы можете скопировать экспортированные данные в буфер обмена

### Форматирование контекста

При отправке сообщений с контекстом, система автоматически форматирует контекст в Toon формат, что позволяет:
- Сэкономить до 60% токенов по сравнению с JSON
- Передать больше контекста в одном запросе
- Улучшить понимание AI модели за счет структурированного формата

## Remote Control и Android Devices

### Настройка Remote Control

1. **Установите ADB:**
   - macOS: `brew install android-platform-tools`
   - Linux: `sudo apt-get install android-tools-adb`
   - Windows: Скачайте [Android SDK Platform Tools](https://developer.android.com/studio/releases/platform-tools)

2. **Подключите устройство:**
   - Для эмулятора: просто запустите эмулятор
   - Для реального устройства:
     - Включите режим разработчика
     - Включите отладку по USB
     - Подключите устройство через USB

3. **Проверьте подключение:**
   ```bash
   adb devices
   ```

4. **Запустите Remote Control MCP Server:**
   ```bash
   ./start-servers.sh
   ```

5. **Включите Remote Control в приложении:**
   - Откройте настройки (⚙️)
   - Включите "Remote Device Control"
   - Включите нужные инструменты Remote Control MCP Server

### Доступные команды

- **list_devices** - Получить список подключенных устройств
- **press_home** - Нажать кнопку Home
- **press_back** - Нажать кнопку Back
- **open_app** - Открыть приложение по package name
- **minimize_app** - Свернуть текущее приложение
- **take_screenshot** - Сделать скриншот экрана
- **execute_adb_command** - Выполнить произвольную ADB команду

Все команды поддерживают опциональный параметр `deviceId` для работы с конкретным устройством.

Подробнее см. [remote-control-mcp-server/README.md](remote-control-mcp-server/README.md)

## Архитектурные принципы

### Clean Architecture
- **Domain Layer**: Бизнес-логика, use cases, модели (не зависит от других слоев)
- **Data Layer**: Реализация репозиториев, API клиенты, маппинг DTO → Domain
- **Presentation Layer**: ViewModels с UDF паттерном, Compose UI

### UDF Pattern (Unidirectional Data Flow)
- **Action**: События от UI
- **UiState**: Состояние UI (data class)
- **Event**: Одноразовые события (sealed interface)

### ViewModel Pattern
- Все методы приватные, кроме `onAction(Action)`
- StateFlow для состояния
- SharedFlow для событий

## Сборка

```bash
./gradlew assembleDebug
```

## Запуск и проверка работоспособности

### Шаг 1: Подготовка окружения

1. **Установите ADB (для Remote Control):**
   ```bash
   # macOS
   brew install android-platform-tools
   
   # Linux
   sudo apt-get install android-tools-adb
   
   # Windows
   # Скачайте Android SDK Platform Tools и добавьте в PATH
   ```

2. **Проверьте подключение устройств:**
   ```bash
   adb devices
   ```
   Должен показать список подключенных устройств/эмуляторов.

### Шаг 2: Настройка Ollama (опционально)

Если вы хотите использовать векторный поиск:

```bash
# Установка и запуск Ollama
./setup-ollama.sh

# Подготовка файла для индексации
adb push README.md /sdcard/README.md
```

### Шаг 3: Запуск MCP серверов

```bash
./start-servers.sh
```

Этот скрипт запустит все MCP серверы в фоновом режиме:
- **Weather MCP Server** (порт 8080)
- **Google Storage MCP Server** (порт 8081)
- **Remote Control MCP Server** (порт 8082)

**Проверка запуска:**
```bash
# Проверка логов
tail -f /tmp/weather-mcp-server.log
tail -f /tmp/google-storage-mcp-server.log
tail -f /tmp/remote-control-mcp-server.log

# Проверка доступности через curl
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

curl -X POST http://localhost:8082/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
```

**Остановка серверов:**
```bash
# Найти процессы
ps aux | grep mcp-server

# Остановить все серверы
pkill -f mcp-server
```

### Шаг 4: Сборка и запуск приложения

1. **Соберите приложение:**
   ```bash
   ./gradlew assembleDebug
   ```

2. **Установите на эмулятор/устройство:**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Запустите приложение** на эмуляторе или устройстве.

### Шаг 5: Настройка в приложении

1. **Откройте приложение** на эмуляторе/устройстве
2. **Нажмите на иконку настроек** (⚙️) в верхней панели
3. **Включите нужные функции:**
   - **Weather Notifications** (опционально) - для уведомлений о погоде
   - **Test Mode** (опционально) - для тестирования уведомлений
   - **Remote Device Control** - для управления подключенными устройствами
   - **Ollama Vector Search** - для векторного поиска по документам
4. **Настройте Remote Device Control:**
   - Включите переключатель "Remote Device Control"
   - **Укажите Device ID** (опционально):
     - Если оставить пустым, будет использоваться устройство по умолчанию
     - Для работы с конкретным устройством укажите его ID (например, `emulator-5554` или `ABC123XYZ`)
     - Чтобы узнать доступные устройства, используйте инструмент `list_devices` из Remote Control MCP Server
5. **Включите инструменты для каждого MCP сервера:**
   - Найдите нужный сервер в списке (Weather, Google Storage, Remote Control)
   - Включите нужные инструменты (например, для Remote Control: `press_home`, `open_app`, `take_screenshot`)

**Примечание:** Состояние всех настроек (включая Device ID) сохраняется между сессиями.

### Шаг 6: Проверка Ollama (если включено)

1. **Включите Ollama в настройках:**
   - Настройки → Ollama Vector Search → Включить
   - Дождитесь завершения индексации (проверьте логи)

2. **Проверьте работу:**
   - Задайте вопрос, связанный с содержимым README.md
   - AI должен использовать контекст из документа
   - Проверьте логи на наличие ошибок

### Шаг 7: Проверка Remote Control

1. **Подключите реальное устройство (если нужно):**
   ```bash
   # Включите режим разработчика на устройстве
   # Включите отладку по USB
   # Подключите через USB
   adb devices
   ```
   
   **Пример вывода:**
   ```
   List of devices attached
   emulator-5554    device
   ABC123XYZ        device
   ```

2. **Настройте Remote Control в приложении:**
   - Настройки → Remote Device Control → Включить
   - **Укажите Device ID** (если нужно работать с конкретным устройством):
     - Например: `emulator-5554` или `ABC123XYZ`
     - Если оставить пустым, будет использоваться устройство по умолчанию
   - Включите инструменты Remote Control MCP Server

3. **Проверьте работу через AI:**
   - Попросите AI: **"Нажми кнопку Home на подключенном устройстве"**
   - Попросите AI: **"Открой Chrome на реальном устройстве"**
   - Попросите AI: **"Сделай скриншот подключенного устройства"**
   - Попросите AI: **"Открой YouTube на подключенном устройстве"**
   
   **Важно:** Если вы указали Device ID в настройках, все команды будут выполняться на указанном устройстве. Если Device ID не указан, будет использоваться устройство по умолчанию.

4. **Проверьте логи сервера:**
   ```bash
   tail -f /tmp/remote-control-mcp-server.log
   ```
   
   В логах вы увидите, на каком устройстве выполняются команды:
   ```
   Executing ADB command: shell input keyevent KEYCODE_HOME on device: emulator-5554
   ```

### Шаг 8: Проверка Toon формата

1. **Экспорт истории в Toon:**
   - Откройте приложение
   - Отправьте несколько сообщений
   - Нажмите на иконку экспорта (📥) в верхней панели
   - Проверьте, что экспортированные данные в формате Toon (компактный формат с таблицами)

2. **Проверка использования Toon для контекста:**
   - Отправьте несколько сообщений с контекстом
   - Проверьте логи приложения (контекст должен быть в Toon формате)
   - Toon формат экономит до 60% токенов по сравнению с JSON

### Шаг 9: Устранение проблем

**Проблема: "README.md file not found"**
- **Решение:** 
  1. Убедитесь, что файл скопирован на устройство: `adb push README.md /sdcard/README.md`
  2. Проверьте права доступа к файлу
  3. Попробуйте использовать другой путь к файлу

**Проблема: "Failed to generate embedding"**
- **Решение:**
  1. Проверьте, что Ollama сервер запущен: `curl http://localhost:11434/api/tags`
  2. Проверьте, что модель установлена: `ollama list | grep nomic-embed-text`
  3. Проверьте подключение из эмулятора: `adb shell curl http://10.0.2.2:11434/api/tags`

**Проблема: Индексация не завершается**
- **Решение:**
  1. Проверьте логи приложения (Logcat)
  2. Убедитесь, что Ollama сервер доступен
  3. Проверьте размер файла (большие файлы могут индексироваться долго)

**Проблема: "Server not found: remote-docker-mcp-server"**
- **Решение:** Приложение автоматически мигрирует старые настройки. Если проблема сохраняется:
  1. Откройте настройки приложения
  2. Отключите и снова включите Remote Device Control
  3. Перезапустите приложение

**Проблема: Команды выполняются не на том устройстве**
- **Решение:** 
  1. Проверьте, что Device ID указан правильно в настройках
  2. Используйте инструмент `list_devices` для получения списка доступных устройств
  3. Убедитесь, что указанный Device ID присутствует в списке `adb devices`

**Проблема: Сервер не запускается**
- Проверьте, что порт свободен: `lsof -i :8082`
- Проверьте логи: `tail -f /tmp/remote-control-mcp-server.log`
- Убедитесь, что скрипт имеет права на выполнение: `chmod +x start-servers.sh`

**Проблема: Устройство не найдено**
- Проверьте подключение: `adb devices`
- Убедитесь, что включена отладка по USB
- Перезапустите ADB: `adb kill-server && adb start-server`

**Проблема: Команды не выполняются**
- Проверьте, что сервер запущен
- Проверьте, что инструменты включены в настройках
- Проверьте логи сервера на наличие ошибок

## Тестирование

- **Unit-тесты**: JUnit5 + MockK
- **UI-тесты**: Compose Testing
- **Именование**: `givenX_whenY_thenZ()`

## License

MIT
