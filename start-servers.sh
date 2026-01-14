#!/bin/bash

# Скрипт для запуска всех MCP серверов

# Функция для чтения значения из local.properties
read_local_property() {
    local key=$1
    local properties_file="local.properties"
    if [ -f "$properties_file" ]; then
        # Ищем строку с ключом, которая не закомментирована
        grep "^${key}=" "$properties_file" | grep -v "^#" | head -1 | cut -d'=' -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
    fi
}

# Загрузка GITHUB_PERSONAL_ACCESS_TOKEN из local.properties
LOCAL_PROP_TOKEN=$(read_local_property "GITHUB_PERSONAL_ACCESS_TOKEN")
if [ -n "$LOCAL_PROP_TOKEN" ]; then
    export GITHUB_PERSONAL_ACCESS_TOKEN="$LOCAL_PROP_TOKEN"
    export GITHUB_PAT="$LOCAL_PROP_TOKEN"
    echo "✅ Loaded GITHUB_PERSONAL_ACCESS_TOKEN from local.properties"
fi

echo "Starting all MCP servers..."

# Запуск GitHub MCP Server в фоне
echo "Starting GitHub MCP Server on port 8083..."
cd github-mcp-server
./start-server.sh 8083 > /tmp/github-mcp-server.log 2>&1 &
GITHUB_PID=$!
cd ..

# Запуск Git MCP Server в фоне
echo "Starting Git MCP Server on port 8084..."
cd git-mcp-server
./start-server.sh 8084 > /tmp/git-mcp-server.log 2>&1 &
GIT_PID=$!
cd ..

# Запуск User Format MCP Server в фоне
echo "Starting User Format MCP Server on port 8085..."
cd user-format-mcp-server
./start-server.sh 8085 > /tmp/user-format-mcp-server.log 2>&1 &
USER_FORMAT_PID=$!
cd ..

echo ""
echo "Servers started:"
echo "  GitHub MCP Server: PID $GITHUB_PID (port 8083)"
echo "  Git MCP Server: PID $GIT_PID (port 8084)"
echo "  User Format MCP Server: PID $USER_FORMAT_PID (port 8085)"
echo ""
echo "Logs:"
echo "  GitHub: /tmp/github-mcp-server.log"
echo "  Git: /tmp/git-mcp-server.log"
echo "  User Format: /tmp/user-format-mcp-server.log"
echo ""
echo "To stop servers, run:"
echo "  kill $GITHUB_PID $GIT_PID $USER_FORMAT_PID"
echo ""
echo "Press Ctrl+C to stop all servers"

# Ожидание сигнала завершения
trap "kill $GITHUB_PID $GIT_PID $USER_FORMAT_PID 2>/dev/null; exit" INT TERM

# Ожидание завершения процессов
wait

