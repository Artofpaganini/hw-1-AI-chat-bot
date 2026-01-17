# Настройка GitHub Secrets - Пошаговая инструкция

## 📍 Где находятся секреты

GitHub Secrets хранятся в настройках репозитория:
- **URL**: `https://github.com/Artofpaganini/hw-1-AI-chat-bot/settings/secrets/actions`
- **Путь**: Репозиторий → Settings → Secrets and variables → Actions

## 🔐 Список необходимых секретов

### Обязательные секреты

#### 1. DEEPSEEK_API_KEY
**Что это:** API ключ для DeepSeek (для анализа изменений)

**Как получить:**
1. Перейдите на https://platform.deepseek.com/
2. Войдите или зарегистрируйтесь
3. Перейдите в раздел API Keys
4. Создайте новый ключ или скопируйте существующий
5. Формат: `sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`

**Как сохранить:**
- Имя секрета: `DEEPSEEK_API_KEY`
- Значение: вставьте ключ (например, `sk-xxxx...`)

**⚠️ ВАЖНО:**
- Скопируйте ключ БЕЗ пробелов и переносов строк
- Убедитесь, что ключ начинается с `sk-`
- Не добавляйте кавычки или другие символы
- Если ключ содержит переносы строк, удалите их перед вставкой

---

#### 2. KEYSTORE_BASE64
**Что это:** Keystore файл для подписи APK, закодированный в base64

**Как получить:**

**Шаг 1: Создайте keystore (если еще нет)**
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

**Шаг 2: Закодируйте keystore в base64**
```bash
# На Mac/Linux:
base64 -i keystore.jks

# Или:
base64 keystore.jks

# На Windows (PowerShell):
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore.jks"))
```

**Важно:** Скопируйте ВЕСЬ вывод команды (может быть очень длинным, несколько строк)

**Как сохранить:**
- Имя секрета: `KEYSTORE_BASE64`
- Значение: вставьте весь base64 вывод (начинается с чего-то вроде `MIIDXTCCAkWgAwIBAg...`)

---

#### 3. SIGNING_STORE_PASSWORD
**Что это:** Пароль от keystore файла

**Как получить:** Это тот пароль, который вы указали при создании keystore (`-storepass`)

**Как сохранить:**
- Имя секрета: `SIGNING_STORE_PASSWORD`
- Значение: ваш пароль от keystore

---

#### 4. SIGNING_KEY_ALIAS
**Что это:** Алиас ключа в keystore

**Как получить:** Это тот алиас, который вы указали при создании keystore (`-alias`)

**Обычно:** `release`

**Как сохранить:**
- Имя секрета: `SIGNING_KEY_ALIAS`
- Значение: `release` (или ваш алиас)

---

#### 5. SIGNING_KEY_PASSWORD
**Что это:** Пароль от ключа в keystore

**Как получить:** Это тот пароль, который вы указали при создании keystore (`-keypass`)

**Как сохранить:**
- Имя секрета: `SIGNING_KEY_PASSWORD`
- Значение: ваш пароль от ключа

---

### Опциональные секреты

#### 6. PLAY_STORE_JSON_BASE64
**Что это:** Service Account JSON для Google Play Console, закодированный в base64

**Нужен только если:** Вы хотите автоматический деплой в Google Play Store

**Как получить:**

**Шаг 1: Создайте Service Account в Google Cloud Console**
1. Перейдите на https://console.cloud.google.com/
2. Создайте проект или выберите существующий
3. Перейдите в **APIs & Services** > **Credentials**
4. Нажмите **Create Credentials** > **Service Account**
5. Заполните форму и создайте аккаунт
6. Перейдите в **Keys** > **Add Key** > **Create new key**
7. Выберите **JSON** и скачайте файл

**Шаг 2: Настройте права в Play Console**
1. Перейдите на https://play.google.com/console/
2. Выберите ваше приложение
3. Перейдите в **Setup** > **API access**
4. Нажмите **Link service account**
5. Введите email Service Account (из JSON файла)
6. Выберите права: **Release apps to production, alpha, beta, or internal testing tracks**

**Шаг 3: Закодируйте JSON в base64**
```bash
# На Mac/Linux:
base64 -i play-store-key.json

# Или:
base64 play-store-key.json

# На Windows (PowerShell):
[Convert]::ToBase64String([IO.File]::ReadAllBytes("play-store-key.json"))
```

**Как сохранить:**
- Имя секрета: `PLAY_STORE_JSON_BASE64`
- Значение: вставьте весь base64 вывод

---

## 📝 Пошаговая инструкция добавления секретов

### Способ 1: Через веб-интерфейс GitHub

1. **Откройте страницу секретов:**
   - Перейдите: `https://github.com/Artofpaganini/hw-1-AI-chat-bot`
   - Нажмите **Settings** (вверху репозитория)
   - В левом меню выберите **Secrets and variables** → **Actions**

2. **Добавьте секрет:**
   - Нажмите **New repository secret**
   - В поле **Name** введите имя секрета (например, `DEEPSEEK_API_KEY`)
   - В поле **Secret** вставьте значение
   - Нажмите **Add secret**

3. **Повторите для всех секретов**

### Способ 2: Через GitHub CLI (gh)

```bash
# Установите GitHub CLI если еще не установлен
# brew install gh  # на Mac
# или скачайте с https://cli.github.com/

# Авторизуйтесь
gh auth login

# Добавьте секреты
gh secret set DEEPSEEK_API_KEY --body "sk-xxxx..."
gh secret set KEYSTORE_BASE64 --body "$(base64 -i keystore.jks)"
gh secret set SIGNING_STORE_PASSWORD --body "your_password"
gh secret set SIGNING_KEY_ALIAS --body "release"
gh secret set SIGNING_KEY_PASSWORD --body "your_password"
gh secret set PLAY_STORE_JSON_BASE64 --body "$(base64 -i play-store-key.json)"
```

---

## ✅ Проверка настройки

### 1. Проверьте список секретов

В GitHub перейдите в **Settings** > **Secrets and variables** > **Actions**

Должны быть видны все добавленные секреты (значения скрыты звездочками).

### 2. Проверьте через workflow

1. Перейдите в **Actions**
2. Выберите **AI Release Pipeline**
3. Нажмите **Run workflow**
4. Выберите параметры и запустите
5. Проверьте логи - не должно быть ошибок о недостающих секретах

---

## 🔍 Troubleshooting

### Ошибка: "Secret not found"
**Причина:** Секрет не добавлен или имя написано неправильно
**Решение:** Проверьте имя секрета (чувствительно к регистру!)

### Ошибка: "Invalid base64"
**Причина:** Keystore или JSON файл закодирован неправильно
**Решение:** 
- Убедитесь, что используете `base64 -i` (не `base64` без флагов на некоторых системах)
- Скопируйте ВЕСЬ вывод команды, включая переносы строк

### Ошибка: "Keystore password incorrect"
**Причина:** Неправильный пароль в `SIGNING_STORE_PASSWORD` или `SIGNING_KEY_PASSWORD`
**Решение:** Проверьте пароли, которые вы использовали при создании keystore

### Ошибка: "Service Account has no permissions"
**Причина:** Service Account не имеет прав в Play Console
**Решение:** Настройте права в Play Console (см. инструкцию выше)

---

## 📋 Чеклист настройки

- [ ] `DEEPSEEK_API_KEY` добавлен
- [ ] `KEYSTORE_BASE64` добавлен (весь base64 вывод)
- [ ] `SIGNING_STORE_PASSWORD` добавлен
- [ ] `SIGNING_KEY_ALIAS` добавлен
- [ ] `SIGNING_KEY_PASSWORD` добавлен
- [ ] `PLAY_STORE_JSON_BASE64` добавлен (опционально)
- [ ] Все секреты проверены в GitHub UI
- [ ] Тестовый запуск workflow выполнен успешно

---

## 🔒 Безопасность

⚠️ **ВАЖНО:**
- Никогда не коммитьте секреты в Git
- Не делитесь секретами с третьими лицами
- Регулярно ротируйте ключи
- Используйте разные ключи для разных окружений (dev/prod)
- Ограничьте доступ к секретам только необходимым людям

---

## 📚 Дополнительные ресурсы

- [GitHub Secrets Documentation](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [Android Signing Guide](https://developer.android.com/studio/publish/app-signing)
- [Play Store API Setup](PLAY_STORE_SETUP.md)
