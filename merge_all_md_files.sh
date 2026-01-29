#!/bin/bash
# Скрипт для объединения всех .md файлов из всех веток

CURRENT_BRANCH="hw-32"
BRANCHES="hw-5 hw-6 hw-7 hw-8 hw-9 hw-10 hw-11 hw-12 hw-13 hw-14 hw-15 hw-16 hw-17 hw-18 hw-19 hw-20 hw-21 hw-22 hw-23 hw-24 hw-25 hw-26 hw-27 hw-28 hw-29 hw-30 hw-31"

echo "=== Объединение всех .md файлов из всех веток ==="
echo ""

for branch in $BRANCHES; do
    echo "=== Обработка ветки $branch ==="
    
    # Получаем список всех .md файлов из ветки
    git ls-tree -r --name-only $branch | grep "\.md$" | grep -v "node_modules\|\.gradle\|\.idea" | while read file; do
        # Пропускаем файлы из старых директорий, которые не нужны
        if [[ "$file" == *"node_modules"* ]] || [[ "$file" == *".gradle"* ]]; then
            continue
        fi
        
        # Проверяем, существует ли файл в текущей ветке
        if [ ! -f "$file" ]; then
            echo "  [NEW] Копирую новый файл: $file"
            mkdir -p "$(dirname "$file")"
            if git show $branch:"$file" > "$file" 2>/dev/null; then
                echo "    ✓ Успешно скопирован"
            else
                echo "    ✗ Не удалось скопировать $file"
            fi
        else
            # Проверяем, отличается ли файл
            if [ -n "$(git diff $CURRENT_BRANCH..$branch -- "$file" 2>/dev/null | head -1)" ]; then
                # Если файл отличается, создаем версию с суффиксом ветки
                branch_suffix=$(echo $branch | sed 's/hw-//')
                new_file="${file%.md}_${branch_suffix}.md"
                echo "  [DIFF] Файл отличается, создаю копию: $new_file"
                git show $branch:"$file" > "$new_file" 2>/dev/null && echo "    ✓ Создана копия" || echo "    ✗ Ошибка"
            fi
        fi
    done
    echo ""
done

echo "=== Объединение .md файлов завершено ==="
