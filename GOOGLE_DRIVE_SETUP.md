# Детальная инструкция по настройке Google Drive

## Что можно автоматизировать, а что нет

### ❌ Нельзя автоматизировать программно:
1. **Создание проекта в Google Cloud Console** - требует ручного входа в консоль
2. **Включение Google Drive API** - требует ручного включения в консоли
3. **Создание OAuth 2.0 credentials** - требует ручной настройки в консоли

### ✅ Можно автоматизировать:
1. **Получение access token** - через Google Sign-In в Android приложении
2. **Обновление access token** - через refresh token
3. **Авторизация пользователя** - через встроенный OAuth flow

## Пошаговая инструкция

### Шаг 1: Создание проекта в Google Cloud Console

1. **Откройте Google Cloud Console:**
   - Перейдите на https://console.cloud.google.com/
   - Войдите в свой Google аккаунт

2. **Создайте новый проект:**
   - Нажмите на выпадающий список проектов в верхней панели (рядом с логотипом Google Cloud)
   - Нажмите "New Project"
   - Введите название проекта (например, "AI Chat Drive Integration")
   - Нажмите "Create"
   - Дождитесь создания проекта (может занять несколько секунд)

3. **Выберите созданный проект:**
   - В выпадающем списке проектов выберите только что созданный проект

### Шаг 2: Включение Google Drive API

1. **Откройте раздел APIs & Services:**
   - В левом меню найдите "APIs & Services"
   - Нажмите "Library" (или "Библиотека")

2. **Найдите Google Drive API:**
   - В поисковой строке введите "Google Drive API"
   - Нажмите на результат "Google Drive API"

3. **Включите API:**
   - На странице API нажмите кнопку "Enable" (или "Включить")
   - Дождитесь активации (обычно несколько секунд)

### Шаг 3: Создание OAuth 2.0 credentials

1. **Откройте раздел Credentials:**
   - В левом меню "APIs & Services" → "Credentials"
   - Или перейдите по прямой ссылке: https://console.cloud.google.com/apis/credentials

2. **Настройте OAuth consent screen (если еще не настроен):**
   - Нажмите "OAuth consent screen" в левом меню
   - Выберите "External" (для тестирования) или "Internal" (для Google Workspace)
   - Заполните обязательные поля:
     - **App name**: "AI Chat Drive Integration" (или любое другое название)
     - **User support email**: Ваш email
     - **Developer contact information**: Ваш email
   - Нажмите "Save and Continue"
   - На шаге "Scopes" нажмите "Add or Remove Scopes"
     - Найдите и добавьте: `https://www.googleapis.com/auth/drive.file`
     - Нажмите "Update" → "Save and Continue"
   - На шаге "Test users" (если выбрали External):
     - Добавьте свой email в список тестовых пользователей
     - Нажмите "Save and Continue"
   - Нажмите "Back to Dashboard"

3. **Создайте OAuth 2.0 Client ID:**
   - Вернитесь в "Credentials"
   - Нажмите "Create Credentials" → "OAuth client ID"
   - Выберите тип приложения: **"Android"**
   - Заполните поля:
     - **Name**: "AI Chat Android App" (или любое другое название)
     - **Package name**: `com.example.aiagentchat` (должен совпадать с `applicationId` в `app/build.gradle.kts`)
     - **SHA-1 certificate fingerprint**: 
       - Для debug: выполните команду:
         ```bash
         keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
         ```
       - Скопируйте SHA-1 fingerprint (например: `A1:B2:C3:D4:E5:F6:...`)
       - Вставьте в поле "SHA-1 certificate fingerprint"
   - Нажмите "Create"
   - **Сохраните Client ID** - он понадобится для настройки приложения

4. **Дополнительно создайте Desktop app credentials (для тестирования):**
   - Снова нажмите "Create Credentials" → "OAuth client ID"
   - Выберите тип: **"Desktop app"**
   - Название: "AI Chat Desktop" (или любое другое)
   - Нажмите "Create"
   - **Сохраните Client ID и Client Secret** - они могут понадобиться для тестирования

### Шаг 4: Настройка Android приложения

1. **Добавьте Client ID в приложение:**
   - Откройте файл `app/build.gradle.kts`
   - Добавьте в `local.properties`:
     ```properties
     GOOGLE_DRIVE_CLIENT_ID=your_client_id_here.apps.googleusercontent.com
     ```
   - Или добавьте в `defaultConfig`:
     ```kotlin
     val googleDriveClientId = localProperties.getProperty("GOOGLE_DRIVE_CLIENT_ID") ?: ""
     buildConfigField("String", "GOOGLE_DRIVE_CLIENT_ID", "\"$googleDriveClientId\"")
     ```

2. **Настройте AndroidManifest.xml:**
   - Убедитесь, что добавлены необходимые разрешения (уже добавлены)

### Шаг 5: Получение Access Token

После настройки Google Cloud Console, вам нужно получить access token. Есть несколько способов:

#### Способ 1: Использование OAuth 2.0 Playground (рекомендуется для тестирования)

1. **Откройте OAuth 2.0 Playground:**
   - Перейдите на https://developers.google.com/oauthplayground/

2. **Настройте OAuth 2.0:**
   - В правом верхнем углу нажмите на иконку настроек (⚙️)
   - Установите флажок "Use your own OAuth credentials"
   - Введите ваш Client ID и Client Secret (из шага 3)
   - Нажмите "Close"

3. **Выберите scope:**
   - В левой панели найдите "Drive API v3"
   - Выберите `https://www.googleapis.com/auth/drive.file`
   - Нажмите "Authorize APIs"

4. **Авторизуйтесь:**
   - Выберите свой Google аккаунт
   - Подтвердите доступ к Google Drive

5. **Получите токен:**
   - Нажмите "Exchange authorization code for tokens"
   - Скопируйте "Access token" (начинается с `ya29.`)

6. **Сохраните токен в приложении:**
   - Откройте приложение
   - Перейдите в настройки MCP Tools (иконка ⚙️)
   - Включите инструмент `save_to_drive` для Google Storage MCP Server
   - Введите access token (можно добавить UI для этого позже)

#### Способ 2: Использование командной строки (для продвинутых пользователей)

```bash
# Установите Google Cloud SDK, если еще не установлен
# Затем выполните:
gcloud auth application-default login --scopes=https://www.googleapis.com/auth/drive.file

# Или используйте curl для получения токена через OAuth 2.0 flow
```

#### Способ 3: Программно через Android приложение (требует дополнительной настройки)

Для автоматической авторизации в приложении можно использовать Google Sign-In API, но это требует:
- Настройки SHA-1 fingerprint в Google Cloud Console
- Добавления Client ID в приложение
- Реализации OAuth flow в коде

Это можно добавить позже как улучшение.

### Шаг 6: Добавление Access Token в local.properties

После получения access token, добавьте его в файл `local.properties`:

1. **Откройте файл `local.properties`** в корне проекта
2. **Добавьте строку:**
   ```properties
   GOOGLE_DRIVE_ACCESS_TOKEN=your_access_token_here
   ```
   (Замените на ваш реальный access token)

3. **Пересоберите приложение:**
   ```bash
   ./gradlew clean assembleDebug
   ```

**Как это работает:**
- При запуске приложения токен из `local.properties` автоматически копируется в `PreferencesManager`
- Токен используется для всех вызовов `save_to_drive` инструмента
- Если токен в `local.properties` пустой, приложение попытается использовать токен из `PreferencesManager` (если он был установлен ранее)
- Токен из `local.properties` удобен для разработки и тестирования
- Для production рекомендуется использовать OAuth flow в приложении

**Важно:**
- Никогда не коммитьте `local.properties` в git (он уже в `.gitignore`)
- Access token имеет ограниченный срок действия (обычно 1 час)
- После истечения срока действия токена нужно получить новый и обновить в `local.properties`

## Проверка настройки

### Проверка OAuth credentials:

1. Убедитесь, что Client ID создан для Android приложения
2. Проверьте, что Package name совпадает с `applicationId` в `app/build.gradle.kts`
3. Проверьте, что SHA-1 fingerprint добавлен правильно

### Проверка API:

1. Убедитесь, что Google Drive API включен
2. Проверьте, что в OAuth consent screen добавлен scope `https://www.googleapis.com/auth/drive.file`

### Проверка в приложении:

1. Запустите приложение
2. Попробуйте авторизоваться в Google Drive
3. Проверьте логи на наличие ошибок авторизации

## Troubleshooting

### Ошибка: "Error 400: redirect_uri_mismatch"

**Причина:** Client ID не настроен правильно или Package name не совпадает.

**Решение:**
1. Проверьте Package name в Google Cloud Console
2. Убедитесь, что он совпадает с `applicationId` в `app/build.gradle.kts`
3. Проверьте SHA-1 fingerprint

### Ошибка: "Error 403: access_denied"

**Причина:** OAuth consent screen не настроен или пользователь не добавлен в тестовые пользователи.

**Решение:**
1. Настройте OAuth consent screen
2. Добавьте свой email в список тестовых пользователей
3. Убедитесь, что scope `https://www.googleapis.com/auth/drive.file` добавлен

### Ошибка: "Error 401: invalid_client"

**Причина:** Client ID неверный или не настроен.

**Решение:**
1. Проверьте Client ID в `local.properties`
2. Убедитесь, что Client ID создан для Android приложения
3. Пересоберите приложение после изменения `local.properties`

## Безопасность

⚠️ **Важно:**
- Никогда не коммитьте `local.properties` в git
- Client ID можно хранить в `local.properties`, но не Client Secret
- Access token хранится в SharedPreferences (зашифрован системой Android)
- Для production используйте App Signing в Google Play Console

## Дополнительные ресурсы

- [Google Drive API Documentation](https://developers.google.com/drive/api)
- [OAuth 2.0 for Mobile & Desktop Apps](https://developers.google.com/identity/protocols/oauth2/native-app)
- [Google Sign-In for Android](https://developers.google.com/identity/sign-in/android)

