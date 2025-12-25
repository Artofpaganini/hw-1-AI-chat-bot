# Настройка Ollama для Android эмулятора

## Важно: Работа на эмуляторе Android

Приложение оптимизировано для работы на Android эмуляторе и использует специальный IP адрес `10.0.2.2` для доступа к localhost хоста (Mac).

## Настройка Ollama

### 1. Установка и запуск Ollama на Mac

```bash
# Запустите скрипт установки
./setup-ollama.sh
```

Скрипт:
- Проверяет установку Ollama
- Устанавливает через Homebrew, если нужно
- Запускает Ollama сервер на `http://localhost:11434`
- Скачивает embedding модель `nomic-embed-text`

### 2. Проверка работы Ollama на Mac

```bash
# Проверка доступности сервера
curl http://localhost:11434/api/tags

# Проверка embedding модели
ollama list | grep nomic-embed-text

# Тест генерации embedding
curl http://localhost:11434/api/embed -d '{
  "model": "nomic-embed-text",
  "input": "test"
}'
```

### 3. Проверка доступа из эмулятора

```bash
# Проверка доступности Ollama из эмулятора
adb shell curl http://10.0.2.2:11434/api/tags
```

**Важно:** `10.0.2.2` - это специальный IP адрес Android эмулятора для доступа к localhost хоста.

## Конфигурация приложения

### URL для эмулятора

Приложение автоматически использует:
- **Base URL:** `http://10.0.2.2:11434/`
- **Endpoint:** `/api/embed`
- **Model:** `nomic-embed-text`

### Network Security Config

Приложение настроено для работы с локальными серверами:
- `network_security_config.xml` разрешает cleartext traffic для `10.0.2.2`
- Используется отдельный OkHttp клиент без DNS resolver для локальных адресов

### Таймауты

Оптимизированы для работы с Ollama:
- **Connect timeout:** 60 секунд
- **Read timeout:** 120 секунд (для генерации embeddings)
- **Write timeout:** 60 секунд

## Проверка работоспособности

### 1. Проверка подключения

При включении Ollama приложение автоматически проверяет доступность сервера:

```
I/ChatViewModel: 🔍 Checking Ollama server connection at http://10.0.2.2:11434...
I/ChatViewModel: ✅ Ollama server is accessible at http://10.0.2.2:11434
```

или при ошибке:

```
E/ChatViewModel: ❌ Cannot connect to Ollama server at http://10.0.2.2:11434
E/ChatViewModel: Make sure:
E/ChatViewModel: 1. Ollama is running on your Mac: ollama serve
E/ChatViewModel: 2. Test from Mac: curl http://localhost:11434/api/tags
E/ChatViewModel: 3. Test from emulator: adb shell curl http://10.0.2.2:11434/api/tags
```

### 2. Проверка индексации

При индексации файла проверьте логи:

```
I/TextIndexingService: 📄 File: README.md
I/TextIndexingService: 📊 Split into 5 chunks
I/TextIndexingService: 🚀 Starting vector indexing...
I/TextIndexingService: ✅ Indexed chunk 1/5 (20%)
...
I/TextIndexingService: 🎉 Indexing completed successfully
```

### 3. Проверка векторного поиска

При использовании векторного поиска:

```
D/ChatViewModel: Generating query embedding for: вопрос...
D/ChatViewModel: ✅ Generated query embedding with 768 dimensions
D/ChatViewModel: Using vector search context with 3 chunks
```

## Решение проблем

### Проблема: Cannot connect to Ollama server

**Решение:**
1. Убедитесь, что Ollama запущен на Mac:
   ```bash
   curl http://localhost:11434/api/tags
   ```

2. Проверьте доступность из эмулятора:
   ```bash
   adb shell curl http://10.0.2.2:11434/api/tags
   ```

3. Если не работает, проверьте:
   - Ollama запущен: `ps aux | grep ollama`
   - Порт не занят: `lsof -i :11434`
   - Файрвол не блокирует: проверьте настройки macOS

### Проблема: Timeout при генерации embeddings

**Решение:**
1. Увеличьте таймауты (уже сделано в коде)
2. Проверьте производительность Mac
3. Используйте более легкую модель, если нужно

### Проблема: Интернет не работает на эмуляторе

**Решение:**
1. Проверьте DNS настройки эмулятора:
   ```bash
   adb shell getprop net.dns1
   adb shell getprop net.dns2
   ```

2. Установите DNS вручную:
   ```bash
   adb shell "setprop net.dns1 8.8.8.8"
   adb shell "setprop net.dns2 8.8.4.4"
   ```

3. Перезапустите эмулятор с правильными DNS:
   ```bash
   emulator -avd <avd_name> -dns-server 8.8.8.8,8.8.4.4
   ```

**Важно:** Работа с Ollama (10.0.2.2) и интернет работают независимо:
- Ollama использует локальный клиент без DNS
- Интернет API используют клиент с DNS resolver
- Они не должны мешать друг другу

## Тестирование

### Тест 1: Проверка Ollama на Mac
```bash
curl http://localhost:11434/api/tags
```

### Тест 2: Проверка Ollama из эмулятора
```bash
adb shell curl http://10.0.2.2:11434/api/tags
```

### Тест 3: Проверка интернета на эмуляторе
```bash
adb shell ping -c 3 8.8.8.8
adb shell ping -c 3 api.deepseek.com
```

### Тест 4: Генерация embedding
```bash
# С Mac
curl http://localhost:11434/api/embed -d '{
  "model": "nomic-embed-text",
  "input": "test"
}'

# Из эмулятора
adb shell curl http://10.0.2.2:11434/api/embed -d '{
  "model": "nomic-embed-text",
  "input": "test"
}'
```

## Логи для отладки

Проверьте Logcat с фильтрами:
```bash
# Все логи Ollama
adb logcat | grep -E "OllamaApi|TextIndexingService|ChatViewModel.*Ollama"

# Логи подключения
adb logcat | grep -E "ApiClient|10.0.2.2|Connection"

# Логи индексации
adb logcat | grep -E "Indexing|embedding|chunk"
```

## Важные замечания

1. **10.0.2.2 работает только на эмуляторе** - для реального устройства используйте IP адрес Mac в локальной сети
2. **Интернет и Ollama работают независимо** - проблемы с одним не должны влиять на другое
3. **Таймауты увеличены** - генерация embeddings может занимать время
4. **Автоматическая проверка подключения** - при включении Ollama приложение проверяет доступность сервера



