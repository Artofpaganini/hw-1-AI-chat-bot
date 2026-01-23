#!/bin/bash

# Скрипт для запуска Ollama MCP Server

cd "$(dirname "$0")"

echo "Building Ollama MCP Server..."
../gradlew :ollama-mcp-server:fatJar

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

JAR_FILE="build/libs/ollama-mcp-server-1.0.0-all.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "JAR file not found: $JAR_FILE"
    exit 1
fi

PORT=${1:-8086}
OLLAMA_URL=${2:-"http://localhost:11434"}
PROJECT_HELPER_URL=${3:-"http://localhost:8081"}

echo "Starting Ollama MCP Server on port $PORT..."
echo "Ollama URL: $OLLAMA_URL"
echo "Project Helper URL: $PROJECT_HELPER_URL"
echo "MCP endpoint: http://0.0.0.0:$PORT/mcp"
echo "For Android emulator: http://10.0.2.2:$PORT/mcp"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

java -jar "$JAR_FILE" "$PORT" "$OLLAMA_URL" "$PROJECT_HELPER_URL"
