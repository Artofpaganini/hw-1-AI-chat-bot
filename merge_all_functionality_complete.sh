#!/bin/bash
# Полное объединение всего функционала из всех веток hw-5 до hw-31 в hw-32

set -e

CURRENT_BRANCH="hw-32"
BRANCHES="hw-5 hw-6 hw-7 hw-8 hw-9 hw-10 hw-11 hw-12 hw-13 hw-14 hw-15 hw-16 hw-17 hw-18 hw-19 hw-20 hw-21 hw-22 hw-23 hw-24 hw-25 hw-26 hw-27 hw-28 hw-29 hw-30 hw-31"

echo "=== Полное объединение функционала из всех веток в $CURRENT_BRANCH ==="
echo ""

total_copied=0
total_updated=0
total_skipped=0

# Функция для копирования файла с созданием директорий
copy_file() {
    local source_file=$1
    local target_file=$2
    local branch=$3
    
    # Создаем директорию, если её нет
    mkdir -p "$(dirname "$target_file")"
    
    # Проверяем, существует ли файл в текущей ветке
    if [ -f "$target_file" ]; then
        # Сравниваем файлы
        if ! diff -q "$source_file" "$target_file" > /dev/null 2>&1; then
            # Файлы отличаются - копируем с предупреждением
            cp "$source_file" "$target_file"
            echo "   ⚠️  Обновлен (отличается): $target_file"
            total_updated=$((total_updated + 1))
        else
            # Файлы идентичны
            total_skipped=$((total_skipped + 1))
        fi
    else
        # Файл не существует - копируем
        cp "$source_file" "$target_file"
        echo "   ✅ Скопирован: $target_file"
        total_copied=$((total_copied + 1))
    fi
}

# Обрабатываем каждую ветку
for branch in $BRANCHES; do
    echo "=== Обработка ветки $branch ==="
    
    # Проверяем существование ветки
    if ! git rev-parse --verify "$branch" > /dev/null 2>&1; then
        echo "⚠️  Ветка $branch не найдена, пропускаем"
        echo ""
        continue
    fi
    
    # Получаем список всех Kotlin и Java файлов из ветки
    files=$(git ls-tree -r --name-only "$branch" | grep -E "\.(kt|java)$" | grep -v "build/" | grep -v "\.gradle/")
    
    if [ -z "$files" ]; then
        echo "   ℹ️  Нет Kotlin/Java файлов в ветке $branch"
        echo ""
        continue
    fi
    
    branch_copied=0
    branch_updated=0
    
    # Обрабатываем каждый файл
    while IFS= read -r file; do
        # Пропускаем сгенерированные файлы
        if [[ "$file" == *"build/"* ]] || [[ "$file" == *"generated/"* ]]; then
            continue
        fi
        
        # Получаем содержимое файла из ветки
        if git show "$branch:$file" > /tmp/temp_file_from_branch 2>/dev/null; then
            # Определяем путь в текущей ветке
            target_path="$file"
            
            # Копируем файл
            copy_file "/tmp/temp_file_from_branch" "$target_path" "$branch"
            
            if [ -f "$target_path" ]; then
                branch_copied=$((branch_copied + 1))
            fi
        else
            echo "   ⚠️  Не удалось получить файл: $file"
        fi
    done <<< "$files"
    
    echo "   📊 Ветка $branch: скопировано/обновлено файлов: $branch_copied"
    echo ""
done

echo "=== Итоговая статистика ==="
echo "Новых файлов скопировано: $total_copied"
echo "Существующих файлов обновлено: $total_updated"
echo "Файлов пропущено (идентичны): $total_skipped"
echo ""

# Очистка временных файлов
rm -f /tmp/temp_file_from_branch

echo "=== Готово ==="
