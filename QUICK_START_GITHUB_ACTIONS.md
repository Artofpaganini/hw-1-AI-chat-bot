# Быстрый старт: GitHub Actions Setup

## ✅ Исправление ошибки workflow

Ошибка была исправлена! Проблема была в использовании условий `if:` с проверкой секретов на уровне шагов.

**Что исправлено:**
- Убраны условия `if: ${{ secrets.KEYSTORE_BASE64 != '' }}` 
- Все проверки секретов перенесены внутрь bash скриптов
- Workflow теперь корректно обрабатывает отсутствующие секреты

## 🚀 Шаги для запуска

### 1. Закоммитьте исправленный workflow

```bash
git add .github/workflows/ai-release.yml
git commit -m "Fix GitHub Actions workflow - remove secrets from if conditions"
git push origin main
```

### 2. Настройте GitHub Secrets

Перейдите: `https://github.com/Artofpaganini/hw-1-AI-chat-bot/settings/secrets/actions`

**Добавьте обязательные секреты:**

#### DEEPSEEK_API_KEY
```
sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

#### KEYSTORE_BASE64
```bash
# Локально выполните:
base64 -i keystore.jks
# Скопируйте ВЕСЬ вывод и вставьте в секрет
```

#### SIGNING_STORE_PASSWORD
Пароль от keystore

#### SIGNING_KEY_ALIAS
Обычно: `release`

#### SIGNING_KEY_PASSWORD
Пароль от ключа

**Опционально (для Play Store):**

#### PLAY_STORE_JSON_BASE64
```bash
# Локально выполните:
base64 -i play-store-key.json
# Скопируйте ВЕСЬ вывод и вставьте в секрет
```

**📖 Подробная инструкция:** [GITHUB_SECRETS_SETUP.md](GITHUB_SECRETS_SETUP.md)

### 3. Проверьте workflow

1. Перейдите в **Actions** в репозитории
2. Выберите **AI Release Pipeline**
3. Нажмите **Run workflow**
4. Выберите параметры и запустите
5. Проверьте, что workflow выполняется без ошибок

### 4. Создайте тестовый релиз

```bash
git tag v1.4.0
git push origin v1.4.0
```

GitHub Actions автоматически:
- Запустит workflow
- Соберет APK и AAB
- Создаст GitHub Release с APK

## 🔍 Проверка

После настройки секретов workflow должен:
- ✅ Парситься без ошибок
- ✅ Запускаться при push тега
- ✅ Собирать APK и AAB
- ✅ Создавать GitHub Release

## ⚠️ Важно

1. **Закоммитьте изменения workflow** перед тестированием
2. **Все секреты должны быть добавлены** в GitHub Secrets
3. **Keystore должен быть закодирован в base64** полностью (весь вывод команды)

## 📚 Дополнительная документация

- [GITHUB_SECRETS_SETUP.md](GITHUB_SECRETS_SETUP.md) - подробная инструкция по секретам
- [GITHUB_ACTIONS_SETUP.md](GITHUB_ACTIONS_SETUP.md) - общая настройка GitHub Actions
