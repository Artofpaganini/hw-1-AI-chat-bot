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

## MCP Серверы

Проект поддерживает работу с несколькими MCP (Model Context Protocol) серверами:

### Weather MCP Server

Сервер для получения информации о погоде через Weather.gov API.

**Запуск:**
```bash
cd weather-mcp-server
./start-server.sh [port]
```

По умолчанию запускается на порту 8080.

**Инструменты:**
- `get_weather` - Получение информации о погоде для указанного местоположения

### Google Storage MCP Server

Сервер для сохранения данных в Google Drive.

**Запуск:**
```bash
cd google-storage-mcp-server
./start-server.sh [port]
```

По умолчанию запускается на порту 8081.

**Инструменты:**
- `save_to_drive` - Сохранение JSON данных в Google Drive

**Настройка Google Drive:**
1. Создайте проект в Google Cloud Console
2. Включите Google Drive API
3. Создайте OAuth 2.0 credentials
4. Получите access token для доступа к Google Drive

### Запуск всех серверов

Для запуска всех MCP серверов одновременно:

```bash
./start-servers.sh
```

Этот скрипт запустит оба сервера в фоновом режиме.

## Работа с MCP серверами в приложении

1. Откройте приложение
2. Нажмите на иконку настроек (⚙️) в верхней панели
3. В диалоге "MCP Tools" вы увидите список доступных серверов
4. Включите нужные инструменты для каждого сервера
5. Состояние инструментов сохраняется между сессиями

## Логика работы

### Поток данных: User → Weather MCP Server → AI Chat → Google Storage MCP Server

1. Пользователь запрашивает погоду у AI чата
2. AI чат использует инструмент `get_weather` из Weather MCP Server
3. Weather MCP Server возвращает данные о погоде
4. AI чат сжимает полученную информацию в JSON формат
5. AI чат использует инструмент `save_to_drive` из Google Storage MCP Server
6. Google Storage MCP Server сохраняет данные в файл `ai-chat-results` в корне Google Drive пользователя
   - Если файл существует, он перезаписывается
   - Если файл не существует, создается новый

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

## Тестирование

- **Unit-тесты**: JUnit5 + MockK
- **UI-тесты**: Compose Testing
- **Именование**: `givenX_whenY_thenZ()`

## License

MIT
