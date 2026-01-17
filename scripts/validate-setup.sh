#!/bin/bash

# Скрипт валидации настройки AI Release Pipeline

set -e

# Определяем корневую директорию проекта
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Переходим в корневую директорию проекта
cd "$PROJECT_ROOT"

echo "🔍 Validating AI Release Pipeline Setup"
echo "========================================"
echo "📁 Project root: $PROJECT_ROOT"
echo ""

ERRORS=0
WARNINGS=0

# Проверка переменных окружения
echo "1. Checking environment variables..."
if [ -f "$PROJECT_ROOT/.env" ]; then
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
    echo "   ✅ .env file found"
else
    echo "   ⚠️  .env file not found (optional for CI/CD)"
    WARNINGS=$((WARNINGS + 1))
fi

if [ -z "$DEEPSEEK_API_KEY" ]; then
    echo "   ❌ DEEPSEEK_API_KEY not set"
    ERRORS=$((ERRORS + 1))
else
    echo "   ✅ DEEPSEEK_API_KEY is set"
fi

# Проверка DeepSeek API
echo ""
echo "2. Testing DeepSeek API..."
if [ -n "$DEEPSEEK_API_KEY" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: Bearer $DEEPSEEK_API_KEY" \
        -H "Content-Type: application/json" \
        -d '{"model":"deepseek-chat","messages":[{"role":"user","content":"test"}]}' \
        https://api.deepseek.com/v1/chat/completions || echo "000")
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
    if [ "$HTTP_CODE" == "200" ] || [ "$HTTP_CODE" == "401" ]; then
        echo "   ✅ DeepSeek API is accessible (HTTP $HTTP_CODE)"
    else
        echo "   ⚠️  DeepSeek API test failed (HTTP $HTTP_CODE)"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo "   ⏭️  Skipping (DEEPSEEK_API_KEY not set)"
fi

# Проверка Keystore
echo ""
echo "3. Checking Keystore..."
if [ -n "$SIGNING_STORE_FILE" ] && [ -f "$PROJECT_ROOT/$SIGNING_STORE_FILE" ]; then
    if command -v keytool &> /dev/null; then
        if keytool -list -v -keystore "$PROJECT_ROOT/$SIGNING_STORE_FILE" -storepass "${SIGNING_STORE_PASSWORD:-}" &> /dev/null; then
            echo "   ✅ Keystore is valid"
        else
            echo "   ❌ Keystore validation failed"
            ERRORS=$((ERRORS + 1))
        fi
    else
        echo "   ⚠️  keytool not found, skipping validation"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo "   ⚠️  Keystore file not found (required for signing)"
    WARNINGS=$((WARNINGS + 1))
fi

# Проверка Play Store Key
echo ""
echo "4. Checking Play Store Service Account..."
if [ -f "$PROJECT_ROOT/play-store-key.json" ]; then
    if command -v jq &> /dev/null; then
        if jq -e .project_id "$PROJECT_ROOT/play-store-key.json" &> /dev/null; then
            echo "   ✅ Play Store key is valid JSON"
        else
            echo "   ❌ Play Store key is invalid JSON"
            ERRORS=$((ERRORS + 1))
        fi
    else
        echo "   ⚠️  jq not found, skipping JSON validation"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo "   ⚠️  play-store-key.json not found (required for deployment)"
    WARNINGS=$((WARNINGS + 1))
fi

# Проверка SQLite
echo ""
echo "5. Checking SQLite..."
if command -v sqlite3 &> /dev/null; then
    echo "   ✅ sqlite3 is available"
else
    echo "   ⚠️  sqlite3 not found (will use JDBC driver)"
    WARNINGS=$((WARNINGS + 1))
fi

# Проверка Gradle
echo ""
echo "6. Checking Gradle..."
if [ -f "$PROJECT_ROOT/gradlew" ]; then
    GRADLE_VERSION=$("$PROJECT_ROOT/gradlew" --version | grep "Gradle" | head -n1 || echo "unknown")
    echo "   ✅ Gradle wrapper found: $GRADLE_VERSION"
    
    MIN_VERSION="8.0"
    if "$PROJECT_ROOT/gradlew" --version | grep -q "Gradle 8\." || "$PROJECT_ROOT/gradlew" --version | grep -q "Gradle [89]"; then
        echo "   ✅ Gradle version is compatible (>= 8.0)"
    else
        echo "   ⚠️  Gradle version may be too old (recommended >= 8.0)"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo "   ❌ gradlew not found"
    ERRORS=$((ERRORS + 1))
fi

# Проверка Git
echo ""
echo "7. Checking Git..."
if command -v git &> /dev/null; then
    if [ -d ".git" ]; then
        echo "   ✅ Git repository found"
        TAG_COUNT=$(git tag | wc -l)
        echo "   📌 Found $TAG_COUNT tags"
    else
        echo "   ⚠️  Not a git repository"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo "   ❌ git not found"
    ERRORS=$((ERRORS + 1))
fi

# Итоги
echo ""
echo "========================================"
echo "📊 Validation Summary:"
echo "   Errors: $ERRORS"
echo "   Warnings: $WARNINGS"
echo ""

if [ $ERRORS -eq 0 ]; then
    echo "✅ Setup is valid! You can proceed with release."
    exit 0
else
    echo "❌ Setup has errors. Please fix them before proceeding."
    exit 1
fi
