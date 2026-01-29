# Быстрое исправление: Play Store Service Account

## Что означает ошибка?

```
Type 'DeployToStoreTask' property 'serviceAccountJson' specifies file 
'play-store-key.json' which doesn't exist.
```

**Это означает:** Для автоматического деплоя в Google Play Store нужен Service Account JSON файл, но он отсутствует.

## ✅ Исправлено автоматически

Пайплайн теперь **не падает** с ошибкой. Вместо этого он:
- ✅ Пропускает деплой в Play Store
- ✅ Показывает информативное предупреждение
- ✅ Продолжает работу и создает все остальные артефакты

## Что делать дальше?

### Вариант 1: Пропустить деплой (для тестирования)

Просто игнорируйте предупреждение. Пайплайн создаст:
- ✅ Release analysis
- ✅ Release notes
- ✅ Changelog
- ✅ Play Store metadata файлы

**Деплой в Play Store будет пропущен**, но все остальное работает.

### Вариант 2: Настроить Play Store деплой

Следуйте пошаговой инструкции в **[PLAY_STORE_SETUP.md](PLAY_STORE_SETUP.md)**

**Кратко:**
1. Создайте Service Account в Google Cloud Console
2. Скачайте JSON ключ
3. Сохраните как `play-store-key.json` в корне проекта
4. Настройте права в Play Console

### Вариант 3: Использовать DRY_RUN режим

```bash
export DRY_RUN=true
./scripts/run-local-release.sh
```

Это явно пропустит деплой, но выполнит все остальные шаги.

## Проверка

После настройки Service Account JSON:

```bash
# Проверьте валидацию
./scripts/validate-setup.sh

# Должно показать:
# ✅ Play Store key is valid JSON
```

## Текущий статус

✅ **Пайплайн работает корректно!**

Даже без `play-store-key.json` пайплайн:
- ✅ Анализирует изменения через DeepSeek API
- ✅ Генерирует release notes
- ✅ Обновляет changelog
- ✅ Создает Play Store metadata
- ⚠️ Пропускает деплой в Play Store (с предупреждением)

**Это нормальное поведение!** Деплой в Play Store - опциональный шаг.
