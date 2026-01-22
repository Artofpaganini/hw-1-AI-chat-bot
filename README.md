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
│   └── home/            # Home feature
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
GITHUB_PERSONAL_ACCESS_TOKEN=your_github_personal_access_token_here
```

**GitHub Personal Access Token:**
- Создайте токен на https://github.com/settings/tokens
- Выберите scopes: `repo`, `read:packages`, `read:org`
- Используется для GitHub MCP Server (code review с PR diffs)

## Project Review Mode - Работа с файлами проекта

Проект поддерживает режим работы с файлами текущего проекта (.kt, .xml, .java, .kts, .sh) через Ollama Vector Search.

### Настройка Project Review Mode

1. **Запустите Ollama сервер (ОБЯЗАТЕЛЬНО!):**
   ```bash
   ./setup-ollama.sh
   ```

2. **Запустите Project Helper MCP Server:**
   ```bash
   cd project-helper-mcp-server
   ./start-server.sh [PORT] [PROJECT_ROOT] [OLLAMA_URL]
   
   # Пример (по умолчанию):
   ./start-server.sh 8081 /Users/Victor/work/hw-1-AI-chat-bot http://localhost:11434
   ```

3. **В приложении:**
   - Откройте настройки (⚙️)
   - Включите "Ollama Vector Search"
   - Включите "Project Review Mode" (внутри Ollama Vector Search)
   - При включении автоматически индексируются все файлы проекта (.kt, .xml, .java, .kts, .sh)

4. **Использование:**
   - Введите `/help ваш вопрос` в чате для получения ответа на основе документации проекта
   - При включенном Project Review Mode + Ollama Vector Search все запросы используют RAG с файлами проекта

Подробнее см. [20HW_PROJECT_ASSISTENT.md](20HW_PROJECT_ASSISTENT.md)

## Project Team Assistant - Ассистент команды

Проект поддерживает ассистента команды, который анализирует кодовую базу и генерирует технические задачи на основе проблемных мест.

### Настройка Project Team Assistant

1. **Запустите Ollama сервер (ОБЯЗАТЕЛЬНО!):**
   ```bash
   ./setup-ollama.sh
   ```

2. **Запустите Project Helper MCP Server:**
   ```bash
   cd project-helper-mcp-server
   ./start-server.sh [PORT] [PROJECT_ROOT] [OLLAMA_URL]
   
   # Пример (по умолчанию):
   ./start-server.sh 8081 /Users/Victor/work/hw-1-AI-chat-bot http://localhost:11434
   ```

3. **В приложении:**
   - Откройте настройки (⚙️)
   - Включите "Ollama Vector Search"
   - Включите "Project Team Assistant" (внутри Ollama Vector Search)
   - Опционально включите "Local MCP Server" (внутри Project Team Assistant)
   - При включении автоматически начнется индексация файлов проекта

4. **Использование:**
   - Введите `/tasks` в чате
   - AI проанализирует проект и сгенерирует 3 технические задачи:
     - 1 критическая
     - 1 важная
     - 1 обычная
   - Каждая задача содержит:
     - Название (max 300 tokens)
     - Источник проблемы (max 300 tokens)
     - Описание (max 1000 tokens)
     - Ожидаемый результат (max 500 tokens)

### Правила для разработки

AI ищет проблемные места по следующим правилам:
- Memory leaks (утечки памяти)
- Crashes (потенциальные краши)
- Non security values (небезопасные значения)
- Clean architecture (нарушения чистой архитектуры)
- Non thread safe logic (непотокобезопасная логика)

Подробнее см. [23HW_PROJECT_TEAM_ASSISTENT.md](23HW_PROJECT_TEAM_ASSISTENT.md)

## Git MCP Server - Доступ к Git репозиторию

Проект поддерживает доступ к git репозиторию через Git MCP Server. Это решает проблему доступа к git с Android устройства/эмулятора.

### Настройка Git MCP Server

1. **Запустите Git MCP Server:**
   ```bash
   cd git-mcp-server
   ./start-server.sh
   ```
   
   Сервер будет доступен на порту 8084 (по умолчанию).

2. **Настройте PROJECT_ROOT (опционально):**
   
   Добавьте в `local.properties`:
   ```properties
   PROJECT_ROOT=/Users/Victor/work/hw-1-AI-chat-bot
   ```

3. **Использование:**
   - Команда `/review` автоматически использует Git MCP Server, если локальный git недоступен
   - GitFileDetector автоматически переключается на Git MCP Server при необходимости

Подробнее см. [git-mcp-server/README.md](git-mcp-server/README.md)

## Code Review System

Проект поддерживает автоматический code review через команду `/review`.

### Настройка

#### 1. Запуск Git MCP Server (обязательно)

Git MCP Server необходим для доступа к git репозиторию с Android устройства:

```bash
cd git-mcp-server
./start-server.sh
```

Сервер будет доступен на порту 8084 (по умолчанию).

**Опционально:** Настройте PROJECT_ROOT в `local.properties`:
```properties
PROJECT_ROOT=/Users/Victor/work/hw-1-AI-chat-bot
```

#### 2. Запуск GitHub MCP Server (опционально, для PR diffs)

1. **Создайте GitHub Personal Access Token:**
   - Перейдите на https://github.com/settings/tokens
   - Нажмите "Generate new token (classic)"
   - Выберите scopes: `repo`, `read:packages`, `read:org`
   - Скопируйте токен

2. **Установите токен в `local.properties`:**
   ```properties
   GITHUB_PERSONAL_ACCESS_TOKEN=your_github_personal_access_token
   ```

3. **Запустите GitHub MCP Server:**
   ```bash
   cd github-mcp-server
   ./start-server.sh
   ```
   
   Сервер будет доступен на порту 8083 (по умолчанию).

#### 3. Или запустите все серверы сразу:

```bash
./start-servers.sh
```

#### 4. В приложении:

- Откройте Tools (⚙️)
- Включите переключатель "GitHub MCP" (опционально)
- Включите переключатель "Project Review Mode" (опционально)

#### 5. Использование:

- Введите `/review` в чате для автоматического code review измененных файлов
- Git MCP Server автоматически используется для получения списка измененных файлов
- GitHub MCP используется для получения PR diffs (если настроен)

Подробнее см. [21HW_PROJECT_REVIEW_ASSISTENT.md](21HW_PROJECT_REVIEW_ASSISTENT.md)

## Ollama Vector Search (Оффлайн режим)

**⚠️ ВАЖНО: Эмулятор может работать с Ollama полностью локально, даже когда интернет полностью отключен!**

См. [EMULATOR_OFFLINE_SETUP.md](./EMULATOR_OFFLINE_SETUP.md) для инструкции по настройке полностью оффлайн режима.

Проект поддерживает векторный поиск с использованием Ollama для индексации документов и семантического поиска в чате.

**🌐 Важно: Оффлайн режим**
- Когда Ollama включен, приложение работает **полностью оффлайн** (без интернета)
- Все запросы идут только на локальный Ollama сервер на Mac (`http://10.0.2.2:11434`)
- Облачные API (DeepSeek, OpenAI, Claude и т.д.) **НЕ используются** когда Ollama включен
- Работает даже когда интернет на эмуляторе выключен

### Настройка Ollama

1. **Установите и запустите Ollama:**
   ```bash
   ./setup-ollama.sh
   ```
   
   Этот скрипт:
   - Проверяет установку Ollama (устанавливает через Homebrew, если нужно)
   - Запускает Ollama сервер на `http://localhost:11434`
   - Скачивает embedding модель `nomic-embed-text`
   - Скачивает модели для чата: `llama3.2:3b` (по умолчанию) и `phi3:medium` (для reranking)
   
   **Дополнительно:** Для установки других моделей используйте:
   ```bash
   ./install-ollama-models.sh
   # или интерактивно:
   ./install-ollama-models.sh --interactive
   ```
   
   Популярные модели для чата:
   - `llama3.2:3b` - быстрая и легкая (рекомендуется)
   - `llama3.2:1b` - очень быстрая, минимальные требования
   - `llama3:8b` - более мощная
   - `mistral:7b` - альтернатива Llama
   - `phi3:mini` - легкая модель от Microsoft
   - `qwen2.5:7b` - китайская модель

2. **Проверьте подключение (для эмулятора):**
   ```bash
   ./test-ollama-connection.sh
   ```
   
   Скрипт проверит:
   - Доступность Ollama на Mac
   - Доступность Ollama из эмулятора (через `10.0.2.2`)
   - Работу генерации embeddings
   - Интернет на эмуляторе (опционально, не требуется для работы Ollama)

3. **Для Android эмулятора:**
   - Приложение автоматически использует `http://10.0.2.2:11434`
   - `10.0.2.2` - специальный IP адрес эмулятора для доступа к localhost хоста
   - **Оффлайн режим:** Интернет на эмуляторе НЕ требуется - все запросы идут только на локальный Ollama сервер
   - Интернет на эмуляторе может быть выключен - приложение будет работать через локальный Ollama
   - **Полностью оффлайн:** Эмулятор может работать с Ollama даже когда интернет полностью отключен на Mac
   - `10.0.2.2` - это локальное подключение через виртуальную сеть эмулятора, не требует интернета
   
   **Важно:** Если при включении Ollama появляется ошибка подключения:
   - Убедитесь, что Ollama запущен на Mac: `ollama serve`
   - Проверьте доступность: `curl http://localhost:11434/api/tags`
   - Проверьте из эмулятора: `adb shell curl http://10.0.2.2:11434/api/tags`
   - Если список моделей не загрузился, используется модель по умолчанию (`llama3.2:3b`)

### Использование Ollama в приложении (RAG)

1. **Включите Ollama (оффлайн режим):**
   - Откройте приложение
   - Нажмите на иконку настроек (⚙️)
   - Включите "Ollama Vector Search" (switcher)
   - **🌐 Оффлайн режим активирован:** Все запросы теперь идут только на локальный Ollama сервер
   - При включении автоматически запускается скрипт `setup-ollama.sh` (на Mac)
   - Проверяется подключение к Ollama серверу (локально, без интернета)
   - Автоматически загружается список доступных моделей
   - **Важно:** Облачные API больше не используются, даже если интернет доступен
   
2. **Выберите локальную модель для чата:**
   - В настройках (⚙️) в секции "Ollama Vector Search"
   - Выберите модель из списка "Local Chat Model"
   - Модель используется для:
     - Обычного чата (когда нет индексированных документов)
     - RAG чата (с индексированными документами)
   - По умолчанию: `llama3.2:3b`

3. **Прикрепите файл для индексации (опционально):**
   - После включения Ollama появится кнопка "Select File"
   - Нажмите на кнопку и выберите файл (.md, .txt, или .pdf)
   - Поддерживаемые форматы: `.md`, `.txt` (`.pdf` в разработке)
   - Файл будет скопирован во внутреннее хранилище приложения
   - После выбора файла автоматически начнется индексация

4. **Индексация:**
   - Прогресс индексации отображается в логах
   - После завершения индексации появится уведомление
   - Проиндексированные данные сохраняются в JSON файл (`vector_index.json`)

5. **Использование RAG (Retrieval-Augmented Generation) с LLM-as-a-Reranker:**
   - После индексации задавайте вопросы в чате
   - **Сценарий 1: Ollama включен (switcher ON)**
     - **Retrieval (поиск кандидатов):**
       - Ollama используется для векторного поиска (embeddings через `nomic-embed-text`)
       - Находятся до 10 релевантных chunks-кандидатов из проиндексированных документов
       - Поиск выполняется по косинусному сходству
     - **Reranking (если включен):**
       - Используется LLM (`phi3:medium`) для оценки релевантности каждого кандидата
       - Для каждого чанка формируется промпт с запросом и текстом чанка
       - LLM возвращает оценку релевантности (0.0-1.0)
       - Chunks сортируются по оценке релевантности
       - Выбираются топ-3 чанка с наивысшей оценкой
       - В конце ответа добавляется фраза "С Ollama и фильтрацией"
     - **Без reranking (switcher OFF):**
       - Используются все найденные chunks без reranking
       - Выбираются топ-3 чанка по косинусному сходству
       - В конце ответа добавляется фраза "С Ollama и без фильтрацией"
     - **Generation (генерация ответа):**
       - Контекст из топ-3 chunks передается в AI chat (выбранная модель)
       - В контексте для каждого chunk указывается оценка релевантности (если reranking включен) или похожести (если выключен)
       - AI chat формирует ответ на основе контекста
       - AI chat сам создает summary для каждого chunk'а
       - В ответе отображается:
         - Основной ответ от AI chat
         - Номера chunks, использованных для ответа
         - Оценка релевантности для каждого chunk (если reranking включен)
         - Краткое summary каждого chunk'а (сформированное AI chat)
   - **Сценарий 2: Ollama включен, но нет индексированных документов**
     - Используется локальная LLM модель для обычного чата (оффлайн режим)
     - В конце ответа добавляется фраза "🌐 OFFLINE: С Ollama (локальная модель: [название модели])"
     - **Работает без интернета** - все запросы идут только на локальный Ollama сервер
   - **Сценарий 3: Ollama выключен (switcher OFF)**
     - AI chat формирует ответ самостоятельно через облачные API (без использования chunks)
     - В конце ответа добавляется фраза "Без Ollama"
     - **Требует интернет** для работы с облачными API

5. **Просмотр индексированных данных:**
   - Нажмите на иконку "Json Export" (📄) в верхней панели
   - Откроется диалог с содержимым JSON файла
   - JSON содержит:
     - Индексированные документы с текстом и векторами
     - История запросов с embeddings
     - Найденные похожие чанки для каждого запроса

### Логика работы RAG

1. **Индексация:**
   - Текст разбивается на чанки размером 500-700 токенов
   - Между чанками делается перекрытие 50-70 токенов
   - Для каждого чанка генерируется embedding через Ollama (модель `nomic-embed-text`)
   - Векторы нормализуются к диапазону [0,1]
   - Сохраняются в JSON файл с хешем файла

2. **RAG запрос (Ollama включен):**
   - **Retrieval (поиск кандидатов):**
     - Определяется выбранный пользователем документ (если выбран)
     - Поиск выполняется только в выбранном документе (или во всех, если не выбран)
     - Вопрос пользователя конвертируется в embedding через Ollama (`nomic-embed-text`)
     - Выполняется поиск похожих векторов (cosine similarity) в JSON
     - Находится до 10 наиболее релевантных chunks-кандидатов
   - **Reranking (если включен):**
     - Для каждого кандидата формируется промпт для оценки релевантности:
       ```
       Оцени релевантность текста запросу по шкале от 0.0 до 1.0.
       Запрос: "[запрос пользователя]"
       Текст: "[текст чанка]"
       Ответь текст + релевантность текста в виде "Релевантность число". Никаких пояснений.
       ```
     - Промпт отправляется в Ollama API с моделью `phi3:medium`
     - LLM возвращает оценку релевантности (0.0-1.0) для каждого чанка
     - Полученные ответы сортируются по убыванию (1.0 = максимальная релевантность)
     - Выбираются максимум 3 самых релевантных чанка
     - В конце ответа добавляется "С Ollama и фильтрацией"
   - **Без reranking:**
     - Используются все найденные chunks без reranking
     - Выбираются топ-3 chunks по косинусному сходству
     - В конце ответа добавляется "С Ollama и без фильтрацией"
   - Chunks добавляются в контекст и отправляются в AI chat (выбранная модель)
   - AI chat генерирует ответ на основе контекста из chunks
   - AI chat сам формирует краткое summary для каждого chunk'а
   - Ответ формируется: основной ответ + информация о chunks (номера + summary от AI chat)
   - JSON файл обновляется с информацией о запросе и найденных чанках

3. **Обычный запрос (Ollama выключен):**
   - AI chat формирует ответ самостоятельно
   - В конце ответа добавляется фраза "Без Ollama"

4. **Обновление индекса:**
   - Если файл изменился (изменился хеш), индексация повторяется
   - Если файл тот же, используется существующий индекс из JSON

### Проверка работоспособности

1. **Проверьте Ollama сервер:**
   ```bash
   curl http://localhost:11434/api/tags
   ```

2. **Проверьте embedding модель:**
   ```bash
   ollama list | grep nomic-embed-text
   ```

3. **Запустите скрипт установки (если еще не запущен):**
   ```bash
   ./setup-ollama.sh
   ```

4. **Включите Ollama в приложении:**
   - Откройте настройки (⚙️)
   - Включите "Ollama Vector Search"
   - Должна появиться кнопка "Select File"

5. **Прикрепите файл:**
   - Нажмите "Select File"
   - Выберите файл (.md или .txt)
   - Дождитесь завершения индексации

6. **Проверьте логи приложения:**
   - Откройте Logcat в Android Studio
   - Фильтр: `ChatViewModel` или `TextIndexingService`
   - Должны быть логи о процессе индексации

7. **Проверьте работу RAG с LLM-as-a-Reranker:**
   - **С Ollama и Reranking включенными:**
     - Убедитесь, что установлена модель `phi3:medium`: `ollama pull phi3:medium`
     - Включите "Reranking (Filtering)" в настройках
     - Выберите документ для поиска (если несколько документов проиндексировано)
     - Задайте вопрос, связанный с содержимым прикрепленного файла
     - AI должен использовать reranked chunks (оцененные через LLM)
     - В ответе должны быть указаны номера chunks и их summary
     - В конце ответа должна быть фраза **"С Ollama и фильтрацией"**
     - Проверьте логи: должны быть сообщения о reranking через phi3:medium
   - **С Ollama включенным, но Reranking выключенным:**
     - Выключите "Reranking (Filtering)"
     - Выберите документ для поиска (если нужно)
     - Задайте вопрос
     - В конце ответа должна быть фраза **"С Ollama и без фильтрацией"**
     - Используются chunks без reranking (только по косинусному сходству)
   - **С Ollama выключенным:**
     - Задайте любой вопрос
     - В конце ответа должна быть фраза **"Без Ollama"**

8. **Проверьте JSON Export:**
   - Нажмите на иконку "Json Export" (📄)
   - Должен отобразиться JSON с индексированными данными
   - JSON должен содержать текст документов и векторы

## VPS Ollama - Удаленный сервер с локальной LLM

Проект поддерживает работу с локальной LLM моделью (Ollama: llama3.2:3b) на удаленном VPS сервере.

### Настройка VPS сервера

1. **Установите Ollama на VPS сервере:**
   ```bash
   curl -fsSL https://ollama.com/install.sh | sh
   ```

2. **Запустите Ollama сервер:**
   ```bash
   ollama serve
   # Или в фоновом режиме:
   sudo systemctl enable ollama
   sudo systemctl start ollama
   ```

3. **Установите модель llama3.2:3b:**
   ```bash
   ollama pull llama3.2:3b
   ```

4. **Настройте firewall (если необходимо):**
   ```bash
   sudo ufw allow 11434/tcp
   ```

5. **Проверьте доступность сервера:**
   ```bash
   curl http://your-vps-ip:11434/api/tags
   ```

### Настройка в приложении

1. **Настройте VPS URL:**
   - Откройте приложение (на эмуляторе или реальном устройстве)
   - Нажмите на иконку настроек (⚙️)
   - Прокрутите до секции "VPS Ollama Configuration"
   - Введите URL вашего VPS сервера (например: `http://109.73.194.244:11434`)
   - **ВАЖНО:** 
     - Используйте публичный IP адрес, а не приватный (192.168.x.x, 10.x.x.x)!
     - **НЕ используйте `10.0.2.2`** - это только для локального Ollama на Mac!
     - Для VPS используйте публичный IP адрес напрямую!
   - Закройте диалог настроек

2. **Настройте параметры модели (опционально):**
   - В секции "VPS Ollama Configuration" настройте параметры модели:
     - **Temperature** (0.0-2.0): Управляет случайностью ответов
       - Для кода: 0.3-0.5 (более детерминированные ответы)
       - Для творческих задач: 0.7-1.0 (более креативные ответы)
     - **Context Window** (1024-16384): Размер контекстного окна в токенах
       - Рекомендуется: 4096-8192 для Android/Kotlin задач
     - **Max Tokens** (512-8192): Максимальное количество токенов для генерации
       - Рекомендуется: 2048-4096 для полных примеров кода
     - **Android/Kotlin/Compose Prompt**: Включите для специализированного промпта
       - Добавляет системный промпт с best practices для Android/Kotlin/Compose
       - Улучшает качество ответов для задач разработки

3. **Выберите модель:**
   - В выпадающем списке моделей (вверху экрана) выберите "VPS - Ollama: llama3.2:3b"
   - Если URL настроен правильно, модель будет отмечена зеленым индикатором

4. **Использование:**
   - Введите сообщение и отправьте его
   - Сообщение будет отправлено на VPS сервер через Ollama API с настроенными параметрами
   - Ответ от модели `llama3.2:3b` будет отображен в чате
   - Если включен Android Prompt, ответы будут оптимизированы для Android/Kotlin/Compose задач

### Требуемые данные VPS

Для корректного взаимодействия необходимо предоставить:

1. **Публичный IP адрес или доменное имя VPS сервера**
   - **ВАЖНО:** Нужен именно **публичный IP**, а не приватный!
   - **Публичный IP:** Пример: `109.73.194.244`, `185.123.45.67` ✅
   - **Приватный IP:** `192.168.x.x`, `10.x.x.x` - НЕ работает ❌
   - **Доменное имя:** `vps.example.com`
   - **Где найти:** В панели управления VPS на вкладке "Сеть" → "IP-адреса"

2. **Минимальные требования к VPS серверу:**
   - **RAM:** 3-4 GB (рекомендуется 4 GB для модели llama3.2:3b)
   - **CPU:** 2 ядра
   - **Диск:** 20 GB SSD
   - **⚠️ Если получаете ошибку "model requires more system memory":**
     - Увеличьте RAM на VPS до минимум 3-4 GB
     - Или используйте модель меньшего размера
     - См. `VPS_OLLAMA_MEMORY_ERROR_FIX.md`

2. **Порт Ollama сервера**
   - По умолчанию: `11434`
   - Должен быть открыт в firewall

3. **Формат URL**
   - **Для публичного IPv4:** `http://109.73.194.244:11434` (замените на ваш публичный IP)
   - **Для IPv6:** `http://[2a03:6f00:a::1:a92c]:11434` (IPv6 в квадратных скобках)
   - **Для домена:** `http://vps.example.com:11434`
   - **ВАЖНО:** Используйте публичный IP, а не приватный (192.168.x.x, 10.x.x.x)!

4. **Доступность сервера**
   - VPS сервер должен быть доступен из сети, где работает Android приложение
   - Ollama должен быть запущен на VPS сервере
   - Модель `llama3.2:3b` должна быть установлена на VPS

### Рекомендуемые параметры для Android/Kotlin/Compose задач

- **Temperature:** 0.3-0.5 (более детерминированные ответы для кода)
- **Context Window:** 4096-8192 (достаточно для больших фрагментов кода)
- **Max Tokens:** 2048-4096 (достаточно для полных примеров кода)
- **Android/Kotlin/Compose Prompt:** Включен (для специализированного промпта)

Подробнее см.:
- [26HW_VPS_LOCAL_LLM.md](26HW_VPS_LOCAL_LLM.md) - базовая настройка VPS Ollama
- [28HW_VPS_LOCAL_LLM_FOR_SPECIFIC_TASK.md](28HW_VPS_LOCAL_LLM_FOR_SPECIFIC_TASK.md) - оптимизация параметров модели для конкретных задач

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

### Проверка оффлайн режима Ollama

#### Шаг 1: Установка и запуск Ollama

```bash
# Установка Ollama и моделей
./setup-ollama.sh

# Проверка работы Ollama на Mac
curl http://localhost:11434/api/tags

# Должен вернуть список установленных моделей
```

**Ожидаемый результат:** ✅ Ollama запущен, модели установлены

#### Шаг 2: Проверка доступности из эмулятора

```bash
# Проверка из эмулятора (работает БЕЗ интернета)
adb shell curl http://10.0.2.2:11434/api/tags

# Должен вернуть список моделей (работает оффлайн!)
```

**Ожидаемый результат:** ✅ Ollama доступен из эмулятора через 10.0.2.2

#### Шаг 3: Тест оффлайн режима (опционально)

```bash
# Выключите интернет на эмуляторе (для теста)
adb shell svc wifi disable
adb shell svc data disable

# Проверьте, что интернет выключен
adb shell ping -c 1 8.8.8.8
# Должна быть ошибка

# Проверьте доступность Ollama (должна работать!)
adb shell curl http://10.0.2.2:11434/api/tags
# Должен вернуть список моделей (работает без интернета!)
```

**Ожидаемый результат:** ✅ Ollama доступен даже без интернета

#### Шаг 4: Тест в приложении

1. **Запустите приложение** на эмуляторе
2. **Включите Ollama** в настройках (⚙️)
3. **Проверьте логи:**
   ```bash
   adb logcat | grep -E "OFFLINE|Ollama"
   ```
   Должны быть логи:
   - `🌐 OFFLINE MODE activated`
   - `🌐 All chat requests will use LOCAL Ollama server (NO internet required)`
4. **Задайте вопрос в чате**
5. **Проверьте ответ:**
   - Должен содержать: "🌐 OFFLINE: С Ollama (локальная модель: llama3.2:3b)"
   - Ответ генерируется локальной моделью (без интернета)

**Ожидаемый результат:** ✅ Приложение работает оффлайн, ответы генерируются локальной моделью

#### Шаг 5: Проверка исправления ошибки ConnectException

1. **Если при включении Ollama появляется ошибка подключения:**
   - Ошибка должна быть только в логах, не показываться пользователю
   - Используется модель по умолчанию (`llama3.2:3b`)
   - В UI должно быть сообщение: "Models list not loaded. Using default model: llama3.2:3b"

2. **Проверьте логи:**
   ```bash
   adb logcat | grep -E "Ollama|ConnectException|Error loading"
   ```
   - Должны быть предупреждения (WARNING), но не ошибки для пользователя
   - Должно быть: "💡 Models list will be empty, but you can still use default model"

3. **Запустите Ollama и проверьте снова:**
   ```bash
   ollama serve
   ```
   - Включите Ollama в приложении
   - Список моделей должен загрузиться
   - Ошибок не должно быть

**Ожидаемый результат:** ✅ Ошибки подключения обрабатываются корректно, используется модель по умолчанию

#### Шаг 6: Настройка полностью оффлайн режима

**Важно:** Эмулятор может работать с Ollama **полностью локально**, даже когда интернет полностью отключен.

**Проверка оффлайн работы:**
1. Отключите интернет на Mac (Wi-Fi/Ethernet)
2. Запустите Ollama: `ollama serve`
3. Проверьте ping: `adb shell ping -c 1 10.0.2.2` (должен работать БЕЗ интернета)
4. Проверьте Ollama: `adb shell curl http://10.0.2.2:11434/api/tags` (должен работать БЕЗ интернета)
5. Запустите приложение - должно работать БЕЗ интернета

**Настройка:**
- См. [EMULATOR_OFFLINE_SETUP.md](./EMULATOR_OFFLINE_SETUP.md) для подробной инструкции

#### Шаг 7: Диагностика проблемы ConnectException

Если при включении Ollama появляется ошибка `ConnectException: Failed to connect to /10.0.2.2:11434`:

**⚠️ ВАЖНО: Сначала проверьте ping до хоста!**

1. **Проверьте ping до хоста (ПЕРВЫЙ ШАГ):**
   ```bash
   adb shell ping -c 1 10.0.2.2
   ```
   
   **Если получаете "Network is unreachable":**
   - Это проблема сетевой конфигурации эмулятора
   - См. [EMULATOR_NETWORK_10.0.2.2_FIX.md](./EMULATOR_NETWORK_10.0.2.2_FIX.md)
   - Запустите скрипт исправления: `./fix-emulator-network.sh`
   - Перезапустите эмулятор с правильными настройками сети

2. **Запустите детальный скрипт проверки:**
   ```bash
   ./test-ollama-connection-detailed.sh
   ```

2. **Проверьте Ollama на Mac:**
   ```bash
   curl http://localhost:11434/api/tags
   # Должен вернуть JSON с моделями
   ```

3. **Проверьте из эмулятора:**
   ```bash
   adb shell curl http://10.0.2.2:11434/api/tags
   # Должен вернуть JSON с моделями
   ```

4. **Проверьте ping:**
   ```bash
   adb shell ping -c 1 10.0.2.2
   # Должен работать
   ```

5. **Проверьте логи приложения:**
   ```bash
   adb logcat | grep -E "ApiClient|Ollama|10.0.2.2|DNS|localDns"
   ```
   Должны быть логи:
   - `localDns.lookup() called for hostname: 10.0.2.2`
   - `✅ Resolved 10.0.2.2 to: 10.0.2.2`
   - `Using local OkHttp client (with local DNS resolver)`

**Если проблема сохраняется:**

1. **Убедитесь, что Ollama запущен:**
   ```bash
   ollama serve
   # Оставьте терминал открытым
   ```

2. **Проверьте доступность:**
   ```bash
   # С Mac
   curl http://localhost:11434/api/tags
   
   # Из эмулятора
   adb shell curl http://10.0.2.2:11434/api/tags
   ```

3. **Запустите детальный скрипт диагностики:**
   ```bash
   ./test-ollama-connection-detailed.sh
   ```

4. **Проверьте файрвол на Mac:**
   - System Settings → Network → Firewall → Options
   - Убедитесь, что Ollama разрешен или отключите файрвол временно

5. **Перезапустите эмулятор:**
   ```bash
   adb emu kill
   # Запустите заново через Android Studio
   ```

6. **Проверьте логи приложения:**
   ```bash
   adb logcat | grep -E "ApiClient|Ollama|10.0.2.2|ConnectException|localDns|DNS"
   ```
   Должны быть логи:
   - `localDns.lookup() called for hostname: 10.0.2.2`
   - `✅ Resolved 10.0.2.2 to: 10.0.2.2`
   - `Using local OkHttp client (with local DNS resolver)`

### Пошаговая проверка функционала Project Helper

#### Шаг 1: Проверка компиляции проекта
```bash
# Проверка сборки MCP сервера
./gradlew :project-helper-mcp-server:fatJar

# Проверка сборки Android приложения
./gradlew :app:assembleDebug
```
**Ожидаемый результат:** ✅ BUILD SUCCESSFUL

#### Шаг 2: Проверка скриптов
```bash
# Проверка прав на выполнение
chmod +x project-helper-mcp-server/start-server.sh
ls -la project-helper-mcp-server/start-server.sh

# Должно быть: -rwxr-xr-x
```
**Ожидаемый результат:** ✅ Скрипт имеет права на выполнение

#### Шаг 3: Настройка Ollama (ОБЯЗАТЕЛЬНО для Project Helper)

```bash
# Установка и запуск Ollama (ОБЯЗАТЕЛЬНО для Project Helper)
./setup-ollama.sh

# Проверка работы Ollama
curl http://localhost:11434/api/tags

# Проверка моделей
ollama list | grep nomic-embed-text
ollama list | grep phi3:medium
```
**Ожидаемый результат:** ✅ Ollama запущен, модели установлены

#### Шаг 4: Запуск Project Helper MCP Server
```bash
cd project-helper-mcp-server
./start-server.sh 8081 /Users/Victor/work/hw-1-AI-chat-bot http://localhost:11434
```
**Ожидаемый результат:** 
- ✅ `Ollama is available at http://localhost:11434`
- ✅ `MCP Project Helper Server started successfully on port 8081`
- ✅ `MCP endpoint available at: http://0.0.0.0:8081/mcp`

#### Шаг 5: Проверка доступности MCP сервера
```bash
# Из терминала (с хоста)
curl http://localhost:8081/mcp -X POST -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","id":1,"method":"initialize"}'

# Из эмулятора (если эмулятор запущен)
adb shell curl http://10.0.2.2:8081/mcp
```
**Ожидаемый результат:** ✅ Сервер отвечает JSON-RPC ответом

#### Шаг 6: Сборка и установка Android приложения
```bash
# Сборка приложения
./gradlew :app:assembleDebug

# Установка на эмулятор/устройство
adb install app/build/outputs/apk/debug/app-debug.apk
```
**Ожидаемый результат:** ✅ Приложение установлено

#### Шаг 7: Настройка Project Helper в приложении

1. **Откройте приложение** на эмуляторе/устройстве
2. **Нажмите на иконку настроек** (⚙️) в верхней панели
3. **Включите "Project helper"** (switcher вверху списка)
   - При включении автоматически запускается индексация всех файлов проекта
   - Дождитесь завершения индексации (проверьте логи)
4. **Опционально включите:**
   - **Ollama Vector Search** - для векторного поиска по документам
   - **Reranking (Filtering)** - для использования LLM-as-a-Reranker (требует `phi3:medium`)

**Ожидаемый результат:** 
- ✅ Project Helper включен
- ✅ Индексация завершена (проверьте логи MCP сервера)
- ✅ Состояние сохраняется между сессиями

#### Шаг 8: Проверка работы команды /help
1. **В чате введите:**
   ```
   /help Как работает RAG в этом проекте?
   ```
2. **Проверьте ответ:**
   - Должен содержать информацию из файлов проекта
   - В конце ответа должно быть: `Источник: [имя файла], Релевантность: [число]`
3. **Проверьте логи:**
   - MCP сервер: должны быть сообщения о поиске и reranking
   - Android приложение (Logcat): должны быть сообщения об успешном запросе

**Ожидаемый результат:** ✅ Получен ответ с указанием источника и релевантности

### Дополнительная проверка (опционально)

#### Проверка работы с разными типами файлов
Попробуйте задать вопросы о разных частях проекта:
```
/help Какие зависимости используются в build.gradle.kts?
/help Как работает ChatViewModel?
/help Опиши структуру проекта
/help Какие модели AI поддерживаются?
```

**Ожидаемый результат:** ✅ Ответы содержат информацию из соответствующих файлов (.kt, .kts, .md)

### Настройка Ollama Vector Search (опционально, отдельно от Project Helper)

1. **Запустите скрипт установки Ollama:**
   ```bash
   ./setup-ollama.sh
   ```

2. **Включите Ollama в настройках:**
   - Настройки → Ollama Vector Search → Включить
   - При включении автоматически запускается скрипт и проверяется подключение

3. **Прикрепите файл для индексации:**
   - После включения нажмите "Select File"
   - Выберите файл (.md или .txt)
   - Дождитесь завершения индексации
   - Дождитесь завершения индексации (проверьте логи)

2. **Проверьте работу:**
   - Задайте вопрос, связанный с содержимым проиндексированного документа
   - AI должен использовать контекст из документа
   - Проверьте логи на наличие ошибок

### Шаг 5: Устранение проблем

**Проблема: "ConnectException: Failed to connect to /10.0.2.2:11434"**

**Симптомы:**
- Ошибка подключения к Ollama серверу
- Ошибка: `ENETUNREACH (Network is unreachable)`
- Сообщение: "Error loading Ollama models"

**Решение:**
1. **Убедитесь, что Ollama запущен на Mac:**
   ```bash
   ollama serve
   ```

2. **Проверьте доступность:**
   ```bash
   # С Mac
   curl http://localhost:11434/api/tags
   
   # Из эмулятора
   adb shell curl http://10.0.2.2:11434/api/tags
   ```

3. **Проверьте ping до хоста:**
   ```bash
   adb shell ping -c 1 10.0.2.2
   ```

4. **Если список моделей не загрузился:**
   - Это не критично - используется модель по умолчанию (`llama3.2:3b`)
   - Приложение продолжит работать с моделью по умолчанию
   - Ошибка не показывается пользователю (только в логах)

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

**Проблема: Reranking не работает**
- **Решение:**
  1. Убедитесь, что модель `phi3:medium` установлена: `ollama pull phi3:medium`
  2. Проверьте, что reranking включен в настройках
  3. Проверьте логи приложения на наличие ошибок при reranking

## AI Release Pipeline

Проект включает автоматизированный пайплайн релиза Android приложения с использованием DeepSeek API, RAG системы и Google Play Console API.

### Команда /publish

В приложении введите `/publish` в чате для автоматического создания и пуша git тега.

**Что делает:**
- ✅ Автоматически находит последний тег
- ✅ Инкрементирует версию (v1.4.13 → v1.4.14 → v1.5.0)
- ✅ Создает и пушит новый тег
- ✅ GitHub Actions автоматически запускает релиз

**Требования:**
- Project Helper MCP Server должен быть запущен
- Git должен быть настроен с правами на push

**Подробнее:** [PUBLISH_COMMAND_SETUP.md](PUBLISH_COMMAND_SETUP.md)

### Локальный запуск

1. **Настройте переменные окружения:**
   ```bash
   # Создайте .env файл в корне проекта
   DEEPSEEK_API_KEY=your_key
   SIGNING_STORE_FILE=keystore.jks
   SIGNING_STORE_PASSWORD=your_password
   SIGNING_KEY_ALIAS=release
   SIGNING_KEY_PASSWORD=your_password
   ```

2. **Валидация настройки:**
   ```bash
   ./scripts/validate-setup.sh
   ```

3. **Запуск пайплайна:**
   ```bash
   ./scripts/run-local-release.sh [previous_tag] [track]
   
   # Пример:
   ./scripts/run-local-release.sh v1.0.0 internal
   ```

### CI/CD через GitHub Actions

**📖 Полная инструкция:** [GITHUB_ACTIONS_SETUP.md](GITHUB_ACTIONS_SETUP.md)

1. **Настройте GitHub Secrets:**
   - `DEEPSEEK_API_KEY` - API ключ для DeepSeek
   - `KEYSTORE_BASE64` - keystore файл в base64
   - `SIGNING_STORE_PASSWORD` - пароль от keystore
   - `SIGNING_KEY_ALIAS` - алиас ключа
   - `SIGNING_KEY_PASSWORD` - пароль ключа
   - `PLAY_STORE_JSON_BASE64` - Service Account JSON для Play Console (опционально)

2. **Создайте git tag:**
   ```bash
   git tag v1.4.0
   git push origin v1.4.0
   ```

3. **Workflow автоматически запустится** и выполнит:
   - Анализ изменений через DeepSeek API + RAG
   - Генерацию release notes на русском и английском
   - Сборку подписанного APK и AAB
   - Создание GitHub Release с APK
   - Деплой в Google Play Store (если настроен)

### Компоненты пайплайна

- **analyzeChanges** - Анализ изменений через DeepSeek API и RAG систему
- **generateRelease** - Генерация release notes и артефактов
- **bumpVersion** - Автоматическое обновление версии в build.gradle.kts
- **bundleRelease** - Сборка AAB файла
- **deployToStore** - Деплой в Google Play Store

### Результаты

После выполнения пайплайна создаются:
- `build/release-analysis.json` - AI анализ изменений
- `build/release-artifacts/RELEASE_NOTES.md` - Release notes
- `build/release-artifacts/play-store-ru.txt` - Play Store metadata (RU)
- `build/release-artifacts/play-store-en.txt` - Play Store metadata (EN)
- `CHANGELOG.md` - Обновленный changelog
- `app/build/outputs/apk/release/app-release.apk` - Подписанный APK
- `app/build/outputs/bundle/release/app-release.aab` - Android App Bundle

**В GitHub Actions:** APK и AAB автоматически добавляются в GitHub Release!

### Настройка Play Store деплоя

Для автоматического деплоя в Google Play Store требуется Service Account JSON файл.

**Если файл отсутствует:**
- Пайплайн автоматически пропустит деплой в Play Store
- Все остальные артефакты будут созданы успешно
- Вы увидите предупреждение с инструкциями

**Для включения деплоя:**
1. Следуйте инструкциям в [PLAY_STORE_SETUP.md](PLAY_STORE_SETUP.md)
2. Сохраните Service Account JSON как `play-store-key.json` в корне проекта
3. Или используйте `DRY_RUN=true` для тестирования без деплоя

Подробнее см. [24HW_PROJECT_PUBLISH_ASSISTENT.md](24HW_PROJECT_PUBLISH_ASSISTENT.md)

## Тестирование

- **Unit-тесты**: JUnit5 + MockK
- **UI-тесты**: Compose Testing
- **Именование**: `givenX_whenY_thenZ()`

## License

MIT
