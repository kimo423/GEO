package com.geo.ledger.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.geo.ledger.GeoApplication
import com.geo.ledger.R
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.LedgerEntry
import com.geo.ledger.domain.TransactionDisplay
import com.geo.ledger.ui.GeoViewModelFactory
import com.geo.ledger.ui.theme.GeoCard
import com.geo.ledger.ui.theme.GeoExpense
import com.geo.ledger.ui.theme.GeoIncome
import com.geo.ledger.util.GeoDates
import com.geo.ledger.util.MoneyFormatter

@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onViewAll: () -> Unit,
    viewModel: HomeViewModel = composeViewModel(
        factory = GeoViewModelFactory(
            (LocalContext.current.applicationContext as GeoApplication).repository,
        ),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val expenseFallback = stringResource(R.string.expense)
    val incomeFallback = stringResource(R.string.income)
    val snapshot = state.snapshot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = state.dateLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )
        if (state.ledgerError) {
            Text(
                text = stringResource(R.string.error_ledger_corrupt),
                style = MaterialTheme.typography.bodyLarge,
                color = GeoExpense,
            )
        } else if (!state.isReady) {
            val loading = stringResource(R.string.content_loading)
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .semantics { contentDescription = loading },
            )
        } else if (state.isReady) {
            Text(
                text = stringResource(R.string.current_balance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = MoneyFormatter.plain(snapshot.currentBalanceCents),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (snapshot.currentBalanceCents < 0) GeoExpense else MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryChip(
                    label = stringResource(R.string.month_income),
                    amount = MoneyFormatter.unsigned(snapshot.monthIncomeCents),
                    amountColor = GeoIncome,
                    modifier = Modifier.weight(1f),
                )
                SummaryChip(
                    label = stringResource(R.string.month_expense),
                    amount = MoneyFormatter.unsigned(snapshot.monthExpenseCents),
                    amountColor = GeoExpense,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.recent_bills),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onViewAll) {
                    Text(stringResource(R.string.view_all))
                }
            }
            if (snapshot.recent.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_bills_title),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = stringResource(R.string.empty_bills_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                snapshot.recent.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider()
                    RecentTransactionRow(
                        entry = entry,
                        expenseFallback = expenseFallback,
                        incomeFallback = incomeFallback,
                        onClick = { onOpenDetail(entry.transaction.id) },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAddTransaction,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_transaction_plus))
        }
    }
}

@Composable
private fun SummaryChip(
    label: String,
    amount: String,
    amountColor: androidx.compose.ui.graphics.Color,
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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = amountColor,
            )
        }
    }
}

@Composable
private fun RecentTransactionRow(
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
    val subtitle = TransactionDisplay.subtitle(
        type = transaction.type,
        personSnapshot = transaction.expensePersonSnapshot,
        categorySnapshot = transaction.expenseCategorySnapshot,
        note = transaction.note,
    )
    val dateLabel = GeoDates.formatEpochDay(transaction.transactionDate)
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
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = listOfNotNull(dateLabel, subtitle).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
