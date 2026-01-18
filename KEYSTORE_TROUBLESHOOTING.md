# Keystore Troubleshooting Guide

## Проблема: "Given final block not properly padded"

Эта ошибка означает, что keystore файл поврежден при декодировании из base64.

## 🔍 Диагностика

### Шаг 1: Проверьте keystore локально

```bash
# Используйте скрипт валидации:
./scripts/validate-keystore.sh keystore.jks YOUR_PASSWORD release YOUR_KEY_PASSWORD
```

Если скрипт показывает ошибки, проблема в keystore или паролях.

### Шаг 2: Проверьте base64 в GitHub Secrets

1. Перейдите в GitHub Secrets
2. Откройте `KEYSTORE_BASE64`
3. Скопируйте значение
4. Проверьте локально:

```bash
# Вставьте base64 в файл
echo "ВАШ_BASE64_ЗДЕСЬ" > test_base64.txt

# Убедитесь, что это одна строка:
wc -l test_base64.txt
# Должно быть: 1

# Декодируйте и проверьте:
cat test_base64.txt | tr -d '\n\r\t ' | base64 -d > test_keystore.jks

# Проверьте размер:
ls -lh keystore.jks test_keystore.jks
# Размеры должны совпадать

# Проверьте валидность:
keytool -list -keystore test_keystore.jks -storepass YOUR_PASSWORD
```

## ✅ Решение

### Вариант 1: Перекодируйте keystore правильно

```bash
# 1. Убедитесь, что keystore валиден:
keytool -list -v -keystore keystore.jks -storepass YOUR_PASSWORD

# 2. Закодируйте БЕЗ переносов строк:
base64 -i keystore.jks | tr -d '\n' | tr -d '\r' > keystore_base64.txt

# 3. Проверьте, что файл одной строкой:
wc -l keystore_base64.txt
# Должно быть: 1

# 4. Проверьте декодирование:
cat keystore_base64.txt | base64 -d > test_keystore.jks
keytool -list -keystore test_keystore.jks -storepass YOUR_PASSWORD
# Должно работать без ошибок

# 5. Скопируйте содержимое:
cat keystore_base64.txt
# Скопируйте ВСЮ строку (может быть очень длинной)
```

### Вариант 2: Используйте Python для кодирования

```python
import base64

with open('keystore.jks', 'rb') as f:
    keystore_bytes = f.read()
    base64_str = base64.b64encode(keystore_bytes).decode('ascii')
    print(base64_str)
```

### Вариант 3: Используйте PowerShell (Windows)

```powershell
$bytes = [IO.File]::ReadAllBytes("keystore.jks")
$base64 = [Convert]::ToBase64String($bytes)
# $base64 уже без переносов строк
$base64 | Out-File -Encoding ASCII keystore_base64.txt
```

## 🔧 Обновление GitHub Secret

1. **Удалите старый секрет:**
   - Settings > Secrets and variables > Actions
   - Найдите `KEYSTORE_BASE64`
   - Нажмите Delete

2. **Создайте новый:**
   - New repository secret
   - Name: `KEYSTORE_BASE64`
   - Secret: вставьте base64 **ОДНОЙ СТРОКОЙ**
   - Add secret

3. **Проверьте другие секреты:**
   - `SIGNING_STORE_PASSWORD` - должен совпадать с паролем при создании keystore
   - `SIGNING_KEY_ALIAS` - обычно `release`
   - `SIGNING_KEY_PASSWORD` - пароль от ключа (может быть таким же как store password)

## ⚠️ Важные моменты

1. **Base64 должен быть ОДНОЙ СТРОКОЙ** без переносов
2. **Не добавляйте пробелы** в начале или конце
3. **Не редактируйте base64** вручную
4. **Пароли должны совпадать** с теми, что были при создании keystore

## 🧪 Тестирование

После обновления секрета:

1. Запустите workflow вручную
2. Проверьте логи шага "Decode Keystore"
3. Должно быть:
   - `✅ Keystore decoded successfully`
   - `✅ Keystore is valid and password is correct`
4. Если ошибки - проверьте логи детально

## 🔄 Если ничего не помогает

### Создайте новый keystore:

```bash
# 1. Создайте новый keystore:
keytool -genkey -v \
  -keystore keystore_new.jks \
  -alias release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass NEW_PASSWORD \
  -keypass NEW_PASSWORD

# 2. Закодируйте правильно:
base64 -i keystore_new.jks | tr -d '\n' > keystore_new_base64.txt

# 3. Обновите ВСЕ секреты:
# - KEYSTORE_BASE64 (новый base64)
# - SIGNING_STORE_PASSWORD (новый пароль)
# - SIGNING_KEY_PASSWORD (новый пароль)
```

**⚠️ ВАЖНО:** Если вы создаете новый keystore, старые APK/AAB, подписанные старым keystore, нельзя будет обновить в Play Store!

## 📞 Дополнительная помощь

Если проблема сохраняется:
1. Проверьте логи workflow детально
2. Убедитесь, что используете правильные пароли
3. Попробуйте создать новый keystore
4. Используйте скрипт `validate-keystore.sh` для диагностики
