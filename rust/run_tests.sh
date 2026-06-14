#!/bin/bash

# Test runner script - ensures database connection works before running tests

set -e

echo "=== School Backend Test Runner ==="
echo ""

# Check if PostgreSQL is running
echo "📋 Checking PostgreSQL connection..."
if ! PGPASSWORD="password" psql -h localhost -U postgres -d myschool -c "SELECT 1;" > /dev/null 2>&1; then
    echo "❌ PostgreSQL not responding on localhost:5432"
    echo "   Start PostgreSQL and try again:"
    echo "   sudo systemctl start postgresql"
    exit 1
fi
echo "✓ PostgreSQL is running"

# Check if API server is running
echo "📋 Checking API server at http://127.0.0.1:8080..."
if ! curl -s http://127.0.0.1:8080/api/health > /dev/null 2>&1; then
    echo "⚠️  API server not running. Starting in background..."
    timeout 300 cargo run > /tmp/server.log 2>&1 &
    SERVER_PID=$!
    
    # Set up trap to clean up server on exit
    trap "echo 'Cleaning up server...'; kill $SERVER_PID 2>/dev/null || true" EXIT
    
    sleep 3
    
    # Check if server is now running
    if ! curl -s http://127.0.0.1:8080/api/health > /dev/null 2>&1; then
        echo "❌ Failed to start server. Check logs:"
        tail -20 /tmp/server.log
        kill $SERVER_PID 2>/dev/null || true
        exit 1
    fi
    echo "✓ Server started (PID: $SERVER_PID)"
else
    echo "✓ API server is already running"
fi

echo ""
echo "🧪 Running tests (--test-threads=1 to avoid connection conflicts)..."
echo "=================================================="
echo ""

# Run all tests with serial execution
cargo test -- --nocapture --test-threads=1

echo ""
echo "✅ Test run complete!"
