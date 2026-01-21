# Решение проблем с интернет-соединением на VPS

## Проблема: "Failed to connect to ollama.com"

### Симптомы

При попытке установить Ollama:
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

Получаете ошибку:
```
curl: (7) Failed to connect to ollama.com port 443 after 10 ms: Couldn't connect to server
```

## Решение 1: Проверка интернет-соединения

### Шаг 1: Проверьте базовое подключение

```bash
# Проверка подключения к Google DNS
ping -c 3 8.8.8.8
```

**Если ping работает:**
- Интернет работает, проблема в DNS или firewall
- Переходите к Решению 2

**Если ping не работает:**
- VPS не имеет доступа в интернет
- См. Решение 4

### Шаг 2: Проверьте DNS

```bash
# Проверка DNS
nslookup ollama.com
```

**Если DNS не работает:**

**Для Ubuntu/Debian:**
```bash
# Установите DNS серверы Google
echo "nameserver 8.8.8.8" | sudo tee /etc/resolv.conf
echo "nameserver 8.8.4.4" | sudo tee -a /etc/resolv.conf

# Или для постоянной настройки
sudo nano /etc/systemd/resolved.conf
# Добавьте строки:
# DNS=8.8.8.8 8.8.4.4
sudo systemctl restart systemd-resolved
```

**Для CentOS/RHEL:**
```bash
# Установите DNS серверы Google
echo "nameserver 8.8.8.8" | sudo tee /etc/resolv.conf
echo "nameserver 8.8.4.4" | sudo tee -a /etc/resolv.conf
```

### Шаг 3: Проверьте доступность сайта

```bash
# Проверка HTTPS соединения
curl -I https://ollama.com

# Или проверка HTTP
curl -I http://ollama.com
```

## Решение 2: Ручная установка Ollama

Если curl не может скачать скрипт, установите Ollama вручную:

### Для Ubuntu/Debian

```bash
# Установите wget (если еще не установлен)
sudo apt update
sudo apt install -y wget curl

# Скачайте Ollama напрямую с GitHub
wget https://github.com/ollama/ollama/releases/latest/download/ollama-linux-amd64

# Если wget не работает, попробуйте curl с таймаутом
curl -L --connect-timeout 30 https://github.com/ollama/ollama/releases/latest/download/ollama-linux-amd64 -o ollama-linux-amd64

# Сделайте файл исполняемым
chmod +x ollama-linux-amd64

# Переместите в системную директорию
sudo mv ollama-linux-amd64 /usr/local/bin/ollama

# Проверьте установку
ollama --version
```

### Для CentOS/RHEL

```bash
# Установите wget (если еще не установлен)
sudo yum install -y wget curl

# Скачайте Ollama напрямую с GitHub
wget https://github.com/ollama/ollama/releases/latest/download/ollama-linux-amd64

# Если wget не работает, попробуйте curl
curl -L --connect-timeout 30 https://github.com/ollama/ollama/releases/latest/download/ollama-linux-amd64 -o ollama-linux-amd64

# Сделайте файл исполняемым
chmod +x ollama-linux-amd64

# Переместите в системную директорию
sudo mv ollama-linux-amd64 /usr/local/bin/ollama

# Проверьте установку
ollama --version
```

## Решение 3: Проверка firewall

Возможно, firewall блокирует исходящие соединения:

### Для Ubuntu/Debian

```bash
# Проверьте статус firewall
sudo ufw status

# Если firewall активен, разрешите исходящие соединения
sudo ufw default allow outgoing
sudo ufw reload
```

### Для CentOS/RHEL

```bash
# Проверьте статус firewall
sudo firewall-cmd --state

# Если firewall активен, проверьте правила
sudo firewall-cmd --list-all
```

## Решение 4: VPS не имеет доступа в интернет

Если ping к 8.8.8.8 не работает, VPS не имеет доступа в интернет.

### Что делать:

1. **Проверьте настройки сети в панели управления VPS:**
   - Убедитесь, что сетевой интерфейс активен
   - Проверьте настройки IP адреса

2. **Свяжитесь с поддержкой VPS провайдера:**
   - Сообщите, что VPS не имеет доступа в интернет
   - Попросите проверить настройки сети

3. **Проверьте настройки маршрутизации:**
   ```bash
   # Проверка маршрутов
   ip route
   
   # Проверка сетевых интерфейсов
   ip addr
   ```

## Решение 5: Использование прокси (если доступен)

Если у вас есть прокси-сервер:

```bash
# Установите переменные прокси
export http_proxy=http://прокси-сервер:порт
export https_proxy=http://прокси-сервер:порт

# Попробуйте установить Ollama снова
curl -fsSL https://ollama.com/install.sh | sh
```

## Решение 6: Альтернативный способ - скачать файл локально

Если ничего не помогает:

1. **Скачайте Ollama на ваш локальный компьютер:**
   - Перейдите на https://github.com/ollama/ollama/releases/latest
   - Скачайте файл `ollama-linux-amd64`

2. **Загрузите файл на VPS через панель управления:**
   - Используйте функцию "Загрузка файлов" в панели управления
   - Или используйте SCP (если SSH работает)

3. **Установите на VPS:**
   ```bash
   # Перейдите в директорию с файлом
   cd /путь/к/файлу
   
   # Сделайте файл исполняемым
   chmod +x ollama-linux-amd64
   
   # Переместите в системную директорию
   sudo mv ollama-linux-amd64 /usr/local/bin/ollama
   
   # Проверьте установку
   ollama --version
   ```

## Проверка после установки

После установки Ollama проверьте:

```bash
# Проверка версии
ollama --version

# Запуск сервера
ollama serve

# В другом терминале проверка работы
curl http://localhost:11434/api/tags
```

## Дополнительные команды для диагностики

```bash
# Проверка сетевых интерфейсов
ip addr show

# Проверка маршрутизации
ip route show

# Проверка DNS
cat /etc/resolv.conf

# Проверка доступности портов
netstat -tuln

# Проверка процессов
ps aux | grep ollama
```
