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

# Очищаем старые логи
> /tmp/project-helper-mcp-server.log
> /tmp/ollama-mcp-server.log
> /tmp/git-mcp-server.log
> /tmp/user-format-mcp-server.log

# Запуск Project Helper MCP Server в фоне
echo "Starting Project Helper MCP Server on port 8081..."
cd project-helper-mcp-server
./start-server.sh 8081 >> /tmp/project-helper-mcp-server.log 2>&1 &
PROJECT_HELPER_PID=$!
cd ..

# Запуск Ollama MCP Server в фоне
echo "Starting Ollama MCP Server on port 8086..."
cd ollama-mcp-server
./start-server.sh 8086 >> /tmp/ollama-mcp-server.log 2>&1 &
OLLAMA_MCP_PID=$!
cd ..

# Запуск Git MCP Server в фоне
echo "Starting Git MCP Server on port 8084..."
cd git-mcp-server
./start-server.sh 8084 >> /tmp/git-mcp-server.log 2>&1 &
GIT_PID=$!
cd ..

# Запуск User Format MCP Server в фоне
echo "Starting User Format MCP Server on port 8085..."
cd user-format-mcp-server
./start-server.sh 8085 >> /tmp/user-format-mcp-server.log 2>&1 &
USER_FORMAT_PID=$!
cd ..

# Ждем немного, чтобы серверы успели запуститься
sleep 2

echo ""
echo "Servers started:"
echo "  Project Helper MCP Server: PID $PROJECT_HELPER_PID (port 8081)"
echo "  Ollama MCP Server: PID $OLLAMA_MCP_PID (port 8086)"
echo "  Git MCP Server: PID $GIT_PID (port 8084)"
echo "  User Format MCP Server: PID $USER_FORMAT_PID (port 8085)"
echo ""
echo "Logs location:"
echo "  Project Helper: /tmp/project-helper-mcp-server.log"
echo "  Ollama MCP: /tmp/ollama-mcp-server.log"
echo "  Git: /tmp/git-mcp-server.log"
echo "  User Format: /tmp/user-format-mcp-server.log"
echo ""
echo "To stop servers, run:"
echo "  ./stop-servers.sh"
echo "  # or: kill $PROJECT_HELPER_PID $OLLAMA_MCP_PID $GIT_PID $USER_FORMAT_PID"
echo ""
echo "=========================================="
echo "Live logs from all MCP servers:"
echo "=========================================="
echo ""

# Функция для вывода логов с префиксом
tail_log() {
    local log_file=$1
    local prefix=$2
    tail -f "$log_file" 2>/dev/null | while IFS= read -r line; do
        echo "[$prefix] $line"
    done
}

# Запускаем tail для каждого лог-файла в фоне
tail_log /tmp/project-helper-mcp-server.log "PROJECT-HELPER" &
TAIL_PROJECT_HELPER_PID=$!

tail_log /tmp/ollama-mcp-server.log "OLLAMA-MCP" &
TAIL_OLLAMA_MCP_PID=$!

tail_log /tmp/git-mcp-server.log "GIT" &
TAIL_GIT_PID=$!

tail_log /tmp/user-format-mcp-server.log "USER-FORMAT" &
TAIL_USER_FORMAT_PID=$!

# Функция для очистки при выходе
cleanup() {
    echo ""
    echo "Stopping log tails..."
    kill $TAIL_PROJECT_HELPER_PID $TAIL_OLLAMA_MCP_PID $TAIL_GIT_PID $TAIL_USER_FORMAT_PID 2>/dev/null
    echo "Stopping MCP servers..."
    kill $PROJECT_HELPER_PID $OLLAMA_MCP_PID $GIT_PID $USER_FORMAT_PID 2>/dev/null
    exit
}

# Ожидание сигнала завершения
trap cleanup INT TERM

# Ожидание завершения процессов
wait

