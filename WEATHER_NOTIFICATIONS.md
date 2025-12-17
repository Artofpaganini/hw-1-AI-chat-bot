# Weather Notifications Feature

## Обзор

Функционал фоновых уведомлений о погоде с использованием AI для формирования кратких summary. Система автоматически запрашивает данные о погоде через MCP сервер и отправляет пользователю push-уведомления с кратким summary.

## Архитектура

### Компоненты

1. **PreferencesManager** (`core/common`)
   - Хранит настройки уведомлений в SharedPreferences
   - Сохраняет последний запрос пользователя

2. **WeatherDataStorage** (`feature/chat/data/storage`)
   - Сохраняет данные о погоде в JSON файл
   - Читает данные за последние N часов
   - Управляет массивом записей о погоде

3. **WeatherNotificationWorker** (`feature/chat/data/worker`)
   - Периодическая задача (каждые 10 минут)
   - Запрашивает погоду через MCP сервер
   - Сохраняет данные в JSON
   - Генерирует summary через AI
   - Отправляет уведомление

4. **WeatherWorkManager** (`feature/chat/data/worker`)
   - Управляет расписанием периодических задач
   - Включает/выключает уведомления

5. **WeatherSummaryUseCase** (`feature/chat/domain/usecase`)
   - Генерирует краткое summary на основе данных о погоде
   - Использует AI модель для анализа

6. **NotificationManager** (`feature/chat/data/notification`)
   - Создает канал уведомлений
   - Отправляет push-уведомления пользователю

## Поток работы

### Включение уведомлений

1. Пользователь включает тогл "Weather Notifications" в UI
2. `ChatViewModel` сохраняет настройку в `PreferencesManager`
3. `WeatherWorkManager` планирует периодическую задачу (каждые 10 минут)

### Периодическая задача (каждые 10 минут)

**Важно:** Задача выполняется только когда:
- Включен тогл уведомлений
- Приложение свернуто или полностью убито (не работает когда приложение активно)

```
WeatherNotificationWorker.doWork()
  ↓
1. Проверка: уведомления включены?
   - Нет → завершение
   - Да → продолжение
  ↓
2. Проверка: приложение в фоне?
   - В foreground → завершение (не работаем когда приложение активно)
   - В фоне/убито → продолжение
  ↓
3. Получение последнего запроса пользователя
   - Нет запроса → завершение
   - Есть запрос → продолжение
  ↓
4. Запрос погоды через MCP сервер
   mcpRepository.callTool("get_weather", {"location": lastUserQuery})
  ↓
5. Генерация summary через AI сразу после получения данных
   weatherSummaryUseCase.generateSummary(model, weatherData, location)
  ↓
6. Сохранение summary в JSON файл (перезаписывает предыдущие данные)
   weatherDataStorage.saveWeatherSummary(location, summary)
  ↓
7. Отправка push-уведомления
   notificationManager.showWeatherSummaryNotification(summary)
```

### Выключение уведомлений

1. Пользователь выключает тогл
2. `WeatherWorkManager` отменяет периодическую задачу
3. Настройка сохраняется в `PreferencesManager`

## Структура данных

### WeatherSummaryData

```kotlin
data class WeatherSummaryData(
    val timestamp: Long,      // Время получения данных
    val location: String,      // Локация запроса
    val summary: String        // Краткое summary, сгенерированное AI
)
```

### JSON файл

Файл хранится в `context.filesDir/weather_summary.json`:

**Важно:** Файл содержит только последние актуальные данные. При каждом обновлении данные перезаписываются.

```json
{
  "timestamp": 1234567890,
  "location": "Вашингтон",
  "summary": "В Вашингтоне сейчас солнечно, 72°F. Ветер слабый, 5 mph."
}
```

## UI

### Тогл уведомлений

Тогл находится в диалоге MCP Tools:
- **Расположение**: `McpToolsDialog` → `WeatherNotificationItem`
- **Описание**: "Get weather updates every 10 minutes based on your last query"
- **Состояние**: Сохраняется в `ChatUiState.weatherNotificationsEnabled`

## Настройки

### Интервал обновления

По умолчанию: **10 минут**

Изменение: `WeatherWorkManager.REPEAT_INTERVAL`

### Период данных для summary

По умолчанию: **5 часов**

Изменение: `WeatherNotificationWorker.getRecentWeatherData(hours = 5)`

## Разрешения

Требуемые разрешения в `AndroidManifest.xml`:
- `POST_NOTIFICATIONS` - для отправки уведомлений
- `WAKE_LOCK` - для фоновых задач
- `FOREGROUND_SERVICE` - для фоновых сервисов
- `INTERNET` - для запросов к MCP серверу
- `ACCESS_NETWORK_STATE` - для проверки сети

## Зависимости

- **WorkManager 2.9.1** - для периодических задач
- **Koin** - для dependency injection
- **Gson** - для работы с JSON

## Логирование

Все компоненты логируют свои действия с тегами:
- `WeatherNotificationWorker` - работа воркера
- `WeatherDataStorage` - сохранение/чтение данных
- `WeatherSummaryUseCase` - генерация summary
- `NotificationManager` - отправка уведомлений

## Обработка ошибок

- Если уведомления выключены → задача завершается успешно
- Если нет последнего запроса → задача завершается успешно
- Если ошибка MCP сервера → логируется, задача завершается
- Если ошибка генерации summary → логируется, уведомление не отправляется
- При критических ошибках → `Result.retry()` для повторной попытки

## Тестирование

### Ручное тестирование

1. Включите уведомления в UI
2. Задайте вопрос о погоде (например, "Какая погода в Вашингтоне?")
3. Подождите 10 минут (или измените интервал для тестирования)
4. Проверьте уведомление

### Проверка логов

```bash
adb logcat | grep -E "WeatherNotificationWorker|WeatherDataStorage|WeatherSummaryUseCase"
```

### Проверка JSON файла

```bash
adb shell run-as com.example.aiagentchat cat files/weather_data.json
```

## Известные ограничения

1. Минимальный интервал для периодических задач WorkManager - 15 минут (но мы используем 10 минут, что может быть округлено системой)
2. Данные хранятся локально в JSON файле (не синхронизируются между устройствами)
3. Summary генерируется на основе данных за последние 5 часов

## Будущие улучшения

- Добавить настройку интервала обновления
- Добавить фильтрацию по локациям
- Добавить историю уведомлений
- Добавить синхронизацию данных между устройствами

