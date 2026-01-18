#!/bin/bash

# Скрипт для автоматического создания и пуша git тега с инкрементом версии

# Не используем set -e, чтобы обрабатывать ошибки вручную
set -o pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

echo "=========================================="
echo "Creating and pushing release tag"
echo "=========================================="
echo ""

# Получаем последний тег
LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")

if [ -z "$LAST_TAG" ]; then
    echo "No existing tags found. Starting with v1.0.0"
    NEW_TAG="v1.0.0"
else
    echo "Last tag: $LAST_TAG"
    
    # Извлекаем версию из тега (убираем 'v' префикс)
    VERSION=${LAST_TAG#v}
    
    # Разбиваем на части
    IFS='.' read -ra VERSION_PARTS <<< "$VERSION"
    MAJOR=${VERSION_PARTS[0]:-1}
    MINOR=${VERSION_PARTS[1]:-0}
    PATCH=${VERSION_PARTS[2]:-0}
    
    echo "Current version: $MAJOR.$MINOR.$PATCH"
    
    # Инкрементируем версию
    PATCH=$((PATCH + 1))
    
    # Если patch достиг 15, сбрасываем и инкрементируем minor
    if [ $PATCH -ge 15 ]; then
        PATCH=0
        MINOR=$((MINOR + 1))
        echo "Patch reached 15, incrementing minor version"
    fi
    
    # Если minor достиг определенного значения, можно инкрементировать major
    # (но по умолчанию не делаем этого автоматически)
    
    NEW_TAG="v$MAJOR.$MINOR.$PATCH"
    echo "New tag: $NEW_TAG"
fi

echo ""
echo "Checking if tag $NEW_TAG already exists..."

# Функция для проверки существования тега
check_tag_exists() {
    local tag=$1
    # Проверяем локально
    if git rev-parse "$tag" >/dev/null 2>&1; then
        return 0
    fi
    # Проверяем на remote
    if git ls-remote --tags origin 2>/dev/null | grep -q "refs/tags/$tag$"; then
        return 0
    fi
    return 1
}

# Проверяем, существует ли тег
if check_tag_exists "$NEW_TAG"; then
    echo "⚠️  Tag $NEW_TAG already exists (locally or on remote)"
    echo ""
    echo "Tag already exists, incrementing version..."
    
    # Инкрементируем версию еще раз
    VERSION=${NEW_TAG#v}
    IFS='.' read -ra VERSION_PARTS <<< "$VERSION"
    MAJOR=${VERSION_PARTS[0]:-1}
    MINOR=${VERSION_PARTS[1]:-0}
    PATCH=${VERSION_PARTS[2]:-0}
    
    PATCH=$((PATCH + 1))
    
    if [ $PATCH -ge 15 ]; then
        PATCH=0
        MINOR=$((MINOR + 1))
        echo "Patch reached 15, incrementing minor version"
    fi
    
    NEW_TAG="v$MAJOR.$MINOR.$PATCH"
    echo "New tag after increment: $NEW_TAG"
    
    # Проверяем еще раз (максимум 5 попыток)
    MAX_ATTEMPTS=5
    ATTEMPT=1
    while check_tag_exists "$NEW_TAG" && [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
        echo "⚠️  Tag $NEW_TAG also exists, incrementing again..."
        VERSION=${NEW_TAG#v}
        IFS='.' read -ra VERSION_PARTS <<< "$VERSION"
        MAJOR=${VERSION_PARTS[0]:-1}
        MINOR=${VERSION_PARTS[1]:-0}
        PATCH=${VERSION_PARTS[2]:-0}
        
        PATCH=$((PATCH + 1))
        
        if [ $PATCH -ge 15 ]; then
            PATCH=0
            MINOR=$((MINOR + 1))
        fi
        
        NEW_TAG="v$MAJOR.$MINOR.$PATCH"
        ATTEMPT=$((ATTEMPT + 1))
    done
    
    if check_tag_exists "$NEW_TAG"; then
        echo "❌ Error: Could not find available tag after $MAX_ATTEMPTS attempts."
        echo "Last attempted tag: $NEW_TAG"
        echo "Please clean up tags manually or create tag manually."
        exit 1
    fi
fi

echo "Creating tag: $NEW_TAG"
if ! git tag "$NEW_TAG" 2>/dev/null; then
    echo "❌ Error: Failed to create tag $NEW_TAG"
    exit 1
fi

echo "Pushing tag to origin..."
if ! git push origin "$NEW_TAG" 2>/dev/null; then
    echo "❌ Error: Failed to push tag $NEW_TAG to origin"
    exit 1
fi

echo ""
echo "=========================================="
echo "✅ Successfully created and pushed tag: $NEW_TAG"
echo "=========================================="
echo ""
echo "GitHub Actions will automatically:"
echo "  - Run AI Release Pipeline"
echo "  - Build APK and AAB"
echo "  - Create GitHub Release"
echo ""
echo "You can check progress at:"
echo "  https://github.com/$(git config --get remote.origin.url | sed 's/.*github.com[:/]\(.*\)\.git/\1/')/actions"
