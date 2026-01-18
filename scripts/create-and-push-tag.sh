#!/bin/bash

# Скрипт для автоматического создания и пуша git тега с инкрементом версии

set -e

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
echo "Creating tag: $NEW_TAG"
git tag "$NEW_TAG"

echo "Pushing tag to origin..."
git push origin "$NEW_TAG"

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
