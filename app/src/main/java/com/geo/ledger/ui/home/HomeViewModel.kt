package com.geo.ledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.domain.HomeDashboard
import com.geo.ledger.domain.HomeSnapshot
import com.geo.ledger.domain.LedgerObservation
import com.geo.ledger.util.GeoDates
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val dateLabel: String,
    val snapshot: HomeSnapshot,
    val isReady: Boolean,
    val ledgerError: Boolean,
)

class HomeViewModel(
    ledgerObservation: Flow<LedgerObservation>,
    private val today: () -> LocalDate = { LocalDate.now() },
    tickCalendar: Boolean = true,
) : ViewModel() {
    constructor(
        repository: LedgerRepository,
        today: () -> LocalDate = { LocalDate.now() },
    ) : this(repository.ledgerObservation, today, tickCalendar = true)

    private val todayState = MutableStateFlow(today())

    val uiState: StateFlow<HomeUiState> = combine(ledgerObservation, todayState) { observation, day ->
        when (observation) {
            is LedgerObservation.Invalid -> errorState(day)
            is LedgerObservation.Ready -> try {
                HomeUiState(
                    dateLabel = GeoDates.formatDate(day),
                    snapshot = HomeDashboard.from(observation.entries, day),
                    isReady = true,
                    ledgerError = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                errorState(day)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(
            dateLabel = GeoDates.formatDate(today()),
            snapshot = HomeDashboard.from(emptyList(), today()),
            isReady = false,
            ledgerError = false,
        ),
    )

    init {
        if (tickCalendar) {
            viewModelScope.launch {
                while (isActive) {
                    todayState.value = today()
                    delay(millisUntilNextLocalMidnight())
                }
            }
        }
    }

    internal fun refreshCalendarDay() {
        todayState.value = today()
    }

    private fun errorState(day: LocalDate) = HomeUiState(
        dateLabel = GeoDates.formatDate(day),
        snapshot = HomeDashboard.from(emptyList(), day),
        isReady = true,
        ledgerError = true,
    )

    private fun millisUntilNextLocalMidnight(): Long {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return java.time.Duration.between(now, next).toMillis().coerceAtLeast(1L)
    }
}
