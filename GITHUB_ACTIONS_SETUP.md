# Настройка GitHub Actions для AI Release Pipeline

## Обзор

GitHub Actions workflow автоматически:
- ✅ Анализирует изменения через DeepSeek API
- ✅ Генерирует release notes
- ✅ Собирает подписанный APK и AAB
- ✅ Создает GitHub Release с APK
- ✅ Опционально деплоит в Google Play Store

## Триггеры

### 1. Push тега (автоматический релиз)

```bash
# Создайте тег и запушьте
git tag v1.4.0
git push origin v1.4.0
```

Workflow автоматически запустится и создаст GitHub Release с APK.

### 2. Ручной запуск (workflow_dispatch)

1. Перейдите в GitHub: **Actions** > **AI Release Pipeline**
2. Нажмите **Run workflow**
3. Выберите:
   - **Track**: internal/alpha/beta/production
   - **Skip deploy**: пропустить деплой в Play Store
4. Нажмите **Run workflow**

## Настройка GitHub Secrets

**📖 Подробная инструкция:** [GITHUB_SECRETS_SETUP.md](GITHUB_SECRETS_SETUP.md)

**Кратко:** Перейдите в **Settings** > **Secrets and variables** > **Actions** и добавьте:

### Обязательные секреты

#### 1. DEEPSEEK_API_KEY
```
sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```
API ключ для DeepSeek (для анализа изменений)

#### 2. KEYSTORE_BASE64
```bash
# Закодируйте keystore в base64
base64 -i keystore.jks
```
Вставьте результат в секрет.

#### 3. SIGNING_STORE_PASSWORD
Пароль от keystore файла

#### 4. SIGNING_KEY_ALIAS
Алиас ключа (обычно `release`)

#### 5. SIGNING_KEY_PASSWORD
Пароль от ключа

### Опциональные секреты

#### 6. PLAY_STORE_JSON_BASE64
```bash
# Закодируйте Service Account JSON в base64
base64 -i play-store-key.json
```
Только если нужен автоматический деплой в Play Store.

## Создание Keystore

Если у вас еще нет keystore:

```bash
keytool -genkey -v \
  -keystore keystore.jks \
  -alias release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD
```

**Важно:** Сохраните пароли и алиас - они понадобятся для GitHub Secrets!

## Проверка настройки

### Локальная проверка

```bash
# Проверьте, что keystore существует
ls -la keystore.jks

# Проверьте валидность keystore
keytool -list -v -keystore keystore.jks -storepass YOUR_PASSWORD
```

### Проверка в GitHub

1. Перейдите в **Actions**
2. Запустите workflow вручную
3. Проверьте логи на наличие ошибок

## Результаты работы

После успешного выполнения workflow:

### 1. GitHub Release
- Перейдите в **Releases** в репозитории
- Найдете новый релиз с тегом (например, `v1.4.0`)
- В релизе будут:
  - 📦 **app-release.apk** - подписанный APK
  - 📦 **app-release.aab** - Android App Bundle
  - 📝 Release notes (RU/EN)
  - 📋 Play Store metadata

### 2. Artifacts
- Перейдите в **Actions** > выберите workflow run
- В разделе **Artifacts** найдете:
  - `app-release-apk`
  - `app-release-aab`
  - `release-artifacts`

### 3. Play Store (если настроен)
- AAB автоматически загрузится в выбранный трек
- Release notes будут добавлены

## Структура workflow

```
1. Checkout code
   ↓
2. Setup Java & Gradle
   ↓
3. Decode Keystore & Play Store Key
   ↓
4. Determine previous tag
   ↓
5. Run AI Release Pipeline
   - analyzeChanges
   - generateRelease
   - bumpVersion
   ↓
6. Build Release APK
   ↓
7. Upload Artifacts
   ↓
8. Create GitHub Release (если тег)
   ↓
9. Cleanup
```

## Troubleshooting

### Ошибка: "Keystore not found"
**Решение:** Убедитесь, что `KEYSTORE_BASE64` секрет добавлен и правильно закодирован.

### Ошибка: "Signing failed"
**Решение:** Проверьте, что все секреты для signing (`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`) установлены правильно.

### Ошибка: "DEEPSEEK_API_KEY not set"
**Решение:** Добавьте `DEEPSEEK_API_KEY` в GitHub Secrets.

### APK не подписан
**Причина:** Keystore не найден или секреты не установлены.
**Решение:** Проверьте все signing секреты. APK будет собран, но без подписи.

### Release не создается
**Причина:** Workflow запущен не через push тега.
**Решение:** Используйте `git tag v1.x.x && git push origin v1.x.x` или запустите workflow вручную с тегом.

### Play Store deployment skipped
**Причина:** `PLAY_STORE_JSON_BASE64` не установлен или `skip_deploy=true`.
**Решение:** Это нормально, если Play Store деплой не нужен. Все остальные артефакты будут созданы.

## Пример использования

### Создание релиза v1.4.0

```bash
# 1. Обновите версию в app/build.gradle.kts (если нужно)
# versionCode = 5
# versionName = "1.4.0"

# 2. Создайте коммит
git add .
git commit -m "Release v1.4.0"

# 3. Создайте и запушьте тег
git tag v1.4.0
git push origin v1.4.0

# 4. GitHub Actions автоматически:
#    - Запустит workflow
#    - Проанализирует изменения
#    - Соберет APK и AAB
#    - Создаст GitHub Release с APK
```

### Ручной запуск без тега

1. Перейдите в **Actions** > **AI Release Pipeline**
2. Нажмите **Run workflow**
3. Выберите параметры
4. Нажмите **Run workflow**

## Безопасность

⚠️ **ВАЖНО:**
- Никогда не коммитьте keystore или пароли в Git
- Используйте только GitHub Secrets для хранения секретов
- Регулярно ротируйте ключи
- Ограничьте доступ к секретам только необходимым людям

## Дополнительные ресурсы

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Android Signing Guide](https://developer.android.com/studio/publish/app-signing)
- [Play Store API Setup](PLAY_STORE_SETUP.md)
