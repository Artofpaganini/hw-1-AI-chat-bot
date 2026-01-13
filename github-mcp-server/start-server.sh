#!/bin/bash

# Скрипт для запуска GitHub MCP HTTP Wrapper

cd "$(dirname "$0")"

PORT=${1:-8083}
GITHUB_PAT=${GITHUB_PAT:-""}

# Функция для чтения значения из local.properties
read_local_property() {
    local key=$1
    local properties_file="../local.properties"
    if [ -f "$properties_file" ]; then
        # Ищем строку с ключом, которая не закомментирована
        grep "^${key}=" "$properties_file" | grep -v "^#" | head -1 | cut -d'=' -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
    fi
}

# Загрузка из local.properties (приоритет 1)
if [ -z "$GITHUB_PAT" ]; then
    LOCAL_PROP_TOKEN=$(read_local_property "GITHUB_PERSONAL_ACCESS_TOKEN")
    if [ -n "$LOCAL_PROP_TOKEN" ]; then
        GITHUB_PAT="$LOCAL_PROP_TOKEN"
        echo "✅ Loaded GITHUB_PERSONAL_ACCESS_TOKEN from local.properties"
    fi
fi

# Загрузка из переменной окружения (приоритет 2)
if [ -z "$GITHUB_PAT" ]; then
    GITHUB_PAT=${GITHUB_PERSONAL_ACCESS_TOKEN:-""}
fi

# Загрузка .env файла, если существует (приоритет 3)
if [ -z "$GITHUB_PAT" ] && [ -f .env ]; then
    echo "Loading environment variables from .env file..."
    export $(cat .env | grep -v '^#' | xargs)
    GITHUB_PAT=${GITHUB_PAT:-$GITHUB_PERSONAL_ACCESS_TOKEN}
fi

if [ -z "$GITHUB_PAT" ]; then
    echo "⚠️  WARNING: GITHUB_PERSONAL_ACCESS_TOKEN is not set!"
    echo ""
    echo "Please set it in one of the following ways (in order of priority):"
    echo ""
    echo "1. In local.properties (recommended):"
    echo "   GITHUB_PERSONAL_ACCESS_TOKEN=your_github_personal_access_token"
    echo ""
    echo "2. As environment variable:"
    echo "   export GITHUB_PERSONAL_ACCESS_TOKEN=your_github_personal_access_token"
    echo "   or"
    echo "   export GITHUB_PAT=your_github_personal_access_token"
    echo ""
    echo "3. In .env file in this directory:"
    echo "   GITHUB_PAT=your_github_personal_access_token"
    echo ""
    echo "To create a GitHub Personal Access Token:"
    echo "  1. Go to https://github.com/settings/tokens"
    echo "  2. Click 'Generate new token (classic)'"
    echo "  3. Select scopes: repo, read:packages, read:org"
    echo "  4. Copy the token and add it to local.properties"
    echo ""
    read -p "Do you want to continue without a token? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo "Building GitHub MCP HTTP Wrapper..."
cd "$(dirname "$0")"

# Use local gradlew if exists, otherwise use root gradlew
if [ -f "./gradlew" ]; then
    ./gradlew fatJar
elif [ -f "../gradlew" ]; then
    ../gradlew :github-mcp-server:fatJar
else
    echo "❌ Gradle wrapper not found!"
    exit 1
fi

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

JAR_FILE="build/libs/github-mcp-server-1.0.0-all.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR file not found: $JAR_FILE"
    echo "Build may have failed. Check the output above."
    exit 1
fi

echo "✅ Build successful: $JAR_FILE"

echo "Starting GitHub MCP HTTP Wrapper on port $PORT..."
echo "GitHub MCP endpoint: http://0.0.0.0:$PORT/mcp"
echo "For Android emulator: http://10.0.2.2:$PORT/mcp"
echo ""

if [ -n "$GITHUB_PAT" ]; then
    echo "✅ GitHub PAT is set (length: ${#GITHUB_PAT} characters)"
    export GITHUB_PAT
else
    echo "⚠️  GitHub PAT is not set - some features may not work"
fi

echo ""
echo "Press Ctrl+C to stop the server"
echo ""

java -jar "$JAR_FILE" "$PORT"
