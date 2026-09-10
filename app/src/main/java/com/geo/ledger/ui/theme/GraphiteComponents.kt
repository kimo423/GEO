package com.geo.ledger.ui.theme
import com.geo.ledger.data.local.peopleLabel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geo.ledger.R
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.LedgerEntry
import com.geo.ledger.domain.PeriodSummary
import com.geo.ledger.domain.TransactionDisplay
import com.geo.ledger.ui.attachments.TransactionAttachmentBadge
import com.geo.ledger.ui.home.HomeUiState
import com.geo.ledger.util.GeoDates
import com.geo.ledger.util.MoneyFormatter

val GraphiteInk = Color(0xFF242B35)
private val Silver = Color(0xFFBBC4CF)
private val LightIncome = Color(0xFF98DDC8)
private val LightExpense = Color(0xFFFFB8AC)

@Composable
fun GraphiteHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
    }
}

@Composable
fun GraphitePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E6EC))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun GraphiteHero(label: String, amount: String, negative: Boolean = false, content: @Composable ColumnScope.() -> Unit = {}) {
    Surface(shape = RoundedCornerShape(28.dp), color = GraphiteInk, contentColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFF444E5C)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF38424F), Color(0xFF1D232C))))
            .padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Silver, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("G /", color = Silver, style = MaterialTheme.typography.titleMedium)
            }
            // Wrapping instead of clipping keeps large balances and accessibility font sizes readable.
            Text(amount, color = if (negative) LightExpense else Color.White,
                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Metric(label: String, amount: String, color: Color, modifier: Modifier = Modifier, dark: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (dark) Silver else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun GraphiteHome(state: HomeUiState, onAdd: () -> Unit, onDetail: (Long) -> Unit, onViewAll: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        GraphiteHeading("GEO", "私人账本  /  ${state.dateLabel}")
        when {
            state.ledgerError -> Text(stringResource(R.string.error_ledger_corrupt), color = GeoExpense)
            !state.isReady -> LinearProgressIndicator(Modifier.fillMaxWidth())
            else -> {
                val data = state.snapshot
                GraphiteHero(stringResource(R.string.current_balance), MoneyFormatter.plain(data.currentBalanceCents), data.currentBalanceCents < 0) {
                    HorizontalDivider(color = Color(0xFF505A67), modifier = Modifier.padding(vertical = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Metric("本月收入", MoneyFormatter.unsigned(data.monthIncomeCents), LightIncome, Modifier.weight(1f), true)
                        Metric("本月支出", MoneyFormatter.unsigned(data.monthExpenseCents), LightExpense, Modifier.weight(1f), true)
                    }
                }
                FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_transaction))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("最近账单", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onViewAll) { Text("查看全部") }
                }
                if (data.recent.isEmpty()) GraphitePanel {
                    Text(stringResource(R.string.empty_bills_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.empty_bills_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    data.recent.forEach { entry -> GraphiteTransactionCard(entry) { onDetail(entry.transaction.id) } }
                }
            }
        }
        if (!state.isReady || state.ledgerError) Button(onClick = onAdd) { Text(stringResource(R.string.add_transaction_plus)) }
    }
}

@Composable
fun GraphiteTransactionCard(entry: LedgerEntry, onClick: () -> Unit) {
    val tx = entry.transaction
    val income = tx.type == TransactionType.INCOME
    val tint = if (income) GeoIncome else GeoExpense
    val title = TransactionDisplay.title(tx.type, tx.expenseCategorySnapshot, tx.incomeSource, tx.note,
        stringResource(R.string.expense), stringResource(R.string.income))
    val subtitle = TransactionDisplay.subtitle(tx.type, tx.peopleLabel(), tx.expenseCategorySnapshot, tx.note)
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E6EC))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = .08f)) {
                    Icon(if (income) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        if (income) "收入" else "支出", tint = tint, modifier = Modifier.padding(10.dp).size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            // Separate amount from the title so long Chinese labels cannot squeeze financial values.
            Text(MoneyFormatter.transaction(tx.type, tx.amountCents), color = tint,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TransactionAttachmentBadge(tx.transactionUuid)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(GeoDates.formatRecordedAt(tx.transactionDate, tx.createdAtMillis),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.balance_after, MoneyFormatter.plain(entry.balanceAfterCents)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GraphitePeriodSummary(summary: PeriodSummary) {
    GraphiteHero("期末余额", MoneyFormatter.plain(summary.endingBalanceCents), summary.endingBalanceCents < 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("收入", MoneyFormatter.unsigned(summary.incomeCents), LightIncome, Modifier.weight(1f), true)
            Metric("支出", MoneyFormatter.unsigned(summary.expenseCents), LightExpense, Modifier.weight(1f), true)
        }
        HorizontalDivider(color = Color(0xFF505A67))
        Text("净变化  ${MoneyFormatter.signed(summary.netChangeCents)}", style = MaterialTheme.typography.bodyMedium,
            color = if (summary.netChangeCents < 0) LightExpense else LightIncome)
    }
}
