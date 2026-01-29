#!/bin/bash
# Скрипт для объединения всех веток в hw-32

CURRENT_BRANCH="hw-32"
BRANCHES="hw-17 hw-18 hw-19 hw-20 hw-21 hw-22"

echo "Starting merge of all branches into $CURRENT_BRANCH..."

for branch in $BRANCHES; do
    echo ""
    echo "=== Merging $branch ==="
    
    # Получаем список всех Kotlin/Java файлов из ветки
    git ls-tree -r --name-only $branch | grep -E "\.(kt|java)$" | grep -v "build\|test" | while read file; do
        # Проверяем, существует ли файл в текущей ветке
        if [ ! -f "$file" ]; then
            echo "  Copying new file: $file"
            mkdir -p "$(dirname "$file")"
            git show $branch:"$file" > "$file" 2>/dev/null || echo "    Failed to copy $file"
        else
            # Проверяем, отличается ли файл
            if [ -n "$(git diff $CURRENT_BRANCHES..$branch -- "$file" 2>/dev/null | head -1)" ]; then
                echo "  File differs: $file (manual merge needed)"
            fi
        fi
    done
done

echo ""
echo "Merge complete! Please review conflicts and update dependencies."
