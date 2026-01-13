#!/bin/bash

# Скрипт для запуска Git MCP HTTP Wrapper

cd "$(dirname "$0")"

PORT=${1:-8084}
PROJECT_ROOT=${2:-""}

# Функция для чтения значения из local.properties
read_local_property() {
    local key=$1
    local properties_file="../local.properties"
    if [ -f "$properties_file" ]; then
        # Ищем строку с ключом, которая не закомментирована
        grep "^${key}=" "$properties_file" | grep -v "^#" | head -1 | cut -d'=' -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
    fi
}

# Загрузка PROJECT_ROOT из local.properties, если не указан
if [ -z "$PROJECT_ROOT" ]; then
    LOCAL_PROP_ROOT=$(read_local_property "PROJECT_ROOT")
    if [ -n "$LOCAL_PROP_ROOT" ]; then
        PROJECT_ROOT="$LOCAL_PROP_ROOT"
        echo "✅ Loaded PROJECT_ROOT from local.properties: $PROJECT_ROOT"
    fi
fi

# Если PROJECT_ROOT все еще не установлен, используем текущую директорию
if [ -z "$PROJECT_ROOT" ]; then
    PROJECT_ROOT="$(pwd)/.."
    echo "⚠️  PROJECT_ROOT not set, using: $PROJECT_ROOT"
fi

echo "Building Git MCP HTTP Wrapper..."
cd "$(dirname "$0")"

# Use local gradlew if exists, otherwise use root gradlew
if [ -f "./gradlew" ]; then
    ./gradlew fatJar
elif [ -f "../gradlew" ]; then
    ../gradlew :git-mcp-server:fatJar
else
    echo "❌ Gradle wrapper not found!"
    exit 1
fi

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

JAR_FILE="build/libs/git-mcp-server-1.0.0-all.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR file not found: $JAR_FILE"
    echo "Build may have failed. Check the output above."
    exit 1
fi

echo "✅ Build successful: $JAR_FILE"

echo "Starting Git MCP HTTP Wrapper on port $PORT..."
echo "Project root: $PROJECT_ROOT"
echo "Git MCP endpoint: http://0.0.0.0:$PORT/mcp"
echo "For Android emulator: http://10.0.2.2:$PORT/mcp"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

java -jar "$JAR_FILE" "$PORT" "$PROJECT_ROOT"
