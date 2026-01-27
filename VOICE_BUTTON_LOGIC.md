# Логика кнопки записи голосового сообщения

## Расположение кнопки

**Кнопка записи голосового сообщения расположена слева от кнопки отправки сообщения.**

Порядок элементов в `ChatInput`:
1. **TextField** (поле ввода текста) - занимает все доступное пространство
2. **Кнопка микрофона** (Mic/Stop) - всегда видна, слева от кнопки отправки
3. **Отступ** (8dp) - между кнопкой микрофона и кнопкой отправки
4. **Кнопка отправки** (Send) - справа от кнопки микрофона

## Логика отображения кнопки микрофона

### Состояние 1: Не идет запись (`isListening = false`)
- **Отображается:** Иконка микрофона (Mic)
- **Цвет фона:** Primary (синий)
- **Цвет иконки:** OnPrimary (белый)
- **Доступность:** Активна, если не идет загрузка (`!isLoading`)
- **Действие при нажатии:** Запускает запись голоса (`onStartVoiceInput`)

### Состояние 2: Идет запись (`isListening = true`)
- **Отображается:** Иконка остановки (Stop)
- **Цвет фона:** Error (красный)
- **Цвет иконки:** OnError (белый)
- **Доступность:** Всегда активна
- **Действие при нажатии:** Останавливает запись (`onStopVoiceInput`)

### Состояние 3: Идет загрузка ответа (`isLoading = true`)
- **Отображается:** Иконка микрофона (Mic)
- **Цвет фона:** Outline с прозрачностью 0.5 (серый)
- **Цвет иконки:** OnSurface с прозрачностью 0.5 (серый)
- **Доступность:** Отключена (`enabled = false`)
- **Действие при нажатии:** Недоступно

## Код реализации

```kotlin
// Кнопка записи голосового сообщения (слева от кнопки отправки)
if (isListening) {
    // Кнопка остановки записи (во время записи)
    IconButton(
        onClick = onStopVoiceInput,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(micButtonColor) // Красный цвет
    ) {
        Icon(
            imageVector = Icons.Default.Stop,
            contentDescription = "Stop recording",
            tint = MaterialTheme.colorScheme.onError
        )
    }
} else {
    // Кнопка начала записи (когда не записываем)
    IconButton(
        onClick = onStartVoiceInput,
        enabled = !isLoading,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (!isLoading) micButtonColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Start voice input",
            tint = if (!isLoading) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            }
        )
    }
}
```

## Передача параметров из ChatScreen

В `ChatScreen.kt` кнопка микрофона получает следующие параметры:

```kotlin
ChatInput(
    value = state.currentInput,
    onValueChange = { viewModel.onEvent(ChatEvent.OnInputChange(it)) },
    onSend = { viewModel.onEvent(ChatEvent.OnSendMessage) },
    isLoading = state.isLoading,                    // Состояние загрузки
    isListening = state.isListening,                // Состояние записи
    onStartVoiceInput = {                           // Обработчик начала записи
        if (hasRecordAudioPermission) {
            viewModel.onEvent(ChatEvent.OnStartVoiceInput)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    },
    onStopVoiceInput = { viewModel.onEvent(ChatEvent.OnStopVoiceInput) } // Обработчик остановки записи
)
```

## Визуальное представление

```
┌─────────────────────────────────────────────────────────────┐
│  [TextField для ввода текста]  [🎤]  [8dp]  [📤]           │
│                                                              │
│  Где:                                                        │
│  🎤 - Кнопка микрофона (Mic/Stop)                           │
│  📤 - Кнопка отправки (Send)                                │
└─────────────────────────────────────────────────────────────┘
```

## Важные моменты

1. **Кнопка всегда видна** - она не скрывается ни при каких условиях
2. **Расположение фиксировано** - всегда слева от кнопки отправки
3. **Визуальная обратная связь** - цвет меняется в зависимости от состояния
4. **Обработка разрешений** - запрос разрешения на запись аудио при первом использовании
5. **Блокировка во время загрузки** - кнопка отключается, когда идет загрузка ответа от AI
