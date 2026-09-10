package com.geo.ledger.ui.transactiondetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.geo.ledger.GeoApplication
import com.geo.ledger.R
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.NavigationLockPolicy
import com.geo.ledger.ui.GeoViewModelFactory
import com.geo.ledger.ui.theme.GeoExpense
import com.geo.ledger.ui.theme.GeoIncome
import com.geo.ledger.util.GeoDates
import com.geo.ledger.util.MoneyFormatter
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun TransactionDetailScreen(
    onEdit: (Long) -> Unit,
    onDeleted: (Int) -> Unit,
    onNavigationLock: (Boolean) -> Unit = {},
    viewModel: TransactionDetailViewModel = composeViewModel(
        factory = GeoViewModelFactory(
            (LocalContext.current.applicationContext as GeoApplication).repository,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val completionConsumed = remember { AtomicBoolean(false) }
    val deleting = uiState.let { it is TransactionDetailUiState.Content && it.isDeleting }
    fun completeDeleted() {
        if (!NavigationLockPolicy.allowCompletionBack(completionConsumed.get())) return
        if (!completionConsumed.compareAndSet(false, true)) return
        showDeleteConfirm = false
        onDeleted(R.string.feedback_deleted)
    }
    BackHandler(enabled = deleting) { }
    DisposableEffect(deleting) {
        onNavigationLock(deleting)
        onDispose { onNavigationLock(false) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TransactionDetailEvent.Deleted -> completeDeleted()
                is TransactionDetailEvent.Failed -> {
                    snackbarHostState.showSnackbar(resources.getString(event.messageRes))
                }
            }
        }
    }
    LaunchedEffect(uiState) {
        if (uiState is TransactionDetailUiState.Deleted) {
            completeDeleted()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            TransactionDetailUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            TransactionDetailUiState.Deleted -> { }
            TransactionDetailUiState.NotFound -> {
                Text(
                    text = stringResource(R.string.transaction_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }
            is TransactionDetailUiState.Content -> {
                ContentBody(
                    state = state,
                    onEdit = { onEdit(state.transactionId) },
                    onDelete = { showDeleteConfirm = true },
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }

    val contentState = uiState as? TransactionDetailUiState.Content
    if (showDeleteConfirm && contentState != null) {
        val deleting = contentState.isDeleting
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_transaction_title)) },
            text = { Text(stringResource(R.string.delete_transaction_message)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::delete,
                    enabled = !deleting,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!deleting) showDeleteConfirm = false },
                    enabled = !deleting,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ContentBody(
    state: TransactionDetailUiState.Content,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val amountColor = when (state.type) {
        TransactionType.INCOME -> GeoIncome
        TransactionType.EXPENSE -> GeoExpense
    }
    val balanceColor = if (state.balanceAfterCents < 0L) {
        GeoExpense
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = com.geo.ledger.ui.theme.GeoSpacing.Page, vertical = com.geo.ledger.ui.theme.GeoSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DetailRow(
                label = stringResource(R.string.transaction_type),
                value = stringResource(
                    if (state.type == TransactionType.EXPENSE) R.string.expense else R.string.income,
                ),
            )
            DetailRow(
                label = stringResource(
                    if (state.type == TransactionType.EXPENSE) R.string.expense_amount else R.string.income_amount,
                ),
                value = MoneyFormatter.transaction(state.type, state.amountCents),
                valueColor = amountColor,
                prominent = true,
            )
            DetailRow(
                label = stringResource(R.string.date),
                value = GeoDates.formatRecordedAt(state.epochDay, state.createdAtMillis),
            )
            if (state.type == TransactionType.EXPENSE) {
                state.personSnapshot?.let { person ->
                    DetailRow(label = stringResource(R.string.person), value = person)
                }
                state.categorySnapshot?.let { category ->
                    DetailRow(label = stringResource(R.string.category), value = category)
                }
            } else {
                state.incomeSource?.let { source ->
                    DetailRow(label = stringResource(R.string.source), value = source)
                }
            }
            state.note?.let { note ->
                DetailRow(label = stringResource(R.string.note), value = note)
            }
            DetailRow(
                label = stringResource(R.string.detail_balance_after),
                value = MoneyFormatter.plain(state.balanceAfterCents),
                valueColor = balanceColor,
            )
            com.geo.ledger.ui.attachments.DetailAttachments(state.transactionId)
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onEdit,
                enabled = !state.isDeleting,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.edit))
            }
            Button(
                onClick = onDelete,
                enabled = !state.isDeleting,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GeoExpense),
            ) {
                if (state.isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onBackground,
    prominent: Boolean = false,
) {
    if (com.geo.ledger.ui.theme.isGraphite) {
        if (prominent) com.geo.ledger.ui.theme.GraphiteHero(label, value, valueColor == GeoExpense)
        else com.geo.ledger.ui.theme.GraphitePanel {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
        }
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (prominent) {
                MaterialTheme.typography.displaySmall
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = if (prominent) FontWeight.SemiBold else FontWeight.Medium,
            color = valueColor,
        )
    }
}
