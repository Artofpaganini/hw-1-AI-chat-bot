# Исправление ошибки "Given final block not properly padded"

## Проблема

Ошибка: `Failed to read key from store: Given final block not properly padded`

**Причина:** Keystore файл поврежден при декодировании из base64. Это происходит когда:
1. Base64 содержит переносы строк
2. Base64 содержит пробелы
3. Keystore был неправильно закодирован
4. Неправильный пароль для keystore

## ✅ Исправление

### 1. Код исправлен автоматически

Workflow теперь:
- ✅ Автоматически удаляет переносы строк из base64 перед декодированием
- ✅ Проверяет размер keystore после декодирования
- ✅ Валидирует keystore с паролем (если доступен)
- ✅ Выводит понятные сообщения об ошибках

### 2. Перекодируйте keystore правильно

**Проблема может быть в том, как keystore закодирован в base64:**

#### Правильный способ (Mac/Linux):
```bash
# Удалите старый base64 файл если есть
rm -f keystore_base64.txt

# Закодируйте keystore БЕЗ переносов строк:
base64 -i keystore.jks | tr -d '\n' > keystore_base64.txt

# Проверьте, что файл одной строкой:
wc -l keystore_base64.txt
# Должно быть: 1

# Скопируйте содержимое:
cat keystore_base64.txt
```

#### Правильный способ (Windows PowerShell):
```powershell
# Закодируйте keystore:
$bytes = [IO.File]::ReadAllBytes("keystore.jks")
$base64 = [Convert]::ToBase64String($bytes)
# $base64 уже без переносов строк, можно копировать
$base64
```

### 3. Обновите GitHub Secret

1. Перейдите в **Settings** > **Secrets and variables** > **Actions**
2. Найдите секрет `KEYSTORE_BASE64`
3. **Удалите его** (Delete secret)
4. **Создайте заново:**
   - Скопируйте base64 **ОДНОЙ СТРОКОЙ** (без переносов)
   - Вставьте в поле **Secret**
   - Нажмите **Update secret**

## 🔍 Проверка

### Локальная проверка keystore

```bash
# 1. Проверьте, что keystore валиден:
keytool -list -v -keystore keystore.jks -storepass YOUR_PASSWORD

# 2. Закодируйте правильно:
base64 -i keystore.jks | tr -d '\n' > test_base64.txt

# 3. Декодируйте обратно для проверки:
cat test_base64.txt | base64 -d > test_keystore.jks

# 4. Проверьте, что декодированный keystore валиден:
keytool -list -v -keystore test_keystore.jks -storepass YOUR_PASSWORD

# 5. Сравните размеры:
ls -lh keystore.jks test_keystore.jks
# Размеры должны быть одинаковыми
```

### Проверка в GitHub Actions

После исправления секрета:
1. Запустите workflow снова
2. В логах должно быть:
   - `✅ Keystore decoded successfully (size: XXXX bytes)`
   - `✅ Keystore is valid and password is correct`
3. Не должно быть ошибок `Given final block not properly padded`

## 📝 Частые ошибки

### ❌ Неправильно:
```bash
# С переносами строк:
base64 keystore.jks
# Вывод:
# MIIDXTCCAkWgAwIBAg...
# QIBAgICAwIDAQAB...
```

### ✅ Правильно:
```bash
# Без переносов строк:
base64 -i keystore.jks | tr -d '\n'
# Вывод (одна длинная строка):
# MIIDXTCCAkWgAwIBAg...QIBAgICAwIDAQAB...
```

## 🔄 Если проблема сохраняется

1. **Проверьте пароль:**
   - Убедитесь, что `SIGNING_STORE_PASSWORD` правильный
   - Убедитесь, что `SIGNING_KEY_PASSWORD` правильный
   - Пароли должны совпадать с теми, что были при создании keystore

2. **Пересоздайте keystore (если возможно):**
   ```bash
   # Создайте новый keystore:
   keytool -genkey -v \
     -keystore keystore.jks \
     -alias release \
     -keyalg RSA \
     -keysize 2048 \
     -validity 10000 \
     -storepass NEW_PASSWORD \
     -keypass NEW_PASSWORD
   
   # Закодируйте правильно:
   base64 -i keystore.jks | tr -d '\n' > keystore_base64.txt
   
   # Обновите все секреты:
   # - KEYSTORE_BASE64 (новый base64)
   # - SIGNING_STORE_PASSWORD (новый пароль)
   # - SIGNING_KEY_PASSWORD (новый пароль)
   ```

3. **Проверьте алиас:**
   - Убедитесь, что `SIGNING_KEY_ALIAS` правильный (обычно `release`)
   - Проверьте: `keytool -list -keystore keystore.jks -storepass YOUR_PASSWORD`

## ✅ Статус

- ✅ Workflow автоматически очищает base64 от переносов строк
- ✅ Добавлена валидация keystore после декодирования
- ✅ Улучшены сообщения об ошибках
- ⚠️ Нужно перекодировать keystore правильно и обновить секрет
