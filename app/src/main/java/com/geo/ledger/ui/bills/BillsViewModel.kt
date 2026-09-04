package com.geo.ledger.ui.bills

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.domain.BillsPeriodMode
import com.geo.ledger.domain.BillsQuery
import com.geo.ledger.domain.DateGroup
import com.geo.ledger.domain.DateRange
import com.geo.ledger.domain.LedgerCalculator
import com.geo.ledger.domain.LedgerObservation
import com.geo.ledger.domain.PeriodSummary
import com.geo.ledger.util.GeoDates
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

data class BillsUiState(
    val mode: BillsPeriodMode,
    val range: DateRange?,
    val periodLabel: String,
    val canShift: Boolean,
    val summary: PeriodSummary,
    val groups: List<DateGroup>,
    val isReady: Boolean = false,
    val invalidCustomRange: Boolean = false,
    val ledgerError: Boolean = false,
)

private data class BillsPeriodKeys(
    val mode: BillsPeriodMode,
    val day: LocalDate,
    val month: YearMonth,
    val year: Int,
    val customStart: LocalDate,
)

class BillsViewModel(
    ledgerObservation: Flow<LedgerObservation>,
    private val savedStateHandle: SavedStateHandle,
    today: () -> LocalDate = { LocalDate.now() },
    private val computation: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    constructor(
        repository: LedgerRepository,
        savedStateHandle: SavedStateHandle,
        today: () -> LocalDate = { LocalDate.now() },
    ) : this(repository.ledgerObservation, savedStateHandle, today)

    private val initial = today()
    private val initialMonth = YearMonth.from(initial)

    val uiState: StateFlow<BillsUiState> = combine(
        combine(
            savedStateHandle.getStateFlow(KEY_MODE, BillsPeriodMode.MONTH.name),
            savedStateHandle.getStateFlow(KEY_DAY, initial.toEpochDay()),
            savedStateHandle.getStateFlow(KEY_MONTH, initialMonth.toString()),
            savedStateHandle.getStateFlow(KEY_YEAR, initial.year),
            savedStateHandle.getStateFlow(KEY_CUSTOM_START, initialMonth.atDay(1).toEpochDay()),
        ) { modeName, dayEpoch, monthText, year, customStartEpoch ->
            BillsPeriodKeys(
                mode = parseMode(modeName),
                day = epochDayOr(dayEpoch, initial),
                month = yearMonthOr(monthText, initialMonth),
                year = yearOr(year, initial.year),
                customStart = epochDayOr(customStartEpoch, initialMonth.atDay(1)),
            )
        },
        savedStateHandle.getStateFlow(KEY_CUSTOM_END, initialMonth.atEndOfMonth().toEpochDay()),
        ledgerObservation,
    ) { keys, customEndEpoch, observation ->
        val customEnd = epochDayOr(customEndEpoch, initialMonth.atEndOfMonth())
        val resolved = BillsQuery.range(
            keys.mode,
            keys.day,
            keys.month,
            keys.year,
            keys.customStart,
            customEnd,
        )
        val invalidCustom = keys.mode == BillsPeriodMode.CUSTOM && resolved == null
        val range = resolved
        val periodLabel = if (invalidCustom) {
            "${GeoDates.formatCompact(keys.customStart)} — ${GeoDates.formatCompact(customEnd)}"
        } else {
            BillsQuery.periodLabel(
                keys.mode,
                keys.day,
                keys.month,
                keys.year,
                range ?: GeoDates.month(keys.month),
            )
        }
        when (observation) {
            is LedgerObservation.Invalid -> errorState(keys, range, periodLabel, invalidCustom)
            is LedgerObservation.Ready -> try {
                if (invalidCustom || range == null) {
                    BillsUiState(
                        mode = keys.mode,
                        range = null,
                        periodLabel = periodLabel,
                        canShift = false,
                        summary = PeriodSummary(0, 0, 0, 0),
                        groups = emptyList(),
                        isReady = true,
                        invalidCustomRange = true,
                        ledgerError = false,
                    )
                } else {
                    val (summary, groups) = withContext(computation) {
                        LedgerCalculator.summary(observation.entries, range) to
                            BillsQuery.groups(observation.entries, range)
                    }
                    BillsUiState(
                        mode = keys.mode,
                        range = range,
                        periodLabel = periodLabel,
                        canShift = keys.mode != BillsPeriodMode.CUSTOM,
                        summary = summary,
                        groups = groups,
                        isReady = true,
                        invalidCustomRange = false,
                        ledgerError = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                errorState(keys, range, periodLabel, invalidCustom)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = initialState(),
    )

    fun setMode(mode: BillsPeriodMode) {
        if (mode == BillsPeriodMode.CUSTOM && currentMode() != BillsPeriodMode.CUSTOM) {
            val currentRange = BillsQuery.range(
                currentMode(),
                currentDay(),
                currentMonth(),
                currentYear(),
                currentCustomStart(),
                currentCustomEnd(),
            ) ?: GeoDates.month(currentMonth())
            savedStateHandle[KEY_CUSTOM_START] = currentRange.start.toEpochDay()
            savedStateHandle[KEY_CUSTOM_END] = currentRange.endInclusive.toEpochDay()
        }
        savedStateHandle[KEY_MODE] = mode.name
    }

    fun shift(delta: Long) {
        when (currentMode()) {
            BillsPeriodMode.DAY ->
                savedStateHandle[KEY_DAY] = currentDay().plusDays(delta).toEpochDay()
            BillsPeriodMode.MONTH ->
                savedStateHandle[KEY_MONTH] = currentMonth().plusMonths(delta).toString()
            BillsPeriodMode.YEAR ->
                savedStateHandle[KEY_YEAR] = currentYear() + delta.toInt()
            BillsPeriodMode.CUSTOM -> Unit
        }
    }

    fun setCustomRange(start: LocalDate, endInclusive: LocalDate): Boolean {
        val range = BillsQuery.customRange(start, endInclusive) ?: return false
        savedStateHandle[KEY_CUSTOM_START] = range.start.toEpochDay()
        savedStateHandle[KEY_CUSTOM_END] = range.endInclusive.toEpochDay()
        savedStateHandle[KEY_MODE] = BillsPeriodMode.CUSTOM.name
        return true
    }

    private fun currentMode(): BillsPeriodMode =
        parseMode(savedStateHandle[KEY_MODE] ?: BillsPeriodMode.MONTH.name)

    private fun currentDay(): LocalDate =
        epochDayOr(savedStateHandle[KEY_DAY] ?: initial.toEpochDay(), initial)

    private fun currentMonth(): YearMonth =
        yearMonthOr(savedStateHandle.get<String>(KEY_MONTH), initialMonth)

    private fun currentYear(): Int = yearOr(savedStateHandle[KEY_YEAR], initial.year)

    private fun currentCustomStart(): LocalDate =
        epochDayOr(savedStateHandle[KEY_CUSTOM_START] ?: initialMonth.atDay(1).toEpochDay(), initialMonth.atDay(1))

    private fun currentCustomEnd(): LocalDate =
        epochDayOr(
            savedStateHandle[KEY_CUSTOM_END] ?: initialMonth.atEndOfMonth().toEpochDay(),
            initialMonth.atEndOfMonth(),
        )

    private fun initialState(): BillsUiState {
        val range = GeoDates.month(initialMonth)
        return BillsUiState(
            mode = BillsPeriodMode.MONTH,
            range = range,
            periodLabel = BillsQuery.periodLabel(BillsPeriodMode.MONTH, initial, initialMonth, initial.year, range),
            canShift = true,
            summary = PeriodSummary(0, 0, 0, 0),
            groups = emptyList(),
            isReady = false,
            ledgerError = false,
        )
    }

    private fun errorState(
        keys: BillsPeriodKeys,
        range: DateRange?,
        periodLabel: String,
        invalidCustom: Boolean,
    ) = BillsUiState(
        mode = keys.mode,
        range = range,
        periodLabel = periodLabel,
        canShift = keys.mode != BillsPeriodMode.CUSTOM && !invalidCustom,
        summary = PeriodSummary(0, 0, 0, 0),
        groups = emptyList(),
        isReady = true,
        invalidCustomRange = invalidCustom,
        ledgerError = true,
    )

    private fun parseMode(modeName: String): BillsPeriodMode =
        runCatching { BillsPeriodMode.valueOf(modeName) }.getOrDefault(BillsPeriodMode.MONTH)

    private fun epochDayOr(value: Long, fallback: LocalDate): LocalDate =
        try {
            LocalDate.ofEpochDay(value)
        } catch (_: DateTimeException) {
            fallback
        }

    private fun yearMonthOr(value: String?, fallback: YearMonth): YearMonth =
        try {
            value?.let(YearMonth::parse) ?: fallback
        } catch (_: DateTimeParseException) {
            fallback
        }

    private fun yearOr(value: Int?, fallback: Int): Int =
        value?.takeIf { it in 1..9_999 } ?: fallback

    internal companion object {
        const val KEY_MODE = "bills_mode"
        const val KEY_DAY = "bills_day"
        const val KEY_MONTH = "bills_month"
        const val KEY_YEAR = "bills_year"
        const val KEY_CUSTOM_START = "bills_custom_start"
        const val KEY_CUSTOM_END = "bills_custom_end"
    }
}
