#!/bin/bash

# Детальная проверка подключения к Ollama из Android эмулятора

echo "🔍 Detailed Ollama connection test for Android emulator..."
echo ""

# Проверка 1: Ollama на Mac
echo "1️⃣ Checking Ollama on Mac (localhost)..."
if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "   ✅ Ollama is running on Mac"
    echo "   Response:"
    curl -s http://localhost:11434/api/tags | head -10
else
    echo "   ❌ Ollama is NOT running on Mac"
    echo "   💡 Run: ollama serve"
    echo ""
    echo "   Checking if Ollama process exists..."
    if pgrep -f "ollama serve" > /dev/null; then
        echo "   ⚠️  Ollama process found, but server not responding"
        echo "   💡 Try: pkill ollama && ollama serve"
    else
        echo "   ⚠️  Ollama process not found"
        echo "   💡 Start Ollama: ollama serve"
    fi
    exit 1
fi

echo ""

# Проверка 2: Эмулятор доступен
echo "2️⃣ Checking Android emulator..."
if ! adb devices | grep -q "device$"; then
    echo "   ❌ No Android emulator/device found"
    echo "   💡 Start an emulator or connect a device"
    exit 1
else
    echo "   ✅ Android emulator/device found"
    adb devices
fi

echo ""

# Проверка 3: Ping до хоста
echo "3️⃣ Testing ping to host (10.0.2.2)..."
if adb shell ping -c 1 10.0.2.2 > /dev/null 2>&1; then
    echo "   ✅ Can ping 10.0.2.2 (emulator can reach host)"
else
    echo "   ❌ Cannot ping 10.0.2.2"
    echo "   💡 This might indicate network configuration issues"
    echo "   💡 Try restarting the emulator"
fi

echo ""

# Проверка 4: Доступность Ollama из эмулятора
echo "4️⃣ Testing Ollama connection from emulator (10.0.2.2:11434)..."
if adb shell curl -s --connect-timeout 5 http://10.0.2.2:11434/api/tags > /dev/null 2>&1; then
    echo "   ✅ Ollama is accessible from emulator"
    echo "   Response:"
    adb shell curl -s http://10.0.2.2:11434/api/tags | head -10
else
    echo "   ❌ Ollama is NOT accessible from emulator"
    echo ""
    echo "   🔧 Troubleshooting steps:"
    echo "   1. Make sure Ollama is running: ollama serve"
    echo "   2. Test from Mac: curl http://localhost:11434/api/tags"
    echo "   3. Check firewall on Mac:"
    echo "      - System Settings → Network → Firewall"
    echo "      - Make sure Ollama is allowed"
    echo "   4. Try restarting emulator"
    echo "   5. Check emulator network settings"
    echo ""
    echo "   Testing with verbose curl..."
    adb shell curl -v http://10.0.2.2:11434/api/tags 2>&1 | head -20
    exit 1
fi

echo ""

# Проверка 5: Тест генерации embedding
echo "5️⃣ Testing embedding generation..."
TEST_RESPONSE=$(curl -s http://localhost:11434/api/embed -d '{
  "model": "nomic-embed-text",
  "input": "test"
}')

if echo "$TEST_RESPONSE" | grep -q "embeddings"; then
    echo "   ✅ Embedding generation works"
    EMBEDDING_SIZE=$(echo "$TEST_RESPONSE" | grep -o '"embeddings":\[\[.*\]\]' | grep -o ',' | wc -l)
    echo "   Embedding dimensions: ~$((EMBEDDING_SIZE + 1))"
else
    echo "   ⚠️  Embedding generation test failed"
    echo "   Response: $TEST_RESPONSE"
fi

echo ""

# Проверка 6: Тест чата
echo "6️⃣ Testing chat API..."
CHAT_RESPONSE=$(curl -s --max-time 30 http://localhost:11434/api/chat -d '{
  "model": "llama3.2:3b",
  "messages": [{"role": "user", "content": "Hi"}],
  "stream": false
}')

if echo "$CHAT_RESPONSE" | grep -q "message"; then
    echo "   ✅ Chat API works"
else
    echo "   ⚠️  Chat API test failed or model not installed"
    echo "   💡 Install model: ollama pull llama3.2:3b"
fi

echo ""
echo "🎉 Connection test complete!"
echo ""
echo "📋 Summary:"
echo "   - Ollama URL for emulator: http://10.0.2.2:11434"
echo "   - Model: llama3.2:3b (default)"
echo "   - Embedding model: nomic-embed-text"
echo ""
echo "✅ Ready to use Ollama in the app!"
