#!/bin/bash

# Скрипт для проверки/установки Ollama и запуска сервера на Mac

set -e

echo "🔍 Checking Ollama installation..."

# Проверяем, установлен ли Ollama
if ! command -v ollama &> /dev/null; then
    echo "❌ Ollama is not installed"
    echo "📥 Installing Ollama..."
    
    # Установка через Homebrew (рекомендуемый способ)
    if command -v brew &> /dev/null; then
        echo "Using Homebrew to install Ollama..."
        brew install ollama
    else
        echo "⚠️  Homebrew not found. Please install Ollama manually:"
        echo "   1. Visit: https://ollama.ai/download"
        echo "   2. Download and install Ollama for macOS"
        echo "   3. Run this script again"
        exit 1
    fi
else
    echo "✅ Ollama is already installed"
    ollama --version
fi

# Проверяем, запущен ли Ollama сервер
if ! curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "🚀 Starting Ollama server..."
    
    # Запускаем Ollama в фоне
    ollama serve &
    OLLAMA_PID=$!
    
    # Ждем, пока сервер запустится
    echo "⏳ Waiting for Ollama server to start..."
    for i in {1..30}; do
        if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
            echo "✅ Ollama server is running"
            break
        fi
        sleep 1
    done
    
    if [ $i -eq 30 ]; then
        echo "❌ Failed to start Ollama server"
        exit 1
    fi
else
    echo "✅ Ollama server is already running"
fi

# Проверяем наличие embedding модели
echo "🔍 Checking for embedding model..."
MODEL_NAME="nomic-embed-text"

if ollama list | grep -q "$MODEL_NAME"; then
    echo "✅ Embedding model '$MODEL_NAME' is already installed"
else
    echo "📥 Downloading embedding model '$MODEL_NAME'..."
    ollama pull "$MODEL_NAME"
    echo "✅ Embedding model installed"
fi

echo ""
echo "🎉 Setup complete!"
echo ""
echo "Ollama server is running at: http://localhost:11434"
echo "For Android emulator, use: http://10.0.2.2:11434"
echo ""
echo "📋 Testing connection:"
echo "  From Mac: curl http://localhost:11434/api/tags"
echo "  From emulator: adb shell curl http://10.0.2.2:11434/api/tags"
echo ""
echo "⚠️  Important for Android emulator:"
echo "  - Make sure Ollama is running on your Mac"
echo "  - The emulator uses 10.0.2.2 to access localhost"
echo "  - Internet on emulator should work independently"
echo ""
echo "To stop the server, run: pkill ollama"

