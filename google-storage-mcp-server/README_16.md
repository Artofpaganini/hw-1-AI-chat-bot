# Google Storage MCP Server

MCP сервер для сохранения данных в Google Drive через Google Drive API с поддержкой HTTP транспорта (JSON-RPC).

## Требования

- Java 17 или выше
- Gradle (используется из корня проекта)
- Google Cloud Console проект с включенным Google Drive API
- OAuth 2.0 credentials для доступа к Google Drive

## Сборка

### Создание Fat JAR (со всеми зависимостями)

```bash
cd google-storage-mcp-server
../gradlew :google-storage-mcp-server:fatJar
```

Fat JAR файл будет создан в `build/libs/google-storage-mcp-server-1.0.0-all.jar`

**Важно:** Используйте именно `fatJar` задачу, так как она создает JAR со всеми зависимостями, необходимыми для запуска.

**Важно:** Сервер компилируется для Java 17. Убедитесь, что у вас установлена Java 17 или выше для запуска.

## Запуск

### Использование скрипта (рекомендуется)

```bash
cd google-storage-mcp-server
./start-server.sh [port]
```

По умолчанию используется порт 8081, если не указан другой.

### Ручной запуск

```bash
cd google-storage-mcp-server
../gradlew :google-storage-mcp-server:fatJar
java -jar build/libs/google-storage-mcp-server-1.0.0-all.jar [port]
```

## Конфигурация

- **Порт по умолчанию:** 8081
- **Хост:** 0.0.0.0 (слушает на всех интерфейсах)
- **MCP endpoint:** `http://localhost:8081/mcp` (или `http://10.0.2.2:8081/mcp` для Android эмулятора)

**Важно:** URL должен заканчиваться на `/` при использовании в Android приложении, так как это базовый URL для Retrofit.

## Настройка Google Drive API

### Шаг 1: Создание проекта в Google Cloud Console

1. Откройте https://console.cloud.google.com/
2. Войдите в свой Google аккаунт
3. Нажмите на выпадающий список проектов в верхней панели
4. Нажмите "New Project"
5. Введите название проекта (например, "AI Chat Drive Integration")
6. Нажмите "Create"
7. Дождитесь создания проекта и выберите его

### Шаг 2: Включение Google Drive API

1. В левом меню найдите "APIs & Services" → "Library"
2. В поисковой строке введите "Google Drive API"
3. Нажмите на результат "Google Drive API"
4. Нажмите кнопку "Enable"
5. Дождитесь активации

### Шаг 3: Создание OAuth 2.0 credentials

1. В левом меню "APIs & Services" → "Credentials"
2. Нажмите "OAuth consent screen" в левом меню
3. Выберите "External" (для тестирования) или "Internal" (для Google Workspace)
4. Заполните обязательные поля:
   - **App name**: "AI Chat Drive Integration"
   - **User support email**: Ваш email
   - **Developer contact information**: Ваш email
5. Нажмите "Save and Continue"
6. На шаге "Scopes" нажмите "Add or Remove Scopes"
   - Найдите и добавьте: `https://www.googleapis.com/auth/drive.file`
   - Нажмите "Update" → "Save and Continue"
7. На шаге "Test users" (если выбрали External):
   - Добавьте свой email в список тестовых пользователей
   - Нажмите "Save and Continue"
8. Нажмите "Back to Dashboard"

### Шаг 4: Создание OAuth 2.0 Client ID

1. Вернитесь в "Credentials"
2. Нажмите "Create Credentials" → "OAuth client ID"
3. Выберите тип приложения: **"Android"**
4. Заполните поля:
   - **Name**: "AI Chat Android App"
   - **Package name**: `com.example.aiagentchat` (должен совпадать с `applicationId` в `app/build.gradle.kts`)
   - **SHA-1 certificate fingerprint**: 
     - Для debug: выполните команду:
       ```bash
       keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
       ```
     - Скопируйте SHA-1 fingerprint (например: `A1:B2:C3:D4:E5:F6:...`)
     - Вставьте в поле "SHA-1 certificate fingerprint"
5. Нажмите "Create"
6. **Сохраните Client ID** - он понадобится для настройки приложения

### Шаг 5: Получение Access Token

1. Откройте https://developers.google.com/oauthplayground/
2. В правом верхнем углу нажмите на иконку настроек (⚙️)
3. Установите флажок "Use your own OAuth credentials"
4. Введите ваш Client ID и Client Secret (из шага 4)
5. Нажмите "Close"
6. В левой панели найдите "Drive API v3"
7. Выберите `https://www.googleapis.com/auth/drive.file`
8. Нажмите "Authorize APIs"
9. Выберите свой Google аккаунт и подтвердите доступ
10. Нажмите "Exchange authorization code for tokens"
11. Скопируйте "Access token" (начинается с `ya29.`)

### Шаг 6: Добавление Access Token в local.properties

1. Откройте файл `local.properties` в корне проекта
2. Добавьте строку:
   ```properties
   GOOGLE_DRIVE_ACCESS_TOKEN=your_access_token_here
   ```
   (Замените `your_access_token_here` на ваш реальный access token)

3. Пересоберите приложение:
   ```bash
   ./gradlew clean assembleDebug
   ```

**Важно:**
- Access token имеет ограниченный срок действия (обычно 1 час)
- После истечения срока действия токена нужно получить новый и обновить в `local.properties`
- Никогда не коммитьте `local.properties` в git (он уже в `.gitignore`)

## Инструменты

### `save_to_drive`

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

### `update_file_in_drive`

Обновление содержимого существующего файла в Google Drive. Файл должен существовать.

**Параметры:**
- `accessToken` (обязательный, string) - Google Drive API access token (OAuth 2.0)
- `fileName` (опциональный, string) - Имя файла для обновления (по умолчанию: "ai-chat-results")
- `data` (обязательный, string) - Новые JSON данные для замены содержимого файла

**Возвращает:**
- Сообщение об успешном обновлении файла или ошибку, если файл не найден

**Пример использования:**
```json
{
  "name": "update_file_in_drive",
  "arguments": {
    "accessToken": "ya29.a0AfH6SMC...",
    "fileName": "ai-chat-results",
    "data": "{\"weather\": \"cloudy\", \"temperature\": 20}"
  }
}
```

### `delete_file_from_drive`

Удаление файла из Google Drive по имени.

**Параметры:**
- `accessToken` (обязательный, string) - Google Drive API access token (OAuth 2.0)
- `fileName` (опциональный, string) - Имя файла для удаления (по умолчанию: "ai-chat-results")

**Возвращает:**
- Сообщение об успешном удалении файла или информацию, что файл не найден

**Пример использования:**
```json
{
  "name": "delete_file_from_drive",
  "arguments": {
    "accessToken": "ya29.a0AfH6SMC...",
    "fileName": "ai-chat-results"
  }
}
```

## Логика работы

1. **Поиск файла:** Сервер ищет файл по имени в корне Google Drive пользователя
2. **Создание/Обновление/Удаление:** В зависимости от используемого инструмента:
   - `save_to_drive`: Если файл существует - обновляет, если нет - создает новый
   - `update_file_in_drive`: Обновляет существующий файл (ошибка, если файл не найден)
   - `delete_file_from_drive`: Удаляет файл (успех, если файл не найден)

## Интеграция с Android приложением

1. Запустите сервер на хосте:
   ```bash
   cd google-storage-mcp-server
   ./start-server.sh 8081
   ```

2. Добавьте в `local.properties`:
   ```properties
   GOOGLE_STORAGE_MCP_SERVER_URL=http://10.0.2.2:8081/
   GOOGLE_DRIVE_ACCESS_TOKEN=your_access_token_here
   ```

3. В приложении включите нужные инструменты в настройках MCP Tools

4. При получении данных о погоде от Weather MCP Server, AI чат автоматически:
   - Удаляет старый файл `ai-chat-results` (если существует)
   - Создает новый файл с обновленными данными

## Проверка работоспособности

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

- Убедитесь, что порт 8081 не занят другим процессом
- Проверьте, что Java 17+ установлена: `java -version`
- Проверьте логи в консоли

### Ошибки Google Drive API

- Убедитесь, что Google Drive API включен в Google Cloud Console
- Проверьте, что access token действителен (не истек)
- Убедитесь, что у пользователя есть права на запись в Google Drive
- Проверьте, что scope `https://www.googleapis.com/auth/drive.file` добавлен в OAuth consent screen

### Ошибка: "Error 400: redirect_uri_mismatch"

- Проверьте Package name в Google Cloud Console
- Убедитесь, что он совпадает с `applicationId` в `app/build.gradle.kts`
- Проверьте SHA-1 fingerprint

### Ошибка: "Error 403: access_denied"

- Настройте OAuth consent screen
- Добавьте свой email в список тестовых пользователей
- Убедитесь, что scope `https://www.googleapis.com/auth/drive.file` добавлен

### Ошибка: "Error 401: invalid_client"

- Проверьте Client ID в `local.properties`
- Убедитесь, что Client ID создан для Android приложения
- Пересоберите приложение после изменения `local.properties`

### Файл не обновляется

- Убедитесь, что используется правильная стратегия: сначала `delete_file_from_drive`, затем `save_to_drive`
- Проверьте логи сервера для деталей ошибки
- Убедитесь, что access token действителен
