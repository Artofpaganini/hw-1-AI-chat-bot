# Настройка Google Play Store для автоматического деплоя

## Что означает ошибка?

Ошибка `serviceAccountJson specifies file 'play-store-key.json' which doesn't exist` означает, что для автоматического деплоя в Google Play Store требуется Service Account JSON файл, который отсутствует.

**Это нормально!** Пайплайн будет работать и без этого файла - просто пропустит деплой в Play Store, но создаст все остальные артефакты (release notes, changelog и т.д.).

## Пошаговая инструкция получения Service Account JSON

### Шаг 1: Создание проекта в Google Cloud Console

1. Перейдите на https://console.cloud.google.com/
2. Войдите в аккаунт Google, связанный с вашим Play Console
3. Создайте новый проект или выберите существующий:
   - Нажмите на выпадающий список проектов вверху
   - Нажмите "New Project"
   - Введите название (например, "AI Agent Chat Release")
   - Нажмите "Create"

### Шаг 2: Включение Google Play Android Publisher API

1. В Google Cloud Console перейдите в "APIs & Services" > "Library"
2. Найдите "Google Play Android Publisher API"
3. Нажмите "Enable" (Включить)

### Шаг 3: Создание Service Account

1. Перейдите в "APIs & Services" > "Credentials"
2. Нажмите "Create Credentials" > "Service Account"
3. Заполните форму:
   - **Service account name**: `play-store-deployer` (или любое другое имя)
   - **Service account ID**: автоматически заполнится
   - **Description**: `Service account for automated Play Store deployments`
4. Нажмите "Create and Continue"
5. В разделе "Grant this service account access to project":
   - Роль: выберите "Editor" (или "Owner" для полного доступа)
   - Нажмите "Continue"
6. В разделе "Grant users access to this service account" можно пропустить
7. Нажмите "Done"

### Шаг 4: Создание ключа для Service Account

1. В списке Service Accounts найдите созданный аккаунт
2. Нажмите на него
3. Перейдите на вкладку "Keys"
4. Нажмите "Add Key" > "Create new key"
5. Выберите тип "JSON"
6. Нажмите "Create"
7. JSON файл автоматически скачается

### Шаг 5: Настройка прав в Google Play Console

1. Перейдите на https://play.google.com/console/
2. Выберите ваше приложение
3. Перейдите в "Setup" > "API access"
4. Найдите раздел "Service accounts"
5. Нажмите "Link service account"
6. Введите email вашего Service Account (формат: `play-store-deployer@your-project-id.iam.gserviceaccount.com`)
   - Email можно найти в Google Cloud Console в разделе Service Accounts
7. Нажмите "Grant access"
8. Выберите права доступа:
   - ✅ **Release apps to production, alpha, beta, or internal testing tracks**
   - ✅ **View app information and download bulk reports**
9. Нажмите "Invite user"

### Шаг 6: Сохранение JSON файла в проект

1. Переместите скачанный JSON файл в корень проекта
2. Переименуйте его в `play-store-key.json`
3. Убедитесь, что файл добавлен в `.gitignore` (уже добавлен)

```bash
# Пример:
mv ~/Downloads/your-project-id-xxxxx.json /Users/Victor/work/hw-1-AI-chat-bot/play-store-key.json
```

### Шаг 7: Проверка настройки

```bash
# Проверьте, что файл на месте
ls -la play-store-key.json

# Запустите валидацию
./scripts/validate-setup.sh

# Должно показать:
# ✅ Play Store key is valid JSON
```

## Альтернативный способ: Использование DRY_RUN режима

Если вы не хотите настраивать Play Store прямо сейчас, можно использовать DRY_RUN режим:

```bash
# Установите переменную окружения
export DRY_RUN=true

# Или добавьте в .env файл:
echo "DRY_RUN=true" >> .env

# Запустите пайплайн
./scripts/run-local-release.sh
```

В этом режиме пайплайн выполнит все шаги, кроме реального деплоя в Play Store.

## Проверка работы

После настройки Service Account JSON:

```bash
# Запустите полный пайплайн
./scripts/run-local-release.sh

# Или только деплой (если AAB уже собран)
./gradlew deployToStore -Ptrack=internal
```

## Безопасность

⚠️ **ВАЖНО:**
- Никогда не коммитьте `play-store-key.json` в Git
- Файл уже добавлен в `.gitignore`
- Храните файл в безопасном месте
- Не передавайте файл третьим лицам

## Troubleshooting

### Ошибка: "The caller does not have permission"
**Решение:** Убедитесь, что Service Account имеет права в Play Console (Шаг 5)

### Ошибка: "Application not found"
**Решение:** Убедитесь, что `packageName` в задаче совпадает с `applicationId` в `build.gradle.kts`

### Ошибка: "Invalid service account"
**Решение:** Проверьте, что JSON файл не поврежден и правильно сохранен

## Дополнительная информация

- [Google Play Console API Documentation](https://developers.google.com/android-publisher)
- [Service Accounts Best Practices](https://cloud.google.com/iam/docs/service-accounts)
