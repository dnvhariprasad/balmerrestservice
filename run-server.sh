#!/bin/bash

# Balmer REST Service - Start Script
# Run this script to start the Spring Boot server

echo "Starting Balmer REST Service..."
echo "================================"

# Change to script directory
cd "$(dirname "$0")"

# Check for process on port 8080 and kill it
PORT=8089
PID=$(lsof -t -i:$PORT)

if [ -n "$PID" ]; then
    echo "Killing existing process on port $PORT (PID: $PID)..."
    kill -9 $PID
    sleep 2
else
    echo "Port $PORT is free."
fi

# Skip tests and run the application
./mvnw spring-boot:run -DskipTests
