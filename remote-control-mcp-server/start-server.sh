#!/bin/bash

# Скрипт для запуска Remote Control MCP Server

cd "$(dirname "$0")"

echo "Building Remote Control MCP Server..."
../gradlew :remote-control-mcp-server:fatJar

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

JAR_FILE="build/libs/remote-control-mcp-server-1.0.0-all.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "JAR file not found: $JAR_FILE"
    exit 1
fi

PORT=${1:-8082}

echo "Starting Remote Control MCP Server on port $PORT..."
echo "MCP endpoint: http://0.0.0.0:$PORT/mcp"
echo "For Android emulator: http://10.0.2.2:$PORT/mcp"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

java -jar "$JAR_FILE" "$PORT"

