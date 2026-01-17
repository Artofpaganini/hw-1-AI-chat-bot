#!/bin/bash

# Скрипт для валидации keystore перед добавлением в GitHub Secrets

set -e

KEYSTORE_FILE="${1:-keystore.jks}"
STORE_PASSWORD="${2:-}"
KEY_ALIAS="${3:-release}"
KEY_PASSWORD="${4:-$STORE_PASSWORD}"

echo "=========================================="
echo "Keystore Validation Script"
echo "=========================================="
echo ""

if [ ! -f "$KEYSTORE_FILE" ]; then
    echo "❌ Error: Keystore file not found: $KEYSTORE_FILE"
    exit 1
fi

echo "📁 Keystore file: $KEYSTORE_FILE"
KEYSTORE_SIZE=$(stat -f%z "$KEYSTORE_FILE" 2>/dev/null || stat -c%s "$KEYSTORE_FILE" 2>/dev/null || echo "0")
echo "📊 Size: $KEYSTORE_SIZE bytes"
echo ""

# Проверка валидности keystore
if [ -z "$STORE_PASSWORD" ]; then
    echo "⚠️  Warning: Store password not provided. Cannot validate keystore."
    echo "Usage: $0 <keystore.jks> <store_password> [key_alias] [key_password]"
    echo ""
    echo "Encoding keystore to base64..."
    base64 -i "$KEYSTORE_FILE" | tr -d '\n' > "${KEYSTORE_FILE}.base64"
    BASE64_SIZE=$(stat -f%z "${KEYSTORE_FILE}.base64" 2>/dev/null || stat -c%s "${KEYSTORE_FILE}.base64" 2>/dev/null || echo "0")
    echo "✅ Base64 file created: ${KEYSTORE_FILE}.base64 (size: $BASE64_SIZE bytes)"
    echo "📋 Base64 length: $(cat "${KEYSTORE_FILE}.base64" | wc -c) characters"
    echo ""
    echo "⚠️  To fully validate, run with password:"
    echo "   $0 $KEYSTORE_FILE YOUR_PASSWORD"
    exit 0
fi

echo "🔐 Validating keystore with provided password..."
echo ""

# Проверка, что keystore можно открыть
if ! keytool -list -keystore "$KEYSTORE_FILE" -storepass "$STORE_PASSWORD" > /dev/null 2>&1; then
    echo "❌ Error: Cannot open keystore with provided password!"
    echo "   Please check SIGNING_STORE_PASSWORD"
    exit 1
fi

echo "✅ Keystore can be opened with provided password"
echo ""

# Список всех алиасов
echo "📋 Available aliases in keystore:"
keytool -list -keystore "$KEYSTORE_FILE" -storepass "$STORE_PASSWORD" | grep -E "^[a-zA-Z]" || echo "  (no aliases found)"
echo ""

# Проверка конкретного алиаса
if keytool -list -v -keystore "$KEYSTORE_FILE" -storepass "$STORE_PASSWORD" -alias "$KEY_ALIAS" > /dev/null 2>&1; then
    echo "✅ Key alias '$KEY_ALIAS' found in keystore"
    
    # Проверка пароля ключа (если указан)
    if [ -n "$KEY_PASSWORD" ] && [ "$KEY_PASSWORD" != "$STORE_PASSWORD" ]; then
        echo "🔐 Testing key password..."
        # Попытка экспортировать ключ (проверяет пароль)
        if keytool -exportcert -alias "$KEY_ALIAS" -keystore "$KEYSTORE_FILE" -storepass "$STORE_PASSWORD" -keypass "$KEY_PASSWORD" > /dev/null 2>&1; then
            echo "✅ Key password is correct"
        else
            echo "⚠️  Warning: Key password might be incorrect"
        fi
    fi
else
    echo "❌ Error: Key alias '$KEY_ALIAS' not found in keystore!"
    echo "   Please check SIGNING_KEY_ALIAS"
    echo "   Available aliases are listed above"
    exit 1
fi

echo ""
echo "=========================================="
echo "Encoding keystore to base64..."
echo "=========================================="

# Кодирование в base64
base64 -i "$KEYSTORE_FILE" | tr -d '\n' | tr -d '\r' > "${KEYSTORE_FILE}.base64"
BASE64_SIZE=$(stat -f%z "${KEYSTORE_FILE}.base64" 2>/dev/null || stat -c%s "${KEYSTORE_FILE}.base64" 2>/dev/null || echo "0")
BASE64_LENGTH=$(cat "${KEYSTORE_FILE}.base64" | wc -c)

echo "✅ Base64 file created: ${KEYSTORE_FILE}.base64"
echo "📊 Base64 size: $BASE64_SIZE bytes"
echo "📏 Base64 length: $BASE64_LENGTH characters"
echo ""

# Проверка, что base64 одной строкой
LINES=$(wc -l < "${KEYSTORE_FILE}.base64" | tr -d ' ')
if [ "$LINES" -eq 1 ]; then
    echo "✅ Base64 is a single line (correct)"
else
    echo "⚠️  Warning: Base64 has $LINES lines (should be 1)"
fi

echo ""
echo "=========================================="
echo "Testing base64 decoding..."
echo "=========================================="

# Тест декодирования
cat "${KEYSTORE_FILE}.base64" | base64 -d > "${KEYSTORE_FILE}.test" 2>&1
DECODE_EXIT=$?

if [ $DECODE_EXIT -ne 0 ]; then
    echo "❌ Error: Failed to decode base64!"
    rm -f "${KEYSTORE_FILE}.test"
    exit 1
fi

TEST_SIZE=$(stat -f%z "${KEYSTORE_FILE}.test" 2>/dev/null || stat -c%s "${KEYSTORE_FILE}.test" 2>/dev/null || echo "0")

if [ "$TEST_SIZE" -eq "$KEYSTORE_SIZE" ]; then
    echo "✅ Decoded keystore size matches original ($TEST_SIZE bytes)"
else
    echo "❌ Error: Decoded keystore size mismatch!"
    echo "   Original: $KEYSTORE_SIZE bytes"
    echo "   Decoded:  $TEST_SIZE bytes"
    rm -f "${KEYSTORE_FILE}.test"
    exit 1
fi

# Проверка валидности декодированного keystore
if keytool -list -keystore "${KEYSTORE_FILE}.test" -storepass "$STORE_PASSWORD" > /dev/null 2>&1; then
    echo "✅ Decoded keystore is valid and can be opened"
else
    echo "❌ Error: Decoded keystore is corrupted!"
    rm -f "${KEYSTORE_FILE}.test"
    exit 1
fi

rm -f "${KEYSTORE_FILE}.test"

echo ""
echo "=========================================="
echo "✅ All checks passed!"
echo "=========================================="
echo ""
echo "📋 Next steps:"
echo "1. Copy the base64 content from: ${KEYSTORE_FILE}.base64"
echo "2. Go to GitHub: Settings > Secrets and variables > Actions"
echo "3. Create/update secret KEYSTORE_BASE64 with the base64 content"
echo "4. Make sure these secrets are set:"
echo "   - SIGNING_STORE_PASSWORD: $STORE_PASSWORD"
echo "   - SIGNING_KEY_ALIAS: $KEY_ALIAS"
echo "   - SIGNING_KEY_PASSWORD: $KEY_PASSWORD"
echo ""
echo "To copy base64:"
echo "   cat ${KEYSTORE_FILE}.base64 | pbcopy  # Mac"
echo "   cat ${KEYSTORE_FILE}.base64 | xclip -selection clipboard  # Linux"
