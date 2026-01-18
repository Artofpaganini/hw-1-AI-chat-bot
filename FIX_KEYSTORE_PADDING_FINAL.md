# Исправление ошибки "Given final block not properly padded"

## Проблема

При работе GitHub Actions падает ошибка:
```
Failed to read key *** from store ".../keystore.jks": Get Key failed: Given final block not properly padded.
```

Эта ошибка означает, что:
1. Keystore файл поврежден (неправильно декодирован из base64)
2. Пароль неправильный
3. Keystore файл невалиден

## Решение

### 1. Улучшена обработка ошибок в GitHub Actions

**Файл:** `.github/workflows/ai-release.yml`

- ✅ Если keystore невалиден, он **удаляется** перед сборкой
- ✅ Переменные окружения для signing **не устанавливаются**, если keystore недоступен
- ✅ Сборка продолжается **без подписи**, если keystore невалиден

### 2. Улучшена проверка keystore в Gradle

**Файл:** `app/build.gradle.kts`

- ✅ Добавлена дополнительная проверка ключа (`keytool -list -v`)
- ✅ Проверка на наличие ошибки "Given final block not properly padded" в выводе
- ✅ Если keystore невалиден, он **удаляется** автоматически
- ✅ Сборка продолжается без подписи

### 3. Что делать, если ошибка все еще возникает

#### Вариант 1: Пересоздать base64 keystore

1. Используйте скрипт для валидации:
   ```bash
   ./scripts/validate-keystore.sh keystore.jks <store_password> <key_alias> [key_password]
   ```

2. Скрипт создаст файл `keystore.jks.base64` с правильным base64

3. Обновите GitHub Secret `KEYSTORE_BASE64`:
   - Скопируйте содержимое из `keystore.jks.base64` (одна строка, без переносов)
   - Вставьте в GitHub Secrets

#### Вариант 2: Проверить пароли

Убедитесь, что в GitHub Secrets правильно установлены:
- `SIGNING_STORE_PASSWORD` - пароль keystore
- `SIGNING_KEY_ALIAS` - алиас ключа (обычно "release")
- `SIGNING_KEY_PASSWORD` - пароль ключа (может быть таким же как store password)

#### Вариант 3: Собрать без подписи

Если keystore невалиден, сборка автоматически продолжается без подписи:
- APK будет создан, но **не подписан**
- AAB будет создан, но **не подписан**
- Можно установить APK вручную, но не загрузить в Play Store

## Проверка

После исправлений:

1. **В логах GitHub Actions должно быть:**
   ```
   ✅ Keystore can be opened with provided password
   ✅ Keystore is valid and password is correct
   ✅ Key alias 'release' found in keystore
   ```

   Или при ошибке:
   ```
   ⚠️  Warning: Cannot open keystore with provided password
   ⚠️  Keystore validation failed - removing invalid keystore file
   ⚠️  Build will continue without signing
   ```

2. **Сборка должна завершиться успешно**, даже если keystore невалиден

3. **APK и AAB будут созданы**, но без подписи (если keystore невалиден)

## Важные замечания

- ⚠️ **Без подписи APK нельзя загрузить в Play Store**
- ✅ **Для локальной установки** unsigned APK подойдет
- ✅ **Для тестирования** можно использовать unsigned APK
- 🔐 **Для production** обязательно нужен валидный keystore

## Следующие шаги

Если ошибка все еще возникает:

1. Проверьте логи шага "Decode Keystore" в GitHub Actions
2. Убедитесь, что base64 строка правильная (одна строка, без переносов)
3. Используйте `scripts/validate-keystore.sh` для проверки keystore локально
4. Пересоздайте base64 и обновите GitHub Secret
