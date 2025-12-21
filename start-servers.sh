#!/bin/bash

# Скрипт для запуска всех MCP серверов

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

echo ""
echo "Servers started:"
echo "  Weather MCP Server: PID $WEATHER_PID (port 8080)"
echo "  Google Storage MCP Server: PID $GOOGLE_PID (port 8081)"
echo "  Remote Control MCP Server: PID $CONTROL_PID (port 8082)"
echo ""
echo "Logs:"
echo "  Weather: /tmp/weather-mcp-server.log"
echo "  Google Storage: /tmp/google-storage-mcp-server.log"
echo "  Remote Control: /tmp/remote-control-mcp-server.log"
echo ""
echo "To stop servers, run:"
echo "  kill $WEATHER_PID $GOOGLE_PID $CONTROL_PID"
echo ""
echo "Press Ctrl+C to stop all servers"

# Ожидание сигнала завершения
trap "kill $WEATHER_PID $GOOGLE_PID $CONTROL_PID 2>/dev/null; exit" INT TERM

# Ожидание завершения процессов
wait

