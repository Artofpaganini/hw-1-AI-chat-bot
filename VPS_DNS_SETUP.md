# Решение проблемы: DNS не работает на VPS

## Проблема: "Could not resolve host"

### Симптомы

Ping работает:
```bash
ping -c 3 8.8.8.8
# Работает!
```

Но curl не может разрешить доменное имя:
```bash
curl -fsSL https://ollama.com/install.sh | sh
curl: (6) Could not resolve host: ollama.com
```

### Причина

DNS серверы не настроены на VPS. VPS может подключаться к интернету по IP адресам, но не может преобразовывать доменные имена в IP адреса.

## Решение: Настройка DNS

### Шаг 1: Проверьте текущие DNS настройки

В веб-консоли VPS выполните:
```bash
cat /etc/resolv.conf
```

**Если файл пустой или содержит нерабочие DNS серверы:**
- Нужно настроить DNS серверы

### Шаг 2: Установите DNS серверы Google

#### Для Ubuntu/Debian

**Временная настройка (работает до перезагрузки):**
```bash
# Очистите старые настройки
sudo rm /etc/resolv.conf

# Создайте новый файл с DNS серверами Google
echo "nameserver 8.8.8.8" | sudo tee /etc/resolv.conf
echo "nameserver 8.8.4.4" | sudo tee -a /etc/resolv.conf
```

**ВАЖНО:** Если видите предупреждение:
```
sudo: unable to resolve host 6448521-hq958575.twc1.net: Temporary failure in name resolution
```

**Это не ошибка!** Это просто предупреждение от sudo. Команды выполнились успешно, если вы видите:
```
nameserver 8.8.8.8
nameserver 8.8.4.4
```

**Чтобы убрать предупреждение (опционально):**
```bash
# Добавьте имя хоста в /etc/hosts
echo "127.0.0.1 6448521-hq958575.twc1.net" | sudo tee -a /etc/hosts
```

Но это не обязательно - DNS уже настроен и работает!

**Постоянная настройка (работает после перезагрузки):**
```bash
# Отредактируйте конфигурационный файл
sudo nano /etc/systemd/resolved.conf

# Найдите и раскомментируйте/измените строки:
# DNS=8.8.8.8 8.8.4.4
# FallbackDNS=1.1.1.1

# Сохраните файл (Ctrl+O, Enter, Ctrl+X)

# Перезапустите службу DNS
sudo systemctl restart systemd-resolved

# Проверьте настройки
cat /etc/resolv.conf
```

#### Для CentOS/RHEL

**Временная настройка:**
```bash
# Очистите старые настройки
sudo rm /etc/resolv.conf

# Создайте новый файл с DNS серверами Google
echo "nameserver 8.8.8.8" | sudo tee /etc/resolv.conf
echo "nameserver 8.8.4.4" | sudo tee -a /etc/resolv.conf
```

**Постоянная настройка:**
```bash
# Найдите имя сетевого интерфейса
ip addr show
# Обычно это eth0, ens3, или enp0s3

# Отредактируйте конфигурационный файл (замените eth0 на ваш интерфейс)
sudo nano /etc/sysconfig/network-scripts/ifcfg-eth0

# Добавьте или измените строки:
# DNS1=8.8.8.8
# DNS2=8.8.4.4

# Сохраните файл (Ctrl+O, Enter, Ctrl+X)

# Перезапустите сеть
sudo systemctl restart network

# Проверьте настройки
cat /etc/resolv.conf
```

### Шаг 3: Проверьте работу DNS

После настройки проверьте:
```bash
# Проверка DNS
nslookup ollama.com

# Должен вернуться IP адрес, например:
# Server:         8.8.8.8
# Address:        8.8.8.8#53
# 
# Non-authoritative answer:
# Name:   ollama.com
# Address: 104.21.xx.xx
```

**Если nslookup работает:**
- DNS настроен правильно!
- Можете продолжать установку Ollama

**Если nslookup не работает:**
- Проверьте, что файл `/etc/resolv.conf` содержит правильные DNS серверы
- Попробуйте перезагрузить VPS (если возможно)
- Свяжитесь с поддержкой VPS провайдера

### Шаг 4: Установите Ollama

После настройки DNS попробуйте установить Ollama:
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

**Если все работает:**
- Вы увидите процесс установки Ollama
- Продолжайте с установкой модели

## Альтернативные DNS серверы

Если Google DNS (8.8.8.8) не работает, попробуйте другие:

**Cloudflare DNS:**
```bash
echo "nameserver 1.1.1.1" | sudo tee /etc/resolv.conf
echo "nameserver 1.0.0.1" | sudo tee -a /etc/resolv.conf
```

**Quad9 DNS:**
```bash
echo "nameserver 9.9.9.9" | sudo tee /etc/resolv.conf
echo "nameserver 149.112.112.112" | sudo tee -a /etc/resolv.conf
```

## Проверка всех компонентов

После настройки DNS проверьте все компоненты:

```bash
# 1. Проверка интернета (ICMP)
ping -c 3 8.8.8.8

# 2. Проверка DNS
nslookup ollama.com
nslookup google.com

# 3. Проверка HTTP/HTTPS
curl -I https://www.google.com

# 4. Установка Ollama
curl -fsSL https://ollama.com/install.sh | sh
```

## Важные замечания

1. **Временная vs Постоянная настройка:**
   - Временная настройка работает только до перезагрузки VPS
   - Постоянная настройка сохраняется после перезагрузки
   - Рекомендуется использовать постоянную настройку

2. **Firewall для DNS:**
   - Убедитесь, что в firewall разрешен исходящий UDP трафик на порт 53
   - Это необходимо для DNS запросов

3. **Проверка после настройки:**
   - Всегда проверяйте DNS командой `nslookup` перед установкой Ollama

## Если ничего не помогает

1. **Проверьте firewall:**
   - Убедитесь, что разрешен исходящий UDP трафик на порт 53
   - См. инструкции в `VPS_FIREWALL_SETUP.md`

2. **Свяжитесь с поддержкой:**
   - Сообщите, что DNS не работает
   - Попросите проверить настройки сети

3. **Используйте IP адреса напрямую:**
   - Если DNS не работает, можно скачать Ollama напрямую по IP
   - Но это сложнее и не рекомендуется
