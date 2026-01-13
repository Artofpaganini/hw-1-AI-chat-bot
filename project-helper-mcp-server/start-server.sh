#!/bin/bash

# Скрипт для запуска Project Helper MCP Server

cd "$(dirname "$0")"

echo "Building Project Helper MCP Server..."
../gradlew :project-helper-mcp-server:fatJar

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

JAR_FILE="build/libs/project-helper-mcp-server-1.0.0-all.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "JAR file not found: $JAR_FILE"
    exit 1
fi

PORT=${1:-8081}
PROJECT_ROOT=${2:-"$(pwd)/.."}
OLLAMA_URL=${3:-"http://localhost:11434"}

echo "Starting Project Helper MCP Server on port $PORT..."
echo "Project root: $PROJECT_ROOT"
echo "Ollama URL: $OLLAMA_URL"
echo "MCP endpoint: http://0.0.0.0:$PORT/mcp"
echo "For Android emulator: http://10.0.2.2:$PORT/mcp"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

java -jar "$JAR_FILE" "$PORT" "$PROJECT_ROOT" "$OLLAMA_URL"
