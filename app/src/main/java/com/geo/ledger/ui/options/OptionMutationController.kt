package com.geo.ledger.ui.options

import com.geo.ledger.R
import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.domain.OptionNameValidator
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ManageOptionEvent {
    data object Saved : ManageOptionEvent
    data class Failed(val messageRes: Int) : ManageOptionEvent
}

internal class OptionMutationController(
    private val scope: CoroutineScope,
    private val currentActive: () -> List<Pair<Long, String>>,
    private val addOption: suspend (String) -> Long,
    private val renameOption: suspend (Long, String) -> Unit,
    private val deactivateOption: suspend (Long) -> Unit,
) {
    private val mutex = Mutex()
    private val inFlight = AtomicBoolean(false)
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()
    private val eventsChannel = Channel<ManageOptionEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    fun add(name: String) = launchMutation { active ->
        when (
            val result = OptionNameValidator.validate(
                raw = name,
                activeItems = active,
                maxLength = LedgerRepository.MAX_OPTION_NAME_LENGTH,
            )
        ) {
            is OptionNameValidator.Result.Invalid ->
                eventsChannel.send(ManageOptionEvent.Failed(result.reason.toMessageRes()))
            is OptionNameValidator.Result.Valid -> {
                addOption(result.normalized)
                eventsChannel.send(ManageOptionEvent.Saved)
            }
        }
    }

    fun rename(id: Long, name: String) = launchMutation { active ->
        when (
            val result = OptionNameValidator.validate(
                raw = name,
                activeItems = active,
                excludeId = id,
                maxLength = LedgerRepository.MAX_OPTION_NAME_LENGTH,
            )
        ) {
            is OptionNameValidator.Result.Invalid ->
                eventsChannel.send(ManageOptionEvent.Failed(result.reason.toMessageRes()))
            is OptionNameValidator.Result.Valid -> {
                renameOption(id, result.normalized)
                eventsChannel.send(ManageOptionEvent.Saved)
            }
        }
    }

    fun delete(id: Long) = launchMutation {
        deactivateOption(id)
        eventsChannel.send(ManageOptionEvent.Saved)
    }

    private fun launchMutation(block: suspend (List<Pair<Long, String>>) -> Unit) {
        if (!inFlight.compareAndSet(false, true)) return
        scope.launch {
            mutex.withLock {
                try {
                    _isBusy.value = true
                    block(currentActive())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: IllegalArgumentException) {
                    eventsChannel.send(ManageOptionEvent.Failed(mapRepositoryError(error)))
                } catch (_: Exception) {
                    eventsChannel.send(ManageOptionEvent.Failed(R.string.error_save_failed))
                } finally {
                    _isBusy.value = false
                    inFlight.set(false)
                }
            }
        }
    }
}

internal fun OptionNameValidator.Reason.toMessageRes(): Int = when (this) {
    OptionNameValidator.Reason.Blank -> R.string.error_blank_name
    OptionNameValidator.Reason.TooLong -> R.string.error_name_too_long
    OptionNameValidator.Reason.Duplicate -> R.string.error_duplicate_name
}

private fun mapRepositoryError(error: IllegalArgumentException): Int {
    val message = error.message.orEmpty()
    return when {
        message.contains("already exists", ignoreCase = true) -> R.string.error_duplicate_name
        message.contains("blank", ignoreCase = true) -> R.string.error_blank_name
        message.contains("too long", ignoreCase = true) -> R.string.error_name_too_long
        else -> R.string.error_save_failed
    }
}
