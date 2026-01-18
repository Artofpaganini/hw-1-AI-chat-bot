# Исправление ошибки "Incorrect padding" при декодировании base64

## Проблема

Ошибка: `Error decoding base64: Incorrect padding`

**Причина:** Base64 строка не имеет правильного padding (должна быть кратна 4 символам).

## ✅ Исправление

### 1. Код исправлен автоматически

Python скрипт теперь:
- ✅ Автоматически добавляет padding если нужно
- ✅ Убирает все не-base64 символы
- ✅ Использует файл для передачи base64 (избегает проблем с кавычками)
- ✅ Выводит детальную информацию об ошибках

### 2. Как правильно закодировать keystore

**Важно:** Base64 должен быть правильной длины (кратен 4).

#### Правильный способ:

```bash
# 1. Закодируйте keystore:
base64 -i keystore.jks | tr -d '\n' | tr -d '\r' > keystore_base64.txt

# 2. Проверьте длину (должна быть кратна 4):
LENGTH=$(cat keystore_base64.txt | wc -c)
REMAINDER=$((LENGTH % 4))
echo "Length: $LENGTH, Remainder: $REMAINDER"
# Remainder должен быть 0

# 3. Проверьте декодирование:
cat keystore_base64.txt | base64 -d > test_keystore.jks
keytool -list -keystore test_keystore.jks -storepass YOUR_PASSWORD
# Должно работать без ошибок
```

#### Использование скрипта валидации:

```bash
./scripts/validate-keystore.sh keystore.jks YOUR_PASSWORD release YOUR_KEY_PASSWORD
```

Скрипт автоматически создаст правильный base64.

## 🔍 Диагностика

### Проверка base64 в GitHub Secrets

Если ошибка все еще возникает:

1. **Скопируйте base64 из GitHub Secrets**
2. **Проверьте локально:**

```bash
# Сохраните base64 в файл
echo "ВАШ_BASE64" > test_base64.txt

# Проверьте padding:
LENGTH=$(cat test_base64.txt | tr -d '\n' | wc -c)
REMAINDER=$((LENGTH % 4))
echo "Length: $LENGTH, Remainder: $REMAINDER"

# Если Remainder не 0, нужно добавить padding:
# Python автоматически это сделает, но лучше исправить в источнике
```

### Проверка декодирования

```bash
# Попробуйте декодировать:
cat test_base64.txt | tr -d '\n' | base64 -d > test_keystore.jks

# Если ошибка "Incorrect padding":
# Python скрипт в workflow автоматически добавит padding
```

## ✅ Решение

### Вариант 1: Перекодируйте keystore

```bash
# Используйте скрипт валидации:
./scripts/validate-keystore.sh keystore.jks YOUR_PASSWORD

# Или вручную:
base64 -i keystore.jks | tr -d '\n' > keystore_base64.txt
cat keystore_base64.txt
# Скопируйте содержимое в GitHub Secret
```

### Вариант 2: Python автоматически исправит

Workflow теперь автоматически:
- Добавляет padding если нужно
- Очищает base64 от недопустимых символов
- Выводит детальные ошибки

Но лучше исправить в источнике.

## 📝 Важные моменты

1. **Base64 должен быть одной строкой** без переносов
2. **Длина должна быть кратна 4** (Python автоматически добавит padding)
3. **Не должно быть не-base64 символов** (Python автоматически удалит)
4. **Используйте скрипт валидации** для проверки перед добавлением в GitHub

## 🔄 Если проблема сохраняется

1. Удалите секрет `KEYSTORE_BASE64` в GitHub
2. Используйте скрипт `validate-keystore.sh` для создания правильного base64
3. Создайте секрет заново с правильным base64
4. Запустите workflow снова

Python скрипт в workflow должен автоматически исправить padding, но лучше исправить в источнике.
