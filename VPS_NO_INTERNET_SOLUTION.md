# Решение проблемы: VPS не имеет доступа в интернет

## Проблема: "Network is unreachable"

### Симптомы

При попытке скачать что-либо с интернета:
```bash
wget https://github.com/ollama/ollama/releases/latest/download/ollama-linux-amd64
```

Получаете ошибку:
```
Connecting to github.com (github.com)|140.82.121.3|:443... failed: Network is unreachable.
```

## Диагностика проблемы

### Шаг 1: Проверка базового подключения

```bash
# Проверка подключения к Google DNS
ping -c 3 8.8.8.8
```

**Если ping работает:**
- Интернет работает на уровне IP
- Проблема может быть в DNS или firewall
- Переходите к Шагу 2

**Если ping НЕ работает:**
- VPS не имеет доступа в интернет
- Переходите к разделу "Решение: Настройка сети"

### Шаг 2: Проверка DNS

```bash
# Проверка DNS
nslookup google.com
```

**Если DNS не работает:**
```bash
# Установите DNS серверы Google
echo "nameserver 8.8.8.8" | sudo tee /etc/resolv.conf
echo "nameserver 8.8.4.4" | sudo tee -a /etc/resolv.conf

# Проверьте снова
nslookup google.com
```

### Шаг 3: Проверка сетевых интерфейсов

```bash
# Просмотр всех сетевых интерфейсов
ip addr show

# Или
ifconfig -a
```

**Что искать:**
- Должен быть интерфейс с IP адресом (например, `eth0`, `ens3`, `enp0s3`)
- Интерфейс должен быть в состоянии `UP`

**Если интерфейс не активен:**
```bash
# Активируйте интерфейс (замените eth0 на ваш интерфейс)
sudo ip link set eth0 up

# Или
sudo ifconfig eth0 up
```

### Шаг 4: Проверка маршрутизации

```bash
# Просмотр маршрутов
ip route show

# Или
route -n
```

**Что искать:**
- Должен быть маршрут по умолчанию (default route)
- Формат: `default via X.X.X.X dev eth0`

**Если нет маршрута по умолчанию:**
```bash
# Добавьте маршрут по умолчанию (замените X.X.X.X на шлюз из панели управления)
sudo ip route add default via X.X.X.X dev eth0

# Или найдите шлюз автоматически
GATEWAY=$(ip route | grep default | awk '{print $3}')
sudo ip route add default via $GATEWAY
```

## Решение: Настройка сети

### Вариант 1: Через панель управления VPS

1. **Откройте панель управления VPS**
2. **Перейдите на вкладку "Сеть" (Network)**
3. **Проверьте настройки:**
   - IP адрес назначен
   - Сетевой интерфейс активен
   - Шлюз (Gateway) настроен
4. **Если что-то не настроено:**
   - Нажмите "Настроить сеть" или "Configure Network"
   - Следуйте инструкциям

### Вариант 2: Ручная настройка через консоль

**Для Ubuntu/Debian:**

```bash
# Отредактируйте сетевой конфигурационный файл
sudo nano /etc/netplan/50-cloud-init.yaml

# Или для старых версий
sudo nano /etc/network/interfaces
```

**Пример конфигурации для netplan:**
```yaml
network:
  version: 2
  ethernets:
    eth0:
      dhcp4: true
      # Или статический IP:
      # addresses:
      #   - 192.168.1.100/24
      # gateway4: 192.168.1.1
      # nameservers:
      #   addresses:
      #     - 8.8.8.8
      #     - 8.8.4.4
```

**Примените изменения:**
```bash
sudo netplan apply
```

**Для CentOS/RHEL:**

```bash
# Отредактируйте сетевой конфигурационный файл
sudo nano /etc/sysconfig/network-scripts/ifcfg-eth0
```

**Пример конфигурации:**
```
BOOTPROTO=dhcp
# Или статический IP:
# BOOTPROTO=static
# IPADDR=192.168.1.100
# NETMASK=255.255.255.0
# GATEWAY=192.168.1.1
# DNS1=8.8.8.8
# DNS2=8.8.4.4
ONBOOT=yes
```

**Примените изменения:**
```bash
sudo systemctl restart network
```

### Вариант 3: Свяжитесь с поддержкой VPS провайдера

Если ничего не помогает:

1. **Свяжитесь с поддержкой VPS провайдера**
2. **Сообщите:**
   - VPS не имеет доступа в интернет
   - Ошибка "Network is unreachable"
   - IP адрес вашего VPS
3. **Попросите:**
   - Проверить настройки сети
   - Включить интернет-доступ для VPS
   - Помочь настроить сетевой интерфейс

## Альтернативное решение: Установка Ollama без интернета

Если интернет на VPS недоступен, но вам нужно установить Ollama:

### Способ 1: Загрузка файла через панель управления

1. **На вашем локальном компьютере:**
   - Откройте https://github.com/ollama/ollama/releases/latest
   - Скачайте файл `ollama-linux-amd64`

2. **В панели управления VPS:**
   - Найдите функцию "Загрузка файлов" или "File Manager"
   - Загрузите файл `ollama-linux-amd64` на VPS

3. **В веб-консоли VPS:**
   ```bash
   # Найдите загруженный файл
   find / -name "ollama-linux-amd64" 2>/dev/null
   
   # Перейдите в директорию с файлом
   cd /путь/к/файлу
   
   # Сделайте файл исполняемым
   chmod +x ollama-linux-amd64
   
   # Переместите в системную директорию
   sudo mv ollama-linux-amd64 /usr/local/bin/ollama
   
   # Проверьте установку
   ollama --version
   ```

### Способ 2: Использование SCP (если SSH работает)

Если SSH работает с вашего компьютера:

```bash
# На вашем локальном компьютере
# Скачайте Ollama
wget https://github.com/ollama/ollama/releases/latest/download/ollama-linux-amd64

# Загрузите на VPS через SCP
scp ollama-linux-amd64 root@[2a03:6f00:a::1:a92c]:/tmp/

# Затем в веб-консоли VPS:
chmod +x /tmp/ollama-linux-amd64
sudo mv /tmp/ollama-linux-amd64 /usr/local/bin/ollama
ollama --version
```

## Проверка после настройки сети

После настройки сети проверьте:

```bash
# 1. Проверка подключения
ping -c 3 8.8.8.8

# 2. Проверка DNS
nslookup google.com

# 3. Проверка доступа к интернету
curl -I https://www.google.com

# 4. Если все работает, попробуйте установить Ollama
curl -fsSL https://ollama.com/install.sh | sh
```

## Важные команды для диагностики

```bash
# Проверка сетевых интерфейсов
ip addr show
ifconfig -a

# Проверка маршрутизации
ip route show
route -n

# Проверка DNS
cat /etc/resolv.conf
nslookup google.com

# Проверка подключения
ping -c 3 8.8.8.8
ping -c 3 google.com

# Проверка открытых портов
netstat -tuln
ss -tuln

# Проверка процессов
ps aux | grep network
systemctl status networking
```

## Заключение

Если VPS не имеет доступа в интернет:

1. **Сначала попробуйте настроить сеть** через панель управления или вручную
2. **Если не получается - свяжитесь с поддержкой** VPS провайдера
3. **Альтернатива:** Загрузите Ollama вручную через панель управления VPS

После настройки интернета вы сможете установить Ollama стандартным способом.
