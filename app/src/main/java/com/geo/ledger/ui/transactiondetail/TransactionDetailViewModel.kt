package com.geo.ledger.ui.transactiondetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geo.ledger.R
import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.domain.LedgerEntry
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TransactionDetailViewModel(
    private val repository: LedgerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val transactionId: Long = savedStateHandle.get<Long>(ARG_TRANSACTION_ID) ?: INVALID_ID
    private val deleteMutex = Mutex()
    private val deleteInFlight = AtomicBoolean(false)
    private val _isDeleting = MutableStateFlow(false)
    private val _deleted = MutableStateFlow(false)
    private val _errorRes = MutableStateFlow<Int?>(null)
    private val eventsChannel = Channel<TransactionDetailEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<TransactionDetailUiState> = combine(
        observedTransaction(),
        _isDeleting,
        _deleted,
        _errorRes,
    ) { observed, isDeleting, deleted, errorRes ->
        TransactionDetailUiMapper.map(
            transactionId = transactionId,
            loaded = observed.loaded,
            entry = observed.entry,
            isDeleting = isDeleting,
            deleted = deleted,
            errorRes = errorRes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionDetailUiMapper.map(
            transactionId = transactionId,
            loaded = false,
            entry = null,
            isDeleting = false,
            deleted = false,
            errorRes = null,
        ),
    )

    fun delete() {
        val snapshot = uiState.value
        if (!TransactionDetailDeleteGuard.canAttempt(transactionId, snapshot, deleteInFlight.get())) {
            return
        }
        if (!deleteInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            deleteMutex.withLock {
                try {
                    _errorRes.value = null
                    _isDeleting.value = true
                    repository.deleteTransaction(transactionId)
                    _deleted.value = true
                    eventsChannel.send(TransactionDetailEvent.Deleted)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    val messageRes = R.string.error_delete_failed
                    _errorRes.value = messageRes
                    _isDeleting.value = false
                    deleteInFlight.set(false)
                    eventsChannel.send(TransactionDetailEvent.Failed(messageRes))
                }
            }
        }
    }

    private fun observedTransaction(): Flow<ObservedTransaction> {
        if (transactionId <= 0L) {
            return flowOf(ObservedTransaction(entry = null, loaded = true))
        }
        return repository.observeTransaction(transactionId)
            .map { entry -> ObservedTransaction(entry = entry, loaded = true) }
            .onStart { emit(ObservedTransaction(entry = null, loaded = false)) }
    }

    private data class ObservedTransaction(
        val entry: LedgerEntry?,
        val loaded: Boolean,
    )

    companion object {
        const val ARG_TRANSACTION_ID = "id"
        const val INVALID_ID = 0L
    }
}
