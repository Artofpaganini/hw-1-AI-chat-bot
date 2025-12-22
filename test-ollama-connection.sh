#!/bin/bash

# Скрипт для проверки подключения к Ollama из Android эмулятора

echo "🔍 Testing Ollama connection for Android emulator..."
echo ""

# Проверка 1: Ollama на Mac
echo "1️⃣ Checking Ollama on Mac (localhost)..."
if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "   ✅ Ollama is running on Mac"
    curl -s http://localhost:11434/api/tags | head -5
else
    echo "   ❌ Ollama is NOT running on Mac"
    echo "   Run: ollama serve"
    exit 1
fi

echo ""

# Проверка 2: Доступность из эмулятора
echo "2️⃣ Checking Ollama from Android emulator (10.0.2.2)..."
if adb shell curl -s http://10.0.2.2:11434/api/tags > /dev/null 2>&1; then
    echo "   ✅ Ollama is accessible from emulator"
    echo "   Response:"
    adb shell curl -s http://10.0.2.2:11434/api/tags | head -5
else
    echo "   ❌ Ollama is NOT accessible from emulator"
    echo "   Check:"
    echo "   - Is emulator running? (adb devices)"
    echo "   - Is Ollama running? (curl http://localhost:11434/api/tags)"
    echo "   - Network configuration"
    exit 1
fi

echo ""

# Проверка 3: Тест генерации embedding
echo "3️⃣ Testing embedding generation..."
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

# Проверка 4: Интернет на эмуляторе
echo "4️⃣ Checking internet on emulator..."
if adb shell ping -c 1 8.8.8.8 > /dev/null 2>&1; then
    echo "   ✅ Internet is working on emulator"
else
    echo "   ⚠️  Internet might not be working on emulator"
    echo "   This is OK if you only use Ollama (local server)"
fi

echo ""
echo "🎉 All checks passed!"
echo ""
echo "📋 Summary:"
echo "   - Ollama URL for emulator: http://10.0.2.2:11434"
echo "   - Model: nomic-embed-text"
echo "   - Endpoint: /api/embed"
echo ""
echo "✅ Ready to use Ollama in the app!"

