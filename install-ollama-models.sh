#!/bin/bash

# Скрипт для установки и проверки локальных LLM моделей Ollama

set -e

echo "🔍 Checking Ollama installation..."

if ! command -v ollama &> /dev/null; then
    echo "❌ Ollama is not installed"
    echo "Please run ./setup-ollama.sh first"
    exit 1
fi

# Проверяем, запущен ли Ollama сервер
if ! curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "❌ Ollama server is not running"
    echo "Please run: ollama serve"
    exit 1
fi

echo "✅ Ollama is installed and running"
echo ""

# Список популярных моделей для чата
MODELS=(
    "llama3.2:3b"      # Быстрая и легкая (рекомендуется)
    "llama3.2:1b"      # Очень быстрая, минимальные требования
    "llama3:8b"        # Более мощная
    "mistral:7b"       # Альтернатива Llama
    "phi3:mini"        # Легкая модель от Microsoft
    "phi3:medium"      # Для reranking
    "qwen2.5:7b"       # Китайская модель
)

echo "📋 Available models to install:"
for i in "${!MODELS[@]}"; do
    echo "  $((i+1)). ${MODELS[$i]}"
done
echo ""

# Функция для установки модели
install_model() {
    local model=$1
    if ollama list | grep -q "$model"; then
        echo "✅ Model '$model' is already installed"
        return 0
    else
        echo "📥 Downloading model '$model'..."
        if ollama pull "$model"; then
            echo "✅ Model '$model' installed successfully"
            return 0
        else
            echo "❌ Failed to install model '$model'"
            return 1
        fi
    fi
}

# Функция для проверки модели
test_model() {
    local model=$1
    echo "🧪 Testing model '$model'..."
    
    TEST_RESPONSE=$(curl -s http://localhost:11434/api/chat -d "{
        \"model\": \"$model\",
        \"messages\": [{\"role\": \"user\", \"content\": \"Say 'Hello' in one word.\"}],
        \"stream\": false
    }")
    
    if echo "$TEST_RESPONSE" | grep -q "message"; then
        echo "✅ Model '$model' is working"
        return 0
    else
        echo "❌ Model '$model' test failed"
        return 1
    fi
}

# Интерактивный режим
if [ "$1" == "--interactive" ] || [ "$1" == "-i" ]; then
    echo "Select models to install (comma-separated numbers, or 'all' for all models):"
    read -r selection
    
    if [ "$selection" == "all" ]; then
        for model in "${MODELS[@]}"; do
            install_model "$model"
        done
    else
        IFS=',' read -ra SELECTED <<< "$selection"
        for num in "${SELECTED[@]}"; do
            num=$((num - 1))
            if [ $num -ge 0 ] && [ $num -lt ${#MODELS[@]} ]; then
                install_model "${MODELS[$num]}"
            fi
        done
    fi
else
    # Установка моделей по умолчанию
    echo "Installing default models..."
    install_model "llama3.2:3b"
    install_model "phi3:medium"
fi

echo ""
echo "📋 Installed models:"
ollama list

echo ""
echo "🧪 Testing installed models..."
for model in "${MODELS[@]}"; do
    if ollama list | grep -q "$model"; then
        test_model "$model"
    fi
done

echo ""
echo "✅ Done!"
echo ""
echo "💡 Tips:"
echo "  - Use 'ollama list' to see all installed models"
echo "  - Use 'ollama pull <model>' to install additional models"
echo "  - Use 'ollama run <model>' to test a model in CLI"
echo "  - Popular models: llama3.2:3b, mistral:7b, phi3:mini"
