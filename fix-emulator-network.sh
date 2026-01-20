#!/bin/bash

# Скрипт для исправления проблемы с сетью эмулятора (10.0.2.2 недоступен)

echo "🔧 Fixing Android emulator network configuration..."
echo ""

# Проверка 1: Эмулятор запущен
echo "1️⃣ Checking if emulator is running..."
if ! adb devices | grep -q "device$"; then
    echo "   ❌ No Android emulator/device found"
    echo "   💡 Please start an emulator first"
    exit 1
else
    echo "   ✅ Emulator is running"
    adb devices
fi

echo ""

# Проверка 2: Проверка ping до 10.0.2.2
echo "2️⃣ Testing ping to 10.0.2.2..."
PING_RESULT=$(adb shell ping -c 1 10.0.2.2 2>&1)
if echo "$PING_RESULT" | grep -q "Network is unreachable"; then
    echo "   ❌ Network is unreachable - this is the problem!"
    echo ""
    echo "   🔧 Attempting fixes..."
    
    # Решение 1: Проверка и установка DNS
    echo ""
    echo "   Fix 1: Checking DNS settings..."
    DNS1=$(adb shell getprop net.dns1 2>/dev/null | tr -d '\r')
    DNS2=$(adb shell getprop net.dns2 2>/dev/null | tr -d '\r')
    echo "   Current DNS1: $DNS1"
    echo "   Current DNS2: $DNS2"
    
    if [ -z "$DNS1" ] || [ "$DNS1" = "" ]; then
        echo "   ⚠️  DNS1 is not set, setting to 8.8.8.8..."
        adb shell "setprop net.dns1 8.8.8.8" 2>/dev/null
    fi
    
    if [ -z "$DNS2" ] || [ "$DNS2" = "" ]; then
        echo "   ⚠️  DNS2 is not set, setting to 8.8.4.4..."
        adb shell "setprop net.dns2 8.8.4.4" 2>/dev/null
    fi
    
    # Решение 2: Проверка маршрутизации
    echo ""
    echo "   Fix 2: Checking routing table..."
    ROUTE_OUTPUT=$(adb shell ip route 2>&1)
    echo "   Routing table:"
    echo "$ROUTE_OUTPUT" | head -5
    
    # Решение 3: Проверка сетевых интерфейсов
    echo ""
    echo "   Fix 3: Checking network interfaces..."
    INTERFACES=$(adb shell ip addr show 2>&1 | grep -E "inet |state" | head -10)
    echo "   Network interfaces:"
    echo "$INTERFACES"
    
    # Решение 4: Перезапуск сетевого стека (требует root)
    echo ""
    echo "   Fix 4: Attempting to restart network stack..."
    if adb root 2>/dev/null; then
        echo "   ✅ Got root access"
        adb shell "svc wifi disable && svc wifi enable" 2>/dev/null
        echo "   ✅ Restarted WiFi"
        sleep 2
    else
        echo "   ⚠️  Cannot get root access (this is normal for non-rooted devices)"
        echo "   💡 Try restarting the emulator manually"
    fi
    
    echo ""
    echo "   ⚠️  IMPORTANT: The issue is likely with emulator network configuration"
    echo "   💡 Note: 10.0.2.2 is a LOCAL connection that does NOT require internet"
    echo "   💡 The emulator should be able to reach the host even when internet is OFF"
    echo "   💡 Try the following solutions:"
    echo ""
    echo "   Solution 1: Restart emulator with proper network settings"
    echo "   - Close the emulator"
    echo "   - In Android Studio: Tools → Device Manager → Edit (pencil icon)"
    echo "   - Advanced Settings → Network:"
    echo "     * Select 'Automatic' or 'NAT'"
    echo "     * DNS: 8.8.8.8, 8.8.4.4"
    echo "   - Save and restart emulator"
    echo ""
    echo "   Solution 2: Restart emulator from command line"
    echo "   - Find your AVD name: emulator -list-avds"
    echo "   - Stop current emulator: adb emu kill"
    echo "   - Start with DNS: emulator -avd <avd_name> -dns-server 8.8.8.8,8.8.4.4"
    echo ""
    echo "   Solution 3: Cold boot emulator"
    echo "   - Stop emulator: adb emu kill"
    echo "   - Cold boot: emulator -avd <avd_name> -wipe-data"
    echo "   (WARNING: This will wipe emulator data!)"
    echo ""
    echo "   Solution 4: Check host network"
    echo "   - Make sure host (Mac) network is working"
    echo "   - Check if localhost is accessible: curl http://localhost:11434/api/tags"
    echo "   - Check firewall settings on Mac"
    echo ""
    
    # Проверка после попыток исправления
    echo "   Testing ping again after fixes..."
    sleep 2
    PING_RESULT2=$(adb shell ping -c 1 10.0.2.2 2>&1)
    if echo "$PING_RESULT2" | grep -q "Network is unreachable"; then
        echo "   ❌ Still cannot ping 10.0.2.2"
        echo "   💡 You need to restart the emulator with proper network settings"
        echo "   💡 See solutions above"
    else
        echo "   ✅ Ping works now!"
    fi
else
    echo "   ✅ Can ping 10.0.2.2"
fi

echo ""

# Проверка 3: Проверка доступности Ollama
echo "3️⃣ Testing Ollama connection..."
if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "   ✅ Ollama is running on Mac"
    
    # Проверка из эмулятора
    if adb shell curl -s --connect-timeout 5 http://10.0.2.2:11434/api/tags > /dev/null 2>&1; then
        echo "   ✅ Ollama is accessible from emulator"
    else
        echo "   ❌ Ollama is NOT accessible from emulator"
        echo "   💡 This is because ping to 10.0.2.2 doesn't work"
        echo "   💡 Fix the network issue first (see solutions above)"
    fi
else
    echo "   ⚠️  Ollama is NOT running on Mac"
    echo "   💡 Start Ollama: ollama serve"
fi

echo ""
echo "📋 Summary:"
echo "   - If ping to 10.0.2.2 doesn't work, you need to restart emulator"
echo "   - Use Solution 1 or 2 from above"
echo "   - After restart, run this script again to verify"
echo ""
echo "✅ Network fix script complete!"
