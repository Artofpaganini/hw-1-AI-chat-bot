#!/bin/bash
# Автоматическое объединение всех веток в hw-32

set -e

CURRENT_BRANCH="hw-32"
BRANCHES="hw-17 hw-18 hw-19 hw-20 hw-21 hw-22 hw-23 hw-24 hw-25 hw-26 hw-27 hw-28 hw-29"

echo "=== Объединение всех веток в $CURRENT_BRANCH ==="
echo ""

# Создаем временную директорию для отслеживания скопированных файлов
TEMP_DIR=$(mktemp -d)
echo "$TEMP_DIR" > "$TEMP_DIR/.temp_dir"

for branch in $BRANCHES; do
    echo "=== Обработка ветки $branch ==="
    
    # Получаем список всех Kotlin/Java файлов из ветки
    git ls-tree -r --name-only $branch | grep -E "\.(kt|java)$" | grep -v "build\|test\|\.idea" | while read file; do
        # Пропускаем файлы из src/ (старая структура)
        if [[ "$file" == src/* ]]; then
            continue
        fi
        
        # Проверяем, существует ли файл в текущей ветке
        if [ ! -f "$file" ]; then
            echo "  [NEW] Копирую новый файл: $file"
            mkdir -p "$(dirname "$file")"
            if git show $branch:"$file" > "$file" 2>/dev/null; then
                echo "$file" >> "$TEMP_DIR/copied_files.txt"
            else
                echo "    [ERROR] Не удалось скопировать $file"
            fi
        fi
    done
    echo ""
done

echo "=== Объединение завершено ==="
echo "Скопированные файлы сохранены в: $TEMP_DIR/copied_files.txt"
echo ""
echo "Следующие шаги:"
echo "1. Проверьте конфликты в существующих файлах"
echo "2. Обновите ChatDatabase.kt с новыми entity"
echo "3. Обновите AppModule.kt с новыми зависимостями"
echo "4. Обновите ChatViewModel.kt с новым функционалом"
echo "5. Проверьте компиляцию: ./gradlew assembleDebug"
