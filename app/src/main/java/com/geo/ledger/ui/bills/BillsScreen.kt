package com.geo.ledger.ui.bills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.geo.ledger.GeoApplication
import com.geo.ledger.R
import com.geo.ledger.data.local.TransactionEntity
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.BillsPeriodMode
import com.geo.ledger.domain.DateRange
import com.geo.ledger.domain.LedgerEntry
import com.geo.ledger.domain.PeriodSummary
import com.geo.ledger.domain.TransactionDisplay
import com.geo.ledger.ui.GeoViewModelFactory
import com.geo.ledger.ui.theme.GeoCard
import com.geo.ledger.ui.theme.GeoExpense
import com.geo.ledger.ui.theme.GeoIncome
import com.geo.ledger.ui.theme.GeoRangeHighlight
import com.geo.ledger.util.GeoDates
import com.geo.ledger.util.MoneyFormatter
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    onOpenDetail: (Long) -> Unit,
    viewModel: BillsViewModel = composeViewModel(
        factory = GeoViewModelFactory(
            (LocalContext.current.applicationContext as GeoApplication).repository,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRangePicker by remember { mutableStateOf(false) }
    val expenseFallback = stringResource(R.string.expense)
    val incomeFallback = stringResource(R.string.income)
    val modes = listOf(
        BillsPeriodMode.DAY to R.string.filter_day,
        BillsPeriodMode.MONTH to R.string.filter_month,
        BillsPeriodMode.YEAR to R.string.filter_year,
        BillsPeriodMode.CUSTOM to R.string.filter_custom,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.bills),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (mode, labelRes) ->
                    SegmentedButton(
                        selected = uiState.mode == mode,
                        onClick = {
                            if (mode == BillsPeriodMode.CUSTOM) {
                                showRangePicker = true
                            } else {
                                viewModel.setMode(mode)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        label = {
                            Text(
                                text = stringResource(labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            PeriodNavigator(
                label = uiState.periodLabel,
                canShift = uiState.canShift,
                onShift = viewModel::shift,
                onLabelClick = {
                    if (uiState.mode == BillsPeriodMode.CUSTOM) {
                        showRangePicker = true
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            if (!uiState.isReady) {
                val loading = stringResource(R.string.content_loading)
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = loading },
                )
            } else if (uiState.invalidCustomRange) {
                Text(
                    text = stringResource(R.string.error_invalid_range),
                    style = MaterialTheme.typography.bodyLarge,
                    color = GeoExpense,
                )
            } else if (uiState.ledgerError) {
                Text(
                    text = stringResource(R.string.error_ledger_corrupt),
                    style = MaterialTheme.typography.bodyLarge,
                    color = GeoExpense,
                )
            } else {
                PeriodSummarySection(summary = uiState.summary)
            }
        }
        if (uiState.isReady && !uiState.ledgerError && !uiState.invalidCustomRange && uiState.groups.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.empty_period_bills),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        } else if (uiState.isReady && !uiState.ledgerError && !uiState.invalidCustomRange) {
            uiState.groups.forEach { group ->
                item(key = "group-${group.date}") {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(
                    items = group.entries,
                    key = { _, entry -> entry.transaction.id },
                ) { index, entry ->
                    Column {
                        if (index > 0) HorizontalDivider()
                        BillsTransactionRow(
                            entry = entry,
                            expenseFallback = expenseFallback,
                            incomeFallback = incomeFallback,
                            onClick = { onOpenDetail(entry.transaction.id) },
                        )
                    }
                }
            }
        }
    }

    if (showRangePicker) {
        CustomRangePickerDialog(
            range = uiState.range ?: DateRange(LocalDate.now(), LocalDate.now()),
            onDismiss = { showRangePicker = false },
            onConfirm = { start, end ->
                if (viewModel.setCustomRange(start, end)) {
                    showRangePicker = false
                }
            },
        )
    }
}

@Composable
private fun PeriodNavigator(
    label: String,
    canShift: Boolean,
    onShift: (Long) -> Unit,
    onLabelClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onShift(-1L) },
            enabled = canShift,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_period),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .then(if (canShift) Modifier else Modifier.clickable(onClick = onLabelClick)),
        )
        IconButton(
            onClick = { onShift(1L) },
            enabled = canShift,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_period),
            )
        }
    }
}

@Composable
private fun PeriodSummarySection(summary: PeriodSummary) {
    val netColor = when {
        summary.netChangeCents > 0L -> GeoIncome
        summary.netChangeCents < 0L -> GeoExpense
        else -> MaterialTheme.colorScheme.onBackground
    }
    val endingColor = if (summary.endingBalanceCents < 0L) {
        GeoExpense
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(
                label = stringResource(R.string.period_income),
                amount = MoneyFormatter.unsigned(summary.incomeCents),
                amountColor = GeoIncome,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = stringResource(R.string.period_expense),
                amount = MoneyFormatter.unsigned(summary.expenseCents),
                amountColor = GeoExpense,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(
                label = stringResource(R.string.net_change),
                amount = MoneyFormatter.signed(summary.netChangeCents),
                amountColor = netColor,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = stringResource(R.string.ending_balance),
                amount = MoneyFormatter.plain(summary.endingBalanceCents),
                amountColor = endingColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    amount: String,
    amountColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = GeoCard),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = amountColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BillsTransactionRow(
    entry: LedgerEntry,
    expenseFallback: String,
    incomeFallback: String,
    onClick: () -> Unit,
) {
    val transaction = entry.transaction
    val title = TransactionDisplay.title(
        type = transaction.type,
        categorySnapshot = transaction.expenseCategorySnapshot,
        incomeSource = transaction.incomeSource,
        note = transaction.note,
        expenseFallback = expenseFallback,
        incomeFallback = incomeFallback,
    )
    val recordedAt = GeoDates.formatRecordedAt(transaction.transactionDate, transaction.createdAtMillis)
    val details = listOfNotNull(recordedAt.takeIf { it.isNotEmpty() }, transactionFieldLine(transaction))
        .joinToString(" · ")
        .ifEmpty { null }
    val amountColor = when (transaction.type) {
        TransactionType.INCOME -> GeoIncome
        TransactionType.EXPENSE -> GeoExpense
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (details != null) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = MoneyFormatter.transaction(transaction.type, transaction.amountCents),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = amountColor,
            )
            Text(
                text = stringResource(R.string.balance_after, MoneyFormatter.plain(entry.balanceAfterCents)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangePickerDialog(
    range: DateRange,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val pickerHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).coerceIn(240f, 400f).dp
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = GeoDates.toPickerMillisOrNull(range.start),
        initialSelectedEndDateMillis = GeoDates.toPickerMillisOrNull(range.endInclusive),
    )
    val startMillis = pickerState.selectedStartDateMillis
    val endMillis = pickerState.selectedEndDateMillis
    val start = startMillis?.let(GeoDates::fromPickerMillisOrNull)
    val end = endMillis?.let(GeoDates::fromPickerMillisOrNull)
    val confirmedRange = if (start != null && end != null) {
        com.geo.ledger.domain.CustomRangeSelection.confirmRange(start, end)
    } else {
        null
    }
    val inverted = start != null && end != null && confirmedRange == null
    val confirmEnabled = confirmedRange != null
    val confirmLabel = if (confirmedRange != null) {
        stringResource(R.string.view_range, GeoDates.formatRange(confirmedRange))
    } else {
        stringResource(R.string.confirm)
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = confirmEnabled,
                onClick = {
                    val range = confirmedRange ?: return@TextButton
                    onConfirm(range.start, range.endInclusive)
                },
            ) {
                Text(
                    text = confirmLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    ) {
        DateRangePicker(
            state = pickerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(pickerHeight)
                .heightIn(max = pickerHeight),
            colors = DatePickerDefaults.colors(
                dayInSelectionRangeContainerColor = GeoRangeHighlight,
            ),
            title = {
                Text(
                    text = stringResource(R.string.select_date_range),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            },
            headline = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.start_date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = start?.let(GeoDates::formatCompact).orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = stringResource(R.string.end_date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = end?.let(GeoDates::formatCompact).orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (inverted) {
                    Text(
                        text = stringResource(R.string.error_invalid_range),
                        style = MaterialTheme.typography.bodySmall,
                        color = GeoExpense,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
                    )
                }
            },
            showModeToggle = false,
        )
    }
}

private fun transactionFieldLine(transaction: TransactionEntity): String? {
    fun present(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
    val parts = when (transaction.type) {
        TransactionType.EXPENSE -> listOfNotNull(
            present(transaction.expensePersonSnapshot),
            present(transaction.expenseCategorySnapshot),
            present(transaction.note),
        )
        TransactionType.INCOME -> listOfNotNull(
            present(transaction.incomeSource),
            present(transaction.note),
        )
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
