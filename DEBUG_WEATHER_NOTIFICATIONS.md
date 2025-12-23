# Отладка Weather Notifications

## Проблемы и решения

### 1. Функционал обновления данных не работает

**Проверка:**
1. Убедитесь, что MCP сервер запущен и доступен
2. Проверьте логи:
   ```bash
   adb logcat | grep -E "WeatherNotificationWorker|WeatherWorkManager"
   ```

**Что искать в логах:**
- `WeatherNotificationWorker started - doWork() called` - Worker запустился
- `Weather notifications are disabled, skipping` - уведомления выключены
- `App is in foreground, skipping worker execution` - приложение в foreground (нормально, задача выполнится позже)
- `No last user query found, skipping` - нет последнего запроса пользователя
- `Processing weather notification for query: ...` - начинается обработка
- `Received weather data: ...` - данные получены от MCP
- `Generated summary: ...` - summary сгенерирован
- `Summary saved successfully` - summary сохранен
- `Notification sent successfully` - уведомление отправлено

**Решение:**
- Убедитесь, что тогл включен
- Убедитесь, что есть последний запрос пользователя (отправьте сообщение в чат)
- Проверьте, что приложение свернуто или убито (Worker работает только в фоне)

### 2. Push-уведомления не приходят

**Проверка:**
1. Проверьте разрешения на уведомления (Android 13+):
   - Настройки → Приложения → AI Agent Chat → Уведомления → Разрешить
2. Проверьте логи:
   ```bash
   adb logcat | grep -E "NotificationManager|WeatherNotificationWorker"
   ```

**Что искать в логах:**
- `POST_NOTIFICATIONS permission not granted` - нет разрешения
- `Notification sent successfully` - уведомление отправлено
- `Error showing notification` - ошибка при отправке

**Решение:**
- Предоставьте разрешение на уведомления в настройках приложения
- Проверьте, что канал уведомлений создан (автоматически при первом запуске)

### 3. Задача не планируется

**Проверка:**
1. Проверьте логи при включении тогла:
   ```bash
   adb logcat | grep "WeatherWorkManager"
   ```

**Что искать в логах:**
- `scheduleWeatherNotifications called: enabled=true` - метод вызван
- `Periodic work scheduled: interval=10 minutes` - задача запланирована
- `Work status: ...` - статус задачи

**Решение:**
- Убедитесь, что WorkManager инициализирован (проверьте логи при запуске приложения)
- Проверьте, что нет ошибок при создании WorkerFactory

## Тестирование

### Шаг 1: Включите уведомления
1. Откройте приложение
2. Откройте диалог MCP Tools (иконка настроек)
3. Включите тогл "Weather Notifications"
4. Проверьте логи: должно быть `scheduleWeatherNotifications called: enabled=true`

### Шаг 2: Отправьте запрос
1. В чате отправьте сообщение с запросом о погоде, например: "What's the weather in London?"
2. Проверьте логи: должно быть сохранение `lastUserQuery`

### Шаг 3: Сверните приложение
1. Сверните приложение (Home button)
2. Подождите 10 минут (или измените интервал в коде на 1 минуту для теста)
3. Проверьте логи: должен запуститься Worker

### Шаг 4: Проверьте уведомление
1. Через 10 минут должно прийти уведомление с summary
2. Если не пришло, проверьте логи на наличие ошибок

## Быстрый тест (1 минута)

Для быстрого теста измените интервал в `WeatherWorkManager.kt`:
```kotlin
private const val REPEAT_INTERVAL = 1L // 1 minute для теста
```

Затем:
1. Включите тогл
2. Отправьте запрос о погоде
3. Сверните приложение
4. Подождите 1 минуту
5. Проверьте уведомление

## Команды для отладки

```bash
# Все логи Worker
adb logcat | grep -E "WeatherNotificationWorker|WeatherWorkManager|NotificationManager"

# Только ошибки
adb logcat | grep -E "WeatherNotificationWorker|WeatherWorkManager|NotificationManager" | grep -i error

# Статус WorkManager задач
adb shell dumpsys jobscheduler | grep weather
```

## Частые проблемы

1. **Worker не запускается:**
   - Проверьте, что WorkManager инициализирован
   - Проверьте, что задача запланирована (логи)
   - Убедитесь, что приложение свернуто

2. **Уведомления не приходят:**
   - Проверьте разрешения (Android 13+)
   - Проверьте, что канал уведомлений создан
   - Проверьте логи на ошибки

3. **Данные не обновляются:**
   - Проверьте, что MCP сервер работает
   - Проверьте логи на ошибки при запросе к MCP
   - Проверьте, что есть последний запрос пользователя


