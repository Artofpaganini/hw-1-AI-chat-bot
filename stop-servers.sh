#!/bin/bash

# Скрипт для остановки всех локальных MCP серверов

echo "Stopping all MCP servers..."

# Порты, на которых работают MCP серверы
PORTS=(8081 8084 8085 8086)

STOPPED_COUNT=0

for PORT in "${PORTS[@]}"; do
    # Находим PID процесса, использующего порт
    PID=$(lsof -ti :$PORT 2>/dev/null)
    
    if [ -n "$PID" ]; then
        # Получаем полную информацию о процессе
        PROCESS_INFO=$(ps -p $PID -o comm=,args= 2>/dev/null)
        
        # Проверяем, что это действительно MCP сервер, а не эмулятор или другой процесс
        if echo "$PROCESS_INFO" | grep -qiE "(mcp|project-helper|ollama-mcp|git-mcp|user-format|\.jar)" && \
           ! echo "$PROCESS_INFO" | grep -qiE "(emulator|qemu|android|adb)"; then
            echo "Stopping MCP server on port $PORT (PID: $PID)..."
            
            # Останавливаем процесс
            kill $PID 2>/dev/null
            
            # Ждем немного и проверяем, остановился ли процесс
            sleep 1
            
            # Если процесс еще работает, принудительно завершаем
            if kill -0 $PID 2>/dev/null; then
                echo "  Force killing process $PID..."
                kill -9 $PID 2>/dev/null
            fi
            
            STOPPED_COUNT=$((STOPPED_COUNT + 1))
            echo "  ✅ Stopped MCP server on port $PORT"
        else
            echo "  ⚠️  Port $PORT is used by non-MCP process (PID: $PID), skipping..."
            echo "     Process: $PROCESS_INFO"
        fi
    else
        echo "  ℹ️  No process found on port $PORT"
    fi
done

echo ""
if [ $STOPPED_COUNT -eq 0 ]; then
    echo "No MCP servers were running."
else
    echo "✅ Stopped $STOPPED_COUNT MCP server(s)"
fi

# Также проверяем процессы по имени (на случай, если порт не определен)
echo ""
echo "Checking for MCP server processes by name..."

MCP_PROCESSES=(
    "project-helper-mcp-server"
    "ollama-mcp-server"
    "git-mcp-server"
    "user-format-mcp-server"
)

for PROCESS in "${MCP_PROCESSES[@]}"; do
    # Ищем процессы по имени JAR файла и по пути
    PIDS=$(pgrep -f "$PROCESS" 2>/dev/null)
    
    # Также ищем java процессы с JAR файлами
    if [ "$PROCESS" = "project-helper-mcp-server" ]; then
        JAVA_PIDS=$(pgrep -f "project-helper-mcp-server.*\.jar" 2>/dev/null)
        if [ -n "$JAVA_PIDS" ]; then
            PIDS="$PIDS $JAVA_PIDS"
        fi
    fi
    
    if [ -n "$PIDS" ]; then
        # Убираем дубликаты
        UNIQUE_PIDS=$(echo $PIDS | tr ' ' '\n' | sort -u | tr '\n' ' ')
        for PID in $UNIQUE_PIDS; do
            # Проверяем, что процесс еще существует и это не эмулятор
            if kill -0 $PID 2>/dev/null; then
                PROCESS_INFO=$(ps -p $PID -o comm=,args= 2>/dev/null)
                # Дополнительная проверка, что это не эмулятор
                if ! echo "$PROCESS_INFO" | grep -qiE "(emulator|qemu|android|adb)"; then
                    echo "Found $PROCESS process: PID $PID"
                    echo "  Stopping PID $PID..."
                    kill $PID 2>/dev/null
                    sleep 1
                    if kill -0 $PID 2>/dev/null; then
                        echo "  Force killing PID $PID..."
                        kill -9 $PID 2>/dev/null
                    fi
                    echo "  ✅ Stopped PID $PID"
                    STOPPED_COUNT=$((STOPPED_COUNT + 1))
                else
                    echo "  ⚠️  Skipping PID $PID (appears to be emulator/Android process)"
                fi
            fi
        done
    fi
done

# Дополнительная проверка для java процессов с MCP серверами
echo ""
echo "Checking for Java processes running MCP servers..."

JAVA_MCP_PIDS=$(ps aux | grep -E "java.*mcp.*server.*\.jar" | grep -v grep | grep -vE "(emulator|qemu|android|adb)" | awk '{print $2}' 2>/dev/null)

if [ -n "$JAVA_MCP_PIDS" ]; then
    for PID in $JAVA_MCP_PIDS; do
        if kill -0 $PID 2>/dev/null; then
            PROCESS_INFO=$(ps -p $PID -o comm=,args= 2>/dev/null)
            # Дополнительная проверка, что это действительно MCP сервер
            if echo "$PROCESS_INFO" | grep -qiE "(mcp|project-helper|ollama-mcp|git-mcp|user-format)" && \
               ! echo "$PROCESS_INFO" | grep -qiE "(emulator|qemu|android|adb)"; then
                echo "Found Java MCP server process: PID $PID"
                echo "  Stopping PID $PID..."
                kill $PID 2>/dev/null
                sleep 1
                if kill -0 $PID 2>/dev/null; then
                    echo "  Force killing PID $PID..."
                    kill -9 $PID 2>/dev/null
                fi
                echo "  ✅ Stopped PID $PID"
                STOPPED_COUNT=$((STOPPED_COUNT + 1))
            else
                echo "  ⚠️  Skipping PID $PID (not a recognized MCP server or appears to be emulator/Android process)"
            fi
        fi
    done
fi

echo ""
echo "Done!"
