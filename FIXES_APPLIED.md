# Исправления в AI Release Pipeline

## Последние исправления

### 7. Ошибка "serviceAccountJson specifies file which doesn't exist"
**Проблема:** Task `deployToStore` падал с ошибкой, если файл `play-store-key.json` отсутствовал.

**Решение:**
- Сделано поле `serviceAccountJson` опциональным (`@Optional`)
- Добавлена проверка существования файла
- Если файл отсутствует - показывается предупреждение и деплой пропускается
- Пайплайн продолжает работу и создает все остальные артефакты

**Файлы:**
- `buildSrc/src/main/kotlin/com/example/releaseautomation/tasks/DeployToStoreTask.kt`

**Результат:** Пайплайн теперь работает даже без Play Store Service Account, просто пропуская деплой.

## Решенные проблемы

### 1. Скрипт `run-local-release.sh` не находил директорию
**Проблема:** Скрипт не мог найти `.env` файл и корневую директорию проекта при запуске из другой директории.

**Решение:**
- Добавлено определение корневой директории проекта через `SCRIPT_DIR` и `PROJECT_ROOT`
- Все пути теперь используют абсолютные пути от `$PROJECT_ROOT`
- Добавлена поддержка флага `--help` для справки

**Файлы:**
- `scripts/run-local-release.sh`
- `scripts/validate-setup.sh`

### 2. Ошибка "this and base files have different roots"
**Проблема:** `RagIndexer` не мог правильно вычислить относительные пути для файлов из разных директорий проекта.

**Решение:**
- В `CodeChunker.createChunk()` добавлен параметр `projectRoot`
- Относительные пути вычисляются от `projectRoot`, а не от текущей директории
- Добавлен fallback на абсолютные пути при ошибке вычисления относительного пути

**Файлы:**
- `buildSrc/src/main/kotlin/com/example/releaseautomation/rag/CodeChunker.kt`
- `buildSrc/src/main/kotlin/com/example/releaseautomation/rag/RagIndexer.kt`

### 3. Ошибка "missing field `model`" в DeepSeek API
**Проблема:** JSON запрос не содержал поле `model`, что вызывало ошибку 400 от API.

**Причина:** В конфигурации JSON было установлено `encodeDefaults = false`, из-за чего поле `model` с дефолтным значением не сериализовалось.

**Решение:**
- Изменено `encodeDefaults = false` на `encodeDefaults = true` в конфигурации JSON
- Теперь все поля с дефолтными значениями включаются в JSON

**Файлы:**
- `buildSrc/src/main/kotlin/com/example/releaseautomation/api/DeepSeekClient.kt`

### 4. Ошибка "NoTransformationFoundException" и проблемы с ContentNegotiation
**Проблема:** Ktor ContentNegotiation не мог правильно сериализовать/десериализовать запросы и ответы.

**Решение:**
- Используется явная сериализация через `json.encodeToString()` для запросов
- Используется явная десериализация через `json.decodeFromString()` для ответов
- Добавлена валидация JSON перед отправкой
- Добавлено логирование размера запросов

**Файлы:**
- `buildSrc/src/main/kotlin/com/example/releaseautomation/api/DeepSeekClient.kt`

### 5. Ошибка "Failed to deserialize the JSON body" - слишком большие запросы
**Проблема:** Запросы к DeepSeek API были слишком большими (более 700KB), что вызывало ошибки десериализации на стороне API.

**Решение:**
- Добавлено ограничение размера git diff (максимум 20000 символов)
- Добавлено ограничение размера RAG контекста (максимум 5000 символов)
- Ограничено количество коммитов (максимум 20)
- Упрощен промпт для уменьшения размера
- Добавлена функция `sanitizeForJson()` для очистки проблемных символов

**Файлы:**
- `buildSrc/src/main/kotlin/com/example/releaseautomation/api/DeepSeekClient.kt`
- `buildSrc/src/main/kotlin/com/example/releaseautomation/tasks/AnalyzeChangesTask.kt`

### 6. Ошибка "property 'previousTag' doesn't have a configured value"
**Проблема:** Gradle task требовал обязательное значение для `previousTag`, даже если оно не было указано.

**Решение:**
- Добавлена аннотация `@Optional` для поля `previousTag` в `AnalyzeChangesTask`
- Добавлен импорт `org.gradle.api.tasks.Optional`

**Файлы:**
- `buildSrc/src/main/kotlin/com/example/releaseautomation/tasks/AnalyzeChangesTask.kt`

## Результаты тестирования

### ✅ Успешно протестировано:

1. **Компиляция:**
   ```bash
   ./gradlew :buildSrc:build
   # ✅ BUILD SUCCESSFUL
   ```

2. **Анализ изменений:**
   ```bash
   ./gradlew :app:analyzeChanges -PpreviousTag=""
   # ✅ BUILD SUCCESSFUL
   # ✅ Создан app/build/release-analysis.json
   ```

3. **Генерация релиза:**
   ```bash
   ./gradlew :app:generateRelease
   # ✅ BUILD SUCCESSFUL
   # ✅ Созданы артефакты в app/build/release-artifacts/
   ```

4. **Скрипты:**
   ```bash
   ./scripts/run-local-release.sh --help
   # ✅ Показывает справку
   
   ./scripts/validate-setup.sh
   # ✅ Валидация проходит успешно
   ```

## Текущий статус

**Все компоненты работают корректно!**

- ✅ DeepSeek API интеграция работает
- ✅ RAG индексация работает
- ✅ Git анализ работает
- ✅ Генерация release notes работает
- ✅ Все скрипты работают из любой директории

**Готово к использованию!** Требуется только настройка секретов для полного функционала (keystore, Play Store Service Account).
