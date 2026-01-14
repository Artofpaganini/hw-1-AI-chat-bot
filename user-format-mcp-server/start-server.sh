#!/bin/bash

# Скрипт для запуска User Format MCP Server

cd "$(dirname "$0")"

echo "Building User Format MCP Server..."
../gradlew :user-format-mcp-server:fatJar

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

JAR_FILE="build/libs/user-format-mcp-server-1.0.0-all.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "JAR file not found: $JAR_FILE"
    exit 1
fi

PORT=${1:-8085}
OLLAMA_URL=${2:-"http://localhost:11434"}

echo "Starting User Format MCP Server on port $PORT..."
echo "Ollama URL: $OLLAMA_URL"
echo "MCP endpoint: http://0.0.0.0:$PORT/mcp"
echo "For Android emulator: http://10.0.2.2:$PORT/mcp"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

java -jar "$JAR_FILE" "$PORT" "$OLLAMA_URL"
