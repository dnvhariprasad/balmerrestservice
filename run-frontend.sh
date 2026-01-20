#!/bin/bash

# Balmer REST Service - Frontend Startup Script
# This script starts the React frontend development server

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/frontend"

echo "=========================================="
echo "  Balmer REST Service - Frontend"
echo "=========================================="

# Check if frontend directory exists
if [ ! -d "$FRONTEND_DIR" ]; then
    echo "Error: Frontend directory not found at $FRONTEND_DIR"
    exit 1
fi

cd "$FRONTEND_DIR"

# Check if node_modules exists, if not run npm install
if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
    if [ $? -ne 0 ]; then
        echo "Error: npm install failed"
        exit 1
    fi
    echo ""
fi

echo "Starting frontend development server..."
echo "URL: http://localhost:3000"
echo ""
echo "Note: Make sure the backend is running on http://localhost:8080"
echo "Press Ctrl+C to stop the server"
echo "=========================================="
echo ""

npm run dev
