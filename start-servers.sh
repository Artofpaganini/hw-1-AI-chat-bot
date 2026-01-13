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

# Запуск Weather MCP Server в фоне
echo "Starting Weather MCP Server on port 8080..."
cd weather-mcp-server
./start-server.sh 8080 > /tmp/weather-mcp-server.log 2>&1 &
WEATHER_PID=$!
cd ..

# Запуск Google Storage MCP Server в фоне
echo "Starting Google Storage MCP Server on port 8081..."
cd google-storage-mcp-server
./start-server.sh 8081 > /tmp/google-storage-mcp-server.log 2>&1 &
GOOGLE_PID=$!
cd ..

# Запуск Remote Control MCP Server в фоне
echo "Starting Remote Control MCP Server on port 8082..."
cd remote-control-mcp-server
./start-server.sh 8082 > /tmp/remote-control-mcp-server.log 2>&1 &
CONTROL_PID=$!
cd ..

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

echo ""
echo "Servers started:"
echo "  Weather MCP Server: PID $WEATHER_PID (port 8080)"
echo "  Google Storage MCP Server: PID $GOOGLE_PID (port 8081)"
echo "  Remote Control MCP Server: PID $CONTROL_PID (port 8082)"
echo "  GitHub MCP Server: PID $GITHUB_PID (port 8083)"
echo "  Git MCP Server: PID $GIT_PID (port 8084)"
echo ""
echo "Logs:"
echo "  Weather: /tmp/weather-mcp-server.log"
echo "  Google Storage: /tmp/google-storage-mcp-server.log"
echo "  Remote Control: /tmp/remote-control-mcp-server.log"
echo "  GitHub: /tmp/github-mcp-server.log"
echo "  Git: /tmp/git-mcp-server.log"
echo ""
echo "To stop servers, run:"
echo "  kill $WEATHER_PID $GOOGLE_PID $CONTROL_PID $GITHUB_PID $GIT_PID"
echo ""
echo "Press Ctrl+C to stop all servers"

# Ожидание сигнала завершения
trap "kill $WEATHER_PID $GOOGLE_PID $CONTROL_PID $GITHUB_PID $GIT_PID 2>/dev/null; exit" INT TERM

# Ожидание завершения процессов
wait

