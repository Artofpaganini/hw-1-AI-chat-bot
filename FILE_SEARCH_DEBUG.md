# Отладка поиска файла README.md

## Проблема: "README.md not found in any location"

Если вы видите эту ошибку, приложение проверило все возможные расположения файла, но не нашло его.

## Что делает приложение

При включении Ollama приложение ищет файл `README.md` в следующем порядке:

### 1. Внутреннее хранилище приложения (приоритет 1)
- Путь: `/data/data/com.example.aiagentchat/files/README.md`
- Преимущества: Не требует разрешений, всегда доступно
- Как скопировать:
  ```bash
  adb push README.md /sdcard/README.md
  adb shell "run-as com.example.aiagentchat cp /sdcard/README.md /data/data/com.example.aiagentchat/files/README.md"
  ```

### 2. Внешнее хранилище приложения (приоритет 2)
- Путь: `getExternalFilesDir(null)/README.md`
- Преимущества: Не требует разрешений, доступно через API
- Обычно: `/storage/emulated/0/Android/data/com.example.aiagentchat/files/README.md`

### 3. Стандартные пути внешнего хранилища (приоритет 3)
- Требуют разрешений на чтение файлов
- Проверяемые пути:
  - `/sdcard/README.md`
  - `/storage/emulated/0/README.md`
  - `/storage/emulated/0/Download/README.md`
  - `/storage/emulated/0/Documents/README.md`

## Автоматическое создание тестового файла

Если файл не найден, приложение автоматически создаст тестовый файл во внутреннем хранилище для демонстрации функционала.

## Логи для отладки

Проверьте Logcat с фильтром `ChatViewModel`:

```
I/ChatViewModel: 🔍 Searching for README.md file...
D/ChatViewModel: Checking: /data/data/com.example.aiagentchat/files/README.md (exists: false)
D/ChatViewModel: Checking: /storage/emulated/0/Android/data/com.example.aiagentchat/files/README.md (exists: false)
D/ChatViewModel: Checking: /sdcard/README.md (exists: false, canRead: false)
...
I/ChatViewModel: ✅ Found README.md in internal storage: /path/to/file
```

или

```
E/ChatViewModel: ❌ README.md not found in any location
E/ChatViewModel: Checked paths:
E/ChatViewModel:   - Internal storage: /data/data/.../files/README.md
E/ChatViewModel:   - External app storage: /storage/emulated/0/Android/data/.../files/README.md
...
I/ChatViewModel: 💡 Creating sample README.md in internal storage for testing...
I/ChatViewModel: ✅ Created sample README.md at: /data/data/.../files/README.md
```

## Решение проблем

### Проблема: Файл не найден

1. **Проверьте логи** - посмотрите, какие пути были проверены
2. **Скопируйте файл** в одно из проверяемых мест:
   ```bash
   # Вариант 1: Внутреннее хранилище (рекомендуется)
   adb push README.md /sdcard/README.md
   adb shell "run-as com.example.aiagentchat cp /sdcard/README.md /data/data/com.example.aiagentchat/files/README.md"
   
   # Вариант 2: Внешнее хранилище
   adb push README.md /sdcard/README.md
   # Затем предоставьте разрешения в приложении
   ```

### Проблема: Permission denied

- Для путей `/sdcard/` и `/storage/emulated/0/` требуются разрешения
- Приложение запросит разрешения при включении Ollama
- Или используйте внутреннее хранилище (не требует разрешений)

### Проблема: Файл существует, но не читается

- Проверьте права доступа: `adb shell ls -l /path/to/README.md`
- Убедитесь, что файл не пустой
- Проверьте, что это текстовый файл (не бинарный)

## Проверка файла вручную

```bash
# Проверить существование файла
adb shell "run-as com.example.aiagentchat ls -la /data/data/com.example.aiagentchat/files/README.md"

# Проверить содержимое файла
adb shell "run-as com.example.aiagentchat cat /data/data/com.example.aiagentchat/files/README.md"

# Проверить размер файла
adb shell "run-as com.example.aiagentchat stat /data/data/com.example.aiagentchat/files/README.md"
```


