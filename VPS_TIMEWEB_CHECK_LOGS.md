# Как проверить логи Ollama на VPS Timeweb

## Способ 1: Через SSH подключение (рекомендуется)

### Шаг 1: Подключитесь к VPS через SSH

```bash
ssh root@109.73.194.244
```

**Если SSH не работает:**
- Используйте веб-консоль Timeweb (см. Способ 2)
- Или настройте SSH ключи (см. `VPS_SSH_TIMEOUT_FIX.md`)

### Шаг 2: Проверьте логи Ollama

#### Если Ollama запущен как systemd сервис:

```bash
# Просмотр последних логов (последние 100 строк)
sudo journalctl -u ollama -n 100 --no-pager

# Просмотр логов в реальном времени (live)
sudo journalctl -u ollama -f

# Просмотр логов за последний час
sudo journalctl -u ollama --since "1 hour ago"

# Просмотр логов с временными метками
sudo journalctl -u ollama -n 50 --no-pager --full
```

#### Если Ollama запущен вручную (в терминале):

Логи будут отображаться в том терминале, где запущен `ollama serve`.

**Чтобы увидеть логи:**
1. Найдите процесс Ollama:
   ```bash
   ps aux | grep ollama | grep -v grep
   ```

2. Если процесс запущен в фоне, перенаправьте вывод в файл:
   ```bash
   # Остановите текущий процесс (если нужно)
   pkill ollama
   
   # Запустите с логированием в файл
   OLLAMA_HOST=0.0.0.0:11434 ollama serve > /tmp/ollama.log 2>&1 &
   
   # Просматривайте логи в реальном времени
   tail -f /tmp/ollama.log
   ```

### Шаг 3: Проверьте логи системных ошибок

```bash
# Логи системных ошибок
sudo dmesg | tail -50

# Логи системных сервисов
sudo journalctl -xe | tail -50

# Проверка доступности порта
sudo netstat -tlnp | grep 11434
# Или:
sudo ss -tlnp | grep 11434
```

## Способ 2: Через веб-консоль Timeweb

### Шаг 1: Войдите в панель управления Timeweb

1. Откройте https://timeweb.com
2. Войдите в аккаунт
3. Перейдите в раздел "VPS" или "Серверы"
4. Выберите ваш VPS сервер

### Шаг 2: Откройте веб-консоль

1. В панели управления VPS найдите кнопку **"Консоль"** или **"Web Console"**
2. Нажмите на неё
3. Откроется веб-терминал

### Шаг 3: Выполните команды для просмотра логов

В веб-консоли выполните те же команды, что и в Способе 1:

```bash
# Проверьте статус Ollama
sudo systemctl status ollama

# Просмотр логов systemd
sudo journalctl -u ollama -n 100 --no-pager

# Просмотр логов в реальном времени
sudo journalctl -u ollama -f
```

## Способ 3: Проверка логов через файлы (если настроено)

Ollama может писать логи в файлы, если настроено логирование:

```bash
# Проверьте, есть ли файлы логов Ollama
ls -la /var/log/ollama* 2>/dev/null
ls -la ~/.ollama/logs/ 2>/dev/null
ls -la /tmp/ollama*.log 2>/dev/null

# Если найдены файлы, просмотрите их
cat /var/log/ollama.log
# Или:
tail -f /var/log/ollama.log
```

## Способ 4: Просмотр логов в реальном времени при запросе

### Настройте Ollama для детального логирования:

```bash
# Остановите Ollama (если запущен)
sudo systemctl stop ollama
# Или:
pkill ollama

# Запустите с детальным логированием
OLLAMA_DEBUG=1 OLLAMA_HOST=0.0.0.0:11434 ollama serve 2>&1 | tee /tmp/ollama-debug.log
```

**В другом терминале (или в фоне):**
```bash
# Просматривайте логи в реальном времени
tail -f /tmp/ollama-debug.log
```

## Диагностика ошибки 500

### Шаг 1: Проверьте последние ошибки в логах

```bash
# Последние ошибки Ollama
sudo journalctl -u ollama -p err -n 50 --no-pager

# Все логи с ошибками
sudo journalctl -u ollama --since "10 minutes ago" | grep -i error
```

### Шаг 2: Проверьте использование ресурсов

```bash
# Использование памяти
free -h

# Использование CPU
top -bn1 | head -20

# Процессы Ollama
ps aux | grep ollama
```

### Шаг 3: Проверьте доступность модели

```bash
# Список установленных моделей
ollama list

# Проверка модели
ollama show llama3.2:3b
```

### Шаг 4: Тестовый запрос с детальным логированием

```bash
# Включите детальное логирование
export OLLAMA_DEBUG=1

# Запустите Ollama
OLLAMA_HOST=0.0.0.0:11434 ollama serve > /tmp/ollama-test.log 2>&1 &

# В другом терминале отправьте тестовый запрос
curl -X POST http://localhost:11434/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama3.2:3b",
    "messages": [{"role": "user", "content": "test"}],
    "stream": false
  }'

# Просмотрите логи
tail -50 /tmp/ollama-test.log
```

## Полезные команды для диагностики

```bash
# Проверка статуса Ollama сервиса
sudo systemctl status ollama

# Перезапуск Ollama
sudo systemctl restart ollama

# Просмотр конфигурации Ollama
cat /etc/systemd/system/ollama.service.d/override.conf 2>/dev/null

# Проверка переменных окружения
sudo systemctl show ollama | grep Environment

# Проверка сетевых соединений
sudo netstat -an | grep 11434
sudo ss -an | grep 11434

# Проверка firewall правил
sudo iptables -L -n | grep 11434
sudo ufw status | grep 11434
```

## Автоматический мониторинг логов

### Создайте скрипт для мониторинга:

```bash
# Создайте файл
nano ~/monitor-ollama.sh
```

**Добавьте содержимое:**
```bash
#!/bin/bash
echo "=== Ollama Status ==="
sudo systemctl status ollama --no-pager -l

echo -e "\n=== Recent Logs (last 20 lines) ==="
sudo journalctl -u ollama -n 20 --no-pager

echo -e "\n=== Recent Errors ==="
sudo journalctl -u ollama -p err -n 10 --no-pager

echo -e "\n=== Resource Usage ==="
ps aux | grep ollama | grep -v grep
free -h | head -2
```

**Сделайте скрипт исполняемым:**
```bash
chmod +x ~/monitor-ollama.sh
```

**Запустите:**
```bash
~/monitor-ollama.sh
```

## Решение проблем с логами

### Проблема: Логи не отображаются

**Решение:**
1. Убедитесь, что Ollama запущен: `sudo systemctl status ollama`
2. Проверьте права доступа: `sudo journalctl -u ollama`
3. Перезапустите Ollama: `sudo systemctl restart ollama`

### Проблема: Логи слишком большие

**Решение:**
```bash
# Очистка старых логов (осторожно!)
sudo journalctl --vacuum-time=7d

# Ограничение размера логов
sudo journalctl --vacuum-size=500M
```

### Проблема: Нужны более детальные логи

**Решение:**
```bash
# Включите debug режим
export OLLAMA_DEBUG=1

# Перезапустите Ollama
sudo systemctl restart ollama

# Просмотрите детальные логи
sudo journalctl -u ollama -f
```

## Полезные ссылки

- [Документация Timeweb](https://timeweb.com/help/)
- [Документация Ollama](https://github.com/ollama/ollama)
- [Systemd Journal](https://www.freedesktop.org/software/systemd/man/journalctl.html)
