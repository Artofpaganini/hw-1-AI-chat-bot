package com.example.aiagentchat.core.common

import kotlinx.coroutines.channels.Channel

/**
 * Менеджер для отложенных действий с потокобезопасной обработкой через CONFLATED Channel.
 * 
 * Работает как связующее звено между ViewModel и Helper:
 * - Helper создает менеджер и передает его в ViewModel
 * - Helper откладывает действия через defer() когда пользователь не авторизован
 * - ViewModel слушает Flow авторизации и вызывает takePending() при авторизации
 * - Helper сам решает, что делать с полученным Action
 * 
 * Пример использования:
 * 
 * ```kotlin
 * // В Helper
 * class ClickHelper {
 *     // Создаем менеджер (без executor - логика выполнения в Helper)
 *     val actionManager = DeferredActionManager<ClickAction>()
 *     
 *     suspend fun handleClick(action: ClickAction) {
 *         if (isUserAuthorized()) {
 *             // Выполняем сразу
 *             performClickAction(action)
 *         } else {
 *             // Откладываем действие
 *             actionManager.defer(action)
 *             showAuthDialog()
 *         }
 *     }
 *     
 *     suspend fun executePendingAction() {
 *         // Получаем отложенное действие и выполняем его
 *         actionManager.takePending()?.let { action ->
 *             performClickAction(action)
 *         }
 *     }
 * }
 * 
 * // В ViewModel
 * class SomeViewModel(
 *     private val helper: ClickHelper,
 *     private val authFlow: Flow<Boolean>
 * ) : ViewModel() {
 *     
 *     init {
 *         // Слушаем изменения авторизации
 *         viewModelScope.launch {
 *             authFlow.collect { isAuthorized ->
 *                 if (isAuthorized) {
 *                     // Уведомляем Helper, что можно выполнить отложенное действие
 *                     helper.executePendingAction()
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 * 
 * @param Action тип действия, которое нужно выполнить
 */
class DeferredActionManager<Action> {
    private val actionChannel = Channel<Action>(Channel.CONFLATED)

    /**
     * Откладывает действие. Если уже есть отложенное — перезаписывает его (CONFLATED).
     * 
     * Вызывается из Helper, когда действие не может быть выполнено (например, пользователь не авторизован).
     */
    suspend fun defer(action: Action) {
        actionChannel.send(action)
    }

    /**
     * Извлекает отложенное действие из менеджера.
     * Возвращает действие или null, если действия не было.
     * 
     * Вызывается из Helper при изменении состояния авторизации (когда пользователь авторизован).
     * Helper сам решает, что делать с полученным Action.
     */
    suspend fun takePending(): Action? {
        return actionChannel.receiveCatching().getOrNull()
    }

    /**
     * Проверяет, есть ли отложенное действие, без извлечения.
     * Возвращает true, если действие было поставлено на выполнение.
     * 
     * Вызывается для проверки наличия отложенного действия без его извлечения.
     */
    suspend fun hasPendingAction(): Boolean {
        val result = actionChannel.tryReceive()
        if (result.isSuccess) {
            // Вернем обратно, так как мы только проверяли
            actionChannel.send(result.getOrNull()!!)
            return true
        }
        return false
    }

    /**
     * Очищает отложенное действие.
     * 
     * Вызывается при необходимости отменить отложенное действие (например, при выходе пользователя).
     */
    suspend fun clear() {
        actionChannel.tryReceive()
    }
}

