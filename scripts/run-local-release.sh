#!/bin/bash

# Скрипт для локального запуска AI Release Pipeline

set -e

# Определяем корневую директорию проекта (где находится .git или settings.gradle.kts)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Переходим в корневую директорию проекта
cd "$PROJECT_ROOT"

# Проверка на help флаг
if [ "$1" == "--help" ] || [ "$1" == "-h" ]; then
    echo "Usage: $0 [previous_tag] [track] [--test-tag]"
    echo ""
    echo "Arguments:"
    echo "  previous_tag  - Previous git tag (default: auto-detect)"
    echo "  track         - Play Store track: internal/alpha/beta/production (default: internal)"
    echo "  --test-tag    - Create a test tag for testing"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Auto-detect tag, internal track"
    echo "  $0 v1.0.0                            # Use v1.0.0 as previous tag"
    echo "  $0 v1.0.0 beta                       # Use v1.0.0, deploy to beta track"
    echo "  $0 v1.0.0 internal --test-tag        # Create test tag"
    exit 0
fi

echo "🚀 Starting AI Release Pipeline (Local)"
echo "📁 Project root: $PROJECT_ROOT"

# Проверяем наличие .env файла
if [ ! -f "$PROJECT_ROOT/.env" ]; then
    echo "❌ .env file not found at $PROJECT_ROOT/.env"
    echo "Please create .env file with required variables."
    echo "Required variables:"
    echo "  DEEPSEEK_API_KEY"
    echo "  SIGNING_STORE_FILE"
    echo "  SIGNING_STORE_PASSWORD"
    echo "  SIGNING_KEY_ALIAS"
    echo "  SIGNING_KEY_PASSWORD"
    exit 1
fi

# Загружаем переменные из .env
set -a
source "$PROJECT_ROOT/.env"
set +a

# Проверяем обязательные переменные
if [ -z "$DEEPSEEK_API_KEY" ]; then
    echo "⚠️  WARNING: DEEPSEEK_API_KEY is not set. Analysis will use fallback mode."
fi

# Определяем предыдущий тег
PREV_TAG=${1:-$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || echo "")}
if [ -z "$PREV_TAG" ]; then
    PREV_TAG=$(git describe --tags --abbrev=0 HEAD~10 2>/dev/null || echo "")
fi

TRACK=${2:-internal}

echo "📋 Configuration:"
echo "  Previous tag: $PREV_TAG"
echo "  Track: $TRACK"
if [ -n "$DEEPSEEK_API_KEY" ]; then
    echo "  DeepSeek API: ✅ Set"
else
    echo "  DeepSeek API: ❌ Not set"
fi

# Создаем временный тег для тестирования (опционально)
if [ "$3" == "--test-tag" ]; then
    TEST_TAG="test-$(date +%s)"
    git tag $TEST_TAG
    echo "Created test tag: $TEST_TAG"
    trap "git tag -d $TEST_TAG" EXIT
fi

# Запускаем пайплайн
echo ""
echo "🔧 Running AI Release Pipeline..."
"$PROJECT_ROOT/gradlew" aiRelease \
    -PpreviousTag="$PREV_TAG" \
    -Ptrack="$TRACK" \
    --no-daemon

echo ""
echo "✅ Pipeline completed!"
echo "📁 Check results in: $PROJECT_ROOT/app/build/release-artifacts/"
