#!/bin/bash
# Скрипт для объединения версионных .md файлов в единые файлы

set -e

echo "=== Объединение версионных .md файлов ==="
echo ""

# Находим все файлы с версиями (например, *_19.md, *_20.md)
VERSIONED_FILES=$(find . -maxdepth 1 -name "*_[0-9]*.md" -type f | grep -E "_\d+\.md$" | sort)

# Извлекаем уникальные базовые имена
BASE_NAMES=$(echo "$VERSIONED_FILES" | sed 's/_[0-9]*\.md$//' | sort -u)

merged_count=0
deleted_count=0

for base_name in $BASE_NAMES; do
    # Находим все версии этого файла
    base_pattern=$(basename "$base_name")
    files=$(find . -maxdepth 1 -name "${base_pattern}_*.md" -type f | grep -E "${base_pattern}_[0-9]+\.md$" | sort)
    
    if [ -z "$files" ]; then
        continue
    fi
    
    # Преобразуем в массив
    file_array=()
    while IFS= read -r line; do
        [ -n "$line" ] && file_array+=("$line")
    done <<< "$files"
    
    if [ ${#file_array[@]} -eq 0 ]; then
        continue
    fi
    
    # Определяем имя объединенного файла
    target_file="${base_name}.md"
    
    # Если файл уже существует, проверяем идентичность и удаляем версии
    if [ -f "$target_file" ]; then
        echo "📝 Файл $(basename "$target_file") уже существует, проверяем версии"
        
        # Проверяем, идентичны ли версии основному файлу
        all_identical=true
        for file in "${file_array[@]}"; do
            if ! diff -q "$target_file" "$file" > /dev/null 2>&1; then
                all_identical=false
                break
            fi
        done
        
        if [ "$all_identical" = true ]; then
            echo "   ✅ Все версии идентичны основному файлу, удаляем версии"
            for file in "${file_array[@]}"; do
                rm "$file"
                deleted_count=$((deleted_count + 1))
                echo "   🗑️  Удален: $(basename "$file")"
            done
            merged_count=$((merged_count + 1))
        else
            echo "   ⚠️  Версии отличаются от основного файла, оставляем как есть"
        fi
        echo ""
        continue
    fi
    
    echo "📝 Объединение файлов для: $(basename "$base_name")"
    echo "   Файлы: ${file_array[*]}"
    
    # Сравниваем все файлы - если они идентичны, берем первый
    all_same=true
    first_file="${file_array[0]}"
    
    for file in "${file_array[@]}"; do
        if ! diff -q "$first_file" "$file" > /dev/null 2>&1; then
            all_same=false
            break
        fi
    done
    
    if [ "$all_same" = true ]; then
        echo "   ✅ Все версии идентичны, используем первую версию"
        cp "$first_file" "$target_file"
        
        # Удаляем версионные файлы
        for file in "${file_array[@]}"; do
            rm "$file"
            deleted_count=$((deleted_count + 1))
            echo "   🗑️  Удален: $(basename "$file")"
        done
    else
        echo "   ⚠️  Файлы различаются, объединяем содержимое"
        
        # Собираем список версий
        versions=""
        for file in "${file_array[@]}"; do
            version=$(echo "$file" | grep -oE '_[0-9]+\.md$' | grep -oE '[0-9]+')
            if [ -z "$versions" ]; then
                versions="hw-$version"
            else
                versions="$versions, hw-$version"
            fi
        done
        
        # Объединяем содержимое, удаляя дубликаты
        {
            echo "# $(basename "$base_name" | tr '_' ' ')"
            echo ""
            echo "## Объединенная версия из веток: $versions"
            echo ""
            echo "---"
            echo ""
            
            # Берем содержимое первого файла
            cat "$first_file"
        } > "$target_file"
        
        # Удаляем версионные файлы
        for file in "${file_array[@]}"; do
            rm "$file"
            deleted_count=$((deleted_count + 1))
            echo "   🗑️  Удален: $(basename "$file")"
        done
    fi
    
    merged_count=$((merged_count + 1))
    echo ""
done

echo "=== Готово ==="
echo "Объединено групп: $merged_count"
echo "Удалено версионных файлов: $deleted_count"
